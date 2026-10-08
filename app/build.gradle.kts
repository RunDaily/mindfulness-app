import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val wxAppId: String = localProperties.getProperty("WX_APP_ID", "")

val keystoreFilePath: String = localProperties.getProperty("KEYSTORE_FILE", "")
val keystorePassword: String = localProperties.getProperty("KEYSTORE_PASSWORD", "")
val keyAliasName: String = localProperties.getProperty("KEY_ALIAS", "")
val keyPasswordValue: String = localProperties.getProperty("KEY_PASSWORD", keystorePassword)

android {
    namespace = "com.life.mindfulnessapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.life.mindfulnessapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // website = 官网 APK 渠道；play = 商店渠道（评分/Billing 行为分流）
        buildConfigField("String", "DISTRIBUTION_CHANNEL", "\"website\"")
        // 微信开放平台 · 移动应用 AppID（APP 支付）
        buildConfigField("String", "WX_APP_ID", "\"${wxAppId.replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        create("release") {
            if (keystoreFilePath.isNotBlank()) {
                storeFile = rootProject.file(keystoreFilePath)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
        disable += setOf("NullSafeMutableLiveData")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.reorderable)

    // 汉字拼音：应用名字母序 + 简拼/全拼搜索（如 xhs → 小红书）
    implementation(libs.pinyin4j)
    implementation(libs.zxing.core)
    implementation(libs.wechat.sdk.android)
    // 系统手机号选择器（自助领邀请码）；无 GMS 时降级为手动输入
    implementation(libs.play.services.auth)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // MPAndroidChart
    implementation(libs.mpandroidchart)

    // Retrofit + OkHttp（网络同步）
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // WorkManager + Hilt-WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // LocalBroadcastManager（在 App 内进程内发送/接收广播，比系统广播更轻量安全）
    implementation(libs.androidx.localbroadcastmanager)

    // Google Play Billing
    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
