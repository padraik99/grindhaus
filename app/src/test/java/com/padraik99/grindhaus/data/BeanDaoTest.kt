package com.padraik99.grindhaus.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padraik99.grindhaus.domain.Species
import kotlinx.coroutines.flow.first
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
        val guji = dao.insert(Bean(title = "Ethiopia Guji", roaster = "Roaster", roastLevel = RoastLevel.LIGHT, purchaseDate = 2))
        dao.insert(Bean(title = "House Espresso", roaster = "Roaster", roastLevel = RoastLevel.MEDIUM_DARK, purchaseDate = 1))

        assertEquals(listOf("Ethiopia Guji", "House Espresso"), dao.active().map { it.title })

        dao.archive(guji)

        assertEquals(listOf("House Espresso"), dao.active().map { it.title })
        assertEquals(listOf("Ethiopia Guji"), dao.observeList(archived = true, query = "").first().map { it.title })
        val stillStored = db.query("SELECT COUNT(*) FROM beans", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(2, stillStored)
    }

    @Test
    fun archivedBeansCanBeRestored() = runBlocking {
        val id = dao.insert(Bean(title = "Moka blend", roastLevel = RoastLevel.DARK))
        dao.archive(id)
        dao.setArchived(id, false)
        assertEquals(listOf("Moka blend"), dao.active().map { it.title })
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

    @Test
    fun searchMatchesTitleRoasterOrOrigin() = runBlocking {
        dao.insert(Bean(title = "Guji", roaster = "Heart", origin = "Ethiopia", roastLevel = RoastLevel.LIGHT))
        dao.insert(Bean(title = "Huila", roaster = "Stumptown", origin = "Colombia", roastLevel = RoastLevel.MEDIUM))
        suspend fun find(q: String) = dao.observeList(false, q).first().map { it.title }
        assertEquals(listOf("Guji"), find("ethio"))
        assertEquals(listOf("Huila"), find("stump"))
        assertEquals(listOf("Huila"), find("Huila"))
        assertEquals(2, find("").size)
    }

    @Test
    fun theOpenedDateIsRecordedOnceAndNeverOverwritten() = runBlocking {
        val id = dao.insert(Bean(title = "Bag", roastLevel = RoastLevel.MEDIUM))
        dao.markOpened(id, 20_000)
        dao.markOpened(id, 20_005)
        assertEquals(20_000L, dao.get(id)!!.openedDate)
    }

    @Test
    fun editsAreSaved() = runBlocking {
        val id = dao.insert(Bean(title = "Bag", roastLevel = RoastLevel.MEDIUM))
        dao.update(dao.get(id)!!.copy(title = "Better name", bagGrams = 340.0, tastingTags = "chocolate, cherry"))
        val saved = dao.get(id)!!
        assertEquals("Better name", saved.title)
        assertEquals(340.0, saved.bagGrams!!, 0.0)
        assertEquals(listOf("chocolate", "cherry"), saved.tags)
    }
}
