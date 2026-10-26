package com.padraik99.grindhaus

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.padraik99.grindhaus.data.BrewRepository
import com.padraik99.grindhaus.data.GrindhausDatabase
import com.padraik99.grindhaus.data.SettingsStore
import java.time.Clock

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Everything the screens share, built once. Tests build their own with fakes. */
class AppGraph(
    val db: GrindhausDatabase,
    val settings: SettingsStore,
    val clock: Clock = Clock.systemDefaultZone(),
) {
    val brewRepository = BrewRepository(db, clock)

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(
                    db = GrindhausDatabase.get(context),
                    settings = SettingsStore(context.applicationContext.settingsDataStore),
                ).also { instance = it }
            }
    }
}
