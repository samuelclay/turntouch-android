package com.turntouch.android

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RemoteFeedbackTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    // Keep fixtures out of the user's remote list, assignments, and credentials.
    private fun isolatedContext(): Context = object: ContextWrapper(rule.activity.applicationContext) {
        override fun getSharedPreferences(name: String, mode: Int) =
            super.getSharedPreferences("feedback-test-$name",mode)
    }.also { context ->
        listOf("turntouch","connections","remotes").forEach {
            context.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun physicalPressHighlightsOnceWhileActionStillRuns() {
        rule.runOnIdle {
            val app=AppState(isolatedContext())
            app.executor=ActionExecutor(app)
            var executed=0
            val mode=app.catalog.mode("Web")
            app.save(Direction.North,Gesture.Single,listOf(Binding(mode.id,mode.actions.first().id)))
            app.executor.nativeAction={executed++}
            val decoder=ButtonDecoder(app::dispatch) { app.active=it }
            try {
                decoder.read(byteArrayOf(-2,0),0)
                assertEquals(Direction.North,app.active)
                decoder.read(byteArrayOf(-1,0),80)
                assertNull(app.active)
                decoder.flush(400)
                assertEquals("A single press must still execute its action once",1,executed)
                assertNull("Dispatch must not re-highlight a released button",app.active)
            } finally { app.scope.cancel() }
        }
    }

    @Test fun unavailableRemoteTimesOutAndCanBeRetried() {
        assumeTrue("Opt in with -e connectionTimeout true",
            InstrumentationRegistry.getArguments().getString("connectionTimeout")=="true")
        lateinit var app: AppState
        val address="02:00:00:00:00:01" // Deliberately nonexistent test remote.
        rule.runOnIdle {
            val context=isolatedContext()
            context.getSharedPreferences("remotes",Context.MODE_PRIVATE).edit()
                .putStringSet("paired",setOf(address)).commit()
            app=AppState(context)
            app.bluetooth=BluetoothController(app)
            app.bluetooth.connect(address,false,background=true)
            assertEquals("Connecting…",app.bluetooth.remotes.single().state)
        }
        fun awaitFailure() {
            val deadline=SystemClock.elapsedRealtime()+18000
            while(SystemClock.elapsedRealtime()<deadline) {
                var failed=false
                rule.runOnIdle { failed=app.bluetooth.remotes.single().state=="Connection failed" }
                if(failed)return
                Thread.sleep(100)
            }
            fail("An unavailable remote stayed in Connecting instead of reporting failure")
        }
        try {
            awaitFailure()
            rule.runOnIdle {
                assertNotNull(app.bluetooth.remotes.single().connectionError)
                // Retry must replace the existing automatic GATT registration.
                app.bluetooth.retry(address)
                assertEquals("Connecting…",app.bluetooth.remotes.single().state)
                assertNull(app.bluetooth.remotes.single().connectionError)
            }
            awaitFailure()
        } finally {
            rule.runOnIdle { app.bluetooth.disconnect(address,true); app.scope.cancel() }
        }
    }
}
