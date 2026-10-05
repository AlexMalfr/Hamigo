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
import java.io.IOException
import java.time.Instant
import java.util.UUID

open class SocialException(message: String, val httpStatus: Int? = null, cause: Throwable? = null) : IOException(message, cause)
class GitHubNetworkException(message: String, cause: IOException) : SocialException(message, cause = cause)

interface GitHubGateway {
    suspend fun api(method: String, path: String, token: String? = null, body: String? = null): String
    suspend fun rawBackup(rawUrl: String, owner: String, gist: String, fileName: String): String
}
data class GitHubIdentity(val login: String)
data class GistSnapshot(val id: String, val url: String, val progress: ShareProgress)
data class SyncReport(val social: GistSnapshot, val restored: Boolean, val lastSyncedAt: String)

/** Opt-in snapshot publishing. A secret Gist is unlisted, and readable by anyone with its link. */
class GitHubSync(context: Context, private val gateway: GitHubGateway = GitHubHttp) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE)
    val tokens = SecureTokenStore(context)
    /** Local, canonical browser links only; reading these never discovers or creates a Gist. */
    val savedGistUrl: String? get() = savedGistPage("ownGistId")
        ?: prefs.getString("ownGistUrl", null)?.let { runCatching { gistPageUrl(it) }.getOrNull() }
    val savedBackupUrl: String? get() = savedGistPage("ownBackupId")
    val lastSyncedAt: String? get() = prefs.getString("lastSyncedAt", null)
    val accountLogin: String? get() = prefs.getString("ownerLogin", null)
    val lastSyncError: String? get() = prefs.getString("lastSyncError", null)

    private fun savedGistPage(key: String): String? = prefs.getString(key, null)
        ?.takeIf { ID_PATTERN.matches(it) }?.let { gistPageUrl(it) }

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
                val gist = JSONObject(gateway.api("GET", "/gists/${candidate.getString("id")}", token))
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
                gateway.api("POST", "/gists", token, body.toString())
            } else gateway.api("PATCH", "/gists/${existing.getString("id")}", token, body.toString())
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
                    val candidate = JSONObject(gateway.api("GET", "/gists/$saved", token))
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
            gateway.api("PATCH", "/gists/${current.getString("id")}", token, body.toString())
        } else {
            body.put("public", false)
            gateway.api("POST", "/gists", token, body.toString())
        }
        val result = JSONObject(response)
        ensureConnected(token)
        val id = result.getString("id")
        require(ID_PATTERN.matches(id)) { "GitHub a renvoyé un identifiant inattendu." }
        val url = gistPageUrl(id)
        prefs.edit().putString("ownGistId", id).putString("ownGistUrl", url)
            .putString("ownerLogin", owner).putString("lastSyncedAt", Instant.now().toString()).apply()
        return GistSnapshot(id, url, safe)
    }

    /** A supplied URL is never requested; only a validated ID reaches the fixed GitHub API host. */
    suspend fun read(gistUrlOrId: String): ShareProgress {
        val id = gistId(gistUrlOrId)
        val gist = JSONObject(gateway.api("GET", "/gists/$id", tokens.get()))
        val file = gist.optJSONObject("files")?.optJSONObject(FILE_NAME)
            ?: throw SocialException("Ce lien ne contient pas de progression Hamigo.")
        if (file.optBoolean("truncated", false)) throw SocialException("Le fichier de progression est trop volumineux.")
        val content = file.optString("content")
        return try { ShareProgress.fromJson(content) } catch (e: IllegalArgumentException) {
            throw SocialException(e.message ?: "Progression Hamigo invalide.")
        }
    }

    private suspend fun identity(token: String): GitHubIdentity {
        val user = JSONObject(gateway.api("GET", "/user", token))
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
            val list = JSONArray(gateway.api("GET", "/gists?per_page=100&page=$page", token))
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
                gateway.rawBackup(file.getString("raw_url"), owner, gist.getString("id"), name)
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

        /** Incoming URLs supply an ID, never a browser destination. */
        fun gistPageUrl(value: String): String = "https://gist.github.com/${gistId(value)}"

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
