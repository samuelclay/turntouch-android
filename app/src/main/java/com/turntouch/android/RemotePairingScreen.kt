package com.turntouch.android

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private val PairingBlue=Color(0xff4383c0)
private val PairingText=Color(0xff3d3c3d)

/** The iOS form-sheet sequence, using the original hero art, type, colors, and footer. */
@Composable fun RemotePairingDialog(app: AppState, startPairing: ()->Unit, initialTutorial: Boolean=false, onClose: ()->Unit) {
    if(app.demo!=null && !initialTutorial) {
        Dialog(onDismissRequest=onClose) { Surface { DemoConnectionScreen(app,"remote",onClose) } }
        return
    }
    val ble=app.bluetooth
    var intro by remember { mutableStateOf(!initialTutorial && !ble.hasSavedRemotes) }
    var tutorial by remember { mutableStateOf(initialTutorial) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(intro,tutorial) { if(!intro && !tutorial && !started) { started=true; startPairing() } }
    DisposableEffect(ble) { onDispose { if(!initialTutorial)ble.cancelPairing() } }
    val phase=if(intro)RemotePairingPhase.Intro else if(!started)RemotePairingPhase.Searching else ble.pairingPhase
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center) {
            val tablet=maxWidth>=600.dp
            Surface(Modifier.padding(horizontal=if(tablet)24.dp else 8.dp,vertical=if(tablet)32.dp else 12.dp)
                .widthIn(max=540.dp).fillMaxWidth().heightIn(max=720.dp).fillMaxHeight(),
                shape=RoundedCornerShape(14.dp),color=Color.White) {
                Column {
                    Box(Modifier.fillMaxWidth().height(52.dp).background(Color(0xfffafafa))) {
                        Text(if(tutorial)tr("How it works") else if(phase in listOf(RemotePairingPhase.Searching,RemotePairingPhase.Connecting,RemotePairingPhase.Buttons))tr("Pairing a new remote") else "",
                            Modifier.align(Alignment.Center),fontFamily=Effra,fontSize=18.sp,color=PairingText)
                        TextButton(onClick=onClose,Modifier.align(Alignment.CenterEnd)) {
                            Text(if(phase in listOf(RemotePairingPhase.Success,RemotePairingPhase.Failure))tr("Done") else tr("Cancel"),fontFamily=Effra,fontSize=17.sp)
                        }
                    }
                    HorizontalDivider(color=Color(0xffe7e7e7))
                    if(!tutorial && app.demo==null)TextButton(onClick={app.onboarding=false;app.screen="demo"},Modifier.align(Alignment.CenterHorizontally)) { Text(tr("Try offline demo")) }
                    if(tutorial) PairingTutorial(app.twelve,onClose)
                    else AnimatedContent(phase,Modifier.weight(1f),transitionSpec={
                        (slideInHorizontally { it }+fadeIn()).togetherWith(slideOutHorizontally { -it/3 }+fadeOut())
                    },label=tr("Pairing pages")) { page ->
                        PairingPage(page,ble.pairingButtons,ble.pairingSecondsLeft,ble.pairingFailure) {
                            when(page) {
                                RemotePairingPhase.Intro -> intro=false
                                RemotePairingPhase.Failure -> startPairing()
                                RemotePairingPhase.Success -> tutorial=true
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable internal fun PairingPage(phase: RemotePairingPhase,buttons: Int=0,secondsLeft: Int=30,failure: String?=null,onNext: ()->Unit) {
    val working=phase in listOf(RemotePairingPhase.Searching,RemotePairingPhase.Connecting,RemotePairingPhase.Buttons)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                if(working) {
                    Canvas(Modifier.widthIn(max=380.dp).fillMaxWidth().aspectRatio(1.36f).semantics {
                        contentDescription=tr("Remote buttons: {0} of 4 confirmed", Integer.bitCount(buttons))
                    }) {
                        diamondPoints(size.width,size.height,4.dp.toPx()).forEachIndexed { index,points ->
                            drawPath(polygon(points),if(buttons and (1 shl index)!=0)Color(0xff1555d8).copy(alpha=0.8f) else Color(0xff606063).copy(alpha=0.2f))
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                } else Artwork(when(phase) {
                    RemotePairingPhase.Success -> "modal_remote_paired"
                    RemotePairingPhase.Failure -> "modal_remote_failed"
                    else -> "modal_remote_hero"
                },Modifier.size(240.dp),240.dp)
                Text(when(phase) {
                    RemotePairingPhase.Intro -> tr("Welcome to Turn Touch")
                    RemotePairingPhase.Success -> tr("That worked perfectly")
                    RemotePairingPhase.Failure -> tr("Uh Oh...")
                    else -> tr("Pairing your Turn Touch")
                },Modifier.padding(top=if(working)0.dp else 24.dp),fontFamily=Effra,fontSize=24.sp,color=PairingText,textAlign=TextAlign.Center)
                Text(when(phase) {
                    RemotePairingPhase.Intro -> tr("Beautiful control")
                    RemotePairingPhase.Searching -> tr("Searching for remotes...")
                    RemotePairingPhase.Connecting -> tr("Connecting...")
                    RemotePairingPhase.Buttons -> tr("Press all four buttons to connect")
                    RemotePairingPhase.Success -> tr("Your remote has been paired")
                    RemotePairingPhase.Failure -> failure ?: tr("No remotes could be found")
                },Modifier.padding(top=if(working)16.dp else 24.dp),fontFamily=Effra,fontSize=if(working)17.sp else 18.sp,
                    fontWeight=if(working)FontWeight.Normal else FontWeight.Light,color=if(working)Color(0xffa6aeb3) else PairingText,textAlign=TextAlign.Center)
                if(working) Box(Modifier.height(72.dp),contentAlignment=Alignment.Center) {
                    if(phase==RemotePairingPhase.Buttons) LinearProgressIndicator(
                        progress={1f-secondsLeft.coerceIn(0,30)/30f},modifier=Modifier.width(150.dp).height(2.dp)
                            .semantics { contentDescription=tr("{0} seconds remaining to pair", secondsLeft) },color=Blue,trackColor=Color(0xffe7e7e7),drawStopIndicator={})
                    else PairingSpinner()
                }
            }
        }
        if(!working) PairingFooter(when(phase) {
            RemotePairingPhase.Success -> tr("Show me how it works")
            RemotePairingPhase.Failure -> tr("Try again")
            else -> tr("Pair remote")
        },when(phase) {
            RemotePairingPhase.Success -> Color(0xff2fb789)
            RemotePairingPhase.Failure -> Color(0xffffca44)
            else -> PairingBlue
        },onNext)
    }
}

@Composable private fun PairingSpinner() {
    val transition=rememberInfiniteTransition(label=tr("Searching for remotes"))
    val scale by transition.animateFloat(0f,1f,infiniteRepeatable(tween(1000,easing=FastOutSlowInEasing),RepeatMode.Reverse),label=tr("Pulsing circles"))
    Canvas(Modifier.size(36.dp).semantics { contentDescription=tr("Scanning for remotes") }) {
        drawCircle(Color.Blue.copy(alpha=0.6f),radius=size.minDimension/2*scale)
        drawCircle(Color.Blue.copy(alpha=0.6f),radius=size.minDimension/2*(1f-scale))
    }
}

@Composable private fun PairingFooter(title: String,color: Color,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().height(100.dp).background(color).clickable(onClick=onClick),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
        Text(title,color=Color.White,fontFamily=Effra,fontSize=20.sp)
        Artwork("modal_bar_button_chevron",Modifier.padding(start=8.dp).width(12.dp).height(14.dp),14.dp)
    }
}

@Composable private fun ColumnScope.PairingTutorial(twelve: Boolean,onDone: ()->Unit) {
    val pager=rememberPagerState(pageCount={5})
    val scope=rememberCoroutineScope()
    val titles=listOf(tr("Here's how it works"),tr("Each button performs an action"),if(twelve)tr("Three ways to press each button") else tr("Press and hold to change apps"),
        tr("Each button can do multiple actions"),tr("Choose your apps and actions"))
    val subtitles=listOf(tr("Your remote has four buttons"),tr("Like changing the lights, playing music, or turning up the volume"),
        if(twelve)tr("Single tap, double tap, and hold\n= twelve different actions") else tr("Four apps × four buttons per app\n= sixteen different actions"),
        tr("There are batch actions and double-tap actions, all configurable in this app"),tr("Tap a button in the app to change its actions. Use Change to choose an app and connect your devices."))
    val art=listOf("modal_ftux_action","modal_ftux_doubletap","modal_ftux_mode","modal_ftux_change_action","modal_ftux_change_mode")
    HorizontalPager(pager,Modifier.weight(1f).fillMaxWidth().semantics { contentDescription=tr("How it works pages") }) { page ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            Artwork(art[page],Modifier.fillMaxWidth().height(240.dp),240.dp)
            Text(titles[page],Modifier.padding(top=36.dp),fontFamily=Effra,fontSize=22.sp,color=Color(0xff7a797a),textAlign=TextAlign.Center)
            Text(subtitles[page],Modifier.padding(top=24.dp),fontFamily=Effra,fontSize=16.sp,color=Color(0xffb5bcc0),textAlign=TextAlign.Center)
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical=24.dp).semantics { contentDescription=tr("Page {0} of 5", pager.currentPage+1) },horizontalArrangement=Arrangement.Center) {
        repeat(5) { page -> Box(Modifier.padding(horizontal=4.dp).size(8.dp).background(if(page==pager.currentPage)Color(0xffa06060) else Color(0xffe0e0e0),CircleShape)) }
    }
    PairingFooter(if(pager.currentPage==4)tr("That's all there is to it") else tr("Continue"),if(pager.currentPage==4)Color(0xff434340) else PairingBlue) {
        if(pager.currentPage==4)onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage+1) }
    }
}
