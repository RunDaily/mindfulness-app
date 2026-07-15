package com.life.mindfulnessapp.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.life.mindfulnessapp.data.network.VipPlan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ════════════════════════════════════════════
//  购买结果密封类
// ════════════════════════════════════════════

sealed class BillingResult2 {
    /** 购买成功，purchaseToken 用于服务端二次验证 */
    data class Success(
        val purchaseToken: String,
        val productId: String,
        val productType: String
    ) : BillingResult2()
    /** 用户取消了购买 */
    object Cancelled : BillingResult2()
    /** 发生错误 */
    data class Error(val message: String, val responseCode: Int = -1) : BillingResult2()
}

// ════════════════════════════════════════════
//  ProductDetails 缓存（含价格字符串）
// ════════════════════════════════════════════

data class PlayProductDetails(
    val plan: VipPlan,
    /** 来自 Google Play 的格式化本地化价格字符串（如 "$4.99/month"） */
    val formattedPrice: String,
    val productDetails: ProductDetails
)

// ════════════════════════════════════════════
//  BillingManager
// ════════════════════════════════════════════

/**
 * 封装 Google Play Billing Library 的全部交互逻辑。
 *
 * 职责：
 *  1. 维护 BillingClient 连接（自动重连）
 *  2. 查询产品详情（含本地化价格），供 UI 展示
 *  3. 发起购买流程（launchBillingFlow）
 *  4. 处理购买回调，通过 [purchaseResultFlow] 向外广播结果
 *  5. 确认已消耗 / 已确认购买（acknowledge / consume）
 *
 * 注意：
 *  - 买断型商品（inapp）：调用 consumePurchase 使其可重复购买（如果业务允许），
 *    或仅 acknowledgePurchase（如果是永久解锁不可重复）。
 *    本应用 lifetime 为永久买断，使用 acknowledgePurchase 不消耗。
 *  - 订阅型商品（subs）：Google Play 自动处理续费，无需客户端 consume，
 *    只需 acknowledgePurchase 确认首次购买。
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── 购买结果广播（ViewModel 订阅）────────────────────────────────────────
    private val _purchaseResultFlow = MutableSharedFlow<BillingResult2>(extraBufferCapacity = 1)
    val purchaseResultFlow: SharedFlow<BillingResult2> = _purchaseResultFlow.asSharedFlow()

    // ── 产品详情缓存（含本地化价格，供 VipScreen 展示）───────────────────────
    private val _productDetailsMap = MutableStateFlow<Map<String, PlayProductDetails>>(emptyMap())
    val productDetailsMap: StateFlow<Map<String, PlayProductDetails>> = _productDetailsMap.asStateFlow()

    // ── BillingClient 实例 ───────────────────────────────────────────────────
    private var billingClient: BillingClient? = null
    private var isConnecting = false

    // ════════════════════════════════════════
    //  连接管理
    // ════════════════════════════════════════

    /** 启动连接（在 Application.onCreate 或第一次需要时调用） */
    fun connect() {
        if (billingClient?.isReady == true || isConnecting) return
        isConnecting = true
        billingClient = BillingClient.newBuilder(context)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: com.android.billingclient.api.BillingResult) {
                isConnecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    // 连接成功，立即查询产品详情
                    scope.launch { queryAllProductDetails() }
                    // 处理可能存在的未消费购买（App 重启后补发）
                    scope.launch { handlePendingPurchases() }
                }
            }

            override fun onBillingServiceDisconnected() {
                isConnecting = false
                // 断线后延迟重连（避免立即循环）
                scope.launch {
                    kotlinx.coroutines.delay(3_000)
                    connect()
                }
            }
        })
    }

    /** 断开连接（在 Application 销毁时调用） */
    fun disconnect() {
        billingClient?.endConnection()
        billingClient = null
    }

    // ════════════════════════════════════════
    //  产品详情查询
    // ════════════════════════════════════════

    /** 查询所有 VipPlan 对应的产品详情（含本地化价格） */
    suspend fun queryAllProductDetails() {
        val client = billingClient ?: return

        val subsIds    = VipPlan.entries.filter { it.productType == "subs"  }.map { it.productId }
        val inappIds   = VipPlan.entries.filter { it.productType == "inapp" }.map { it.productId }
        val newMap     = mutableMapOf<String, PlayProductDetails>()

        // 查询订阅产品
        if (subsIds.isNotEmpty()) {
            val subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(subsIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }).build()
            val subsResult = withContext(Dispatchers.IO) { client.queryProductDetails(subsParams) }
            if (subsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                subsResult.productDetailsList?.forEach { pd ->
                    val plan = VipPlan.entries.firstOrNull { it.productId == pd.productId } ?: return@forEach
                    val price = pd.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.firstOrNull()
                        ?.formattedPrice ?: ""
                    newMap[pd.productId] = PlayProductDetails(plan, price, pd)
                }
            }
        }

        // 查询一次性购买产品
        if (inappIds.isNotEmpty()) {
            val inappParams = QueryProductDetailsParams.newBuilder()
                .setProductList(inappIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                }).build()
            val inappResult = withContext(Dispatchers.IO) { client.queryProductDetails(inappParams) }
            if (inappResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                inappResult.productDetailsList?.forEach { pd ->
                    val plan = VipPlan.entries.firstOrNull { it.productId == pd.productId } ?: return@forEach
                    val price = pd.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
                    newMap[pd.productId] = PlayProductDetails(plan, price, pd)
                }
            }
        }

        _productDetailsMap.value = newMap
    }

    // ════════════════════════════════════════
    //  发起购买
    // ════════════════════════════════════════

    /**
     * 调起 Google Play 购买底栏。
     *
     * @param activity 当前前台 Activity（Billing 要求必须传 Activity）
     * @param plan     要购买的方案
     * @return true = 成功调起购买底栏；false = 产品详情未加载或客户端未连接
     */
    fun launchBillingFlow(activity: Activity, plan: VipPlan): Boolean {
        val client = billingClient?.takeIf { it.isReady } ?: run {
            connect()
            _purchaseResultFlow.tryEmit(BillingResult2.Error("支付服务连接中，请稍后再试"))
            return false
        }

        val playDetails = _productDetailsMap.value[plan.productId] ?: run {
            // 产品详情未缓存，重新拉取后通知用户重试
            scope.launch { queryAllProductDetails() }
            _purchaseResultFlow.tryEmit(BillingResult2.Error("产品信息加载中，请稍后再试"))
            return false
        }

        val productDetailsParams = if (plan.productType == "subs") {
            val offerToken = playDetails.productDetails
                .subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken ?: run {
                _purchaseResultFlow.tryEmit(BillingResult2.Error("未找到订阅报价，请稍后重试"))
                return false
            }
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(playDetails.productDetails)
                .setOfferToken(offerToken)
                .build()
        } else {
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(playDetails.productDetails)
                .build()
        }

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = client.launchBillingFlow(activity, billingFlowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    // ════════════════════════════════════════
    //  购买回调处理
    // ════════════════════════════════════════

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    scope.launch { handlePurchase(purchase) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseResultFlow.tryEmit(BillingResult2.Cancelled)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // 已购买：重新查询并补发结果（用户换机重装后可能触发）
                scope.launch { handlePendingPurchases() }
            }
            else -> {
                _purchaseResultFlow.tryEmit(
                    BillingResult2.Error(
                        message = "支付失败（code: ${result.responseCode}）",
                        responseCode = result.responseCode
                    )
                )
            }
        }
    }

    /**
     * 处理单笔购买：
     * 1. 仅在购买状态为 PURCHASED（已完成，非 PENDING）时处理
     * 2. 广播购买令牌供 ViewModel → Repository → 服务端验证
     * 3. 服务端验证成功后再 acknowledgePurchase，这是 Google Play 要求的顺序
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val productId = purchase.products.firstOrNull() ?: return
        val plan = VipPlan.entries.firstOrNull { it.productId == productId } ?: return

        // 广播结果（ViewModel 收到后向自己的服务端验证）
        _purchaseResultFlow.emit(
            BillingResult2.Success(
                purchaseToken = purchase.purchaseToken,
                productId     = productId,
                productType   = plan.productType
            )
        )
    }

    /**
     * 服务端验证成功后，调用此方法确认购买。
     * Google Play 要求：INAPP 和 SUBS 商品都需在 3 天内 acknowledge，否则自动退款。
     */
    suspend fun acknowledgePurchase(purchaseToken: String): Boolean {
        val client = billingClient?.takeIf { it.isReady } ?: return false
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        val result = withContext(Dispatchers.IO) { client.acknowledgePurchase(params) }
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    // ════════════════════════════════════════
    //  处理待处理购买（App 重启时补发）
    // ════════════════════════════════════════

    /** 查询所有未处理购买，补发结果（用于处理网络中断后未 ack 的订单） */
    private suspend fun handlePendingPurchases() {
        val client = billingClient?.takeIf { it.isReady } ?: return

        // 查询订阅
        val subsResult = withContext(Dispatchers.IO) {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )
        }
        subsResult.purchasesList.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                handlePurchase(purchase)
            }
        }

        // 查询一次性购买
        val inappResult = withContext(Dispatchers.IO) {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        }
        inappResult.purchasesList.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                handlePurchase(purchase)
            }
        }
    }

    // ════════════════════════════════════════
    //  便捷：获取本地化价格字符串
    // ════════════════════════════════════════

    /** 获取指定方案的 Google Play 本地化价格字符串（如 "$4.99"）；未加载时返回 null */
    fun getFormattedPrice(plan: VipPlan): String? =
        _productDetailsMap.value[plan.productId]?.formattedPrice
}
