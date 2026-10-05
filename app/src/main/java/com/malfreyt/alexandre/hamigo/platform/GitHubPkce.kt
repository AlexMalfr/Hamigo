package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** OAuth authorization-code flow for the registered native client, with RFC 7636 S256. */
internal object GitHubPkce {
    const val CALLBACK = "https://alexmalfr.github.io/hamigo/oauth/"
    const val SESSION_TTL_MILLIS = 10 * 60 * 1000L
    private const val MAX_URL_LENGTH = 8192
    private const val MAX_CODE_LENGTH = 512
    private val random = SecureRandom()
    private val noncePattern = Regex("[A-Za-z0-9_-]{32,128}")
    private val verifierPattern = Regex("[A-Za-z0-9._~-]{43,128}")
    private val clientPattern = Regex("[A-Za-z0-9._-]{4,100}")
    private val codePattern = Regex("[A-Za-z0-9._~-]{1,$MAX_CODE_LENGTH}")

    // Deliberately not a data class: persistence must be encrypted, and diagnostics must not
    // print either the state or verifier. Construction also supports a restored session.
    class Session(val state: String, internal val verifier: String, val expiresAtMillis: Long) {
        init {
            require(noncePattern.matches(state) && verifierPattern.matches(verifier) && expiresAtMillis > 0) {
                "Session d’autorisation GitHub invalide."
            }
        }
        override fun toString() = "GitHubPkce.Session(redacted)"
    }

    fun start(nowMillis: Long = System.currentTimeMillis()): Session {
        require(nowMillis in 0..(Long.MAX_VALUE - SESSION_TTL_MILLIS)) { "Date de connexion GitHub invalide." }
        return Session(nonce(), nonce(), nowMillis + SESSION_TTL_MILLIS)
    }

    fun authorizationUrl(clientId: String, session: Session): String {
        requireClient(clientId)
        return "https://github.com/login/oauth/authorize?" + form(
            "client_id" to clientId,
            "redirect_uri" to CALLBACK,
            "scope" to "gist",
            "state" to session.state,
            "code_challenge" to challenge(session.verifier),
            "code_challenge_method" to "S256"
        )
    }

    /** Exact registered origin/path; encoded paths, explicit ports and userinfo are refused. */
    fun isCallback(url: String): Boolean = callbackUri(url) != null

    /** An unrelated or ambiguous callback must never cancel another in-flight login. */
    fun matchesState(url: String, session: Session): Boolean = runCatching {
        val values = callbackValues(url)
        val state = values["state"] ?: return@runCatching false
        noncePattern.matches(state) && MessageDigest.isEqual(
            session.state.toByteArray(Charsets.US_ASCII), state.toByteArray(Charsets.US_ASCII)
        )
    }.getOrDefault(false)

    fun codeFromCallback(url: String, session: Session, nowMillis: Long = System.currentTimeMillis()): String {
        val values = callbackValues(url)
        val state = values["state"]
        if (state == null || !noncePattern.matches(state) || !MessageDigest.isEqual(
                session.state.toByteArray(Charsets.US_ASCII), state.toByteArray(Charsets.US_ASCII)
            )) throw SocialException("Ce retour GitHub ne correspond pas à la connexion en cours.")
        requireActive(session, nowMillis)
        if (values.containsKey("code") == values.containsKey("error")) invalidCallback()
        if (values.containsKey("error")) throw SocialException(errorMessage(values.getValue("error")))
        return values.getValue("code").also { if (!codePattern.matches(it)) invalidCallback() }
    }

    suspend fun exchange(
        clientId: String,
        clientSecret: String,
        session: Session,
        code: String,
        post: suspend (String, String) -> JSONObject = GitHubHttp::oauth
    ): String {
        requireClient(clientId)
        if (clientSecret.length !in 1..512 || clientSecret.any { it.code !in 33..126 }) {
            throw SocialException("La configuration de connexion GitHub est incomplète.")
        }
        requireActive(session, System.currentTimeMillis())
        if (!codePattern.matches(code)) invalidCallback()
        val reply = post("/login/oauth/access_token", form(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "code" to code,
            "redirect_uri" to CALLBACK,
            "code_verifier" to session.verifier
        ))
        if (reply.has("error")) {
            // Do not echo error_description/error_uri or a malformed server body into UI/logs.
            if (reply.has("access_token")) invalidReply()
            throw SocialException(errorMessage(reply.opt("error") as? String ?: ""))
        }
        val token = reply.opt("access_token") as? String ?: invalidReply()
        if (token.length !in 1..4096 || token.any { it.code !in 33..126 }) invalidReply()
        val type = reply.opt("token_type") as? String ?: invalidReply()
        if (!type.equals("bearer", ignoreCase = true)) invalidReply()
        val scope = reply.opt("scope") as? String ?: invalidReply()
        if (scope.length > 4096 || scope.any { it.code !in 32..126 }) invalidReply()
        if ("gist" !in scope.split(Regex("[, ]+")).filter(String::isNotEmpty)) {
            throw SocialException("L’autorisation GitHub doit inclure l’accès aux Gists. Relance la connexion.")
        }
        return token
    }

