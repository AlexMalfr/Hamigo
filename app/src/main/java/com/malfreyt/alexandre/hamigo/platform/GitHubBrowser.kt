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
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.widget.Toast
import android.widget.RemoteViews
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.malfreyt.alexandre.hamigo.MainActivity
import com.malfreyt.alexandre.hamigo.R

internal sealed interface GitHubBrowserCommand {
    class Open(val session: DeviceOAuth.Session) : GitHubBrowserCommand
    data object Close : GitHubBrowserCommand
}

/** Owned by the ViewModel; resuming Hamigo does not imply the browser was dismissed. */
internal class GitHubBrowserState {
    var open = false
    var launchesAwaitingResult = 0
}

/** Uses the default browser's ordinary cookie jar, never an ephemeral or embedded WebView. */
internal class GitHubBrowser(private val activity: Activity, private val state: GitHubBrowserState,
    private val launch: (Intent) -> Unit) {

    fun open(session: DeviceOAuth.Session) {
        copyGitHubCode(activity, session.userCode, notify = false)
        val intent = intentFor(activity, session)
        val previouslyOpen = state.open
        state.launchesAwaitingResult++
        state.open = true
        try { launch(intent) }
        catch (e: Exception) {
            state.launchesAwaitingResult--
            state.open = previouslyOpen
            throw e
        }
    }

    fun onTabResult() {
        state.launchesAwaitingResult = (state.launchesAwaitingResult - 1).coerceAtLeast(0)
        if (state.launchesAwaitingResult == 0) state.open = false
    }

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
                    .setData(Uri.parse("hamigo-internal://github-code/${session.browserRequestId}"))
                    .putExtra("user_code", session.userCode)
                    .putExtra("expires_elapsed", session.expiresAtElapsed),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val tab = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_START)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(0xFFE1F2EF.toInt()).build())
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_LIGHT)
                .setActionButton(codeActionIcon(context, session.userCode),
                    "Code ${session.userCode}, copier le code GitHub", copy, false)
                .addMenuItem("Copier ${session.userCode}", copy)
                .setSecondaryToolbarViews(RemoteViews(context.packageName, R.layout.github_code_toolbar).apply {
                    setTextViewText(R.id.github_device_code, session.userCode)
                    setContentDescription(R.id.github_copy_code, "Copier le code GitHub ${session.userCode}")
                }, intArrayOf(R.id.github_device_code, R.id.github_copy_code), copy)
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

        /** Custom Tabs limits the top action to 48 × 24 dp: two readable four-character rows. */
        internal fun codeActionIcon(context: Context, code: String): Bitmap {
            val density = context.resources.displayMetrics.density
            return Bitmap.createBitmap((48 * density).toInt(), (24 * density).toInt(), Bitmap.Config.ARGB_8888).apply {
                Canvas(this).apply {
                    scale(density, density)
                    val value = code.filter { it.isLetterOrDigit() }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF174C51.toInt(); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        textSize = 11f
                    }
                    drawText(value.take(4), 0f, 10f, paint)
                    drawText(value.drop(4).take(4), 0f, 23f, paint)
                    paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f
                    drawRoundRect(35f, 8f, 45f, 20f, 1f, 1f, paint)
                    drawLine(32f, 17f, 32f, 5f, paint)
                    drawLine(32f, 5f, 42f, 5f, paint)
                }
            }
        }
    }
}

/** GitHub distributes a native paste across its fields; keyboard IME insertion is different. */
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
