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
import com.padraik99.grindhaus.domain.Species

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
    fun newBeansDefaultToNotSureAndKeepTheirSpecies() = runBlocking {
        dao.insert(Bean(title = "Unlabelled", roaster = "Roaster", roastLevel = RoastLevel.MEDIUM))
        dao.insert(Bean(title = "Blend", roaster = "Roaster", species = Species.BLEND, robustaShare = 0.3, roastLevel = RoastLevel.DARK))
        val byTitle = dao.active().associateBy { it.title }
        assertEquals(Species.NOT_SURE, byTitle.getValue("Unlabelled").species)
        assertEquals(Species.BLEND, byTitle.getValue("Blend").species)
        assertEquals(0.3, byTitle.getValue("Blend").robustaShare!!, 1e-9)
    }
}
