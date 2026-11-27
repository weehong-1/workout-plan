package com.vitrack.app.ui.weight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.vitrack.app.R
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.ui.common.Format
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Y axis with three gridlines at whole numbers that cover all [values]. */
internal data class WeightAxis(val low: Double, val high: Double) {
    val mid: Double get() = (low + high) / 2
}

internal fun weightAxis(values: List<Double>): WeightAxis {
    var low = floor(values.min())
    var high = ceil(values.max())
    // At least 2 units of range, so small day-to-day changes don't look dramatic.
    if (high - low < 2) {
        val centre = (low + high) / 2
        low = floor(centre - 1)
        high = low + 2
    }
    // Even range so the middle gridline also lands on a whole number.
    if ((high - low).toInt() % 2 != 0) high += 1
    return WeightAxis(low, high)
}

/**
 * Smooth trend line over [start]..[end] in the weight colour, with the area
 * below lightly filled and the latest point ringed in [ringColor] (the card
 * behind). Points are placed by day, so gaps show.
 *
 * Motion, like the wavy indicators on Activity: the line draws in from the
 * left, then a small wave keeps flowing along it. The wave flattens to
 * nothing at both ends, so the end point sits exactly on its value.
 */
@Composable
fun WeightTrendChart(
    points: List<ChartPoint>,
    unit: WeightUnit,
    start: LocalDate,
    end: LocalDate,
    color: Color,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.weight_chart_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val values = points.map { unit.fromKg(it.kg) }
    val axis = weightAxis(values)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val description = stringResource(
        R.string.weight_chart_description,
        Format.weight(points.first().kg, unit),
        Format.weight(points.last().kg, unit),
        Format.unitLabel(unit),
        points.size,
    )
    val totalDays = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1).toFloat()

    // Draw-in whenever the data, range or unit changes.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points, start, unit) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    // The wave's phase runs continuously, one wavelength every 2 s.
    val phase by rememberInfiniteTransition(label = "weight wave").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = description }) {
        val axisLabelWidth = 28.dp.toPx()
        val bottomLabelHeight = 18.dp.toPx()
        val dot = 9.dp.toPx()
        val plotLeft = dot
        val plotRight = size.width - axisLabelWidth - dot
        val plotTop = 10.dp.toPx()
        val plotBottom = size.height - bottomLabelHeight - 8.dp.toPx()

        fun y(v: Double) = (plotBottom - (v - axis.low) / (axis.high - axis.low) * (plotBottom - plotTop)).toFloat()
        fun x(date: LocalDate) = plotLeft + ChronoUnit.DAYS.between(start, date) / totalDays * (plotRight - plotLeft)

        listOf(axis.high, axis.mid, axis.low).forEach { v ->
            val gy = y(v)
            drawLine(gridColor, Offset(0f, gy), Offset(size.width - axisLabelWidth, gy), strokeWidth = 1.dp.toPx())
            val text = measurer.measure(axisLabel(v), labelStyle)
            drawText(text, topLeft = Offset(size.width - text.size.width, gy - text.size.height / 2f))
        }

        // Smooth curve: each segment is a cubic with both control points at the
        // horizontal midpoint, so it never overshoots between points.
        val offsets = points.mapIndexed { i, p -> Offset(x(p.date), y(values[i])) }
        val line = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.zipWithNext { a, b ->
                val midX = (a.x + b.x) / 2
                cubicTo(midX, a.y, midX, b.y, b.x, b.y)
            }
        }
        val progress = reveal.value
        if (points.size > 1) {
            val area = Path().apply {
                addPath(line)
                lineTo(offsets.last().x, plotBottom)
                lineTo(offsets.first().x, plotBottom)
                close()
            }
            val wavy = wavyAlong(
                line,
                amplitude = 2.5.dp.toPx(),
                wavelength = 28.dp.toPx(),
                taper = 16.dp.toPx(),
                phase = phase,
            )
            // Reveal left to right; the half-stroke margin keeps the round cap whole.
            val revealX = offsets.first().x + progress * (offsets.last().x - offsets.first().x) + 3.dp.toPx()
            clipRect(right = revealX) {
                drawPath(area, color.copy(alpha = 0.16f * progress))
                drawPath(wavy, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        // The latest point pops in as the line reaches it, with a small overshoot.
        val last = offsets.last()
        val dotScale = DotPop.transform(((progress - 0.8f) / 0.2f).coerceIn(0f, 1f))
        if (dotScale > 0f) {
            drawCircle(ringColor, radius = (dot + 2.dp.toPx()) * dotScale, center = last)
            drawCircle(color, radius = (dot - 2.dp.toPx()) * dotScale, center = last)
        }

        val firstLabel = measurer.measure(Format.dayMonth(points.first().date), labelStyle)
        val lastLabel = measurer.measure(Format.dayMonth(points.last().date), labelStyle)
        val labelY = size.height - firstLabel.size.height.toFloat()
        val firstX = (x(points.first().date) - firstLabel.size.width / 2f).coerceAtLeast(0f)
        drawText(firstLabel, topLeft = Offset(firstX, labelY))
        if (points.size > 1) {
            val lastX = (last.x - lastLabel.size.width / 2f).coerceAtMost(size.width - lastLabel.size.width)
            // Skip the end label when it would overlap the start label.
            if (lastX > firstX + firstLabel.size.width + 8.dp.toPx()) drawText(lastLabel, topLeft = Offset(lastX, labelY))
        }
    }
}

/**
 * [path] with a sine wave of [amplitude] laid along it, perpendicular to the
 * path, shifted by [phase] (0..1 of a wavelength). The wave fades in and out
 * over [taper] at each end so the path's endpoints stay put.
 */
private fun wavyAlong(path: Path, amplitude: Float, wavelength: Float, taper: Float, phase: Float): Path {
    val measure = PathMeasure().apply { setPath(path, false) }
    val length = measure.length
    val out = Path()
    if (length <= 0f) return out
    val step = 2f
    var d = 0f
    var first = true
    while (true) {
        val at = min(d, length)
        val position = measure.getPosition(at)
        val tangent = measure.getTangent(at)
        val edge = min(at, length - at)
        val fade = (edge / taper).coerceIn(0f, 1f)
        val offset = amplitude * fade * sin(2 * PI * (at / wavelength - phase)).toFloat()
        // Normal is the tangent turned 90°.
        val px = position.x - tangent.y * offset
        val py = position.y + tangent.x * offset
        if (first) out.moveTo(px, py) else out.lineTo(px, py)
        first = false
        if (at >= length) break
        d += step
    }
    return out
}

/** Ease out with a slight overshoot (peaks about 1.1, settles at 1). */
private val DotPop = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

private fun axisLabel(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else Format.oneDecimal(v)
