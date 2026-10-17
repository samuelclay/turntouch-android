package com.turntouch.android

import androidx.compose.foundation.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class DeviceChoice(val id: String, val name: String, val group: String? = null)
data class LightingChoices(val rooms: List<DeviceChoice> = emptyList(), val scenes: List<DeviceChoice> = emptyList())

object LightingOptions {
    fun base(config: Map<String, String>, port: Int? = null): String {
        val host = config["host"]?.takeIf { it.isNotBlank() } ?: error(tr("Connect this app first."))
        val address = if (host.startsWith("http://") || host.startsWith("https://")) host else "http://$host"
        val uri = java.net.URI(address)
        return address.trimEnd('/') + if (port != null && uri.port == -1) ":$port" else ""
    }

    fun objectResponse(text: String): JSONObject {
        if (text.trimStart().startsWith("[")) {
            val result = JSONArray(text)
            for (i in 0 until result.length()) result.getJSONObject(i).optJSONObject("error")?.let {
                error(it.optString("description", tr("The device rejected the request.")))
            }
            error(tr("The device returned an unexpected response."))
        }
        return JSONObject(text)
    }

    suspend fun load(mode: String, config: Map<String, String>): LightingChoices = withContext(Dispatchers.IO) {
        val token = config["token"]?.takeIf { it.isNotBlank() } ?: error(tr("Connect {0} first.", mode))
        when (mode) {
            "Hue" -> {
                val base = "${base(config)}/api/$token"
                val groups = objectResponse(Network.request("$base/groups"))
                val scenes = objectResponse(Network.request("$base/scenes"))
                LightingChoices(
                    listOf(DeviceChoice("0", tr("All rooms"))) + groups.keys().asSequence().map { id ->
                        DeviceChoice(id, groups.getJSONObject(id).getString("name"))
                    }.sortedBy { it.name.lowercase() }.toList(),
                    scenes.keys().asSequence().map { id ->
                        val scene = scenes.getJSONObject(id)
                        DeviceChoice(id, scene.getString("name"), scene.optString("group").takeIf { it.isNotBlank() })
                    }.sortedBy { it.name.lowercase() }.toList()
                )
            }
            "Nanoleaf" -> {
                val response = Network.request("${base(config, 16021)}/api/v1/$token/effects/effectsList")
                val effects = JSONArray(response)
                LightingChoices(scenes = (0 until effects.length()).map { effects.getString(it) }.sortedBy { it.lowercase() }.map { DeviceChoice(it, it) })
            }
            else -> LightingChoices()
        }
    }
}

/** Options stay in the button inspector; selecting or typing saves immediately. */
@Composable fun BindingOptions(app: AppState, binding: Binding, doubleBinding: Binding?=null, onDoubleChange: (Binding)->Unit={}, onChange: (Binding) -> Unit) {
    fun update(key: String, value: String) = onChange(binding.copy(options = binding.options + (key to value)))
    if (binding.mode == "Hue" || binding.mode == "Nanoleaf") {
        LightingBindingOptions(app, binding, onChange)
        if(binding.action=="TTModeHueRandom")HueRandomOptions(binding,doubleBinding,onChange,onDoubleChange)
        return
    }
    val fields = when {
        binding.mode in listOf("Custom", "Ifttt", "Web", "News", "Presentation", "Video", "Yoga") -> actionFields(binding.mode)
        binding.action.endsWith("VolumeJump") -> listOf("volume" to tr("Volume (0–100)"))
        else -> emptyList()
    }
    fields.forEach { (key, label) ->
        OutlinedTextField(binding.options[key].orEmpty(), { update(key, it) }, Modifier.fillMaxWidth().padding(vertical = 5.dp), label = { Text(label) }, singleLine = key != "body")
    }
}

@Composable private fun LightingBindingOptions(app: AppState, binding: Binding, onChange: (Binding) -> Unit) {
    val config = app.config(binding.mode)
    var choices by remember(binding.mode) { mutableStateOf<LightingChoices?>(null) }
    var error by remember(binding.mode) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(binding.mode, config, refresh) {
        loading = true
        error = null
        try { choices = app.demo?.choices(binding.mode) ?: LightingOptions.load(binding.mode, config) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: tr("Could not load options.") }
        finally { loading = false }
    }
    fun update(key: String, value: String) = onChange(binding.copy(options = binding.options + (key to value)))
    Column(Modifier.fillMaxWidth().semantics { contentDescription = tr("{0} button options", binding.mode) }) {
        if (config["token"].isNullOrBlank()) {
            TextButton(onClick = { app.screen = "connect:${binding.mode}" }) { Text(tr("Connect to {0}", binding.mode)) }
        } else {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { refresh++ }) { Text(tr("Try again")) } }
            val action = binding.action.removePrefix("TTMode${binding.mode}")
            if (binding.mode == "Hue") {
                val group = binding.options["group"] ?: config["group"] ?: "0"
                InlineChoice("Room", choices?.rooms.orEmpty(), group, loading=loading, onRefresh={refresh++}) { room ->
                    onChange(binding.copy(options = binding.options.toMutableMap().apply { put("group", room); remove("scene"); remove("scenes") }))
                }
                val scenes = choices?.scenes.orEmpty().filter { group == "0" || it.group == null || it.group == group }
                val sceneAction = action == "CycleScenes" || (action.startsWith("Scene") && action !in listOf("SceneLightsOff", "SceneColorLoop"))
                if (sceneAction) {
                    if (action == "CycleScenes") {
                        val selected = binding.options["scenes"]?.split('|')?.filter { it.isNotBlank() }.orEmpty()
                        Text(tr("Scenes to cycle"), Modifier.padding(top = 12.dp))
                        Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            scenes.forEach { scene ->
                                val checked = scene.id in selected
                                Row(Modifier.fillMaxWidth().clickable { update("scenes", (if (checked) selected - scene.id else selected + scene.id).joinToString("|")) }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked, null); Text(scene.name, Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    } else InlineChoice("Scene", scenes, binding.options["scene"]) { update("scene", it) }
                }
                if (action == "Sleep") OutlinedTextField(binding.options["seconds"] ?: "60", { update("seconds", it) }, Modifier.fillMaxWidth(), label = { Text(tr("Fade duration (seconds)")) }, singleLine = true)
            } else {
                InlineChoice("Effect", choices?.scenes.orEmpty(), binding.options["scene"], loading=loading, onRefresh={refresh++}) { effect ->
                    onChange(binding.copy(action="TTModeNanoleafSceneCustom", options=binding.options + ("scene" to effect)))
                }
            }

        }
    }
}

