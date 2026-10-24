package com.padraik99.grindhaus.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BeanDaoTest {

    private lateinit var db: GrindhausDatabase
    private lateinit var dao: BeanDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GrindhausDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.beanDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun archivedBeansLeaveTheActiveListButAreNotDeleted() = runBlocking {
        val guji = dao.insert(Bean(title = "Ethiopia Guji", roaster = "Roaster", roastLevel = RoastLevel.LIGHT, roastDate = 2))
        dao.insert(Bean(title = "House Espresso", roaster = "Roaster", roastLevel = RoastLevel.MEDIUM_DARK, roastDate = 1))

        assertEquals(listOf("Ethiopia Guji", "House Espresso"), dao.active().map { it.title })

        dao.archive(guji)

        assertEquals(listOf("House Espresso"), dao.active().map { it.title })
        val stillStored = db.query("SELECT COUNT(*) FROM beans", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(2, stillStored)
    }

    @Test
    fun caffeineFractionDefaultsFromSpecies() = runBlocking {
        dao.insert(Bean(title = "Robusta lot", roaster = "Roaster", species = Species.ROBUSTA, roastLevel = RoastLevel.DARK))
        assertEquals(0.022, dao.active().single().caffeineFraction, 1e-9)
    }
}
