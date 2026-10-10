package com.turntouch.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun catalogEditingAndBluetoothDiscovery() {
    val app=(rule.activity.application as TurnTouchApp).state
    rule.runOnIdle {app.finishOnboarding();app.screen="remote";app.select(1);app.changePerformMode(false)}
    rule.onNodeWithContentDescription("North button").assertExists().performTouchInput { click() }
    rule.mainClock.advanceTimeBy(500)
    rule.onRoot().printToLog("TurnTouchTest")
    rule.onNodeWithText("North button").assertExists()
    rule.onNodeWithContentDescription("Change action 1").performScrollTo().performClick()
    rule.onNodeWithText("Mute music").performScrollTo().performClick()
    rule.runOnIdle {Assert.assertEquals("TTModeMusicVolumeMute",app.bindings(Direction.North).first().action);app.save(Direction.North,Gesture.Single,listOf(Binding("Music","TTModeMusicVolumeUp")));app.screen="devices";app.bluetooth.startScan()}
    // Keep the real scanner alive after instrumentation, inspect results via logcat.
    Thread.sleep(12000)
    rule.runOnIdle {android.util.Log.i("TurnTouchTest","BLE results: ${app.bluetooth.remotes}")}
 }
 @Test fun bluetoothDiscovery() {
    val app=(rule.activity.application as TurnTouchApp).state
    rule.runOnIdle {app.finishOnboarding();app.screen="devices";app.bluetooth.startScan()}
    Thread.sleep(16000)
    rule.runOnIdle {android.util.Log.i("TurnTouchTest","BLE results: ${app.bluetooth.remotes}");app.bluetooth.remotes.firstOrNull()?.let {app.bluetooth.connect(it.address)}}
    Thread.sleep(8000)
    rule.runOnIdle {android.util.Log.i("TurnTouchTest","BLE connection: ${app.bluetooth.remotes}")}
 }
 @Test fun wifiDiscovery() {val context=InstrumentationRegistry.getInstrumentation().targetContext;val devices=runBlocking {Discovery.ssdp(context)};android.util.Log.i("TurnTouchTest","LAN results: $devices")}
}
