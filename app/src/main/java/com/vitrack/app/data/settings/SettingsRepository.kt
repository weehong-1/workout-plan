package com.vitrack.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

interface SettingsRepository {
    val weightUnit: Flow<WeightUnit>
    suspend fun setWeightUnit(unit: WeightUnit)
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

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
    }
}
