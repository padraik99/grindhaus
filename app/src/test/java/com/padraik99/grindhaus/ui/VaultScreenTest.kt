package com.padraik99.grindhaus.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.ScanResult
import com.padraik99.grindhaus.data.ProductResult
import com.padraik99.grindhaus.data.ProductLookup
import com.padraik99.grindhaus.data.BarcodeScanner
import com.padraik99.grindhaus.data.BagDetails
import com.padraik99.grindhaus.testing.TestGraph
import com.padraik99.grindhaus.data.RoastLevel
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.ui.theme.GrindhausTheme
import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.FilterOption
import com.padraik99.grindhaus.domain.Taste
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The Vault end to end on a simulated phone, backed by a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class VaultScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 25)
    private lateinit var graph: AppGraph
    private val db get() = graph.db

    @Before
    fun setUp() {
        graph = TestGraph.create(TestGraph.clockAt("2026-10-25T10:00"))
    }

    @After
    fun tearDown() = db.close()

    private fun seed() = runBlocking {
        val dao = db.beanDao()
        dao.insert(
            Bean(
                title = "Colombia Huila", roaster = "Supermarket roaster", origin = "Colombia",
                species = Species.NOT_SURE, roastLevel = RoastLevel.MEDIUM,
                bestByDate = today.plusMonths(9).toEpochDay(), openedDate = today.minusDays(9).toEpochDay(),
                purchaseDate = today.minusDays(12).toEpochDay(), bagGrams = 340.0, preGround = true,
            ),
        )
        dao.insert(
            Bean(
                title = "Ethiopia Guji", roaster = "Specialty roaster", origin = "Ethiopia",
                species = Species.ARABICA, roastLevel = RoastLevel.LIGHT,
                roastDate = today.minusDays(12).toEpochDay(), purchaseDate = today.minusDays(5).toEpochDay(),
                bagGrams = 340.0, tastingTags = "Blueberry, Jasmine, Lemon",
            ),
        )
        dao.insert(
            Bean(
                title = "House Espresso", roaster = "Supermarket roaster",
                species = Species.BLEND, robustaShare = 0.3, roastLevel = RoastLevel.MEDIUM_DARK,
                bestByDate = today.plusMonths(11).toEpochDay(), purchaseDate = today.minusDays(1).toEpochDay(),
                bagGrams = 454.0,
            ),
        )
    }

    private fun showVault() {
        compose.setContent { GrindhausTheme { GrindhausApp(startTab = Tab.VAULT, graph = graph) } }
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun listShowsEachBagWithTheRightFreshness() {
        seed()
        showVault()
        waitForText("Ethiopia Guji")
        compose.onNodeWithText("Peak · day 12").assertExists()
        compose.onNodeWithText("Opened 9 days ago").assertExists()
        compose.onNodeWithText("Unopened").assertExists()
        compose.onNodeWithText("Active · 3").assertExists()
        compose.onRoot().captureRoboImage("screenshots/vault_list.png")
    }

    @Test
    fun emptyVaultExplainsWhatToDo() {
        showVault()
        waitForText("No beans yet. Tap New coffee to add your first bag.")
        compose.onRoot().captureRoboImage("screenshots/vault_empty.png")
    }

    @Test
    fun beanPageShowsDatesAndSpecies() {
        seed()
        showVault()
        waitForText("Colombia Huila")
        compose.onNodeWithText("Colombia Huila").performClick()
        waitForText("Not sure · estimated as arabica")
        compose.onNodeWithText("Not on bag").assertExists()
        compose.onRoot().captureRoboImage("screenshots/vault_bean.png")
    }

    @Test
    fun addingABeanNeedsOnlyANameAndLandsOnItsPage() {
        showVault()
        waitForText("New coffee")
        compose.onNode(hasText("New coffee") and hasClickAction()).performClick()
        waitForText("Name (required)")
        compose.onRoot().captureRoboImage("screenshots/vault_editor.png")
        compose.onNodeWithText("Save").assertIsNotEnabled()

        compose.onNodeWithText("Name (required)").performTextInput("Lavazza Super Crema")
        compose.onNodeWithText("Roaster").performTextInput("Lavazza")
        compose.onNodeWithText("Medium-dark").performClick()
        compose.onNodeWithText("Blend").performClick()
        compose.onNodeWithText("12 oz").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().assertIsEnabled().performClick()

        waitForText("Blend · 50% robusta")
        compose.onNodeWithText("340 g").assertExists()
        compose.onNode(hasContentDescription("Back to Vault")).assertExists()
    }

    @Test
    fun finishingABagAsksTheTwoQuestionsAndArchivesIt() {
        seed()
        runBlocking {
            // Make the Guji the standing favourite.
            val guji = db.beanDao().active().first { it.title == "Ethiopia Guji" }
            db.beanDao().setBuyAgain(guji.id, BuyAgain.YES)
        }
        showVault()
        waitForText("House Espresso")
        compose.onNodeWithText("House Espresso").performClick()
        waitForText("Finished this bag")
        compose.onNodeWithText("Finished this bag").performScrollTo().performClick()
        waitForText("Would you buy it again?")
        compose.onNodeWithText("Better than Ethiopia Guji?").assertExists()
        compose.onNodeWithText("Maybe").performClick()
        compose.onNodeWithText("Worse").performClick()
        compose.onRoot().captureRoboImage("screenshots/vault_finish_dialog.png")
        compose.onNodeWithText("Archive").performClick()
        waitForText("Restore to active")
        compose.onNodeWithText("Maybe again").assertExists()
        compose.onNode(hasContentDescription("Back to Vault")).performClick()
        waitForText("Active · 2")
        compose.onNodeWithText("Archived · 1").performClick()
        waitForText("House Espresso")
    }

    @Test
    fun favoritesSortPutsBuyAgainBagsFirst() {
        seed()
        runBlocking {
            val dao = db.beanDao()
            val beans = dao.active().associateBy { it.title }
            dao.setBuyAgain(beans.getValue("Colombia Huila").id, BuyAgain.YES)
            dao.setBuyAgain(beans.getValue("House Espresso").id, BuyAgain.NO)
        }
        showVault()
        waitForText("Favorites")
        compose.onNodeWithText("Favorites").performClick()
        compose.waitUntil(5_000) {
            val titles = compose.onAllNodes(hasText("Colombia Huila") or hasText("Ethiopia Guji") or hasText("House Espresso"))
                .fetchSemanticsNodes()
                .sortedBy { it.boundsInRoot.top }
                .flatMap { n -> n.config.getOrElseNullable(SemanticsProperties.Text) { null }.orEmpty().map { it.text } }
                .filter { it in setOf("Colombia Huila", "Ethiopia Guji", "House Espresso") }
            titles == listOf("Colombia Huila", "Ethiopia Guji", "House Espresso")
        }
        compose.onNodeWithText("★ Favourite").assertExists()
        compose.onRoot().captureRoboImage("screenshots/vault_favorites.png")
    }

    @Test
    fun tappingABrewInTheHistoryOpensItForEditingOrDeleting() {
        seed()
        val gujiId = runBlocking {
            val guji = db.beanDao().active().first { it.title == "Ethiopia Guji" }
            val g = graph.brewRepository.defaultGrinder()
            graph.brewRepository.logBrew(
                BrewLog(
                    beanId = guji.id, method = BrewMethod.ESPRESSO, grinderId = g.id, filter = FilterOption.SINGLE_WALL,
                    grindSetting = 3.0, doseG = 18.0, outG = 36.0, weighed = false, shotTimeS = 29, rating = 4,
                    taste = Taste.BALANCED, caffeineMg = 163.8, brewedAt = graph.clock.millis(),
                ),
                guji, g, autoApplyGrind = false,
            )
            guji.id
        }
        showVault()
        waitForText("Ethiopia Guji")
        compose.onNodeWithText("Ethiopia Guji").performClick()
        waitForText("Tap a brew to edit it. Long-press to delete.")
        compose.onNodeWithText("Tap a brew to edit it. Long-press to delete.").performScrollTo()
        compose.onRoot().captureRoboImage("screenshots/vault_history_unweighed.png")
        compose.onAllNodes(hasText("not weighed", substring = true))[1].performScrollTo().performClick()

        waitForText("Edit brew")
        compose.onNodeWithText("Espresso · Single-wall · Ethiopia Guji").assertExists()
        compose.onNodeWithText("Delete this brew").performScrollTo().performClick()
        compose.onNodeWithText("Delete").performClick()

        waitForText("Brews you log on the Brew tab appear here with their ratings.")
        assertEquals(0, runBlocking { db.brewDao().observeLogsForBean(gujiId).first().size })
    }

    /** Rebuilds the app on a fresh database whose scanner reads [code] and whose lookup answers [answer]. */
    private fun withScanner(code: String?, answer: ProductResult = ProductResult.NotFound): () -> Int {
        db.close()
        var asked = 0
        graph = TestGraph.create(
            TestGraph.clockAt("2026-10-25T10:00"),
            products = ProductLookup {
                asked++
                answer
            },
            scanner = BarcodeScanner { code?.let(ScanResult::Code) ?: ScanResult.Cancelled },
        )
        return { asked }
    }

    private fun openNewCoffee() {
        showVault()
        waitForText("New coffee")
        compose.onNode(hasText("New coffee") and hasClickAction()).performClick()
        waitForText("Scan barcode", substring = true)
    }

    @Test
    fun scanningANewBagFillsInWhatOpenFoodFactsKnows() {
        withScanner(
            "0012345678905",
            ProductResult.Found(
                BagDetails(
                    title = "Decaf Medium Roast Ground Coffee", roaster = "Some Roaster", species = Species.DECAF,
                    roastLevel = RoastLevel.MEDIUM, bagGrams = 340.0, preGround = true,
                ),
            ),
        )
        openNewCoffee()
        compose.onNodeWithText("Scan barcode", substring = true).performClick()
        waitForText("Found on Open Food Facts", substring = true)
        compose.onRoot().captureRoboImage("screenshots/vault_scan_found.png")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        waitForText("Decaf · caffeine counted at about 3%", substring = true)
        val bean = runBlocking { db.beanDao().active().single() }
        assertEquals("Decaf Medium Roast Ground Coffee", bean.title)
        assertEquals("0012345678905", bean.barcode)
        assertEquals(true, bean.preGround)
        assertEquals(340.0, bean.bagGrams!!, 0.0)
    }

    @Test
    fun aBagBoughtBeforeIsFilledFromYourOwnVault() {
        val asked = withScanner("0123456789012")
        runBlocking {
            val id = db.beanDao().insert(
                Bean(
                    title = "Colombian Anei", roaster = "Local roaster", origin = "Colombia", species = Species.ARABICA,
                    roastLevel = RoastLevel.MEDIUM, bagGrams = 340.0, tastingTags = "Cocoa, Caramel",
                    barcode = "0123456789012", buyAgain = BuyAgain.YES,
                ),
            )
            db.beanDao().archive(id)
        }
        openNewCoffee()
        compose.onNodeWithText("Scan barcode", substring = true).performClick()
        waitForText("You've had this one before", substring = true)
        compose.onNodeWithText("Last time you said you'd buy it again.", substring = true).assertExists()
        compose.onRoot().captureRoboImage("screenshots/vault_scan_repeat.png")
        assertEquals(0, asked())

        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitForText("Arabica")
        val newBag = runBlocking { db.beanDao().active().single() }
        assertEquals("Colombian Anei", newBag.title)
        assertEquals("Cocoa, Caramel", newBag.tastingTags)
        assertEquals(null, newBag.buyAgain)
        assertEquals(null, newBag.openedDate)
    }

    @Test
    fun anUnknownBarcodeIsStillSavedForNextTime() {
        withScanner("0099999999990")
        openNewCoffee()
        compose.onNodeWithText("Scan barcode", substring = true).performClick()
        waitForText("Not on Open Food Facts yet", substring = true)
        compose.onNodeWithText("Name (required)").performTextInput("Mystery decaf")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitForText("Mystery decaf")
        assertEquals("0099999999990", runBlocking { db.beanDao().active().single().barcode })
    }

    @Test
    fun cancellingTheScanChangesNothing() {
        withScanner(null)
        openNewCoffee()
        compose.onNodeWithText("Scan barcode", substring = true).performClick()
        compose.onNodeWithText("Only this button uses the internet", substring = true).assertExists()
        compose.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun editingAnExistingBagHasNoScanButton() {
        seed()
        showVault()
        waitForText("Ethiopia Guji")
        compose.onNodeWithText("Ethiopia Guji").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Edit bean"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription("Edit bean"), useUnmergedTree = true).performClick()
        waitForText("Edit coffee")
        assertEquals(0, compose.onAllNodes(hasText("Scan barcode", substring = true)).fetchSemanticsNodes().size)
    }
}
