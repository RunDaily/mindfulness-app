# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# 保留行号，方便线上 crash 定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin & Coroutines ──────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# ── Hilt / Dagger ────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Room ─────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Retrofit + OkHttp + Gson ──────────────────────────────────────────────────
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
# 保留 API 请求/响应的数据类，避免 Gson 序列化失败
-keep class com.life.mindfulnessapp.data.network.** { *; }

# ── 业务数据类（Room Entity / ViewModel State）──────────────────────────────
-keep class com.life.mindfulnessapp.data.db.entity.** { *; }
-keep class com.life.mindfulnessapp.domain.model.** { *; }
-keep class com.life.mindfulnessapp.ui.**.UiState { *; }

# ── Android 组件（Service、Receiver）────────────────────────────────────────
-keep class com.life.mindfulnessapp.service.** { *; }
-keep class com.life.mindfulnessapp.receiver.** { *; }

# ── Google Play Billing ───────────────────────────────────────────────────────
# Billing Library 自带 consumer ProGuard 规则，以下为补充保护
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**
# 保留 BillingManager 及其回调接口，防止被混淆后回调失效
-keep class com.life.mindfulnessapp.billing.** { *; }

# ── Compose（R8 默认已处理，以下仅兜底）────────────────────────────────────
-dontwarn androidx.compose.**