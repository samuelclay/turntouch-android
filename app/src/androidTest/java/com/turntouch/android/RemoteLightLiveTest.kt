package com.turntouch.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/** Run only with -e liveRemote true, with a person ready to tap, double-tap, and hold. */
class RemoteLightLiveTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun realRemoteControlsNanoleafFromBackgroundAndRestoresState() {
        assumeTrue("Opt in with -e liveRemote true", InstrumentationRegistry.getArguments().getString("liveRemote")=="true")
        val app=(rule.activity.application as TurnTouchApp).state
        val config=app.config("Nanoleaf")
        assumeTrue("Pair a remote and Nanoleaf first",app.bluetooth.hasSavedRemotes && !config["token"].isNullOrBlank())
        val host=config.getValue("host").removePrefix("http://").trimEnd('/')
        val base="http://$host${if(':' in host)"" else ":16021"}/api/v1/${config.getValue("token")}/state"
        fun state()=JSONObject(Network.request(base))
        val original=state()
        lateinit var snapshot: DeviceStateSnapshot
        rule.runOnIdle {snapshot=DeviceStateSnapshot(app)}
        val seen=CopyOnWriteArrayList<String>()
        val failures=CopyOnWriteArrayList<String>()
        ServerSocket(0,8,java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout=1000
            val listening=java.util.concurrent.atomic.AtomicBoolean(true)
            val listener=thread(name="remote-light-readback") {
                var previous=state()
                while(listening.get()) {
                    try {server.accept().use { socket ->
                        socket.soTimeout=3000
                        val request=socket.getInputStream().bufferedReader()
                        val path=request.readLine().split(' ')[1].removePrefix("/")
                        while(!request.readLine().isNullOrEmpty()) {}
                        val gesture=path.substringBefore('-')
                        val current=state()
                        try {
                            when(gesture) {
                                "Single" -> assertEquals((previous.getJSONObject("brightness").getInt("value")+10).coerceAtMost(100),current.getJSONObject("brightness").getInt("value"))
                                "Double" -> assertEquals((previous.getJSONObject("brightness").getInt("value")-10).coerceAtLeast(0),current.getJSONObject("brightness").getInt("value"))
                                "Hold" -> assertEquals(!previous.getJSONObject("on").getBoolean("value"),current.getJSONObject("on").getBoolean("value"))
                                else -> error("Unexpected gesture")
                            }
                            seen.add(gesture)
                            android.util.Log.i("TurnTouchTest","REMOTE LIGHT PASS $path: Nanoleaf read-back confirmed")
                        } catch(e: Throwable) {failures.add("$path: ${e.message}")}
                        previous=current
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())
                    }} catch(_:java.net.SocketTimeoutException) {} catch(e: Exception) {if(listening.get())failures.add(e.toString())}
                }
            }
            try {
                rule.runOnIdle {
                    app.finishOnboarding();app.changeButtonMode(true);app.screen="remote"
                    for(gesture in Gesture.entries) {
                        app.select(gesture.ordinal)
                        for(direction in Direction.entries)app.save(direction,Gesture.Single,listOf(
                            Binding("Nanoleaf",when(gesture) {Gesture.Single->"TTModeNanoleafRaiseBrightness";Gesture.Double->"TTModeNanoleafLowerBrightness";Gesture.Hold->"TTModeNanoleafToggle"}),
                            Binding("Custom","TTModeCustomURL",mapOf("url" to "http://127.0.0.1:${server.localPort}/${gesture.name}-${direction.name}"))
                        ))
                    }
                    app.select(0);app.bluetooth.reconnect()
                }
                rule.waitUntil(20000) {app.bluetooth.remotes.any {it.state=="Connected"}}
                rule.runOnIdle {rule.activity.moveTaskToBack(true)}
                android.util.Log.i("TurnTouchTest","REMOTE LIGHT READY: app backgrounded; single tap, double tap, then hold any button, pausing between gestures")
                val timeout=InstrumentationRegistry.getArguments().getString("remoteTimeoutSeconds")?.toLongOrNull()?.coerceIn(10,600) ?: 180
                val until=System.currentTimeMillis()+timeout*1000
                while(System.currentTimeMillis()<until && seen.toSet().size<3 && failures.isEmpty())Thread.sleep(250)
                assertTrue("Physical remote action failed: $failures",failures.isEmpty())
                assertEquals("Missing real remote gestures",setOf("Single","Double","Hold"),seen.toSet())
            } finally {
                listening.set(false);listener.join(15000)
                Network.request(base,"PUT",JSONObject()
                    .put("brightness",JSONObject().put("value",original.getJSONObject("brightness").getInt("value")).put("duration",0))
                    .put("on",JSONObject().put("value",original.getJSONObject("on").getBoolean("value"))).toString())
                rule.runOnIdle {snapshot.restore()}
                val restored=state()
                assertEquals(original.getJSONObject("brightness").getInt("value"),restored.getJSONObject("brightness").getInt("value"))
                assertEquals(original.getJSONObject("on").getBoolean("value"),restored.getJSONObject("on").getBoolean("value"))
                android.util.Log.i("TurnTouchTest","REMOTE LIGHT restored Nanoleaf and button assignments")
            }
        }
    }
}
