package com.life.mindfulnessapp.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.pay.WeChatPayHelper
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory

/**
 * 微信支付结果回调页。包名必须为 `{applicationId}.wxapi.WXPayEntryActivity`。
 */
class WXPayEntryActivity : Activity(), IWXAPIEventHandler {

    private val api by lazy {
        WXAPIFactory.createWXAPI(this, BuildConfig.WX_APP_ID, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            api.handleIntent(intent, this)
        } catch (_: Exception) {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        api.handleIntent(intent, this)
    }

    override fun onReq(req: BaseReq?) {
        finish()
    }

    override fun onResp(resp: BaseResp?) {
        WeChatPayHelper.emitResult(resp?.errCode ?: BaseResp.ErrCode.ERR_COMM)
        finish()
    }
}
