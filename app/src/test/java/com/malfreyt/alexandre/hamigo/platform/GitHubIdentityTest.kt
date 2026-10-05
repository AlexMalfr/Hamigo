package com.malfreyt.alexandre.hamigo.platform

import org.junit.Assert.*
import org.junit.Test

class GitHubIdentityTest {
    @Test fun profileAndAvatarUseFixedOriginsAndNumericAccountId() {
        val identity=GitHubIdentity("some-user",12345L)
        assertEquals("https://github.com/some-user",identity.profileUrl)
        assertEquals("https://avatars.githubusercontent.com/u/12345?s=128&v=4",identity.avatarUrl)
        assertNull(GitHubIdentity("some-user").avatarUrl)
        assertNull(GitHubIdentity("some-user",-2L).avatarUrl)
    }
    @Test fun cannotTurnLoginIntoAnotherOriginOrPath() {
        for(login in listOf("https://evil.test","a/b","a?redirect=x","a#x","a@evil.test","a\\b","","-name","name-","x".repeat(40))) {
            assertThrows(IllegalArgumentException::class.java) { GitHubIdentity.profileUrl(login) }
        }
        assertEquals("https://github.com/A",GitHubIdentity.profileUrl("A"))
    }
}
