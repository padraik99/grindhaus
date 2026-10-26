package com.padraik99.grindhaus.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.GrindhausDatabase
import com.padraik99.grindhaus.data.RoastLevel
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.ui.theme.GrindhausTheme
import com.padraik99.grindhaus.ui.vault.VaultViewModel
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
    private lateinit var db: GrindhausDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GrindhausDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
        val factory = VaultViewModel.factory(db.beanDao()) { today }
        compose.setContent { GrindhausTheme { GrindhausApp(startTab = Tab.VAULT, vaultFactory = factory) } }
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
    fun archivingMovesABagToTheArchivedList() {
        seed()
        showVault()
        waitForText("House Espresso")
        compose.onNodeWithText("House Espresso").performClick()
        waitForText("Archive bean")
        compose.onNodeWithText("Archive bean").performScrollTo().performClick()
        waitForText("Restore to active")
        compose.onNode(hasContentDescription("Back to Vault")).performClick()
        waitForText("Active · 2")
        compose.onNodeWithText("Archived · 1").performClick()
        waitForText("House Espresso")
    }
}
