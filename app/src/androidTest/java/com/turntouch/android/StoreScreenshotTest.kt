package com.turntouch.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in capture of real device UI. Restores the user's assignments and preferences. */
class StoreScreenshotTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun captureStoreScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeScreenshots")=="true")
        val app=(rule.activity.application as TurnTouchApp).state
        lateinit var snapshot: DeviceStateSnapshot
        rule.runOnIdle {
            snapshot=DeviceStateSnapshot(app)
            app.finishOnboarding();app.screen="remote";app.changePerformMode(false)
            app.changeButtonMode(false)
            app.select((0..3).firstOrNull {app.mode(it).id=="Hue"} ?: 2)
        }
        try {
            // Capture connection status as reported by the real device; never synthesize it.
            rule.waitUntil(60000) {app.bluetooth.remotes.any {it.state=="Connected"}}
            capture("01-actions")
            val direction=Direction.entries.firstOrNull {app.bindings(it).firstOrNull()?.action=="TTModeHueRandom"} ?: Direction.North
            rule.onNodeWithContentDescription("${direction.name} button").performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithContentDescription("Change action 1").assertExists()
            if(app.bindings(direction).firstOrNull()?.action=="TTModeHueRandom") {
                rule.onNodeWithContentDescription("Double tap Luminance: Bright").performScrollTo()
            } else rule.onNodeWithContentDescription("Hue button options").performScrollTo()
            capture("02-button-actions")
            rule.onNodeWithContentDescription("Change action 1").performScrollTo().performClick()
            capture("03-action-picker")
            rule.runOnIdle {rule.activity.onBackPressedDispatcher.onBackPressed()}
            rule.onNodeWithContentDescription("Settings",useUnmergedTree=true).performClick()
            rule.onNodeWithContentDescription("Settings popover").assertExists()
            capture("04-settings")
            rule.onNodeWithText("How it works").performScrollTo().performClick()
            rule.onNodeWithText("Here's how it works").assertIsDisplayed()
            capture("05-tutorial")
            rule.onNodeWithText("Continue").performClick()
            rule.onNodeWithText("Each button performs an action").assertIsDisplayed()
            capture("06-buttons")
            rule.onNodeWithText("Cancel").performClick()
            rule.onNodeWithText("Change",useUnmergedTree=true).performClick()
            capture("07-apps")
        } finally {
            rule.runOnIdle {snapshot.restore()}
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(600) // Window animations run independently of the Compose clock.
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory=File(rule.activity.getExternalFilesDir(null),"store-1.0").apply {mkdirs()}
        File(directory,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
