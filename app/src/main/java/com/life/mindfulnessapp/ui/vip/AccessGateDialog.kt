package com.life.mindfulnessapp.ui.vip

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.util.PhoneNumberHintHelper

enum class AccessGateStep {
    /** 路径选择：领码优先 / 已有码 / 看方案 */
    Gate,
    /** 输入并兑换会员码 */
    Redeem,
    /** 留资领码：手机或邮箱 */
    Claim,
    /** 领码成功但自动兑换失败时的兜底 */
    Issued
}

data class AccessGateUiState(
    val visible: Boolean = false,
    val step: AccessGateStep = AccessGateStep.Gate,
    val codeInput: String = "",
    /** phone | email */
    val claimChannel: String = "phone",
    val claimContact: String = "",
    val issuedCode: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    /** 开通成功后由宿主弹出 Toast，随后 [consumeUnlockToast] */
    val unlockToast: String? = null
)

/**
 * 免费坑位耗尽时的体系底栏：领码优先 → 已有码兑换 → 正式会员方案。
 * 未兑码不谈早鸟。领码成功后自动兑换；失败才进入 Issued 兜底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessGateDialog(
    state: AccessGateUiState,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    onDismiss: () -> Unit,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onOpenClaim: () -> Unit,
    onOpenRedeem: () -> Unit,
    onClaimChannelChange: (String) -> Unit,
    onClaimContactChange: (String) -> Unit,
    onSubmitClaim: () -> Unit,
    onRedeemIssued: () -> Unit,
    onViewMembership: () -> Unit,
    onBackToGate: () -> Unit,
    onConsumeUnlockToast: () -> Unit = {},
    /** 会员页等场景：弱化「坑位已满」话术 */
    limitReachedContext: Boolean = true
) {
    val context = LocalContext.current

    LaunchedEffect(state.unlockToast) {
        val msg = state.unlockToast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        onConsumeUnlockToast()
    }

    if (!state.visible) return

    val activity = context as? Activity
    val vipGold = Color(0xFFFFCC44)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val phoneHintLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && activity != null) {
            PhoneNumberHintHelper.parsePhoneFromResult(activity, result.data)
                ?.let(onClaimContactChange)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cardColor,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp, bottom = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(borderColor.copy(alpha = 0.45f))
            )

            when (state.step) {
                AccessGateStep.Gate -> GateStep(
                    state = state,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accentGreen = accentGreen,
                    vipGold = vipGold,
                    limitReachedContext = limitReachedContext,
                    onOpenClaim = onOpenClaim,
                    onOpenRedeem = onOpenRedeem,
                    onViewMembership = onViewMembership,
                    onDismiss = onDismiss
                )
                AccessGateStep.Redeem -> RedeemStep(
                    state = state,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    borderColor = borderColor,
                    accentGreen = accentGreen,
                    onCodeChange = onCodeChange,
                    onRedeem = onRedeem,
                    onBack = onBackToGate
                )
                AccessGateStep.Claim -> ClaimStep(
                    state = state,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    borderColor = borderColor,
                    accentGreen = accentGreen,
                    onChannelChange = onClaimChannelChange,
                    onContactChange = onClaimContactChange,
                    onSubmit = onSubmitClaim,
                    onBack = onBackToGate,
                    onPickPhone = {
                        if (activity == null) return@ClaimStep
                        PhoneNumberHintHelper.requestHintIntentSender(activity) { sender ->
                            if (sender != null) {
                                phoneHintLauncher.launch(
                                    IntentSenderRequest.Builder(sender).build()
                                )
                            }
                        }
                    }
                )
                AccessGateStep.Issued -> IssuedStep(
                    state = state,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    borderColor = borderColor,
                    accentGreen = accentGreen,
                    vipGold = vipGold,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("member_code", state.issuedCode))
                    },
                    onRedeem = onRedeemIssued,
                    onBack = onBackToGate
                )
            }
        }
    }
}

