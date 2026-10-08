package com.life.mindfulnessapp.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.life.mindfulnessapp.domain.model.DaySlot
import com.life.mindfulnessapp.domain.model.ScheduleItem

@Entity(
    tableName = "schedule_items",
    indices = [Index(value = ["dayKey"])]
)
data class ScheduleItemEntity(
    @PrimaryKey
    val id: String,
    val dayKey: String,
    val title: String,
    val note: String = "",
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val slot: String = DaySlot.FORENOON.name,
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): ScheduleItem {
        val start = startMinute?.coerceIn(0, 1439)
        val end = endMinute?.coerceIn(0, 1439)
        val storedSlot = DaySlot.fromStorage(slot)
        return ScheduleItem(
            id = id,
            dayKey = dayKey,
            title = title,
            note = note,
            startMinute = start,
            endMinute = end,
            slot = start?.let { DaySlot.forStartMinute(it) } ?: storedSlot,
            done = done,
            sortOrder = sortOrder,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(item: ScheduleItem): ScheduleItemEntity {
            val start = item.startMinute?.coerceIn(0, 1439)
            val end = item.endMinute?.coerceIn(0, 1439)
            val resolved = start?.let { DaySlot.forStartMinute(it) } ?: item.slot
            return ScheduleItemEntity(
                id = item.id,
                dayKey = item.dayKey.trim(),
                title = item.title.trim().take(ScheduleItem.TITLE_MAX_CHARS),
                note = item.note.trim().take(ScheduleItem.NOTE_MAX_CHARS),
                startMinute = start,
                endMinute = end,
                slot = resolved.name,
                done = item.done,
                sortOrder = item.sortOrder,
                createdAt = item.createdAt
            )
        }
    }
}
