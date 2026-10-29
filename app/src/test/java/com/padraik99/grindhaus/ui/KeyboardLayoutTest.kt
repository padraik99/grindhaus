package com.padraik99.grindhaus.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.RoastLevel
import com.padraik99.grindhaus.testing.TestGraph
import com.padraik99.grindhaus.ui.theme.GrindhausTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * With the keyboard up, a screen's pinned button must sit right on top of the keys: no dead
 * band between them (the tab bar's space counted twice) and nothing pushed under the status bar.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class KeyboardLayoutTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var graph: AppGraph

    @After
    fun tearDown() = graph.db.close()

    @Test
    fun theEditScreensSaveButtonSitsJustAboveTheKeyboard() {
        graph = TestGraph.create(TestGraph.clockAt("2026-10-26T09:00"))
        runBlocking { graph.db.beanDao().insert(Bean(title = "Ethiopia Guji", roastLevel = RoastLevel.LIGHT)) }
        compose.runOnUiThread { WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false) }
        compose.setContent { GrindhausTheme { GrindhausApp(startTab = Tab.HOME, graph = graph) } }

        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Espresso") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Espresso") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Save brew")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Save brew") and hasClickAction()).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("Edit Ethiopia Guji Espresso")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Edit Ethiopia Guji Espresso")).performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Save changes")).fetchSemanticsNodes().isNotEmpty() }

        val density = compose.activity.resources.displayMetrics.density
        val status = (24 * density).toInt()
        val nav = (48 * density).toInt()
        val keyboard = (300 * density).toInt()
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, status, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, nav))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, keyboard))
                .setVisible(WindowInsetsCompat.Type.ime(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, insets)
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/edit_with_keyboard.png")

        val rootHeight = compose.onRoot().getBoundsInRoot().bottom
        val keyboardTop = rootHeight - (keyboard / density).dp
        val save = compose.onNode(hasText("Save changes") and hasClickAction()).getBoundsInRoot()
        // The button's bar is 12 dp of padding below the button; allow a little more, never a band.
        val gap = keyboardTop - save.bottom
        assertTrue("Gap above the keyboard is $gap", gap >= 0.dp && gap <= 20.dp)
        val close = compose.onNode(hasContentDescription("Close without saving")).getBoundsInRoot()
        assertTrue("Close button is under the status bar", close.top >= (status / density).dp || close.bottom < 0.dp)
    }
}
