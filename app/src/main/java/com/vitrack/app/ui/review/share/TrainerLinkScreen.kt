package com.vitrack.app.ui.review.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitrack.app.R
import com.vitrack.app.data.share.ShareContents
import com.vitrack.app.data.share.SharePeriod
import com.vitrack.app.ui.common.ConnectedChoiceRow
import com.vitrack.app.ui.review.periodLabel
import com.vitrack.app.ui.theme.MetricColor
import com.vitrack.app.ui.theme.Spacing
import com.vitrack.app.ui.theme.VitrackTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TrainerLinkRoute(
    onClose: () -> Unit,
    viewModel: TrainerLinkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.result) {
        if (state.result == CreateResult.Created) onClose()
    }
    TrainerLinkScreen(state, viewModel, onClose)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrainerLinkScreen(state: TrainerLinkState, viewModel: TrainerLinkViewModel, onClose: () -> Unit) {
    val metrics = VitrackTheme.metrics
    var pickExpiry by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs).size(48.dp)) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.share_close))
        }
        Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.xxl)) {
            // Hero: a link on a cookie, the page title and what it makes.
            Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    Box(
                        Modifier.size(84.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.onTertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_link), contentDescription = null, tint = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(32.dp))
                    }
                    Column {
                        Text(stringResource(R.string.review_share), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.share_subtitle), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
                    }
                }
            }

            OutlinedTextField(
                value = state.trainerName,
                onValueChange = viewModel::setTrainerName,
                label = { Text(stringResource(R.string.share_trainer)) },
                placeholder = { Text(stringResource(R.string.share_trainer_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )

            SectionTitle(R.string.share_period)
            PeriodPicker(state.period, state.today, viewModel::setPeriod)

            SectionTitle(R.string.share_include)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IncludeRow(R.drawable.ic_pulse, R.string.share_include_activity, metrics.strain, state.contents.activity) {
                    viewModel.setContents(state.contents.copy(activity = it))
                }
                IncludeRow(R.drawable.ic_nav_weight, R.string.tab_weight, metrics.weight, state.contents.weight) {
                    viewModel.setContents(state.contents.copy(weight = it))
                }
                IncludeRow(R.drawable.ic_camera, R.string.share_include_weight_photos, metrics.weight, state.contents.weightPhotos) {
                    viewModel.setContents(state.contents.copy(weightPhotos = it))
                }
                IncludeRow(R.drawable.ic_nav_food, R.string.share_include_food_photos, metrics.food, state.contents.foodPhotos) {
                    viewModel.setContents(state.contents.copy(foodPhotos = it))
                }
            }

            SectionTitle(R.string.share_expiry)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 18.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.share_link_expires), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        CheckSwitch(state.expires, viewModel::setExpires, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    }
                    if (state.expires) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 18.dp, end = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.share_expires_on), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Surface(
                                onClick = { pickExpiry = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Text(
                                    state.expiresOn.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = Spacing.s),
                                )
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(if (state.expires) R.string.share_expiry_note else R.string.share_no_expiry_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = 6.dp),
            )

            // Consent (spec 7): Health data leaves the phone and anyone with the link can read it.
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.padding(top = Spacing.s)) {
                Row(Modifier.padding(horizontal = Spacing.l, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Icon(painterResource(R.drawable.ic_shield), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.share_consent), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick = viewModel::create,
                enabled = state.canCreate,
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(64.dp),
            ) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.share_create),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
            if (!state.canCreate && !state.isCreating) {
                Text(
                    stringResource(if (state.contents.isEmpty) R.string.share_need_content else R.string.share_need_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                )
            }
        }
    }

    if (pickExpiry) ExpiryPicker(state.expiresOn, state.today, onPick = { viewModel.setExpiresOn(it); pickExpiry = false }, onDismiss = { pickExpiry = false })

    when (state.result) {
        CreateResult.NotSetUp -> AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            title = { Text(stringResource(R.string.share_not_set_up_title)) },
            text = { Text(stringResource(R.string.share_not_set_up_body)) },
            confirmButton = { TextButton(onClick = viewModel::dismissResult) { Text(stringResource(R.string.weighin_ok)) } },
        )
        CreateResult.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissResult,
            title = { Text(stringResource(R.string.share_failed_title)) },
            text = { Text(stringResource(R.string.share_failed_body)) },
            confirmButton = { TextButton(onClick = viewModel::dismissResult) { Text(stringResource(R.string.weighin_ok)) } },
        )
        else -> Unit
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xl, bottom = 10.dp).semantics { heading() },
    )
}

/** Month or Year, plus a chip to pick which one (up to two years back). */
@Composable
private fun PeriodPicker(period: SharePeriod, today: LocalDate, onPick: (SharePeriod) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val isYear = period is SharePeriod.Year
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ConnectedChoiceRow(
            options = listOf(false, true),
            selected = isYear,
            onSelect = { year ->
                onPick(
                    when {
                        year && period is SharePeriod.Month -> SharePeriod.Year(period.month.year)
                        !year && period is SharePeriod.Year -> SharePeriod.Month(
                            if (period.year == today.year) YearMonth.from(today) else YearMonth.of(period.year, 12),
                        )
                        else -> period
                    },
                )
            },
            label = { stringResource(if (it) R.string.review_year else R.string.review_month) },
            modifier = Modifier.width(176.dp),
        )
        Box(Modifier.weight(1f)) {
            Surface(
                onClick = { open = true },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        periodLabel(period),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                val options: List<SharePeriod> = if (isYear) {
                    (0..2).map { SharePeriod.Year(today.year - it) }
                } else {
                    (0 until 24).map { SharePeriod.Month(YearMonth.from(today).minusMonths(it.toLong())) }
                }
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(periodLabel(option)) }, onClick = { onPick(option); open = false })
                }
            }
        }
    }
}

@Composable
private fun IncludeRow(icon: Int, name: Int, metric: MetricColor, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (checked) metric.hero else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(start = Spacing.m, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Box(Modifier.size(40.dp).background(metric.badge, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null, tint = metric.text, modifier = Modifier.size(20.dp))
            }
            Text(stringResource(name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            CheckSwitch(checked, onChange, metric.graphic, androidx.compose.ui.graphics.Color.White)
        }
    }
}

/** A switch with a check in the thumb when on, as in the design system. */
@Composable
private fun CheckSwitch(checked: Boolean, onChange: (Boolean) -> Unit, track: androidx.compose.ui.graphics.Color, thumb: androidx.compose.ui.graphics.Color) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        thumbContent = if (checked) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize), tint = track) }
        } else {
            null
        },
        colors = SwitchDefaults.colors(checkedTrackColor = track, checkedThumbColor = thumb),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryPicker(selected: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis > todayMillis
            override fun isSelectableYear(year: Int) = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.weighin_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.weighin_cancel)) } },
    ) { DatePicker(state = picker) }
}
