package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.repository.DisplayQuote
import dagger.hilt.android.EntryPointAccessors
import java.util.Calendar

/**
 * 停住门页脚注格言：尊重快关；雾色斜体；右侧可收藏，不弹页。
 * 同一次展示按小时槽稳定取句。
 */
@Composable
fun StopDoorQuoteFootnote(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var quote by remember { mutableStateOf<DisplayQuote?>(null) }
    var favorited by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            QuoteRepositoryEntryPoint::class.java
        )
        val prefs = entry.appPreferences()
        if (!prefs.isStopQuoteEnabled()) return@LaunchedEffect
        val repo = entry.quoteRepository()
        val slotMinute = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60
        val q = repo.getPushQuote(slotMinute)
        if (q.content.isBlank()) return@LaunchedEffect
        quote = q
        favorited = prefs.isQuoteFavorited(q.id, q.content)
    }

    val shown = quote ?: return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shown.content,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic,
                color = LockInk.mist.copy(alpha = 0.78f),
                lineHeight = 18.sp,
                letterSpacing = 0.15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val author = shown.author.trim().removePrefix("—").trim()
            if (author.isNotEmpty()) {
                Text(
                    text = "— $author",
                    modifier = Modifier.padding(top = 5.dp),
                    fontSize = 11.sp,
                    color = LockInk.mist.copy(alpha = 0.48f)
                )
            }
        }
        Text(
            text = if (favorited) "♥" else "♡",
            fontSize = 14.sp,
            color = if (favorited) LockInk.leafText else LockInk.mist.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(start = 10.dp, top = 1.dp)
                .clickable(role = Role.Button) {
                    val entry = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        QuoteRepositoryEntryPoint::class.java
                    )
                    favorited = entry.quoteRepository().toggleFavoriteQuote(shown)
                }
        )
    }
}
