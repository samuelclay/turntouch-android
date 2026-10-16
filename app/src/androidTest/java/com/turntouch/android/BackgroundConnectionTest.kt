package com.turntouch.android

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Requires a saved nearby remote, with other hosts disconnected. Toggles the real radio. */
@Suppress("MissingPermission")
class BackgroundConnectionTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun reconnectsAfterBluetoothToggleWithoutOpeningActivity() {
        assumeTrue("Opt in with -e backgroundBluetooth true",
            InstrumentationRegistry.getArguments().getString("backgroundBluetooth")=="true")
        val app=(rule.activity.application as TurnTouchApp).state
        val adapter=rule.activity.getSystemService(BluetoothManager::class.java).adapter
        assumeTrue("Enable Bluetooth and pair a remote first",adapter.isEnabled && app.bluetooth.hasSavedRemotes)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun shell(command: String)=ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        fun awaitState(message: String,timeout: Long=30000,predicate: ()->Boolean) {
            val until=SystemClock.elapsedRealtime()+timeout
            while(SystemClock.elapsedRealtime()<until) {
                var ready=false
                instrumentation.runOnMainSync { ready=predicate() }
                if(ready)return
                Thread.sleep(200)
            }
            fail(message)
        }
        awaitState("Remote never connected",60000) { app.bluetooth.remotes.any { it.state=="Connected" } }
        val address=app.bluetooth.remotes.first { it.state=="Connected" }.address
        rule.runOnIdle { rule.activity.moveTaskToBack(true) }
        try {
            Thread.sleep(20000)
            awaitState("Connection lost after backgrounding") { app.bluetooth.remotes.any { it.address==address && it.state=="Connected" } }
            shell("svc bluetooth disable")
            // BLE scanning may remain enabled system-wide; classic STATE_OFF is not required.
            awaitState("Bluetooth never turned off") { !adapter.isEnabled }
            awaitState("App did not clear its old connection") { app.bluetooth.remotes.none { it.state=="Connected" } }
            shell("svc bluetooth enable")
            awaitState("Bluetooth never turned on") { adapter.state==BluetoothAdapter.STATE_ON }
            awaitState("Remote did not reconnect in background",60000) {
                app.bluetooth.remotes.any { it.address==address && it.state=="Connected" }
            }
            Thread.sleep(20000)
            awaitState("Recovered connection did not persist") { app.bluetooth.remotes.any { it.address==address && it.state=="Connected" } }
            android.util.Log.i("TurnTouchTest","BACKGROUND PASS: connection survived backgrounding and recovered after radio toggle without reopening activity")
        } finally {
            shell("svc bluetooth enable")
        }
    }
}
