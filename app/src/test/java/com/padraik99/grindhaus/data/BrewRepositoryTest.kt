package com.padraik99.grindhaus.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.domain.Comparison
import com.padraik99.grindhaus.domain.FilterOption
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.domain.Taste
import com.padraik99.grindhaus.domain.TeaType
import com.padraik99.grindhaus.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BrewRepositoryTest {

    private lateinit var graph: AppGraph
    private val repo get() = graph.brewRepository
    private val clock = TestGraph.clockAt("2026-10-26T09:00")

    @Before
    fun setUp() {
        graph = TestGraph.create(clock)
    }

    @After
    fun tearDown() = graph.db.close()

    private suspend fun newBean(species: Species = Species.ARABICA) =
        graph.db.beanDao().let { dao ->
            dao.get(dao.insert(Bean(title = "Guji", roastLevel = RoastLevel.LIGHT, species = species, bagGrams = 340.0)))!!
        }

    private fun shot(bean: Bean, grinder: Grinder, grind: Double, taste: Taste?, yield: Double = 36.0) = BrewLog(
        beanId = bean.id, method = BrewMethod.ESPRESSO, grinderId = grinder.id, filter = FilterOption.SINGLE_WALL,
        grindSetting = grind, doseG = 18.0, outG = yield, shotTimeS = 22, rating = 2, taste = taste,
        caffeineMg = repo.caffeineMg(bean, BrewMethod.ESPRESSO, 18.0, yield), brewedAt = clock.millis(),
    )

    @Test
    fun theMaestriHouseIsSeededOnceWithItsDial() = runBlocking {
        val g = repo.defaultGrinder()
        assertEquals(g.id, repo.defaultGrinder().id)
        assertEquals(1, graph.db.grinderDao().active().size)
        assertEquals("Maestri House", g.name)
        assertEquals("Espresso", g.rangeName(3.0))
        assertEquals("Mocca", g.rangeName(13.0))
        assertEquals("Pourover", g.rangeName(30.0))
    }

    @Test
    fun loggingABrewRecordsCaffeineRemembersSettingsAndOpensTheBag() = runBlocking {
        val bean = newBean()
        val g = repo.defaultGrinder()
        val id = repo.logBrew(shot(bean, g, 4.0, Taste.SOUR), bean, g, autoApplyGrind = false)

        val intake = graph.db.brewDao().observeIntakeSince(0).first().single()
        assertEquals(id, intake.brewLogId)
        assertEquals(163.8, intake.mg, 0.1)

        val preset = graph.db.brewDao().preset(bean.id, BrewMethod.ESPRESSO, g.id, FilterOption.SINGLE_WALL)!!
        assertEquals(4.0, preset.grindSetting!!, 0.0)
        assertEquals(36.0, preset.outG, 0.0)

        assertEquals(LocalDate.of(2026, 10, 26).toEpochDay(), graph.db.beanDao().get(bean.id)!!.openedDate)
    }

    @Test
    fun withAutoApplyASourShotMovesTheRememberedGrindOneStepFiner() = runBlocking {
        val bean = newBean()
        val g = repo.defaultGrinder()
        repo.logBrew(shot(bean, g, 4.0, Taste.SOUR), bean, g, autoApplyGrind = true)
        val preset = graph.db.brewDao().preset(bean.id, BrewMethod.ESPRESSO, g.id, FilterOption.SINGLE_WALL)!!
        assertEquals(3.0, preset.grindSetting!!, 0.0)
    }

    @Test
    fun oneComboKeepsOnePresetRowThatUpdates() = runBlocking {
        val bean = newBean()
        val g = repo.defaultGrinder()
        repo.logBrew(shot(bean, g, 4.0, Taste.BALANCED), bean, g, autoApplyGrind = false)
        repo.logBrew(shot(bean, g, 3.0, Taste.BALANCED, yield = 40.0), bean, g, autoApplyGrind = false)
        val count = graph.db.query("SELECT COUNT(*) FROM dial_presets", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count)
        assertEquals(40.0, graph.db.brewDao().preset(bean.id, BrewMethod.ESPRESSO, g.id, FilterOption.SINGLE_WALL)!!.outG, 0.0)
    }

    @Test
    fun deletingABrewTakesItsCaffeineWithIt() = runBlocking {
        val bean = newBean()
        val g = repo.defaultGrinder()
        val id = repo.logBrew(shot(bean, g, 4.0, Taste.SOUR), bean, g, autoApplyGrind = false)
        repo.deleteBrew(graph.db.brewDao().log(id)!!)
        assertNull(graph.db.brewDao().log(id))
        assertEquals(0, graph.db.brewDao().observeIntakeSince(0).first().size)
    }

    @Test
    fun correctingABrewRecalculatesItsCaffeineEverywhere() = runBlocking {
        val bean = newBean()
        val g = repo.defaultGrinder()
        val id = repo.logBrew(shot(bean, g, 4.0, Taste.SOUR), bean, g, autoApplyGrind = false)
        val logged = graph.db.brewDao().log(id)!!

        // The scale was wrong: it was a 1:1 ristretto, not a 1:2.
        repo.updateBrew(logged.copy(outG = 18.0, taste = Taste.BALANCED), bean)

        val after = graph.db.brewDao().log(id)!!
        assertEquals(18.0, after.outG, 0.0)
        assertEquals(Taste.BALANCED, after.taste)
        assertEquals(117.0, after.caffeineMg, 0.1) // 18 g x 6.5 mg/g
        val intake = graph.db.brewDao().observeIntakeSince(0).first().single()
        assertEquals(after.caffeineMg, intake.mg, 1e-9)
        assertEquals(logged.brewedAt, intake.takenAt)
    }

    @Test
    fun anUnweighedBrewIsKeptAndCountedAtThePlannedYield() = runBlocking {
        val bean = newBean()
        val g = repo.defaultGrinder()
        val id = repo.logBrew(shot(bean, g, 4.0, Taste.BALANCED).copy(weighed = false), bean, g, autoApplyGrind = false)
        val log = graph.db.brewDao().log(id)!!
        assertEquals(false, log.weighed)
        assertEquals(163.8, log.caffeineMg, 0.1)
        assertEquals(4.0, graph.db.brewDao().preset(bean.id, BrewMethod.ESPRESSO, g.id, FilterOption.SINGLE_WALL)!!.grindSetting!!, 0.0)
    }

    @Test
    fun robustaBeansCountDoubleCaffeine() = runBlocking {
        val bean = newBean(Species.ROBUSTA)
        assertEquals(327.6, repo.caffeineMg(bean, BrewMethod.ESPRESSO, 18.0, 36.0), 0.1)
    }

    @Test
    fun teaIsLoggedWithItsDefaultCaffeine() = runBlocking {
        repo.logTea(TeaType.BLACK)
        repo.logTea(TeaType.HERBAL)
        val events = graph.db.brewDao().observeIntakeSince(0).first()
        assertEquals(listOf(30.0, 0.0), events.map { it.mg })
        assertEquals(IntakeSource.TEA, events.first().source)
    }

    @Test
    fun gramsUsedAddsUpEveryDose() = runBlocking {
        val bean = newBean()
        val g = repo.defaultGrinder()
        repo.logBrew(shot(bean, g, 4.0, null), bean, g, autoApplyGrind = false)
        repo.logBrew(shot(bean, g, 4.0, null), bean, g, autoApplyGrind = false)
        assertEquals(36.0, graph.db.brewDao().observeGramsUsed(bean.id).first(), 0.0)
    }

    @Test
    fun finishingABagArchivesItAndRecordsTheVerdictAndComparison() = runBlocking {
        val dao = graph.db.beanDao()
        val fav = dao.insert(Bean(title = "Favourite", roastLevel = RoastLevel.MEDIUM, buyAgain = BuyAgain.YES))
        val bag = newBean()

        assertEquals(fav, dao.favourite(excludeId = bag.id)!!.id)
        repo.finishBag(bag.id, BuyAgain.YES, comparedWith = fav, outcome = Comparison.BETTER)

        val finished = dao.get(bag.id)!!
        assertEquals(true, finished.archived)
        assertEquals(BuyAgain.YES, finished.buyAgain)
        assertEquals(1512.0, finished.rankScore, 1e-9)
        assertEquals(1488.0, dao.get(fav)!!.rankScore, 1e-9)
        val logged = graph.db.query("SELECT outcome FROM bean_comparisons", null).use { it.moveToFirst(); it.getString(0) }
        assertEquals("BETTER", logged)
        // The finished bag is now the one new bags get compared with.
        assertEquals(bag.id, dao.favourite(excludeId = -1)!!.id)
    }

    @Test
    fun changingYourMindLaterKeepsTheBagArchived() = runBlocking {
        val bag = newBean()
        repo.finishBag(bag.id, BuyAgain.MAYBE, comparedWith = null, outcome = null)
        repo.updateVerdict(bag.id, BuyAgain.NO, comparedWith = null, outcome = null)
        val after = graph.db.beanDao().get(bag.id)!!
        assertEquals(BuyAgain.NO, after.buyAgain)
        assertEquals(true, after.archived)
        assertEquals(1500.0, after.rankScore, 1e-9)
    }

    @Test
    fun aBagYouWouldNotBuyAgainIsNeverTheFavourite() = runBlocking {
        val dao = graph.db.beanDao()
        dao.insert(Bean(title = "Meh", roastLevel = RoastLevel.DARK, buyAgain = BuyAgain.NO, rankScore = 1800.0))
        assertEquals(null, dao.favourite(excludeId = -1))
    }
}
