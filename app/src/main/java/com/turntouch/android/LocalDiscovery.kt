package com.turntouch.android

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.*

object LocalDiscovery {
    suspend fun kasa(): List<LanDevice> = withContext(Dispatchers.IO) {
        val found=linkedMapOf<String,LanDevice>()
        DatagramSocket().use { socket ->
            socket.broadcast=true;socket.soTimeout=500
            val command=Network.kasaCrypt("""{"system":{"get_sysinfo":{}}}""".toByteArray())
            val modern=byteArrayOf(2,0,0,1,0,0,0,0,0,0,0,0,0x46,0x3c,0xb5.toByte(),0xd3.toByte())
            val broadcasts=NetworkInterface.getNetworkInterfaces().toList().flatMap { it.interfaceAddresses.mapNotNull { a->a.broadcast } }.distinct()
            for(address in broadcasts+InetAddress.getByName("255.255.255.255")) {socket.send(DatagramPacket(command,command.size,address,9999));socket.send(DatagramPacket(modern,modern.size,address,20002))}
            val end=System.currentTimeMillis()+5000
            while(System.currentTimeMillis()<end) {ensureActive();val p=DatagramPacket(ByteArray(8192),8192)
                try {socket.receive(p);val raw=p.data.copyOf(p.length)
                    if(p.port==9999) {val info=JSONObject(Network.kasaCrypt(raw,true).toString(Charsets.UTF_8)).getJSONObject("system").getJSONObject("get_sysinfo");found[p.address.hostAddress!!]=LanDevice(info.optString("alias","Kasa"),"http://${p.address.hostAddress}","Kasa legacy")}
                    else if(p.port==20002 && raw.size>16) {val info=JSONObject(String(raw,16,raw.size-16)).optJSONObject("result") ?: continue;found[p.address.hostAddress!!]=LanDevice(info.optString("device_model","Kasa"),"http://${p.address.hostAddress}","Kasa KLAP")}
                } catch(_:SocketTimeoutException) {} catch(_:org.json.JSONException) {} }
        };found.values.toList()
    }
    suspend fun govee(context: Context): List<LanDevice> = withContext(Dispatchers.IO) {GoveeLan.scan(context)}
}
object GoveeLan {
    private fun message(command: String,data: JSONObject)=JSONObject().put("msg",JSONObject().put("cmd",command).put("data",data)).toString().toByteArray()
    private fun send(socket: DatagramSocket,host: String,port: Int,command: String,data: JSONObject) {val bytes=message(command,data);socket.send(DatagramPacket(bytes,bytes.size,InetAddress.getByName(host),port))}
    private fun socket()=DatagramSocket(null).apply {reuseAddress=true;bind(InetSocketAddress(4002));soTimeout=700}
    @Synchronized fun scan(context: Context): List<LanDevice> {
        val lock=context.getSystemService(WifiManager::class.java).createMulticastLock("turntouch-govee").apply {acquire()}
        try {socket().use {s->send(s,"239.255.255.250",4001,"scan",JSONObject().put("account_topic","reserve"));val found=linkedMapOf<String,LanDevice>();val end=System.currentTimeMillis()+4000
            while(System.currentTimeMillis()<end) {val p=DatagramPacket(ByteArray(4096),4096);try {s.receive(p);val msg=JSONObject(String(p.data,0,p.length)).getJSONObject("msg");if(msg.optString("cmd")!="scan")continue;val data=msg.getJSONObject("data");val host=p.address.hostAddress!!;found[host]=LanDevice(data.optString("sku","Govee"),"http://$host",data.optString("device"))} catch(_:SocketTimeoutException) {} }
            return found.values.toList()
        }}finally {lock.release()}
    }
    @Synchronized fun state(host: String): JSONObject = socket().use {s->
        send(s,host,4003,"devStatus",JSONObject());val end=System.currentTimeMillis()+4000
        while(System.currentTimeMillis()<end) {val p=DatagramPacket(ByteArray(4096),4096);try {s.receive(p);if(p.address.hostAddress!=host)continue;val msg=JSONObject(String(p.data,0,p.length)).getJSONObject("msg");if(msg.optString("cmd")=="devStatus")return@use msg.getJSONObject("data")} catch(_:SocketTimeoutException) {}}
        error("Govee did not respond. Enable LAN Control in the Govee app.")
    }
    @Synchronized fun control(host: String,action: String) {
        val old=state(host)
        val brightness=action.startsWith("Brightness")
        val value=if(brightness)((old.getInt("brightness"))+if(action.endsWith("Up"))10 else -10).coerceIn(0,100) else if(action=="DeviceToggle")1-old.getInt("onOff") else if(action=="DeviceOn")1 else 0
        DatagramSocket().use {send(it,host,4003,if(brightness)"brightness" else "turn",JSONObject().put("value",value))}
        var confirmed=false
        repeat(4) {if(!confirmed) {Thread.sleep(200);val actual=state(host);confirmed=actual.optInt(if(brightness)"brightness" else "onOff")==value}}
        check(confirmed) {"Govee did not confirm the change"}
    }
}
