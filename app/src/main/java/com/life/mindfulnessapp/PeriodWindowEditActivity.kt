package com.life.mindfulnessapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.ui.applist.PeriodDaysPickerContent
import com.life.mindfulnessapp.ui.applist.PeriodRangePickerContent
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import com.life.mindfulnessapp.ui.theme.chrome
import com.life.mindfulnessapp.ui.theme.rememberResolvedThemePack
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * App 级时段锁 · 添加/编辑（二级页）。
 * 结果通过 [EXTRA_RESULT_WINDOW_JSON] 回传。
 */
@AndroidEntryPoint
class PeriodWindowEditActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isNew = intent.getBooleanExtra(EXTRA_IS_NEW, true)
        val windowJson = intent.getStringExtra(EXTRA_WINDOW_JSON).orEmpty()
        val existingJson = intent.getStringExtra(EXTRA_EXISTING_JSON).orEmpty()
        val editingWindow = PeriodWindowsCodec.decode(windowJson).firstOrNull()
            ?: PeriodWindow.defaultSleep()
        val existing = PeriodWindowsCodec.decode(existingJson)

        val bootPack = appPreferences.resolvedThemePack()
        window.setBackgroundDrawableResource(bootPack.chrome().windowBgRes)
        val darkTheme = bootPack.isDark
        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)

        setContent {
            val themePackPref by appPreferences.themePack.collectAsState()
            val themeFollow by appPreferences.themeFollowSystem.collectAsState()
            val themePack = rememberResolvedThemePack(themePackPref, themeFollow)
            val isDarkTheme = themePack.isDark
            SideEffect {
                window.setBackgroundDrawableResource(themePack.chrome().windowBgRes)
            }
            MindfulnessAppTheme(themePack = themePack) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PeriodWindowEditScreen(
                        initial = editingWindow,
                        isNew = isNew,
                        existing = existing,
                        onConfirm = { updated ->
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(
                                    EXTRA_RESULT_WINDOW_JSON,
                                    PeriodWindowsCodec.encode(listOf(updated))
                                )
                            )
                            finish()
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_IS_NEW = "is_new"
        const val EXTRA_WINDOW_JSON = "window_json"
        const val EXTRA_EXISTING_JSON = "existing_json"
        const val EXTRA_RESULT_WINDOW_JSON = "result_window_json"

        fun createIntent(
            context: Context,
            window: PeriodWindow,
            isNew: Boolean,
            existing: List<PeriodWindow>
        ): Intent = Intent(context, PeriodWindowEditActivity::class.java).apply {
            putExtra(EXTRA_IS_NEW, isNew)
            putExtra(EXTRA_WINDOW_JSON, PeriodWindowsCodec.encode(listOf(window)))
            putExtra(EXTRA_EXISTING_JSON, PeriodWindowsCodec.encode(existing))
        }

        fun parseResult(data: Intent?): PeriodWindow? {
            val json = data?.getStringExtra(EXTRA_RESULT_WINDOW_JSON) ?: return null
            return PeriodWindowsCodec.decode(json).firstOrNull()
        }
    }
}

@Composable
private fun PeriodWindowEditScreen(
    initial: PeriodWindow,
    isNew: Boolean,
    existing: List<PeriodWindow>,
    onConfirm: (PeriodWindow) -> Unit,
    onCancel: () -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var conflictHint by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val maxMsg = PeriodLockPolicy.MESSAGE_MAX_CHARS
    val policy = PeriodLockPolicy

    BackHandler { onCancel() }

    fun tryConfirm() {
        val updated = draft.copy(message = draft.message.trim())
        val conflict = policy.conflictWith(
            candidate = updated,
            existing = existing,
            excludeId = if (isNew) null else initial.id
        )
        if (conflict != null) {
            conflictHint = when (conflict) {
                com.life.mindfulnessapp.domain.model.PeriodWindowConflict.Duplicate -> "此时段已添加"
                com.life.mindfulnessapp.domain.model.PeriodWindowConflict.Overlap -> "与已有时段重叠"
            }
            return
        }
        onConfirm(updated)
    }

    SecondaryPageScaffold(
        title = if (isNew) "添加时段" else "编辑时段",
        onBack = onCancel
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        PeriodRangePickerContent(
            initialStartMinute = draft.startMinute,
            initialEndMinute = draft.endMinute,
            onRangeChange = { start, end ->
                conflictHint = null
                draft = draft.copy(startMinute = start, endMinute = end)
            }
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "重复",
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = cs.onSurface.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        PeriodDaysPickerContent(
            selectedMask = draft.daysMask,
            onMaskChange = {
                conflictHint = null
                draft = draft.copy(daysMask = it)
            }
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "寄语",
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = cs.onSurface.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "锁定时拦截页会显示这段话。",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.36f)
        )
        OutlinedTextField(
            value = draft.message,
            onValueChange = { draft = draft.copy(message = it.take(maxMsg)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            placeholder = {
                Text(
                    "写一段话，留给锁定中的自己…",
                    fontSize = 14.sp,
                    color = cs.onSurface.copy(alpha = 0.35f)
                )
            },
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LogoGreen,
                unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
            )
        )
        conflictHint?.let {
            Text(
                it,
                color = cs.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            "保存",
            color = LogoGreen,
            fontSize = 15.sp,
            modifier = Modifier.clickable(onClick = ::tryConfirm)
        )
        Text(
            "取消",
            color = cs.onSurfaceVariant.copy(alpha = 0.65f),
            fontSize = 15.sp,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 24.dp)
                .clickable(onClick = onCancel)
        )
    }
    }
}
