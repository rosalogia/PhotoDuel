package dev.rosalogia.photoduel.feature.duel

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Zoom and pan shared by both photos, so flipping keeps the same spot on screen. */
@Stable
class ZoomState {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport by mutableStateOf(IntSize.Zero)
        internal set

    val isZoomed: Boolean get() = scale > 1.01f

    /** Zoom by [factor] about [focus] (in view coordinates), then pan by [pan]. */
    fun transform(factor: Float, focus: Offset, pan: Offset) {
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val f = newScale / scale
        // Keep the content point under the focus fixed while scaling about the view center.
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val newOffset = (offset - (focus - center)) * f + (focus - center) + pan
        scale = newScale
        offset = clamp(newOffset)
    }

    /** Toggles between fit and [targetScale] centered on [focus]. */
    fun toggle(focus: Offset, targetScale: Float = DOUBLE_TAP_SCALE) {
        if (isZoomed) reset() else transform(targetScale / scale, focus, Offset.Zero)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clamp(o: Offset): Offset {
        val maxX = viewport.width * (scale - 1) / 2
        val maxY = viewport.height * (scale - 1) / 2
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 8f
        const val DOUBLE_TAP_SCALE = 3f
    }
}

/**
 * Shows [king], or [challenger] while [showChallenger] is true, in exactly the same place.
 *
 * Gestures, when not zoomed: touching the photo shows the challenger until release; a vertical
 * swipe picks (up keeps the challenger, down keeps the king). Pinch zooms; when zoomed, one finger
 * pans and flipping is done with the hold button instead. Double-tap toggles zoom.
 */
@Composable
fun FlipView(
    king: ImageBitmap,
    challenger: ImageBitmap,
    showChallenger: Boolean,
    zoom: ZoomState,
    onPress: (Boolean) -> Unit,
    onSwipe: (up: Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val press by rememberUpdatedState(onPress)
    val swipe by rememberUpdatedState(onSwipe)
    val drag by rememberUpdatedState(onDrag)
    val lastTap = remember { LastTap() }

    Box(
        modifier
            .onSizeChanged { zoom.viewport = it }
            .testTag("flipView")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val zoomedAtStart = zoom.isZoomed
                    var flipping = !zoomedAtStart
                    if (flipping) press(true)
                    var multiTouch = false
                    var total = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed >= 2) {
                            if (flipping) {
                                press(false)
                                flipping = false
                            }
                            if (!multiTouch) drag(0f)
                            multiTouch = true
                            zoom.transform(event.calculateZoom(), event.calculateCentroid(), event.calculatePan())
                            event.changes.forEach { it.consume() }
                        } else if (!multiTouch) {
                            val pan = event.calculatePan()
                            total += pan
                            if (zoom.isZoomed) {
                                zoom.transform(1f, Offset.Zero, pan)
                            } else if (!zoomedAtStart) {
                                drag(total.y)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                    if (flipping) press(false)
                    drag(0f)
                    if (multiTouch) return@awaitEachGesture

                    val isTap = total.getDistance() < touchSlop
                    if (isTap) {
                        val now = down.uptimeMillis
                        if (now - lastTap.time < doubleTapTimeout && (down.position - lastTap.position).getDistance() < touchSlop * 4) {
                            zoom.toggle(down.position)
                            lastTap.time = 0
                        } else {
                            lastTap.time = now
                            lastTap.position = down.position
                        }
                    } else if (!zoomedAtStart && abs(total.y) > swipeThreshold && abs(total.y) > 1.5f * abs(total.x)) {
                        swipe(total.y < 0)
                    }
                }
            },
    ) {
        val layer = Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom.scale
            scaleY = zoom.scale
            translationX = zoom.offset.x
            translationY = zoom.offset.y
        }
        // Both stay composed; only alpha changes, so a flip never re-decodes or re-uploads.
        Image(king, contentDescription = "Current pick", contentScale = ContentScale.Fit,
            modifier = layer.graphicsLayer { alpha = if (showChallenger) 0f else 1f })
        Image(challenger, contentDescription = "Challenger", contentScale = ContentScale.Fit,
            modifier = layer.graphicsLayer { alpha = if (showChallenger) 1f else 0f })
    }
}

private class LastTap {
    var time = 0L
    var position = Offset.Zero
}
