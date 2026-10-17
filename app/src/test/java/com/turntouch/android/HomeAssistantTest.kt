package com.turntouch.android
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
class HomeAssistantTest {
    @Test fun targetCannotBeOverriddenOrBroadened() {
        for(key in listOf("entity_id","device_id","area_id","floor_id","label_id","target")) {
            try {HomeAssistantClient.body("light.desk",JSONObject().put(key,"all").toString());fail(key)}catch(expected:IllegalArgumentException) {}
        }
        for(data in listOf("[]","null","false","invalid")) {try {HomeAssistantClient.body("light.desk",data);fail(data)}catch(expected:IllegalStateException) {}}
        assertEquals(42,HomeAssistantClient.body("light.desk","{\"brightness_pct\":42}").getInt("brightness_pct"))
    }
    @Test fun serviceCatalogHidesMaintenanceAndPreservesRequiredFields() {
        val services=JSONObject("""{"light":{"reload":{"fields":{}},"toggle":{"target":{}},"set_level":{"target":{},"fields":{"brightness_pct":{"required":true}}}}}""")
        val actions=HomeAssistantClient.actions(JSONObject().put("entity_id","light.desk"),services)
        assertEquals(listOf("light.toggle","light.set_level"),actions.map {it.getString("id")})
        try {HomeAssistantClient.validate(JSONObject(),actions[1].getJSONObject("metadata"));fail()}catch(expected:IllegalArgumentException) {}
        HomeAssistantClient.validate(JSONObject().put("brightness_pct",0),actions[1].getJSONObject("metadata"))
        assertTrue(HomeAssistantClient.actions(JSONObject().put("entity_id","sensor.weather"),services).isEmpty())
    }
    @Test fun urlAndSearchNormalization() {
        assertEquals("http://homeassistant.local:8123",HomeAssistantClient.normalize("homeassistant.local:8123/"))
        for(url in listOf("https://user:secret@home/","file:///tmp/x","https://home/?token=x","https://home/#x","https://home:99999")) {try {HomeAssistantClient.normalize(url);fail(url)}catch(expected:IllegalArgumentException) {}}
        assertTrue(HomeAssistantClient.searchable(JSONObject().put("entity_id","light.desk").put("tt_area","Büro")).contains("buro"))
    }
}
