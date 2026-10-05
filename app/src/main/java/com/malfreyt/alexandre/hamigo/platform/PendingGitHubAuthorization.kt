package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Survives process death while the browser is open; never part of a progress backup. */
internal class PendingGitHubAuthorization(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hamigo_pending_oauth", Context.MODE_PRIVATE)
    private val alias = "${context.packageName}.github-pkce.v1"
    private val purpose = "hamigo.github.pkce.v1".toByteArray(Charsets.UTF_8)

    @Synchronized fun save(session: GitHubPkce.Session) {
        val plain = JSONObject().put("state", session.state).put("verifier", session.verifier)
            .put("expires", session.expiresAtMillis).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(purpose)
        val encrypted = cipher.doFinal(plain)
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Impossible de préparer la connexion sur cet appareil."
        }
    }

    @Synchronized fun restore(nowMillis: Long = System.currentTimeMillis()): GitHubPkce.Session? {
        val encrypted = prefs.getString("ciphertext", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,
                Base64.decode(prefs.getString("iv", null), Base64.NO_WRAP)))
            cipher.updateAAD(purpose)
            val value = JSONObject(String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8))
            val state = value.getString("state"); val verifier = value.getString("verifier")
            val expires = value.getLong("expires")
            require(state.matches(Regex("[A-Za-z0-9_-]{43,128}")) && verifier.matches(Regex("[A-Za-z0-9_-]{43,128}")))
            require(expires > nowMillis && expires <= nowMillis + 600_000L)
            GitHubPkce.Session(state, verifier, expires)
        } catch (_: Exception) { clear(); null }
    }

    @Synchronized fun clear() { prefs.edit().clear().commit() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}
