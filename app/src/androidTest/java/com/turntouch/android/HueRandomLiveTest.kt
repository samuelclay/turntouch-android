package com.turntouch.android

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class HueRandomLiveTest {
    @Test fun randomOptionsChangeRealRoomLightsAndRestoreOriginalStates() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val app=(context.applicationContext as TurnTouchApp).state
        val config=app.config("Hue")
        assumeTrue("Pair Hue on this device first",!config["token"].isNullOrBlank())
        val base="${LightingOptions.base(config)}/api/${config.getValue("token")}"
        val lights=LightingOptions.objectResponse(Network.request("$base/lights"))
        val groups=LightingOptions.objectResponse(Network.request("$base/groups"))
        fun members(group: JSONObject): List<String> {val ids=group.getJSONArray("lights");return (0 until ids.length()).map {ids.getString(it)}}
        val room=groups.keys().asSequence().filter {id->
            val ids=members(groups.getJSONObject(id))
            ids.size in 1..6 && ids.all {lightId ->
                val state=lights.optJSONObject(lightId)?.optJSONObject("state")
                state!=null && state.optBoolean("reachable") && state.has("hue") && state.has("sat")
            }
        }.minByOrNull {members(groups.getJSONObject(it)).size}
        assumeTrue("No small reachable color-light room is available",room!=null)
        val ids=members(groups.getJSONObject(room!!))
        val originals=ids.associateWith {lights.getJSONObject(it).getJSONObject("state")}
        try {
            for((color,brightness) in listOf("2" to "2","0" to "0")) {
                runBlocking {app.executor.run(Binding("Hue","TTModeHueRandom",mapOf("group" to room,"randomColors" to color,"randomBrightness" to brightness,"randomSaturation" to "0")))}
                Thread.sleep(600)
                val current=ids.map {LightingOptions.objectResponse(Network.request("$base/lights/$it")).getJSONObject("state")}
                current.forEach {state->
                    assertTrue(state.getBoolean("on"))
                    assertEquals(174,state.getInt("sat"))
                    if(brightness=="2")assertEquals(254,state.getInt("bri")) else assertTrue(state.getInt("bri") in 0..99)
                }
                if(color=="2")assertEquals(1,current.map {it.getInt("hue")}.distinct().size)
            }
        } finally {
            originals.forEach {(id,old)->
                val restore=JSONObject().put("on",old.getBoolean("on")).put("bri",old.getInt("bri")).put("transitiontime",0)
                when(old.optString("colormode")) {
                    "xy" -> restore.put("xy",old.getJSONArray("xy"))
                    "ct" -> restore.put("ct",old.getInt("ct"))
                    else -> restore.put("hue",old.getInt("hue")).put("sat",old.getInt("sat"))
                }
                if(old.has("effect"))restore.put("effect",old.getString("effect"))
                val result=JSONArray(Network.request("$base/lights/$id/state","PUT",restore.toString()))
                for(i in 0 until result.length())check(!result.getJSONObject(i).has("error")) {"Could not restore a Hue light"}
            }
        }
        android.util.Log.i("TurnTouchTest","Hue Random live color/saturation/brightness read-back passed; restored ${ids.size} lights")
    }
}
