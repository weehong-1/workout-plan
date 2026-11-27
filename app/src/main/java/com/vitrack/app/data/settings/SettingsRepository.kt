package com.vitrack.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Weights are stored in kg; this is only how they are shown and typed. */
enum class WeightUnit(private val perKg: Double) {
    Kg(1.0),
    Lb(2.2046226218);

    fun fromKg(kg: Double): Double = kg * perKg
    fun toKg(value: Double): Double = value / perKg
}

enum class GoalKind { Lose, Maintain, Gain }

/**
 * The weight goal (Profile). It decides which weekly changes show green:
 * down for Lose, up for Gain, within [MAINTAIN_BAND_KG] of the target for Maintain.
 */
data class WeightGoal(
    val kind: GoalKind = GoalKind.Lose,
    val targetKg: Double? = null,
    /** Where progress is measured from: the weekly average when the target was set. */
    val startKg: Double? = null,
    val startedOn: LocalDate? = null,
) {
    /** Whether a change of [changeKg] between two periods is the good way for this goal. */
    fun isGoodChange(changeKg: Double): Boolean = when (kind) {
        GoalKind.Lose -> changeKg <= 0
        GoalKind.Gain -> changeKg >= 0
        GoalKind.Maintain -> kotlin.math.abs(changeKg) <= MAINTAIN_BAND_KG
    }

    /**
     * Whether a period average is on track: for Maintain, near the target;
     * otherwise moving the goal's way from [previousKg] (the first period counts as on track).
     */
    fun isOnTrack(averageKg: Double, previousKg: Double?): Boolean = when {
        kind == GoalKind.Maintain && targetKg != null -> kotlin.math.abs(averageKg - targetKg) <= MAINTAIN_BAND_KG
        previousKg == null -> true
        else -> isGoodChange(averageKg - previousKg)
    }

    companion object {
        const val MAINTAIN_BAND_KG = 0.5
    }
}

enum class ThemeMode { System, Light, Dark }

enum class ReminderKind(val defaultTime: LocalTime) {
    /** Every morning. */
    WeighIn(LocalTime.of(7, 0)),

    /** In the evening, only when no food photo was added that day. */
    Food(LocalTime.of(20, 0)),
}

data class Reminders(val weighIn: Boolean = false, val food: Boolean = false) {
    fun isOn(kind: ReminderKind) = when (kind) {
        ReminderKind.WeighIn -> weighIn
        ReminderKind.Food -> food
    }
}

const val DEFAULT_STEP_GOAL = 10_000
val STEP_GOAL_RANGE = 1_000..50_000
val HEIGHT_CM_RANGE = 100..250

/** Settings kept on the phone. */
interface SettingsRepository {
    val weightUnit: Flow<WeightUnit>
    suspend fun setWeightUnit(unit: WeightUnit)

    val weightGoal: Flow<WeightGoal>
    suspend fun setWeightGoal(goal: WeightGoal)

    val stepGoal: Flow<Int>
    suspend fun setStepGoal(steps: Int)

    val heightCm: Flow<Int?>
    suspend fun setHeightCm(cm: Int?)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val reminders: Flow<Reminders>
    suspend fun setReminder(kind: ReminderKind, on: Boolean)

    /** A name the person typed, over the account's. */
    val displayName: Flow<String?>
    suspend fun setDisplayName(name: String?)

    /** Removes every setting (account deletion). */
    suspend fun clear()
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val weightUnit: Flow<WeightUnit> = dataStore.data.map { prefs ->
        prefs[WEIGHT_UNIT]?.let { name -> WeightUnit.entries.firstOrNull { it.name == name } } ?: WeightUnit.Kg
    }

    override suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[WEIGHT_UNIT] = unit.name }
    }

    override val weightGoal: Flow<WeightGoal> = dataStore.data.map { prefs ->
        WeightGoal(
            kind = prefs[GOAL_KIND]?.let { name -> GoalKind.entries.firstOrNull { it.name == name } } ?: GoalKind.Lose,
            targetKg = prefs[GOAL_TARGET],
            startKg = prefs[GOAL_START],
            startedOn = prefs[GOAL_STARTED_ON]?.let(LocalDate::ofEpochDay),
        )
    }

    override suspend fun setWeightGoal(goal: WeightGoal) {
        dataStore.edit { prefs ->
            prefs[GOAL_KIND] = goal.kind.name
            prefs.setOrRemove(GOAL_TARGET, goal.targetKg)
            prefs.setOrRemove(GOAL_START, goal.startKg)
            prefs.setOrRemove(GOAL_STARTED_ON, goal.startedOn?.toEpochDay())
        }
    }

    override val stepGoal: Flow<Int> = dataStore.data.map { it[STEP_GOAL] ?: DEFAULT_STEP_GOAL }

    override suspend fun setStepGoal(steps: Int) {
        dataStore.edit { it[STEP_GOAL] = steps.coerceIn(STEP_GOAL_RANGE) }
    }

    override val heightCm: Flow<Int?> = dataStore.data.map { it[HEIGHT_CM] }

    override suspend fun setHeightCm(cm: Int?) {
        dataStore.edit { it.setOrRemove(HEIGHT_CM, cm?.coerceIn(HEIGHT_CM_RANGE)) }
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    override val reminders: Flow<Reminders> = dataStore.data.map {
        Reminders(weighIn = it[REMIND_WEIGH_IN] ?: false, food = it[REMIND_FOOD] ?: false)
    }

    override suspend fun setReminder(kind: ReminderKind, on: Boolean) {
        dataStore.edit {
            when (kind) {
                ReminderKind.WeighIn -> it[REMIND_WEIGH_IN] = on
                ReminderKind.Food -> it[REMIND_FOOD] = on
            }
        }
    }

    override val displayName: Flow<String?> = dataStore.data.map { it[DISPLAY_NAME] }

    override suspend fun setDisplayName(name: String?) {
        dataStore.edit { it.setOrRemove(DISPLAY_NAME, name?.trim()?.take(40)?.ifEmpty { null }) }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun <T> androidx.datastore.preferences.core.MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val GOAL_KIND = stringPreferencesKey("goal_kind")
        val GOAL_TARGET = doublePreferencesKey("goal_target_kg")
        val GOAL_START = doublePreferencesKey("goal_start_kg")
        val GOAL_STARTED_ON = longPreferencesKey("goal_started_on")
        val STEP_GOAL = intPreferencesKey("step_goal")
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val THEME = stringPreferencesKey("theme")
        val REMIND_WEIGH_IN = booleanPreferencesKey("remind_weigh_in")
        val REMIND_FOOD = booleanPreferencesKey("remind_food")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
    }
}
