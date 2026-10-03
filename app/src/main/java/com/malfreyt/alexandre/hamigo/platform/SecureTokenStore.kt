package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM ciphertext in app-private preferences; the encryption key never leaves Android Keystore. */
class SecureTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hamigo_secure", Context.MODE_PRIVATE)
    private val alias = "${context.packageName}.github-token.v1"

    @Synchronized
    fun store(token: String) {
        val clean = token.trim()
        require(clean.length in 10..4096 && clean.none { it.isWhitespace() || it.isISOControl() }) {
            "Le jeton GitHub n'a pas un format valide."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        check(prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()) { "Impossible de conserver le jeton sur cet appareil." }
    }

    @Synchronized
    fun get(): String? {
        val encrypted = prefs.getString("ciphertext", null) ?: return null
        val iv = prefs.getString("iv", null) ?: return null
        return try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secret = store.getKey(alias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            // A restored ciphertext or invalidated key needs fresh authentication, never plaintext recovery.
            prefs.edit().clear().apply()
            null
        }
    }

    @Synchronized
    fun delete() {
        prefs.edit().clear().commit()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
    }

    fun hasToken(): Boolean = get() != null

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }
}
