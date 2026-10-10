package com.turntouch.android

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*

/** Direction order is the firmware's bit order, not compass order. */
enum class Direction { North, East, West, South }
enum class Gesture { Single, Double, Hold }
data class ActionDefinition(val id: String, val title: String, val icon: String)
data class ModeDefinition(val id: String, val title: String, val subtitle: String, val icon: String, val actions: List<ActionDefinition>, val defaults: List<String>, val origin: String)
data class Binding(val mode: String, val action: String, val options: Map<String, String> = emptyMap()) {
    fun json() = JSONObject().put("mode", mode).put("action", action).put("options", JSONObject(options))
    companion object { fun from(j: JSONObject): Binding { val o=j.optJSONObject("options") ?: JSONObject(); return Binding(j.getString("mode"), j.getString("action"), o.keys().asSequence().associateWith { o.getString(it) }) } }
}
class Catalog(context: Context) {
    val modes: List<ModeDefinition> = JSONArray(context.assets.open("catalog.json").bufferedReader().use { it.readText() }).let { a ->
        (0 until a.length()).map { i -> val m=a.getJSONObject(i); val actions=m.getJSONArray("actions"); val defaults=m.getJSONArray("defaults")
            ModeDefinition(m.getString("id"),m.getString("title"),m.getString("subtitle"),m.getString("icon"), (0 until actions.length()).map { n -> actions.getJSONObject(n).let { ActionDefinition(it.getString("id"),it.getString("title"),it.getString("icon")) } }, (0..3).map { defaults.getString(it) }, m.getString("origin")) }
    }
    fun mode(id: String) = modes.first { it.id == id }
    fun action(b: Binding): ActionDefinition {
        val definition=mode(b.mode).actions.firstOrNull { it.id==b.action } ?: ActionDefinition(b.action,"Choose an action",mode(b.mode).icon)
        return if(b.action=="TTModeNanoleafSceneCustom" && !b.options["scene"].isNullOrBlank())definition.copy(title=b.options.getValue("scene")) else definition
    }
}
class AppState(val context: Context) {
    val catalog=Catalog(context)
    private val prefs=context.getSharedPreferences("turntouch",Context.MODE_PRIVATE)
    private val secrets=SecretStore(context)
    var revision by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(prefs.getInt("selected",2)); private set
    var twelve by mutableStateOf(prefs.getBoolean("twelve",false)); private set
    var perform by mutableStateOf(prefs.getBoolean("perform",false)); private set
    var status by mutableStateOf("Ready")
    var active by mutableStateOf<Direction?>(null)
    var screen by mutableStateOf("remote")
    var onboarding by mutableStateOf(!prefs.getBoolean("onboarded",false))
    val events=mutableStateListOf<String>()
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    lateinit var bluetooth: BluetoothController
    lateinit var executor: ActionExecutor
    fun log(message: String) { scope.launch { status=message; events.add(0,message); if(events.size>100)events.removeAt(events.lastIndex); android.util.Log.i("TurnTouch",message) } }
    fun select(n: Int) { selected=n; prefs.edit().putInt("selected",n).apply() }
    fun changeButtonMode(value: Boolean) { twelve=value; select(0); prefs.edit().putBoolean("twelve",value).apply() }
    fun changePerformMode(value: Boolean) { perform=value; prefs.edit().putBoolean("perform",value).apply() }
    fun finishOnboarding() { onboarding=false; prefs.edit().putBoolean("onboarded",true).apply() }
    private fun key(slot: Int, direction: Direction, gesture: Gesture) = "${if(twelve)12 else 16}:$slot:${direction.name}:${gesture.name}"
    fun mode(slot: Int=selected): ModeDefinition { revision; return catalog.mode(prefs.getString("mode:$slot",listOf("Wemo","Music","Hue","Sonos")[slot.coerceIn(0,3)])!!) }
    fun assignMode(id: String) { if(twelve) { val m=catalog.mode(id); for(d in Direction.entries)save(d,Gesture.Single,listOf(Binding(id,m.defaults[d.ordinal]))); return }; prefs.edit().putString("mode:$selected",id).apply(); for(d in Direction.entries)for(g in Gesture.entries)prefs.edit().remove(key(selected,d,g)).apply(); revision++ }
    fun bindings(direction: Direction, gesture: Gesture=Gesture.Single, slot: Int=selected): List<Binding> {
        revision
        val saved=prefs.getString(key(slot,direction,gesture),null)
        fun withDefaults(bindings: List<Binding>)=bindings.map {binding ->
            if(binding.action=="TTModeHueRandom")binding.copy(options=HueRandomSettings.defaults(gesture==Gesture.Double)+binding.options) else binding
        }
        if(saved!=null) return withDefaults(runCatching { JSONArray(saved).let { a -> (0 until a.length()).map { Binding.from(a.getJSONObject(it)) } } }.getOrDefault(emptyList()))
        if(gesture==Gesture.Double && !twelve) {
            val single=bindings(direction,Gesture.Single,slot)
            if(single.firstOrNull()?.action=="TTModeHueRandom")return single.map {binding ->
                if(binding.action=="TTModeHueRandom")binding.copy(options=binding.options+HueRandomSettings.defaults(true)) else binding
            }
        }
        val m=if(twelve) catalog.mode(listOf("Music","Hue","Wemo")[slot.coerceIn(0,2)]) else mode(slot)
        val action=m.defaults[direction.ordinal]
        if(gesture==Gesture.Hold && !twelve) return emptyList()
        val doubleAction=if(gesture==Gesture.Double && !twelve) when { action.endsWith("PlayPause") -> "TTMode${m.id}PreviousTrack"; action.endsWith("NextTrack") -> "TTMode${m.id}PreviousTrack"; else -> action } else action
        return withDefaults(listOf(Binding(m.id,doubleAction)))
    }
    fun save(direction: Direction,gesture: Gesture,bindings: List<Binding>) { prefs.edit().putString(key(selected,direction,gesture),JSONArray(bindings.map { it.json() }).toString()).apply(); revision++ }
    fun config(mode: String): Map<String,String> { revision; val j=JSONObject(secrets.get(mode) ?: "{}"); return j.keys().asSequence().associateWith { j.getString(it) } }
    fun saveConfig(mode: String, values: Map<String,String>) { secrets.put(mode,JSONObject(values).toString()); revision++ }
    fun dispatch(direction: Direction,gesture: Gesture) {
        if(!twelve && gesture==Gesture.Hold) { select(direction.ordinal); log("Switched to ${mode().title}"); return }
        val slot=if(twelve)gesture.ordinal else selected
        val batch=bindings(direction,if(twelve)Gesture.Single else gesture,slot)
        scope.launch { active=direction; for(binding in batch) executor.run(binding); delay(180); active=null }
    }
}
class TurnTouchApp: Application() {
    lateinit var state: AppState
    override fun onCreate() { super.onCreate(); state=AppState(this); state.executor=ActionExecutor(state); state.bluetooth=BluetoothController(state) }
}
