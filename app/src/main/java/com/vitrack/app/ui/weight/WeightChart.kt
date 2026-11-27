package com.vitrack.app.ui.weight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vitrack.app.data.weight.WeightPeriod
import com.vitrack.app.ui.common.rememberReducedMotion
import com.vitrack.app.ui.theme.VitrackTheme

/**
 * Period averages as a line, with each period's lowest-to-highest weigh-in
 * as a soft bar behind its point. Points are green where the average fell
 * from the period before and red where it rose. Periods are evenly spaced.
 *
 * Motion: bars fade in, the line draws left to right, then the points pop
 * in. With reduced motion everything shows at once.
 */
@Composable
fun WeightPeriodChart(
    periods: List<WeightPeriod>,
    description: String,
    ringColor: Color,
    modifier: Modifier = Modifier,
) {
    val weight = VitrackTheme.metrics.weight
    val trend = VitrackTheme.metrics.trend
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    val bandColor = weight.graphic.copy(alpha = 0.24f)
    val reducedMotion = rememberReducedMotion()

    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(periods) {
        if (reducedMotion) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 1_400, easing = LinearOutSlow))
        }
    }

    Canvas(modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
        if (periods.isEmpty()) return@Canvas
        val inset = 14.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()
        var lo = periods.minOf { it.minKg }
        var hi = periods.maxOf { it.maxKg }
        if (hi - lo < 1.0) {
            val mid = (hi + lo) / 2
            lo = mid - 0.5
            hi = mid + 0.5
        }
        fun x(i: Int) = if (periods.size == 1) size.width / 2 else inset + i * (size.width - 2 * inset) / (periods.size - 1)
        fun y(kg: Double) = (top + (hi - kg) / (hi - lo) * (bottom - top)).toFloat()

        listOf(0.25f, 0.5f, 0.75f).forEach { f ->
            val gy = top + f * (bottom - top)
            drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
        }

        val p = progress.value
        // Phases on one clock: bars 0–0.4, line 0.1–0.8, points 0.7–1.
        val barAlpha = (p / 0.4f).coerceIn(0f, 1f)
        val lineAmount = ((p - 0.1f) / 0.7f).coerceIn(0f, 1f)
        val dotAmount = PointPop.transform(((p - 0.7f) / 0.3f).coerceIn(0f, 1f))

        val barWidth = 8.dp.toPx()
        periods.forEachIndexed { i, period ->
            val yTop = y(period.maxKg) - barWidth / 2
            val yBottom = y(period.minKg) + barWidth / 2
            drawRoundRect(
                color = bandColor.copy(alpha = bandColor.alpha * barAlpha),
                topLeft = Offset(x(i) - barWidth / 2, yTop),
                size = Size(barWidth, yBottom - yTop),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }

        if (periods.size > 1 && lineAmount > 0f) {
            val line = Path().apply {
                periods.forEachIndexed { i, period ->
                    if (i == 0) moveTo(x(i), y(period.averageKg)) else lineTo(x(i), y(period.averageKg))
                }
            }
            val measure = PathMeasure().apply { setPath(line, false) }
            val drawn = Path()
            measure.getSegment(0f, measure.length * lineAmount, drawn, true)
            drawPath(drawn, weight.graphic, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        if (dotAmount > 0f) {
            val radius = 6.dp.toPx() * dotAmount
            val ring = 2.5.dp.toPx() * dotAmount
            periods.forEachIndexed { i, period ->
                val rose = i > 0 && period.averageKg > periods[i - 1].averageKg
                val center = Offset(x(i), y(period.averageKg))
                drawCircle(ringColor, radius = radius + ring / 2, center = center)
                drawCircle(if (rose) trend.upDot else trend.goodDot, radius = radius - ring / 2, center = center)
            }
        }
    }
}

/** Ease out with a little overshoot on the points. */
private val PointPop = CubicBezierEasing(0.3f, 1.6f, 0.5f, 1f)
private val LinearOutSlow = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
