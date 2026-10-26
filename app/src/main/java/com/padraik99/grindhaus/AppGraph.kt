package com.padraik99.grindhaus

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.padraik99.grindhaus.data.BarcodeLookup
import com.padraik99.grindhaus.data.BarcodeScanner
import com.padraik99.grindhaus.data.BrewRepository
import com.padraik99.grindhaus.data.GmsBarcodeScanner
import com.padraik99.grindhaus.data.GrindhausDatabase
import com.padraik99.grindhaus.data.OpenFoodFacts
import com.padraik99.grindhaus.data.ProductLookup
import com.padraik99.grindhaus.data.SettingsStore
import com.padraik99.grindhaus.ui.components.appVersionName
import java.time.Clock

private const val PROJECT_URL = "https://github.com/padraik99/grindhaus"

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Everything the screens share, built once. Tests build their own with fakes. */
class AppGraph(
    val db: GrindhausDatabase,
    val settings: SettingsStore,
    val clock: Clock = Clock.systemDefaultZone(),
    products: ProductLookup = OpenFoodFacts(userAgent = "Grindhaus ($PROJECT_URL)"),
    /** The scanner needs the screen's own context to open its camera view. */
    val scannerFor: (Context) -> BarcodeScanner = ::GmsBarcodeScanner,
) {
    val brewRepository = BrewRepository(db, clock)
    val barcodeLookup = BarcodeLookup(db.beanDao(), products)

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(
                    db = GrindhausDatabase.get(context),
                    settings = SettingsStore(context.applicationContext.settingsDataStore),
                    // Open Food Facts asks every app to name itself and give a contact.
                    products = OpenFoodFacts(userAgent = "Grindhaus/${appVersionName(context)} ($PROJECT_URL)"),
                ).also { instance = it }
            }
    }
}
