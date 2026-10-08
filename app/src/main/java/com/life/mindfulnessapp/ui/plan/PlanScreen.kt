package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.life.mindfulnessapp.InstrumentConfigActivity
import com.life.mindfulnessapp.ui.theme.LogoGreen

/** 方案 Tab：列表；单 App 配置走 [InstrumentConfigActivity]。 */
@Composable
fun PlanScreen(
    onAddApp: () -> Unit = {},
    viewModel: PlanViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        PageTitle("方案")
        Spacer(Modifier.height(16.dp))

        if (state.packs.isEmpty()) {
            Text("还没有规则", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            HintChip("添加 App", LogoGreen, onAddApp)
        } else {
            Text(
                "生效中",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            state.packs.forEach { pack ->
                HairlineRow(
                    title = pack.appName,
                    meta = buildString {
                        append(pack.summary())
                        if (pack.rationale.isNotBlank()) append("\n").append(pack.rationale)
                    },
                    trailing = "›",
                    trailingSub = "",
                    packageName = pack.packageName,
                    onClick = {
                        context.startActivity(
                            InstrumentConfigActivity.createIntent(context, pack.packageName)
                        )
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "添加 App",
                color = LogoGreen,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onAddApp)
            )
        }

        state.message?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Text(msg, color = LogoGreen, fontSize = 12.sp)
        }
        if (state.loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                color = LogoGreen,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
