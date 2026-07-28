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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.javcinema.data.model.DownloadLink

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
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(keyword) {
        if (keyword.isNotBlank()) {
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
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("magnet-link", magnetLink))
                    viewModel.dismissMagnet()
                }) {
                    Text("复制到剪贴板")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMagnet() }) {
                    Text("关闭")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val magnetSources = listOf(
            "BtSearch" to "btsearch",
            "Cili" to "cili",
            "BTSOW" to "btso"
        )
        TabRow(selectedTabIndex = selectedTab) {
            magnetSources.forEachIndexed { index, (label, provider) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        viewModel.search(keyword, provider)
                    },
                    text = { Text(label) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            val results = listOf(btSearchResults, ciliResults, btsoResults).getOrElse(selectedTab) { btsoResults }

            if (results.isEmpty() && !isSearching) {
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
                            val providerName = providerNames.getOrElse(selectedTab) { "btso" }
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
