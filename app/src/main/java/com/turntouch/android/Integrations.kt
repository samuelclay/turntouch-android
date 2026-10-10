package com.turntouch.android

import android.content.*
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.view.KeyEvent
import kotlinx.coroutines.*
import org.json.*
import java.net.*
import java.io.*

object Network {
    fun request(url: String,method: String="GET",body: String?=null,headers: Map<String,String> = emptyMap()): String {
        val uri=URI(url); require(uri.scheme in listOf("http","https")) { "Enter an http:// or https:// address." }
        val c=uri.toURL().openConnection() as HttpURLConnection
        c.connectTimeout=5000; c.readTimeout=7000; c.requestMethod=method; c.instanceFollowRedirects=false
        headers.forEach { (k,v)->c.setRequestProperty(k,v) }
        try {
            if(body!=null) { c.doOutput=true; if(headers.keys.none { it.equals("Content-Type",true) })c.setRequestProperty("Content-Type","application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
            val code=c.responseCode
            val stream=if(code in 200..299)c.inputStream else c.errorStream
            val text=stream?.bufferedReader()?.use { it.readText() } ?: ""
            check(code in 200..299) { "Device returned HTTP $code" }
            return text
        } finally { c.disconnect() }
    }
    fun xml(s: String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    fun tag(xml: String,name: String): String? = Regex("<(?:\\w+:)?$name(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?$name>",RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
    fun soap(host: String,path: String,service: String,action: String,parameters: Map<String,String>): String {
        val args=parameters.entries.joinToString("") { "<${it.key}>${xml(it.value)}</${it.key}>" }
        return request("$host$path","POST","""<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="$service">$args</u:$action></s:Body></s:Envelope>""",mapOf("Content-Type" to "text/xml; charset=utf-8","SOAPACTION" to "\"$service#$action\""))
    }
    fun kasaCrypt(bytes: ByteArray,decrypt: Boolean=false): ByteArray { var key=171; return bytes.map { b -> val input=b.toInt() and 255; val value=input xor key; key=if(decrypt) input else value; value.toByte() }.toByteArray() }
    fun kasa(host: String,command: String): JSONObject {
        Socket().use { s -> s.connect(InetSocketAddress(host,9999),4000); s.soTimeout=5000
            val payload=kasaCrypt(command.toByteArray()); DataOutputStream(s.getOutputStream()).apply { writeInt(payload.size); write(payload); flush() }
            val input=DataInputStream(s.getInputStream()); val size=input.readInt(); require(size in 1..1048576) { "Invalid Kasa response" }; val bytes=ByteArray(size); input.readFully(bytes); return JSONObject(kasaCrypt(bytes,true).toString(Charsets.UTF_8)) }
    }
}
class ActionExecutor(private val app: AppState) {
    private val context=app.context
    private val audio=context.getSystemService(AudioManager::class.java)
    private val klap=mutableMapOf<String,Klap>()
    private val sceneCycles=mutableMapOf<String,Int>()
    var nativeAction: ((Binding)->Unit)?=null
    suspend fun run(binding: Binding) {
        val title=app.catalog.action(binding).title
        try {
            val config=app.config(binding.mode)+binding.options
            val action=binding.action.removePrefix("TTMode${binding.mode}")
            when(binding.mode) {
                "Phone","Music","Spotify","Airfoil" -> media(action,config)
                "Camera","Yoga","Web","News","Presentation","Video" -> { val handler=nativeAction ?: error("Open Turn Touch to use ${binding.mode}."); handler(binding) }
                "AlarmClock" -> alarm(action)
                else -> withContext(Dispatchers.IO) { network(binding.mode,action,config) }
            }
            app.log(title)
        } catch(e: CancellationException) { throw e } catch(e: Exception) { app.log("$title: ${e.message ?: "Unable to complete action"}") }
    }
    private fun media(action: String,c: Map<String,String>) {
        when(action) {
            "VolumeUp" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI)
            "VolumeDown" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI)
            "VolumeMute","Mute" -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_TOGGLE_MUTE,AudioManager.FLAG_SHOW_UI)
            "VolumeJump" -> audio.setStreamVolume(AudioManager.STREAM_MUSIC,((c["volume"]?.toIntOrNull() ?: 50).coerceIn(0,100)*audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/100),AudioManager.FLAG_SHOW_UI)
            else -> {
                val code=when(action) { "PlayPause","PauseToggle"->KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE; "Play"->KeyEvent.KEYCODE_MEDIA_PLAY; "Pause"->KeyEvent.KEYCODE_MEDIA_PAUSE; "NextTrack"->KeyEvent.KEYCODE_MEDIA_NEXT; "PreviousTrack"->KeyEvent.KEYCODE_MEDIA_PREVIOUS; else->error("Choose a supported media action") }
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,code)); audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP,code))
            }
        }
    }
    private fun alarm(action: String) {
        val intent=Intent(when { action.endsWith("Snooze")->AlarmClock.ACTION_SNOOZE_ALARM; action.endsWith("Stop")->AlarmClock.ACTION_DISMISS_ALARM; else->AlarmClock.ACTION_SHOW_ALARMS }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    private fun required(c: Map<String,String>,key: String)=c[key]?.takeIf { it.isNotBlank() } ?: error("Set $key in connection options first.")
    private fun host(c: Map<String,String>,port: Int?=null): String { val value=required(c,"host").trim().trimEnd('/'); return if(value.startsWith("http"))value else "http://$value${port?.let { if(':' !in value)":$it" else "" } ?: ""}" }
    private fun network(mode: String,action: String,c: Map<String,String>) {
        when(mode) {
            "Custom" -> Network.request(required(c,"url"),c["method"] ?: "GET",c["body"]?.takeIf { it.isNotBlank() })
            "Ifttt" -> Network.request("https://maker.ifttt.com/trigger/${Uri.encode(required(c,"event"))}/with/key/${Uri.encode(required(c,"key"))}","POST",JSONObject().put("value1",c["value1"] ?: "").put("value2",c["value2"] ?: "").put("value3",c["value3"] ?: "").toString())
            "Hue" -> hue(action,c)
            "Nanoleaf" -> nanoleaf(action,c)
            "Sonos" -> sonos(action,c)
            "Wemo" -> wemo(action,c)
            "Kasa" -> {
                val hostname=required(c,"host").removePrefix("http://").substringBefore(':').substringBefore('/')
                if(c["protocol"]?.equals("klap",true)==true) {
                    val session=klap.getOrPut(hostname+":"+c["username"]+":"+c["password"].hashCode()) { Klap(hostname,required(c,"username"),required(c,"password")) }
                    val old=session.command(JSONObject().put("method","get_device_info")).getJSONObject("result").getBoolean("device_on")
                    session.command(JSONObject().put("method","set_device_info").put("params",JSONObject().put("device_on",if(action=="DeviceToggle")!old else action=="DeviceOn")))
                    return
                }
                val old=Network.kasa(hostname,"""{"system":{"get_sysinfo":{}}}""").getJSONObject("system").getJSONObject("get_sysinfo").getInt("relay_state")
                val on=if(action=="DeviceToggle")1-old else if(action=="DeviceOn")1 else 0
                val result=Network.kasa(hostname,"""{"system":{"set_relay_state":{"state":$on}}}""")
                check(result.getJSONObject("system").getJSONObject("set_relay_state").optInt("err_code",-1)==0) { "Kasa rejected the command" }
            }
            "Govee" -> govee(action,c)
            "Bose" -> bose(action,c)
            "HomeKit" -> error("Apple HomeKit is not available on Android. A home-scene bridge is required.")
            "Nest" -> error("Nest requires a Google Device Access project and OAuth setup.")
            else -> error("Connection not configured")
        }
    }
    private fun hue(action: String,c: Map<String,String>) {
        val base="${host(c)}/api/${required(c,"token")}"
        val group=c["group"]?.ifBlank { "0" } ?: "0"
        val body=JSONObject()
        val amount=(c["step"]?.toIntOrNull() ?: 25).coerceIn(1,254)
        when(action) {
            "RaiseBrightness","LowerBrightness" -> body.put("bri_inc",if(action.startsWith("Raise"))amount else -amount)
            "RaiseSaturation","LowerSaturation" -> body.put("sat_inc",if(action.startsWith("Raise"))amount else -amount)
            "ShiftColorLeft","ShiftColorRight" -> body.put("hue_inc",if(action.endsWith("Right"))5000 else -5000)
            "SceneLightsOff","Sleep" -> { body.put("on",false); if(action=="Sleep")body.put("transitiontime",(c["seconds"]?.toIntOrNull() ?: 60)*10) }
            "Random" -> {HueRandom.apply(base,group,HueRandomSettings.from(c));return}
            "SceneColorLoop" -> body.put("on",true).put("effect","colorloop")
            "CycleScenes" -> {
                val scenes=c["scenes"].orEmpty().split('|').filter { it.isNotBlank() }
                check(scenes.isNotEmpty()) { "Choose scenes in this button’s options first." }
                val key=base+":"+group+":"+scenes.joinToString("|")
                val index=sceneCycles[key] ?: 0
                body.put("scene",scenes[index % scenes.size])
            }
            "SceneCustom" -> body.put("scene",required(c,"scene"))
            else -> body.put("scene",required(c,"scene"))
        }
        val result=JSONArray(Network.request("$base/groups/$group/action","PUT",body.toString()))
        for(i in 0 until result.length())result.getJSONObject(i).optJSONObject("error")?.let { error(it.optString("description","Hue command failed")) }
        if(action=="CycleScenes") {
            val key=base+":"+group+":"+c["scenes"]
            sceneCycles[key]=(sceneCycles[key] ?: 0)+1
        }
    }
    private fun nanoleaf(action: String,c: Map<String,String>) {
        val base="${host(c,16021)}/api/v1/${required(c,"token")}"
        val body=JSONObject()
        when(action) {
            "SceneCustom" -> { Network.request("$base/effects","PUT",JSONObject().put("select",required(c,"scene")).toString()); return }
            "RaiseBrightness","LowerBrightness" -> body.put("brightness",JSONObject().put("increment",if(action.startsWith("Raise"))10 else -10))
            "Toggle" -> { val state=JSONObject(Network.request("$base/state")); body.put("on",JSONObject().put("value",!state.getJSONObject("on").getBoolean("value"))) }
            else -> body.put("on",JSONObject().put("value",action=="TurnOn"))
        }
        Network.request("$base/state","PUT",body.toString())
    }
    private fun sonos(action: String,c: Map<String,String>) {
        val base=host(c,1400)
        fun soap(service: String,name: String,args: Map<String,String> = emptyMap())=Network.soap(base,"/MediaRenderer/$service/Control","urn:schemas-upnp-org:service:$service:1",name,mapOf("InstanceID" to "0")+args)
        if(action.startsWith("Volume")) {
            val channel=mapOf("Channel" to "Master")
            if(action=="VolumeMute") { val old=Network.tag(soap("RenderingControl","GetMute",channel),"CurrentMute"); soap("RenderingControl","SetMute",channel+("DesiredMute" to if(old=="1")"0" else "1")); return }
            val old=Network.tag(soap("RenderingControl","GetVolume",channel),"CurrentVolume")?.toIntOrNull() ?: error("Speaker did not return volume")
            val volume=when(action) { "VolumeUp"->old+3; "VolumeDown"->old-3; else->c["volume"]?.toIntOrNull() ?: 50 }.coerceIn(0,100)
            soap("RenderingControl","SetVolume",channel+("DesiredVolume" to "$volume"))
        } else {
            val command=when(action) { "NextTrack"->"Next"; "PreviousTrack"->"Previous"; "PlayPause"->if(Network.tag(soap("AVTransport","GetTransportInfo"),"CurrentTransportState")=="PLAYING")"Pause" else "Play"; else->action }
            soap("AVTransport",command,if(command=="Play")mapOf("Speed" to "1") else emptyMap())
        }
    }
    private fun wemo(action: String,c: Map<String,String>) {
        val base=host(c,49153); val service="urn:Belkin:service:basicevent:1"; val path=c["path"]?.ifBlank { null } ?: "/upnp/control/basicevent1"
        val on=if(action=="DeviceToggle")Network.tag(Network.soap(base,path,service,"GetBinaryState",emptyMap()),"BinaryState")?.substringBefore('|')!="1" else action=="DeviceStart"
        Network.soap(base,path,service,"SetBinaryState",mapOf("BinaryState" to if(on)"1" else "0"))
    }
    private fun bose(action: String,c: Map<String,String>) {
        val base=host(c,8090)
        if(action.startsWith("Volume") && action!="VolumeMute") {
            val old=Network.tag(Network.request("$base/volume"),"actualvolume")?.toIntOrNull() ?: 0
            val volume=when(action) { "VolumeUp"->old+5; "VolumeDown"->old-5; else->c["volume"]?.toIntOrNull() ?: 50 }.coerceIn(0,100)
            Network.request("$base/volume","POST","<volume>$volume</volume>",mapOf("Content-Type" to "application/xml"))
        } else {
            val key=when(action) { "PlayPause"->"PLAY_PAUSE"; "NextTrack"->"NEXT_TRACK"; "PreviousTrack"->"PREV_TRACK"; "VolumeMute"->"MUTE"; else->action.uppercase() }
            for(state in listOf("press","release"))Network.request("$base/key","POST","<key state=\"$state\" sender=\"Gabbo\">$key</key>",mapOf("Content-Type" to "application/xml"))
        }
    }
    private fun govee(action: String,c: Map<String,String>) {
        c["host"]?.takeIf {it.isNotBlank()}?.let {GoveeLan.control(it.removePrefix("http://").substringBefore(':'),action);return}
        val headers=mapOf("Govee-API-Key" to required(c,"key"))
        val device=required(c,"device"); val model=required(c,"model")
        val state=JSONObject(Network.request("https://developer-api.govee.com/v1/devices/state?device=${Uri.encode(device)}&model=${Uri.encode(model)}",headers=headers)).getJSONObject("data").getJSONArray("properties")
        val props=mutableMapOf<String,String>(); for(i in 0 until state.length()) { val p=state.getJSONObject(i); p.keys().forEach { props[it]=p.get(it).toString() } }
        val command=if(action.startsWith("Brightness"))JSONObject().put("name","brightness").put("value",((props["brightness"]?.toIntOrNull() ?: 50)+if(action.endsWith("Up"))10 else -10).coerceIn(0,100)) else JSONObject().put("name","turn").put("value",if(action=="DeviceOn" || action=="DeviceToggle" && props["powerState"]!="on")"on" else "off")
        val result=JSONObject(Network.request("https://developer-api.govee.com/v1/devices/control","PUT",JSONObject().put("device",device).put("model",model).put("cmd",command).toString(),headers))
        check(result.optInt("code")==200) { result.optString("message","Govee rejected command") }
    }
    suspend fun pair(mode: String,c: Map<String,String>): Map<String,String> = withContext(Dispatchers.IO) {
        when(mode) {
            "Hue" -> { val result=JSONArray(Network.request("${host(c)}/api","POST","""{"devicetype":"turntouch#android"}""")); val item=result.getJSONObject(0); item.optJSONObject("error")?.let { error(it.optString("description")) }; c+("token" to item.getJSONObject("success").getString("username")) }
            "Nanoleaf" -> c+("token" to JSONObject(Network.request("${host(c,16021)}/api/v1/new","POST","{}")).getString("auth_token"))
            else -> error("Enter connection details for this app")
        }
    }
}
