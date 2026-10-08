package com.life.mindfulnessapp.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.DisplayQuote
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.wallpaper.WallpaperLines
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class WallpaperStudioViewModel @Inject constructor(
    private val quoteRepository: QuoteRepository
) : ViewModel() {

    data class LineState(
        val text: String = WallpaperLines.default,
        val author: String = "",
        /** 是否来自短句芯片（非格言库） */
        val fromPreset: Boolean = true
    )

    private val _line = MutableStateFlow(LineState())
    val line: StateFlow<LineState> = _line.asStateFlow()

    private val _quoteLoading = MutableStateFlow(false)
    val quoteLoading: StateFlow<Boolean> = _quoteLoading.asStateFlow()

    private var refreshCount = 0

    fun selectPreset(text: String) {
        _line.value = LineState(text = text, author = "", fromPreset = true)
    }

    /** 从跨进程样式文件恢复上次选择 */
    fun restoreSaved(line: String, author: String) {
        val text = line.trim().ifBlank { WallpaperLines.default }
        val authorClean = author.trim()
        _line.value = LineState(
            text = text,
            author = authorClean,
            fromPreset = authorClean.isEmpty() && text in WallpaperLines.all
        )
    }

    fun refreshQuote() {
        if (_quoteLoading.value) return
        viewModelScope.launch {
            _quoteLoading.value = true
            try {
                refreshCount += 1
                val minuteOfDay = Calendar.getInstance().let {
                    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
                }
                val slot = (minuteOfDay + refreshCount * 37) % (24 * 60)
                val quote: DisplayQuote = quoteRepository.getPushQuote(slot)
                val content = quote.content.trim()
                if (content.isNotEmpty()) {
                    _line.value = LineState(
                        text = content,
                        author = quote.author.trim(),
                        fromPreset = false
                    )
                }
            } finally {
                _quoteLoading.value = false
            }
        }
    }
}
