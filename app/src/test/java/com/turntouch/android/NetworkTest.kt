package com.turntouch.android
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread
class NetworkTest {
 @Test fun kasaKnownCipherAndRoundTrip() {val plaintext="{\"system\":{\"get_sysinfo\":{}}}".toByteArray();val encrypted=Network.kasaCrypt(plaintext);assertEquals(0xd0,encrypted[0].toInt() and 255);assertEquals(0xf2,encrypted[1].toInt() and 255);assertArrayEquals(plaintext,Network.kasaCrypt(encrypted,true))}
 @Test fun xmlEscapesUntrustedValues() {assertEquals("&lt;a&gt;&amp;&quot;&apos;",Network.xml("<a>&\"'"));assertEquals("23",Network.tag("<s:CurrentVolume>23</s:CurrentVolume>","CurrentVolume"))}
 @Test fun httpFailuresAreNotReportedAsSuccess() {ServerSocket(0).use { server-> val t=thread {server.accept().use {client->val r=client.getInputStream().bufferedReader();while(!r.readLine().isNullOrEmpty()){};client.getOutputStream().write("HTTP/1.1 500 Error\r\nContent-Length: 4\r\nConnection: close\r\n\r\nfail".toByteArray())}};val result=runCatching {Network.request("http://127.0.0.1:${server.localPort}")};assertTrue(result.isFailure);assertTrue(result.exceptionOrNull()!!.message!!.contains("500"));t.join()}}
}
