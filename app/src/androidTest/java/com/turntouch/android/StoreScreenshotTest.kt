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
        val language=InstrumentationRegistry.getArguments().getString("language") ?: "en"
        val languagePrefs=rule.activity.getSharedPreferences("turntouch-language",0)
        val welcomed=languagePrefs.getBoolean("welcomed",false)
        if(Localization.needsWelcome)rule.onNodeWithText(tr("Continue")).performClick()
        rule.runOnIdle { Localization.previewLanguage(language) }
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
            rule.onNodeWithContentDescription(tr("{0} button",direction.displayName)).performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithContentDescription(tr("Change action {0}",1)).assertExists()
            if(app.bindings(direction).firstOrNull()?.action=="TTModeHueRandom") {
                rule.onNodeWithContentDescription(tr("{0}: {1}: {2}",tr("Double tap"),tr("Luminance"),tr("Bright"))).performScrollTo()
            } else rule.onNodeWithContentDescription(tr("{0} button options","Hue")).performScrollTo()
            capture("02-button-actions")
            rule.onNodeWithContentDescription(tr("Change action {0}",1)).performScrollTo().performClick()
            capture("03-action-picker")
            rule.runOnIdle {rule.activity.onBackPressedDispatcher.onBackPressed()}
            rule.onNodeWithContentDescription(tr("Settings"),useUnmergedTree=true).performClick()
            rule.onNodeWithContentDescription(tr("Settings popover")).assertExists()
            capture("04-settings")
            rule.onNodeWithText(tr("How it works")).performScrollTo().performClick()
            rule.onNodeWithText(tr("Here's how it works")).assertIsDisplayed()
            capture("05-tutorial")
            rule.onNodeWithText(tr("Continue")).performClick()
            rule.onNodeWithText(tr("Each button performs an action")).assertIsDisplayed()
            capture("06-buttons")
            rule.onNodeWithText(tr("Cancel")).performClick()
            rule.onNodeWithText(tr("Change"),useUnmergedTree=true).performClick()
            capture("07-apps")
        } finally {
            rule.runOnIdle {snapshot.restore();Localization.previewLanguage(null);languagePrefs.edit().putBoolean("welcomed",welcomed).commit()}
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(600) // Window animations run independently of the Compose clock.
        val bitmap=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory=File(rule.activity.getExternalFilesDir(null),"store-1.0/${Localization.code}").apply {mkdirs()}
        File(directory,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
