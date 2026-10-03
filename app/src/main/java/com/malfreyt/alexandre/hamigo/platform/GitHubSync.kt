package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.net.Uri
import com.malfreyt.alexandre.hamigo.Progress
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
import java.util.UUID

class SocialException(message: String, val httpStatus: Int? = null) : IOException(message)
data class GitHubIdentity(val login: String)
data class GistSnapshot(val id: String, val url: String, val progress: ShareProgress)
data class SyncReport(val social: GistSnapshot, val restored: Boolean, val lastSyncedAt: String)

/** Opt-in snapshot publishing. A secret Gist is unlisted, and readable by anyone with its link. */
class GitHubSync(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE)
    val tokens = SecureTokenStore(context)
    val savedGistUrl: String? get() = prefs.getString("ownGistUrl", null)
    val lastSyncedAt: String? get() = prefs.getString("lastSyncedAt", null)
    val accountLogin: String? get() = prefs.getString("ownerLogin", null)
    val lastSyncError: String? get() = prefs.getString("lastSyncError", null)

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
        ProgressSyncScheduler.cancel(appContext)
        tokens.delete()
        prefs.edit().clear().apply()
    }

    /** Full account restore/merge and publication, serialized against foreground and background sync. */
    suspend fun synchronize(progress: Progress): SyncReport = writeLock.withLock {
        val token = tokens.get() ?: throw SocialException("Connecte ton compte GitHub pour synchroniser ta progression.")
        try {
            val owner = ensureOwner(token)
            val device = prefs.getString("installationId", null) ?: UUID.randomUUID().toString().also {
                prefs.edit().putString("installationId", it).commit()
            }
            // Every installation writes its own file. Concurrent PATCHes cannot remove another device's events.
            val candidates = findExisting(token, owner, BACKUP_FILE_NAME)
            require(candidates.size <= 8) { "Plus de huit sauvegardes Hamigo ont été trouvées sur ce compte. Tes données restent conservées sur l'appareil." }
            var restored = false
            var existing: JSONObject? = null
            for (candidate in candidates) {
                val gist = JSONObject(GitHubHttp.api("GET", "/gists/${candidate.getString("id")}", token))
                if (!matchesOwn(gist, owner, BACKUP_FILE_NAME)) continue
                if (existing == null) existing = gist
                for (json in backupContents(gist, owner)) {
                    synchronized(Progress.CLOUD_LOCK) {
                        progress.reload()
                        if (progress.mergeCloud(json)) restored = true
                    }
                }
            }
            val snapshot = synchronized(Progress.CLOUD_LOCK) { progress.reload(); progress.cloudExport() }
            CloudProgress.validate(snapshot)
            ensureConnected(token)
            val body = JSONObject().put("description", "Hamigo • sauvegarde complète de mon apprentissage (non répertoriée)")
                .put("files", JSONObject()
                    .put(BACKUP_FILE_NAME, JSONObject().put("content", snapshot))
                    .put("hamigo-device-$device.json", JSONObject().put("content", snapshot)))
            val response = if (existing == null) {
                body.put("public", false)
                GitHubHttp.api("POST", "/gists", token, body.toString())
            } else GitHubHttp.api("PATCH", "/gists/${existing.getString("id")}", token, body.toString())
            val saved = JSONObject(response)
            val backupId = saved.getString("id")
            require(ID_PATTERN.matches(backupId))
            ensureConnected(token)
            prefs.edit().putString("ownBackupId", backupId).apply()
            // Merge the response too: it may already include a concurrent device file added during our request.
            for (json in backupContents(saved, owner)) {
                synchronized(Progress.CLOUD_LOCK) {
                    progress.reload()
                    if (progress.mergeCloud(json)) restored = true
                }
            }
            val social = publish(synchronized(Progress.CLOUD_LOCK) { progress.reload(); progress.snapshot() }, token, owner)
            val now = Instant.now().toString()
            prefs.edit().putString("lastSyncedAt", now).remove("lastSyncError").apply()
            SyncReport(social, restored, now)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (tokens.get() != null) prefs.edit().putString("lastSyncError", e.message ?: "Synchronisation indisponible.").apply()
            throw e
        }
    }

    /** Returns a stable link; creates once or updates the existing secret hamigo-progress.json Gist. */
    suspend fun push(progress: ShareProgress): GistSnapshot = writeLock.withLock {
        val token = tokens.get() ?: throw SocialException("Connecte ton compte GitHub pour publier ta progression.")
        publish(progress, token, ensureOwner(token))
    }

    private suspend fun publish(progress: ShareProgress, token: String, owner: String): GistSnapshot {
        var existing: JSONObject? = null
        prefs.getString("ownGistId", null)?.let { saved ->
            if (ID_PATTERN.matches(saved)) {
                try {
                    val candidate = JSONObject(GitHubHttp.api("GET", "/gists/$saved", token))
                    if (matchesOwn(candidate, owner, FILE_NAME)) existing = candidate
                } catch (e: SocialException) { if (e.httpStatus != 404) throw e }
            }
        }
        if (existing == null) existing = findExisting(token, owner, FILE_NAME).firstOrNull()
        val safe = ShareProgress.fromJson(progress.toJson())
        val body = JSONObject()
            .put("description", "Hamigo • ma progression radioamateur")
            .put("files", JSONObject().put(FILE_NAME, JSONObject().put("content", safe.toJson())))
        val current = existing
        ensureConnected(token)
        val response = if (current != null) {
            GitHubHttp.api("PATCH", "/gists/${current.getString("id")}", token, body.toString())
        } else {
            body.put("public", false)
            GitHubHttp.api("POST", "/gists", token, body.toString())
        }
        val result = JSONObject(response)
        ensureConnected(token)
        val id = result.getString("id")
        require(ID_PATTERN.matches(id)) { "GitHub a renvoyé un identifiant inattendu." }
        val url = result.optString("html_url").takeIf { it.startsWith("https://gist.github.com/") }
            ?: "https://gist.github.com/$id"
        prefs.edit().putString("ownGistId", id).putString("ownGistUrl", url)
            .putString("ownerLogin", owner).putString("lastSyncedAt", Instant.now().toString()).apply()
        return GistSnapshot(id, url, safe)
    }

    /** A supplied URL is never requested; only a validated ID reaches the fixed GitHub API host. */
    suspend fun read(gistUrlOrId: String): ShareProgress {
        val id = gistId(gistUrlOrId)
        val gist = JSONObject(GitHubHttp.api("GET", "/gists/$id", tokens.get()))
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

    private suspend fun ensureOwner(token: String): String {
        val owner = identity(token).login
        ensureConnected(token)
        if (prefs.getString("ownerLogin", null) != owner) prefs.edit().clear().putString("ownerLogin", owner).commit()
        return owner
    }

    private fun ensureConnected(token: String) {
        if (tokens.get() != token) throw SocialException("La connexion GitHub a changé. Réessaie avec le compte actuel.")
    }

    private suspend fun findExisting(token: String, owner: String, fileName: String): List<JSONObject> {
        val matches = mutableListOf<JSONObject>()
        for (page in 1..10) {
            val list = JSONArray(GitHubHttp.api("GET", "/gists?per_page=100&page=$page", token))
            for (index in 0 until list.length()) {
                val gist = list.getJSONObject(index)
                if (matchesOwn(gist, owner, fileName)) matches += gist
            }
            if (list.length() < 100) return matches.sortedWith(compareBy({ it.optString("created_at") }, { it.optString("id") }))
        }
        throw SocialException("Ton compte contient plus de 1 000 Gists. La recherche de sauvegarde est trop longue ; tes données restent sur le téléphone.")
    }

    private suspend fun backupContents(gist: JSONObject, owner: String): List<String> {
        val files = gist.optJSONObject("files") ?: return emptyList()
        val names = files.keys().asSequence().filter { it == BACKUP_FILE_NAME || DEVICE_FILE_PATTERN.matches(it) }.sorted().toList()
        require(names.size <= 64) { "La sauvegarde dépasse 63 appareils. Tes données locales restent conservées." }
        return names.map { name ->
            val file = files.getJSONObject(name)
            val content = if (file.optBoolean("truncated", false)) {
                GitHubHttp.rawBackup(file.getString("raw_url"), owner, gist.getString("id"), name)
            } else file.getString("content")
            try { CloudProgress.validate(content) } catch (e: Exception) {
                throw SocialException("Une sauvegarde GitHub est invalide. Tes données locales sont conservées : ${e.message}")
            }
        }
    }

    private fun matchesOwn(gist: JSONObject, owner: String, fileName: String): Boolean =
        !gist.optBoolean("public", true) && gist.optJSONObject("files")?.has(fileName) == true &&
            gist.optJSONObject("owner")?.optString("login")?.equals(owner, ignoreCase = true) == true &&
            ID_PATTERN.matches(gist.optString("id"))

    companion object {
        const val FILE_NAME = "hamigo-progress.json"
        const val BACKUP_FILE_NAME = "hamigo-backup.json"
        private val ID_PATTERN = Regex("[a-fA-F0-9]{5,64}")
        private val DEVICE_FILE_PATTERN = Regex("hamigo-device-[a-fA-F0-9-]{36}\\.json")
        private val writeLock = Mutex()

        fun gistId(value: String): String {
            val clean = value.trim()
            if (ID_PATTERN.matches(clean)) return clean.lowercase(java.util.Locale.ROOT)
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
            return id.lowercase(java.util.Locale.ROOT)
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

    /** Large authenticated-owner backups may be truncated in API JSON. Raw reads carry no credential. */
    suspend fun rawBackup(rawUrl: String, owner: String, gist: String, fileName: String): String {
        val uri = Uri.parse(rawUrl)
        val parts = uri.pathSegments
        require(uri.scheme == "https" && uri.host == "gist.githubusercontent.com" && uri.userInfo == null &&
            uri.port == -1 && uri.query == null && uri.fragment == null && parts.size == 5 &&
            parts[0].equals(owner, true) && parts[1].equals(gist, true) && parts[2] == "raw" &&
            Regex("[a-fA-F0-9]{40,64}").matches(parts[3]) && parts[4] == fileName) { "Adresse de sauvegarde GitHub inattendue." }
        return request("https://gist.githubusercontent.com/$owner/$gist/raw/${parts[3]}/$fileName", "GET", null, null,
            "application/json", 8 * 1024 * 1024)
    }

    private suspend fun request(url: String, method: String, token: String?, body: String?, contentType: String,
        maxResponseBytes: Int = 20 * 1024 * 1024): String =
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
                        if (bytes.size() + count > maxResponseBytes)
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
