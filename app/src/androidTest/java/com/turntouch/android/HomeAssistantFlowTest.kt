package com.turntouch.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.json.*

/** Run against scripts/home_assistant_fixture.py through adb reverse tcp:18123 tcp:18123. */
class HomeAssistantFlowTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun sixteenButtonAssignment() = checkLayout(false)
    @Test fun twelveButtonAssignment() = checkLayout(true)
    @Test fun oauthCredentialsRestoreAndDispatch() {
        val app=(rule.activity.application as TurnTouchApp).state
        val original=app.homeAssistant
        val client=HomeAssistantClient(app.context,"HomeAssistantOAuthInstrumentation")
        try {
            runBlocking {client.exchange("http://127.0.0.1:18123","fixture-code")}
            rule.runOnIdle {app.homeAssistant=client;assertTrue(app.config("HomeAssistant").isNotEmpty())}
            val before=JSONArray(Network.request("http://127.0.0.1:18123/records")).length()
            runBlocking {
                app.executor.run(Binding("HomeAssistant","TTModeHomeAssistantAction",mapOf("server_url" to client.server,"entity_id" to "light.desk","service" to "light.toggle","data" to "{}")))
                val restored=HomeAssistantClient(app.context,"HomeAssistantOAuthInstrumentation");restored.restore();assertTrue(restored.connected)
            }
            assertEquals(before+1,JSONArray(Network.request("http://127.0.0.1:18123/records")).length())
        } finally {runBlocking {client.disconnect()};rule.runOnIdle {app.homeAssistant=original}}
    }
    @Test fun localizedLayouts() {
        if(rule.onAllNodesWithText("Welcome to Turn Touch").fetchSemanticsNodes().isNotEmpty())rule.onNodeWithText("Continue").performClick()
        val app=(rule.activity.application as TurnTouchApp).state
        val snapshot=DeviceStateSnapshot(app)
        val original=app.homeAssistant
        val client=HomeAssistantClient(app.context,"HomeAssistantLayoutInstrumentation")
        runBlocking {client.connect("http://127.0.0.1:18123","fixture-token")}
        try {
            rule.runOnIdle {
                app.homeAssistant=client;app.finishOnboarding();app.changePerformMode(false);app.changeButtonMode(true);app.select(0);app.screen="remote"
                app.save(Direction.North,Gesture.Single,listOf(Binding("HomeAssistant","TTModeHomeAssistantAction",mapOf("entity_id" to "scene.evening","service" to "scene.turn_on","data" to "{}","server_url" to client.server))))
            }
            rule.onNodeWithContentDescription("North button").performScrollTo().performClick()
            rule.waitUntil(15000) {rule.onAllNodesWithText("Evening",substring=false).fetchSemanticsNodes().isNotEmpty()}
            for(language in listOf("de","ar")) {
                rule.runOnIdle {Localization.previewLanguage(language)}
                rule.onNodeWithText(tr("Set remote button")).performScrollTo().assertIsDisplayed().assertIsEnabled()
                val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                java.io.File(rule.activity.getExternalFilesDir(null),"home-assistant-$language.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            runBlocking {client.disconnect()}
            for(language in listOf("de","ar")) {
                rule.runOnIdle {Localization.previewLanguage(language)}
                rule.onNodeWithTag("ha-sign-in").performScrollTo().assertIsDisplayed().assertIsEnabled()
                val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                java.io.File(rule.activity.getExternalFilesDir(null),"home-assistant-sign-in-$language.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            rule.onNodeWithTag("ha-sign-in").performClick()
            rule.onNodeWithText(tr("Home Assistant address")).assertExists()
        } finally {runBlocking {client.disconnect()};rule.runOnIdle {Localization.previewLanguage(null);app.homeAssistant=original;snapshot.restore()}}
    }
    private fun checkLayout(twelve: Boolean) {
        if(rule.onAllNodesWithText("Welcome to Turn Touch").fetchSemanticsNodes().isNotEmpty())rule.onNodeWithText("Continue").performClick()
        val app=(rule.activity.application as TurnTouchApp).state
        val snapshot=DeviceStateSnapshot(app)
        val original=app.homeAssistant
        val client=HomeAssistantClient(app.context,"HomeAssistantInstrumentation")
        runBlocking {client.connect("http://127.0.0.1:18123","fixture-token")}
        try {

                rule.runOnIdle {
                    app.homeAssistant=client; app.finishOnboarding();app.changePerformMode(false);app.changeButtonMode(twelve);app.select(0);app.screen="remote"
                    app.save(Direction.North,Gesture.Single,listOf(Binding("HomeAssistant","TTModeHomeAssistantAction")))
                }
                rule.onNodeWithContentDescription("North button").performScrollTo().performTouchInput {click()}
                rule.mainClock.advanceTimeBy(500)
                rule.waitUntil(15000) {rule.onAllNodesWithText("Search Home Assistant").fetchSemanticsNodes().isNotEmpty()}
                rule.onNodeWithText("Search Home Assistant").performScrollTo().performTextInput("living")
                rule.onNode(hasSetTextAction()).performImeAction()
                rule.waitUntil(5000) {androidx.core.view.ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())!=true}
                rule.waitForIdle()
                rule.onNodeWithTag("ha-entities").performScrollTo().assertIsDisplayed()
                rule.onNodeWithText("Evening",substring=false).performClick()
                rule.onNodeWithText("Action settings").assertExists()
                rule.onNodeWithText("Action data (optional JSON)").assertDoesNotExist()
                rule.onNodeWithText("Set remote button").performScrollTo()
                rule.waitUntil(10000) {rule.onAllNodesWithText("Set remote button").filter(isEnabled()).fetchSemanticsNodes().isNotEmpty()}
                assertTrue(app.bindings(Direction.North).single().options.isEmpty())
                val before=JSONArray(Network.request("http://127.0.0.1:18123/records")).length()
                rule.onNodeWithText("Test action").performScrollTo().performClick()
                rule.waitUntil(10000) {rule.onAllNodesWithText("Home Assistant accepted the action.").fetchSemanticsNodes().isNotEmpty()}
                assertTrue("Testing must not assign",app.bindings(Direction.North).single().options.isEmpty())
                val calls=JSONArray(Network.request("http://127.0.0.1:18123/records"))
                assertEquals(before+1,calls.length())
                assertEquals("scene.evening",calls.getJSONObject(calls.length()-1).getJSONObject("body").getString("entity_id"))
                rule.onNodeWithText("Set remote button").performScrollTo().performClick()
                rule.runOnIdle {
                    val saved=AppState(app.context).bindings(Direction.North).single()
                    assertEquals("scene.evening",saved.options["entity_id"])
                    assertEquals("scene.turn_on",saved.options["service"])
                }
                rule.onNodeWithText("Set remote button").performScrollTo()
                rule.waitForIdle()
                val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                java.io.File(rule.activity.getExternalFilesDir(null),"home-assistant-${if(twelve)12 else 16}.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                runBlocking {app.executor.run(app.bindings(Direction.North).single())}
                assertEquals(before+2,JSONArray(Network.request("http://127.0.0.1:18123/records")).length())

            val automation=mapOf("server_url" to client.server,"entity_id" to "automation.evening","service" to "automation.trigger","data" to "{}")
            runBlocking {client.call(automation)}
            val automationCalls=JSONArray(Network.request("http://127.0.0.1:18123/records"))
            assertFalse(automationCalls.getJSONObject(automationCalls.length()-1).getJSONObject("body").getBoolean("skip_condition"))
            runBlocking {
                val restored=HomeAssistantClient(app.context,"HomeAssistantInstrumentation");restored.restore();assertTrue(restored.connected)
                try { restored.call(automation+("server_url" to "http://different-home"));fail("Cross-home action should fail") } catch(expected:IllegalStateException) {}
            }
        } catch(failure:Throwable) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { shot ->
                java.io.File(rule.activity.getExternalFilesDir(null),"ha-failure.png").outputStream().use {shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
            throw failure
        } finally {runBlocking {client.disconnect()};rule.runOnIdle {app.homeAssistant=original;snapshot.restore()}}
    }
}
