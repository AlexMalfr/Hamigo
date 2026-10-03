package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class SocialException(message: String, val httpStatus: Int? = null) : IOException(message)
data class GitHubIdentity(val login: String)
data class GistSnapshot(val id: String, val url: String, val progress: ShareProgress)

/** Opt-in snapshot publishing. A secret Gist is unlisted, and readable by anyone with its link. */
class GitHubSync(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE)
    val tokens = SecureTokenStore(context)
    val savedGistUrl: String? get() = prefs.getString("ownGistUrl", null)
    val lastSyncedAt: String? get() = prefs.getString("lastSyncedAt", null)

    /** Validate account identity before replacing credentials, so account changes cannot overwrite another Gist. */
    suspend fun connect(token: String): GitHubIdentity = writeLock.withLock {
        val clean = token.trim()
        require(clean.length in 10..4096 && clean.none { it.isWhitespace() || it.isISOControl() }) {
            "Le jeton GitHub n'a pas un format valide."
        }
        val identity = identity(clean)
        if (prefs.getString("ownerLogin", null) != identity.login) prefs.edit().clear().commit()
        tokens.store(clean)
        prefs.edit().putString("ownerLogin", identity.login).apply()
        identity
    }

    fun disconnect() {
        tokens.delete()
        prefs.edit().clear().apply()
    }

    /** Returns a stable link; creates once or updates the existing secret hamigo-progress.json Gist. */
    suspend fun push(progress: ShareProgress): GistSnapshot = writeLock.withLock {
        val token = tokens.get() ?: throw SocialException("Connecte ton compte GitHub pour publier ta progression.")
        val owner = identity(token).login
        if (prefs.getString("ownerLogin", null) != owner) {
            prefs.edit().clear().putString("ownerLogin", owner).commit()
        }
        var existing: JSONObject? = null
        prefs.getString("ownGistId", null)?.let { saved ->
            if (ID_PATTERN.matches(saved)) {
                try {
                    val candidate = JSONObject(GitHubHttp.api("GET", "/gists/$saved", token))
                    if (matchesOwn(candidate, owner)) existing = candidate
                } catch (e: SocialException) { if (e.httpStatus != 404) throw e }
            }
        }
        if (existing == null) existing = findExisting(token, owner)
        val safe = ShareProgress.fromJson(progress.toJson())
        val body = JSONObject()
            .put("description", "Hamigo • ma progression radioamateur")
            .put("files", JSONObject().put(FILE_NAME, JSONObject().put("content", safe.toJson())))
        val current = existing
        val response = if (current != null) {
            GitHubHttp.api("PATCH", "/gists/${current.getString("id")}", token, body.toString())
        } else {
            body.put("public", false)
            GitHubHttp.api("POST", "/gists", token, body.toString())
        }
        val result = JSONObject(response)
        val id = result.getString("id")
        require(ID_PATTERN.matches(id)) { "GitHub a renvoyé un identifiant inattendu." }
        val url = result.optString("html_url").takeIf { it.startsWith("https://gist.github.com/") }
            ?: "https://gist.github.com/$id"
        prefs.edit().putString("ownGistId", id).putString("ownGistUrl", url)
            .putString("ownerLogin", owner).putString("lastSyncedAt", Instant.now().toString()).apply()
        GistSnapshot(id, url, safe)
    }

    /** Friend reads are deliberately unauthenticated: no token is sent through a supplied URL. */
    suspend fun read(gistUrlOrId: String): ShareProgress {
        val id = gistId(gistUrlOrId)
        val gist = JSONObject(GitHubHttp.api("GET", "/gists/$id"))
        val file = gist.optJSONObject("files")?.optJSONObject(FILE_NAME)
            ?: throw SocialException("Ce lien ne contient pas de progression Hamigo.")
        if (file.optBoolean("truncated", false)) throw SocialException("Le fichier de progression est trop volumineux.")
        val content = file.optString("content")
        return try { ShareProgress.fromJson(content) } catch (e: IllegalArgumentException) {
            throw SocialException(e.message ?: "Progression Hamigo invalide.")
        }
    }

    private suspend fun identity(token: String): GitHubIdentity {
        val user = JSONObject(GitHubHttp.api("GET", "/user", token))
        val login = user.optString("login")
        if (login.isBlank()) throw SocialException("Impossible de reconnaître le compte GitHub.")
        return GitHubIdentity(login)
    }

    private suspend fun findExisting(token: String, owner: String): JSONObject? {
        for (page in 1..10) {
            val list = JSONArray(GitHubHttp.api("GET", "/gists?per_page=100&page=$page", token))
            for (index in 0 until list.length()) {
                val gist = list.getJSONObject(index)
                if (matchesOwn(gist, owner)) return gist
            }
            if (list.length() < 100) return null
        }
        throw SocialException("Plus de 1 000 Gists : associe d'abord ta progression existante pour éviter un doublon.")
    }

    private fun matchesOwn(gist: JSONObject, owner: String): Boolean =
        !gist.optBoolean("public", true) && gist.optJSONObject("files")?.has(FILE_NAME) == true &&
            gist.optJSONObject("owner")?.optString("login")?.equals(owner, ignoreCase = true) == true &&
            ID_PATTERN.matches(gist.optString("id"))

    companion object {
        const val FILE_NAME = "hamigo-progress.json"
        private val ID_PATTERN = Regex("[a-fA-F0-9]{5,64}")
        private val writeLock = Mutex()

        fun gistId(value: String): String {
            val clean = value.trim()
            if (ID_PATTERN.matches(clean)) return clean
            val uri = Uri.parse(clean)
            require(uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
                (uri.host == "gist.github.com" || uri.host == "api.github.com")) {
                "Colle le lien HTTPS d'un Gist GitHub ou son identifiant."
            }
            val parts = uri.pathSegments
            val id = when {
                uri.host == "api.github.com" && parts.size == 2 && parts.first() == "gists" -> parts.last()
                uri.host == "gist.github.com" && parts.size in 1..2 -> parts.last()
                else -> ""
            }
            require(ID_PATTERN.matches(id)) { "Identifiant de Gist GitHub invalide." }
            return id
        }
    }
}

