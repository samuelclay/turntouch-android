package com.turntouch.android

import android.media.AudioManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in physical-device checks. Every mutation restores the observed starting state. */
class ReleaseHardwareTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app get() = (context.applicationContext as TurnTouchApp).state
    private fun log(message: String) { android.util.Log.i("TurnTouchTest", message) }
    private fun execute(mode: String, action: String, options: Map<String, String> = emptyMap()) = runBlocking {
        withContext(Dispatchers.Main) { app.executor.run(Binding(mode, "TTMode$mode$action", options)) }
    }

    @Test fun inventoryAndDiscovery() = runBlocking {
        for (mode in listOf("Hue", "Nanoleaf", "Sonos", "Govee", "Kasa")) {
            val config = app.config(mode)
            log("Saved $mode: fields=${config.filterValues { it.isNotBlank() }.keys}")
        }
        log("SSDP: ${Discovery.ssdp(context)}")
        log("Govee LAN: ${LocalDiscovery.govee(context)}")
        log("Kasa LAN: ${LocalDiscovery.kasa()}")
    }

    @Test fun nanoleafActionsReadBackAndRestore() {
        val c = app.config("Nanoleaf")
        assumeTrue("Pair Nanoleaf first", !c["token"].isNullOrBlank())
        val host = c.getValue("host").removePrefix("http://").trimEnd('/')
        val base = "http://$host${if (':' in host) "" else ":16021"}/api/v1/${c.getValue("token")}"
        fun state() = JSONObject(Network.request("$base/state"))
        fun value(s: JSONObject, key: String) = s.getJSONObject(key).getInt("value")
        val original = state()
        val originalEffect = Network.request("$base/effects/select").trim('"')
        val effects = JSONObject(Network.request("$base/effects")).getJSONArray("effectsList")
        try {
            execute("Nanoleaf", "TurnOn")
            assertTrue(state().getJSONObject("on").getBoolean("value"))
            Network.request("$base/state", "PUT", """{"brightness":{"value":50,"duration":0}}""")
            execute("Nanoleaf", "RaiseBrightness")
            assertEquals(60, value(state(), "brightness"))
            execute("Nanoleaf", "LowerBrightness")
            assertEquals(50, value(state(), "brightness"))
            execute("Nanoleaf", "Toggle")
            assertFalse(state().getJSONObject("on").getBoolean("value"))
            execute("Nanoleaf", "Toggle")
            assertTrue(state().getJSONObject("on").getBoolean("value"))
            if (effects.length() > 0) {
                val effect = effects.getString(0)
                execute("Nanoleaf", "SceneCustom", mapOf("scene" to effect))
                assertEquals(effect, Network.request("$base/effects/select").trim('"'))
            }
            execute("Nanoleaf", "TurnOff")
            assertFalse(state().getJSONObject("on").getBoolean("value"))
        } finally {
            if ((0 until effects.length()).any { effects.getString(it) == originalEffect }) {
                Network.request("$base/effects", "PUT", JSONObject().put("select", originalEffect).toString())
            } else {
                val color = JSONObject()
                for (key in listOf("hue", "sat", "ct")) original.optJSONObject(key)?.let { color.put(key, JSONObject().put("value", it.get("value"))) }
                Network.request("$base/state", "PUT", color.toString())
            }
            Network.request("$base/state", "PUT", JSONObject()
                .put("brightness", JSONObject().put("value", value(original, "brightness")).put("duration", 0))
                .put("on", JSONObject().put("value", original.getJSONObject("on").getBoolean("value"))).toString())
            val restored = state()
            assertEquals(value(original, "brightness"), value(restored, "brightness"))
            assertEquals(original.getJSONObject("on").getBoolean("value"), restored.getJSONObject("on").getBoolean("value"))
        }
        log("PASS Nanoleaf on/off/toggle/brightness/effect with read-back and restore")
    }

    @Test fun hueBrightnessColorSaturationPowerAndScenesRestore() {
        val config=app.config("Hue")
        assumeTrue("Pair Hue first",!config["token"].isNullOrBlank())
        val base="${LightingOptions.base(config)}/api/${config.getValue("token")}"
        val lights=JSONObject(Network.request("$base/lights"))
        val groups=JSONObject(Network.request("$base/groups"))
        fun members(id: String): List<String> {
            val ids=groups.getJSONObject(id).getJSONArray("lights")
            return (0 until ids.length()).map {ids.getString(it)}
        }
        val group=groups.keys().asSequence().filter { id ->
            val ids=members(id)
            ids.size in 1..6 && ids.all {light -> lights.getJSONObject(light).getJSONObject("state").let { it.optBoolean("reachable") && it.has("hue") && it.has("sat") }}
        }.minByOrNull {members(it).size}
        assumeTrue("No small reachable color room",group!=null)
        val ids=members(group!!)
        val original=ids.associateWith {lights.getJSONObject(it).getJSONObject("state")}
        fun write(id: String, body: JSONObject) {
            val response=org.json.JSONArray(Network.request("$base/lights/$id/state","PUT",body.toString()))
            for(i in 0 until response.length())assertFalse("Hue rejected state change",response.getJSONObject(i).has("error"))
        }
        fun current()=ids.map {JSONObject(Network.request("$base/lights/$it")).getJSONObject("state")}
        fun action(name: String,options: Map<String,String> = emptyMap()) {
            execute("Hue",name,mapOf("group" to group)+options);Thread.sleep(600)
        }
        try {
            ids.forEach {write(it,JSONObject().put("on",true).put("bri",100).put("hue",20000).put("sat",100).put("effect","none").put("transitiontime",0))}
            action("RaiseBrightness");current().forEach {log("Hue raised brightness read-back: ${it.getInt("bri")}");assertEquals(125.0,it.getInt("bri").toDouble(),1.0)}
            action("LowerBrightness");current().forEach {log("Hue lowered brightness read-back: ${it.getInt("bri")}");assertEquals(100.0,it.getInt("bri").toDouble(),1.0)}
            action("RaiseSaturation");current().forEach {assertEquals(125,it.getInt("sat"))}
            action("LowerSaturation");current().forEach {assertEquals(100,it.getInt("sat"))}
            action("ShiftColorRight");current().forEach {assertEquals(25000,it.getInt("hue"))}
            action("ShiftColorLeft");current().forEach {assertEquals(20000,it.getInt("hue"))}
            action("SceneColorLoop");current().forEach {assertEquals("colorloop",it.getString("effect"))}
            ids.forEach {write(it,JSONObject().put("effect","none"))}
            action("SceneLightsOff");current().forEach {assertFalse(it.getBoolean("on"))}
            ids.forEach {write(it,JSONObject().put("on",true))}
            action("Sleep",mapOf("seconds" to "0"));current().forEach {assertFalse(it.getBoolean("on"))}
            val scenes=JSONObject(Network.request("$base/scenes"))
            val scene=scenes.keys().asSequence().firstOrNull {scenes.getJSONObject(it).optString("group")==group}
            if(scene!=null) {
                val expected=JSONObject(Network.request("$base/scenes/$scene")).getJSONObject("lightstates")
                for(name in listOf("SceneCustom","CycleScenes")) {
                    ids.forEach {write(it,JSONObject().put("on",false).put("bri",1))}
                    action(name,mapOf("scene" to scene,"scenes" to scene))
                    ids.zip(current()).forEach {(id,state)->
                        expected.optJSONObject(id)?.let { want ->
                            if(want.has("on"))assertEquals(want.getBoolean("on"),state.getBoolean("on"))
                            if(want.has("bri"))assertEquals(want.getInt("bri"),state.getInt("bri"))
                        }
                    }
                }
                log("PASS Hue custom scene and cycle scene read-back")
            } else log("SKIP Hue scene activation: no scene belongs to the selected small room")
        } finally {
            original.forEach {(id,old)->
                val restore=JSONObject().put("on",old.getBoolean("on")).put("bri",old.getInt("bri")).put("transitiontime",0).put("effect",old.optString("effect","none"))
                when(old.optString("colormode")) {"xy"->restore.put("xy",old.getJSONArray("xy"));"ct"->restore.put("ct",old.getInt("ct"));else->restore.put("hue",old.getInt("hue")).put("sat",old.getInt("sat"))}
                write(id,restore)
            }
            ids.zip(current()).forEach {(id,state)->assertEquals(original.getValue(id).getBoolean("on"),state.getBoolean("on"));assertEquals(original.getValue(id).getInt("bri"),state.getInt("bri"))}
        }
        log("PASS Hue brightness/saturation/color/colorloop/off/sleep-zero with read-back; restored ${ids.size} lights")
    }

    @Test fun sonosVolumeMuteReadBackAndRestore() = runBlocking {
        val c = app.config("Sonos")
        val discovered = if (c["host"].isNullOrBlank()) Discovery.ssdp(context).firstOrNull { it.server.contains("Sonos", true) } else null
        val host = c["host"]?.takeIf { it.isNotBlank() } ?: discovered?.location?.let { java.net.URI(it).host }
        assumeTrue("No Sonos discovered or configured", host != null)
        val base = "http://${host!!.removePrefix("http://").substringBefore(':')}:1400"
        fun soap(action: String, args: Map<String,String> = emptyMap()) = Network.soap(base,
            "/MediaRenderer/RenderingControl/Control", "urn:schemas-upnp-org:service:RenderingControl:1",
            action, mapOf("InstanceID" to "0", "Channel" to "Master") + args)
        fun volume() = Network.tag(soap("GetVolume"), "CurrentVolume")!!.toInt()
        fun mute() = Network.tag(soap("GetMute"), "CurrentMute")!!
        val oldVolume = volume()
        val oldMute = mute()
        try {
            execute("Sonos", "VolumeDown", mapOf("host" to host))
            assertEquals((oldVolume - 3).coerceAtLeast(0), volume())
            execute("Sonos", "VolumeUp", mapOf("host" to host))
            assertEquals(((oldVolume - 3).coerceAtLeast(0) + 3).coerceAtMost(100), volume())
            execute("Sonos", "VolumeMute", mapOf("host" to host))
            assertEquals(if (oldMute == "1") "0" else "1", mute())
        } finally {
            soap("SetVolume", mapOf("DesiredVolume" to "$oldVolume"))
            soap("SetMute", mapOf("DesiredMute" to oldMute))
            assertEquals(oldVolume, volume()); assertEquals(oldMute, mute())
        }
        log("PASS Sonos volume and mute through ActionExecutor with read-back and restore")
    }

    @Test fun goveeLanActionsReadBackAndRestore() = runBlocking {
        val host = app.config("Govee")["host"]?.takeIf { it.isNotBlank() }
            ?: LocalDiscovery.govee(context).firstOrNull()?.location?.let { java.net.URI(it).host }
        assumeTrue("No Govee LAN device discovered or configured", host != null)
        val old = GoveeLan.state(host!!)
        try {
            execute("Govee", "DeviceOn", mapOf("host" to host))
            assertEquals(1, GoveeLan.state(host).getInt("onOff"))
            execute("Govee", "BrightnessDown", mapOf("host" to host))
            assertEquals((old.getInt("brightness") - 10).coerceAtLeast(0), GoveeLan.state(host).getInt("brightness"))
            execute("Govee", "DeviceToggle", mapOf("host" to host))
            assertEquals(0, GoveeLan.state(host).getInt("onOff"))
        } finally {
            fun send(command: String, value: Int) {
                val bytes = JSONObject().put("msg", JSONObject().put("cmd", command).put("data", JSONObject().put("value", value))).toString().toByteArray()
                java.net.DatagramSocket().use { it.send(java.net.DatagramPacket(bytes, bytes.size, java.net.InetAddress.getByName(host), 4003)) }
            }
            send("brightness", old.getInt("brightness")); send("turn", old.getInt("onOff"))
            Thread.sleep(400)
            val restored = GoveeLan.state(host)
            assertEquals(old.getInt("brightness"), restored.getInt("brightness")); assertEquals(old.getInt("onOff"), restored.getInt("onOff"))
        }
        log("PASS Govee LAN on/toggle/brightness with read-back and restore")
    }

    @Test fun nativeVolumeReadBackAndRestore() {
        val audio = context.getSystemService(AudioManager::class.java)
        val old = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 4, 0)
            execute("Music", "VolumeDown")
            assertEquals(3, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            execute("Music", "VolumeUp")
            assertEquals(4, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            execute("Music", "VolumeMute")
            assertTrue(audio.isStreamMute(AudioManager.STREAM_MUSIC))
        } finally {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, old, 0)
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
        }
        log("PASS native Android volume up/down/mute and restore")
    }
}
