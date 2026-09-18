package io.github.javcinema.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest

// 原生播放链路（进行中）：
// 1. 搜索页只用于取流，结果走 Compose 原生列表（封面、标题、时长、版本）——已实现。
// 2. 播放页在已验证会话里探测可播 HLS/MP4 直链（MissavStreamPolicy），命中后由用户点击
//    「用原生播放器播放」交给 Media3 全屏播放；未命中则留在站点 WebView 播放器——已实现。
// 3. 待办：探测到即自动接管、播放失败自动回退站点、彻底隐藏站点搜索/广告 UI。

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MissavPlayScreen(
    movieCode: String,
    onBack: () -> Unit,
    onPlayStream: (streamUrl: String, referer: String) -> Unit = { _, _ -> }
) {
    val searchUrl = remember(movieCode) { missavSearchUrl(movieCode) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var results by remember { mutableStateOf<List<MissavSearchResult>>(emptyList()) }
    var showChooser by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var detectedStream by remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun probeStream(view: WebView) {
        if (detectedStream != null) return
        view.evaluateJavascript(MISSAV_STREAM_PROBE_JS) { raw ->
            val found = unescapeJsString(raw)
            if (isPlayableStreamUrl(found)) {
                detectedStream = found
            }
        }
    }

    fun extractResults(view: WebView) {
        if (playing) return
        val url = view.url
        if (isMissavChallengeUrl(url) || isMissavChallengeTitle(view.title)) return
        if (!isMissavSearchUrl(url)) return
        view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { raw ->
            val html = unescapeJsString(raw)
            if (isMissavChallengeHtml(html)) {
                showChooser = false
                return@evaluateJavascript
            }
            val parsed = parseMissavSearchResults(html, movieCode)
            if (parsed.isNotEmpty()) {
                results = parsed
                showChooser = true
            }
        }
    }

    fun scheduleExtract(view: WebView) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ extractResults(view) }, 800)
    }

    fun cleanPlayPage(view: WebView) {
        view.evaluateJavascript(MISSAV_CLEAN_PLAY_JS, null)
    }

    BackHandler {
        val current = webView
        when {
            showChooser -> onBack()
            current?.canGoBack() == true -> current.goBack()
            else -> onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            webView?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (playing) "播放 $movieCode" else "选择 $movieCode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (playing) {
                        TextButton(onClick = {
                            playing = false
                            detectedStream = null
                            if (results.isNotEmpty()) {
                                showChooser = true
                            } else {
                                showChooser = false
                                webView?.loadUrl(searchUrl)
                            }
                        }) { Text("重选") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean = false
                        }
                        webViewClient = object : WebViewClient() {
                            private fun allowNavigation(url: String?, mainFrame: Boolean): Boolean {
                                return isAllowedMissavNavigation(url, mainFrame)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return !allowNavigation(
                                    request?.url?.toString(),
                                    request?.isForMainFrame != false
                                )
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (url != null && isMissavPlayUrl(url, movieCode) && !isMissavSearchUrl(url)) {
                                    playing = true
                                    showChooser = false
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                // 被拦截的广告主帧不展示错误页，直接停掉避免闪一下。
                                if (request?.isForMainFrame == true &&
                                    !isAllowedMissavNavigation(request.url?.toString()) &&
                                    isMissavSiteUrl(view?.url)
                                ) {
                                    view?.stopLoading()
                                    return
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (view == null || url.isNullOrBlank()) return
                                if (!isAllowedMissavNavigation(url)) return
                                CookieManager.getInstance().flush()
                                if (isMissavChallengeUrl(url) || isMissavChallengeTitle(view.title)) return
                                if (isMissavPlayUrl(url, movieCode) && !isMissavSearchUrl(url)) {
                                    playing = true
                                    showChooser = false
                                    cleanPlayPage(view)
                                    // 播放器可能延迟注入 src，多探几次；找到即止。
                                    mainHandler.postDelayed({ probeStream(view) }, 1500)
                                    mainHandler.postDelayed({ probeStream(view) }, 3500)
                                    return
                                }
                                if (playing) return
                                if (isMissavSearchUrl(url)) {
                                    scheduleExtract(view)
                                }
                            }
                        }
                        loadUrl(searchUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (!playing && showChooser && results.isNotEmpty()) {
                MissavResultList(
                    results = results,
                    onSelect = { item ->
                        playing = true
                        showChooser = false
                        webView?.loadUrl(item.url)
                    }
                )
            }
            val stream = detectedStream
            if (playing && stream != null) {
                Button(
                    onClick = { onPlayStream(stream, webView?.url ?: "") },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text("用原生播放器播放")
                }
            }
        }
    }
}

@Composable
private fun MissavResultList(
    results: List<MissavSearchResult>,
    onSelect: (MissavSearchResult) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("请选择要播放的影片", style = MaterialTheme.typography.titleMedium)
        }
        items(results, key = { it.url }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!item.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            imageLoader = context.imageLoader,
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.badge?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            item.duration?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
