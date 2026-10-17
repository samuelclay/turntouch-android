package com.turntouch.android

import android.net.nsd.*
import android.content.Intent
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

private val HABlue=Color(0xff387db3)
fun haCategories()=listOf("" to tr("Controllable entities"),"*" to tr("All entities"),"light" to tr("Lights"),"switch" to tr("Switches"),"scene" to tr("Scenes"),"script" to tr("Scripts"),"automation" to tr("Automations"),"cover" to tr("Covers"),"media_player" to tr("Media players"),"climate" to tr("Climate"),"other" to tr("Other"))

@Composable fun HomeAssistantOptions(app:AppState,binding:Binding?=null,destination:String="",onChange:((Binding)->Unit)?=null) {
    val focus=LocalFocusManager.current
    val client=app.homeAssistant;val scope=rememberCoroutineScope();val context=LocalContext.current
    var search by remember {mutableStateOf("")};var category by remember {mutableStateOf("")}
    var menu by remember {mutableStateOf(false)};var filters by remember {mutableStateOf(false)}
    var connection by remember {mutableStateOf(false)};var tokenEntry by remember {mutableStateOf(false)}
    var address by remember {mutableStateOf(client.server.ifBlank {"http://homeassistant.local:8123"})}
    var token by remember {mutableStateOf("")};var auth by remember {mutableStateOf<String?>(null)}
    var entityID by remember(binding?.action) {mutableStateOf(binding?.options?.get("entity_id").orEmpty())}
    var service by remember(binding?.action) {mutableStateOf(binding?.options?.get("service").orEmpty())}
    var data by remember(binding?.action) {mutableStateOf(binding?.options?.get("data") ?: "{}")}
    var settings by remember {mutableStateOf(false)};var advanced by remember {mutableStateOf(false)};var actionMenu by remember {mutableStateOf(false)}
    var busy by remember {mutableStateOf(false)};var message by remember {mutableStateOf("")};var failed by remember {mutableStateOf(false)}
    val discovered=remember {mutableStateListOf<String>()}
    fun work(block:suspend ()->Unit) { scope.launch {busy=true;message="";failed=false;try {block()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){message=e.message.orEmpty();failed=true}finally{busy=false}} }
    LaunchedEffect(client) {try {client.restore();if(client.server.isNotEmpty())address=client.server;if(client.connected)client.refresh()}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){message=e.message.orEmpty();failed=true} }
    DisposableEffect(connection) {
        val nsd=context.getSystemService(NsdManager::class.java)
        val listener=object:NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type:String) {}
            override fun onDiscoveryStopped(type:String) {}
            override fun onStartDiscoveryFailed(type:String,code:Int) {}
            override fun onStopDiscoveryFailed(type:String,code:Int) {}
            override fun onServiceLost(service:NsdServiceInfo) {}
            override fun onServiceFound(service:NsdServiceInfo) {
                @Suppress("DEPRECATION") nsd.resolveService(service,object:NsdManager.ResolveListener {
                    override fun onResolveFailed(service:NsdServiceInfo,code:Int) {}
                    override fun onServiceResolved(service:NsdServiceInfo) {
                        val host=service.host?.hostAddress ?: return
                        val url="http://${if(':' in host)"[$host]" else host}:${service.port}"
                        scope.launch {if(url !in discovered)discovered.add(url)}
                    }
                })
            }
        }
        if(connection)runCatching {nsd.discoverServices("_home-assistant._tcp.",NsdManager.PROTOCOL_DNS_SD,listener)}
        onDispose {if(connection)runCatching {nsd.stopServiceDiscovery(listener)}}
    }
    val categories=haCategories()
    val entity=client.entities.firstOrNull {it.optString("entity_id")==entityID}
    val actions=entity?.let {HomeAssistantClient.actions(it,client.services)} ?: emptyList()
    val action=actions.firstOrNull {it.optString("id")==service}
    fun selected():Map<String,String> {
        check(entity!=null && action!=null) {tr("Choose an entity first.")}
        val body=HomeAssistantClient.body(entityID,data)
        HomeAssistantClient.validate(body,action.optJSONObject("metadata"))
        return mapOf("entity_id" to entityID,"service" to service,"data" to data,"name" to HomeAssistantClient.name(entity),"server_url" to client.server)
    }
    val visible=client.entities.filter {item ->
        val domain=item.optString("entity_id").substringBefore('.')
        val matches=when(category){""->HomeAssistantClient.actions(item,client.services).isNotEmpty();"*"->true;"other"->categories.none {it.first==domain};else->domain==category}
        matches && HomeAssistantClient.searchable(item).contains(HomeAssistantClient.fold(search))
    }
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),placeholder={Text(tr("Search Home Assistant"))},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={focus.clearFocus()}),shape=RoundedCornerShape(8.dp))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                TextButton(onClick={filters=true}) {Text(categories.first {it.first==category}.second,color=Ink);Text(" ▾",color=Color.Gray)}
                DropdownMenu(filters,{filters=false}) {categories.forEach {(id,title)->DropdownMenuItem(text={Text(title)},onClick={category=id;filters=false})} }
            }
            Box {
                TextButton(onClick={menu=true}) {Text("⋯",fontSize=26.sp,modifier=Modifier.semanticsDescription(tr("Home Assistant menu")))}
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text(tr("Connection…"))},onClick={connection=!connection;menu=false})
                    DropdownMenuItem(text={Text(tr("Sign in…"))},enabled=!busy,onClick={connection=true;tokenEntry=false;menu=false})
                    DropdownMenuItem(text={Text(tr("Use access token…"))},onClick={connection=true;tokenEntry=true;menu=false})
                    DropdownMenuItem(text={Text(tr("Open token settings"))},onClick={menu=false;runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(HomeAssistantClient.normalize(address)+"/profile/security")))}})
                    HorizontalDivider()
                    DropdownMenuItem(text={Text(tr("Refresh"))},enabled=client.connected && !busy,onClick={menu=false;work {client.refresh()}})
                    DropdownMenuItem(text={Text(tr("Disconnect"))},enabled=client.connected,onClick={menu=false;work {client.disconnect();entityID="";service="";token=""}})
                }
            }
        }
        if(connection) {
            OutlinedTextField(address,{address=it;message=""},Modifier.fillMaxWidth(),label={Text(tr("Home Assistant address"))},singleLine=true)
            discovered.forEach {url->TextButton(onClick={address=url}) {Text(url)}}
            if(tokenEntry)OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text(tr("Access token"))},singleLine=true,visualTransformation=PasswordVisualTransformation())
            Button(onClick={if(tokenEntry)work {client.connect(address,token);token="";connection=false} else {runCatching {HomeAssistantClient.normalize(address)}.onSuccess {auth=it}.onFailure {message=it.message.orEmpty();failed=true}}},enabled=!busy,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=HABlue)) {Text(if(tokenEntry)tr("Connect") else tr("Sign in…"))}
        }
        Box(Modifier.testTag("ha-entities").fillMaxWidth().height(210.dp).border(0.5.dp,Color(0xffe1e5e9),RoundedCornerShape(8.dp))) {
            if(!client.connected)Button(onClick={connection=true;tokenEntry=false},enabled=!busy,modifier=Modifier.align(Alignment.Center).padding(horizontal=24.dp).fillMaxWidth().heightIn(min=56.dp).testTag("ha-sign-in"),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=HABlue)) {Text(tr("Sign in…"),fontSize=18.sp)}
            else if(visible.isEmpty())Text(tr("No matching entities"),Modifier.align(Alignment.Center).padding(24.dp),color=Color.Gray,textAlign=TextAlign.Center)
            if(client.connected)LazyColumn {items(visible,key={it.getString("entity_id")}) {item ->
                val id=item.getString("entity_id");val domain=id.substringBefore('.')
                Row(Modifier.fillMaxWidth().background(if(id==entityID)Color(0xffeaf2f8) else Color.White).clickable {
                    focus.clearFocus();entityID=id;service=HomeAssistantClient.actions(item,client.services).firstOrNull()?.optString("id").orEmpty();data="{}";message=""
                }.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                    Artwork(if(domain in listOf("light","switch","scene","script","automation","cover","media_player","climate"))"ha_$domain" else "mode_home",size=24.dp,tint=HABlue)
                    Column(Modifier.weight(1f).padding(start=10.dp)) {
                        Text(HomeAssistantClient.name(item),fontSize=14.sp,color=Ink)
                        Text(listOf(item.optString("tt_area"),categories.firstOrNull {it.first==domain}?.second ?: tr("Other")).filter {it.isNotBlank()}.joinToString(" · "),fontSize=12.sp,color=Color.Gray)
                    }
                    if(id==entityID)Text("✓",color=HABlue)
                };HorizontalDivider(thickness=0.5.dp,color=Color(0xffeef0f2))
            }}
        }
        if(entity!=null) {
            TextButton(onClick={settings=!settings},Modifier.fillMaxWidth()) {Text(tr("Action settings"),Modifier.weight(1f),color=Ink,textAlign=TextAlign.Start);Text(if(settings)"⌄" else "›",color=Color.Gray)}
            if(settings) {
                Text(tr("Choose what the remote button does in Home Assistant."),fontSize=12.sp,color=Color.Gray)
                Box {OutlinedButton(onClick={actionMenu=true},Modifier.fillMaxWidth(),shape=RoundedCornerShape(8.dp)) {Text(action?.optString("name") ?: tr("This entity has no callable actions. Sensors are usually read-only."),Modifier.weight(1f));Text(" ▾")};DropdownMenu(actionMenu,{actionMenu=false}) {actions.forEach {item->DropdownMenuItem(text={Text(item.optString("name"))},onClick={service=item.getString("id");message="";actionMenu=false})}} }
                TextButton(onClick={advanced=!advanced}) {Text(tr("Action data (optional JSON)"),color=Ink);Text(if(advanced)" ⌄" else " ›")}
                if(advanced) {
                    val metadata=action?.optJSONObject("metadata")
                    val fields=metadata?.optJSONObject("fields")
                    val required=fields?.keys()?.asSequence()?.filter {it!="entity_id" && fields.optJSONObject(it)?.optBoolean("required")==true}?.toList().orEmpty()
                    Text(metadata?.optString("description").orEmpty()+"\n"+if(required.isEmpty())tr("No required action data.") else tr("Required data: ")+required.joinToString(", "),fontSize=12.sp,color=Color.Gray)
                    OutlinedTextField(data,{data=it;message=""},Modifier.fillMaxWidth(),textStyle=LocalTextStyle.current.copy(fontFamily=FontFamily.Monospace,fontSize=13.sp),minLines=3)
                }
            }
        }
        Button(onClick={work {try {client.call(selected());message=tr("Home Assistant accepted the action.")}catch(e:Exception){settings=true;advanced=true;throw e}}},enabled=action!=null && client.connected && !busy,modifier=Modifier.fillMaxWidth().heightIn(min=44.dp),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xffeef1f4),contentColor=Ink)) {Text(tr("Test action"))}
        if(binding!=null && onChange!=null) {
            HorizontalDivider(Modifier.padding(vertical=4.dp),thickness=0.5.dp,color=Color(0xffe1e5e9))
            Text(destination,Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontSize=12.sp,color=Color.Gray)
            Button(onClick={try {val value=selected();onChange(binding.copy(options=value));failed=false;message="✓ "+tr("{0} now runs “{1}”.",destination,value["name"].orEmpty())}catch(e:Exception){settings=true;advanced=true;failed=true;message=e.message.orEmpty()}},enabled=action!=null && client.connected && !busy,modifier=Modifier.fillMaxWidth().heightIn(min=44.dp),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=HABlue)) {Text(tr("Set remote button"))}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=HABlue)
        if(message.isNotEmpty())Text(message,Modifier.fillMaxWidth(),fontSize=13.sp,color=if(failed)MaterialTheme.colorScheme.error else HABlue,textAlign=TextAlign.Center)
    }
    auth?.let {home ->HomeAssistantSignIn(home,onClose={auth=null}) {code->auth=null;work {client.exchange(home,code);connection=false}}}
}
private fun Modifier.semanticsDescription(value:String)=this.then(Modifier.semantics {contentDescription=value})

