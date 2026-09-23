package io.github.javcinema.ui.screen

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.javcinema.BuildConfig
import io.github.javcinema.ui.components.TOP_BAR_HEIGHT
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay

// 原生播放接管：
// 1. 站点页面（搜索页 / 播放页）全程在后台加载，用户看不到站点广告与控件。
// 2. 搜索页解析出候选后由 MissavPlayPolicy.selectBestMissavResult 自动选片，无需用户介入。
// 3. 播放页探测到 HLS/MP4 直链即自动交给 Media3 全屏播放。
// 4. 人机验证是**独立的前置步骤**：撞上就把页面露出来让用户过验证，过了之后从搜索页
//    重走一遍上面的 2→3（见 decideChallengeOutcome），不试图从验证页的落点续跑 ——
//    这样「首次验证后播放」与「已认证后再次播放」走的是同一段代码，体验一致。
// 5. 其余失败（解析超时、选不出片）才回退到可见的站点播放页，用户可继续手动操作。

private const val TAG = "MissavPlay"

/** 解析总预算。超过后回退站点页面，避免用户对着转圈干等。 */
private const val RESOLVE_TIMEOUT_MS = 20_000L

/** 播放页探测直链的时间点：播放器常延迟注入 src，多点几次提高命中率。 */
private val PROBE_DELAYS_MS = listOf(1_500L, 3_500L, 6_000L, 10_000L, 15_000L)

/** 整页兜底探测的时间点，排在最后一轮 DOM 探测之后、解析超时之前。 */
private const val DEEP_PROBE_DELAY_MS = 18_000L

/** 搜索结果页解析出候选的延迟：等页面把列表渲染完。 */
private const val EXTRACT_DELAY_MS = 800L

/** 第一轮没解析出候选时的补抓延迟：列表异步渲染，再给一次机会才敢判「未收录」。 */
private const val RETRY_EXTRACT_DELAY_MS = 1_800L

/**
 * 取流阶段。
 *
 * [RESOLVING] 站点页面在后台跑，界面盖着遮罩，探测到直链会自动跳原生播放器。
 * [CHALLENGE] 撞上人机验证 —— 站点页面**必须露出来**（用户得亲手过验证），
 *   只叠一条不挡操作的提示；验证一通过就自动切回 [RESOLVING] 重走解析，用户不必自己再搜一次。
 * [NOT_FOUND] 站点确实没有这部片 —— 明确告知，不再假装还在解析。
 * [SITE] 自动接管失败或用户主动接管失败后，站点页面直接露出来给用户手动操作。
 */
