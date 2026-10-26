package dev.rosalogia.photoduel.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

// ---- Text ----

/** A mono, upper-case readout: counts, dates and small labels, like a camera's display. */
@Composable
fun Readout(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink.Dim,
    small: Boolean = false,
    maxLines: Int = 1,
) {
    Text(
        text.uppercase(),
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** A section heading: a small accent dot and a readout. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(Ink.Kept, CircleShape))
        Spacer(Modifier.width(10.dp))
        Readout(text)
    }
}

// ---- Buttons ----

private val ButtonHeight = 48.dp
private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)

/** The one main action on a screen: a yellow pill. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = ButtonHeight),
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink.Kept,
            contentColor = Ink.OnKept,
            disabledContainerColor = Ink.Raised,
            disabledContentColor = Ink.Faint,
        ),
    ) { ButtonContent(text, icon) }
}

/** Everyday actions: a graphite pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = ButtonHeight),
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = Ink.Raised,
            contentColor = Ink.Fg,
            disabledContainerColor = Ink.Surface,
            disabledContentColor = Ink.Faint,
        ),
    ) { ButtonContent(text, icon) }
}

/** The only red in the app: an outlined pill with an optional note on how to undo it. */
@Composable
fun TrashButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = ButtonHeight),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Ink.Trash.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.Trash),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ButtonContent(text, BrandIcons.Trash)
            if (note != null) Readout(note, color = Ink.Trash.copy(alpha = 0.6f), small = true)
        }
    }
}

/** Something you might want but probably don't: plain dim text. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ink.Dim,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = Ink.Faint),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A plain icon button in the theme's dim foreground. */
@Composable
fun BrandIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Ink.Fg,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(icon, contentDescription = label, tint = if (enabled) tint else Ink.Faint, modifier = Modifier.size(22.dp))
    }
}

// ---- Bars ----

/**
 * A screen's header: an optional back button, a title with a readout under it, and actions on the
 * right. Every screen uses it, so they all line up.
 */
@Composable
fun BrandTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: ImageVector = BrandIcons.Back,
    backLabel: String = "Back",
    backTag: String = "navBack",
    titleTag: String = "title",
    subtitleTag: String = "subtitle",
    onTitleClick: (() -> Unit)? = null,
    large: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(if (large) 72.dp else 60.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BrandIconButton(backIcon, backLabel, onBack, Modifier.testTag(backTag))
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Column(
            Modifier.weight(1f).then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(titleTag),
                )
            }
            if (subtitle != null) Readout(subtitle, Modifier.testTag(subtitleTag))
        }
        actions()
        Spacer(Modifier.width(4.dp))
    }
}

/** A floating translucent pill for controls over a photo. */
@Composable
fun FloatingBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(34.dp))
            .background(Ink.Raised2.copy(alpha = 0.86f))
            .border(1.dp, Ink.Hair, RoundedCornerShape(34.dp))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A small label over a photo: an accent dot and a readout on a dark chip. */
@Composable
fun PhotoTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Ink.Black.copy(alpha = 0.62f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Readout(text, color = color, small = true)
    }
}

/** One segment per step: done ones in the accent, the current one white, the rest faint. */
@Composable
fun StepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Pick ${step + 1} of $total" },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(total) { i ->
            val color by animateColorAsState(
                when {
                    i < step -> Ink.Kept
                    i == step -> Color.White.copy(alpha = 0.7f)
                    else -> Color.White.copy(alpha = 0.18f)
                },
                tween(220),
            )
            Box(Modifier.weight(1f).height(2.dp).background(color, RoundedCornerShape(1.dp)))
        }
    }
}

// ---- The mark ----

/** Eases out hard, like a lens settling. */
private val Settle = CubicBezierEasing(0.65f, 0f, 0.15f, 1f)

/**
 * The focus ring: a thin circle that closes when a choice is made. With [animate] it draws itself
 * once when it first appears and calls [onClosed] as it closes.
 */
@Composable
fun Ring(
    modifier: Modifier = Modifier,
    color: Color = Ink.Kept,
    stroke: Dp = 2.dp,
    animate: Boolean = true,
    onClosed: (() -> Unit)? = null,
) {
    val progress = remember { Animatable(if (animate) 0.12f else 1f) }
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(Unit) {
        if (animate) {
            progress.animateTo(1f, tween(durationMillis = 520, delayMillis = 120, easing = Settle))
            closed?.invoke()
        }
    }
    Canvas(modifier) {
        val w = stroke.toPx()
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.value,
            useCenter = false,
            topLeft = Offset(w / 2, w / 2),
            size = Size(size.width - w, size.height - w),
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
    }
}

/**
 * The app icon drawn in place: two overlapping frames, the kept one in front with an accent edge.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val w = 1.6f * u
        val r = androidx.compose.ui.geometry.CornerRadius(1.6f * u)
        drawRoundRect(
            color = Ink.Faint,
            topLeft = Offset(3.5f * u, 6.5f * u),
            size = Size(10.5f * u, 13.5f * u),
            cornerRadius = r,
            style = Stroke(width = w),
        )
        drawRoundRect(color = Ink.Fg, topLeft = Offset(10f * u, 4f * u), size = Size(10.5f * u, 13.5f * u), cornerRadius = r)
        drawRoundRect(
            color = Ink.Kept,
            topLeft = Offset(10f * u, 4f * u),
            size = Size(10.5f * u, 13.5f * u),
            cornerRadius = r,
            style = Stroke(width = w),
        )
    }
}

// ---- Photos ----

/** Where photos are shown: plain black, edge to edge. */
@Composable
fun PhotoStage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(color = Ink.Black, contentColor = Ink.Fg, modifier = modifier.fillMaxSize(), content = content)
}

/** A square-cropped thumbnail with a placeholder while it loads. */
@Composable
fun PhotoThumb(mediaId: Long, modifier: Modifier = Modifier, corner: Dp = 6.dp, alpha: Float = 1f) {
    AsyncImage(
        model = PhotoRef(mediaId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier.clip(RoundedCornerShape(corner)).background(Ink.Raised),
    )
}

// ---- Moments ----

/**
 * A quiet full-screen message: an optional ring, a large light headline, a line of body text and
 * any actions. Used for empty states and first-run screens.
 */
@Composable
fun Moment(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    ring: Boolean = false,
    mark: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier.widthIn(max = 480.dp).padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (ring) Ring(Modifier.size(40.dp), stroke = 2.5.dp)
        if (mark) BrandMark(Modifier.size(56.dp))
        Text(title, style = MaterialTheme.typography.displaySmall)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge, color = Ink.Dim)
        content()
    }
}

/** A card: the surface color with large rounded corners, no border or shadow. */
@Composable
fun BrandCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(Ink.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        content = content,
    )
}

// ---- Haptics ----

/** Haptics named by what they mean here. Silent on the desktop. */
class Haptics(private val feedback: HapticFeedback) {
    /** Flipping between the two photos. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A choice made. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something removed. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
