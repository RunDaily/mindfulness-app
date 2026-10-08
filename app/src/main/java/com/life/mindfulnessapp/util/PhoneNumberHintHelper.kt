package com.life.mindfulnessapp.util

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest
import com.google.android.gms.auth.api.identity.Identity

/**
 * 弹出系统「选择本机号码」面板（非静默读号）。
 * 依赖 Google Play 服务；不可用时返回 null，UI 回退手动输入。
 */
object PhoneNumberHintHelper {

    fun requestHintIntentSender(
        activity: Activity,
        onReady: (IntentSender?) -> Unit
    ) {
        try {
            val request = GetPhoneNumberHintIntentRequest.builder().build()
            Identity.getSignInClient(activity)
                .getPhoneNumberHintIntent(request)
                .addOnSuccessListener { pendingIntent ->
                    onReady(pendingIntent.intentSender)
                }
                .addOnFailureListener {
                    onReady(null)
                }
        } catch (_: Exception) {
            onReady(null)
        }
    }

    fun parsePhoneFromResult(activity: Activity, data: Intent?): String? {
        if (data == null) return null
        return try {
            val raw = Identity.getSignInClient(activity).getPhoneNumberFromIntent(data)
            normalizeCnMobile(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeCnMobile(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length == 11 && digits.startsWith("1") -> digits
            digits.length == 13 && digits.startsWith("86") -> digits.substring(2)
            else -> digits.takeLast(11).takeIf { it.length == 11 && it.startsWith("1") }
        }
    }
}
