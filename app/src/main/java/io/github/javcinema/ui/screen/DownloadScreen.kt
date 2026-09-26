package io.github.javcinema.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.javcinema.data.model.MagnetFile
import io.github.javcinema.ui.components.AppTopTabRow
import io.github.javcinema.ui.components.TopBarSelectedContentColor
import io.github.javcinema.ui.components.TopBarUnselectedContentColor
import io.github.javcinema.ui.navigation.NavRoutes
import io.github.javcinema.util.copyText
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(
    navController: NavController,
    keyword: String,
    viewModel: DownloadViewModel = viewModel()
) {
    val btsoState by viewModel.btsoState.collectAsState()
    val ciliState by viewModel.ciliState.collectAsState()
    val btSearchState by viewModel.btSearchState.collectAsState()
    val magnetLink by viewModel.magnetLink.collectAsState()
    val isGettingMagnet by viewModel.isGettingMagnet.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    LaunchedEffect(keyword) {
        if (keyword.isBlank()) {
            viewModel.resetSearch()
            return@LaunchedEffect
        }
        viewModel.search(keyword, "btsearch")
        viewModel.search(keyword, "cili")
        viewModel.search(keyword, "btso")
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    if (magnetLink != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissMagnet() },
            title = { Text("磁力链接") },
            text = { Text(magnetLink ?: "", style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = {
                        val link = magnetLink ?: ""
                        viewModel.dismissMagnet()
                        if (link.isNotBlank()) {
                            // 交给应用内的磁力播放：ExoPlayer + 本地 BT 引擎，不跳第三方。
                            navController.navigate(NavRoutes.player(link))
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Text("播放")
                    }
                    TextButton(onClick = {
                        copyText(context, magnetLink ?: "", "已复制磁力链接")
                        viewModel.dismissMagnet()
                    }, modifier = Modifier.weight(1f)) {
                        Text("复制")
                    }
                    TextButton(onClick = { viewModel.dismissMagnet() }, modifier = Modifier.weight(1f)) {
                        Text("关闭")
                    }
                    TextButton(onClick = {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(magnetLink))
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "没有支持打开该链接的应用", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        viewModel.dismissMagnet()
                    }, modifier = Modifier.weight(1f)) {
                        Text("打开")
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val magnetSources = listOf(
            "BTSEARCH" to "btsearch",
            "无极磁链" to "cili",
            "BTSOW" to "btso"
        )
        AppTopTabRow(selectedIndex = pagerState.currentPage) {
            magnetSources.forEachIndexed { index, (label, _) ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(label) },
                    selectedContentColor = TopBarSelectedContentColor,
                    unselectedContentColor = TopBarUnselectedContentColor
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            val uiState = listOf(btSearchState, ciliState, btsoState).getOrElse(page) { btsoState }
            val providerName = magnetSources[page].second

            Box(modifier = Modifier.fillMaxSize()) {
                when (val source = uiState) {
                    MagnetSourceUi.Idle, MagnetSourceUi.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is MagnetSourceUi.Error -> {
                        Text(
                            text = source.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp)
                        )
                    }
                    is MagnetSourceUi.Success -> {
                        if (source.items.isEmpty()) {
                            Text(
                                text = "未找到结果",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            val visibleItems = visibleSearchResults(source.items)
                            LazyColumn(
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = visibleItems,
                                    key = { "${it.link ?: ""}_${it.title ?: ""}" }
                                ) { link ->
                                    DownloadLinkItem(
                                        title = link.title,
                                        size = link.size,
                                        date = link.date,
                                        files = link.files,
                                        filesError = link.filesError,
                                        onMagnetClick = {
                                            viewModel.getMagnetLink(link, providerName)
                                        },
                                        onExpand = {
                                            viewModel.loadFiles(link, providerName)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isGettingMagnet) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1L.shl(40) -> "%.2f TB".format(bytes.toDouble() / (1L.shl(40)).toDouble())
        bytes >= 1L.shl(30) -> "%.2f GB".format(bytes.toDouble() / (1L.shl(30)).toDouble())
        bytes >= 1L.shl(20) -> "%.2f MB".format(bytes.toDouble() / (1L.shl(20)).toDouble())
        bytes >= 1L.shl(10) -> "%.2f KB".format(bytes.toDouble() / (1L.shl(10)).toDouble())
        else -> "$bytes B"
    }
}

@Composable
private fun DownloadLinkItem(
    title: String?,
    size: String?,
    date: String?,
    files: List<MagnetFile>?,
    filesError: String?,
    onMagnetClick: () -> Unit,
    onExpand: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onMagnetClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (date?.isNotEmpty() == true) {
                        Text(
                            text = date,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (size?.isNotEmpty() == true) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = {
                expanded = !expanded
                if (expanded) onExpand()
            }) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                when {
                    files != null -> {
                        val visible = visibleMagnetFiles(files)
                        val mainIndex = largestVideoIndex(visible)
                        if (visible.isEmpty()) {
                            Text(
                                text = "没有文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        visible.forEachIndexed { index, file ->
                            val isMain = index == mainIndex
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = file.filename,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isMain) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isMain) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (file.size > 0) {
                                    Text(
                                        text = formatFileSize(file.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    filesError != null -> {
                        Text(
                            text = filesError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            text = "加载中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider()
}
