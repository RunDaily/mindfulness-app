package com.life.mindfulnessapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.life.mindfulnessapp.domain.model.PeriodDays
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanLockScope
import com.life.mindfulnessapp.domain.model.PlanPackagesCodec
import com.life.mindfulnessapp.domain.model.PlanScene

@Entity(tableName = "plan_blocks")
data class PlanBlockEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int = PeriodDays.EVERY_DAY,
    val enabled: Boolean = true,
    /** JSON 字符串数组，见 [PlanPackagesCodec]；[lockScope]=MONITORED 时可为空 */
    val packagesJson: String = "[]",
    /** [PlanLockScope.name] */
    val lockScope: String = PlanLockScope.MONITORED.name,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val scene: String = PlanScene.LIFE.name,
    val why: String = ""
) {
    fun toDomain(): PlanBlock {
        val (s, e) = PeriodWindow.normalizeRange(startMinute, endMinute)
        val scope = PlanLockScope.fromStorage(lockScope)
        val packages = PlanPackagesCodec.decode(packagesJson)
        // 旧数据无 scope 列时默认 SPECIFIC（有包名）或 MONITORED（无包名不应出现，保守 SPECIFIC）
        val resolvedScope = when {
            lockScope.isBlank() && packages.isNotEmpty() -> PlanLockScope.SPECIFIC
            else -> scope
        }
        return PlanBlock(
            id = id,
            title = title,
            startMinute = s,
            endMinute = e,
            daysMask = daysMask and PeriodDays.EVERY_DAY,
            enabled = enabled,
            packageNames = packages,
            lockScope = resolvedScope,
            sortOrder = sortOrder,
            createdAt = createdAt,
            scene = PlanScene.fromStorage(scene),
            why = why
        )
    }

    companion object {
        fun fromDomain(block: PlanBlock): PlanBlockEntity {
            val (s, e) = PeriodWindow.normalizeRange(block.startMinute, block.endMinute)
            val packages = when (block.lockScope) {
                PlanLockScope.MONITORED -> emptyList()
                PlanLockScope.SPECIFIC,
                PlanLockScope.BOTH -> block.packageNames
            }
            return PlanBlockEntity(
                id = block.id,
                title = block.title.trim().take(PlanBlock.TITLE_MAX_CHARS),
                startMinute = s,
                endMinute = e,
                daysMask = block.daysMask and PeriodDays.EVERY_DAY,
                enabled = block.enabled,
                packagesJson = PlanPackagesCodec.encode(packages),
                lockScope = block.lockScope.name,
                sortOrder = block.sortOrder,
                createdAt = block.createdAt,
                scene = block.scene.name,
                why = block.why.trim().take(PlanBlock.WHY_MAX_CHARS)
            )
        }
    }
}
