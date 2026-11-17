package dev.kalpane.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.kalpane.data.hash
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Tokens never enter notebook JSON, logs, saved UI state, or Android backup. */
class CredentialVault(context: Context) {
    private val prefs = context.getSharedPreferences("encrypted_credentials", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("kalpane.backup.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("kalpane.backup.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }
    }
    fun put(repo: String, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(repo.toByteArray()) }
        val payload = cipher.iv + cipher.doFinal(token.toByteArray())
        check(prefs.edit().putString(hash(repo.toByteArray()), Base64.encodeToString(payload, Base64.NO_WRAP)).commit()) { "Could not store credential." }
    }
    fun get(repo: String): String? {
        val encoded = prefs.getString(hash(repo.toByteArray()), null) ?: return null
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0,12)))
            updateAAD(repo.toByteArray()); doFinal(data.copyOfRange(12,data.size)).toString(Charsets.UTF_8)
        }
    }
    fun remove(repo: String) { prefs.edit().remove(hash(repo.toByteArray())).apply() }
}
