package com.gibilator.gmg.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gibilator.gmg.units.Units
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gmg_prefs")

/** Snapshot of all user preferences. */
data class GmgPrefs(
    val selectedSerial: String?,
    val scanIntervalS: Int,
    val maxPitF: Int,
    val autoCookEnabled: Boolean,
    val devMode: Boolean,
    val push: Boolean,
    val tempUnitPref: String,
    val weightUnitPref: String,
    val onboardingDone: Boolean,
    val plannerMeat: String,
    val plannerMode: String,
    val plannerProbe: Int,
    val plannerWeightKg: Double,
    val plannerFinishH: Double,
)

/** DataStore-backed preferences (replaces HA options flow + RestoreEntity state). */
class GmgPreferences(private val context: Context) {

    val flow: Flow<GmgPrefs> = context.dataStore.data.map { p ->
        GmgPrefs(
            selectedSerial = p[SELECTED_SERIAL],
            scanIntervalS = p[SCAN_INTERVAL] ?: 15,
            maxPitF = p[MAX_PIT_F] ?: 375,
            autoCookEnabled = p[AUTO_COOK] ?: false,
            devMode = p[DEV_MODE] ?: false,
            push = p[PUSH] ?: true,
            tempUnitPref = p[TEMP_UNIT] ?: Units.TEMP_UNIT_AUTO,
            weightUnitPref = p[WEIGHT_UNIT] ?: Units.WEIGHT_UNIT_AUTO,
            onboardingDone = p[ONBOARDING_DONE] ?: false,
            plannerMeat = p[PLANNER_MEAT] ?: "beef_brisket_packer",
            plannerMode = p[PLANNER_MODE] ?: "autonomous",
            plannerProbe = p[PLANNER_PROBE] ?: 1,
            plannerWeightKg = p[PLANNER_WEIGHT] ?: 4.0,
            plannerFinishH = p[PLANNER_FINISH] ?: 12.0,
        )
    }

    suspend fun setSelectedSerial(serial: String?) = edit { p ->
        if (serial == null) p.remove(SELECTED_SERIAL) else p[SELECTED_SERIAL] = serial
    }

    suspend fun setScanInterval(seconds: Int) = edit { it[SCAN_INTERVAL] = seconds.coerceIn(5, 600) }
    suspend fun setMaxPitF(f: Int) = edit { it[MAX_PIT_F] = f.coerceIn(150, 375) }
    suspend fun setAutoCook(enabled: Boolean) = edit { it[AUTO_COOK] = enabled }
    suspend fun setDevMode(enabled: Boolean) = edit { it[DEV_MODE] = enabled }
    suspend fun setPush(enabled: Boolean) = edit { it[PUSH] = enabled }
    suspend fun setTempUnit(pref: String) = edit { it[TEMP_UNIT] = pref }
    suspend fun setWeightUnit(pref: String) = edit { it[WEIGHT_UNIT] = pref }
    suspend fun setOnboardingDone(done: Boolean) = edit { it[ONBOARDING_DONE] = done }

    suspend fun setPlanner(
        meat: String? = null,
        mode: String? = null,
        probe: Int? = null,
        weightKg: Double? = null,
        finishH: Double? = null,
    ) = edit { p ->
        meat?.let { p[PLANNER_MEAT] = it }
        mode?.let { p[PLANNER_MODE] = it }
        probe?.let { p[PLANNER_PROBE] = it }
        weightKg?.let { p[PLANNER_WEIGHT] = it }
        finishH?.let { p[PLANNER_FINISH] = it }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val SELECTED_SERIAL = stringPreferencesKey("selected_serial")
        val SCAN_INTERVAL = intPreferencesKey("scan_interval")
        val MAX_PIT_F = intPreferencesKey("max_pit_f")
        val AUTO_COOK = booleanPreferencesKey("auto_cook")
        val DEV_MODE = booleanPreferencesKey("dev_mode")
        val PUSH = booleanPreferencesKey("push")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PLANNER_MEAT = stringPreferencesKey("planner_meat")
        val PLANNER_MODE = stringPreferencesKey("planner_mode")
        val PLANNER_PROBE = intPreferencesKey("planner_probe")
        val PLANNER_WEIGHT = doublePreferencesKey("planner_weight")
        val PLANNER_FINISH = doublePreferencesKey("planner_finish")
    }
}
