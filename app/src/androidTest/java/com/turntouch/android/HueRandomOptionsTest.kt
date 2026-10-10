package com.turntouch.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HueRandomOptionsTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun sixControlsPersistSeparatelyAndSelectedDiamondClosesInspector() {
        val app=(rule.activity.application as TurnTouchApp).state
        val snapshot=DeviceStateSnapshot(app)
        val prefs=rule.activity.getSharedPreferences("turntouch",0)
        val keys=listOf("16:2:North:Single","16:2:North:Double")
        try {
            rule.runOnIdle {
                app.finishOnboarding();app.screen="remote";app.changeButtonMode(false);app.select(2);app.changePerformMode(false)
                app.save(Direction.North,Gesture.Single,listOf(Binding("Hue","TTModeHueRandom")))
                prefs.edit().remove(keys[1]).commit()
            }
            rule.onNodeWithContentDescription("North button").performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithText("North button").assertDoesNotExist()
            rule.onNodeWithText("Done").assertDoesNotExist()
            for((label,choice) in listOf("Color" to "Same","Saturation" to "Strong","Luminance" to "Bright"))
                rule.onNodeWithContentDescription("Single tap $label: $choice").assertIsSelected()
            rule.onNodeWithContentDescription("Double tap Color: Mix").assertIsSelected()
            rule.onNodeWithContentDescription("Double tap Luminance: Dim").assertIsSelected()
            rule.onNodeWithContentDescription("Single tap Color: Different").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Double tap Saturation: Pastels").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Double tap Luminance: Bright").performScrollTo().performClick()
            rule.runOnIdle {
                val restored=AppState(rule.activity)
                assertEquals("0",restored.bindings(Direction.North).single().options["randomColors"])
                assertEquals("2",restored.bindings(Direction.North).single().options["randomSaturation"])
                assertEquals("1",restored.bindings(Direction.North,Gesture.Double).single().options["randomColors"])
                assertEquals("0",restored.bindings(Direction.North,Gesture.Double).single().options["randomSaturation"])
                assertEquals("2",restored.bindings(Direction.North,Gesture.Double).single().options["randomBrightness"])
            }
            rule.onNodeWithContentDescription("Single tap Color: Different").performScrollTo()
            rule.waitForIdle()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {bitmap->
                java.io.File(rule.activity.getExternalFilesDir(null),"hue-random-options.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            rule.onNodeWithContentDescription("North button").performScrollTo().performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithContentDescription("Change action 1").assertDoesNotExist()
            rule.onNodeWithContentDescription("Single tap Color: Different").assertDoesNotExist()
        } finally {
            rule.runOnIdle {snapshot.restore()}
        }
    }
    @Test fun twelveButtonModeKeepsRandomOptionsForEachTapSeparate() {
        val app=(rule.activity.application as TurnTouchApp).state
        val snapshot=DeviceStateSnapshot(app)
        try {
            rule.runOnIdle {
                app.finishOnboarding();app.screen="remote";app.changeButtonMode(true);app.changePerformMode(false)
                for(slot in 0..1) {app.select(slot);app.save(Direction.North,Gesture.Single,listOf(Binding("Hue","TTModeHueRandom")))}
                app.select(0)
            }
            rule.onNodeWithContentDescription("North button").performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithContentDescription("Color: Different").performScrollTo().performClick()
            rule.onNodeWithText("DOUBLE TAP").performClick()
            rule.onNodeWithContentDescription("North button").performScrollTo().performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithContentDescription("Color: Same").assertIsSelected()
            rule.onNodeWithContentDescription("Luminance: Dim").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals("0",app.bindings(Direction.North,slot=0).single().options["randomColors"])
                assertEquals("2",app.bindings(Direction.North,slot=1).single().options["randomColors"])
                assertEquals("0",app.bindings(Direction.North,slot=1).single().options["randomBrightness"])
            }
        } finally {rule.runOnIdle {snapshot.restore()}}
    }

}
