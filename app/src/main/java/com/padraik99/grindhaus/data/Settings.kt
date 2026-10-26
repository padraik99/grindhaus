package com.padraik99.grindhaus.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.padraik99.grindhaus.domain.TeaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

/** Settings that are preferences, not records. Settings screen arrives with Sleep Guard. */
data class GrindhausSettings(
    val bedtime: LocalTime = LocalTime.of(23, 30),
    val halfLifeHours: Double = 5.0,
    val limitOverrideMg: Double? = null,
    val autoApplyGrind: Boolean = false,
    val defaultTea: TeaType = TeaType.BLACK,
)

class SettingsStore(private val store: DataStore<Preferences>) {

    private object Keys {
        val BEDTIME_MINUTES = intPreferencesKey("bedtime_minutes")
        val HALF_LIFE = doublePreferencesKey("half_life_hours")
        val LIMIT_OVERRIDE = doublePreferencesKey("limit_override_mg")
        val AUTO_APPLY_GRIND = booleanPreferencesKey("auto_apply_grind")
        val DEFAULT_TEA = stringPreferencesKey("default_tea")
    }

    val settings: Flow<GrindhausSettings> = store.data.map { p ->
        val defaults = GrindhausSettings()
        GrindhausSettings(
            bedtime = p[Keys.BEDTIME_MINUTES]?.let { LocalTime.of(it / 60, it % 60) } ?: defaults.bedtime,
            halfLifeHours = p[Keys.HALF_LIFE] ?: defaults.halfLifeHours,
            limitOverrideMg = p[Keys.LIMIT_OVERRIDE],
            autoApplyGrind = p[Keys.AUTO_APPLY_GRIND] ?: defaults.autoApplyGrind,
            defaultTea = p[Keys.DEFAULT_TEA]?.let { name -> TeaType.entries.firstOrNull { it.name == name } }
                ?: defaults.defaultTea,
        )
    }

    suspend fun setAutoApplyGrind(on: Boolean) {
        store.edit { it[Keys.AUTO_APPLY_GRIND] = on }
    }

    suspend fun setBedtime(time: LocalTime) {
        store.edit { it[Keys.BEDTIME_MINUTES] = time.hour * 60 + time.minute }
    }

    suspend fun setHalfLife(hours: Double) {
        store.edit { it[Keys.HALF_LIFE] = hours }
    }
}
