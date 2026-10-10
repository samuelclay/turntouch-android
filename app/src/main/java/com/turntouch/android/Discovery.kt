package com.turntouch.android

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.net.*

data class LanDevice(val name: String,val location: String,val server: String)
object Discovery {
    suspend fun ssdp(context: Context): List<LanDevice> = withContext(Dispatchers.IO) {
        val wifi=context.applicationContext.getSystemService(WifiManager::class.java)
        val lock=wifi.createMulticastLock("turntouch-discovery").apply { setReferenceCounted(false); acquire() }
        try { DatagramSocket().use { socket ->
            socket.soTimeout=1000
            val bytes="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n".toByteArray()
            repeat(2) { socket.send(DatagramPacket(bytes,bytes.size,InetAddress.getByName("239.255.255.250"),1900)) }
            val found=linkedMapOf<String,LanDevice>(); val end=System.currentTimeMillis()+5000
            while(System.currentTimeMillis()<end) { ensureActive(); val p=DatagramPacket(ByteArray(8192),8192)
                try { socket.receive(p); val headers=String(p.data,0,p.length,Charsets.UTF_8).lines().mapNotNull { val i=it.indexOf(':'); if(i<0)null else it.substring(0,i).lowercase() to it.substring(i+1).trim() }.toMap()
                    val location=headers["location"] ?: continue; if(location in found)continue
                    val name=runCatching { Network.tag(Network.request(location),"friendlyName") }.getOrNull() ?: p.address.hostAddress.orEmpty()
                    found[location]=LanDevice(name,location,headers["server"] ?: "")
                } catch(_:SocketTimeoutException) {} }
            found.values.toList()
        } } finally { lock.release() }
    }
}
