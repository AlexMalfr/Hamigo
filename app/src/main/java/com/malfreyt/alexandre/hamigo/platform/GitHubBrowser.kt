package com.malfreyt.alexandre.hamigo.platform

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.malfreyt.alexandre.hamigo.MainActivity

internal sealed interface GitHubBrowserCommand {
    class Open(val session: DeviceOAuth.Session) : GitHubBrowserCommand
    data object Close : GitHubBrowserCommand
}

/** Owned by the ViewModel so a recreated host still knows whether the browser covered it. */
internal class GitHubBrowserState {
    var open = false
    var hostPaused = false
}

/** Uses the default browser's ordinary cookie jar, never an ephemeral or embedded WebView. */
internal class GitHubBrowser(private val activity: Activity, private val state: GitHubBrowserState) {

    fun open(session: DeviceOAuth.Session) {
        copyGitHubCode(activity, session.userCode, notify = false)
        val intent = intentFor(activity, session)
        state.hostPaused = false
        state.open = true
        try { activity.startActivity(intent) }
        catch (e: Exception) { state.open = false; throw e }
    }

    fun onHostPause() { if (state.open) state.hostPaused = true }
    fun onHostResume() { if (state.hostPaused) { state.open = false; state.hostPaused = false } }

    /** CLEAR_TOP removes the temporary browser activity from Hamigo's existing task. */
    fun close() {
        if (!state.open || activity.isFinishing || activity.isDestroyed) return
        state.open = false
        activity.startActivity(returnIntent(activity))
    }

    companion object {
        internal fun intentFor(context: Context, session: DeviceOAuth.Session): Intent {
            require(session.verificationUri == "https://github.com/login/device")
            val copy = PendingIntent.getBroadcast(context, 91,
                Intent(context, GitHubCodeReceiver::class.java)
                    .setAction(GitHubCodeReceiver.ACTION)
                    .putExtra("user_code", session.userCode)
                    .putExtra("expires_elapsed", session.expiresAtElapsed),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val tab = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_START)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(0xFFE1F2EF.toInt()).build())
                .setActionButton(copyIcon(), "Copier le code GitHub", copy, true)
                .build()
            // A generic web URL finds the browser rather than a GitHub deep-link handler.
            val browser = context.packageManager.resolveActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
                    .addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.takeUnless { it == "android" }
            tab.intent.setPackage(browser)
            tab.intent.data = Uri.parse(session.verificationUri)
            return tab.intent
        }

        internal fun returnIntent(context: Context) = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        private fun copyIcon(): Bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF174C51.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f
            }
            Canvas(this).apply {
                drawRoundRect(8f, 7f, 21f, 22f, 1.5f, 1.5f, paint)
                drawLine(4f, 17f, 4f, 2f, paint)
                drawLine(4f, 2f, 17f, 2f, paint)
            }
        }
    }
}

/** Eight significant characters also work with segmented fields that don't strip separators. */
fun copyGitHubCode(context: Context, code: String, notify: Boolean = true) {
    val value = code.replace("-", "").trim()
    if (!value.matches(Regex("[A-Za-z0-9]{8}"))) return
    val clip = ClipData.newPlainText("Code GitHub Hamigo", value)
    if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (notify) Toast.makeText(context, "Code GitHub copié.", Toast.LENGTH_SHORT).show()
}

class GitHubCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || SystemClock.elapsedRealtime() >= intent.getLongExtra("expires_elapsed", 0)) return
        copyGitHubCode(context, intent.getStringExtra("user_code") ?: return)
    }
    companion object { internal const val ACTION = "com.malfreyt.alexandre.hamigo.COPY_GITHUB_CODE" }
}
