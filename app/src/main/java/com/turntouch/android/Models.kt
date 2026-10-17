package com.turntouch.android

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.*

/** Direction order is the firmware's bit order, not compass order. */
enum class Direction { North, East, West, South;
    val displayName get()=when(this) { North->tr("North"); East->tr("East"); West->tr("West"); South->tr("South") }
}
enum class Gesture { Single, Double, Hold }
data class ActionDefinition(val id: String, private val sourceTitle: String, val icon: String, val translateTitle: Boolean=true) {
    val title get()=if(translateTitle)tr(sourceTitle) else sourceTitle
}
data class ModeDefinition(val id: String, private val sourceTitle: String, private val sourceSubtitle: String, val icon: String, val actions: List<ActionDefinition>, val defaults: List<String>, val origin: String) {
    val title get()=tr(sourceTitle)
    val subtitle get()=tr(sourceSubtitle)
}
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
        val definition=mode(b.mode).actions.firstOrNull { it.id==b.action } ?: ActionDefinition(b.action,tr("Choose an action"),mode(b.mode).icon)
        return if(b.action=="TTModeNanoleafSceneCustom" && !b.options["scene"].isNullOrBlank())definition.copy(sourceTitle=b.options.getValue("scene"),translateTitle=false) else definition
    }
}
class AppState(val context: Context, val demo: DemoSession? = null) {
    val catalog=Catalog(context)
    private val prefs=demo?.preferences ?: context.getSharedPreferences("turntouch",Context.MODE_PRIVATE)
    private val secrets by lazy { SecretStore(context) }
    var revision by mutableIntStateOf(0); private set
    var selected by mutableIntStateOf(prefs.getInt("selected",2)); private set
    var twelve by mutableStateOf(prefs.getBoolean("twelve",false)); private set
    var perform by mutableStateOf(prefs.getBoolean("perform",false)); private set
    var status by mutableStateOf(tr("Ready"))
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
    fun config(mode: String): Map<String,String> { revision; demo?.let { return it.configs[mode].orEmpty() }; val j=JSONObject(secrets.get(mode) ?: "{}"); return j.keys().asSequence().associateWith { j.getString(it) } }
    fun saveConfig(mode: String, values: Map<String,String>) { demo?.let { it.configs[mode]=values; revision++; return }; secrets.put(mode,JSONObject(values).toString()); revision++ }
    fun dispatch(direction: Direction,gesture: Gesture) {
        if(demo==null && screen=="demo")return // Physical input must not control the home while previewing.
        if(!twelve && gesture==Gesture.Hold) { select(direction.ordinal); log(tr("Switched to {0}", mode().title)); return }
        val slot=if(twelve)gesture.ordinal else selected
        val batch=bindings(direction,if(twelve)Gesture.Single else gesture,slot)
        // Physical press/release owns the HUD highlight; delayed action execution must not flash it again.
        scope.launch { for(binding in batch) executor.run(binding) }
    }
}
class TurnTouchApp: Application() {
    lateinit var state: AppState
    override fun onCreate() { super.onCreate(); Localization.initialize(this); state=AppState(this); state.executor=ActionExecutor(state); state.bluetooth=BluetoothController(state) }
}
