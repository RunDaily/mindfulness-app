package com.life.mindfulnessapp.util

import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.BatchPickAppRow
import com.life.mindfulnessapp.domain.model.BatchPickSortMode

object BatchPickSorting {

    fun sort(rows: List<BatchPickAppRow>, mode: BatchPickSortMode): List<BatchPickAppRow> =
        when (mode) {
            BatchPickSortMode.Duration -> rows.sortedWith(
                compareByDescending<BatchPickAppRow> { it.usage?.totalSeconds ?: 0L }
                    .thenBy { AppNameSearch.sortKey(it.app.appName) }
                    .thenBy { it.app.appName }
            )
            BatchPickSortMode.Launches -> rows.sortedWith(
                compareByDescending<BatchPickAppRow> { it.usage?.totalLaunches ?: 0 }
                    .thenBy { AppNameSearch.sortKey(it.app.appName) }
                    .thenBy { it.app.appName }
            )
        }

    fun toRows(
        apps: List<AppInfo>,
        usageByPackage: Map<String, com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage>
    ): List<BatchPickAppRow> = apps.map { app ->
        BatchPickAppRow(app, usageByPackage[app.packageName])
    }
}