    /** Pure JVM helper, also tested with the published RFC 7636 Appendix B vector. */
    internal fun challenge(verifier: String): String {
        require(verifierPattern.matches(verifier)) { "Vérificateur PKCE invalide." }
        return base64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    }

    private fun nonce(): String = base64(ByteArray(32).also(random::nextBytes))
    private fun base64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun form(vararg fields: Pair<String, String>): String = fields.joinToString("&") { (key, value) ->
        URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
    }
    private fun requireClient(clientId: String) {
        if (!clientPattern.matches(clientId)) throw SocialException("La configuration de connexion GitHub est incomplète.")
    }
    private fun requireActive(session: Session, nowMillis: Long) {
        // The inferred creation time also rejects a restored far-future expiry or a large clock rewind.
        if (nowMillis < 0 || nowMillis >= session.expiresAtMillis ||
            nowMillis < session.expiresAtMillis - SESSION_TTL_MILLIS) {
            throw SocialException("La connexion GitHub a expiré. Relance-la pour obtenir une nouvelle autorisation.")
        }
    }
    private fun callbackUri(url: String): URI? {
        if (url.length !in 1..MAX_URL_LENGTH || url.any { it.code !in 33..126 }) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        return uri.takeIf {
            it.scheme == "https" && it.rawAuthority == "alexmalfr.github.io" &&
                it.host == "alexmalfr.github.io" && it.port == -1 && it.rawUserInfo == null &&
                it.rawPath == "/hamigo/oauth/" && it.rawFragment == null && !it.isOpaque
        }
    }
    private fun callbackValues(url: String): Map<String, String> {
        val query = callbackUri(url)?.rawQuery ?: invalidCallback()
        if (query.isEmpty()) invalidCallback()
        val result = linkedMapOf<String, String>()
        val parts = query.split('&')
        if (parts.size > 16) invalidCallback()
        for (part in parts) {
            val separator = part.indexOf('=')
            if (separator <= 0) invalidCallback()
            val key = decode(part.substring(0, separator))
            val value = decode(part.substring(separator + 1))
            if (key.length !in 1..64 || key.any { it.code !in 33..126 } ||
                value.length > 4096 || value.any { it.isISOControl() } || result.containsKey(key)) invalidCallback()
            result[key] = value
        }
        return result
    }
    private fun decode(encoded: String): String = try {
        val bytes = ByteArrayOutputStream(encoded.length)
        var index = 0
        while (index < encoded.length) {
            val char = encoded[index]
            when (char) {
                '%' -> {
                    if (index + 2 >= encoded.length) invalidCallback()
                    val high = encoded[index + 1].digitToIntOrNull(16) ?: invalidCallback()
                    val low = encoded[index + 2].digitToIntOrNull(16) ?: invalidCallback()
                    bytes.write(high * 16 + low)
                    index += 3
                }
                '+' -> { bytes.write(' '.code); index++ }
                else -> { bytes.write(char.code); index++ }
            }
        }
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    } catch (_: java.nio.charset.CharacterCodingException) { invalidCallback() }

    private fun errorMessage(error: String): String = when (error) {
        "access_denied" -> "La connexion GitHub a été annulée."
        "bad_verification_code", "expired_token" -> "L’autorisation GitHub a expiré ou a déjà été utilisée. Relance la connexion."
        "incorrect_client_credentials", "redirect_uri_mismatch", "invalid_client" -> "La configuration de connexion GitHub doit être corrigée."
        else -> "GitHub n’a pas accepté cette autorisation. Relance la connexion."
    }
    private fun invalidCallback(): Nothing = throw SocialException("Retour d’autorisation GitHub invalide.")
    private fun invalidReply(): Nothing = throw SocialException("La réponse d’autorisation GitHub est invalide. Relance la connexion.")
}
