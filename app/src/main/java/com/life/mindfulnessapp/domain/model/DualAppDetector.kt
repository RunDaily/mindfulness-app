package com.life.mindfulnessapp.domain.model

/**
 * 应用分身 / 双开启发式检测。
 *
 * - 异包名分身：同名 / 分身后缀 / 常见双开包名模式
 * - 同包名系统分身（华为 128、小米 999、三星 Dual Messenger 95）：见 [com.life.mindfulnessapp.util.OemDualSpace]
 */
object DualAppDetector {

    data class LauncherApp(
        val packageName: String,
        val appName: String
    )

    private val CLONE_LABEL_SUFFIX = Regex(
        """[\s\-_.（(]*?(分身|双开|克隆|副号|Clone|Dual|Twin|II|2)[）)]?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val CLONE_PACKAGE_SUFFIX = Regex(
        """[.\-_]*(clone|dual|twin|mult|parallel|appsclone|appclone|dualapp|second|fenShen|fenshen)[a-z0-9._\-]*$""",
        RegexOption.IGNORE_CASE
    )

    fun normalizeLabel(label: String): String =
        label.trim()
            .replace(Regex("""\s+"""), "")
            .lowercase()

    /** [other] 是否像 [primary] 的分身 */
    fun isLikelyClone(
        primaryPackage: String,
        primaryLabel: String,
        otherPackage: String,
        otherLabel: String
    ): Boolean {
        if (primaryPackage == otherPackage) return false
        val pLabel = normalizeLabel(primaryLabel)
        val oLabel = normalizeLabel(otherLabel)
        if (pLabel.isEmpty() || oLabel.isEmpty()) return false

        if (pLabel == oLabel) return true

        val strippedOther = CLONE_LABEL_SUFFIX.replace(otherLabel.trim(), "").let { normalizeLabel(it) }
        if (strippedOther.isNotEmpty() && strippedOther == pLabel) return true

        val strippedPrimary = CLONE_LABEL_SUFFIX.replace(primaryLabel.trim(), "").let { normalizeLabel(it) }
        if (strippedPrimary.isNotEmpty() && strippedPrimary == oLabel) return true

        if (packageLooksLikeCloneOf(otherPackage, primaryPackage)) return true
        return false
    }

    fun findClonePackages(
        installed: List<LauncherApp>,
        primaryPackage: String,
        primaryLabel: String
    ): List<String> =
        installed
            .filter {
                isLikelyClone(
                    primaryPackage = primaryPackage,
                    primaryLabel = primaryLabel,
                    otherPackage = it.packageName,
                    otherLabel = it.appName
                )
            }
            .map { it.packageName }
            .distinct()

    /**
     * 若 [pkg] 像某个已安装 App 的分身，返回主包名（优先选「更不像分身」的那个）。
     */
    fun findPrimaryPackage(
        installed: List<LauncherApp>,
        pkg: String,
        label: String
    ): String? {
        val self = installed.firstOrNull { it.packageName == pkg } ?: LauncherApp(pkg, label)
        val candidates = installed.filter {
            it.packageName != pkg &&
                isLikelyClone(it.packageName, it.appName, self.packageName, self.appName)
        }
        if (candidates.isEmpty()) return null
        return candidates.minWith(
            compareBy<LauncherApp> { cloneLikenessScore(it.packageName, it.appName) }
                .thenBy { it.packageName.length }
                .thenBy { it.packageName }
        ).packageName
    }

    fun isSuspectedCloneLabel(label: String): Boolean =
        CLONE_LABEL_SUFFIX.containsMatchIn(label.trim())

    fun isSuspectedClonePackage(packageName: String): Boolean =
        CLONE_PACKAGE_SUFFIX.containsMatchIn(packageName)

    private fun packageLooksLikeCloneOf(clonePkg: String, primaryPkg: String): Boolean {
        if (clonePkg == primaryPkg) return false
        if (clonePkg.startsWith(primaryPkg)) {
            val suffix = clonePkg.removePrefix(primaryPkg)
            if (suffix.isNotEmpty() && CLONE_PACKAGE_SUFFIX.matches(suffix)) return true
            if (suffix.isNotEmpty() && CLONE_PACKAGE_SUFFIX.containsMatchIn(suffix)) return true
        }
        if (isSuspectedClonePackage(clonePkg) && !isSuspectedClonePackage(primaryPkg)) {
            val baseClone = CLONE_PACKAGE_SUFFIX.replace(clonePkg, "")
            if (baseClone.isNotEmpty() &&
                (primaryPkg == baseClone || primaryPkg.startsWith(baseClone) || baseClone.startsWith(primaryPkg))
            ) {
                return true
            }
        }
        return false
    }

    private fun cloneLikenessScore(packageName: String, label: String): Int {
        var score = 0
        if (isSuspectedClonePackage(packageName)) score += 2
        if (isSuspectedCloneLabel(label)) score += 2
        return score
    }
}
