package io.github.javcinema.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.navigation.NavController
import coil.imageLoader
import io.github.javcinema.BuildConfig
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.ui.navigation.NavRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(navController: NavController? = null) {
    var showDataUrlDialog by remember { mutableStateOf(false) }
    var showMagnetUrlDialog by remember { mutableStateOf(false) }
    var showHomePageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFavoriteBackupChooser by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Configurations.hideFromRecents 是普通字段，改它不会触发重组，
    // 所以用本地 state 驱动 UI，写回时同时更新两者。
    var hideFromRecents by remember { mutableStateOf(Configurations.hideFromRecents) }
    val exportFavoritesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { exportFavoritesToUri(context, uri) }
            Toast.makeText(context, if (ok) "已导出收藏" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
    val importFavoritesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val added = importFavoritesFromUri(context, uri)
                    "已导入 $added 条"
                }.getOrElse { "导入失败: ${it.message ?: "文件无效"}" }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 顶部不留边距：MainScreen 的 Scaffold 已经给了状态栏高度的 padding，
            // 再叠一层 16dp 会让设置项离状态栏过远。其他 tab 页顶部有 38dp 顶栏，
            // 设置页没有顶栏，所以这里不需要额外留白。
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsItem(
            icon = Icons.Filled.Star,
            title = "数据源配置",
            summary = "当前：${getCurrentSourceSummary()}（点此切换与自定义地址）",
            onClick = { showDataUrlDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.Visibility,
            title = "磁力源配置",
            summary = "自定义磁力源地址",
            onClick = { showMagnetUrlDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.Home,
            title = "首页设置",
            summary = getHomePageSummary(),
            onClick = { showHomePageDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.Palette,
            title = "主题",
            summary = getThemeSummary(),
            onClick = { showThemeDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.GridView,
            title = "网格列数",
            summary = "${Configurations.gridColumns.coerceIn(2, 4)} 列",
            onClick = { showGridDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.ImportExport,
            title = "导入/导出收藏",
            summary = "通过 JSON 文件备份影片和女优",
            onClick = { showFavoriteBackupChooser = true }
        )
        SettingsItem(
            icon = Icons.Filled.Delete,
            title = "清理缓存",
            summary = "清除图片、网页与内存缓存",
            onClick = {
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) { clearAllCaches(context) }
                    Toast.makeText(context, buildCacheClearMessage(outcome), Toast.LENGTH_SHORT).show()
                }
            }
        )
        SettingsItem(
            icon = Icons.Filled.VisibilityOff,
            title = "最近任务隐藏",
            summary = if (hideFromRecents) {
                "不在系统最近任务列表中显示本应用（重启后生效）"
            } else {
                "在系统最近任务列表中显示本应用"
            },
            trailing = {
                Switch(
                    checked = hideFromRecents,
                    onCheckedChange = { checked ->
                        hideFromRecents = checked
                        Configurations.hideFromRecents = checked
                        Configurations.savePrefs(context)
                        Toast.makeText(
                            context,
                            if (checked) "已开启，重启后生效" else "已关闭，重启后生效",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        )
        SettingsItem(
            icon = Icons.Filled.Info,
            title = "关于",
            summary = "JavCinema ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            onClick = { showAboutDialog = true }
        )
    }

    if (showDataUrlDialog) {
        DataUrlDialog(
            onDismiss = { showDataUrlDialog = false },
            navController = navController
        )
    }
    if (showMagnetUrlDialog) {
        MagnetUrlDialog(
            onDismiss = { showMagnetUrlDialog = false },
            navController = navController
        )
    }
    if (showHomePageDialog) {
        HomePageDialog(onDismiss = { showHomePageDialog = false })
    }
    if (showThemeDialog) {
        ThemeDialog(onDismiss = { showThemeDialog = false })
    }
    if (showGridDialog) {
        GridColumnsDialog(onDismiss = { showGridDialog = false })
    }
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
    if (showFavoriteBackupChooser) {
        AlertDialog(
            onDismissRequest = { showFavoriteBackupChooser = false },
            title = { Text("导入/导出收藏") },
            text = {
                Text("只操作已收藏的影片和女优，使用 JSON 文件导入或导出。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showFavoriteBackupChooser = false
                    if (!hasFavoritesToExport()) {
                        Toast.makeText(context, "没有可导出的收藏", Toast.LENGTH_SHORT).show()
                    } else {
                        exportFavoritesLauncher.launch(FAVORITE_BACKUP_FILE_NAME)
                    }
                }) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFavoriteBackupChooser = false
                    importFavoritesLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }) { Text("导入") }
            }
        )
    }
}

private fun getThemeSummary(): String {
    return when (Configurations.themeMode) {
        "light" -> "浅色"
        "dark" -> "深色"
        else -> "跟随系统"
    }
}

@Composable
private fun ThemeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Configurations.themeMode ?: "system") }
    val options = listOf(
        "system" to "跟随系统",
        "light" to "浅色",
        "dark" to "深色"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = value }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = { selected = value }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Configurations.themeMode = selected
                Configurations.savePrefs(context)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun GridColumnsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // mutableIntStateOf：Int 状态不必装箱。
    var selected by remember { mutableIntStateOf(Configurations.gridColumns.coerceIn(2, 4)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("网格列数") },
        text = {
            Column {
                listOf(2, 3, 4).forEach { columns ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = columns }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == columns,
                            onClick = { selected = columns }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${columns} 列")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Configurations.gridColumns = selected
                Configurations.savePrefs(context)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun openUrlWithChooser(context: Context, url: String) {
    // createChooser 保证每次都弹应用选择框，避免被默认应用直接接管。
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching {
        context.startActivity(Intent.createChooser(intent, "选择应用打开"))
    }.onFailure {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 清理全部缓存：Coil 图片磁盘缓存、WebView 缓存、Coil 内存缓存。
 *
 * WebView 的 [WebStorage] / [CookieManager] 必须在主线程调用，因此内部切回 Main。
 * 每一项独立捕获异常，避免一项失败影响其余项。
 */
@OptIn(coil.annotation.ExperimentalCoilApi::class)
private suspend fun clearAllCaches(context: Context): CacheClearOutcome {
    val imageCleared = withContext(Dispatchers.IO) {
        runCatching { context.imageLoader.diskCache?.clear() }.isSuccess
    }
    val memoryCleared = runCatching { context.imageLoader.memoryCache?.clear() }.isSuccess
    val webCleared = withContext(Dispatchers.Main) {
        runCatching {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }.isSuccess
    }
    return CacheClearOutcome(
        imageCacheCleared = imageCleared,
        webViewCacheCleared = webCleared,
        memoryCacheCleared = memoryCleared
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repos = listOf(
        "本项目源码" to "https://github.com/buycs/JavCinema",
        "原项目源码" to "https://github.com/SplashCodes/JAViewer"
    )
    var checking by remember { mutableStateOf(false) }
    // 检查结果不在本对话框内展示，而是驱动一个独立浮窗，避免「关于」内容被结果撑长。
    var updatePrompt by remember { mutableStateOf<UpdatePrompt?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // 版本号 + 检查更新入口放在一起
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "JavCinema ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = !checking,
                        onClick = {
                            checking = true
                            scope.launch {
                                val result = fetchLatestRelease()
                                checking = false
                                updatePrompt = result.fold(
                                    onSuccess = { info ->
                                        if (isNewerVersion(info.tag, BuildConfig.VERSION_NAME)) {
                                            UpdatePrompt.NewVersion(info)
                                        } else {
                                            UpdatePrompt.UpToDate
                                        }
                                    },
                                    onFailure = { e ->
                                        UpdatePrompt.Failed(e.message ?: "网络异常")
                                    }
                                )
                            }
                        }
                    ) { Text(if (checking) "检查中…" else "检查更新") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "基于 JAViewer 的二次开发。内容来自第三方数据源，仅供学习交流。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text("项目源码", style = MaterialTheme.typography.titleSmall)
                repos.forEach { (label, url) ->
                    TextButton(
                        onClick = { openUrlWithChooser(context, url) }
                    ) { Text(label) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )

    // 检查结果浮窗：覆盖在「关于」之上，关闭后回到「关于」。
    updatePrompt?.let { prompt ->
        UpdateResultDialog(
            prompt = prompt,
            onDismiss = { updatePrompt = null },
            onOpenRelease = { url ->
                updatePrompt = null
                openUrlWithChooser(context, url)
            }
        )
    }
}

/** 「检查更新」的结果，用于驱动结果浮窗。 */
private sealed interface UpdatePrompt {
    /** 已是最新版本。 */
    data object UpToDate : UpdatePrompt

    /** 发现新版本。 */
    data class NewVersion(val info: ReleaseInfo) : UpdatePrompt

    /** 检查失败。 */
    data class Failed(val message: String) : UpdatePrompt
}

/**
 * 检查更新结果浮窗。
 *
 * - 已是最新：单个「知道了」
 * - 发现新版：展示版本号与更新说明，「去更新」打开 release 页
 * - 检查失败：展示失败原因
 */
@Composable
private fun UpdateResultDialog(
    prompt: UpdatePrompt,
    onDismiss: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    when (prompt) {
        is UpdatePrompt.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查更新") },
            text = { Text("已是最新版本（${BuildConfig.VERSION_NAME}）") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
        )

        is UpdatePrompt.NewVersion -> {
            val info = prompt.info
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("发现新版本") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = info.tag.ifBlank { info.name }.ifBlank { "未知版本" },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "当前版本 ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (info.body.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = info.body.take(400),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        onOpenRelease(info.htmlUrl.ifBlank { UPDATE_RELEASES_PAGE })
                    }) { Text("去更新") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("稍后") }
                }
            )
        }

        is UpdatePrompt.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("检查更新") },
            text = { Text("检查失败：${prompt.message}") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
        )
    }
}

private fun getHomePageSummary(): String = NavRoutes.homePageLabel(Configurations.homePage)

@Composable
private fun HomePageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 存的是导航图里注册的 route；旧版本可能存过 "search"，这里归一化一次。
    val current = NavRoutes.normalizeHomePage(Configurations.homePage)
    var selected by remember { mutableStateOf(current) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val options = NavRoutes.HOME_PAGE_OPTIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("首页设置") },
        text = {
            Column {
                Text(
                    text = "app 启动时默认显示的功能页面",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { (route, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = route }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == route,
                            onClick = { selected = route }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = label)
                    }
                }
                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    Configurations.homePage = selected
                    Configurations.savePrefs(context)
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    errorMsg = "保存失败: ${e.localizedMessage}"
                    Toast.makeText(context, "保存失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun getCurrentSourceSummary(): String {
    val ds = JavCinema.getDataSource()
    return ds?.toString() ?: "未选择"
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    SettingsItem(icon = icon, title = title, summary = summary, onClick = onClick, trailing = null)
}

/**
 * 设置项卡片。
 *
 * @param trailing 右侧附加控件（如 [Switch]）。为 null 时整行可点击并触发 [onClick]；
 *   不为 null 时由控件自己处理点击，外层 [Card] 不再可点，避免误触开关。
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun DataUrlDialog(onDismiss: () -> Unit, navController: NavController? = null) {
    val context = LocalContext.current
    val dataSources = JavCinema.DATA_SOURCES
    val items = dataSources.map { ds ->
        val defaultLink = ds.link ?: ""
        val custom = when (ds.name) {
            "骑兵" -> Configurations.customAvmooUrl
            "步兵" -> Configurations.customAvsoUrl
            "欧美" -> Configurations.customAvxoUrl
            else -> null
        }
        SourceItem(ds.name ?: "", custom ?: defaultLink, defaultLink,
            onSave = { v -> when (ds.name) {
                "骑兵" -> Configurations.customAvmooUrl = v
                "步兵" -> Configurations.customAvsoUrl = v
                "欧美" -> Configurations.customAvxoUrl = v
            }}
        )
    }
    // 数据源选择与地址配置合并：同一个对话框里既选当前源，也改各源地址。
    SourceConfigDialog(
        title = "数据源配置",
        items = items,
        onDismiss = onDismiss,
        context = context,
        navController = navController,
        selectable = true,
        initialSelected = JavCinema.getDataSource(),
        dataSources = dataSources
    )
}

@Composable
private fun MagnetUrlDialog(onDismiss: () -> Unit, navController: NavController? = null) {
    val context = LocalContext.current
    val items = JavCinema.MAGNET_SOURCES.map { ds ->
        val defaultLink = ds.link ?: ""
        val custom = when (ds.name) {
            "BtSearch" -> Configurations.customBtSearchUrl
            "Cili" -> Configurations.customCiliUrl
            "BTSOW" -> Configurations.customBtsowUrl
            else -> null
        }
        SourceItem(ds.name ?: "", custom ?: defaultLink, defaultLink,
            onSave = { v -> when (ds.name) {
                "BtSearch" -> Configurations.customBtSearchUrl = v
                "Cili" -> Configurations.customCiliUrl = v
                "BTSOW" -> Configurations.customBtsowUrl = v
            }}
        )
    }
    SourceConfigDialog("磁力源配置", items, onDismiss, context, navController, reloadOnSave = false)
}

private data class SourceItem(
    val label: String,
    val initial: String,
    val defaultLink: String,
    val onSave: (String?) -> Unit
)

@Composable
private fun SourceConfigDialog(
    title: String,
    items: List<SourceItem>,
    onDismiss: () -> Unit,
    context: android.content.Context,
    navController: NavController? = null,
    reloadOnSave: Boolean = true,
    selectable: Boolean = false,
    initialSelected: Any? = null,
    dataSources: List<Any> = emptyList()
) {
    val urls = remember { items.associate { it.label to mutableStateOf(it.initial) } }
    var saved by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var pendingHttp by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedIndex by remember {
        mutableIntStateOf(
            if (selectable) {
                items.indexOfFirst { it.label == (initialSelected as? io.github.javcinema.data.model.DataSource)?.name }
                    .coerceAtLeast(0)
            } else {
                -1
            }
        )
    }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (selectable) {
                        "选择启用的数据源，并可自定义各源地址。留空则恢复默认地址，默认只接受 https。"
                    } else {
                        "留空则恢复默认地址。默认只接受 https，http 需确认。保存前会探测地址。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                // 每个数据源占一行：选择框 + 名称 + 地址输入框。
                items.forEachIndexed { index, item ->
                    val state = urls[item.label]!!
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (selectable) {
                            RadioButton(
                                selected = selectedIndex == index,
                                onClick = { selectedIndex = index }
                            )
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(min = 52.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        TextField(
                            value = state.value,
                            onValueChange = { state.value = it; saved = false; errorMsg = null },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            placeholder = { Text("默认: ${item.defaultLink}", style = MaterialTheme.typography.bodySmall) },
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.height(56.dp).weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (saved) {
                    Text(text = "已保存", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !checking,
                onClick = {
                    scope.launch {
                        persistSourceUrls(
                            items = items,
                            urls = urls.mapValues { it.value.value },
                            allowHttp = false,
                            context = context,
                            navController = navController,
                            reloadOnSave = reloadOnSave,
                            onDismiss = onDismiss,
                            onSaved = { saved = true },
                            onError = { errorMsg = it },
                            onChecking = { checking = it },
                            onNeedHttpConfirm = { pendingHttp = it },
                            selectedIndex = selectedIndex,
                            dataSources = dataSources
                        )
                    }
                }
            ) {
                Text(if (checking) "探测中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
    if (pendingHttp.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingHttp = emptyList() },
            title = { Text("允许 HTTP？") },
            text = { Text("以下地址不是 https，确认后才会保存：\n${pendingHttp.joinToString("\n")}") },
            confirmButton = {
                TextButton(onClick = {
                    pendingHttp = emptyList()
                    scope.launch {
                        persistSourceUrls(
                            items = items,
                            urls = urls.mapValues { it.value.value },
                            allowHttp = true,
                            context = context,
                            navController = navController,
                            reloadOnSave = reloadOnSave,
                            onDismiss = onDismiss,
                            onSaved = { saved = true },
                            onError = { errorMsg = it },
                            onChecking = { checking = it },
                            onNeedHttpConfirm = {},
                            selectedIndex = selectedIndex,
                            dataSources = dataSources
                        )
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHttp = emptyList() }) { Text("取消") }
            }
        )
    }
}

private suspend fun persistSourceUrls(
    items: List<SourceItem>,
    urls: Map<String, String>,
    allowHttp: Boolean,
    context: android.content.Context,
    navController: NavController?,
    reloadOnSave: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
    onChecking: (Boolean) -> Unit,
    onNeedHttpConfirm: (List<String>) -> Unit,
    selectedIndex: Int = -1,
    dataSources: List<Any> = emptyList()
) {
    val httpPending = mutableListOf<String>()
    val normalized = mutableMapOf<String, String?>()
    items.forEach { item ->
        val check = validateSourceUrl(urls[item.label], allowHttp)
        if (check.needsHttpConfirm) {
            httpPending += check.normalized ?: urls[item.label].orEmpty()
        } else if (check.error != null) {
            onError("${item.label}: ${check.error}")
            return
        } else {
            normalized[item.label] = check.normalized
        }
    }
    if (httpPending.isNotEmpty()) {
        onNeedHttpConfirm(httpPending)
        return
    }
    onChecking(true)
    try {
        normalized.forEach { (label, url) ->
            if (!url.isNullOrBlank()) {
                val probeError = withContext(Dispatchers.IO) { probeSourceUrl(url) }
                if (probeError != null) {
                    onError("$label: $probeError")
                    return
                }
            }
        }
        items.forEach { item ->
            item.onSave(normalized[item.label])
        }
        Configurations.savePrefs(context)
        val config = JavCinema.CONFIGURATIONS
        if (config != null) {
            config.applyCustomUrls()
            // 数据源选择与地址配置合并后，保存时一并应用选中的源。
            @Suppress("UNCHECKED_CAST")
            val sources = dataSources as? List<io.github.javcinema.data.model.DataSource>
            val picked = sources?.getOrNull(selectedIndex)
            if (picked != null) {
                config.dataSource = picked
            }
            config.save()
        }
        if (reloadOnSave) {
            JavCinema.recreateService()
            onDismiss()
            navController?.navigate(homeDestination()) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            onDismiss()
        }
        onSaved()
        Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        onError("保存失败: ${e.localizedMessage}")
        Toast.makeText(context, "保存失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    } finally {
        onChecking(false)
    }
}

/**
 * 数据源切换后需要重建导航图（[JavCinema.recreateService]），因此要重新导航到首页。
 *
 * ⚠️ 这里必须用 [NavRoutes.navigateTarget]（返回具体路径 "search" / "home"），
 * **不能**用 [NavRoutes.normalizeHomePage]（返回注册原值 "search?query={query}"）。
 *
 * 曾经就是后者：`navigate("search?query={query}")` 会把 `{query}` 当成字面量填进
 * `query` 参数，表现为「保存设置回到首页后，搜索框里写着 `{query}`，并且真的拿它去搜」。
 * 带占位符的原值只有 `NavHost(startDestination = ...)` 才能用。
 */
private fun homeDestination(): String = NavRoutes.navigateTarget(Configurations.homePage)


