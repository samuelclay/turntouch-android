package com.turntouch.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS TTDeviceTitleView: fixed 44-point rows below the scrolling action editor. */
@Composable fun RemoteStatusBar(app: AppState) {
    val ble=app.bluetooth
    val remotes=ble.remotes.filter { ble.isSaved(it.address) || it.state=="Connected" || it.state.startsWith("Press") }
    var menuAddress by remember { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<Remote?>(null) }
    Column(Modifier.fillMaxWidth().heightIn(max=176.dp).verticalScroll(rememberScrollState())
        .background(Color(0xfff5f6f8)).semantics { contentDescription="Remote status bar" }) {
        if(remotes.isEmpty()) {
            StatusRow {
                Artwork("remote_graphic",size=32.dp)
                Text("No remotes paired",Modifier.weight(1f).padding(start=12.dp),fontFamily=Effra,fontSize=15.sp,color=Ink)
                TextButton(onClick={app.screen="pairing"},contentPadding=PaddingValues(horizontal=8.dp)) { Text("Pair remote",fontFamily=Effra,fontSize=15.sp) }
            }
        }
        remotes.forEach { remote -> key(remote.address) {
            val connected=remote.state=="Connected"
            val status=when {
                connected -> "connected"
                remote.state=="Connecting…" -> "connecting"
                remote.state=="Discovering services…" -> "connecting"
                remote.state.startsWith("Press") -> "pairing"
                else -> "disconnected"
            }
            Box {
                StatusRow(Modifier.semantics { stateDescription=status }) {
                    Artwork("remote_graphic",size=32.dp)
                    Text(remote.name,Modifier.weight(1f).padding(start=12.dp,end=12.dp),fontFamily=Effra,fontSize=15.sp,color=Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(status,fontFamily=Effra,fontSize=15.sp,color=Color(0xff808aa0))
                    // Keep the iOS 22-point glyph with a full-row-height touch target.
                    Box(Modifier.padding(start=2.dp).size(44.dp).clickable { menuAddress=remote.address }
                        .semantics { contentDescription="Settings for ${remote.name}" },contentAlignment=Alignment.Center) {
                        Artwork("settings",size=22.dp)
                        DropdownMenu(expanded=menuAddress==remote.address,onDismissRequest={menuAddress=null},modifier=Modifier.widthIn(min=240.dp),containerColor=Color.White,tonalElevation=0.dp) {
                            DropdownMenuItem(text={Text(if(connected && remote.battery!=null)"Battery: ${remote.battery}%" else "Battery unavailable")},
                                leadingIcon={Artwork("battery",size=22.dp)},onClick={menuAddress=null})
                            DropdownMenuItem(text={Text(remote.firmware?.let { "Firmware: v$it" } ?: "Firmware unavailable")},
                                leadingIcon={Artwork("versions",size=22.dp)},onClick={menuAddress=null})
                            if(remote.state=="Disconnected" || remote.state=="Nearby")DropdownMenuItem(text={Text("Reconnect remote")},onClick={menuAddress=null;ble.connect(remote.address,false)})
                            DropdownMenuItem(text={Text("Rename remote")},leadingIcon={Artwork("rename",size=22.dp)},enabled=connected,onClick={menuAddress=null;rename=remote})
                            DropdownMenuItem(text={Text("Forget this remote")},leadingIcon={Artwork("remove",size=22.dp)},onClick={menuAddress=null;ble.disconnect(remote.address,true)})
                        }
                    }
                }
            }
        } }
    }
    rename?.let { remote ->
        var name by remember(remote.address) { mutableStateOf(remote.name) }
        var error by remember(remote.address) { mutableStateOf("") }
        AlertDialog(onDismissRequest={rename=null},title={Text("Name your remote")},text={Column {
            OutlinedTextField(name,{name=it},label={Text("Name")});Text(error,color=MaterialTheme.colorScheme.error)
        }},confirmButton={TextButton(onClick={runCatching { ble.rename(remote.address,name) }.onSuccess { rename=null }.onFailure { error=it.message.orEmpty() }}) {Text("Save")}},
            dismissButton={TextButton(onClick={rename=null}) {Text("Cancel")}})
    }
}

@Composable private fun StatusRow(modifier: Modifier=Modifier,content: @Composable RowScope.()->Unit) {
    Column(modifier.fillMaxWidth().height(44.dp)) {
        HorizontalDivider(thickness=0.5.dp,color=Color(0xffc2cbce))
        Row(Modifier.fillMaxWidth().weight(1f).padding(start=24.dp,end=15.dp),verticalAlignment=Alignment.CenterVertically,content=content)
    }
}
