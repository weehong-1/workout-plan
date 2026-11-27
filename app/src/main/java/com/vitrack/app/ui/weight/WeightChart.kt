package com.vitrack.app.ui.weight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.vitrack.app.R
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.ui.common.Format
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

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
 * Line chart of weigh-ins over [start]..[end], in the weight colour, with the
 * latest point marked. Dates are placed by day, so gaps between weigh-ins show.
 */
@Composable
fun WeightTrendChart(
    points: List<WeighIn>,
    unit: WeightUnit,
    start: LocalDate,
    end: LocalDate,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.weight_chart_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val values = points.map { unit.fromKg(it.weightKg) }
    val axis = weightAxis(values)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.surfaceContainer
    val description = stringResource(
        R.string.weight_chart_description,
        Format.weight(points.first().weightKg, unit),
        Format.weight(points.last().weightKg, unit),
        Format.unitLabel(unit),
        points.size,
    )
    val totalDays = ChronoUnit.DAYS.between(start, end).coerceAtLeast(1).toFloat()

    Canvas(modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = description }) {
        val axisLabelWidth = 28.dp.toPx()
        val bottomLabelHeight = 22.dp.toPx()
        val dot = 7.dp.toPx()
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

        val path = Path()
        points.forEachIndexed { i, p ->
            val px = x(p.date)
            val py = y(values[i])
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        if (points.size > 1) {
            drawPath(path, color, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val last = Offset(x(points.last().date), y(values.last()))
        drawCircle(ringColor, radius = dot + 1.5.dp.toPx(), center = last)
        drawCircle(color, radius = dot - 1.5.dp.toPx(), center = last)

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

private fun axisLabel(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else Format.oneDecimal(v)
