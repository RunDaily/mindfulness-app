package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.db.entity.PurposeStatFullRow

object IntentPoolBuilder {

    data class RawPurposeAggregate(
        val purpose: String,
        val useCount: Int,
        val totalSeconds: Long,
        val todaySeconds: Long,
        val firstUsedAt: Long,
        val lastUsedAt: Long
    )

    fun aggregateRawRows(rows: List<PurposeStatFullRow>): List<RawPurposeAggregate> =
        rows.map { row ->
            RawPurposeAggregate(
                purpose = IntentPoolCodec.normalizePurpose(row.purpose),
                useCount = row.useCount,
                totalSeconds = row.totalSeconds,
                todaySeconds = row.todaySeconds,
                firstUsedAt = row.firstUsedAt,
                lastUsedAt = row.lastUsedAt
            )
        }.filter { it.purpose.isNotEmpty() }

    /**
     * 将 DB 聚合与配置层 alias 合并，补齐缺失条目。
     * @return 可能更新后的 config（含新 auto-entry）
     */
    fun syncConfigWithRawPurposes(
        config: IntentPoolConfig,
        rawAggregates: List<RawPurposeAggregate>,
        nowMs: Long = System.currentTimeMillis()
    ): IntentPoolConfig {
        val aliasByRaw = config.aliases.associateBy { it.rawPurpose }
        val entriesById = config.entries.associateBy { it.id }.toMutableMap()
        val aliases = config.aliases.toMutableList()
        var changed = false

        rawAggregates.forEach { raw ->
            if (aliasByRaw.containsKey(raw.purpose)) return@forEach
            val entryId = IntentPoolCodec.newEntryId()
            entriesById[entryId] = IntentEntry(
                id = entryId,
                displayName = raw.purpose,
                createdAt = raw.firstUsedAt.coerceAtMost(nowMs),
                updatedAt = nowMs
            )
            aliases += IntentAlias(rawPurpose = raw.purpose, entryId = entryId)
            changed = true
        }

        if (!changed) return config
        return config.copy(
            entries = entriesById.values.sortedBy { it.createdAt },
            aliases = aliases.distinctBy { it.rawPurpose }
        )
    }

