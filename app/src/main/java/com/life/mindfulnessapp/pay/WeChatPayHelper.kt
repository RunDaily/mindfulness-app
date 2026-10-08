package com.life.mindfulnessapp.pay

import android.content.Context
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.network.WxAppPayParams
import com.tencent.mm.opensdk.modelpay.PayReq
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 微信 APP 支付：OpenSDK 调起收银台 + 结果总线。
 */
object WeChatPayHelper {

    /** errCode: 0 成功，-1 错误，-2 用户取消（与微信 BaseResp 一致） */
    private val _payResults = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val payResults: SharedFlow<Int> = _payResults.asSharedFlow()

    fun createApi(context: Context): IWXAPI {
        val appId = BuildConfig.WX_APP_ID
        return WXAPIFactory.createWXAPI(context.applicationContext, appId, true).also {
            if (appId.isNotBlank()) it.registerApp(appId)
        }
    }

    fun isConfigured(): Boolean = BuildConfig.WX_APP_ID.isNotBlank()

    fun isWeChatInstalled(context: Context): Boolean {
        if (!isConfigured()) return false
        return try {
            createApi(context).isWXAppInstalled
        } catch (_: Exception) {
            false
        }
    }

    fun launch(context: Context, params: WxAppPayParams): Boolean {
        if (!isConfigured()) return false
        if (params.appId.isBlank() || params.prepayId.isBlank() || params.sign.isBlank()) return false
        return try {
            val api = createApi(context)
            if (!api.isWXAppInstalled) return false
            val req = PayReq().apply {
                appId = params.appId
                partnerId = params.partnerId
                prepayId = params.prepayId
                packageValue = params.packageValue.ifBlank { "Sign=WXPay" }
                nonceStr = params.nonceStr
                timeStamp = params.timeStamp
                sign = params.sign
            }
            api.sendReq(req)
        } catch (_: Exception) {
            false
        }
    }

    fun emitResult(errCode: Int) {
        _payResults.tryEmit(errCode)
    }
}
