package io.github.javcinema.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.javcinema.data.model.DownloadLink
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(
    keyword: String,
    viewModel: DownloadViewModel = viewModel()
) {
    val btsoResults by viewModel.btsoResults.collectAsState()
    val ciliResults by viewModel.ciliResults.collectAsState()
    val btSearchResults by viewModel.btSearchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val magnetLink by viewModel.magnetLink.collectAsState()
    val isGettingMagnet by viewModel.isGettingMagnet.collectAsState()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    LaunchedEffect(keyword) {
        if (keyword.isNotBlank()) {
            viewModel.search(keyword, "btsearch")
            viewModel.search(keyword, "cili")
            viewModel.search(keyword, "btso")
        }
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
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("magnet-link", magnetLink))
                        viewModel.dismissMagnet()
                    }, modifier = Modifier.weight(1f)) {
                        Text("复制链接")
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
        TabRow(selectedTabIndex = pagerState.currentPage) {
            magnetSources.forEachIndexed { index, (label, _) ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(label) }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            val results = listOf(btSearchResults, ciliResults, btsoResults).getOrElse(page) { btsoResults }

            Box(modifier = Modifier.fillMaxSize()) {
                if (isSearching && results.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (results.isEmpty()) {
                    Text(
                        text = "未找到结果",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = results,
                        key = { it.link ?: it.title ?: it.hashCode().toString() }
                    ) { link ->
                        DownloadLinkItem(
                            link = link,
                            onClick = {
                                val providerNames = listOf("btsearch", "cili", "btso")
                                val providerName = providerNames.getOrElse(page) { "btso" }
                                if (providerName == "btsearch") {
                                    if (!link.filesExpanded) {
                                        link.filesExpanded = true
                                        viewModel.loadBtSearchDetail(link)
                                    } else {
                                        link.filesExpanded = false
                                    }
                                } else {
                                    viewModel.getMagnetLink(link, providerName)
                                }
                            }
                        )
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

@Composable
private fun DownloadLinkItem(
    link: DownloadLink,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = link.title ?: "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (link.files != null) {
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = if (link.filesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (link.filesExpanded) "收起" else "展开"
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            val size = link.size
            val date = link.date
            if (size?.isNotEmpty() == true) {
                Text(
                    text = size,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (date?.isNotEmpty() == true) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = link.filesExpanded && link.files != null) {
            Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                link.files?.forEach { file ->
                    Text(
                        text = file.filename,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    HorizontalDivider()
}
