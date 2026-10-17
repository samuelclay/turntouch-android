package com.turntouch.android

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** The original Apple app uses different/mix/same, pastel/mix/strong, dim/mix/bright. */
data class HueRandomSettings(val colors: Int, val saturation: Int, val brightness: Int) {
    companion object {
        fun defaults(doubleTap: Boolean=false) = mapOf(
            "randomColors" to if(doubleTap) "1" else "2",
            "randomSaturation" to "2",
            "randomBrightness" to if(doubleTap) "0" else "2"
        )
        fun from(options: Map<String,String>): HueRandomSettings {
            fun value(key: String) = options[key]?.toIntOrNull()?.takeIf {it in 0..2} ?: 2
            return HueRandomSettings(value("randomColors"),value("randomSaturation"),value("randomBrightness"))
        }
    }
}

data class HueRandomLightState(val hue: Int, val saturation: Int, val brightness: Int)

object HueRandom {
    fun generate(count: Int, settings: HueRandomSettings, random: Random=Random.Default): List<HueRandomLightState> {
        val first=random.nextInt(65535)
        val second=random.nextInt(65535)
        return List(count) {
            val hue=when(settings.colors) {2->first;1->if(random.nextBoolean())first else second;else->random.nextInt(65535)}
            // Preserve the Apple app's distributions, using the v1 API's maximum of 254.
            val brightness=when(settings.brightness) {0->random.nextInt(100);1->random.nextInt(255);else->254}
            val saturation=when(settings.saturation) {0->174;1->254-random.nextInt(80);else->254}
            HueRandomLightState(hue,saturation,brightness)
        }
    }

    fun apply(base: String, group: String, settings: HueRandomSettings,
              request: (String,String,String?)->String = {url,method,body->Network.request(url,method,body)}) {
        val lights=LightingOptions.objectResponse(request("$base/lights","GET",null))
        val ids=if(group in listOf("0","all",""))lights.keys().asSequence().toList() else {
            val members=LightingOptions.objectResponse(request("$base/groups/$group","GET",null)).getJSONArray("lights")
            (0 until members.length()).map {members.getString(it)}.filter {lights.has(it)}
        }
        check(ids.isNotEmpty()) {tr("No lights in the selected room.")}
        val states=generate(ids.size,settings)
        val failures=mutableListOf<String>()
        ids.zip(states).forEach { (id,state) ->
            val old=lights.getJSONObject(id).getJSONObject("state")
            val body=JSONObject().put("on",true)
            if(old.has("bri"))body.put("bri",state.brightness)
            if(old.has("hue") && old.has("sat"))body.put("hue",state.hue).put("sat",state.saturation)
            if(old.has("effect"))body.put("effect","none")
            // White-only bulbs still receive brightness without unsupported color fields.
            val result=JSONArray(request("$base/lights/$id/state","PUT",body.toString()))
            for(i in 0 until result.length())result.getJSONObject(i).optJSONObject("error")?.let {
                failures.add(it.optString("description",tr("Hue rejected a light change")))
            }
        }
        check(failures.isEmpty()) {failures.distinct().joinToString("; ")}
    }
}
