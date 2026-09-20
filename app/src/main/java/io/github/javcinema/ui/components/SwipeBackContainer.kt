package io.github.javcinema.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SwipeBackContainer(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // mutableFloatStateOf：Float 状态不必装箱，滑动过程中每帧都会读写它。
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenWidthPx: Float = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > screenWidthPx * 0.25f) {
                            scope.launch {
                                animate(offsetX, screenWidthPx) { value, _ -> offsetX = value }
                            }
                            onBack()
                        } else {
                            scope.launch {
                                animate(offsetX, 0f) { value, _ -> offsetX = value }
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(0f, screenWidthPx)
                    }
                )
            }
            .offset { IntOffset(offsetX.roundToInt(), 0) }
    ) {
        content()
    }
}
