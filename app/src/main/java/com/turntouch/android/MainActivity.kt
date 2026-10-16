@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.turntouch.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import java.net.URI

val Ink=Color(0xff404a60)
val Blue=Color(0xff007aff)
val SelectedStroke=Color(0xff303aa0)
val Effra=FontFamily(Font(R.font.effra),Font(R.font.effra_light,FontWeight.Light),Font(R.font.effra_bold,FontWeight.Bold))
class MainActivity: ComponentActivity() {
    private val state get()=(application as TurnTouchApp).state
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> if(state.onboarding || state.screen=="pairing") { if(result.values.all { it })state.bluetooth.beginPairing() else state.bluetooth.pairingUnavailable("Allow Nearby devices in Settings to connect a remote.") } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); openRequestedSetup(intent) }
    private fun openRequestedSetup(intent: Intent) { if(BuildConfig.DEBUG)intent.getStringExtra("setup")?.takeIf { id -> state.catalog.modes.any { it.id==id } }?.let { state.finishOnboarding(); state.screen="connect:$it" } }
    fun scan() {
        val needed=if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if(needed.all { checkSelfPermission(it)==PackageManager.PERMISSION_GRANTED })state.bluetooth.beginPairing() else permissions.launch(needed)
    }
    override fun onStart() { super.onStart(); state.bluetooth.reconnect() }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); openRequestedSetup(intent); enableEdgeToEdge(); setContent {
        MaterialTheme(colorScheme=lightColorScheme(primary=Blue,onPrimary=Color.White,background=Color.White,surface=Color.White,onSurface=Ink),typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontFamily=Effra,fontSize=17.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontFamily=Effra,fontSize=15.sp),labelLarge=androidx.compose.ui.text.TextStyle(fontFamily=Effra,fontSize=15.sp),titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=Effra,fontSize=24.sp))) { Surface(Modifier.fillMaxSize()) { App(state,::scan) } }
    } }
}
@Composable fun Artwork(name: String,modifier: Modifier=Modifier,size: Dp=32.dp,description: String?=null,tint: Color?=null) {
    val context=LocalContext.current
    val id=remember(name) { context.resources.getIdentifier(if(name=="settings")"settings_sharp" else name,"drawable",context.packageName) }
    if(id!=0)Image(painterResource(id),description,modifier.size(size),colorFilter=tint?.let {ColorFilter.tint(it)}) else Box(modifier.size(size))
}
@Composable fun App(app: AppState,scan: ()->Unit) {
    var settingsOpen by remember { mutableStateOf(false) }
    var inspected by remember { mutableStateOf<Direction?>(null) }
    var chooseMode by remember { mutableStateOf(false) }
    var connection by remember { mutableStateOf<String?>(null) }
    var nativeBinding by remember { mutableStateOf<Binding?>(null) }
    val nativeCommands=remember { kotlinx.coroutines.flow.MutableSharedFlow<Binding>(extraBufferCapacity=16) }
    DisposableEffect(app) { app.executor.nativeAction={ b -> nativeBinding=b; app.scope.launch { nativeCommands.emit(b) } }; onDispose { app.executor.nativeAction=null } }
    BackHandler(enabled=app.screen!="remote" || inspected!=null || chooseMode || nativeBinding!=null) { app.screen="remote"; inspected=null; chooseMode=false; nativeBinding=null }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp).background(Brush.verticalGradient(listOf(Color.White,Color(0xffe7e7e7))))) {
            Artwork("title",Modifier.align(Alignment.Center).width(100.dp).height(12.dp),100.dp)
            Box(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick={settingsOpen=!settingsOpen}) { Artwork("settings",size=22.dp,description="Settings") }
                if(settingsOpen)SettingsPopover(app,onDismiss={settingsOpen=false},
                    onNavigate={settingsOpen=false;app.screen=it},
                    onConnection={settingsOpen=false;connection=it})
            }
        }
        HorizontalDivider(color=Color(0xffe7e7e7))
        when {
            app.screen.startsWith("connect:") -> ConnectionScreen(app,app.screen.substringAfter(":")) { app.screen="remote" }
            app.screen=="devices" -> Devices(app,scan)
            app.screen=="privacy" -> Column(Modifier.fillMaxSize()) {
                TextButton(onClick={app.screen="remote"}) { Text("Done") }
                val context=LocalContext.current
                val policy=remember { context.assets.open("privacy.txt").bufferedReader().use { it.readText() } }
                Text(policy,Modifier.verticalScroll(rememberScrollState()).padding(24.dp),fontSize=16.sp)
            }
            app.screen=="diagnostics" -> Column { TextButton(onClick={app.screen="remote";settingsOpen=true}) { Text("Back") }; LazyColumn { items(app.events.toList()) { Text(it,Modifier.padding(12.dp),fontSize=13.sp) } } }
            nativeBinding!=null -> NativeScreen(app,nativeBinding!!,nativeCommands) { nativeBinding=null }
            else -> {
                Row(Modifier.fillMaxWidth().height(92.dp)) {
                    repeat(if(app.twelve)3 else 4) { i -> val selected=app.selected==i
                        Column(Modifier.weight(1f).fillMaxHeight().background(if(selected)Brush.verticalGradient(listOf(Color.White,Color.White)) else Brush.verticalGradient(listOf(Color.White,Color(0xffe7e7e7)))).combinedClickable(onClick={app.select(i);inspected=null},onLongClick={app.select(i);chooseMode=true}),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            MiniDiamond(i,selected)
                            Spacer(Modifier.height(13.dp))
                            Text(if(app.twelve)listOf("SINGLE TAP","DOUBLE TAP","HOLD")[i] else app.mode(i).title.uppercase(),fontFamily=Effra,fontSize=13.sp,color=if(selected)Ink else Color(0xff808388))
                        }
                    }
                }
                HorizontalDivider(color=Color(0xffe7e7e7))
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=32.dp),verticalAlignment=Alignment.CenterVertically) {
                    Artwork(app.mode().icon)
                    Text(if(app.twelve)"${listOf("Single tap","Double tap","Hold")[app.selected]} actions" else app.mode().subtitle.ifBlank { app.mode().title },Modifier.weight(1f).padding(start=12.dp),fontSize=13.sp)
                    TextButton(onClick={chooseMode=!chooseMode}) { Text(if(chooseMode)"Done" else "Change",fontSize=13.sp) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if(chooseMode) ModePicker(app) { id->app.assignMode(id);chooseMode=false }
                    else Column(Modifier.fillMaxSize().background(Color(0xfff5f6f8)).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val h=if(maxWidth>600.dp)420.dp else 270.dp
                            RemoteDiamond(app,inspected,Modifier.fillMaxWidth().height(h)) { direction,gesture -> if(app.perform)app.dispatch(direction,gesture) else inspected=if(inspected==direction)null else direction }
                        }
                        if(inspected!=null) ActionEditor(app,inspected!!)
                        else {
                            Text(if(app.perform)"Tap a button to perform its action" else "Tap a button to change its action",fontSize=14.sp,color=Color(0xff808388))
                            TextButton(onClick={connection=app.mode().id}) { Text("${app.mode().title} options") }
                        }

                    }
                }
                RemoteStatusBar(app)
            }
        }
    }
    if(app.onboarding || app.screen=="pairing" || app.screen=="tutorial") RemotePairingDialog(app,scan,app.screen=="tutorial") { app.finishOnboarding(); app.screen="remote" }
    connection?.let { id -> LaunchedEffect(id) { app.screen="connect:$id";connection=null } }
}
@Composable fun MiniDiamond(selected: Int,active: Boolean) {
    Canvas(Modifier.size(32.dp,25.dp)) { val w=size.width;val h=size.height
        diamondPoints(w,h,0.6f).forEachIndexed { i,points -> drawPath(polygon(points),if(i==selected)if(active)Color(0xff4376d7) else Color(0xff707073) else Color(0xffdedee0)) }
    }
}
fun polygon(points: List<Offset>)=Path().apply { moveTo(points[0].x,points[0].y);points.drop(1).forEach { lineTo(it.x,it.y) }; close() }
fun diamondPoints(w: Float,h: Float,s: Float): List<List<Offset>> = listOf(
    listOf(Offset(w/2,0f),Offset(w*3/4-1.3f*s,h/4-s),Offset(w/2,h/2-2*s),Offset(w/4+1.3f*s,h/4-s)),
    listOf(Offset(w,h/2),Offset(w*3/4+1.3f*s,h*3/4-s),Offset(w/2+2.6f*s,h/2),Offset(w*3/4+1.3f*s,h/4+s)),
    listOf(Offset(0f,h/2),Offset(w/4-1.3f*s,h/4+s),Offset(w/2-2.6f*s,h/2),Offset(w/4-1.3f*s,h*3/4-s)),
    listOf(Offset(w/2,h),Offset(w/4+1.3f*s,h*3/4+s),Offset(w/2,h/2+2*s),Offset(w*3/4-1.3f*s,h*3/4+s))
)
@Composable fun RemoteDiamond(app: AppState,inspected: Direction?,modifier: Modifier,onPress: (Direction,Gesture)->Unit) {
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
        val width=(maxWidth*0.8f).coerceAtMost(420.dp)
        val height=maxHeight*0.8f
        val density=LocalDensity.current
        val points=with(density) { diamondPoints(width.toPx(),height.toPx(),height.toPx()*0.0175f) }
        Box(Modifier.size(width,height)) {
            Direction.entries.forEach { direction ->
                val vertices=points[direction.ordinal]
                val left=vertices.minOf {it.x};val top=vertices.minOf {it.y}
                val faceWidth=vertices.maxOf {it.x}-left;val faceHeight=vertices.maxOf {it.y}-top
                val localPoints=vertices.map {Offset((it.x-left)/faceWidth,(it.y-top)/faceHeight)}
                val shape=remember(localPoints) { object: Shape {
                    override fun createOutline(size: androidx.compose.ui.geometry.Size,layoutDirection: LayoutDirection,density: Density): Outline =
                        Outline.Generic(polygon(localPoints.map {Offset(it.x*size.width,it.y*size.height)}))
                } }
                val interaction=remember {MutableInteractionSource()}
                val pressed by interaction.collectIsPressedAsState()
                var touchPressed by remember {mutableStateOf(false)}
                val selected=direction==inspected
                val active=direction==app.active || pressed || touchPressed
                val binding=app.bindings(direction).firstOrNull()
                val action=binding?.let {app.catalog.action(it)}
                Box(
                    Modifier.offset(with(density){left.toDp()},with(density){top.toDp()})
                        .size(with(density){faceWidth.toDp()},with(density){faceHeight.toDp()})
                        .clip(shape).background(if(active)Color(0xffc3c7c9) else if(selected)Color.White else Color(0xfffafbfd))
                        .pointerInput(direction) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed=false)
                                touchPressed=true
                                try {waitForUpOrCancellation()} finally {touchPressed=false}
                            }
                        }
                        .combinedClickable(interactionSource=interaction,indication=null,
                            onClick={onPress(direction,Gesture.Single)},
                            onDoubleClick={onPress(direction,Gesture.Double)},onLongClick={onPress(direction,Gesture.Hold)})
                        .semantics {contentDescription="${direction.name} button"}
                        .drawWithContent {
                            drawContent()
                            drawPath(polygon(localPoints.map {Offset(it.x*size.width,it.y*size.height)}),
                                if(selected)SelectedStroke else Color(0xffd3d7d9),style=Stroke(if(selected)3.dp.toPx() else 1.dp.toPx()))
                        },contentAlignment=Alignment.Center
                ) {
                    Column(Modifier.fillMaxWidth(if(app.twelve)0.55f else 0.66f),horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.Center) {
                        if(app.twelve)Artwork(binding?.let {app.catalog.mode(it.mode).icon} ?: "add",size=16.dp)
                        Text(action?.title ?: "Add action",Modifier.fillMaxWidth().padding(top=if(app.twelve)3.dp else 0.dp),
                            style=androidx.compose.ui.text.TextStyle(fontFamily=Effra,color=Ink,fontSize=13.sp,lineHeight=15.sp,
                                platformStyle=PlatformTextStyle(includeFontPadding=false)),
                            textAlign=TextAlign.Center,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
@Composable fun ModePicker(app: AppState,onChoose: (String)->Unit) {
    LazyColumn(Modifier.fillMaxWidth()) { items(app.catalog.modes) { mode -> Row(Modifier.fillMaxWidth().clickable { onChoose(mode.id) }.padding(horizontal=32.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically) { Artwork(mode.icon); Column(Modifier.padding(start=16.dp)) { Text(mode.title,fontSize=18.sp); Text(mode.subtitle,fontSize=13.sp,color=Color.Gray) } }; HorizontalDivider(color=Color(0xffeeeeee)) } }
}
@Composable fun ActionEditor(app: AppState,direction: Direction) {
    var gesture by remember(direction) { mutableStateOf(Gesture.Single) }
    var choosing by remember(direction) { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var changingIndex by remember { mutableIntStateOf(0) }
    var choiceMode by remember { mutableStateOf<String?>(null) }
    val singleBatch=app.bindings(direction,Gesture.Single)
    val doubleBatch=app.bindings(direction,Gesture.Double)
    val pairedRandom=!app.twelve && singleBatch.firstOrNull()?.action=="TTModeHueRandom" && doubleBatch.firstOrNull()?.action=="TTModeHueRandom"
    val editingGesture=if(pairedRandom)Gesture.Single else gesture
    val batch=app.bindings(direction,editingGesture)
    Column(Modifier.widthIn(max=700.dp).fillMaxWidth().padding(horizontal=24.dp)) {
        if(!app.twelve && !pairedRandom)Row { listOf(Gesture.Single,Gesture.Double).forEach { g->TextButton(onClick={gesture=g;choosing=false},Modifier.weight(1f)) { Text(if(g==Gesture.Single)"Single tap" else "Double tap",color=if(gesture==g)Blue else Color.Gray) } } }
        batch.forEachIndexed { i,b ->
            key(direction,gesture,i,b.mode,b.action) {
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Artwork(app.catalog.action(b).icon)
                    Column(Modifier.weight(1f).padding(start=12.dp)) { Text(app.catalog.action(b).title); Text(app.catalog.mode(b.mode).title,fontSize=12.sp,color=Color.Gray) }
                    TextButton(onClick={val same=choosing && !adding && changingIndex==i;adding=false;changingIndex=i;choosing=!same;choiceMode=b.mode},Modifier.semantics {contentDescription="Change action ${i+1}"}) {Text("Change",fontSize=13.sp)}
                    if(i>0)IconButton(onClick={app.save(direction,editingGesture,batch.filterIndexed { n,_->n!=i })}) { Artwork("button_x",size=14.dp,description="Remove action ${i+1}") }
                }
                if(choosing && !adding && changingIndex==i) ActionChooser(app,choiceMode,{choiceMode=it},{choosing=false}) { selected ->
                    val current=app.bindings(direction,editingGesture)
                    app.save(direction,editingGesture,current.mapIndexed {n,item->if(n==i)selected else item});choosing=false
                } else BindingOptions(app,b,
                    doubleBinding=if(pairedRandom)doubleBatch.getOrNull(i)?.takeIf {it.action=="TTModeHueRandom"} else null,
                    onDoubleChange={updated ->app.save(direction,Gesture.Double,app.bindings(direction,Gesture.Double).mapIndexed {n,item->if(n==i)updated else item})}
                ) { updated ->
                    val current=app.bindings(direction,editingGesture)
                    app.save(direction,editingGesture,current.mapIndexed { n,item->if(n==i)updated else item })
                    if(pairedRandom && updated.options["group"]!=b.options["group"]) {
                        val group=updated.options["group"] ?: "0"
                        app.save(direction,Gesture.Double,app.bindings(direction,Gesture.Double).mapIndexed {n,item->if(n==i)item.copy(options=item.options+("group" to group)) else item})
                    }
                }
                HorizontalDivider(Modifier.padding(vertical=12.dp),color=Color(0xffd3d7d9))
            }
        }
        Box(Modifier.fillMaxWidth().background(Color.White).height(64.dp),contentAlignment=Alignment.Center) {
            HorizontalDivider(Modifier.align(Alignment.TopCenter),thickness=0.5.dp,color=Color(0xffc2cbce))
            TextButton(onClick={if(choosing && adding)choosing=false else {adding=true;choosing=true;choiceMode=null}}) {
                Artwork(if(choosing && adding)"button_x" else "button_plus",size=14.dp)
                Spacer(Modifier.width(8.dp))
                Text(if(choosing && adding)"Cancel" else "Add new action",fontFamily=Effra,fontSize=13.sp,color=Color(0xffa0a0a0))
            }
            HorizontalDivider(Modifier.align(Alignment.BottomCenter),thickness=0.5.dp,color=Color(0xffc2cbce))
        }
        if(choosing && adding) ActionChooser(app,choiceMode,{choiceMode=it},{choosing=false}) { selected ->
            app.save(direction,editingGesture,app.bindings(direction,editingGesture)+selected);choosing=false
        }
    }
}
@Composable private fun ActionChooser(app: AppState,mode: String?,onMode: (String?)->Unit,onCancel: ()->Unit,onChoose: (Binding)->Unit) {
    val reveal=remember {BringIntoViewRequester()}
    LaunchedEffect(mode) {withFrameNanos { };reveal.bringIntoView()}
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(reveal).background(Color.White).semantics {contentDescription="Action chooser"}) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(mode==null)"Choose app" else "Choose action",Modifier.weight(1f),fontSize=17.sp)
            if(mode!=null)TextButton(onClick={onMode(null)}) {Text("Apps",fontSize=13.sp)}
            IconButton(onClick=onCancel) {Artwork("button_x",size=14.dp,description="Close action chooser",tint=Ink)}
        }
        Column(Modifier.fillMaxWidth().heightIn(max=240.dp).verticalScroll(rememberScrollState())) {
            if(mode==null)app.catalog.modes.forEach {m->TextButton(onClick={onMode(m.id)},Modifier.fillMaxWidth()) {
                Artwork(m.icon,size=24.dp);Text(m.title,Modifier.weight(1f).padding(12.dp),textAlign=TextAlign.Start)
            }} else app.catalog.mode(mode).actions.forEach {a->TextButton(onClick={onChoose(Binding(mode,a.id))},Modifier.fillMaxWidth()) {
                Artwork(a.icon,size=24.dp);Text(a.title,Modifier.weight(1f).padding(12.dp),textAlign=TextAlign.Start)
            }}
        }
    }
}
@Composable fun Devices(app: AppState,scan: ()->Unit) {
    val ble=app.bluetooth
    var rename by remember { mutableStateOf<Remote?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) { Text("Your remotes",Modifier.weight(1f),fontSize=25.sp); TextButton(onClick={app.screen="remote"}) { Text("Done") } }
        Text("Press a button on your Turn Touch to wake it, then connect.",Modifier.padding(vertical=16.dp))
        Button(onClick={app.screen="pairing"}) { Text("Pair a new remote") }
        ble.error?.let { Text(it,Modifier.padding(vertical=12.dp),color=MaterialTheme.colorScheme.error) }
        ble.remotes.forEach { remote ->
            Column(Modifier.fillMaxWidth().padding(vertical=18.dp)) {
                Text(remote.name,fontSize=22.sp); Text(remote.state,fontSize=15.sp); Text("${remote.address} · ${remote.rssi} dBm${remote.battery?.let { " · $it% battery" } ?: ""}${remote.firmware?.let { " · Firmware $it" } ?: ""}",fontSize=12.sp,color=Color.Gray)
                if(remote.state.startsWith("Press")) { Row { Direction.entries.forEach { d->Text(if(remote.pairedButtons and (1 shl d.ordinal)!=0)"✓ ${d.name}  " else "○ ${d.name}  ",fontSize=13.sp) } } }
                remote.connectionError?.let { Text(it,fontSize=14.sp,color=MaterialTheme.colorScheme.error) }
                Row {
                    if(remote.state!="Connected" && !remote.state.startsWith("Press"))TextButton(onClick={if(ble.isSaved(remote.address))ble.retry(remote.address) else app.screen="pairing"}) { Text(if(remote.state=="Nearby")"Connect" else "Try again") }
                    if(remote.state=="Connected")TextButton(onClick={rename=remote}) { Text("Rename") }
                    if(ble.isSaved(remote.address))TextButton(onClick={ble.disconnect(remote.address,true)}) { Text("Forget") }
                }
                HorizontalDivider()
            }
        }
        if(ble.remotes.isEmpty())Text("No remotes found yet. A remote connected to your Mac or iPhone may need to disconnect there first.",Modifier.padding(vertical=24.dp),color=Color.Gray)
    }
    rename?.let { remote -> var name by remember { mutableStateOf(remote.name) }; var error by remember { mutableStateOf("") }; AlertDialog(onDismissRequest={rename=null},title={Text("Name your remote")},text={Column { OutlinedTextField(name,{name=it},label={Text("Name")});Text(error,color=MaterialTheme.colorScheme.error) }},confirmButton={TextButton(onClick={runCatching { ble.rename(remote.address,name) }.onSuccess { rename=null }.onFailure { error=it.message.orEmpty() }}) { Text("Save") }},dismissButton={TextButton(onClick={rename=null}) { Text("Cancel") }}) }
}
fun connectionFields(mode: String): List<Pair<String,String>> = when(mode) {
    "Hue"->listOf("host" to "Bridge IP address","token" to "Bridge username","group" to "Group ID (0 = all lights)")
    "Nanoleaf"->listOf("host" to "Panel IP address","token" to "Authorization token")
    "Kasa"->listOf("host" to "Device IP address","protocol" to "Protocol (legacy or klap)","username" to "TP-Link email (KLAP)","password" to "TP-Link password (KLAP)")
    "Sonos","Bose","Wemo"->listOf("host" to "Device IP address (include port if needed)")
    "Govee"->listOf("host" to "Device IP address (LAN Control)","key" to "Govee API key","device" to "Device address","model" to "Model")
    "Ifttt"->listOf("key" to "Webhook key")
    "HomeKit","Nest"->emptyList()
    else->emptyList()
}
fun actionFields(mode: String): List<Pair<String,String>> = when(mode) {
    "Custom"->listOf("url" to "URL","method" to "HTTP method (GET or POST)","body" to "Request body")
    "Ifttt"->listOf("event" to "Event name","value1" to "Value 1","value2" to "Value 2","value3" to "Value 3")
    "Hue"->listOf("group" to "Group ID","scene" to "Scene ID","step" to "Brightness step","seconds" to "Fade duration (seconds)")
    "Nanoleaf"->listOf("scene" to "Effect name")
    "Web","News","Presentation","Video"->listOf("url" to "Page or media URL")
    "Yoga"->listOf("seconds" to "Pose duration (seconds)")
    else->listOf("volume" to "Jump volume (0–100)")
}
@Composable fun Fields(fields: List<Pair<String,String>>,values: MutableMap<String,String>) {
    fields.forEach { (key,label) -> OutlinedTextField(values[key] ?: "",{values[key]=it},Modifier.fillMaxWidth().padding(vertical=5.dp),label={Text(label,fontSize=13.sp)},singleLine=key!="body",visualTransformation=if(key in listOf("key","token","password"))PasswordVisualTransformation() else VisualTransformation.None) }
}
