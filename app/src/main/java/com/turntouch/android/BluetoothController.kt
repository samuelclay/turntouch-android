@file:Suppress("MissingPermission", "DEPRECATION")
package com.turntouch.android

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.os.*
import androidx.compose.runtime.*
import java.util.UUID
import kotlinx.coroutines.*

object RemoteProtocol {
    val service: UUID=UUID.fromString("99c31523-dc4f-41b1-bb04-4e4deb81fadd")
    val buttons: UUID=UUID.fromString("99c31525-dc4f-41b1-bb04-4e4deb81fadd")
    val nickname: UUID=UUID.fromString("99c31526-dc4f-41b1-bb04-4e4deb81fadd")
    val battery: UUID=UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    val firmware: UUID=UUID.fromString("00001534-1212-efde-1523-785feabcd123")
    val cccd: UUID=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
data class Remote(val address: String, val name: String, val rssi: Int=0, val state: String="Nearby", val battery: Int?=null, val firmware: String?=null, val pairedButtons: Int=0)
enum class RemotePairingPhase { Intro, Searching, Connecting, Buttons, Success, Failure }
class BluetoothController(private val app: AppState) {
    private val context=app.context
    private val manager=context.getSystemService(BluetoothManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private val prefs=context.getSharedPreferences("remotes",Context.MODE_PRIVATE)
    val remotes=mutableStateListOf<Remote>()
    var scanning by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    private val connections=mutableMapOf<String,BluetoothGatt>()
    private val decoders=mutableMapOf<String,ButtonDecoder>()
    private val queues=mutableMapOf<String,ArrayDeque<() -> Boolean>>()
    private val retries=mutableMapOf<String,Int>()
    private val automatic=mutableSetOf<String>()
    private val pairing=mutableSetOf<String>()
    private val saved get()=prefs.getStringSet("paired",emptySet())!!.toSet()
    val hasSavedRemotes get()=saved.isNotEmpty()
    fun isSaved(address: String)=address in saved
    var pairingPhase by mutableStateOf(RemotePairingPhase.Intro); private set
    var pairingButtons by mutableIntStateOf(0); private set
    var pairingSecondsLeft by mutableIntStateOf(30); private set
    var pairingFailure by mutableStateOf<String?>(null); private set
    private var pairingActive=false
    private var pairingDeadline=0L
    private var scanGeneration=0
    private val pairingTick=object: Runnable {
        override fun run() {
            if(!pairingActive)return
            val left=pairingDeadline-SystemClock.elapsedRealtime()
            pairingSecondsLeft=((left+999)/1000).toInt().coerceAtLeast(0)
            if(left<=0)failPairing(if(pairingPhase==RemotePairingPhase.Buttons)"Press all four buttons before the timer runs out." else "No remotes could be found")
            else handler.postDelayed(this,200)
        }
    }
    init { saved.forEach { remotes.add(Remote(it,prefs.getString("name:$it","Turn Touch")!!,state="Disconnected")) } }
    fun beginPairing() {
        cancelPairing(); error=null; pairingFailure=null; pairingButtons=0
        remotes.removeAll { it.address !in saved }
        pairingActive=true; pairingPhase=RemotePairingPhase.Searching
        pairingDeadline=SystemClock.elapsedRealtime()+20000
        startScan(); handler.post(pairingTick)
        error?.let { failPairing(it) }
    }
    fun cancelPairing() {
        pairingActive=false; handler.removeCallbacks(pairingTick); stopScan()
        pairing.toList().forEach { disconnect(it) }; pairing.clear()
    }
    private fun failPairing(message: String) {
        cancelPairing(); pairingFailure=message; pairingPhase=RemotePairingPhase.Failure
    }
    fun pairingUnavailable(message: String) { report(message); failPairing(message) }
    private fun refreshPairing() {
        if(!pairingActive)return
        val candidates=remotes.filter { it.address in pairing }
        val ready=candidates.filter { it.state.startsWith("Press") }.maxByOrNull { Integer.bitCount(it.pairedButtons) }
        if(ready!=null) {
            if(pairingPhase!=RemotePairingPhase.Buttons)pairingDeadline=SystemClock.elapsedRealtime()+30000
            pairingPhase=RemotePairingPhase.Buttons; pairingButtons=ready.pairedButtons
        } else if(candidates.any { it.state=="Connecting…" || it.state=="Discovering services…" }) {
            if(pairingPhase!=RemotePairingPhase.Connecting)pairingDeadline=SystemClock.elapsedRealtime()+15000
            pairingPhase=RemotePairingPhase.Connecting
        }
    }
    private fun update(address: String, transform: (Remote)->Remote) { val i=remotes.indexOfFirst { it.address==address }; if(i>=0)remotes[i]=transform(remotes[i]) }
    private fun report(message: String) { error=message; app.log(message) }
    private val scan=object: ScanCallback() {
        override fun onScanResult(type: Int,result: ScanResult) { handler.post {
            val name=result.scanRecord?.deviceName ?: result.device.name ?: "Turn Touch"
            if(name.contains("sol",true) || name.contains("page turner",true))return@post
            val address=result.device.address
            if(remotes.none { it.address==address }) { remotes.add(Remote(address,prefs.getString("name:$address",name)!!,result.rssi)); app.log("Found $name ($address)") }
            else update(address) { it.copy(rssi=result.rssi) }
            if(address in saved && address !in connections) connect(address,false)
            else if(pairingActive && address !in saved && address !in connections)connect(address)
        } }
        override fun onScanFailed(code: Int) { handler.post { scanning=false; report("Bluetooth scan failed ($code). Try again."); if(pairingActive)failPairing(error!!) } }
    }
    fun startScan() {
        if(scanning)return
        error=null
        try {
            val adapter=manager?.adapter
            if(adapter==null || !adapter.isEnabled) { report("Turn on Bluetooth to find your remotes."); return }
            scanning=true
            val filters=listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(RemoteProtocol.service)).build(),ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString("00001523-0000-1000-8000-00805f9b34fb"))).build())
            adapter.bluetoothLeScanner.startScan(filters,ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan)
            val generation=++scanGeneration
            handler.postDelayed({ if(scanGeneration==generation)stopScan() },20000)
            app.log("Looking for Turn Touch remotes…")
        } catch(e: SecurityException) { scanning=false; report("Allow Nearby devices to find and connect your remote.") }
    }
    fun stopScan() { scanGeneration++; try { manager?.adapter?.bluetoothLeScanner?.stopScan(scan) } catch(_:SecurityException) {} ; scanning=false }
    fun reconnect(background: Boolean=false) { try { if(manager?.adapter?.isEnabled==true)saved.forEach { if(it !in connections)connect(it,false,background) } } catch(_:SecurityException) {} }
    fun adapterStateChanged(state: Int) {
        if(state==BluetoothAdapter.STATE_ON) { app.log("Bluetooth enabled; reconnecting saved remotes"); reconnect(background=true) }
        else if(state==BluetoothAdapter.STATE_TURNING_OFF || state==BluetoothAdapter.STATE_OFF) {
            if(pairingActive)failPairing("Turn on Bluetooth to find your remotes.")
            connections.keys.toList().forEach { disconnect(it) }
            retries.clear()
            app.log("Bluetooth disabled; waiting for it to return")
        }
    }
    fun connect(address: String,newPair: Boolean=true,background: Boolean=false) {
        if(address in connections)return
        try {
            if(manager?.adapter?.isEnabled!=true)return
            context.startForegroundService(Intent(context,RemoteService::class.java))
            if(remotes.none { it.address==address })remotes.add(Remote(address,prefs.getString("name:$address","Turn Touch")!!))
            update(address) { it.copy(state="Connecting…") }
            if(newPair && address !in saved)pairing.add(address)
            refreshPairing()
            decoders[address]=ButtonDecoder(app::dispatch) { app.active=it }
            // Opening the app tries a quick direct connection. Background recovery keeps
            // known remotes registered with Android while they are asleep/out of range.
            val autoConnect=background && address in saved
            if(autoConnect)automatic.add(address)
            val g=manager!!.adapter.getRemoteDevice(address).connectGatt(context,autoConnect,callback,BluetoothDevice.TRANSPORT_LE)
            connections[address]=g
            if(!autoConnect)handler.postDelayed({ if(connections[address]===g && remotes.firstOrNull { it.address==address }?.state=="Connecting…") { connectionFailed(g); report("Connection timed out. Wake the remote and try again.") } },15000)
        } catch(e: SecurityException) { report("Nearby devices permission is needed to connect.") }
    }
    fun disconnect(address: String,forget: Boolean=false) {
        if(forget) { prefs.edit().putStringSet("paired",saved-address).apply(); pairing.remove(address) }
        pairing.remove(address)
        automatic.remove(address); retries.remove(address)
        val g=connections.remove(address); g?.disconnect(); g?.close(); decoders.remove(address)?.reset(); queues.remove(address)
        update(address) { it.copy(state=if(forget)"Nearby" else "Disconnected",pairedButtons=0) }
        if(connections.isEmpty() && saved.isEmpty())context.stopService(Intent(context,RemoteService::class.java))
    }
    private fun connectionFailed(g: BluetoothGatt) {
        val address=g.device.address
        if(connections[address]!==g)return
        connections.remove(address); automatic.remove(address); queues.remove(address)
        decoders.remove(address)?.reset(); g.disconnect(); g.close()
        update(address) { it.copy(state="Disconnected") }
        if(address in saved) {
            val attempt=(retries[address] ?: 0)+1; retries[address]=attempt
            handler.postDelayed({ if(address in saved && address !in connections)connect(address,false,background=true) },(attempt*2000L).coerceAtMost(30000))
        }
    }
    fun rename(address: String,name: String) {
        val clean=name.trim(); require(clean.toByteArray(Charsets.UTF_8).size in 1..20) { "Use a name of 1–20 UTF-8 bytes." }
        val g=connections[address] ?: return
        val c=g.getService(RemoteProtocol.service)?.getCharacteristic(RemoteProtocol.nickname) ?: return
        enqueue(g) { c.value=clean.toByteArray(); g.writeCharacteristic(c) }
        prefs.edit().putString("name:$address",clean).apply(); update(address) { it.copy(name=clean) }
    }
    private fun enqueue(g: BluetoothGatt, op: ()->Boolean) { val q=queues.getOrPut(g.device.address) { ArrayDeque() }; val empty=q.isEmpty(); q.addLast(op); if(empty)advance(g,false) }
    private fun advance(g: BluetoothGatt,remove: Boolean=true) { val q=queues[g.device.address] ?: return; if(remove && q.isNotEmpty())q.removeFirst(); while(q.isNotEmpty()) { if(q.first().invoke())break else { q.removeFirst(); app.log("Bluetooth operation could not start") } } }
    private fun value(g: BluetoothGatt,c: BluetoothGattCharacteristic,bytes: ByteArray) { val address=g.device.address
        when(c.uuid) {
            RemoteProtocol.buttons -> {
                if(bytes.size<2)return
                app.log("Remote ${address.takeLast(5)} packet ${bytes.joinToString("") { "%02x".format(it) }}")
                if(address in pairing) {
                    val mask=bytes[0].toInt().inv() and 15
                    update(address) { val count=it.pairedButtons or mask; it.copy(pairedButtons=count,state=if(count==15)"Connected" else "Press all four buttons to pair") }
                    refreshPairing()
                    if(remotes.first { it.address==address }.pairedButtons==15) {
                        pairing.remove(address); prefs.edit().putStringSet("paired",saved+address).apply(); app.log("Remote paired")
                        if(pairingActive) { pairingButtons=15; pairingPhase=RemotePairingPhase.Success; cancelPairing() }
                    }
                } else { val decoder=decoders[address] ?: return; decoder.read(bytes,SystemClock.elapsedRealtime()); handler.postDelayed({ decoder.flush(SystemClock.elapsedRealtime()) },300) }
            }
            RemoteProtocol.battery -> if(bytes.isNotEmpty())update(address) { it.copy(battery=bytes[0].toInt() and 255) }
            RemoteProtocol.nickname -> { val name=bytes.toString(Charsets.UTF_8).trim('\u0000',' '); if(name.isNotBlank()) { prefs.edit().putString("name:$address",name).apply(); update(address) { it.copy(name=name) } } }
            RemoteProtocol.firmware -> update(address) { it.copy(firmware=bytes.joinToString(".") { (it.toInt() and 255).toString() }) }
        }
    }
    private val callback=object: BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt,status: Int,newState: Int) { handler.post {
            val address=g.device.address
            if(connections[address]!==g)return@post
            if(newState==BluetoothProfile.STATE_CONNECTED && status==BluetoothGatt.GATT_SUCCESS) {
                update(address) { it.copy(state="Discovering services…") }
                if(!g.discoverServices())connectionFailed(g)
                handler.postDelayed({ if(connections[address]===g && remotes.firstOrNull { it.address==address }?.state=="Discovering services…") { report("Remote setup timed out; reconnecting"); connectionFailed(g) } },15000)
            }
            else if(newState==BluetoothProfile.STATE_DISCONNECTED || status!=0) {
                app.log("Remote disconnected (GATT $status)")
                // Normal link loss keeps the automatic GATT registration alive. Stack errors
                // need a fresh registration; a just-paired direct connection does too.
                if(status==BluetoothGatt.GATT_SUCCESS && address in automatic && address in saved) {
                    queues.remove(address); decoders[address]?.reset()
                    update(address) { it.copy(state="Connecting…") }
                    app.log("Waiting for saved remote to return")
                } else connectionFailed(g)
            }
        } }
        override fun onServicesDiscovered(g: BluetoothGatt,status: Int) { handler.post {
            if(connections[g.device.address]!==g)return@post
            if(status!=0) { report("Unable to read remote services ($status)"); connectionFailed(g); return@post }
            val buttons=g.getService(RemoteProtocol.service)?.getCharacteristic(RemoteProtocol.buttons)
            val cccd=buttons?.getDescriptor(RemoteProtocol.cccd)
            if(buttons==null || cccd==null) { report("This device does not expose the Turn Touch button service."); disconnect(g.device.address); return@post }
            if(!g.setCharacteristicNotification(buttons,true)) { connectionFailed(g); return@post }
            enqueue(g) { cccd.value=BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(cccd) }
            for(s in g.services)for(c in s.characteristics)if(c.uuid in listOf(RemoteProtocol.battery,RemoteProtocol.nickname,RemoteProtocol.firmware))enqueue(g) { g.readCharacteristic(c) }
        } }
        override fun onDescriptorWrite(g: BluetoothGatt,d: BluetoothGattDescriptor,status: Int) { handler.post {
            if(connections[g.device.address]!==g)return@post
            if(status==0) { retries.remove(g.device.address); update(g.device.address) { it.copy(state=if(it.address in pairing)"Press all four buttons to pair" else "Connected") }; app.log("Button notifications enabled") }
            else { report("Could not subscribe to remote buttons ($status)"); connectionFailed(g); return@post }
            refreshPairing()
            advance(g)
        } }
        override fun onCharacteristicRead(g: BluetoothGatt,c: BluetoothGattCharacteristic,status: Int) { handler.post { if(connections[g.device.address]!==g)return@post; if(status==0)value(g,c,c.value ?: byteArrayOf()); advance(g) } }
        override fun onCharacteristicChanged(g: BluetoothGatt,c: BluetoothGattCharacteristic) { val bytes=c.value?.clone() ?: return; handler.post { if(connections[g.device.address]===g)value(g,c,bytes) } }
        override fun onCharacteristicWrite(g: BluetoothGatt,c: BluetoothGattCharacteristic,status: Int) { handler.post { if(connections[g.device.address]!==g)return@post; if(status!=0)report("Remote write failed ($status)"); advance(g) } }
    }
}
class RemoteService: Service() {
    private val adapterReceiver=object: BroadcastReceiver() {
        override fun onReceive(context: Context,intent: Intent) {
            if(intent.action==BluetoothAdapter.ACTION_STATE_CHANGED)
                (application as TurnTouchApp).state.bluetooth.adapterStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,BluetoothAdapter.ERROR))
        }
    }
    override fun onCreate() { super.onCreate(); val nm=getSystemService(NotificationManager::class.java); nm.createNotificationChannel(NotificationChannel("remote","Remote connection",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        startForeground(1,Notification.Builder(this,"remote").setSmallIcon(R.drawable.battery).setContentTitle("Turn Touch").setContentText("Listening for your remote").setContentIntent(open).setOngoing(true).build())
        val filter=IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if(Build.VERSION.SDK_INT>=33)registerReceiver(adapterReceiver,filter,Context.RECEIVER_EXPORTED) else registerReceiver(adapterReceiver,filter)
    }
    override fun onStartCommand(intent: Intent?,flags: Int,id: Int): Int { (application as TurnTouchApp).state.bluetooth.reconnect(background=true); return START_STICKY }
    override fun onDestroy() { unregisterReceiver(adapterReceiver); super.onDestroy() }
    override fun onBind(intent: Intent?)=null
}
