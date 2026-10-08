package com.life.mindfulnessapp.domain.model

/**
 * 日限硬挡、次数页附注、今日页共用的时长账。
 *
 * **监控生效后只用心锚会话**（含进行中会话的加成，由调用方并入 [recordSeconds]）。
 * 门口盖层期间目标 App 对系统仍常算前台，UsageStats 会虚高，不得再与系统取 max。
 * 加入前 / 未监控的展示另走系统 UsageStats，不经过本对象混算。
 */
object DailyCapFacts {

    /** 监控后已用秒数：仅心锚记录。 */
    fun usedSeconds(recordSeconds: Long): Long =
        recordSeconds.coerceAtLeast(0L)

    /**
     * @deprecated 监控后禁止混入系统秒数；双参形式仅保留编译兼容，**忽略** [systemSeconds]。
     */
    @Deprecated(
        message = "监控后只用 recordSeconds；系统用量不得参与日限账",
        replaceWith = ReplaceWith("DailyCapFacts.usedSeconds(recordSeconds)")
    )
    fun usedSeconds(recordSeconds: Long, systemSeconds: Long): Long =
        usedSeconds(recordSeconds)

    fun exhausted(usedSeconds: Long, limitSeconds: Long): Boolean =
        limitSeconds > 0L && usedSeconds >= limitSeconds

    /** 整分钟。超出不足一分钟时仍落在当前分钟，刚好满就是 N / N。 */
    fun wholeMinutes(seconds: Long): Int =
        (seconds.coerceAtLeast(0L) / 60L).toInt()

    /**
     * 次数页「时长还剩」。
     * 没开日限，或时长已经用尽，不写这一行。
     */
    fun remainingMinutes(usedSeconds: Long, limitSeconds: Long): Int? {
        if (limitSeconds <= 0L || exhausted(usedSeconds, limitSeconds)) return null
        val remain = wholeMinutes(limitSeconds) - wholeMinutes(usedSeconds)
        return remain.takeIf { it > 0 }
    }
}
