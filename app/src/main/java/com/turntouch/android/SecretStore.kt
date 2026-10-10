package com.turntouch.android
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private secrets are encrypted with a non-exportable Android Keystore key. */
class SecretStore(context: Context) {
    private val prefs=context.getSharedPreferences("connections",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey("turntouch-connections",null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {init(KeyGenParameterSpec.Builder("turntouch-connections",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
    }
    fun get(name: String): String? {
        val saved=prefs.getString(name,null) ?: return null
        val bytes=Base64.decode(saved,Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").run {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8)}
    }
    fun put(name: String,value: String) {val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());prefs.edit().putString(name,Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray()),Base64.NO_WRAP)).apply()}
}