    fun buildSnapshot(
        config: IntentPoolConfig,
        rawAggregates: List<RawPurposeAggregate>,
        timeScope: IntentPoolTimeScope = IntentPoolTimeScope.All,
        sort: IntentPoolSort = IntentPoolSort.Duration
    ): IntentPoolSnapshot {
        val categoriesById = config.categories.associateBy { it.id }
        val entryById = config.entries.associateBy { it.id }
        val rawByPurpose = rawAggregates.associateBy { it.purpose }

        val grouped = config.aliases
            .groupBy { it.entryId }
            .mapNotNull { (entryId, aliasGroup) ->
                val entry = entryById[entryId] ?: return@mapNotNull null
                val aliasTexts = aliasGroup.map { it.rawPurpose }.distinct()
                val stats = aliasTexts.mapNotNull { rawByPurpose[it] }
                if (stats.isEmpty() && entry.isHidden) {
                    return@mapNotNull entry to IntentPoolItem(
                        entry = entry,
                        aliases = aliasTexts,
                        totalSeconds = 0L,
                        todaySeconds = 0L,
                        useCount = 0,
                        firstUsedAt = entry.createdAt,
                        lastUsedAt = entry.updatedAt,
                        shareOfMindful = 0f,
                        category = entry.categoryId?.let { categoriesById[it] }
                    )
                }
                if (stats.isEmpty()) return@mapNotNull null

                val totalSeconds = stats.sumOf { it.totalSeconds }
                val todaySeconds = stats.sumOf { it.todaySeconds }
                val useCount = stats.sumOf { it.useCount }
                val firstUsedAt = stats.minOf { it.firstUsedAt }
                val lastUsedAt = stats.maxOf { it.lastUsedAt }

                entry to IntentPoolItem(
                    entry = entry,
                    aliases = aliasTexts,
                    totalSeconds = totalSeconds,
                    todaySeconds = todaySeconds,
                    useCount = useCount,
                    firstUsedAt = firstUsedAt,
                    lastUsedAt = lastUsedAt,
                    shareOfMindful = 0f,
                    category = entry.categoryId?.let { categoriesById[it] }
                )
            }

        val visibleItems = grouped
            .map { it.second }
            .filter { !it.entry.isHidden }
        val hiddenCount = grouped.count { it.second.entry.isHidden }

        val scopeSeconds = { item: IntentPoolItem ->
            when (timeScope) {
                IntentPoolTimeScope.All -> item.totalSeconds
                IntentPoolTimeScope.Today -> item.todaySeconds
            }
        }

        val totalMindfulSeconds = visibleItems.sumOf { scopeSeconds(it).toInt() }.toLong()
        val itemsWithShare = visibleItems.map { item ->
            val scoped = scopeSeconds(item)
            item.copy(
                shareOfMindful = if (totalMindfulSeconds > 0L) {
                    scoped.toFloat() / totalMindfulSeconds.toFloat()
                } else {
                    0f
                }
            )
        }

        val sortedItems = when (sort) {
            IntentPoolSort.Duration -> itemsWithShare.sortedByDescending { scopeSeconds(it) }
            IntentPoolSort.Count -> itemsWithShare.sortedByDescending { it.useCount }
            IntentPoolSort.Recent -> itemsWithShare.sortedByDescending { it.lastUsedAt }
        }

        val categoryBreakdown = buildCategoryBreakdown(
            items = itemsWithShare,
            categories = config.categories,
            totalMindfulSeconds = totalMindfulSeconds,
            timeScope = timeScope
        )

        return IntentPoolSnapshot(
            items = sortedItems,
            hiddenCount = hiddenCount,
            uncategorizedCount = sortedItems.count { it.category == null },
            categoryBreakdown = categoryBreakdown,
            totalMindfulSeconds = totalMindfulSeconds,
            totalEntryCount = sortedItems.size,
            timeScope = timeScope,
            sort = sort
        )
    }

    private fun buildCategoryBreakdown(
        items: List<IntentPoolItem>,
        categories: List<IntentCategory>,
        totalMindfulSeconds: Long,
        timeScope: IntentPoolTimeScope
    ): List<CategoryBreakdown> {
        val scopeSeconds = { item: IntentPoolItem ->
            when (timeScope) {
                IntentPoolTimeScope.All -> item.totalSeconds
                IntentPoolTimeScope.Today -> item.todaySeconds
            }
        }
        val grouped = items.groupBy { it.category?.id }
        val breakdown = mutableListOf<CategoryBreakdown>()

        categories.sortedBy { it.sortOrder }.forEach { cat ->
            val catItems = grouped[cat.id].orEmpty()
            val seconds = catItems.sumOf { scopeSeconds(it).toInt() }.toLong()
            breakdown += CategoryBreakdown(
                category = cat,
                totalSeconds = seconds,
                entryCount = catItems.size,
                share = if (totalMindfulSeconds > 0L) seconds.toFloat() / totalMindfulSeconds else 0f
            )
        }

        val uncategorized = grouped[null].orEmpty()
        if (uncategorized.isNotEmpty()) {
            val seconds = uncategorized.sumOf { scopeSeconds(it).toInt() }.toLong()
            breakdown += CategoryBreakdown(
                category = null,
                totalSeconds = seconds,
                entryCount = uncategorized.size,
                share = if (totalMindfulSeconds > 0L) seconds.toFloat() / totalMindfulSeconds else 0f
            )
        }

        return breakdown.sortedByDescending { it.totalSeconds }
    }

    fun toRecentPurposeStats(snapshot: IntentPoolSnapshot, limit: Int = 20): List<RecentPurposeStat> =
        snapshot.items
            .take(limit)
            .map { item ->
                RecentPurposeStat(
                    purpose = item.entry.displayName,
                    useCount = item.useCount,
                    lastUsedAt = item.lastUsedAt,
                    todaySeconds = item.todaySeconds
                )
            }
}
