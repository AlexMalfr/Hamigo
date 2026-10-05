package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONObject

/** Only account metadata from GitHub's API belongs here; URLs are always reconstructed. */
data class GitHubIdentity(val login: String, val id: Long? = null) {
    val profileUrl: String get() = profileUrl(login)
    val avatarUrl: String? get() = id?.takeIf { it > 0 }?.let { "https://avatars.githubusercontent.com/u/$it?s=128&v=4" }
    fun toJson(): JSONObject = JSONObject().put("login", login).also { id?.let { value -> it.put("id", value) } }

    companion object {
        private val loginPattern = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
        fun profileUrl(login: String): String {
            require(loginPattern.matches(login)) { "Identifiant GitHub invalide." }
            return "https://github.com/$login"
        }
        fun fromJson(value: JSONObject): GitHubIdentity {
            val login = value.getString("login")
            profileUrl(login)
            val id = if (value.has("id")) {
                val number = value.get("id")
                require(number is Number && number.toDouble().isFinite() && number.toDouble() % 1.0 == 0.0 &&
                    number.toDouble() in 1.0..9_007_199_254_740_991.0) { "Identité GitHub invalide." }
                number.toLong()
            } else null
            return GitHubIdentity(login, id)
        }
        fun fromApi(value: JSONObject?): GitHubIdentity? = value?.let { runCatching { fromJson(it) }.getOrNull() }
    }
}

data class GitHubFriendProfile(val progress: ShareProgress, val identity: GitHubIdentity?)
