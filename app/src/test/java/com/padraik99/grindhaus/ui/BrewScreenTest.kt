package com.padraik99.grindhaus.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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

/** Brew Bar end to end on a simulated phone: dial in, log a shot, take the suggestion. */
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
        compose.setContent { GrindhausTheme { GrindhausApp(startTab = Tab.BREW, graph = graph) } }
    }

    private val guji = Bean(
        title = "Ethiopia Guji", roaster = "Specialty roaster", species = Species.ARABICA,
        roastLevel = RoastLevel.LIGHT, roastDate = LocalDate.of(2026, 10, 14).toEpochDay(),
        purchaseDate = LocalDate.of(2026, 10, 20).toEpochDay(), bagGrams = 340.0,
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

    @Test
    fun emptyVaultPointsToTheVault() {
        start("09:00", emptyList())
        waitForText("Go to the Vault")
        compose.onNodeWithText("Go to the Vault").performClick()
        waitForText("No beans yet. Tap New coffee to add your first bag.")
    }

    @Test
    fun aNewBeanStartsAtTheMethodDefaults() {
        start("09:00", listOf(guji))
        waitForText("Espresso range")
        compose.onNodeWithText("Caffeine ~164 mg").assertExists()
        compose.onNodeWithText("1:2").assertExists()
        compose.onRoot().captureRoboImage("screenshots/brew_bar.png")
    }

    @Test
    fun preGroundBeansSkipTheGrindAndUseTheDoubleWallBasket() {
        start("09:00", listOf(huila))
        waitForText("Pre-ground: no grind setting")
        assertEquals(
            1,
            compose.onAllNodes(hasText("Double-wall")).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun aSourShotOffersOneStepFinerAndUsingItMovesTheGrind() {
        start("09:00", listOf(guji))
        waitForText("Log brew")
        compose.onNode(hasText("Log brew") and hasClickAction()).performClick()
        waitForText("Yield (g)")
        compose.onNode(hasContentDescription("Coarser")).performClick() // 3 -> 4
        compose.onNodeWithText("Yield (g)").performTextReplacement("37")
        compose.onNodeWithText("Time (s)").performTextReplacement("22")
        compose.onNode(hasContentDescription("Rate 2 of 5")).performClick()
        compose.onNodeWithText("Sour").performClick()
        compose.onRoot().captureRoboImage("screenshots/brew_log.png")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        waitForText("Ran sour. Try grind 3, one step finer?")
        compose.onNodeWithText("18 → 37 g · 22 s").assertExists()
        compose.onRoot().captureRoboImage("screenshots/brew_bar_after_sour.png")

        compose.onNodeWithText("Use 3").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Ran sour. Try grind 3, one step finer?")).fetchSemanticsNodes().isEmpty()
        }

        val preset = runBlocking {
            val g = graph.brewRepository.defaultGrinder()
            graph.db.brewDao().preset(1, BrewMethod.ESPRESSO, g.id, FilterOption.SINGLE_WALL)
        }!!
        assertEquals(3.0, preset.grindSetting!!, 0.0)
        assertEquals(37.0, preset.outG, 0.0)

        // The bag's first brew opened it.
        assertEquals(LocalDate.of(2026, 10, 26).toEpochDay(), runBlocking { graph.db.beanDao().get(1)!!.openedDate })
    }

    @Test
    fun anEveningShotWarnsAboutBedtime() {
        start("21:00", listOf(guji))
        waitForText("This brew leaves about", substring = true)
        compose.onRoot().captureRoboImage("screenshots/brew_bar_warning.png")
    }

    @Test
    fun aMorningShotDoesNotWarn() {
        start("09:00", listOf(guji))
        waitForText("Log brew")
        assertEquals(0, compose.onAllNodes(hasText("This brew leaves about", substring = true)).fetchSemanticsNodes().size)
    }

    @Test
    fun teaIsLoggedFromTheHeader() {
        start("10:30", listOf(guji))
        waitForText("+ Tea")
        compose.onNodeWithText("+ Tea").performClick()
        compose.onNodeWithText("Black tea · ~30 mg").performClick()
        compose.waitUntil(5_000) { runBlocking { graph.db.brewDao().observeIntakeSince(0).first().isNotEmpty() } }
        assertEquals(30.0, runBlocking { graph.db.brewDao().observeIntakeSince(0).first().single().mg }, 0.0)
    }

    @Test
    fun theBeanPageShowsDialHistoryAfterABrew() {
        start("09:00", listOf(guji))
        waitForText("Log brew")
        compose.onNode(hasText("Log brew") and hasClickAction()).performClick()
        waitForText("Yield (g)")
        compose.onNode(hasContentDescription("Rate 4 of 5")).performClick()
        compose.onNodeWithText("Balanced").performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitForText("LAST BREW")

        compose.onNodeWithText("Vault").performClick()
        waitForText("Ethiopia Guji")
        compose.onNodeWithText("Ethiopia Guji").performClick()
        waitForText("BEST SO FAR")
        compose.onNodeWithText("~322 g · ~17 brews").assertExists()
        compose.onRoot().captureRoboImage("screenshots/vault_bean_history.png")
    }
}
