package com.padraik99.grindhaus.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.RoastLevel
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.FilterOption
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.testing.TestGraph
import com.padraik99.grindhaus.ui.theme.GrindhausTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Home and the brew session end to end on a simulated phone, backed by a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class BrewScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @After
    fun tearDown() = graph.db.close()

    private fun start(time: String, beans: List<Bean>) {
        graph = TestGraph.create(TestGraph.clockAt("2026-10-26T$time"))
        runBlocking { beans.forEach { graph.db.beanDao().insert(it) } }
        compose.setContent { GrindhausTheme { GrindhausApp(startTab = Tab.HOME, graph = graph) } }
    }

    private val guji = Bean(
        title = "Ethiopia Guji", roaster = "Specialty roaster", species = Species.ARABICA,
        roastLevel = RoastLevel.LIGHT, roastDate = LocalDate.of(2026, 10, 14).toEpochDay(),
        purchaseDate = LocalDate.of(2026, 10, 20).toEpochDay(), bagGrams = 340.0,
    )
    private val decaf = Bean(
        title = "Kicking Horse", roaster = "Kicking Horse", species = Species.DECAF,
        roastLevel = RoastLevel.DARK, purchaseDate = LocalDate.of(2026, 10, 24).toEpochDay(),
    )
    private val huila = Bean(
        title = "Colombia Huila", roaster = "Supermarket roaster", species = Species.NOT_SURE,
        roastLevel = RoastLevel.MEDIUM, bestByDate = LocalDate.of(2027, 7, 1).toEpochDay(),
        purchaseDate = LocalDate.of(2026, 10, 10).toEpochDay(), preGround = true,
    )

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun gone(text: String, substring: Boolean = false) =
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isEmpty()
        }

    /** Taps a method sticker on Home and waits for the brew session. */
    private fun startBrew(method: String) {
        waitForText(method)
        compose.onNode(hasText(method) and hasClickAction()).performScrollTo().performClick()
        waitForText("New brew")
    }

    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    private fun save() = compose.onNode(hasText("Save brew") and hasClickAction()).performClick()

    private fun intake() = runBlocking { graph.db.brewDao().observeIntakeSince(0).first() }

    private fun preset(beanId: Long, method: BrewMethod, filter: FilterOption) = runBlocking {
        graph.db.brewDao().preset(beanId, method, graph.brewRepository.defaultGrinder().id, filter)
    }

    @Test
    fun emptyVaultPointsToTheVault() {
        start("09:00", emptyList())
        waitForText("Go to the Vault")
        compose.onNodeWithText("Go to the Vault").performClick()
        waitForText("No beans yet. Tap New coffee to add your first bag.")
    }

    @Test
    fun homeShowsTheShelfTheMethodsAndTheVersion() {
        start("09:00", listOf(guji, decaf))
        waitForText("ON THE SHELF")
        compose.onNodeWithText("Freddo").assertExists()
        compose.onNodeWithText("Your brews show up here once you log one.").assertExists()
        waitForText("v0.1.", substring = true)
        compose.onRoot().captureRoboImage("screenshots/home.png")
    }

    @Test
    fun aNewBrewStartsAtTheMethodDefaults() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        compose.onNodeWithText("Caffeine ~164 mg").assertExists()
        compose.onNodeWithText("1:2").assertExists()
        compose.onNode(hasContentDescription("Grind 3")).assertExists()
        compose.onRoot().captureRoboImage("screenshots/session.png")
    }

    @Test
    fun preGroundBeansSkipTheGrindAndUseTheDoubleWallBasket() {
        start("09:00", listOf(huila))
        startBrew("Espresso")
        compose.onNodeWithText("Pre-ground: no grind setting").assertExists()
        compose.onNode(hasText("Double-wall") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertExists()
    }

    @Test
    fun typingIntoANumberReplacesIt() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        field("Dose").performClick()
        field("Dose").performTextInput("11")
        waitForText("1:3.3")
        assertEquals(1, compose.onAllNodes(hasText("11") and hasSetTextAction()).fetchSemanticsNodes().size)
    }

    @Test
    fun aSourShotIsSavedAndTheNextOneOffersOneStepFiner() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        compose.onNode(hasContentDescription("Coarser")).performClick() // 3 -> 4
        field("Yield").performScrollTo().performTextReplacement("37")
        field("Time").performTextReplacement("22")
        compose.onNode(hasContentDescription("Rate 2 of 5")).performScrollTo().performClick()
        compose.onNode(hasText("Sour") and hasClickAction()).performScrollTo().performClick()
        compose.onRoot().captureRoboImage("screenshots/session_sour.png")
        save()

        waitForText("Saved: Ethiopia Guji · espresso")
        waitForText("g4 · 18→37 g · 22 s")
        compose.onRoot().captureRoboImage("screenshots/home_after_brew.png")
        // The bag's first brew opened it.
        assertEquals(LocalDate.of(2026, 10, 26).toEpochDay(), runBlocking { graph.db.beanDao().get(1)!!.openedDate })

        compose.onNode(hasContentDescription("Brew Ethiopia Guji Espresso again")).performClick()
        waitForText("Ran sour. Try grind 3, one step finer?")
        compose.onNode(hasText("Use 3") and hasClickAction()).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Grind 3")).fetchSemanticsNodes().isNotEmpty() }
        save()
        compose.waitUntil(5_000) { preset(1, BrewMethod.ESPRESSO, FilterOption.SINGLE_WALL)?.grindSetting == 3.0 }
    }

    @Test
    fun anEveningShotWarnsAboutBedtime() {
        start("21:00", listOf(guji))
        startBrew("Espresso")
        compose.onNodeWithText("This would leave about", substring = true).performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/session_warning.png")
    }

    @Test
    fun aMorningShotIsFineForTonight() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        compose.onNodeWithText("Fine for tonight", substring = true).performScrollTo()
    }

    @Test
    fun theCoffeeCanBeSwitchedInsideTheBrew() {
        start("09:00", listOf(guji, decaf))
        startBrew("Espresso")
        compose.onNode(hasText("Change") and hasClickAction() and hasText("COFFEE")).performClick()
        compose.onNode(hasText("Kicking Horse (decaf)") and hasClickAction()).performClick()
        waitForText("Caffeine ~5 mg (decaf)")
    }

    @Test
    fun aShotLeftUnweighedIsMarkedAndCanBeCorrectedLater() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        compose.onNodeWithText("Leave yield blank", substring = true).performScrollTo()
        save()
        waitForText("g3 · 18→~36 g")
        val id = intake().single().brewLogId!!
        assertEquals(false, runBlocking { graph.db.brewDao().log(id)!!.weighed })

        compose.onNode(hasContentDescription("Edit Ethiopia Guji Espresso")).performClick()
        waitForText("Edit brew")
        field("Yield").performScrollTo().performTextReplacement("42")
        compose.onRoot().captureRoboImage("screenshots/edit_brew.png")
        compose.onNode(hasText("Save changes") and hasClickAction()).performClick()

        waitForText("g3 · 18→42 g")
        val log = runBlocking { graph.db.brewDao().log(id)!! }
        assertEquals(true, log.weighed)
        assertEquals(42.0, log.outG, 0.0)
        assertEquals(log.caffeineMg, intake().single().mg, 1e-9)
    }

    @Test
    fun aBrewLoggedAgainstTheWrongCoffeeAndDayIsMovedWithItsCaffeine() {
        // What happened on the phone: the session opened on the decaf, but the shot was the Guji.
        start("09:00", listOf(guji, decaf))
        waitForText("ON THE SHELF")
        compose.onNode(hasText("Kicking Horse") and hasClickAction()).performClick()
        startBrew("Espresso")
        save()
        waitForText("Saved: Kicking Horse · espresso")
        val id = intake().single().brewLogId!!
        val loggedAt = runBlocking { graph.db.brewDao().log(id)!!.brewedAt }

        compose.onNode(hasContentDescription("Edit Kicking Horse Espresso")).performClick()
        waitForText("Edit brew")
        compose.onNode(hasText("Ethiopia Guji") and hasClickAction()).performClick()
        waitForText("Caffeine goes from ~5 mg to ~164 mg", substring = true)
        compose.onNode(hasText("Yesterday") and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText("Save changes") and hasClickAction()).performClick()
        waitForText("Ethiopia Guji · Espresso")

        val log = runBlocking { graph.db.brewDao().log(id)!! }
        assertEquals(1L, log.beanId)
        assertEquals(163.8, log.caffeineMg, 0.1)
        assertEquals(loggedAt - 86_400_000L, log.brewedAt)
        val event = intake().single()
        assertEquals(log.brewedAt, event.takenAt)
        assertEquals(163.8, event.mg, 0.1)
        assertEquals("Espresso · Ethiopia Guji", event.label)
    }

    @Test
    fun aBrewDrunkEarlierCanBeLoggedAtThatTime() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        compose.onNode(hasText("Pick a time") and hasClickAction()).performScrollTo().performClick()
        field("Hour").performScrollTo().performTextReplacement("8")
        field("Min").performTextReplacement("05")
        compose.onNodeWithText("Caffeine counts from 8:05 am", substring = true).assertExists()
        compose.onRoot().captureRoboImage("screenshots/session_pick_time.png")
        save()
        compose.waitUntil(5_000) { intake().isNotEmpty() }
        val expected = java.time.LocalDateTime.parse("2026-10-26T08:05").atZone(TestGraph.LA).toInstant().toEpochMilli()
        assertEquals(expected, intake().single().takenAt)
    }

    @Test
    fun anEditCanSetTheTimeTheBrewWasDrunk() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        save()
        waitForText("g3 · 18→~36 g")
        compose.onNode(hasContentDescription("Edit Ethiopia Guji Espresso")).performClick()
        waitForText("Edit brew")
        field("Hour").performScrollTo().performTextReplacement("7")
        field("Min").performTextReplacement("15")
        compose.onNode(hasText("Save changes") and hasClickAction()).performClick()
        waitForText("TODAY 7:15 AM")
        val expected = java.time.LocalDateTime.parse("2026-10-26T07:15").atZone(TestGraph.LA).toInstant().toEpochMilli()
        assertEquals(expected, intake().single().takenAt)
    }

    @Test
    fun deletingABrewFromItsEditorTakesItAndItsCaffeineOff() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        save()
        waitForText("g3 · 18→~36 g")
        compose.onNode(hasContentDescription("Edit Ethiopia Guji Espresso")).performClick()
        waitForText("Delete this brew")
        compose.onNodeWithText("Delete this brew").performScrollTo().performClick()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        waitForText("Your brews show up here once you log one.")
        assertEquals(0, intake().size)
    }

    @Test
    fun theShotTimerFillsTime() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        compose.onNodeWithText("Start timer").performScrollTo()
        // The timer ticks every frame, so drive the clock by hand while it runs.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Start timer").performClick()
        compose.mainClock.advanceTimeBy(27_300)
        compose.onNodeWithText("Stop").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.autoAdvance = true
        waitForText("Reset")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Time") and hasText("27")).fetchSemanticsNodes().isNotEmpty() }
        save()
        waitForText("g3 · 18→~36 g · 27 s")
    }

    @Test
    fun aFreddoStartsFromTheAeroPressDialIn() {
        start("09:00", listOf(guji))
        startBrew("AeroPress")
        compose.onNode(hasContentDescription("Coarser")).performClick() // 17 -> 18
        save()
        waitForText("Saved: Ethiopia Guji · aeropress")
        startBrew("Freddo")
        compose.onNode(hasContentDescription("Grind 18")).assertExists()
        compose.onNodeWithText("Last time with this coffee").assertExists()
        save()
        compose.waitUntil(5_000) { intake().size == 2 }
        assertEquals("Caffè freddo · Ethiopia Guji", intake().last().label)
    }

    @Test
    fun teaIsLoggedWithAConfirmationThatCanUndo() {
        start("10:30", listOf(guji))
        waitForText("+ Tea")
        compose.onNodeWithText("+ Tea").performClick()
        waitForText("Log a tea")
        compose.onNode(hasText("Black") and hasText("~30 mg") and hasClickAction()).performClick()
        waitForText("Black tea logged · ~30 mg")
        assertEquals(30.0, intake().single().mg, 0.0)
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { intake().isEmpty() }
    }

    @Test
    fun aSavedTeaIsOneTapAndCanBeRenamed() {
        start("10:30", listOf(guji))
        waitForText("+ Tea")
        compose.onNodeWithText("+ Tea").performClick()
        compose.onNode(hasText("+ Save a tea you drink often") and hasClickAction()).performClick()
        compose.onNode(hasText("Tea name") and hasSetTextAction()).performTextInput("Earl Grey")
        compose.onNode(hasText("Save tea") and hasClickAction()).performClick()
        waitForText("Earl Grey")
        compose.onRoot().captureRoboImage("screenshots/tea.png")

        compose.onNode(hasText("Earl Grey") and hasClickAction()).performClick()
        waitForText("Earl Grey logged · ~30 mg")
        assertEquals("Earl Grey", intake().single().label)
        assertEquals(1, runBlocking { graph.db.teaDao().observeAll().first().single().timesLogged })

        compose.onNodeWithText("+ Tea").performClick()
        waitForText("Earl Grey")
        compose.onNode(hasText("Earl Grey") and hasClickAction()).performTouchInput { longClick() }
        compose.onNode(hasText("Tea name") and hasSetTextAction()).performTextReplacement("Lady Grey")
        compose.onNode(hasText("Save") and hasClickAction()).performClick()
        waitForText("Lady Grey")
    }

    @Test
    fun theBeanPageShowsDialHistoryAfterABrew() {
        start("09:00", listOf(guji))
        startBrew("Espresso")
        compose.onNode(hasContentDescription("Rate 4 of 5")).performScrollTo().performClick()
        compose.onNode(hasText("Balanced") and hasClickAction()).performScrollTo().performClick()
        save()
        waitForText("RECENT BREWS")
        gone("New brew")

        compose.onNodeWithText("Vault").performClick()
        waitForText("Ethiopia Guji")
        compose.onNodeWithText("Ethiopia Guji").performClick()
        waitForText("BEST SO FAR")
        compose.onNodeWithText("~322 g · ~17 brews").assertExists()
        compose.onRoot().captureRoboImage("screenshots/vault_bean_history.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun largerTextStillFits() {
        start("09:00", listOf(guji, decaf))
        waitForText("ON THE SHELF")
        compose.onRoot().captureRoboImage("screenshots/home_large_text.png")
        startBrew("Espresso")
        compose.onRoot().captureRoboImage("screenshots/session_large_text.png")
    }
}
