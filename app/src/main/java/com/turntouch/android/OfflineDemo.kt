package com.turntouch.android

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.cancel

/** A separate, disposable session: no real assignments, credentials, or device commands. */
class DemoSession {
    val preferences: SharedPreferences=MemoryPreferences()
    val configs=mutableMapOf<String,Map<String,String>>()
    var lightOn by mutableStateOf(true)
    var brightness by mutableIntStateOf(70)
    var colorIndex by mutableIntStateOf(0)
    var playing by mutableStateOf(false)
    var volume by mutableIntStateOf(40)
    var result by mutableStateOf("Tap a remote button to try an action")
    val colors=listOf(Color(0xfff4c66a),Color(0xffa88cf5),Color(0xff61ccca),Color(0xffef8db3))
    init { listOf("Hue","Nanoleaf","Govee","Sonos").forEach { configs[it]=mapOf("token" to "offline-sample","group" to "0") } }
    fun choices(mode: String)=LightingChoices(
        rooms=if(mode=="Hue")listOf(DeviceChoice("0","All rooms"),DeviceChoice("1","Sample living room")) else emptyList(),
        scenes=listOf(DeviceChoice("relax","Relax"),DeviceChoice("focus","Focus"),DeviceChoice("sunset","Sunset")))
    fun run(binding: Binding,app: AppState) {
        val action=binding.action.removePrefix("TTMode${binding.mode}")
        when {
            binding.mode in listOf("Hue","Nanoleaf","Govee","Wemo","Kasa") -> when {
                action.contains("Toggle") -> lightOn=!lightOn
                action.endsWith("Off") || action=="Sleep" -> lightOn=false
                (action.contains("BrightnessUp") || action.contains("RaiseBrightness")) -> {lightOn=true;brightness=(brightness+10).coerceAtMost(100)}
                (action.contains("BrightnessDown") || action.contains("LowerBrightness")) -> brightness=(brightness-10).coerceAtLeast(0)
                action.endsWith("On") -> lightOn=true
                else -> {lightOn=true;colorIndex=(colorIndex+1)%colors.size}
            }
            action.contains("VolumeUp") -> volume=(volume+10).coerceAtMost(100)
            action.contains("VolumeDown") -> volume=(volume-10).coerceAtLeast(0)
            action.contains("Mute") -> volume=if(volume==0)40 else 0
            action.contains("Play") || action.contains("Pause") -> playing=!playing
        }
        result="Simulated: ${app.catalog.mode(binding.mode).title} · ${app.catalog.action(binding).title}"
        app.log(result)
    }
}

@Composable fun OfflineDemo(real: AppState) {
    val session=remember { DemoSession() }
    val app=remember { AppState(real.context,session).apply {
        executor=ActionExecutor(this);bluetooth=BluetoothController(this)
        finishOnboarding();changePerformMode(true)
        listOf("Hue","Nanoleaf","Govee","Sonos").forEachIndexed {i,id->select(i);assignMode(id)}
        select(0)
    } }
    fun exit() {real.screen="remote"}
    BackHandler {exit()}
    DisposableEffect(app) {onDispose {app.scope.cancel()}}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(Color(0xffe8f1ff)).statusBarsPadding().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical=8.dp)) {
                Text("Offline demo",color=Ink,fontSize=18.sp)
                Text("Sample devices · no real commands",color=Ink,fontSize=12.sp)
            }
            TextButton(onClick={exit()}) {Text("Exit demo")}
        }
        Box(Modifier.weight(1f)) {App(app,{})}
    }
}

@Composable fun DemoPreview(app: AppState,demo: DemoSession) {
    Column(Modifier.fillMaxWidth().background(Color(0xfff5f6f8)).padding(horizontal=16.dp,vertical=6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(if(demo.lightOn)demo.colors[demo.colorIndex] else Color(0xff78808c))
                .semantics {contentDescription="Sample light ${if(demo.lightOn)"on" else "off"}, color ${demo.colorIndex}, brightness ${demo.brightness} percent"})
            Text("Sample light: ${if(demo.lightOn)"${demo.brightness}%" else "off"}",Modifier.weight(1f).padding(start=8.dp),fontSize=13.sp)
            TextButton(onClick={app.changePerformMode(!app.perform)}) {Text(if(app.perform)"Edit buttons" else "Try buttons")}
        }
        Text(demo.result,fontSize=12.sp,color=Ink,maxLines=2)
        if(app.mode().id in listOf("Sonos","Music","Phone","Spotify"))Text("Sample speaker: ${if(demo.playing)"playing" else "paused"} · ${demo.volume}%",fontSize=12.sp)
    }
}

@Composable fun DemoConnectionScreen(app: AppState,mode: String,onDone: ()->Unit) {
    var paired by remember(mode) {mutableStateOf(false)}
    val title=if(mode=="remote")"Turn Touch" else app.catalog.mode(mode).title
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        TextButton(onClick=onDone,Modifier.align(Alignment.Start)) {Text("Back")}
        Artwork(if(mode=="remote")"remote_graphic" else app.catalog.mode(mode).icon,size=80.dp)
        Text("Sample $title",Modifier.padding(vertical=20.dp),fontSize=26.sp)
        Text("This is an offline preview. In normal mode, connect your own remote and supported devices. No account, Bluetooth, Wi-Fi, camera, or media access is used by demo actions.")
        Button(onClick={paired=true},Modifier.padding(top=24.dp)) {Text("Simulate connection")}
        if(paired)Text("Simulated connection ready",Modifier.padding(16.dp),color=Blue)
        TextButton(onClick=onDone) {Text("Done")}
    }
}

/** Implements the same preference contract using only session memory. */
internal class MemoryPreferences: SharedPreferences {
    private val values=mutableMapOf<String,Any>()
    override fun getAll(): Map<String,*> = values.toMap()
    override fun getString(key: String?,defValue: String?)=values[key] as? String ?: defValue
    override fun getStringSet(key: String?,defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues?.toMutableSet()
    override fun getInt(key: String?,defValue: Int)=values[key] as? Int ?: defValue
    override fun getLong(key: String?,defValue: Long)=values[key] as? Long ?: defValue
    override fun getFloat(key: String?,defValue: Float)=values[key] as? Float ?: defValue
    override fun getBoolean(key: String?,defValue: Boolean)=values[key] as? Boolean ?: defValue
    override fun contains(key: String?)=values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?)=Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?)=Unit
    override fun edit(): SharedPreferences.Editor=object: SharedPreferences.Editor {
        private val pending=mutableMapOf<String,Any?>()
        private var clear=false
        private fun put(key: String?,value: Any?)=apply {if(key!=null)pending[key]=value}
        override fun putString(key: String?,value: String?)=put(key,value)
        override fun putStringSet(key: String?,values: MutableSet<String>?)=put(key,values?.toSet())
        override fun putInt(key: String?,value: Int)=put(key,value)
        override fun putLong(key: String?,value: Long)=put(key,value)
        override fun putFloat(key: String?,value: Float)=put(key,value)
        override fun putBoolean(key: String?,value: Boolean)=put(key,value)
        override fun remove(key: String?)=put(key,null)
        override fun clear()=apply {clear=true}
        override fun commit(): Boolean {if(clear)values.clear();pending.forEach {(k,v)->if(v==null)values.remove(k) else values[k]=v};return true}
        override fun apply() {commit()}
    }
}
