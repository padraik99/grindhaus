package com.padraik99.grindhaus.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.padraik99.grindhaus.ui.theme.GrindhausTheme
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

    @Test
    fun brewTab() {
        compose.setContent { GrindhausTheme { GrindhausApp() } }
        compose.onNodeWithText("GRINDHAUS").assertExists()
        compose.onRoot().captureRoboImage("screenshots/brew.png")
    }

    @Test
    fun sleepTabShowsDerivedLimits() {
        compose.setContent { GrindhausTheme { GrindhausApp() } }
        compose.onNodeWithText("Sleep").performClick()
        compose.onNodeWithText("5 h  →  32 mg").assertExists()
        compose.onRoot().captureRoboImage("screenshots/sleep.png")
    }
}
