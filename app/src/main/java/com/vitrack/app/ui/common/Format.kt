package com.vitrack.app.ui.common

import com.vitrack.app.data.settings.WeightUnit
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Display formatting shared by the screens. Locale-aware, no business logic. */
object Format {
    fun grouped(value: Int): String = NumberFormat.getIntegerInstance().format(value)

    fun oneDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

    fun time(value: LocalTime): String =
        value.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    /** "Thursday, 26 November" */
    fun dayHeading(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))

    /** "T" for Thursday. */
    fun dayLetter(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    /** "N" for November. */
    fun monthLetter(date: LocalDate): String =
        date.month.getDisplayName(TextStyle.NARROW, Locale.getDefault())

    /** "Thu, 26 Nov" */
    fun shortDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

    /** "26 Nov" */
    fun dayMonth(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    /** A weight in kg shown in [unit] with one decimal, without the unit. */
    fun weight(kg: Double, unit: WeightUnit): String = oneDecimal(unit.fromKg(kg))

    fun unitLabel(unit: WeightUnit): String = when (unit) {
        WeightUnit.Kg -> "kg"
        WeightUnit.Lb -> "lb"
    }
}