@Composable
private fun GateStep(
    state: AccessGateUiState,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    vipGold: Color,
    limitReachedContext: Boolean,
    onOpenClaim: () -> Unit,
    onOpenRedeem: () -> Unit,
    onViewMembership: () -> Unit,
    onDismiss: () -> Unit
) {
    HeaderRow(
        icon = Icons.Default.WorkspacePremium,
        iconTint = accentGreen,
        title = if (limitReachedContext) "免费坑位已满" else "开通会员",
        subtitle = if (limitReachedContext) {
            "免费版可监控 ${AppPreferences.FREE_MONITOR_LIMIT} 个 App"
        } else {
            "用会员码开通，或查看正式方案"
        },
        textPrimary = textPrimary,
        textSecondary = textSecondary
    )

    Text(
        if (limitReachedContext) {
            "继续添加需要开通会员。可免费领取会员码，也可直接查看正式方案。"
        } else {
            "会员码可免费领取并立即开通；也可按正式标价自行购买。"
        },
        fontSize = 13.sp,
        color = textSecondary.copy(alpha = 0.78f),
        lineHeight = 19.sp
    )

    if (state.issuedCode.isNotBlank()) {
        Text(
            "本机已有未兑换的会员码，可直接去兑换。",
            fontSize = 12.sp,
            color = accentGreen.copy(alpha = 0.9f),
            lineHeight = 17.sp
        )
    }

    Button(
        onClick = onOpenClaim,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentGreen,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text("免费领取会员码", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }

    OutlinedButton(
        onClick = onOpenRedeem,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary)
    ) {
        Text(
            if (state.issuedCode.isNotBlank()) "兑换已有会员码" else "已有会员码，去兑换",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }

    Text(
        "查看正式会员方案",
        fontSize = 13.sp,
        color = vipGold.copy(alpha = 0.95f),
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(enabled = !state.busy, onClick = onViewMembership)
            .padding(vertical = 4.dp)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = onDismiss,
            colors = ButtonDefaults.textButtonColors(contentColor = textSecondary.copy(alpha = 0.5f))
        ) {
            Text("稍后再说", fontSize = 13.sp)
        }
    }
}

@Composable
private fun RedeemStep(
    state: AccessGateUiState,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onBack: () -> Unit
) {
    StepTitleBar(
        title = "兑换会员码",
        textPrimary = textPrimary,
        onBack = onBack,
        enabled = !state.busy
    )

    Text(
        "输入会员码即可在本机开通无限坑位与会员能力。",
        fontSize = 13.sp,
        color = textSecondary.copy(alpha = 0.75f),
        lineHeight = 19.sp
    )

    OutlinedTextField(
        value = state.codeInput,
        onValueChange = onCodeChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("输入会员码", color = textSecondary.copy(alpha = 0.4f)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(textPrimary, borderColor, accentGreen)
    )

    state.error?.let {
        Text(it, fontSize = 12.sp, color = Color(0xFFE74C3C))
    }

    Button(
        onClick = onRedeem,
        enabled = !state.busy && state.codeInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentGreen,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        BusyLabel(busy = state.busy, idle = "兑换并开通", busyText = "兑换中…")
    }
}

@Composable
private fun ClaimStep(
    state: AccessGateUiState,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    onChannelChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onPickPhone: () -> Unit
) {
    StepTitleBar(
        title = "领取会员码",
        textPrimary = textPrimary,
        onBack = onBack,
        enabled = !state.busy
    )

    Text(
        "留下联系方式即可领取（用于名额管理，不会发送验证码）。领取后将自动开通。",
        fontSize = 13.sp,
        color = textSecondary.copy(alpha = 0.75f),
        lineHeight = 19.sp
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChannelChip(
            label = "手机号",
            selected = state.claimChannel == "phone",
            accentGreen = accentGreen,
            textPrimary = textPrimary,
            borderColor = borderColor,
            onClick = { onChannelChange("phone") }
        )
        ChannelChip(
            label = "邮箱",
            selected = state.claimChannel == "email",
            accentGreen = accentGreen,
            textPrimary = textPrimary,
            borderColor = borderColor,
            onClick = { onChannelChange("email") }
        )
    }

    OutlinedTextField(
        value = state.claimContact,
        onValueChange = onContactChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                if (state.claimChannel == "phone") "请输入手机号" else "请输入邮箱",
                color = textSecondary.copy(alpha = 0.4f)
            )
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (state.claimChannel == "phone") KeyboardType.Phone else KeyboardType.Email
        ),
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(textPrimary, borderColor, accentGreen)
    )

    if (state.claimChannel == "phone") {
        Text(
            "选择本机号码",
            fontSize = 13.sp,
            color = accentGreen,
            modifier = Modifier
                .clickable(enabled = !state.busy, onClick = onPickPhone)
                .padding(vertical = 2.dp)
        )
    }

    Text(
        if (state.claimChannel == "phone") "一号一码，重复申请会返回已有会员码。"
        else "一邮一码，重复申请会返回已有会员码。",
        fontSize = 12.sp,
        color = textSecondary.copy(alpha = 0.55f),
        lineHeight = 17.sp
    )

    state.error?.let {
        Text(it, fontSize = 12.sp, color = Color(0xFFE74C3C))
    }

    Button(
        onClick = onSubmit,
        enabled = !state.busy && state.claimContact.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentGreen,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        BusyLabel(busy = state.busy, idle = "领取并开通", busyText = "处理中…")
    }
}

