package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.model.IntentCategory
import com.life.mindfulnessapp.domain.model.IntentPoolBuilder
import com.life.mindfulnessapp.domain.model.IntentPoolCodec
import com.life.mindfulnessapp.domain.model.IntentPoolConfig
import com.life.mindfulnessapp.domain.model.IntentPoolItem
import com.life.mindfulnessapp.domain.model.IntentPoolItemDetail
import com.life.mindfulnessapp.domain.model.IntentPoolSession
import com.life.mindfulnessapp.domain.model.IntentPoolSnapshot
import com.life.mindfulnessapp.domain.model.IntentPoolSort
import com.life.mindfulnessapp.domain.model.IntentPoolTimeScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentPoolRepository @Inject constructor(
    private val appLimitRepository: AppLimitRepository,
    private val usageRecordRepository: UsageRecordRepository
) {

    suspend fun loadSnapshot(
        packageName: String,
        timeScope: IntentPoolTimeScope = IntentPoolTimeScope.All,
        sort: IntentPoolSort = IntentPoolSort.Duration,
        categoryFilterId: String? = null
    ): IntentPoolSnapshot {
        val limit = appLimitRepository.getAppLimit(packageName) ?: return emptySnapshot(timeScope, sort)
        val synced = ensureSyncedConfig(limit)
        val raw = IntentPoolBuilder.aggregateRawRows(
            usageRecordRepository.getFullPurposeStats(packageName)
        )
        val snapshot = IntentPoolBuilder.buildSnapshot(
            config = synced,
            rawAggregates = raw,
            timeScope = timeScope,
            sort = sort
        )
        if (categoryFilterId == null) return snapshot
        val filtered = when (categoryFilterId) {
            UNCategorizedFilterId -> snapshot.items.filter { it.category == null }
            AllFilterId -> snapshot.items
            else -> snapshot.items.filter { it.category?.id == categoryFilterId }
        }
        return snapshot.copy(items = filtered)
    }

    suspend fun loadItemDetail(packageName: String, entryId: String): IntentPoolItemDetail? {
        val snapshot = loadSnapshot(packageName)
        val item = snapshot.items.firstOrNull { it.entry.id == entryId }
            ?: loadHiddenItem(packageName, entryId)
            ?: return null
        val records = usageRecordRepository.getRecordsForPurposes(
            packageName = packageName,
            purposes = item.aliases,
            limit = 120
        )
        return IntentPoolItemDetail(
            item = item,
            sessions = records.map { it.toPoolSession() }
        )
    }

    suspend fun assignCategory(packageName: String, entryId: String, categoryId: String?) {
        mutateConfig(packageName) { config ->
            val now = System.currentTimeMillis()
            config.copy(
                entries = config.entries.map { entry ->
                    if (entry.id != entryId) entry
                    else entry.copy(categoryId = categoryId, updatedAt = now)
                }
            )
        }
    }

    suspend fun batchAssignCategory(
        packageName: String,
        entryIds: Set<String>,
        categoryId: String?
    ) {
        if (entryIds.isEmpty()) return
        mutateConfig(packageName) { config ->
            val now = System.currentTimeMillis()
            config.copy(
                entries = config.entries.map { entry ->
                    if (entry.id !in entryIds) entry
                    else entry.copy(categoryId = categoryId, updatedAt = now)
                }
            )
        }
    }

    suspend fun renameEntry(packageName: String, entryId: String, displayName: String) {
        val name = IntentPoolCodec.normalizePurpose(displayName)
        if (name.isEmpty()) return
        mutateConfig(packageName) { config ->
            val now = System.currentTimeMillis()
            config.copy(
                entries = config.entries.map { entry ->
                    if (entry.id != entryId) entry
                    else entry.copy(displayName = name, updatedAt = now)
                }
            )
        }
    }

    suspend fun hideEntry(packageName: String, entryId: String) {
        mutateConfig(packageName) { config ->
            val now = System.currentTimeMillis()
            config.copy(
                entries = config.entries.map { entry ->
                    if (entry.id != entryId) entry
                    else entry.copy(isHidden = true, updatedAt = now)
                }
            )
        }
    }

    suspend fun unhideEntry(packageName: String, entryId: String) {
        mutateConfig(packageName) { config ->
            val now = System.currentTimeMillis()
            config.copy(
                entries = config.entries.map { entry ->
                    if (entry.id != entryId) entry
                    else entry.copy(isHidden = false, updatedAt = now)
                }
            )
        }
    }

    suspend fun createCategory(
        packageName: String,
        name: String,
        emoji: String? = null
    ): IntentCategory? {
        val trimmed = name.trim().take(16)
        if (trimmed.isEmpty()) return null
        var created: IntentCategory? = null
        mutateConfig(packageName) { config ->
            if (config.categories.size >= IntentPoolCodec.MAX_CATEGORIES) return@mutateConfig config
            val category = IntentCategory(
                id = IntentPoolCodec.newCategoryId(),
                name = trimmed,
                emoji = emoji?.trim()?.takeIf { it.isNotEmpty() },
                sortOrder = config.categories.size
            )
            created = category
            config.copy(categories = config.categories + category)
        }
        return created
    }

    suspend fun updateCategory(
        packageName: String,
        categoryId: String,
        name: String,
        emoji: String?
    ) {
        val trimmed = name.trim().take(16)
        if (trimmed.isEmpty()) return
        mutateConfig(packageName) { config ->
            config.copy(
                categories = config.categories.map { cat ->
                    if (cat.id != categoryId) cat
                    else cat.copy(
                        name = trimmed,
                        emoji = emoji?.trim()?.takeIf { it.isNotEmpty() }
                    )
                }
            )
        }
    }

    suspend fun deleteCategory(packageName: String, categoryId: String) {
        mutateConfig(packageName) { config ->
            val now = System.currentTimeMillis()
            config.copy(
                categories = config.categories.filterNot { it.id == categoryId },
                entries = config.entries.map { entry ->
                    if (entry.categoryId != categoryId) entry
                    else entry.copy(categoryId = null, updatedAt = now)
                }
            )
        }
    }

    suspend fun applyCategoryTemplate(packageName: String) {
        mutateConfig(packageName) { config ->
            if (config.categories.isNotEmpty()) return@mutateConfig config
            val categories = IntentPoolCodec.categoryTemplates.mapIndexed { index, (name, emoji) ->
                IntentCategory(
                    id = IntentPoolCodec.newCategoryId(),
                    name = name,
                    emoji = emoji,
                    sortOrder = index
                )
            }
            config.copy(categories = categories)
        }
    }

    /**
     * 将 [sourceEntryId] 并入 [targetEntryId]。
     * 若 [newDisplayName] 非空则更新目标展示名。
     */
    suspend fun mergeEntries(
        packageName: String,
        sourceEntryId: String,
        targetEntryId: String,
        newDisplayName: String? = null
    ): Boolean {
        if (sourceEntryId == targetEntryId) return false
        var ok = false
        mutateConfig(packageName) { config ->
            val source = config.entries.find { it.id == sourceEntryId } ?: return@mutateConfig config
            val target = config.entries.find { it.id == targetEntryId } ?: return@mutateConfig config
            val now = System.currentTimeMillis()
            val remappedAliases = config.aliases.map { alias ->
                if (alias.entryId == sourceEntryId) alias.copy(entryId = targetEntryId)
                else alias
            }.distinctBy { it.rawPurpose }
            val updatedTargetName = newDisplayName
                ?.let { IntentPoolCodec.normalizePurpose(it) }
                ?.takeIf { it.isNotEmpty() }
                ?: target.displayName
            val mergedCategory = target.categoryId ?: source.categoryId
            ok = true
            config.copy(
                entries = config.entries
                    .filterNot { it.id == sourceEntryId }
                    .map { entry ->
                        if (entry.id != targetEntryId) entry
                        else entry.copy(
                            displayName = updatedTargetName,
                            categoryId = mergedCategory,
                            updatedAt = now
                        )
                    },
                aliases = remappedAliases
            )
        }
        return ok
    }

    suspend fun getConfig(packageName: String): IntentPoolConfig {
        val limit = appLimitRepository.getAppLimit(packageName) ?: return IntentPoolConfig()
        return ensureSyncedConfig(limit)
    }

    private suspend fun ensureSyncedConfig(limit: AppLimitEntity): IntentPoolConfig {
        val decoded = IntentPoolCodec.decode(limit.intentPoolJson)
        val raw = IntentPoolBuilder.aggregateRawRows(
            usageRecordRepository.getFullPurposeStats(limit.packageName)
        )
        val synced = IntentPoolBuilder.syncConfigWithRawPurposes(decoded, raw)
        if (synced != decoded) {
            appLimitRepository.saveAppLimit(
                limit.copy(intentPoolJson = IntentPoolCodec.encode(synced))
            )
        }
        return synced
    }

    private suspend fun mutateConfig(
        packageName: String,
        transform: (IntentPoolConfig) -> IntentPoolConfig
    ) {
        val limit = appLimitRepository.getAppLimit(packageName) ?: return
        val synced = ensureSyncedConfig(limit)
        val next = transform(synced)
        if (next == synced) return
        appLimitRepository.saveAppLimit(
            limit.copy(intentPoolJson = IntentPoolCodec.encode(next))
        )
    }

    private suspend fun loadHiddenItem(packageName: String, entryId: String): IntentPoolItem? {
        val limit = appLimitRepository.getAppLimit(packageName) ?: return null
        val config = IntentPoolCodec.decode(limit.intentPoolJson)
        val entry = config.entries.find { it.id == entryId && it.isHidden } ?: return null
        val raw = IntentPoolBuilder.aggregateRawRows(
            usageRecordRepository.getFullPurposeStats(packageName)
        )
        return IntentPoolBuilder.buildSnapshot(
            config = config,
            rawAggregates = raw
        ).items.firstOrNull { it.entry.id == entryId }
            ?: run {
                val aliases = config.aliases.filter { it.entryId == entryId }.map { it.rawPurpose }
                val stats = aliases.mapNotNull { alias ->
                    raw.find { it.purpose == alias }
                }
                if (stats.isEmpty()) return null
                IntentPoolItem(
                    entry = entry,
                    aliases = aliases,
                    totalSeconds = stats.sumOf { it.totalSeconds },
                    todaySeconds = stats.sumOf { it.todaySeconds },
                    useCount = stats.sumOf { it.useCount },
                    firstUsedAt = stats.minOf { it.firstUsedAt },
                    lastUsedAt = stats.maxOf { it.lastUsedAt },
                    shareOfMindful = 0f,
                    category = entry.categoryId?.let { id -> config.categories.find { it.id == id } }
                )
            }
    }

    private fun UsageRecordEntity.toPoolSession() = IntentPoolSession(
        recordId = id,
        purpose = purpose.orEmpty(),
        startTime = startTime,
        endTime = endTime,
        durationSeconds = durationSeconds
    )

    private fun emptySnapshot(
        timeScope: IntentPoolTimeScope,
        sort: IntentPoolSort
    ) = IntentPoolSnapshot(
        items = emptyList(),
        hiddenCount = 0,
        uncategorizedCount = 0,
        categoryBreakdown = emptyList(),
        totalMindfulSeconds = 0L,
        totalEntryCount = 0,
        timeScope = timeScope,
        sort = sort
    )

    companion object {
        const val AllFilterId = "__all__"
        const val UNCategorizedFilterId = "__uncategorized__"
    }
}
