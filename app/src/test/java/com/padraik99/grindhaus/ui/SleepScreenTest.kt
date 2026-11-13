package com.padraik99.grindhaus.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.GrindhausSettings
import com.padraik99.grindhaus.data.IntakeEvent
import com.padraik99.grindhaus.data.IntakeSource
import com.padraik99.grindhaus.testing.TestGraph
import com.padraik99.grindhaus.ui.theme.GrindhausTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.LocalTime

/** The Sleep tab on a simulated phone, backed by a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class SleepScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @After
    fun tearDown() = graph.db.close()

    private fun millis(time: String) =
        LocalDateTime.parse("2026-10-26T$time").atZone(TestGraph.LA).toInstant().toEpochMilli()

    private fun open(now: String, vararg cups: Pair<String, Double>) {
        graph = TestGraph.create(TestGraph.clockAt("2026-10-26T$now"))
        runBlocking {
            cups.forEach { (time, mg) ->
                graph.db.brewDao().insertIntake(IntakeEvent(takenAt = millis(time), mg = mg, source = IntakeSource.TEA, label = "Black tea"))
            }
        }
        compose.setContent { GrindhausTheme { GrindhausApp(startTab = Tab.SLEEP, graph = graph) } }
        waitForText("Bedtime")
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun waitForNoText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty()
        }

    private fun field(label: String) = compose.onNode(hasText(label) and hasSetTextAction())

    private fun settings() = runBlocking { graph.settings.settings.first() }

    /** Settings are saved from the main thread, so let it run between checks. */
    private fun settled(condition: (GrindhausSettings) -> Boolean) {
        repeat(100) {
            compose.waitForIdle()
            if (condition(settings())) return
            Thread.sleep(20)
        }
        fail("Setting never arrived: ${settings()}")
    }

    private fun intake() = runBlocking { graph.db.brewDao().observeIntakeSince(0).first() }

    @Test
    fun aMorningShotReadsAsUnderTheLimitWithALastCallTime() {
        open("09:00", "07:30" to 30.0)
        compose.onNodeWithText("Under your 32\u00A0mg limit").assertExists()
        compose.onNodeWithText("In you now").assertExists()
        compose.onNodeWithText("After 11:30 pm").assertExists()
        compose.onRoot().captureRoboImage("screenshots/sleep_morning.png")
        compose.onNodeWithText("7:30 am").performScrollTo().assertExists()
        // A usual brew (none logged yet, so an 18 g espresso) has a time; tea at this hour does too.
        compose.onNode(hasText("by ", substring = true)).assertExists()
        compose.onRoot().captureRoboImage("screenshots/sleep_morning_today.png")
    }

    @Test
    fun anEveningDoubleShowsHowFarOverAndThatItsTooLate() {
        open("21:00", "20:30" to 164.0)
        waitForText("over your 32\u00A0mg limit", substring = true)
        // Neither a usual brew nor a tea fits now.
        compose.onAllNodesWithText("not tonight").assertCountEquals(2)
        compose.onRoot().captureRoboImage("screenshots/sleep_over.png")
    }

    @Test
    fun caffeineFromElsewhereIsAddedAndCanBeTakenBack() {
        open("13:00")
        field("Caffeine from elsewhere").performScrollTo().performTextReplacement("80")
        compose.onNode(hasText("Add") and hasClickAction()).performClick()
        waitForText("Other caffeine")
        val event = intake().single()
        assertEquals(80.0, event.mg, 0.0)
        assertEquals(IntakeSource.CUSTOM, event.source)
        assertEquals(millis("13:00"), event.takenAt)

        compose.onNode(hasContentDescription("Remove Other caffeine")).performClick()
        waitForNoText("Other caffeine")
        assertEquals(0, intake().size)
    }

    @Test
    fun bedtimeHalfLifeAndLimitAreSetHere() {
        open("09:00")
        field("Hour").performScrollTo().performTextReplacement("10")
        field("Min").performTextReplacement("15")
        settled { it.bedtime == LocalTime.of(22, 15) }
        waitForText("After 10:15 pm")

        compose.onNode(hasContentDescription("Longer half-life")).performScrollTo().performClick()
        settled { it.halfLifeHours == 5.5 }

        field("Limit").performScrollTo().performTextReplacement("40")
        settled { it.limitOverrideMg == 40.0 }
        waitForText("Your own limit", substring = true)
        field("Limit").performTextReplacement("")
        settled { it.limitOverrideMg == null }
        assertNull(settings().limitOverrideMg)
        compose.onRoot().captureRoboImage("screenshots/sleep_settings.png")
    }
}