private enum class MissavPhase { RESOLVING, CHALLENGE, NOT_FOUND, SITE }

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
    // 验证通过后自动重走过的次数。只用于给「重走」封顶：验证判据是否定式的，
    // 万一被误判成已通过，没有上限就会一直重走。
    var challengeRestarts by remember(movieCode) { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    // 自己排的延时任务要能精确取消：不能用 removeCallbacksAndMessages(null)，
    // 那会把同一 Handler 上刚排好的探测任务一并清掉。
    val pendingTasks = remember { mutableListOf<Runnable>() }
    // shouldInterceptRequest 在非 UI 线程回调，而 WebView 不是线程安全的 —— 不能在里面读
    // view.url 判断「当前是不是播放页」。改为在主线程的 onPageStarted 里记下状态。
    val onPlayPage = remember(movieCode) { AtomicBoolean(false) }

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

    /**
     * 撞上人机验证：把站点页面交还给用户亲手过验证。
     *
     * 与 [fallbackToSite] 的关键区别是**还会自动收回来** —— 用户过完验证后
     * `onPageFinished` 会发现已离开验证页，直接 [restartResolve] 从搜索页重走一遍解析，
     * 用户不用自己再搜一次、也不用再点「重试解析」。
     * 用户已经主动打开过站点页面（SITE）时不再抢占。
     */
    fun enterChallenge() {
        if (phase == MissavPhase.SITE) return
        Log.i(TAG, "challenge: 需要人工过验证，验证通过后会自动继续")
        phase = MissavPhase.CHALLENGE
    }

    /** 站点确实没有这部片 —— 明确告知，不再假装还在解析。 */
    fun markNotFound() {
        Log.w(TAG, "resolve: 站点没有该番号的资源")
        phase = MissavPhase.NOT_FOUND
    }

    fun probeStream(view: WebView) {
        if (detectedStream != null) return
        view.evaluateJavascript(MISSAV_STREAM_PROBE_JS) { raw ->
            val found = unescapeJsString(raw)
            Log.i(TAG, "probe: ${if (found.isBlank()) "(未命中)" else found}")
            if (isAcceptableStreamUrl(found)) {
                detectedStream = found
            }
        }
    }

    /**
     * 网络层嗅到的 m3u8。**这是唯一稳的取流手段**，理由见 [shouldInterceptRequest] 的注释。
     *
     * 只在播放页采信：搜索页与推荐位同样会拉 m3u8，误采等于把广告交给播放器。
     * 回调不在主线程，状态更新必须 post 回主线程。
     */
    fun onSniffedStream(streamUrl: String) {
        if (!onPlayPage.get()) return
        if (!isAcceptableStreamUrl(streamUrl)) return
        mainHandler.post {
            if (detectedStream == null) {
                Log.i(TAG, "sniff: $streamUrl")
                detectedStream = streamUrl
            }
        }
    }

    /**
     * 整页兜底：在 HTML 明文里找直链。
     *
     * 当前站点把 m3u8 打包混淆在 `eval(function(p,a,c,k,e,d){...})` 里，明文扫不到，
     * 主力仍是 [onSniffedStream]；但页面模板回退到明文时这条路能救回来，
     * 而且它是纯 Kotlin 的，能被单测覆盖。
     */
    fun deepProbe(view: WebView) {
        if (detectedStream != null) return
        view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { raw ->
            val found = extractMissavStreamUrl(unescapeJsString(raw))
            Log.i(TAG, "deepProbe: ${found ?: "(未命中)"}")
            if (found != null && isAcceptableStreamUrl(found)) {
                detectedStream = found
            }
        }
    }

    fun scheduleProbes(view: WebView) {
        PROBE_DELAYS_MS.forEach { delayMs -> schedule(delayMs) { probeStream(view) } }
        schedule(DEEP_PROBE_DELAY_MS) { deepProbe(view) }
    }

    /**
     * 解析搜索结果页并决定下一步。
     *
     * [attempt] 从 0 起 —— 列表是异步渲染的，第一轮可能只拿到空壳，
     * 所以「解析不出候选」要再给一次机会才敢判「资源库未收录」。
     */
    fun extractResults(view: WebView, attempt: Int = 0) {
        if (phase != MissavPhase.RESOLVING) return
        val url = view.url
        if (!isChallengePassed(url, view.title)) {
            Log.i(TAG, "resolve: 人机验证页 url=$url title=${view.title}")
            enterChallenge()
            return
        }
        if (!isMissavSearchUrl(url)) return
        view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { raw ->
            if (phase != MissavPhase.RESOLVING) return@evaluateJavascript
            val html = unescapeJsString(raw)
            writePageDump(view, "missav_search.html", html)
            val decision = decideSearchOutcome(html, movieCode, attempt)
            Log.i(
                TAG,
                "resolve: html=${html.length} candidates=${decision.candidates} " +
                    "action=${decision.action} url=${decision.url}"
            )
            when (decision.action) {
                MissavResolveAction.PLAY -> webView?.loadUrl(decision.url.orEmpty())
                MissavResolveAction.CHALLENGE -> enterChallenge()
                MissavResolveAction.RETRY ->
                    schedule(RETRY_EXTRACT_DELAY_MS) { extractResults(view, attempt + 1) }
                MissavResolveAction.NOT_FOUND -> markNotFound()
            }
        }
    }

    fun cleanPlayPage(view: WebView) {
        view.evaluateJavascript(MISSAV_CLEAN_PLAY_JS, null)
    }

    /** 修掉站点「卡片点不动」的问题，见 [MISSAV_CLICK_FIX_JS]。 */
    fun installClickFix(view: WebView) {
        view.evaluateJavascript(MISSAV_CLICK_FIX_JS, null)
    }

    /**
     * 重走一遍完整的自动解析：取消已排的任务 → 清掉已嗅到的直链 → 回到 [MissavPhase.RESOLVING]
     * → 重新加载搜索页。
     *
     * 这是**唯一**的「开始解析」入口：用户点「重试解析」走它，人机验证通过后也走它。
     * 两者共用同一条路，所以「首次验证后播放」与「已认证后再次播放」的体验天然一致 ——
     * 认证只是一次独立的前置步骤，过了就重新走一遍，不试图从验证页的落点续跑。
     *
     * [resetChallengeBudget] 只给用户主动重试用：亲手点的重试应该把验证预算还回去，
     * 而自动重走要累计，否则验证判据一旦误判就会无限重走。
     */
    fun restartResolve(resetChallengeBudget: Boolean = false) {
        if (resetChallengeBudget) challengeRestarts = 0
        cancelPendingTasks()
        detectedStream = null
        phase = MissavPhase.RESOLVING
        webView?.loadUrl(searchUrl)
    }

    BackHandler {
        val current = webView
        when {
            // 后台解析 / 未收录时返回 = 直接退出，站点历史不该暴露给用户。
            // 人机验证阶段不在此列：那时站点页面是露出来的，返回应先回退站点历史。
            phase == MissavPhase.RESOLVING || phase == MissavPhase.NOT_FOUND -> onBack()
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
            // 不用 Material3 的 TopAppBar：它固定 64dp 高，且默认再叠一层状态栏 inset，
            // 而外层 MainScreen 的 Scaffold 已经给了状态栏 padding → 实际顶栏变成
            // 「状态栏 ×2 + 64dp」，明显过高。这里自绘成与项目其他页面一致的紧凑顶栏
            // （高度取 AppTopTabRow 的 TOP_BAR_HEIGHT），只保留一层状态栏间距。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TOP_BAR_HEIGHT)
                    .background(MaterialTheme.colorScheme.primary),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(TOP_BAR_HEIGHT)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "播放 $movieCode",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (phase == MissavPhase.SITE || phase == MissavPhase.NOT_FOUND) {
                    TextButton(
                        onClick = { restartResolve(resetChallengeBudget = true) },
                        modifier = Modifier.height(TOP_BAR_HEIGHT)
                    ) {
                        Text(
                            text = "重试解析",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
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
                            /**
                             * 旁路嗅探直链 —— **取流的主力手段**。
                             *
                             * 为什么非得在这一层拿：
                             * - 播放器走 MSE，`<video>` 的 `currentSrc` 是 `blob:https://...`，
                             *   对 Media3 毫无意义；
                             * - 站点把真正的 m3u8 打包混淆在 `eval(function(p,a,c,k,e,d){...})` 里，
                             *   整页 HTML 里根本没有明文 `.m3u8`（实测 `surrit.com/<uuid>/...`）；
                             * - 所以唯一稳的线索是**播放器实际发出的那次请求**。
                             *
                             * 返回 null = 只旁听，不改写响应，页面照常加载。
                             * 注意本回调不在 UI 线程，不能碰 WebView 的成员。
                             */
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val target = request?.url?.toString().orEmpty()
                                if (target.contains(".m3u8", ignoreCase = true)) {
                                    onSniffedStream(target)
                                }
                                return null
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?
                            ) {
                                onPlayPage.set(
                                    isMissavPlayUrl(url.orEmpty(), movieCode) &&
                                        !isMissavSearchUrl(url)
                                )
                            }

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
                                installClickFix(view)

                                val onPlayPage =
                                    isMissavPlayUrl(url, movieCode) && !isMissavSearchUrl(url)

                                // 人机验证阶段：站点页面露在外面等用户亲手过验证，这里只盯
                                // 「已经离开验证页」这一个信号。验证一旦过了就**重走整个解析流程**，
                                // 而不是从当前落点续跑 —— 落点是不确定的（搜索页 / 站点自己的播放页 /
                                // 首页 / 带参数的重定向），续跑就得把每种落点都处理对，漏一种就是静默
                                // 卡死：界面停在「正在解析播放地址…」，直到解析超时把用户甩回站点页面，
                                // 而那时站点播放器往往已经自己播起来了 —— 表现为「验证完却跳去了网页」。
                                if (phase == MissavPhase.CHALLENGE) {
                                    val outcome = decideChallengeOutcome(
                                        passed = isChallengePassed(url, view.title),
                                        restarts = challengeRestarts
                                    )
                                    when (outcome) {
                                        MissavChallengeOutcome.WAIT -> Unit
                                        MissavChallengeOutcome.RESTART -> {
                                            challengeRestarts += 1
                                            Log.i(TAG, "challenge: 验证已通过，重走解析（第 $challengeRestarts 次）")
                                            restartResolve()
                                        }
                                        MissavChallengeOutcome.GIVE_UP -> {
                                            Log.w(TAG, "challenge: 已重走 $challengeRestarts 次仍在验证页，回退站点")
                                            fallbackToSite()
                                        }
                                    }
                                    return
                                }

                                // 播放页两个阶段都探测：解析阶段命中即自动接管，
                                // 已回退到站点时命中则降级为手动按钮。
                                if (onPlayPage) {
                                    Log.i(TAG, "onPageFinished: 命中播放页 $url，开始探测直链")
                                    dumpCurrentPage(view, "missav_play.html")
                                    cleanPlayPage(view)
                                    scheduleProbes(view)
                                    return
                                }
                                // 站点阶段由用户自己操作，不再自动解析（否则会反复把人弹回播放器）。
                                if (phase != MissavPhase.RESOLVING) return
                                if (!isChallengePassed(url, view.title)) {
                                    enterChallenge()
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

            if (phase == MissavPhase.NOT_FOUND) {
                MissavNotFoundOverlay(
                    movieCode = movieCode,
                    onRetry = { restartResolve(resetChallengeBudget = true) },
                    onOpenSite = { fallbackToSite() },
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 验证阶段站点页面必须可交互，所以这里只叠一条不挡操作的顶部提示。
            if (phase == MissavPhase.CHALLENGE) {
                MissavChallengeBanner(
                    onSkip = { fallbackToSite() },
                    modifier = Modifier.align(Alignment.TopCenter)
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

/**
 * 「站点确实没有这部片」的收口提示。
 *
 * 单独做成一个终态，而不是像以前那样悄悄回退到站点页面：回退后用户看到的是一张
 * 空白搜索结果页，分不清是「站点没有」还是「解析挂了」，只能反复点重试。
 */
@Composable
private fun MissavNotFoundOverlay(
    movieCode: String,
    onRetry: () -> Unit,
    onOpenSite: () -> Unit,
    onBack: () -> Unit,
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
            Text(
                text = MISSAV_NOT_FOUND_MESSAGE,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "站点搜索「$movieCode」没有任何结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("重试解析") }
                TextButton(onClick = onOpenSite) { Text("打开站点页面") }
            }
            TextButton(onClick = onBack) { Text("返回") }
        }
    }
}

/**
 * 人机验证期间贴在顶部的提示条。
 *
 * 刻意不做成整屏遮罩：验证要用户亲手点，站点页面必须保持可交互。
 *
 * ⚠️ **文案必须单行显示**：这条提示本身就矮，一旦在窄屏 / 大字号下折成两行，
 * 就会向下压住站点页面的验证框。所以既限制了 `maxLines = 1`（配 `softWrap = false`
 * 才是真正的单行 + 省略号），文案也刻意取短 —— 别把「通过后会自动继续播放」加回来，
 * 加了在 320dp 宽 + 大字号下必折行。
 */
@Composable
private fun MissavChallengeBanner(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "请在页面中完成人机验证",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSkip) {
            Text("跳过", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * 把站点页面落盘到 cacheDir（系统会自动回收），仅 debug 包。
 *
 * 站点改版会让选择器**静默**失效 —— 没有报错，只是解析结果变空。定位这类问题只能靠
 * 真实页面，所以留一个不用改代码就能看 HTML 的口子：
 *
 * ```
 * adb exec-out run-as io.github.javcinema cat cache/missav_search.html > search.html
 * adb exec-out run-as io.github.javcinema cat cache/missav_play.html   > play.html
 * ```
 */
private fun writePageDump(view: WebView, name: String, html: String) {
    if (!BuildConfig.DEBUG || html.isBlank()) return
    runCatching {
        java.io.File(view.context.cacheDir, name).writeText(html)
    }
}

/** 取当前页面 HTML 后落盘，见 [writePageDump]。 */
private fun dumpCurrentPage(view: WebView, name: String) {
    if (!BuildConfig.DEBUG) return
    view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { raw ->
        writePageDump(view, name, unescapeJsString(raw))
    }
}
