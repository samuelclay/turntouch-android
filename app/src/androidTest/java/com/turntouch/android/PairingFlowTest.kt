package com.turntouch.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class PairingFlowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val app get()=(rule.activity.application as TurnTouchApp).state
    private lateinit var snapshot: DeviceStateSnapshot
    @Before fun remember() { rule.runOnIdle { snapshot=DeviceStateSnapshot(app); app.finishOnboarding() } }
    @After fun restore() { rule.runOnIdle { app.bluetooth.cancelPairing(); snapshot.restore() } }

    @Test fun tutorialSwipesBothWaysAndFinishesWithoutStartingBluetoothPairing() {
        rule.runOnIdle { app.screen="tutorial" }
        rule.onNodeWithText("Here's how it works").assertIsDisplayed()
        rule.onNodeWithContentDescription("Page 1 of 5").assertExists()
        screenshot("pairing-tutorial-first")
        rule.onNodeWithContentDescription("How it works pages").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Page 2 of 5").assertExists()
        rule.onNodeWithText("Each button performs an action").assertIsDisplayed()
        rule.onNodeWithContentDescription("How it works pages").performTouchInput { swipeRight() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Page 1 of 5").assertExists()
        repeat(4) { rule.onNodeWithText("Continue").performClick(); rule.waitForIdle() }
        rule.onNodeWithText("Choose your apps and actions").assertIsDisplayed()
        rule.onNodeWithContentDescription("Page 5 of 5").assertExists()
        screenshot("pairing-tutorial-last")
        rule.onNodeWithText("That's all there is to it").performClick()
        rule.runOnIdle { assertEquals("remote",app.screen); assertFalse(app.onboarding); assertFalse(app.bluetooth.scanning) }
    }

    @Test fun cancelPairingStopsScanAndClosesUnconfirmedConnections() {
        rule.runOnIdle { app.screen="pairing" }
        if(rule.onAllNodesWithText("Pair remote").fetchSemanticsNodes().isNotEmpty())rule.onNodeWithText("Pair remote").performClick()
        rule.waitUntil(10000) { app.bluetooth.scanning || app.bluetooth.pairingPhase==RemotePairingPhase.Failure }
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle {
            assertFalse(app.bluetooth.scanning)
            assertTrue(app.bluetooth.remotes.filter { !app.bluetooth.isSaved(it.address) }.all { it.state=="Disconnected" || it.state=="Nearby" })
            assertEquals("remote",app.screen)
        }
        // Give in-flight GATT callbacks time to arrive after the dialog has closed.
        Thread.sleep(1500)
        rule.runOnIdle { assertFalse(app.bluetooth.scanning); assertFalse(app.bluetooth.remotes.any { it.state.startsWith("Press") }) }
    }

    @Test fun unansweredPairingTimesOutThenRetryCanBeCancelled() {
        // Opt in because this waits for real Bluetooth timers and must not receive physical presses.
        org.junit.Assume.assumeTrue("Run with -e pairingTimeout true",InstrumentationRegistry.getArguments().getString("pairingTimeout")=="true")
        val pairedBefore=app.bluetooth.remotes.filter {app.bluetooth.isSaved(it.address)}.map {it.address}.toSet()
        rule.runOnIdle {app.screen="pairing"}
        if(rule.onAllNodesWithText("Pair remote").fetchSemanticsNodes().isNotEmpty())rule.onNodeWithText("Pair remote").performClick()
        val started=System.currentTimeMillis()
        rule.waitUntil(65000) {app.bluetooth.pairingPhase==RemotePairingPhase.Failure}
        assertTrue("Must allow time to discover or confirm a remote",System.currentTimeMillis()-started>=14000)
        rule.onNodeWithText("Uh Oh...").assertIsDisplayed()
        rule.onNodeWithText("Try again").assertIsDisplayed()
        screenshot("pairing-timeout")
        rule.runOnIdle {
            assertFalse(app.bluetooth.scanning)
            assertTrue(pairedBefore.all {app.bluetooth.isSaved(it)})
            assertFalse(app.bluetooth.remotes.any {it.state.startsWith("Press")})
        }
        rule.onNodeWithText("Try again").performClick()
        rule.waitUntil(5000) {app.bluetooth.scanning}
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle {assertFalse(app.bluetooth.scanning)}
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        Thread.sleep(500) // Android's dialog-window fade runs outside the Compose test clock.
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(rule.activity.getExternalFilesDir(null),"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        }
    }
}
