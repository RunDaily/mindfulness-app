package com.life.mindfulnessapp.ui.profile

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.life.mindfulnessapp.ui.theme.themeChrome

private const val MANUAL_URL =
    "file:///android_asset/product_manual/manual.html"

/**
 * 产品说明书：原生顶栏 + 本地单页 H5（目录抽屉与章切换均在 H5 内，避免手势冲突）。
 */
@Composable
fun ProductManualScreen(
    onNavigateBack: () -> Unit = {}
) {
    val chrome = themeChrome()
    val themeAttr = if (chrome.isDark) "dark" else "light"
    val bgArgb = chrome.bg.toArgb()
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember { mutableStateOf("心锚是什么") }
    var drawerOpen by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun eval(js: String) {
        webViewRef.value?.evaluateJavascript(js, null)
    }

    fun applyTheme(view: WebView) {
        view.evaluateJavascript(
            "document.documentElement.setAttribute('data-theme','$themeAttr');",
            null
        )
    }

    fun leave() {
        if (drawerOpen) {
            eval("HA.closeDrawer();")
        } else {
            onNavigateBack()
        }
    }

    BackHandler(onBack = ::leave)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = chrome.bg,
        // 顶栏自行 statusBarsPadding；避免与系统 inset 叠成「下沉」
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            QuietTopBar(
                title = pageTitle,
                textPrimary = chrome.textPrimary,
                textSecondary = chrome.textSecondary,
                onBack = ::leave,
                trailing = {
                    IconButton(onClick = { eval("HA.openDrawer();") }) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "目录",
                            tint = chrome.textSecondary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { context ->
                @SuppressLint("SetJavaScriptEnabled")
                WebView(context).apply {
                    setBackgroundColor(bgArgb)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = false
                        allowFileAccess = true
                        allowContentAccess = false
                        displayZoomControls = false
                        builtInZoomControls = false
                        setSupportZoom(false)
                    }
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onChapter(id: String, title: String) {
                                mainHandler.post {
                                    pageTitle = title
                                }
                            }

                            @JavascriptInterface
                            fun onDrawer(open: Boolean) {
                                mainHandler.post {
                                    drawerOpen = open
                                }
                            }
                        },
                        "ManualBridge"
                    )
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url?.toString().orEmpty()
                            if (url.startsWith("javascript:")) return false
                            if (url.startsWith("file:///android_asset/product_manual/manual.html")) {
                                return false
                            }
                            return true
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            url: String
                        ): Boolean {
                            if (url.startsWith("javascript:")) return false
                            if (url.startsWith("file:///android_asset/product_manual/manual.html")) {
                                return false
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            applyTheme(view)
                            view.evaluateJavascript("HA.show('ch01');", null)
                            view.setBackgroundColor(AndroidColor.TRANSPARENT)
                        }
                    }
                    loadUrl(MANUAL_URL)
                    webViewRef.value = this
                }
            },
            update = { view ->
                view.setBackgroundColor(bgArgb)
                applyTheme(view)
            },
            onRelease = { view ->
                webViewRef.value = null
                view.removeJavascriptInterface("ManualBridge")
                view.stopLoading()
                view.destroy()
            }
        )
    }
}
