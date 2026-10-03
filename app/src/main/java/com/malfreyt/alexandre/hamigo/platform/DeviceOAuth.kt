package com.malfreyt.alexandre.hamigo.platform

import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.net.URLEncoder

/** Requires a user-owned GitHub OAuth app with device flow enabled; no client secret is embedded. */
object DeviceOAuth {
    class Session internal constructor(
        internal val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        internal val interval: Int,
        internal val expiresAtElapsed: Long
    ) // Intentionally not a data class: toString() must not expose the device credential.

    suspend fun start(clientId: String): Session {
        validateClient(clientId)
        val reply = GitHubHttp.oauth("/login/device/code", form("client_id" to clientId.trim(), "scope" to "gist"))
        if (reply.has("error")) throw SocialException(oauthError(reply.optString("error")))
        val expires = reply.optInt("expires_in", 900).coerceIn(1, 3600)
        val verification = reply.optString("verification_uri")
        if (verification != "https://github.com/login/device") throw SocialException("Lien de connexion GitHub inattendu.")
        val device = reply.optString("device_code")
        val user = reply.optString("user_code")
        if (device.isBlank() || user.isBlank()) throw SocialException("GitHub n'a pas fourni de code de connexion.")
        return Session(device, user, verification, expires, reply.optInt("interval", 5).coerceAtLeast(5),
            SystemClock.elapsedRealtime() + expires * 1000L)
    }

    /** Cancellable polling observes GitHub's interval and slow_down; caller stores the result via connect(). */
    suspend fun awaitToken(clientId: String, session: Session): String {
        validateClient(clientId)
        var interval = session.interval
        while (SystemClock.elapsedRealtime() < session.expiresAtElapsed) {
            currentCoroutineContext().ensureActive()
            delay(interval * 1000L)
            if (SystemClock.elapsedRealtime() >= session.expiresAtElapsed) break
            val reply = GitHubHttp.oauth("/login/oauth/access_token", form(
                "client_id" to clientId.trim(), "device_code" to session.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            ))
            val token = reply.optString("access_token")
            if (token.isNotBlank()) {
                if ("gist" !in reply.optString("scope").split(',', ' ').filter { it.isNotBlank() })
                    throw SocialException("L'autorisation Gist n'a pas été accordée. Relance la connexion.")
                return token
            }
            when (val error = reply.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval = maxOf(interval + 5, reply.optInt("interval", interval + 5))
                else -> throw SocialException(oauthError(error))
            }
        }
        throw SocialException("Le code a expiré. Lance une nouvelle connexion GitHub.")
    }

    private fun validateClient(clientId: String) {
        require(clientId.trim().matches(Regex("[A-Za-z0-9._-]{4,100}"))) { "Client ID OAuth GitHub invalide." }
    }

    private fun form(vararg pairs: Pair<String, String>): String = pairs.joinToString("&") {
        "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}"
    }

    private fun oauthError(error: String): String = when (error) {
        "expired_token", "token_expired" -> "Le code a expiré. Lance une nouvelle connexion GitHub."
        "access_denied" -> "Connexion GitHub annulée."
        "device_flow_disabled" -> "Active Device Flow dans les réglages de ton application OAuth GitHub."
        "incorrect_client_credentials" -> "Ce Client ID OAuth GitHub n'est pas reconnu."
        else -> "La connexion OAuth GitHub n'a pas abouti. Tu peux utiliser un jeton personnel à la place."
    }
}
