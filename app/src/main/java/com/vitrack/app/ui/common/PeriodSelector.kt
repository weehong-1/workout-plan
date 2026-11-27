package com.vitrack.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.vitrack.app.R
import com.vitrack.app.ui.theme.MetricColor

/** Range shown by a history chart. */
enum class ChartPeriod(val days: Int) {
    Week(7),
    Month(30),
    Year(365),
}

@Immutable
data class ChoiceColors(
    val container: Color,
    val content: Color,
    val checkedContainer: Color,
    val checkedContent: Color,
)

object ChoiceDefaults {
    /** Primary on secondary container, from the design system. */
    @Composable
    fun colors() = ChoiceColors(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        checkedContainer = MaterialTheme.colorScheme.primary,
        checkedContent = MaterialTheme.colorScheme.onPrimary,
    )

    /** The metric's strong colour for the selected option, on a card of [unchecked]. */
    @Composable
    fun metric(metric: MetricColor, unchecked: Color = MaterialTheme.colorScheme.secondaryContainer) = ChoiceColors(
        container = unchecked,
        content = if (unchecked == MaterialTheme.colorScheme.secondaryContainer) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        checkedContainer = metric.strong,
        checkedContent = metric.onStrong,
    )
}

/** Connected button group for single choice; the selected option is filled and bold. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    colors: ChoiceColors = ChoiceDefaults.colors(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = colors.container,
                    contentColor = colors.content,
                    checkedContainerColor = colors.checkedContainer,
                    checkedContentColor = colors.checkedContent,
                ),
            ) {
                Text(label(option), fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun PeriodSelector(
    selected: ChartPeriod,
    onSelect: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    colors: ChoiceColors = ChoiceDefaults.colors(),
) {
    ConnectedChoiceRow(
        options = ChartPeriod.entries,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        colors = colors,
        label = {
            stringResource(
                when (it) {
                    ChartPeriod.Week -> R.string.period_week
                    ChartPeriod.Month -> R.string.period_month
                    ChartPeriod.Year -> R.string.period_year
                },
            )
        },
    )
}
