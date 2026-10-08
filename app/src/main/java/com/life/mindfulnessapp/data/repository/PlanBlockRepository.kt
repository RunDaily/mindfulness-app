package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.db.dao.PlanBlockDao
import com.life.mindfulnessapp.data.db.entity.PlanBlockEntity
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindowConflict
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanBlockRepository @Inject constructor(
    private val dao: PlanBlockDao
) {
    fun observeAll(): Flow<List<PlanBlock>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAllOnce(): List<PlanBlock> =
        dao.getAllOnce().map { it.toDomain() }

    suspend fun getById(id: String): PlanBlock? =
        dao.getById(id)?.toDomain()

    /**
     * 保存计划块。若与其他计划时段冲突，返回 conflict 且不写入。
     */
    suspend fun save(block: PlanBlock): SaveResult {
        val title = block.title.trim()
        if (title.isEmpty()) return SaveResult.EmptyTitle
        if (block.lockScope.includesSpecific && block.packageNames.isEmpty()) {
            return SaveResult.EmptyPackages
        }

        val existing = getAllOnce()
        val conflict = PlanBlockPolicy.conflictWith(block, existing, excludeId = block.id)
        if (conflict != null) return SaveResult.Conflict(conflict)

        val sortOrder = if (existing.any { it.id == block.id }) {
            block.sortOrder
        } else {
            dao.getMaxSortOrder() + 1
        }
        dao.upsert(
            PlanBlockEntity.fromDomain(
                block.copy(
                    title = title.take(PlanBlock.TITLE_MAX_CHARS),
                    sortOrder = sortOrder,
                    packageNames = block.packageNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                )
            )
        )
        return SaveResult.Ok
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun activeForPackage(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PlanBlock? = PlanBlockPolicy.activeForPackage(getAllOnce(), packageName, nowMillis)

    sealed class SaveResult {
        data object Ok : SaveResult()
        data object EmptyTitle : SaveResult()
        data object EmptyPackages : SaveResult()
        data class Conflict(val type: PeriodWindowConflict) : SaveResult()
    }
}
