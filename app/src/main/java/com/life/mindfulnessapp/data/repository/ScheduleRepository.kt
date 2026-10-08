package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.db.dao.ScheduleItemDao
import com.life.mindfulnessapp.data.db.entity.ScheduleItemEntity
import com.life.mindfulnessapp.domain.model.DaySlot
import com.life.mindfulnessapp.domain.model.ScheduleItem
import com.life.mindfulnessapp.domain.model.ScheduleSlotSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val dao: ScheduleItemDao
) {
    fun observeDay(dayKey: String): Flow<List<ScheduleItem>> =
        dao.observeByDay(dayKey).map { list -> list.map { it.toDomain() } }

    suspend fun getDayOnce(dayKey: String): List<ScheduleItem> =
        dao.getByDay(dayKey).map { it.toDomain() }

    fun observeDaySections(dayKey: String): Flow<List<ScheduleSlotSection>> =
        observeDay(dayKey).map { items -> groupIntoSections(dayKey, items) }

    suspend fun getById(id: String): ScheduleItem? =
        dao.getById(id)?.toDomain()

    suspend fun save(item: ScheduleItem): SaveResult {
        val title = item.title.trim()
        if (title.isEmpty()) return SaveResult.EmptyTitle
        if (item.dayKey.isBlank()) return SaveResult.BadDay

        val start = item.startMinute
        val end = item.endMinute
        if (start != null && end != null && end <= start) {
            return SaveResult.BadRange
        }

        val existing = dao.getById(item.id)
        val sortOrder = existing?.sortOrder ?: (dao.getMaxSortOrder(item.dayKey) + 1)
        val resolvedSlot = start?.let { DaySlot.forStartMinute(it) } ?: item.slot

        dao.upsert(
            ScheduleItemEntity.fromDomain(
                item.copy(
                    title = title.take(ScheduleItem.TITLE_MAX_CHARS),
                    note = item.note.trim().take(ScheduleItem.NOTE_MAX_CHARS),
                    slot = resolvedSlot,
                    sortOrder = sortOrder
                )
            )
        )
        return SaveResult.Ok
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun setDone(id: String, done: Boolean) = dao.setDone(id, done)

    sealed class SaveResult {
        data object Ok : SaveResult()
        data object EmptyTitle : SaveResult()
        data object BadDay : SaveResult()
        data object BadRange : SaveResult()
    }

    companion object {
        private val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")

        private fun dayFmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun todayKey(): String = dayFmt().format(Date())

        fun shiftDayKey(dayKey: String, deltaDays: Int): String {
            val cal = Calendar.getInstance()
            cal.time = dayFmt().parse(dayKey) ?: Date()
            cal.add(Calendar.DAY_OF_YEAR, deltaDays)
            return dayFmt().format(cal.time)
        }

        /** 「今天 · 周二」/ 「3月28日 · 周六」 */
        fun dayLabel(dayKey: String, todayKey: String = todayKey()): String {
            val cal = Calendar.getInstance()
            cal.time = dayFmt().parse(dayKey) ?: return dayKey
            val week = "周${weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]}"
            return when (dayKey) {
                todayKey -> "今天 · $week"
                shiftDayKey(todayKey, -1) -> "昨天 · $week"
                shiftDayKey(todayKey, 1) -> "明天 · $week"
                else -> {
                    val m = cal.get(Calendar.MONTH) + 1
                    val d = cal.get(Calendar.DAY_OF_MONTH)
                    "${m}月${d}日 · $week"
                }
            }
        }

        fun groupIntoSections(
            dayKey: String,
            items: List<ScheduleItem>,
            nowMinute: Int = DaySlot.currentMinuteOfDay(),
            todayKey: String = todayKey()
        ): List<ScheduleSlotSection> {
            val currentSlot = if (dayKey == todayKey) DaySlot.containingNow(nowMinute) else null
            val grouped = items.groupBy { it.resolvedSlot() }
            return DaySlot.entries.map { slot ->
                ScheduleSlotSection(
                    slot = slot,
                    items = grouped[slot].orEmpty(),
                    isCurrent = slot == currentSlot
                )
            }
        }
    }
}
