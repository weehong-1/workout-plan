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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.vitrack.app.R

/** Range shown by a history chart. */
enum class ChartPeriod(val days: Int) {
    Week(7),
    Month(30),
    Year(365),
}

/**
 * Connected button group for single choice, as in the design system:
 * the selected button is filled with primary.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> ConnectedChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(label(option))
            }
        }
    }
}

@Composable
fun PeriodSelector(
    selected: ChartPeriod,
    onSelect: (ChartPeriod) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    ConnectedChoiceRow(
        options = ChartPeriod.entries,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
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