/** The selector expands in place and scrolls into view on smaller screens. */
@Composable private fun InlineChoice(
    label: String, choices: List<DeviceChoice>, selected: String?, loading: Boolean=false,
    onRefresh: (() -> Unit)?=null, onSelect: (String) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    val bringIntoView=remember { BringIntoViewRequester() }
    LaunchedEffect(expanded) {
        if(expanded) { withFrameNanos { }; bringIntoView.bringIntoView() }
    }
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(tr(label),Modifier.widthIn(min=54.dp,max=110.dp),fontFamily=Effra,fontSize=13.sp,color=Ink)
            Row(
                Modifier.weight(1f).heightIn(min=44.dp).border(0.5.dp,Color(0xffc2cbce),RoundedCornerShape(5.dp))
                    .background(Color.White,RoundedCornerShape(5.dp)).clickable { expanded=!expanded }
                    .semantics { contentDescription=tr("{0} selector", tr(label)) }.padding(horizontal=12.dp,vertical=10.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                Text(choices.firstOrNull { it.id==selected }?.name ?: selected?.takeIf { it.isNotBlank() } ?: when(label) { "Effect"->tr("Choose effect"); "Room"->tr("Choose room"); else->tr("Choose scene") },Modifier.weight(1f),fontFamily=Effra,fontSize=15.sp,color=Ink)
                Text(if(expanded)"▴" else "▾",color=Ink)
            }
            if(onRefresh!=null) {
                IconButton(onClick=onRefresh,enabled=!loading,modifier=Modifier.semantics {contentDescription=if(label=="Room")tr("Refresh rooms and scenes") else tr("Refresh effects")}) {
                    if(loading)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)
                    else Artwork("refresh",size=18.dp,tint=Blue)
                }
            }
        }
        if(expanded) {
            Column(Modifier.fillMaxWidth().heightIn(max=216.dp).bringIntoViewRequester(bringIntoView)
                .background(Color.White).verticalScroll(rememberScrollState())) {
                if(choices.isEmpty())Text(if(loading)tr("Loading…") else when(label) { "Effect"->tr("No effects available."); "Room"->tr("No rooms available."); else->tr("No scenes available.") },Modifier.padding(12.dp))
                choices.forEach {choice ->
                    TextButton(onClick={onSelect(choice.id);expanded=false},modifier=Modifier.fillMaxWidth()) {
                        Text(choice.name,Modifier.weight(1f),fontFamily=Effra);if(choice.id==selected)Text("✓")
                    }
                }
            }
        }
    }
}

@Composable private fun HueRandomOptions(single: Binding,double: Binding?,onSingleChange: (Binding)->Unit,onDoubleChange: (Binding)->Unit) {
    val groups=listOf(
        Triple(tr("Color"),"randomColors",listOf(tr("Different"),tr("Mix"),tr("Same"))),
        Triple(tr("Saturation"),"randomSaturation",listOf(tr("Pastels"),tr("Mix"),tr("Strong"))),
        Triple(tr("Luminance"),"randomBrightness",listOf(tr("Dim"),tr("Mix"),tr("Bright")))
    )
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        groups.forEach { (label,key,choices) ->
            Text(label,Modifier.padding(top=12.dp,bottom=4.dp),fontFamily=Effra,fontSize=13.sp,color=Ink)
            RandomSegments(label,if(double==null)"" else "Single tap",key,choices,single,onSingleChange)
            if(double!=null)RandomSegments(label,"Double tap",key,choices,double,onDoubleChange)
        }
    }
}

@Composable private fun RandomSegments(label: String,tap: String,key: String,choices: List<String>,binding: Binding,onChange: (Binding)->Unit) {
    val selected=binding.options[key]?.toIntOrNull() ?: 2
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
        if(tap.isNotBlank())Artwork(if(tap=="Single tap")"single_tap" else "double_tap",Modifier.padding(end=8.dp),size=20.dp,description=tr(tap),tint=Ink)
        Row(Modifier.weight(1f).border(1.dp,Blue,RoundedCornerShape(5.dp)).clip(RoundedCornerShape(5.dp)).selectableGroup()) {
            choices.forEachIndexed {index,title ->
                Box(Modifier.weight(1f).heightIn(min=36.dp).background(if(index==selected)Blue else Color.White)
                    .selectable(selected=index==selected,role=androidx.compose.ui.semantics.Role.RadioButton) {
                        onChange(binding.copy(options=binding.options+(key to index.toString())))
                    }.semantics {contentDescription=tr("{0}: {1}: {2}", tr(tap), label, title)}.padding(horizontal=3.dp,vertical=8.dp),
                    contentAlignment=Alignment.Center) {
                    Text(title,fontFamily=Effra,fontSize=13.sp,color=if(index==selected)Color.White else Blue)
                }
                if(index<choices.lastIndex)Box(Modifier.width(1.dp).height(36.dp).background(Blue))
            }
        }
    }
}
