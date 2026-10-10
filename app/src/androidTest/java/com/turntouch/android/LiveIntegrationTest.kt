package com.turntouch.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
@RunWith(AndroidJUnit4::class)
class LiveIntegrationTest {
 @Test fun hueAndSonosReadWriteRestore() {
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val file=File(context.filesDir,"test-connections.json")
    Assume.assumeTrue("Provision local test connections first",file.exists())
    val connections=JSONObject(file.readText());file.delete()
    val state=(context.applicationContext as TurnTouchApp).state
    for(mode in connections.keys()) {val c=connections.getJSONObject(mode);val values=c.keys().asSequence().associateWith {c.getString(it)};state.saveConfig(mode,values)}
    val hue=connections.getJSONObject("Hue");val base="http://${hue.getString("host")}/api/${hue.getString("token")}"
    val lights=JSONObject(Network.request("$base/lights"))
    val id=lights.keys().asSequence().firstOrNull {lights.getJSONObject(it).getJSONObject("state").optBoolean("on") && lights.getJSONObject(it).getJSONObject("state").optBoolean("reachable")}
    Assume.assumeNotNull(id)
    val original=lights.getJSONObject(id!!).getJSONObject("state").getInt("bri")
    try { val target=if(original<254)original+1 else original-1; Network.request("$base/lights/$id/state","PUT",JSONObject().put("bri",target).put("transitiontime",0).toString()); val actual=JSONObject(Network.request("$base/lights/$id")).getJSONObject("state").getInt("bri");Assert.assertEquals(target,actual) }
    finally {Network.request("$base/lights/$id/state","PUT",JSONObject().put("bri",original).put("transitiontime",0).toString())}
    android.util.Log.i("TurnTouchTest","Hue brightness write, read-back, and restore passed")
    val sonos="http://${connections.getJSONObject("Sonos").getString("host")}:1400"
    fun volume(action: String,args: Map<String,String>)=Network.soap(sonos,"/MediaRenderer/RenderingControl/Control","urn:schemas-upnp-org:service:RenderingControl:1",action,mapOf("InstanceID" to "0","Channel" to "Master")+args)
    val old=Network.tag(volume("GetVolume",emptyMap()),"CurrentVolume")!!.toInt()
    try {val target=if(old<100)old+1 else old-1;volume("SetVolume",mapOf("DesiredVolume" to "$target"));Assert.assertEquals("$target",Network.tag(volume("GetVolume",emptyMap()),"CurrentVolume"))}
    finally {volume("SetVolume",mapOf("DesiredVolume" to "$old"))}
    android.util.Log.i("TurnTouchTest","Sonos volume write, read-back, and restore passed")
 }
}
