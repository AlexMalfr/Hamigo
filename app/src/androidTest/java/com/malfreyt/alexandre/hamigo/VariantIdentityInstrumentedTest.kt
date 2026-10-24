package com.malfreyt.alexandre.hamigo

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.FriendInvite
import com.malfreyt.alexandre.hamigo.platform.GitHubPkce
import org.junit.Assert.*
import org.junit.Test

class VariantIdentityInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun resolves(url: String): Boolean = context.packageManager.resolveActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName),
        PackageManager.MATCH_DEFAULT_ONLY) != null

    @Test fun devIdentityHasOwnDataProviderLabelAndDeepLinks() {
        assertEquals("dev", BuildConfig.CHANNEL)
        assertEquals("com.malfreyt.alexandre.hamigo.dev", context.packageName)
        assertEquals("Hamigo Dev", context.applicationInfo.loadLabel(context.packageManager).toString())
        val provider = context.packageManager.resolveContentProvider("${context.packageName}.files", 0)!!
        assertEquals(context.packageName, provider.packageName)
        assertTrue(resolves(GitHubPkce.CALLBACK))
        assertTrue(resolves("hamigo-dev://return"))
        assertFalse(resolves("hamigo://return"))
        assertFalse(resolves("https://alexmalfr.github.io/hamigo/oauth/"))
        assertTrue(GitHubPkce.isCallback(GitHubPkce.CALLBACK))
        assertFalse(GitHubPkce.isCallback("https://alexmalfr.github.io/hamigo/oauth/"))
    }

    @Test fun invitationsAndReturnsCannotBeConsumedByTheOtherIdentity() {
        val invite = FriendInvite.link("abcde12345")
        assertEquals("https://alexmalfr.github.io/hamigo-dev/?invite=abcde12345", invite)
        assertEquals("abcde12345", FriendInvite.parse(invite))
        assertEquals("abcde12345", FriendInvite.parse("hamigo-dev://join?invite=abcde12345"))
        assertNull(FriendInvite.parse("https://alexmalfr.github.io/hamigo/?invite=abcde12345"))
        assertNull(FriendInvite.parse("hamigo://join?invite=abcde12345"))
        assertTrue(HamigoLinks.isFeedbackReturn("hamigo-dev://return"))
        assertFalse(HamigoLinks.isFeedbackReturn("hamigo://return"))
    }
}
