package io.github.javcinema.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun ZoomableImage(
    imageUrl: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    // 基于实际绘制内容(ContentScale.Fit)计算的最大偏移量：
    // 图片(放大后)没超过屏幕的轴向上 max=0，即该方向不可拖动；
    // 超过时图片边缘恰好对齐屏幕边界，不会越过。
    val maxOffsetsOf: (Float) -> Offset = { zoom ->
        val bw = if (boxSize.width > 0) boxSize.width.toFloat() else 0f
        val bh = if (boxSize.height > 0) boxSize.height.toFloat() else 0f
        val iw = imageSize.width.takeIf { it > 0f } ?: bw
        val ih = imageSize.height.takeIf { it > 0f } ?: bh
        val fit = if (bw > 0f && iw > 0f) min(bw / iw, bh / ih) else 1f
        val maxX = ((iw * fit * zoom - bw) / 2f).coerceAtLeast(0f)
        val maxY = ((ih * fit * zoom - bh) / 2f).coerceAtLeast(0f)
        Offset(maxX, maxY)
    }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        val target = if (scale.value > 1f) 1f else 3f
                        scope.launch {
                            scale.animateTo(
                                target,
                                tween(250, easing = FastOutSlowInEasing)
                            )
                        }
                        scope.launch {
                            offset.animateTo(
                                Offset.Zero,
                                tween(250, easing = FastOutSlowInEasing)
                            )
                        }
                    },
                    onLongPress = { onLongPress() }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    var isPinching = false
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.size >= 2 -> {
                                isPinching = true
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val newScale = (scale.value * zoomChange).coerceIn(1f, 5f)
                                val max = maxOffsetsOf(newScale)
                                scope.launch {
                                    scale.snapTo(newScale)
                                    offset.snapTo(
                                        Offset(
                                            x = (offset.value.x + panChange.x).coerceIn(-max.x, max.x),
                                            y = (offset.value.y + panChange.y).coerceIn(-max.y, max.y)
                                        )
                                    )
                                }
                                event.changes.forEach { it.consume() }
                            }
                            pressed.size == 1 && !isPinching -> {
                                val change = pressed.first()
                                val delta = change.position - change.previousPosition
                                val currentScale = scale.value
                                if (currentScale > 1f && delta != Offset.Zero) {
                                    val max = maxOffsetsOf(currentScale)
                                    val maxX = max.x
                                    val maxY = max.y
                                    val cur = offset.value
                                    val rawX = cur.x + delta.x
                                    val rawY = cur.y + delta.y
                                    val targetX = rawX.coerceIn(-maxX, maxX)
                                    val targetY = rawY.coerceIn(-maxY, maxY)

                                    // 到达左右边界后继续朝外拖：不再消费，交给 pager
                                    // 由 pager 跟随手指过渡带出上一张/下一张，松手后吸附
                                    val atRightEdgeOutward = maxX > 0f && targetX >= maxX && rawX > maxX && delta.x > 0f
                                    val atLeftEdgeOutward = maxX > 0f && targetX <= -maxX && rawX < -maxX && delta.x < 0f
                                    if (atRightEdgeOutward || atLeftEdgeOutward) {
                                        // 不消费，让外层 HorizontalPager 接管翻页过渡
                                    } else {
                                        // 上下朝外越过边界时允许轻微越界，松手后自动弹回
                                        val overY =
                                            if (rawY > maxY) rawY - maxY
                                            else if (rawY < -maxY) rawY + maxY
                                            else 0f
                                        val appliedY =
                                            if (overY != 0f) targetY + overY * 0.3f else targetY

                                        scope.launch {
                                            offset.snapTo(Offset(targetX, appliedY))
                                        }
                                        change.consume()
                                    }
                                } else {
                                    // 未放大时图片未越界，不消费，交给 pager 翻页
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // 松手后越界部分弹回边界
                    if (scale.value > 1f) {
                        val maxY = maxOffsetsOf(scale.value).y
                        val cur = offset.value
                        if (cur.y > maxY || cur.y < -maxY) {
                            scope.launch {
                                offset.animateTo(
                                    Offset(cur.x, cur.y.coerceIn(-maxY, maxY)),
                                    spring(dampingRatio = 0.7f, stiffness = 300f)
                                )
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                imageSize = state.painter.intrinsicSize
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
                }
        )
    }

    // 放大时按返回键先取消放大
    BackHandler(enabled = scale.value > 1f) {
        scope.launch {
            scale.animateTo(
                1f,
                tween(250, easing = FastOutSlowInEasing)
            )
        }
        scope.launch {
            offset.animateTo(
                Offset.Zero,
                tween(250, easing = FastOutSlowInEasing)
            )
        }
    }
}