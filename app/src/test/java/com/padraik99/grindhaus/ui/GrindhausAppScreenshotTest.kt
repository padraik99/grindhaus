package com.padraik99.grindhaus.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.testing.TestGraph
import com.padraik99.grindhaus.ui.theme.GrindhausTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders screens on a simulated Pixel-sized device and saves PNGs for review. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h915dp-xxhdpi")
class GrindhausAppScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        graph = TestGraph.create(TestGraph.clockAt("2026-10-26T09:00"))
    }

    @After
    fun tearDown() = graph.db.close()

    @Test
    fun sleepTabOpensOnTonightsForecast() {
        compose.setContent { GrindhausTheme { GrindhausApp(graph = graph) } }
        compose.onNodeWithText("Sleep").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Under your 32\u00A0mg limit").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage("screenshots/sleep.png")
    }
}
