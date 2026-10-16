package com.turntouch.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OfflineDemoTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun previewExecutesEditsAndExitsWithoutChangingPersonalSetup() {
        val real=(rule.activity.application as TurnTouchApp).state
        val before=real.context.getSharedPreferences("turntouch",0).all.toMap()
        val secrets=real.context.getSharedPreferences("connections",0).all.toMap()
        val remotePrefs=real.context.getSharedPreferences("remotes",0).all.toMap()
        var wasOnboarding=false
        rule.runOnIdle {wasOnboarding=real.onboarding;real.onboarding=false;real.screen="remote"}
        try {
            rule.onNodeWithContentDescription("Settings",useUnmergedTree=true).performClick()
            rule.onNodeWithText("Try offline demo").performScrollTo().performClick()
            rule.onNodeWithText("Offline demo").assertIsDisplayed()
            rule.onNodeWithText("simulated").assertIsDisplayed()
            // Hue's default north scene changes the virtual room color.
            rule.onNodeWithContentDescription("North button").performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithContentDescription("Sample light on, color 1, brightness 70 percent").assertIsDisplayed()
            rule.onNodeWithText("Edit buttons").performClick()
            rule.onNodeWithContentDescription("North button").performTouchInput {click()}
            rule.mainClock.advanceTimeBy(500)
            rule.onNodeWithContentDescription("Change action 1").performScrollTo().performClick()
            rule.onNodeWithContentDescription("Action chooser").assertExists()
            rule.onNodeWithText("Room").assertDoesNotExist() // chooser replaced the inspector
            rule.onNodeWithContentDescription("Close action chooser").performClick()
            rule.onNodeWithContentDescription("Room selector").performScrollTo().performClick()
            rule.onNodeWithText("Sample living room").performScrollTo().performClick()
            rule.onNodeWithText("Try buttons").performClick()
            rule.onNodeWithText("Exit demo").performClick()
            rule.onNodeWithText("Offline demo").assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(before,real.context.getSharedPreferences("turntouch",0).all)
                assertEquals(secrets,real.context.getSharedPreferences("connections",0).all)
                assertEquals(remotePrefs,real.context.getSharedPreferences("remotes",0).all)
                real.screen="demo"
            }
            // A second visit is fresh rather than persisting the preview brightness or assignments.
            rule.onNodeWithContentDescription("Sample light on, color 0, brightness 70 percent").assertIsDisplayed()
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            java.io.File(rule.activity.getExternalFilesDir(null),"offline-demo.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            bitmap.recycle()
            rule.onNodeWithText("Exit demo").performClick()
        } finally {rule.runOnIdle {real.screen="remote";real.onboarding=wasOnboarding}}
    }

    @Test fun everyExecutorRouteIsInterceptedAndSecretsStayIsolated() {
        val context=rule.activity.applicationContext
        val session=DemoSession()
        val demo=AppState(context,session)
        val executor=ActionExecutor(demo)
        executor.nativeAction={error("A demo action must never open real native content")}
        assertTrue(demo.config("Custom").isEmpty())
        demo.saveConfig("Custom",mapOf("url" to "http://127.0.0.1:1"))
        listOf("Custom","Hue","Nanoleaf","Govee","Sonos","Music","Camera","Web","AlarmClock","Kasa","Ifttt").forEach {mode->
            val action=demo.catalog.mode(mode).actions.first()
            runBlocking {executor.run(Binding(mode,action.id))}
            assertEquals("Simulated: ${demo.catalog.mode(mode).title} · ${action.title}",session.result)
        }
        assertTrue(AppState(context,DemoSession()).config("Custom").isEmpty())
    }
}
