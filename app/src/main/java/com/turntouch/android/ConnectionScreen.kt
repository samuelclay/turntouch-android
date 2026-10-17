package com.turntouch.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.net.URI

@Composable fun ConnectionScreen(app: AppState,mode: String,onDone: ()->Unit) {
    if(app.demo!=null) { DemoConnectionScreen(app,mode,onDone); return }
    val values=remember(mode) {mutableStateMapOf<String,String>().apply {putAll(app.config(mode))}}
    var phase by remember(mode) {mutableStateOf(if(mode in listOf("Hue","Nanoleaf")) { if(values["token"].isNullOrBlank()) "discover" else "connected" } else "options")}
    var devices by remember(mode) {mutableStateOf<List<LanDevice>>(emptyList())}
    var busy by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf("")}
    var manual by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val title=app.catalog.mode(mode).title
    suspend fun discover() {
        busy=true;message=tr("Searching your Wi-Fi network…")
        runCatching {when(mode) {"Kasa"->LocalDiscovery.kasa();"Govee"->LocalDiscovery.govee(app.context);else->Discovery.ssdp(app.context)}}.onSuccess {found->
            devices=found.filter {d->when(mode) {"Hue"->d.server.contains("Hue",true)||d.name.contains("Hue",true);"Nanoleaf"->d.location.contains(":16021");"Sonos"->d.server.contains("Sonos",true);else->true}}
            message=if(devices.isEmpty())tr("No {0} devices found. Check that your device is powered on and connected to the same Wi-Fi network.", title) else ""
        }.onFailure {if(it is kotlinx.coroutines.CancellationException)throw it;message=tr("Could not search the network. {0}", it.message.orEmpty())}
        busy=false
    }
    LaunchedEffect(mode,phase) {if(phase=="discover")discover()}
    val lifecycleOwner=LocalLifecycleOwner.current
    LaunchedEffect(mode, phase, lifecycleOwner) {
        if(phase=="pair") lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            busy=true
            message=tr("Waiting for the button…")
            try {
                val config=awaitPairing(onFailure={ failure ->
                    message=if(failure.message?.contains("link button",true)==true || failure.message?.contains("HTTP 403")==true)
                        tr("Waiting for the button…") else tr("Waiting for {0}. Check that it’s on the same Wi-Fi network.", title)
                }) { app.executor.pair(mode,values.toMap()) }
                values.putAll(config);app.saveConfig(mode,config);phase="connected";message=""
            } finally {busy=false}
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {TextButton(onClick=onDone) {Text(tr("Back"))};Spacer(Modifier.weight(1f));Text(title,fontSize=20.sp);Spacer(Modifier.weight(1f));TextButton(onClick=onDone) {Text(tr("Done"))}}
        Column(Modifier.widthIn(max=540.dp).fillMaxWidth().padding(vertical=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Artwork(app.catalog.mode(mode).icon,size=64.dp)
            Spacer(Modifier.height(24.dp))
            when(phase) {
                "discover" -> {
                    Text(if(mode=="Hue")tr("Find your Hue bridge") else tr("Find your Nanoleaf"),fontSize=28.sp,fontFamily=Effra,fontWeight=FontWeight.Light,textAlign=TextAlign.Center)
                    Text(if(mode=="Hue")tr("Connect your Android device to the same Wi-Fi network, then select your bridge below.") else tr("Connect your Android device to the same Wi-Fi network, then select your light panels below."),Modifier.padding(vertical=20.dp),textAlign=TextAlign.Center)
                    devices.forEach {device->
                        OutlinedButton(onClick={values["host"]=URI(device.location).let {"${it.host}${if(it.port>0)":${it.port}" else ""}"};phase="pair";message=""},Modifier.fillMaxWidth().padding(vertical=6.dp)) {
                            Column(Modifier.padding(10.dp)) {Text(if(mode=="Nanoleaf")"Nanoleaf" else device.name,fontSize=18.sp);Text(URI(device.location).host,fontSize=13.sp)}
                        }
                    }
                    if(busy)CircularProgressIndicator(Modifier.padding(20.dp))
                    TextButton(onClick={scope.launch {discover()}},enabled=!busy) {Text(tr("Search again"))}
                    TextButton(onClick={manual=!manual}) {Text(tr("Enter an IP address"))}
                    if(manual) {Fields(listOf("host" to tr("IP address")),values);Button(onClick={phase="pair";message=""},enabled=!values["host"].isNullOrBlank()) {Text(tr("Continue"))}}
                }
                "pair" -> {
                    Text(if(mode=="Hue")tr("Press the link button") else tr("Hold the power button"),fontSize=28.sp,fontFamily=Effra,fontWeight=FontWeight.Light,textAlign=TextAlign.Center)
                    if(mode=="Hue")Artwork("hue_pushlink",Modifier.fillMaxWidth().height(180.dp).padding(vertical=16.dp),180.dp)
                    else Artwork("mode_nanoleaf",Modifier.padding(vertical=30.dp),100.dp)
                    Text(if(mode=="Hue")tr("Press the large round button on your Hue bridge. Turn Touch checks every 5 seconds and connects automatically.") else tr("Hold the power button on your Nanoleaf controller for 5–7 seconds, until its indicator starts flashing. Turn Touch checks every 5 seconds and connects automatically."),Modifier.padding(vertical=20.dp),fontSize=18.sp,textAlign=TextAlign.Center)
                    Text(values["host"].orEmpty(),fontSize=13.sp,color=Color.Gray)
                    CircularProgressIndicator(Modifier.padding(top=24.dp).size(24.dp))
                    TextButton(onClick={phase="discover";message=""}) {Text(tr("Choose another device"))}
                }
                "connected" -> {
                    Artwork("modal_success",size=80.dp)
                    Text(tr("Connected to {0}", title),Modifier.padding(vertical=20.dp),fontSize=26.sp)
                    Text(tr("Choose a button to assign your lights and scenes."),textAlign=TextAlign.Center)
                    Button(onClick=onDone,Modifier.padding(top=24.dp)) {Text(tr("Done"))}
                }
                else -> {
                    Text(tr("{0} options", title),fontSize=26.sp)
                    if(mode=="HomeKit")Text(tr("Google Home connection needs app authorization setup. Home Assistant will be a separate integration."),Modifier.padding(vertical=20.dp))
                    if(mode in listOf("Music","Spotify","Airfoil"))Text(tr("Start playback in your music app. Turn Touch controls Android’s active media session and volume."),Modifier.padding(vertical=20.dp))
                    if(mode=="Kasa")Text(tr("Choose legacy or KLAP. KLAP devices use the TP-Link account you used to set them up."),Modifier.padding(vertical=20.dp))
                    Fields(connectionFields(mode),values)
                    if(mode in listOf("Sonos","Bose","Wemo","Kasa","Govee")) {
                        TextButton(onClick={scope.launch {discover()}},enabled=!busy) {Text(tr("Find devices on Wi-Fi"))}
                        devices.forEach {device->TextButton(onClick={values["host"]=URI(device.location).let {"${it.host}${if(it.port>0)":${it.port}" else ""}"};if(mode=="Kasa")values["protocol"]=if(device.server.contains("KLAP"))"klap" else "legacy";message=tr("Selected {0}",device.name)}) {Text(device.name)}}
                    }
                    Button(onClick={app.saveConfig(mode,values.toMap());onDone()},Modifier.padding(top=20.dp)) {Text(tr("Save"))}
                }
            }
            if(message.isNotBlank())Text(message,Modifier.padding(vertical=18.dp),color=if(busy)Ink else MaterialTheme.colorScheme.error,textAlign=TextAlign.Center)
        }
    }
}
