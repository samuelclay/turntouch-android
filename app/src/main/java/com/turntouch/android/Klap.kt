package com.turntouch.android

import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/** TP-Link KLAP v1 and v2; challenge and response signatures are verified. */
class Klap(private val host: String,private val username: String,private val password: String) {
    private var cookie=""
    private lateinit var key: ByteArray
    private lateinit var iv: ByteArray
    private lateinit var signing: ByteArray
    private var sequence=0
    private var ready=false
    private fun hash(algorithm: String,bytes: ByteArray)=MessageDigest.getInstance(algorithm).digest(bytes)
    private fun sha(bytes: ByteArray)=hash("SHA-256",bytes)
    private fun post(path: String,body: ByteArray): ByteArray {
        val c=URI("http://$host/app/$path").toURL().openConnection() as HttpURLConnection
        c.requestMethod="POST";c.connectTimeout=4000;c.readTimeout=5000;c.doOutput=true;c.setRequestProperty("Content-Type","application/octet-stream");c.instanceFollowRedirects=false
        if(cookie.isNotEmpty())c.setRequestProperty("Cookie",cookie)
        try {c.outputStream.use {it.write(body)};check(c.responseCode==200) {tr("Kasa authorization or connection failed ({0})", c.responseCode)};c.headerFields.filterKeys {it?.equals("Set-Cookie",true)==true}.values.flatten().firstOrNull {it.startsWith("TP_SESSIONID=") }?.let {cookie=it.substringBefore(';')};return c.inputStream.use {it.readBytes()}} finally {c.disconnect()}
    }
    private fun handshake() {
        val local=ByteArray(16).also {SecureRandom().nextBytes(it)}
        val reply=post("handshake1",local);require(reply.size==48) {tr("Invalid Kasa handshake")}
        val remote=reply.copyOfRange(0,16);val challenge=reply.copyOfRange(16,48)
        val v1=hash("MD5",hash("MD5",username.toByteArray())+hash("MD5",password.toByteArray()))
        val v2=sha(hash("SHA-1",username.toByteArray())+hash("SHA-1",password.toByteArray()))
        val modern=MessageDigest.isEqual(sha(local+remote+v2),challenge)
        val auth=if(modern)v2 else v1
        check(modern || MessageDigest.isEqual(sha(local+v1),challenge)) {tr("Kasa credentials did not match this device")}
        post("handshake2",sha(if(modern)remote+local+auth else remote+auth))
        val seeds=local+remote+auth
        key=sha("lsk".toByteArray()+seeds).copyOf(16)
        val ivHash=sha("iv".toByteArray()+seeds);iv=ivHash.copyOf(12);sequence=ByteBuffer.wrap(ivHash.copyOfRange(28,32)).int
        signing=sha("ldk".toByteArray()+seeds).copyOf(28);ready=true
    }
    @Synchronized fun command(command: JSONObject): JSONObject {
        if(!ready)handshake()
        sequence+=1;val seq=ByteBuffer.allocate(4).putInt(sequence).array()
        val cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv+seq))
        val encrypted=cipher.doFinal(command.toString().toByteArray())
        try {
            val response=post("request?seq=$sequence",sha(signing+seq+encrypted)+encrypted)
            require(response.size>32) {tr("Invalid Kasa response")}
            val data=response.copyOfRange(32,response.size)
            check(MessageDigest.isEqual(response.copyOf(32),sha(signing+seq+data))) {tr("Kasa response signature did not match")}
            cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv+seq))
            val result=JSONObject(cipher.doFinal(data).toString(Charsets.UTF_8));check(result.optInt("error_code",0)==0) {tr("Kasa rejected command ({0})", result.optInt("error_code"))};return result
        } catch(e: Exception) {ready=false;throw e}
    }
}
