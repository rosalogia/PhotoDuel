package dev.rosalogia.photoduel.feature.duel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.rosalogia.photoduel.core.ml.NormRect
import dev.rosalogia.photoduel.feature.duel.align.Alignment
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

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

    /** Zooms to [targetScale] with [point] (view coordinates at fit) moved to the center. */
    fun zoomTo(point: Offset, targetScale: Float) {
        scale = targetScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        offset = clamp(-(point - center) * scale)
    }

    /** Toggles between fit and [targetScale] centered on [focus]. */
    fun toggle(focus: Offset, targetScale: Float = DOUBLE_TAP_SCALE) {
        if (isZoomed) reset() else transform(targetScale / scale, focus, Offset.Zero)
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    /** Zooms by [factor] about the middle of the view, e.g. from the keyboard. */
    fun zoomBy(factor: Float) = transform(factor, Offset(viewport.width / 2f, viewport.height / 2f), Offset.Zero)

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
 * Gestures, when not zoomed: touching the photo shows the challenger until release; a horizontal
 * swipe picks (right keeps the challenger, left keeps the king, matching the buttons), showing the
 * photo it would keep while the finger moves. Vertical drags
 * are left unconsumed for a parent, such as the feed's pager. Pinch zooms; when zoomed, one finger
 * pans and flipping is done with the hold button instead. Double-tap toggles zoom.
 */
@Composable
fun FlipView(
    king: ImageBitmap,
    challenger: ImageBitmap,
    alignment: Alignment?,
    /** Normalized box in the king's frame; double-tap zooms here instead of to the tap point. */
    focusFace: NormRect?,
    showChallenger: Boolean,
    zoom: ZoomState,
    onPress: (Boolean) -> Unit,
    onSwipe: (keepChallenger: Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** A hairline drawn around the photo as it appears on screen, zoom included; null for none. */
    frameColor: Color? = null,
    frameWidth: Dp = 2.dp,
    /** A label pinned inside the photo's top-left corner. */
    tag: (@Composable () -> Unit)? = null,
) {
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val press by rememberUpdatedState(onPress)
    val swipe by rememberUpdatedState(onSwipe)
    val drag by rememberUpdatedState(onDrag)
    val lastTap = remember { LastTap() }
    val face by rememberUpdatedState(focusFace)
    val kingSize by rememberUpdatedState(IntSize(king.width, king.height))

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { zoom.viewport = it }
            .testTag("flipView")
            // Mouse wheel or trackpad scroll zooms about the pointer, like most photo viewers.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val dy = change.scrollDelta.y
                        if (dy != 0f) {
                            zoom.transform(SCROLL_ZOOM.pow(-dy), change.position, Offset.Zero)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val zoomedAtStart = zoom.isZoomed
                    var flipping = !zoomedAtStart
                    if (flipping) press(true)
                    var multiTouch = false
                    var total = Offset.Zero
                    // Decided once the finger passes touch slop: horizontal picks, vertical goes to the parent.
                    var horizontal: Boolean? = null
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
                                event.changes.forEach { it.consume() }
                            } else if (!zoomedAtStart) {
                                if (horizontal == null && total.getDistance() > touchSlop) {
                                    horizontal = abs(total.x) > abs(total.y)
                                    if (horizontal == false && flipping) {
                                        press(false)
                                        flipping = false
                                    }
                                }
                                if (horizontal == true) {
                                    // Show the photo this swipe would keep: right is the challenger, left the king.
                                    if (flipping) press(total.x > 0)
                                    drag(total.x)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                    if (flipping) press(false)
                    drag(0f)
                    if (multiTouch) return@awaitEachGesture

                    val isTap = total.getDistance() < touchSlop
                    if (isTap) {
                        val now = down.uptimeMillis
                        if (now - lastTap.time < doubleTapTimeout && (down.position - lastTap.position).getDistance() < touchSlop * 4) {
                            val f = face
                            if (!zoom.isZoomed && f != null) {
                                zoomToFace(zoom, f, kingSize)
                            } else {
                                zoom.toggle(down.position)
                            }
                            lastTap.time = 0
                        } else {
                            lastTap.time = now
                            lastTap.position = down.position
                        }
                    } else if (horizontal == true && abs(total.x) > swipeThreshold && abs(total.x) > 1.5f * abs(total.y)) {
                        swipe(total.x > 0)
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
        Canvas(layer.graphicsLayer { alpha = if (showChallenger) 0f else 1f }) {
            drawWithMatrix(king, fitMatrix(king, size))
        }
        Canvas(layer.graphicsLayer { alpha = if (showChallenger) 1f else 0f }) {
            val matrix = if (alignment != null) alignedMatrix(challenger, king, alignment, size) else fitMatrix(challenger, size)
            drawWithMatrix(challenger, matrix)
        }
        if (frameColor != null || tag != null) {
            // An unaligned challenger may have another shape; aligned, it sits exactly over the king.
            val shown = if (showChallenger && alignment == null) challenger else king
            val rect = visibleRect(shown, zoom)
            if (frameColor != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = frameWidth.toPx()
                    drawRoundRect(
                        color = frameColor,
                        topLeft = Offset(rect.left + w / 2, rect.top + w / 2),
                        size = Size(rect.width - w, rect.height - w),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(w),
                    )
                }
            }
            if (tag != null) {
                Box(Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }.padding(10.dp)) { tag() }
            }
        }
    }
}

/** Where [image] appears in the view, after fitting and the current zoom, clipped to the view. */
private fun visibleRect(image: ImageBitmap, zoom: ZoomState): Rect {
    val view = zoom.viewport
    if (view.width == 0 || view.height == 0) return Rect.Zero
    val s = min(view.width.toFloat() / image.width, view.height.toFloat() / image.height)
    val w = image.width * s
    val h = image.height * s
    val cx = view.width / 2f
    val cy = view.height / 2f
    // The zoom layer scales about the view's center, then translates.
    fun x(v: Float) = cx + (v - cx) * zoom.scale + zoom.offset.x
    fun y(v: Float) = cy + (v - cy) * zoom.scale + zoom.offset.y
    return Rect(
        left = x(cx - w / 2).coerceAtLeast(0f),
        top = y(cy - h / 2).coerceAtLeast(0f),
        right = x(cx + w / 2).coerceAtMost(view.width.toFloat()),
        bottom = y(cy + h / 2).coerceAtMost(view.height.toFloat()),
    )
}

/** Fills about half the view height with the face, centered. */
private fun zoomToFace(zoom: ZoomState, face: NormRect, image: IntSize) {
    val view = zoom.viewport
    val s = min(view.width.toFloat() / image.width, view.height.toFloat() / image.height)
    val dx = (view.width - image.width * s) / 2
    val dy = (view.height - image.height * s) / 2
    val center = Offset(dx + face.centerX * image.width * s, dy + face.centerY * image.height * s)
    val faceHeight = face.height * image.height * s
    zoom.zoomTo(center, (0.5f * view.height / faceHeight).coerceIn(1.5f, ZoomState.MAX_SCALE))
}

/** A row-major 3x3 matrix mapping image pixels to view pixels. */
private class Transform(val m: DoubleArray) {
    operator fun times(o: Transform) = Transform(
        DoubleArray(9) { i ->
            val r = i / 3
            val c = i % 3
            m[r * 3] * o.m[c] + m[r * 3 + 1] * o.m[3 + c] + m[r * 3 + 2] * o.m[6 + c]
        },
    )

    fun toMatrix() = Matrix().apply {
        values[Matrix.ScaleX] = m[0].toFloat()
        values[Matrix.SkewX] = m[1].toFloat()
        values[Matrix.TranslateX] = m[2].toFloat()
        values[Matrix.SkewY] = m[3].toFloat()
        values[Matrix.ScaleY] = m[4].toFloat()
        values[Matrix.TranslateY] = m[5].toFloat()
        values[Matrix.Perspective0] = m[6].toFloat()
        values[Matrix.Perspective1] = m[7].toFloat()
        values[Matrix.Perspective2] = m[8].toFloat()
    }

    companion object {
        fun scale(sx: Double, sy: Double) = Transform(doubleArrayOf(sx, 0.0, 0.0, 0.0, sy, 0.0, 0.0, 0.0, 1.0))
    }
}

private fun DrawScope.drawWithMatrix(image: ImageBitmap, transform: Transform) {
    withTransform({ transform(transform.toMatrix()) }) {
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstSize = IntSize(image.width, image.height),
            filterQuality = FilterQuality.High,
        )
    }
}

/** Scales [image] to fit [size], centered, like ContentScale.Fit. */
private fun fitMatrix(image: ImageBitmap, size: Size): Transform {
    val s = min(size.width / image.width, size.height / image.height).toDouble()
    val tx = (size.width - image.width * s) / 2
    val ty = (size.height - image.height * s) / 2
    return Transform(doubleArrayOf(s, 0.0, tx, 0.0, s, ty, 0.0, 0.0, 1.0))
}

/**
 * Challenger bitmap px -> normalized challenger -> (homography) normalized king -> king bitmap px
 * -> view, so the challenger lands exactly over the king. The GPU applies the warp at draw time.
 */
private fun alignedMatrix(challenger: ImageBitmap, king: ImageBitmap, alignment: Alignment, size: Size): Transform =
    fitMatrix(king, size) *
        Transform.scale(king.width.toDouble(), king.height.toDouble()) *
        Transform(alignment.h) *
        Transform.scale(1.0 / challenger.width, 1.0 / challenger.height)

/** Zoom per scroll step; a mouse wheel notch is one step, trackpads send fractions. */
private const val SCROLL_ZOOM = 1.2f

private class LastTap {
    var time = 0L
    var position = Offset.Zero
}