@Composable
private fun IssuedStep(
    state: AccessGateUiState,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    accentGreen: Color,
    vipGold: Color,
    onCopy: () -> Unit,
    onRedeem: () -> Unit,
    onBack: () -> Unit
) {
    StepTitleBar(
        title = "你的会员码",
        textPrimary = textPrimary,
        onBack = onBack,
        enabled = !state.busy
    )

    Text(
        state.infoMessage ?: "会员码已生成。自动开通未完成，请手动兑换。",
        fontSize = 13.sp,
        color = textSecondary.copy(alpha = 0.75f),
        lineHeight = 19.sp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentGreen.copy(alpha = 0.08f))
            .border(1.dp, accentGreen.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                state.issuedCode,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                color = textPrimary
            )
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "复制",
                tint = accentGreen,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onCopy)
            )
        }
    }

    state.error?.let {
        Text(it, fontSize = 12.sp, color = Color(0xFFE74C3C))
    }

    Button(
        onClick = onRedeem,
        enabled = !state.busy && state.issuedCode.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = accentGreen,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        BusyLabel(busy = state.busy, idle = "兑换并开通", busyText = "兑换中…")
    }

    Text(
        "也可稍后在会员页兑换。",
        fontSize = 12.sp,
        color = vipGold.copy(alpha = 0.75f),
        lineHeight = 17.sp
    )
}

@Composable
private fun StepTitleBar(
    title: String,
    textPrimary: Color,
    onBack: () -> Unit,
    enabled: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onBack, enabled = enabled) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = textPrimary
            )
        }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
    }
}

@Composable
private fun BusyLabel(busy: Boolean, idle: String, busyText: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
        if (busy) busyText else idle,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )
}

@Composable
private fun HeaderRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = textSecondary.copy(alpha = 0.65f), lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ChannelChip(
    label: String,
    selected: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) accentGreen.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) accentGreen.copy(alpha = 0.45f) else borderColor.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accentGreen else textPrimary.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun fieldColors(
    textPrimary: Color,
    borderColor: Color,
    accentGreen: Color
) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = textPrimary,
    unfocusedTextColor = textPrimary,
    focusedBorderColor = accentGreen.copy(alpha = 0.55f),
    unfocusedBorderColor = borderColor.copy(alpha = 0.55f),
    cursorColor = accentGreen
)