@Composable private fun HomeAssistantSignIn(home:String,onClose:()->Unit,onCode:(String)->Unit) {
    var completed by remember {mutableStateOf(false)}
    val state=remember {UUID.randomUUID().toString()};var web:WebView? by remember {mutableStateOf(null)}
    val callback=remember(home,state) {HomeAssistantClient.REDIRECT}
    DisposableEffect(Unit) {onDispose {web?.destroy()}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().background(Color.White)) {
            TextButton(onClick=onClose) {Text(tr("Cancel"))}
            AndroidView(factory={context->WebView(context).apply {
                web=this;settings.javaScriptEnabled=true;settings.domStorageEnabled=true
                settings.allowFileAccess=false;settings.allowContentAccess=false
                webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                        val uri=request.url
                        if(uri.scheme=="https" && uri.host=="turntouch.com" && uri.port==-1 && uri.path=="/home-assistant/callback" && uri.userInfo==null) {
                            if(!completed && uri.getQueryParameters("state").size==1 && uri.getQueryParameter("state")==state)uri.getQueryParameter("code")?.takeIf {it.isNotEmpty()}?.let {completed=true;onCode(it)}
                            return true
                        }
                        return uri.scheme !in listOf("http","https")
                    }
                }
                loadUrl(Uri.parse("$home/auth/authorize").buildUpon().appendQueryParameter("client_id",HomeAssistantClient.CLIENT).appendQueryParameter("redirect_uri",callback).appendQueryParameter("state",state).build().toString())
            }},modifier=Modifier.fillMaxSize())
        }
    }
}
