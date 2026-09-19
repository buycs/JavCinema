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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

// 原生播放接管：
// 1. 站点页面（搜索页 / 播放页）全程在后台加载，用户看不到站点广告与控件。
// 2. 搜索页解析出候选后由 MissavPlayPolicy.selectBestMissavResult 自动选片，无需用户介入。
// 3. 播放页探测到 HLS/MP4 直链即自动交给 Media3 全屏播放。
// 4. 任一环节失败（人机验证、解析超时、选不出片）回退到可见的站点播放页，用户可继续手动操作。

/** 解析总预算。超过后回退站点页面，避免用户对着转圈干等。 */
private const val RESOLVE_TIMEOUT_MS = 20_000L

/** 播放页探测直链的时间点：播放器常延迟注入 src，多点几次提高命中率。 */
private val PROBE_DELAYS_MS = listOf(1_500L, 3_500L, 6_000L, 10_000L, 15_000L)

/** 搜索结果页解析出候选的延迟：等页面把列表渲染完。 */
private const val EXTRACT_DELAY_MS = 800L

/**
 * 取流阶段。
 *
 * [RESOLVING] 站点页面在后台跑，界面盖着遮罩，探测到直链会自动跳原生播放器。
 * [SITE] 自动接管失败或用户主动接管失败后，站点页面直接露出来给用户手动操作。
 */
