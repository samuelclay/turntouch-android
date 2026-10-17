package com.turntouch.android

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

@Composable fun NativeScreen(app: AppState,binding: Binding,commands: SharedFlow<Binding>,onDone: ()->Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp)) { Text(app.catalog.mode(binding.mode).title,Modifier.weight(1f).padding(top=12.dp),fontSize=22.sp);TextButton(onClick=onDone) { Text(tr("Done")) } }
        when(binding.mode) {
            "Camera"->CameraScreen(app,commands)
            "Yoga"->YogaScreen(app,commands)
            else->BrowserScreen(app,binding,commands)
        }
    }
}
@Composable fun ColumnScope.CameraScreen(app: AppState,commands: SharedFlow<Binding>) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted=it }
    val preview=remember { PreviewView(context) }
    val capture=remember { ImageCapture.Builder().build() }
    val recorder=remember { Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build() }
    val video=remember { VideoCapture.withOutput(recorder) }
    val executor=remember { Executor { android.os.Handler(android.os.Looper.getMainLooper()).post(it) } }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) };var front by remember { mutableStateOf(false) };var movie by remember { mutableStateOf(false) };var recording by remember { mutableStateOf<Recording?>(null) };var flash by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var notice by remember { mutableStateOf(tr("Ready")) }
    fun shoot() {
        if(!granted) {permission.launch(Manifest.permission.CAMERA);return}
        val values=ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,"TurnTouch-${System.currentTimeMillis()}");put(MediaStore.MediaColumns.MIME_TYPE,if(movie)"video/mp4" else "image/jpeg") }
        if(movie) {
            if(recording!=null) {recording?.stop();recording=null;return}
            recording=video.output.prepareRecording(context,MediaStoreOutputOptions.Builder(context.contentResolver,MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()).start(executor) { event -> if(event is VideoRecordEvent.Finalize) { notice=if(event.hasError())tr("Video could not be saved ({0})", event.error) else tr("Video saved");app.log(notice);recording=null } };notice=tr("Recording…")
        } else capture.takePicture(ImageCapture.OutputFileOptions.Builder(context.contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values).build(),executor,object: ImageCapture.OnImageSavedCallback { override fun onImageSaved(result: ImageCapture.OutputFileResults) {notice=tr("Photo saved");app.log(notice)};override fun onError(e: ImageCaptureException) {notice=e.message.orEmpty();app.log(notice)} })
    }
    LaunchedEffect(granted) { if(granted) { val future=ProcessCameraProvider.getInstance(context);future.addListener({provider=future.get()},executor) } }
    DisposableEffect(provider,front,movie,granted) {
        val p=provider
        if(granted && p!=null)runCatching { p.unbindAll();val usePreview=Preview.Builder().build().also {it.surfaceProvider=preview.surfaceProvider};p.bindToLifecycle(owner,if(front)CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,usePreview,if(movie)video else capture) }.onFailure {notice=it.message.orEmpty()}
        onDispose { recording?.stop();p?.unbindAll() }
    }
    LaunchedEffect(commands) { commands.collect { b->if(b.mode=="Camera")when(b.action.removePrefix("TTModeCamera")) {"Shoot"->shoot();"SwitchView"->front=!front;"SwitchPhotoVideo"->movie=!movie;"SwitchFlash"->{flash=if(flash==ImageCapture.FLASH_MODE_OFF)ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF;capture.flashMode=flash} } } }
    if(!granted)Button(onClick={permission.launch(Manifest.permission.CAMERA)}) {Text(tr("Allow camera"))}
    else {
        AndroidView(factory={preview},Modifier.fillMaxWidth().weight(1f))
        Text(notice,Modifier.padding(12.dp))
        Row {TextButton(onClick={front=!front}) {Text(tr("Flip"))};Button(onClick={shoot()}) {Text(if(movie)if(recording==null)tr("Record") else tr("Stop") else tr("Take photo"))};TextButton(onClick={movie=!movie}) {Text(if(movie)tr("Photo") else tr("Video"))}}
    }
}
@Composable fun YogaScreen(app: AppState,commands: SharedFlow<Binding>) {
    val poses=remember { listOf(tr("Mountain"),tr("Forward fold"),tr("Half lift"),tr("Plank"),tr("Downward dog"),tr("Warrior I"),tr("Warrior II"),tr("Triangle"),tr("Child’s pose"),tr("Rest")) }
    var pose by remember { mutableIntStateOf(0) };var seconds by remember { mutableIntStateOf(60) };var running by remember { mutableStateOf(false) }
    LaunchedEffect(running,seconds) {if(running && seconds>0) {delay(1000);seconds--} else if(running) {pose=(pose+1)%poses.size;seconds=60} }
    LaunchedEffect(commands) {commands.collect { b->if(b.mode=="Yoga")when(b.action.removePrefix("TTModeYoga")) {"Next"->{pose=(pose+1)%poses.size;seconds=b.options["seconds"]?.toIntOrNull() ?: 60};"Previous"->{pose=(pose-1+poses.size)%poses.size;seconds=60};"TimeUp"->seconds+=15;"TimeDown"->seconds=(seconds-15).coerceAtLeast(0)} } }
    Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center) {Text(poses[pose],fontSize=36.sp);Text(tr("{0}:{1}", seconds/60, (seconds%60).toString().padStart(2,'0')),fontSize=64.sp);Button(onClick={running=!running}) {Text(if(running)tr("Pause") else tr("Start"))};Text(tr("Use your remote to move through poses or adjust the time."))}
}
@Composable fun BrowserScreen(app: AppState,binding: Binding,commands: SharedFlow<Binding>) {
    val context=LocalContext.current
    val web=remember { WebView(context).apply { settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;webViewClient=WebViewClient() } }
    var url by remember(binding.mode) { mutableStateOf(binding.options["url"] ?: if(binding.mode=="News")"https://newsblur.com" else "") }
    fun navigate() {if(url.startsWith("https://") || url.startsWith("http://"))web.loadUrl(url) else app.log(tr("Enter an http:// or https:// URL"))}
    LaunchedEffect(binding.mode) {if(url.isNotBlank())navigate()}
    LaunchedEffect(commands) {commands.collect { b->if(b.mode==binding.mode)when(b.action.removePrefix("TTMode${b.mode}")) {
        "ScrollUp"->web.pageUp(false);"ScrollDown"->web.pageDown(false)
        "NextSlide","Next","NextStory"->web.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_RIGHT))
        "PreviousSlide","PreviousStory"->web.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_LEFT))
        "Menu"->if(web.canGoBack())web.goBack()
        "Play","Pause"->web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(v=>v.paused?v.play():v.pause())",null)
        "FF"->web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(v=>v.currentTime+=10)",null)
        "Rewind"->web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(v=>v.currentTime-=10)",null)
        else->app.log(tr("This page does not expose that control"))
    } } }
    DisposableEffect(web) {onDispose {web.stopLoading();web.destroy()}}
    Column(Modifier.fillMaxSize()) {Row {OutlinedTextField(url,{url=it},Modifier.weight(1f),label={Text(tr("Website or presentation URL"))},singleLine=true);TextButton(onClick={navigate()}) {Text(tr("Open"))}};AndroidView(factory={web},Modifier.fillMaxSize())}
}
