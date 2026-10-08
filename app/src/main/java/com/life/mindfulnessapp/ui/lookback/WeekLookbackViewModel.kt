package com.life.mindfulnessapp.ui.lookback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.export.LookbackCardExporter
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getWeekRange
import com.life.mindfulnessapp.domain.model.WeekLookbackSnapshot
import com.life.mindfulnessapp.domain.model.computeWeekLookback
import com.life.mindfulnessapp.domain.model.shouldOfferSundayLookbackTip
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekLookbackViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val usageRecordRepository: UsageRecordRepository,
    appLimitRepository: AppLimitRepository,
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    /**
     * 相对本周的偏移：0=本周，-1=上一周。
     * 仅允许查看本周与上周（避免无尽历史噪音）。
     */
    private val weekOffset = MutableStateFlow(0)

    init {
        analyticsRepository.trackLookbackView(weekOffset = 0)
    }

    private val limitsFlow = appLimitRepository.getEnabledAppLimits()

    val snapshot: StateFlow<WeekLookbackSnapshot?> = combine(
        weekOffset,
        limitsFlow
    ) { offset, limits -> offset to limits }
        .flatMapLatest { (offset, limits) ->
            val now = System.currentTimeMillis()
            val (thisStart, _) = getWeekRange(now)
            val targetStart = thisStart + offset * 7L * 24 * 60 * 60 * 1000
            val (start, end) = getWeekRange(targetStart)
            val prevStart = start - 7L * 24 * 60 * 60 * 1000
            combine(
                usageRecordRepository.getWeekRecords(start, end),
                usageRecordRepository.getWeekRecords(prevStart, start)
            ) { records: List<UsageRecordEntity>, prevRecords: List<UsageRecordEntity> ->
                computeWeekLookback(
                    records = records,
                    limits = limits,
                    weekStartMs = start,
                    weekEndMs = end,
                    nowMs = now,
                    previousWeekRecords = prevRecords
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canGoPrevWeek: StateFlow<Boolean> = weekOffset
        .map { it > -1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val canGoNextWeek: StateFlow<Boolean> = weekOffset
        .map { it < 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun goPrevWeek() {
        if (weekOffset.value > -1) {
            weekOffset.value = weekOffset.value - 1
            analyticsRepository.trackLookbackView(weekOffset = weekOffset.value)
        }
    }

    fun goNextWeek() {
        if (weekOffset.value < 0) {
            weekOffset.value = weekOffset.value + 1
            analyticsRepository.trackLookbackView(weekOffset = weekOffset.value)
        }
    }

    fun goCurrentWeek() {
        weekOffset.value = 0
        analyticsRepository.trackLookbackView(weekOffset = 0)
    }

    private val _isExportingCard = MutableStateFlow(false)
    val isExportingCard: StateFlow<Boolean> = _isExportingCard.asStateFlow()

    private var pendingCardPng: ByteArray? = null

    fun cardFileName(): String {
        val snap = snapshot.value
        return if (snap != null) {
            LookbackCardExporter.suggestedFileName(snap.weekStartMs, snap.weekEndMs)
        } else {
            "心锚_周回望.png"
        }
    }

    fun shareCard(bitmap: Bitmap, onDone: (Boolean, String) -> Unit) {
        if (_isExportingCard.value) return
        viewModelScope.launch {
            _isExportingCard.value = true
            try {
                val fileName = cardFileName()
                val png = withContext(Dispatchers.IO) { LookbackCardExporter.bitmapToPng(bitmap) }
                val file = withContext(Dispatchers.IO) {
                    LookbackCardExporter.writeToCache(appContext, fileName, png)
                }
                LookbackCardExporter.sharePng(
                    context = appContext,
                    file = file,
                    subject = "心锚 · 周回望"
                )
                analyticsRepository.trackExportRecords(
                    range = "week",
                    action = HaEvents.ExportAction.SHARE,
                    format = HaEvents.ExportFormat.LOOKBACK_CARD
                )
                onDone(true, "请选择分享方式")
            } catch (e: Exception) {
                onDone(false, e.message?.takeIf { it.isNotBlank() } ?: "分享失败，请稍后重试")
            } finally {
                _isExportingCard.value = false
            }
        }
    }

    fun prepareSaveCard(bitmap: Bitmap, onReady: (fileName: String) -> Unit, onError: (String) -> Unit) {
        if (_isExportingCard.value) return
        viewModelScope.launch {
            _isExportingCard.value = true
            try {
                pendingCardPng = withContext(Dispatchers.IO) { LookbackCardExporter.bitmapToPng(bitmap) }
                onReady(cardFileName())
            } catch (e: Exception) {
                pendingCardPng = null
                onError(e.message?.takeIf { it.isNotBlank() } ?: "准备保存失败")
            } finally {
                _isExportingCard.value = false
            }
        }
    }

    fun completeSaveCard(uri: Uri, onDone: (Boolean, String) -> Unit) {
        val png = pendingCardPng
        if (png == null) {
            onDone(false, "卡片已失效，请重试")
            return
        }
        viewModelScope.launch {
            _isExportingCard.value = true
            try {
                withContext(Dispatchers.IO) {
                    LookbackCardExporter.writeToUri(appContext, uri, png)
                }
                analyticsRepository.trackExportRecords(
                    range = "week",
                    action = HaEvents.ExportAction.SAVE,
                    format = HaEvents.ExportFormat.LOOKBACK_CARD
                )
                onDone(true, "已保存这一周")
            } catch (e: Exception) {
                onDone(false, e.message?.takeIf { it.isNotBlank() } ?: "保存失败，请稍后重试")
            } finally {
                pendingCardPng = null
                _isExportingCard.value = false
            }
        }
    }

    fun cancelPendingCardSave() {
        pendingCardPng = null
    }
}

@HiltViewModel
class WeekLookbackTipViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {

    val showSundayTip: StateFlow<Boolean> = appPreferences.weekLookbackTipDismissedWeekStart
        .map { dismissedWeekStart -> sundayTipVisible(dismissedWeekStart) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            sundayTipVisible(appPreferences.weekLookbackTipDismissedWeekStart.value)
        )

    private fun sundayTipVisible(dismissedWeekStart: Long): Boolean {
        if (!shouldOfferSundayLookbackTip()) return false
        val (weekStart, _) = getWeekRange(System.currentTimeMillis())
        return dismissedWeekStart != weekStart
    }

    fun dismiss() {
        val (weekStart, _) = getWeekRange(System.currentTimeMillis())
        appPreferences.dismissWeekLookbackTip(weekStart)
    }
}
