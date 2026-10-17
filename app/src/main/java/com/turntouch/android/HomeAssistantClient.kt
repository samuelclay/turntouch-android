package com.turntouch.android

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.*
import java.net.URI
import java.text.Normalizer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** The same single-entity binding and API contract as the Mac client. */
class HomeAssistantClient(context: Context, private val storageKey: String = "HomeAssistant") {
    private val secrets = SecretStore(context)
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).build()
    private val tokenLock = Mutex()
    private var credentials = JSONObject()
    private var generation = 0
    var server by mutableStateOf(""); private set
    var connected by mutableStateOf(false); private set
    var entities by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var services by mutableStateOf(JSONObject()); private set
    private var restored = false
    suspend fun restore() = tokenLock.withLock {
        if(!restored) {
            val saved=withContext(Dispatchers.IO) { secrets.get(storageKey) }
            credentials=runCatching { JSONObject(saved ?: "{}") }.getOrDefault(JSONObject())
            server=credentials.optString("server_url"); connected=credentials.optString("access_token").isNotEmpty(); restored=true
        }
    }
    private suspend fun request(url:String,token:String="",body:RequestBody?=null):String = withContext(Dispatchers.IO) {
        val request=Request.Builder().url(url).apply { if(token.isNotEmpty())header("Authorization","Bearer $token"); if(body!=null)post(body) }.build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { if(response.code==401)tr("Your Home Assistant session expired or the token is invalid. Connect again.") else tr("Device returned HTTP {0}",response.code) }
            response.body?.string() ?: "{}"
        }
    }
    private suspend fun save(value:JSONObject,url:String) {
        value.put("server_url",url)
        withContext(Dispatchers.IO) { secrets.put(storageKey,value.toString()) }
        if(server!=url) { entities=emptyList(); services=JSONObject() }
        credentials=value; server=url; connected=true; restored=true
    }
    suspend fun connect(url:String,token:String) {
        val home=normalize(url); require(token.isNotBlank()) { tr("Enter a Home Assistant address and access token.") }
        val version=++generation
        request("$home/api/",token.trim())
        tokenLock.withLock { check(version==generation) { tr("Connection cancelled.") }; save(JSONObject().put("access_token",token.trim()),home) }
        refresh()
    }
    suspend fun exchange(url:String,code:String) {
        val home=normalize(url); val version=++generation
        val result=JSONObject(request("$home/auth/token",body=FormBody.Builder().add("grant_type","authorization_code").add("client_id",CLIENT).add("code",code).build()))
        require(result.optString("access_token").isNotBlank() && result.optString("refresh_token").isNotBlank()) { tr("Invalid sign-in response.") }
        result.put("expires_at",System.currentTimeMillis()+(result.optLong("expires_in")-60)*1000)
        tokenLock.withLock { check(version==generation) { tr("Connection cancelled.") }; save(result,home) }
        refresh()
    }
    private suspend fun token():String {
        restore()
        return tokenLock.withLock {
            check(connected) { tr("Connect to Home Assistant first.") }
            if(credentials.has("refresh_token") && credentials.optLong("expires_at")<=System.currentTimeMillis()) {
                val version=generation
                val result=JSONObject(request("$server/auth/token",body=FormBody.Builder().add("grant_type","refresh_token").add("client_id",CLIENT).add("refresh_token",credentials.getString("refresh_token")).build()))
                check(version==generation) { tr("Connection changed.") }
                require(result.optString("access_token").isNotBlank()) { tr("Invalid sign-in response.") }
                save(JSONObject(credentials.toString()).put("access_token",result.getString("access_token")).put("expires_at",System.currentTimeMillis()+(result.optLong("expires_in")-60)*1000),server)
            }
            credentials.getString("access_token")
        }
    }
    suspend fun refresh() {
        val access=token(); val version=generation; val home=server
        val states=JSONArray(request("$home/api/states",access))
        val groups=JSONArray(request("$home/api/services",access)); val catalog=JSONObject()
        for(i in 0 until groups.length())groups.getJSONObject(i).let { catalog.put(it.getString("domain"),it.getJSONObject("services")) }
        check(version==generation) { tr("Connection changed.") }
        services=catalog; entities=(0 until states.length()).map { states.getJSONObject(it) }.sortedBy { name(it).lowercase() }
        // Registry access is optional; non-admin users retain the REST catalog.
        val registries=withTimeoutOrNull(6000) { registries(home,access) }
        if(version==generation && registries!=null) {
            val areas=registries[0].index("area_id"); val devices=registries[1].index("id"); val entries=registries[2].index("entity_id")
            entities=entities.map { entity ->
                val entry=entries[entity.optString("entity_id")]; val device=devices[entry?.optString("device_id")]
                val area=entry?.optString("area_id")?.takeIf { it!="null" && it.isNotBlank() } ?: device?.optString("area_id")
                JSONObject(entity.toString()).put("tt_area",areas[area]?.optString("name").orEmpty()).put("tt_device",device?.optString("name_by_user")?.takeIf { it!="null" && it.isNotBlank() } ?: device?.optString("name").orEmpty())
            }
        }
    }
    private fun JSONArray.index(key:String)=(0 until length()).map {getJSONObject(it)}.associateBy {it.optString(key)}
    private suspend fun registries(home:String,access:String):List<JSONArray>? = suspendCancellableCoroutine { continuation ->
        val commands=listOf("config/area_registry/list","config/device_registry/list","config/entity_registry/list")
        val results=mutableListOf<JSONArray>()
        val socket=http.newWebSocket(Request.Builder().url(home.replaceFirst("http","ws")+"/api/websocket").build(),object:WebSocketListener() {
            fun finish(socket:WebSocket,result:List<JSONArray>?) { if(continuation.isActive)continuation.resume(result); socket.close(1000,null) }
            override fun onMessage(socket:WebSocket,text:String) {
                runCatching {
                    val message=JSONObject(text)
                    when(message.optString("type")) {
                        "auth_required" -> socket.send(JSONObject().put("type","auth").put("access_token",access).toString())
                        "auth_ok" -> socket.send(JSONObject().put("id",1).put("type",commands[0]).toString())
                        "result" -> {
                            if(!message.optBoolean("success")) { finish(socket,null); return }
                            results.add(message.getJSONArray("result"))
                            if(results.size==3)finish(socket,results.toList()) else socket.send(JSONObject().put("id",results.size+1).put("type",commands[results.size]).toString())
                        }
                        "auth_invalid" -> finish(socket,null)
                    }
                }.onFailure { finish(socket,null) }
            }
            override fun onFailure(socket:WebSocket,t:Throwable,response:Response?) { finish(socket,null) }
            override fun onClosed(socket:WebSocket,code:Int,reason:String) { if(continuation.isActive)continuation.resume(null) }
        })
        continuation.invokeOnCancellation { socket.cancel() }
    }
    suspend fun call(binding:Map<String,String>) {
        val access=token()
        check(binding["server_url"]==server) { tr("Connect to Home Assistant first.") }
        val service=binding["service"].orEmpty()
        require(Regex("[a-z0-9_]+\\.[a-z0-9_]+").matches(service)) { tr("Choose a valid Home Assistant action.") }
        val body=body(binding["entity_id"].orEmpty(),binding["data"].orEmpty())
        if(service=="automation.trigger" && !body.has("skip_condition"))body.put("skip_condition",false)
        val parts=service.split('.'); val metadata=services.optJSONObject(parts[0])?.optJSONObject(parts[1])
        validate(body,metadata)
        val response=metadata?.optJSONObject("response")
        val suffix=if(response?.has("optional")==true && !response.optBoolean("optional"))"?return_response" else ""
        request("$server/api/services/${parts[0]}/${parts[1]}$suffix",access,body.toString().toRequestBody("application/json".toMediaType()))
    }
    suspend fun disconnect() { ++generation; tokenLock.withLock { withContext(Dispatchers.IO) { secrets.put(storageKey,"{}") };credentials=JSONObject();connected=false;entities=emptyList();services=JSONObject();restored=true } }
    companion object {
        const val CLIENT="https://turntouch.com"
        const val REDIRECT="https://turntouch.com/home-assistant/callback"
        fun normalize(input:String):String {
            val value=input.trim().let {if("://" in it)it else "http://$it"};val url=URI(value)
            require(url.scheme in listOf("http","https") && !url.host.isNullOrBlank() && url.userInfo==null && url.query==null && url.fragment==null && (url.port==-1 || url.port in 1..65535)) {tr("Enter an http:// or https:// address.")}
            return URI(url.scheme.lowercase(),null,url.host.lowercase(),url.port,url.path.trimEnd('/'),null,null).toString()
        }
        fun name(entity:JSONObject)=entity.optJSONObject("attributes")?.optString("friendly_name")?.takeIf { it.isNotBlank() } ?: entity.optString("entity_id")
        fun searchable(entity:JSONObject)=fold(listOf(name(entity),entity.optString("entity_id"),entity.optString("tt_area"),entity.optString("tt_device")).joinToString(" "))
        fun fold(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}"),"").lowercase()
        fun actions(entity:JSONObject,services:JSONObject):List<JSONObject> {
            val domain=entity.optString("entity_id").substringBefore('.');val available=services.optJSONObject(domain) ?: return emptyList()
            val preferred=listOf("toggle","turn_on","turn_off","press","trigger","open_cover","close_cover","stop_cover","media_play_pause","volume_up","volume_down")
            return (preferred+available.keys().asSequence().toList().sorted()).distinct().filter {available.has(it)}.mapNotNull {key ->
                val metadata=available.getJSONObject(key)
                if(!metadata.has("target") && metadata.optJSONObject("fields")?.has("entity_id")!=true && key !in preferred)return@mapNotNull null
                val name=when("$domain.$key") {"scene.turn_on"->tr("Activate scene");"script.turn_on"->tr("Run script");"automation.trigger"->tr("Run automation (check conditions)");else->metadata.optString("name").ifBlank {key.replace('_',' ')}}
                JSONObject().put("id","$domain.$key").put("name",name).put("metadata",metadata)
            }
        }
        fun body(entity:String,json:String):JSONObject {
            require(Regex("[a-z0-9_]+\\.[a-z0-9_]+").matches(entity)) {tr("Choose an entity first.")}
            val body=try {JSONObject(json.ifBlank {"{}"})} catch(e:JSONException) {error(tr("Action data must be a JSON object, for example {\"brightness_pct\": 50}."))}
            require(listOf("entity_id","device_id","area_id","floor_id","label_id","target").none {body.has(it)}) {tr("Choose the target in the entity list, not in action data.")}
            return body.put("entity_id",entity)
        }
        fun validate(body:JSONObject,metadata:JSONObject?) {
            val fields=metadata?.optJSONObject("fields") ?: return
            fields.keys().forEach {key ->val field=fields.optJSONObject(key);require(field?.optBoolean("required")!=true || body.has(key) || field.has("default")) {tr("Add the required action data field: ")+key} }
        }
    }
}
