package com.padraik99.grindhaus.testing

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.GrindhausDatabase
import com.padraik99.grindhaus.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** A real app graph on an in-memory database, fresh settings, and a stopped clock. */
object TestGraph {

    val LA: ZoneId = ZoneId.of("America/Los_Angeles")

    fun clockAt(localDateTime: String): Clock =
        Clock.fixed(LocalDateTime.parse(localDateTime).atZone(LA).toInstant(), LA)

    fun create(clock: Clock): AppGraph {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder(context, GrindhausDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val file = File(context.cacheDir, "settings-${UUID.randomUUID()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { file },
        )
        return AppGraph(db, SettingsStore(store), clock)
    }
}