/** Fixed GitHub hosts, finite timeouts, bounded responses, and no credential-bearing redirects. */
internal object GitHubHttp {
    suspend fun api(method: String, path: String, token: String? = null, body: String? = null): String =
        request("https://api.github.com$path", method, token, body, "application/json")

    suspend fun oauth(path: String, form: String): JSONObject = JSONObject(
        request("https://github.com$path", "POST", null, form, "application/x-www-form-urlencoded")
    )

    private suspend fun request(url: String, method: String, token: String?, body: String?, contentType: String): String =
        withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive()
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.requestMethod = method
                connection.setRequestProperty("Accept", if (token != null || url.contains("api.github.com"))
                    "application/vnd.github+json" else "application/json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                connection.setRequestProperty("User-Agent", "Hamigo-Android")
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                body?.let {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "$contentType; charset=utf-8")
                    val bytes = it.toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { output -> output.write(bytes) }
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val message = when (status) {
                        401 -> "Le jeton GitHub est invalide ou a expiré. Reconnecte ton compte."
                        403 -> if (connection.getHeaderField("X-RateLimit-Remaining") == "0")
                            "La limite GitHub est atteinte. Réessaie plus tard."
                        else "GitHub refuse l'accès. Vérifie l'autorisation Gists en écriture de ton jeton."
                        404 -> "Gist introuvable. Vérifie le lien ou crée une nouvelle connexion."
                        422 -> "GitHub n'a pas accepté la progression. Réessaie plus tard."
                        429 -> "Trop de demandes GitHub. Réessaie plus tard."
                        in 500..599 -> "GitHub est momentanément indisponible. Tes révisions restent enregistrées."
                        else -> "La connexion GitHub a échoué (HTTP $status)."
                    }
                    throw SocialException(message, status)
                }
                val bytes = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes.size() + count > 2 * 1024 * 1024)
                            throw SocialException("La réponse GitHub est trop volumineuse.")
                        bytes.write(buffer, 0, count)
                    }
                }
                currentCoroutineContext().ensureActive()
                bytes.toString("UTF-8")
            } catch (e: SocialException) {
                throw e
            } catch (_: IOException) {
                throw SocialException("Connexion impossible. Vérifie ton réseau ; tes révisions sont conservées sur l'appareil.")
            } finally {
                connection.disconnect()
            }
        }
}