private enum class MissavPhase { RESOLVING, SITE }

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
    // 用 rememberSaveable 而非 remember：本页交棒后会被导航层移出回退栈，但万一移除没生效
    // （例如 popUpTo 没匹配上），从播放器返回时 phase 会被恢复成 SITE 而不是 RESOLVING，
    // 不至于再自动跳一次形成无限跳转。这是对 popUpTo 的兜底，不是主机制。
    var phase by rememberSaveable(movieCode) { mutableStateOf(MissavPhase.RESOLVING) }
    var detectedStream by remember(movieCode) { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // 自己排的延时任务要能精确取消：不能用 removeCallbacksAndMessages(null)，
    // 那会把同一 Handler 上刚排好的探测任务一并清掉。
    val pendingTasks = remember { mutableListOf<Runnable>() }

    fun schedule(delayMs: Long, action: () -> Unit) {
        val task = Runnable { action() }
        pendingTasks.add(task)
        mainHandler.postDelayed(task, delayMs)
    }

    fun cancelPendingTasks() {
        pendingTasks.forEach { mainHandler.removeCallbacks(it) }
        pendingTasks.clear()
    }

    /**
     * 回退到可见的站点页面。所有失败路径都收敛到这里。
     *
     * 刻意**不**取消已排队的探测任务：探测在回退后仍有意义 ——
     * 若稍后拿到直链，界面会降级显示「用原生播放器播放」按钮，保住阶段一的能力。
     */
    fun fallbackToSite() {
        phase = MissavPhase.SITE
    }

    fun probeStream(view: WebView) {
        if (detectedStream != null) return
        view.evaluateJavascript(MISSAV_STREAM_PROBE_JS) { raw ->
            val found = unescapeJsString(raw)
            if (isPlayableStreamUrl(found)) {
                detectedStream = found
            }
        }
    }

    fun scheduleProbes(view: WebView) {
        PROBE_DELAYS_MS.forEach { delayMs -> schedule(delayMs) { probeStream(view) } }
    }

    fun extractResults(view: WebView) {
        if (phase != MissavPhase.RESOLVING) return
        val url = view.url
        if (isMissavChallengeUrl(url) || isMissavChallengeTitle(view.title)) {
            fallbackToSite()
            return
        }
        if (!isMissavSearchUrl(url)) return
        view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { raw ->
            if (phase != MissavPhase.RESOLVING) return@evaluateJavascript
            val html = unescapeJsString(raw)
            if (isMissavChallengeHtml(html)) {
                fallbackToSite()
                return@evaluateJavascript
            }
            val best = selectBestMissavResult(parseMissavSearchResults(html, movieCode), movieCode)
            if (best == null) {
                fallbackToSite()
                return@evaluateJavascript
            }
            webView?.loadUrl(best.url)
        }
    }

    fun cleanPlayPage(view: WebView) {
        view.evaluateJavascript(MISSAV_CLEAN_PLAY_JS, null)
    }

    /** 重新走一遍自动解析（回退后用户可主动重试）。 */
    fun restartResolve() {
        cancelPendingTasks()
        detectedStream = null
        phase = MissavPhase.RESOLVING
        webView?.loadUrl(searchUrl)
    }

    BackHandler {
        val current = webView
        when {
            // 后台解析时返回 = 直接退出，站点历史不该暴露给用户。
            phase == MissavPhase.RESOLVING -> onBack()
            current?.canGoBack() == true -> current.goBack()
            else -> onBack()
        }
    }

    // 解析超时兜底。phase 变化会重启/取消本效果，回退后不会误触发。
    LaunchedEffect(phase) {
        if (phase != MissavPhase.RESOLVING) return@LaunchedEffect
        delay(RESOLVE_TIMEOUT_MS)
        if (phase == MissavPhase.RESOLVING) fallbackToSite()
    }

    // 探测到直链即自动接管。先把 phase 落到 SITE，避免从播放器返回时再次自动跳转。
    LaunchedEffect(detectedStream, phase) {
        val stream = detectedStream ?: return@LaunchedEffect
        if (phase != MissavPhase.RESOLVING) return@LaunchedEffect
        val referer = webView?.url.orEmpty()
        phase = MissavPhase.SITE
        onPlayStream(stream, referer)
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelPendingTasks()
            webView?.apply {
                stopLoading()
                destroy()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放 $movieCode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (phase == MissavPhase.SITE) {
                        TextButton(onClick = { restartResolve() }) { Text("重试解析") }
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
            // WebView 始终铺满并留在视图树里（只是被遮罩盖住）：
            // 脱离视图树或零尺寸会让站点脚本不执行，取流也就无从谈起。
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.mediaPlaybackRequiresUserGesture = false
                        // 站点本体是 HTTPS，流探测也不依赖 HTTP 资源；不放宽混合内容，避免降级攻击面。
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
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
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                return !isAllowedMissavNavigation(
                                    request?.url?.toString(),
                                    request?.isForMainFrame != false
                                )
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
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (view == null || url.isNullOrBlank()) return
                                if (!isAllowedMissavNavigation(url)) return
                                CookieManager.getInstance().flush()

                                // 播放页两个阶段都探测：解析阶段命中即自动接管，
                                // 已回退到站点时命中则降级为手动按钮。
                                if (isMissavPlayUrl(url, movieCode) && !isMissavSearchUrl(url)) {
                                    cleanPlayPage(view)
                                    scheduleProbes(view)
                                    return
                                }
                                // 站点阶段由用户自己操作，不再自动解析（否则会反复把人弹回播放器）。
                                if (phase != MissavPhase.RESOLVING) return
                                if (isMissavChallengeUrl(url) || isMissavChallengeTitle(view.title)) {
                                    fallbackToSite()
                                    return
                                }
                                if (isMissavSearchUrl(url)) {
                                    schedule(EXTRACT_DELAY_MS) { extractResults(view) }
                                }
                            }
                        }
                        loadUrl(searchUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (phase == MissavPhase.RESOLVING) {
                MissavResolvingOverlay(
                    movieCode = movieCode,
                    onOpenSite = { fallbackToSite() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 回退到站点页面后若仍探测到直链，降级为手动按钮，保留阶段一的能力。
            val stream = detectedStream
            if (phase == MissavPhase.SITE && stream != null) {
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
private fun MissavResolvingOverlay(
    movieCode: String,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator()
            Text("正在解析播放地址…", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "无需操作，解析成功会自动开始播放",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onOpenSite) { Text("直接打开站点页面") }
            Text(
                text = movieCode,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
