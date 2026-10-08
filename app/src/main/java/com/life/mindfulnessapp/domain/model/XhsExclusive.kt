package com.life.mindfulnessapp.domain.model

/**
 * 小红书独占能力（包级特判）。
 * 其它 App 即使库里有同名开关字段，运行时也不会生效。
 */
object XhsExclusive {
    const val PACKAGE_NAME = "com.xingin.xhs"

    fun isXhsPackage(packageName: String?): Boolean =
        packageName == PACKAGE_NAME
}
