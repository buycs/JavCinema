package io.github.javcinema.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.github.javcinema.data.model.Actress
import io.github.javcinema.ui.components.ActressFavoriteDialog
import io.github.javcinema.ui.components.InfoChip
import io.github.javcinema.ui.components.SwipeBackContainer

/**
 * 头像直径。取这个值是为了**和右侧胶囊块的高度对齐** ——
 * 满配的 10 个字段在头像右侧排 3 行、每行约 24dp，加上行距正好约 88dp。
 * 改胶囊的字号/内边距时要连这里一起调，否则两边又会对不齐。
 */
private val AVATAR_SIZE = 88.dp

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ActressDetailScreen(
    navController: NavController,
    starId: String,
    name: String,
    imageUrl: String?,
    detailViewModel: ActressDetailViewModel = viewModel(),
    moviesViewModel: MovieListViewModel = viewModel(),
    scrollToTopTrigger: Long = 0L
) {
    val uiState by detailViewModel.uiState.collectAsState()
    var dialogActress by remember { mutableStateOf<Actress?>(null) }
    val moviesUrl = remember(starId) { actressMoviesUrl(starId, null) }

    LaunchedEffect(starId, name, imageUrl) {
        detailViewModel.load(starId, name, imageUrl)
        moviesViewModel.load(moviesUrl)
    }

    SwipeBackContainer(
        onBack = { navController.popBackStack() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                ActressDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ActressDetailUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is ActressDetailUiState.Success -> {
                    val profile = state.profile
                    val actress = profile.actress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = actress.name ?: name,
                            style = MaterialTheme.typography.titleLarge
                        )

                        // 资料降级时明确告知，避免用户以为看到的就是完整信息。
                        profile.warning?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // 头像与右侧胶囊**取同样的高度**：胶囊满配（10 个字段）时约 3 行
                        // ≈ AVATAR_SIZE，两者基本齐平；字段少时头像略高，视觉上仍平衡。
                        // 名字挪到上面单独一行，否则右列会被名字顶高一截，怎么调都对不齐。
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = actress.imageUrl ?: imageUrl,
                                contentDescription = actress.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(AVATAR_SIZE)
                                    .clip(CircleShape)
                                    // 收藏入口：长按头像弹出「复制女优 / 收藏女优」。
                                    // 原来这里有个常驻的「收藏」按钮，但它占了一整行高度，
                                    // 而收藏并不是高频操作 —— 收进长按菜单更省地方。
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { dialogActress = actress }
                                    )
                            )

                            // 资料胶囊：一行一个字段太占高度（8 个字段就顶掉小半屏，把下面的
                            // 作品列表挤下去），改成头像右侧可换行的圆角标签。
                            // 内容由 buildActressInfoRows 组装 —— 没有值的字段不产生胶囊，
                            // 以前这里会把 API 返回的三围对象直接 toString()，
                            // 于是界面上出现 {"T":"163","B":"88",...} 这样的原始 JSON。
                            val rows = buildActressInfoRows(profile)
                            if (rows.isNotEmpty()) {
                                FlowRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    rows.forEach { (label, value) -> InfoChip(label, value) }
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                MovieListScreen(
                    navController = navController,
                    title = name,
                    url = moviesUrl,
                    viewModel = moviesViewModel,
                    scrollToTopTrigger = scrollToTopTrigger,
                    enableSwipeBack = false
                )
            }
        }
    }

    dialogActress?.let { actress ->
        ActressFavoriteDialog(
            actress = actress,
            onDismiss = { dialogActress = null }
        )
    }
}
