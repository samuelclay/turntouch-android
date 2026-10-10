package com.turntouch.android

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HueRandomTest {
    @Test fun colorsUseOneTwoOrIndependentHues() {
        val same=HueRandom.generate(100,HueRandomSettings(2,2,2),Random(1))
        val mix=HueRandom.generate(100,HueRandomSettings(1,2,2),Random(1))
        val different=HueRandom.generate(100,HueRandomSettings(0,2,2),Random(1))
        assertEquals(1,same.map {it.hue}.toSet().size)
        assertEquals(2,mix.map {it.hue}.toSet().size)
        assertTrue(different.map {it.hue}.toSet().size>90)
        assertNotEquals(same,HueRandom.generate(100,HueRandomSettings(2,2,2),Random(2)))
    }
    @Test fun everyCombinationUsesAppleRangesAndFreshValues() {
        for(color in 0..2)for(saturation in 0..2)for(brightness in 0..2) {
            val states=HueRandom.generate(200,HueRandomSettings(color,saturation,brightness),Random(42))
            states.forEach {state ->
                assertTrue(state.hue in 0..65534)
                assertTrue(state.brightness in when(brightness) {0->0..99;1->0..254;else->254..254})
                assertTrue(state.saturation in when(saturation) {0->174..174;1->175..254;else->254..254})
            }
            if(brightness!=2)assertTrue(states.map {it.brightness}.distinct().size>20)
            if(saturation==1)assertTrue(states.map {it.saturation}.distinct().size>20)
        }
    }
    @Test fun selectedRoomGetsIndividualLightCommandsAndWhiteBulbsOnlyGetBrightness() {
        val sent=mutableMapOf<String,JSONObject>()
        val lights="""{"1":{"state":{"bri":20,"hue":40,"sat":50,"effect":"none"}},"2":{"state":{"bri":40}},"3":{"state":{"bri":20,"hue":40,"sat":50}}}"""
        HueRandom.apply("http://bridge/api/test","room",HueRandomSettings(2,0,2)) {url,method,body ->
            when {
                url.endsWith("/lights") -> lights
                url.endsWith("/groups/room") -> """{"lights":["1","2"]}"""
                else -> {assertEquals("PUT",method);sent[url.substringAfter("/lights/").substringBefore('/')]=JSONObject(body!!);"""[{"success":{}}]"""}
            }
        }
        assertEquals(setOf("1","2"),sent.keys)
        assertEquals(174,sent.getValue("1").getInt("sat"))
        assertEquals(254,sent.getValue("1").getInt("bri"))
        assertEquals("none",sent.getValue("1").getString("effect"))
        assertFalse(sent.getValue("2").has("hue"))
        assertFalse(sent.getValue("2").has("sat"))
        assertTrue(sent.getValue("2").getBoolean("on"))
    }
    @Test fun allRoomsIncludesAllLightsAndBridgeErrorsAreReported() {
        var writes=0
        try {
            HueRandom.apply("http://bridge/api/test","0",HueRandomSettings(1,1,0)) {url,_,_ ->
                if(url.endsWith("/lights"))"""{"1":{"state":{"bri":10}},"2":{"state":{"bri":20}}}"""
                else {writes++;"""[{"error":{"description":"light is unreachable"}}]"""}
            }
            fail("Bridge errors must not report success")
        } catch(expected: IllegalStateException) {assertTrue(expected.message!!.contains("unreachable"))}
        assertEquals(2,writes)
    }
}
