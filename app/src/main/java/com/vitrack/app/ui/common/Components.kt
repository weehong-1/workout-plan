package com.vitrack.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitrack.app.ui.theme.CardShapes
import com.vitrack.app.ui.theme.Spacing

/** Solid card on surfaceContainer; cards never use glass or elevation. */
@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShapes.Card,
    contentPadding: Dp = Spacing.l,
    /** surfaceContainer, or a metric's hero colour for its lead card. */
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = shape, color = color) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Round icon badge in a metric's container colour. */
@Composable
fun MetricIconBadge(
    @DrawableRes icon: Int,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    shape: Shape = CircleShape,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A bold number followed by a smaller unit, e.g. "58 bpm resting". */
@Composable
fun ValueWithUnit(
    value: String,
    unit: String,
    valueStyle: TextStyle,
    modifier: Modifier = Modifier,
    unitStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    unitColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            withStyle(valueStyle.toSpanStyle()) { append(value) }
            withStyle(unitStyle.toSpanStyle().copy(color = unitColor)) { append(" $unit") }
        },
    )
}

/** Horizontal bar split into weighted segments with small gaps. */
@Composable
fun SegmentedBar(
    segments: List<Pair<Float, Color>>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        segments.filter { it.first > 0f }.forEach { (weight, color) ->
            Box(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(color),
            )
        }
    }
}

/** Small line chart without axes. */
@Composable
fun Sparkline(
    values: List<Int>,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.semantics { this.contentDescription = contentDescription }) {
        if (values.size < 2) return@Canvas
        // At least a 12-unit range, so a few bpm of day-to-day noise stays a gentle line.
        val range = (values.max() - values.min()).toFloat().coerceAtLeast(12f)
        val min = (values.max() + values.min()) / 2f - range / 2f
        val stroke = 2.dp.toPx()
        val stepX = (size.width - stroke) / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stroke / 2 + i * stepX
            // Higher value draws higher, so a falling resting rate reads as going down.
            val y = stroke / 2 + (1f - (v - min) / range) * (size.height - stroke)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** One bar in a [BarChart]. */
data class BarSpec(
    val value: Float,
    val label: String,
    val highlighted: Boolean,
    val valueLabel: String? = null,
)

/**
 * Vertical bar chart in one metric colour: the highlighted bar is solid, the
 * rest are faded. An optional dashed [goal] line is drawn across.
 */
@Composable
fun BarChart(
    bars: List<BarSpec>,
    color: Color,
    maxValue: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 96.dp,
    goal: Float? = null,
    goalColor: Color = MaterialTheme.colorScheme.outlineVariant,
    barSpacing: Dp = 10.dp,
    showLabels: Boolean = true,
    /** Overrides the default bar shape, e.g. full pills. */
    barShape: Shape? = null,
    /** Text colour of the highlighted bar's value label, drawn on a pill of [color]. */
    highlightedValueColor: Color? = null,
) {
    Column(modifier.semantics { this.contentDescription = contentDescription }) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(chartHeight)) {
            // Goal line first, so the bars draw over it.
            if (goal != null) {
                Canvas(Modifier.matchParentSize()) {
                    val y = size.height * (1f - (goal / maxValue).coerceIn(0f, 1f))
                    drawLine(
                        color = goalColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(barSpacing),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        bar.valueLabel?.let {
                            val pill = bar.highlighted && highlightedValueColor != null
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (highlightedValueColor != null) FontWeight.ExtraBold else null,
                                ),
                                color = if (pill) highlightedValueColor!! else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .wrapContentWidth(unbounded = true)
                                    .then(if (pill) Modifier.clip(RoundedCornerShape(8.dp)).background(color) else Modifier)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                        val fraction = (bar.value / maxValue).coerceIn(0f, 1f)
                        val labelSpace = if (bar.valueLabel != null) 24.dp else 0.dp
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height((chartHeight - labelSpace) * fraction)
                                .clip(barShape ?: if (barSpacing < 6.dp) RoundedCornerShape(3.dp) else CardShapes.Bar)
                                .background(if (bar.highlighted) color else color.copy(alpha = 0.42f)),
                        )
                    }
                }
            }
        }
        if (showLabels) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(barSpacing),
            ) {
                bars.forEach { bar ->
                    // Labels may be wider than a thin bar (e.g. "26" over a month); let them overflow centred.
                    Text(
                        text = bar.label,
                        modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true),
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (bar.highlighted) FontWeight.Bold else null,
                        ),
                        color = if (bar.highlighted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
