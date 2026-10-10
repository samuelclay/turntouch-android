package com.turntouch.android

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After

class DiamondInteractionTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    private lateinit var snapshot: DeviceStateSnapshot
    @Before fun rememberPreferences() {rule.runOnIdle {snapshot=DeviceStateSnapshot((rule.activity.application as TurnTouchApp).state)}}
    @After fun restorePreferences() {rule.runOnIdle {snapshot.restore()}}

    private fun openMusic(): AppState {
        val app=(rule.activity.application as TurnTouchApp).state
        rule.runOnIdle {app.finishOnboarding();app.screen="remote";app.changeButtonMode(false);app.select(1);app.changePerformMode(false)}
        return app
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let {bitmap ->
            java.io.File(rule.activity.getExternalFilesDir(null),"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
    }

    @Test fun fullFaceRespondsAndPressedHighlightFollowsDiamond() {
        openMusic()
        screenshot("main")
        val north=rule.onNodeWithContentDescription("North button")
        // The rectangle corner is outside the diamond and must not activate it.
        north.performTouchInput {click(Offset(width*0.05f,height*0.05f))}
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Change action 1").assertDoesNotExist()
        // A tap near the top tip, beyond the old label rectangle, must activate it.
        north.performTouchInput {down(Offset(width*0.5f,height*0.15f))}
        rule.waitForIdle()
        val pixels=north.captureToImage().toPixelMap()
        val fill=pixels[pixels.width/2,(pixels.height*0.15f).toInt()]
        assertEquals(0xc3/255f,fill.red,0.025f)
        assertEquals(0xc7/255f,fill.green,0.025f)
        val corner=pixels[(pixels.width*0.05f).toInt(),(pixels.height*0.05f).toInt()]
        assertTrue("The pressed fill must not cover the rectangle outside the face",kotlin.math.abs(fill.red-corner.red)>0.05f || corner.alpha<0.1f)
        screenshot("pressed")
        north.performTouchInput {up()}
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithContentDescription("Change action 1").assertExists()
    }

    @Test fun changeRevealsActionsAndPreservesOtherBatchActions() {
        val app=openMusic()
        val original=app.bindings(Direction.North)
        try {
            rule.runOnIdle {app.save(Direction.North,Gesture.Single,listOf(Binding("Music","TTModeMusicVolumeUp"),Binding("Music","TTModeMusicVolumeDown")))}
            rule.onNodeWithContentDescription("North button").performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithContentDescription("Change action 2").performScrollTo().performClick()
            rule.mainClock.advanceTimeBy(1000)
            rule.onNodeWithContentDescription("Action chooser").assertIsDisplayed()
            rule.onNodeWithText("Choose action").assertIsDisplayed()
            screenshot("chooser")
            rule.onNodeWithText("Mute music").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(listOf("TTModeMusicVolumeUp","TTModeMusicVolumeMute"),app.bindings(Direction.North).map {it.action})
            }
        } finally {rule.runOnIdle {app.save(Direction.North,Gesture.Single,original)}}
    }

    @Test fun longLabelsStayCenteredInsideEveryFace() {
        val app=openMusic()
        val originals=Direction.entries.associateWith {app.bindings(it)}
        try {
            rule.runOnIdle {Direction.entries.forEach {app.save(it,Gesture.Single,listOf(Binding("Nanoleaf","TTModeNanoleafSceneCustom",mapOf("scene" to "Fireworks and Firecrackers"))))}}
            Direction.entries.forEach {direction ->
                val face=rule.onNodeWithContentDescription("${direction.name} button").fetchSemanticsNode().boundsInRoot
                val label=rule.onNode(hasText("Fireworks and Firecrackers") and hasAnyAncestor(hasContentDescription("${direction.name} button")),useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
                assertEquals(face.center.x,label.center.x,2f)
                assertEquals(face.center.y,label.center.y,2f)
                val relativeWidth=label.width/face.width
                val relativeHeight=label.height/face.height
                assertTrue("Label box must fit within the diamond's sloping sides",relativeWidth+relativeHeight<=1.02f)
            }
        } finally {rule.runOnIdle {originals.forEach {(direction,bindings)->app.save(direction,Gesture.Single,bindings)}}}
    }
}
