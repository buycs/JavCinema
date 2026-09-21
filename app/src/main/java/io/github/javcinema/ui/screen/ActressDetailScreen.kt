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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.github.javcinema.data.model.Actress
import io.github.javcinema.ui.components.INFO_CHIP_HORIZONTAL_PADDING
import io.github.javcinema.ui.components.ActressFavoriteDialog
import io.github.javcinema.ui.components.InfoChip
import io.github.javcinema.ui.components.SwipeBackContainer

/**
 * 头像直径。取这个值是为了**和右侧的三行标题等高对齐** ——
 * 名称（`titleMedium` 行高 24）+ 作品数 + 最近作品（胶囊各约 20，含 2dp 上下内边距）
 * + 两个 4dp 行距 ≈ 72dp。
 * 改这三行的字号/行距或 `Column` 的 `spacedBy` 时要连这里一起调，否则又会对不齐。
 */
private val AVATAR_SIZE = 72.dp

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
        MovieListScreen(
            navController = navController,
            title = name,
            url = moviesUrl,
            viewModel = moviesViewModel,
            scrollToTopTrigger = scrollToTopTrigger,
            enableSwipeBack = false,
            // 名片栏成了影片网格的第 0 项：不固定，跟列表一起滚进滚出；
            // 双击底栏回到顶部时它也跟着重新露出来。
            headerContent = {
                ActressProfileHeader(
                    state = uiState,
                    fallbackName = name,
                    fallbackImageUrl = imageUrl,
                    onAvatarLongClick = { dialogActress = it }
                )
            }
        )
    }

    dialogActress?.let { actress ->
        ActressFavoriteDialog(
            actress = actress,
            onDismiss = { dialogActress = null }
        )
    }
}

/**
 * 女优资料胶囊。特殊处理只有一处：「作品数 4617 部 · 可下载 2433」里的**「可下载」三个字
 * 不加粗** —— 胶囊的值整串走 `labelMedium`（中等字重），三个 token 一样粗时
 * 「可下载」会被读成第三个数字，所以把它降回标签的字重和颜色。
 */
@Composable
private fun ActressInfoChip(label: String, value: String) {
    if (label != LABEL_MOVIE_COUNT || !value.contains(LABEL_DOWNLOADABLE)) {
        InfoChip(label, value)
        return
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val (head, tail) = value.split(LABEL_DOWNLOADABLE, limit = 2)
    InfoChip(
        label = label,
        value = buildAnnotatedString {
            append(head)
            // 只改字重和颜色，**不改字号** —— 混进同一行的片段必须和两侧数字一样高。
            withStyle(SpanStyle(color = labelColor, fontWeight = FontWeight.Normal)) {
                append(LABEL_DOWNLOADABLE)
            }
            append(tail)
        }
    )
}

/**
 * 女优名片信息栏，作为 `MovieListScreen` 网格的顶部整行渲染（见 `headerContent`），
 * 所以左右上下内边距只留 8dp：网格自身已有 8dp 的 `contentPadding`，
 * 加起来才是原先那 16dp 的视觉缩进。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ActressProfileHeader(
    state: ActressDetailUiState,
    fallbackName: String,
    fallbackImageUrl: String?,
    onAvatarLongClick: (Actress) -> Unit
) {
    when (state) {
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 资料降级时明确告知，避免用户以为看到的就是完整信息。
                profile.warning?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                val (headline, details) =
                    splitActressHeadline(buildActressInfoRows(profile))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = actress.imageUrl ?: fallbackImageUrl,
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
                                onLongClick = { onAvatarLongClick(actress) }
                            )
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 名称挤掉了原来的 titleLarge：右侧列比整屏窄一截，
                        // 长艺名（欧美女优常见）在这里必须单行不折行。
                        Text(
                            text = actress.name ?: fallbackName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // 胶囊里的文字被内边距往里推了 8dp，名称按同样的量缩进，
                            // 三行的左侧才是一条直线。
                            modifier = Modifier.padding(
                                start = INFO_CHIP_HORIZONTAL_PADDING
                            )
                        )
                        // 作品数、最近作品各占一行，整块与头像等高对齐。
                        headline.forEach { (label, value) -> ActressInfoChip(label, value) }
                    }
                }

                // 生日一直到兴趣的这些字段：不待在头像右侧那一列里（那里只有屏宽减去
                // 头像的宽度，一行塞不下两个胶囊就全散着），而是挪到头像下面，
                // 从内容区左边缘顶格起、**铺满一行再换行**。
                // 内容全由 buildActressInfoRows 组装 —— 没有值的字段不产生胶囊，
                // 以前这里会把 API 返回的三围对象直接 toString()，
                // 于是界面上出现 {"T":"163","B":"88",...} 这样的原始 JSON。
                if (details.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        // 行距比横向间距小：胶囊本身有圆角和上下内边距，行距再给到 4dp
                        // 就散成了「一行一条字段」的观感。
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        details.forEach { (label, value) -> ActressInfoChip(label, value) }
                    }
                }
            }
        }
    }
}
