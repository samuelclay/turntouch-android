package com.turntouch.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InlineLightingOptionsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun hueRoomsAndScenesPersistInline() = checkPicker("Hue", "Scene", "TTModeHueSceneCustom")
    @Test fun nanoleafEffectsPersistInline() = checkPicker("Nanoleaf", "Effect", "TTModeNanoleafToggle")

    private fun checkPicker(mode: String, label: String, action: String) {
        val app = (rule.activity.application as TurnTouchApp).state
        val config = app.config(mode)
        assertFalse("Connect $mode in the app before running this live test", config["token"].isNullOrBlank())
        val choices = runBlocking { LightingOptions.load(mode, config) }
        assertTrue("The connected device should return at least one scene/effect", choices.scenes.isNotEmpty())
        val slot = (0..3).first { app.mode(it).id == mode }
        val original = app.bindings(Direction.North, Gesture.Single, slot)
        val selected = choices.scenes.first()
        try {
            rule.runOnIdle {
                app.finishOnboarding(); app.screen = "remote"; app.select(slot); app.changePerformMode(false)
                app.save(Direction.North, Gesture.Single, listOf(Binding(mode, action)))
            }
            rule.onNodeWithContentDescription("North button").performTouchInput { click() }
            rule.mainClock.advanceTimeBy(500)
            rule.waitUntil(15000) { rule.onAllNodesWithContentDescription("$label selector").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("$label selector").performScrollTo().performClick()
            rule.waitUntil(15000) { rule.onAllNodesWithText(selected.name).fetchSemanticsNodes().isNotEmpty() }
            rule.onAllNodesWithText(selected.name).onFirst().performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(selected.id, app.bindings(Direction.North).single().options["scene"])
                assertEquals(selected.id, AppState(rule.activity).bindings(Direction.North).single().options["scene"])
            }
            rule.onAllNodes(isDialog()).assertCountEquals(0)
            rule.onNodeWithText("Options").assertDoesNotExist()
            rule.onNodeWithText("Ready").assertDoesNotExist()
            rule.onNodeWithText("Test").assertDoesNotExist()
            rule.onNodeWithText("Change action").assertDoesNotExist()
            rule.onNodeWithText("Refresh effects").assertDoesNotExist()
            rule.onNodeWithText("Refresh rooms and scenes").assertDoesNotExist()
            rule.onNodeWithText("Add new action").assertExists()
            rule.onNodeWithContentDescription("Refresh ${if(mode=="Nanoleaf")"effects" else "rooms and scenes"}").assertExists()
            rule.onNodeWithContentDescription("$mode button options").assertExists()
            rule.onNodeWithContentDescription("$label selector").performScrollTo().assertIsDisplayed()
            rule.onNodeWithContentDescription("$label selector").performClick()
            rule.mainClock.advanceTimeBy(500)
            rule.waitForIdle()
            android.util.Log.i("TurnTouchTest", "$mode: ${choices.rooms.size} rooms, ${choices.scenes.size} scenes/effects; inline selection and persistence passed")
            Thread.sleep(1000)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(rule.activity.getExternalFilesDir(null), "$mode-inline-options.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            rule.runOnIdle { app.save(Direction.North, Gesture.Single, original) }
        }
    }
}
