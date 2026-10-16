package com.turntouch.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsMuted=Color(0xff808aa0)

/** Anchored to the title-bar gear; the action editor stays in place behind it. */
@Composable fun SettingsPopover(app: AppState,onDismiss: ()->Unit,onNavigate: (String)->Unit,onConnection: (String)->Unit) {
    val configuration=LocalConfiguration.current
    val width=(configuration.screenWidthDp-32).coerceAtMost(340).dp
    val height=(configuration.screenHeightDp*0.72f).coerceAtMost(600f).dp
    val offset=with(LocalDensity.current) { IntOffset(-12.dp.roundToPx(),44.dp.roundToPx()) }
    Popup(alignment=Alignment.TopEnd,offset=offset,onDismissRequest=onDismiss,
        properties=PopupProperties(focusable=true,dismissOnBackPress=true,dismissOnClickOutside=true)) {
        Column(Modifier.width(width).semantics { contentDescription="Settings popover" }) {
            Canvas(Modifier.align(Alignment.End).padding(end=4.dp).size(16.dp,8.dp)) {
                drawPath(Path().apply { moveTo(0f,size.height);lineTo(size.width/2,0f);lineTo(size.width,size.height);close() },Color.White)
            }
            Surface(shape=RoundedCornerShape(16.dp),color=Color.White,shadowElevation=10.dp,
                modifier=Modifier.fillMaxWidth().height(height)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start=20.dp,end=8.dp).height(48.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("Settings",Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold,color=Ink)
                        TextButton(onClick=onDismiss) { Text("Done") }
                    }
                    HorizontalDivider(color=Color(0xffe0e5e8),thickness=0.5.dp)
                    Settings(app,onNavigate,onConnection)
                }
            }
        }
    }
}

@Composable private fun Settings(app: AppState,onNavigate: (String)->Unit,onConnection: (String)->Unit) {
    var showAllApps by remember { mutableStateOf(false) }
    val remotes=if(app.demo!=null)emptyList() else app.bluetooth.remotes.filter { app.bluetooth.isSaved(it.address) }
    Column(Modifier.fillMaxSize().background(Color(0xfff5f6f8)).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=16.dp)) {
            SettingsSection("REMOTES") {
                remotes.forEach { remote ->
                    SettingsLink(remote.name,"remote_graphic",remote.state.removeSuffix("…")+
                        if(remote.state=="Connected" && remote.battery!=null)" · ${remote.battery}% battery" else "",onClick={onNavigate("devices")})
                    SettingsDivider()
                }
                SettingsLink("Add a new remote","add",if(remotes.isEmpty())"Pair your Turn Touch" else null,onClick={onNavigate("pairing")})
            }
            SettingsSection("BUTTONS") {
                Column(Modifier.padding(16.dp)) {
                    Text("Button mode",fontSize=17.sp,color=Ink)
                    Text(if(app.twelve)"Single tap, double tap, and hold" else "Hold a button to switch between four apps",Modifier.padding(top=4.dp,bottom=14.dp),fontSize=14.sp,color=SettingsMuted)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0xfff0f2f5)).padding(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                        listOf(true to "12 actions",false to "16 actions").forEach { (twelve,label) ->
                            val selected=app.twelve==twelve
                            TextButton(onClick={if(!selected)app.changeButtonMode(twelve)},modifier=Modifier.weight(1f),shape=RoundedCornerShape(7.dp),
                                colors=ButtonDefaults.textButtonColors(containerColor=if(selected)Color.White else Color.Transparent,contentColor=if(selected)Blue else Ink)) {
                                Text(label,fontSize=15.sp,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
                SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end=12.dp)) {
                        Text("Run actions from the screen",fontSize=17.sp,color=Ink)
                        Text(if(app.perform)"Screen taps perform actions" else "Screen taps edit your buttons",Modifier.padding(top=4.dp),fontSize=14.sp,color=SettingsMuted)
                    }
                    Switch(app.perform,{app.changePerformMode(it)},colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=Blue,
                        uncheckedThumbColor=Color.White,uncheckedTrackColor=Color(0xffd5dbe2),uncheckedBorderColor=Color.Transparent))
                }
            }
            SettingsSection("APPS & CONNECTIONS") {
                val favorites=listOf("Hue","Nanoleaf","Govee","Sonos","Kasa")
                val modes=if(showAllApps)app.catalog.modes.sortedBy { if(it.id in favorites)favorites.indexOf(it.id) else favorites.size+app.catalog.modes.indexOf(it) }
                    else favorites.mapNotNull { id->app.catalog.modes.firstOrNull { it.id==id } }
                modes.forEachIndexed { index,mode ->
                    if(index>0)SettingsDivider()
                    val detail=when {
                        mode.id in listOf("HomeKit","Nest") -> "Not available on Android"
                        app.config(mode.id).isNotEmpty() -> "Saved setup"
                        connectionFields(mode.id).isNotEmpty() -> "Set up connection"
                        else -> "App options"
                    }
                    SettingsLink(mode.title,mode.icon,detail,onClick={onConnection(mode.id)})
                }
                SettingsDivider()
                TextButton(onClick={showAllApps=!showAllApps},Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(if(showAllApps)"Show fewer apps" else "Show all apps",fontSize=15.sp) }
            }
            SettingsSection("HELP") {
                if(app.demo==null) {
                    SettingsLink("Try offline demo","remote_graphic","Explore without hardware",onClick={onNavigate("demo")})
                    SettingsDivider()
                }
                SettingsLink("How it works","how_it_works",onClick={onNavigate("tutorial")})
                SettingsDivider()
                SettingsLink("Connection diagnostics","electrical_connected",onClick={onNavigate("diagnostics")})
                SettingsDivider()
                SettingsLink("Privacy policy","how_it_works",onClick={onNavigate("privacy")})
            }
            Text("Turn Touch for Android · ${BuildConfig.VERSION_NAME}",Modifier.align(Alignment.CenterHorizontally).padding(top=4.dp),fontSize=13.sp,color=SettingsMuted)
            Text("Original design and artwork © Turn Touch",Modifier.align(Alignment.CenterHorizontally).padding(top=4.dp,bottom=12.dp),fontSize=12.sp,color=SettingsMuted)
        }
    }
}

@Composable private fun SettingsSection(title: String,content: @Composable ColumnScope.()->Unit) {
    Text(title,Modifier.padding(start=16.dp,bottom=8.dp),fontFamily=Effra,fontSize=12.sp,fontWeight=FontWeight.Bold,color=SettingsMuted,letterSpacing=1.sp)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White),content=content)
    Spacer(Modifier.height(24.dp))
}

@Composable private fun SettingsDivider() = HorizontalDivider(Modifier.padding(start=56.dp),thickness=0.5.dp,color=Color(0xffe0e5e8))

@Composable private fun SettingsLink(title: String,icon: String,subtitle: String?=null,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
        Artwork(icon,size=28.dp)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)) {
            Text(title,fontSize=17.sp,color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
            subtitle?.let {Text(it,Modifier.padding(top=3.dp),fontSize=13.sp,color=SettingsMuted)}
        }
        Canvas(Modifier.size(8.dp,14.dp)) {
            val stroke=1.5.dp.toPx()
            drawLine(Color(0xffa5afb7),androidx.compose.ui.geometry.Offset(1f,1f),androidx.compose.ui.geometry.Offset(size.width-1f,size.height/2),stroke)
            drawLine(Color(0xffa5afb7),androidx.compose.ui.geometry.Offset(size.width-1f,size.height/2),androidx.compose.ui.geometry.Offset(1f,size.height-1f),stroke)
        }
    }
}
