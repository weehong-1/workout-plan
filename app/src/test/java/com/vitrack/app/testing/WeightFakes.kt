package com.vitrack.app.testing

import com.vitrack.app.data.settings.SettingsRepository
import com.vitrack.app.data.settings.DEFAULT_STEP_GOAL
import com.vitrack.app.data.settings.HEIGHT_CM_RANGE
import com.vitrack.app.data.settings.ReminderKind
import com.vitrack.app.data.settings.Reminders
import com.vitrack.app.data.settings.STEP_GOAL_RANGE
import com.vitrack.app.data.settings.ThemeMode
import com.vitrack.app.data.settings.WeightGoal
import com.vitrack.app.data.settings.WeightUnit
import com.vitrack.app.data.weight.PhotoStore
import com.vitrack.app.data.weight.WeighIn
import com.vitrack.app.data.weight.WeighInDraft
import com.vitrack.app.data.weight.WeighInPhoto
import com.vitrack.app.data.weight.WeightRepository
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 09:00 UTC on [TODAY]. */
val FIXED_CLOCK: Clock = Clock.fixed(TODAY.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

fun weighIn(date: LocalDate, kg: Double, photoIds: List<Long> = emptyList()) = WeighIn(
    date = date,
    weightKg = kg,
    loggedAt = date.atTime(7, 12).toInstant(ZoneOffset.UTC),
    photos = photoIds.map { WeighInPhoto(it, File("photo-$it.jpg")) },
)

class FakeWeightRepository(initial: List<WeighIn> = emptyList()) : WeightRepository {
    val weighIns = MutableStateFlow(initial.sortedByDescending { it.date })
    val saved = mutableListOf<WeighInDraft>()
    val deleted = mutableListOf<LocalDate>()
    var failSave = false

    override fun observeWeighIns(): Flow<List<WeighIn>> = weighIns

    override suspend fun save(draft: WeighInDraft) {
        if (failSave) error("disk full")
        saved += draft
        val others = weighIns.value.filter { it.date != draft.date && it.date != draft.originalDate }
        weighIns.value = (others + WeighIn(draft.date, draft.weightKg, Instant.EPOCH, emptyList())).sortedByDescending { it.date }
    }

    override suspend fun delete(date: LocalDate) {
        deleted += date
        weighIns.value = weighIns.value.filter { it.date != date }
    }
}

class FakeSettingsRepository(unit: WeightUnit = WeightUnit.Kg) : SettingsRepository {
    override val weightUnit = MutableStateFlow(unit)
    override suspend fun setWeightUnit(unit: WeightUnit) {
        weightUnit.value = unit
    }

    override val weightGoal = MutableStateFlow(WeightGoal())
    override suspend fun setWeightGoal(goal: WeightGoal) {
        weightGoal.value = goal
    }

    override val stepGoal = MutableStateFlow(DEFAULT_STEP_GOAL)
    override suspend fun setStepGoal(steps: Int) {
        stepGoal.value = steps.coerceIn(STEP_GOAL_RANGE)
    }

    override val heightCm = MutableStateFlow<Int?>(null)
    override suspend fun setHeightCm(cm: Int?) {
        heightCm.value = cm?.coerceIn(HEIGHT_CM_RANGE)
    }

    override val themeMode = MutableStateFlow(ThemeMode.System)
    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override val reminders = MutableStateFlow(Reminders())
    override suspend fun setReminder(kind: ReminderKind, on: Boolean) {
        reminders.value = when (kind) {
            ReminderKind.WeighIn -> reminders.value.copy(weighIn = on)
            ReminderKind.Food -> reminders.value.copy(food = on)
        }
    }

    override val displayName = MutableStateFlow<String?>(null)
    override suspend fun setDisplayName(name: String?) {
        displayName.value = name?.trim()?.ifEmpty { null }
    }

    var cleared = false
    override suspend fun clear() {
        cleared = true
        weightGoal.value = WeightGoal()
        stepGoal.value = DEFAULT_STEP_GOAL
        heightCm.value = null
        themeMode.value = ThemeMode.System
        reminders.value = Reminders()
        displayName.value = null
    }
}

class FakePhotoStore : PhotoStore {
    private var captures = 0
    override fun file(fileName: String) = File(fileName)
    override fun newCaptureUri() = "content://test/capture-${++captures}.jpg"
    override suspend fun import(uri: String) = "imported-${uri.substringAfterLast('/')}"
    override suspend fun delete(fileNames: List<String>) = Unit
    var deletedAll = false
    override suspend fun deleteAll() {
        deletedAll = true
    }
}
