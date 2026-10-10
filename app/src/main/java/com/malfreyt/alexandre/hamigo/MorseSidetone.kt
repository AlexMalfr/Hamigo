package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.CopyOnWriteArraySet

internal interface MorseLiveOutput {
    fun key(down: Boolean)
    fun signal(symbol: Char)
    fun stop()
    fun close()
}

/** One prepared, silent stream while a visible key is usable. No new track per press. */
internal class MorseSidetone : MorseLiveOutput {
    private val envelope = MorseLiveEnvelope()
    @Volatile private var track: AudioTrack? = null
    @Volatile private var closed = false
    @Volatile internal var ready = false; private set
    private val worker = Thread({ stream() }, "Hamigo-Morse").apply { isDaemon = true }
    internal val running get() = worker.isAlive
    init { outputs.add(this); worker.start() }

    @Synchronized override fun key(down: Boolean) {
        if (closed) return
        if (!down) envelope.key(false)
        else if (!FeedbackAudioGate.busy || FeedbackAudioGate.ownedBy(this)) {
            stopMorsePlayback(); FeedbackAudioGate.reserve(this); envelope.key(true)
        }
    }
    @Synchronized override fun signal(symbol: Char) {
        if (!closed && (symbol == '.' || symbol == '-') && (!FeedbackAudioGate.busy || FeedbackAudioGate.ownedBy(this))) {
            stopMorsePlayback(); FeedbackAudioGate.reserve(this); envelope.signal(symbol)
        }
    }
    @Synchronized override fun stop() { envelope.stop(); FeedbackAudioGate.release(this) }
    @Synchronized override fun close() {
        if (closed) return
        closed = true; stop(); outputs.remove(this)
        // Pause unblocks a blocking write; release belongs to the audio thread.
        track?.let { runCatching { it.pause(); it.flush() } }
        worker.interrupt()
    }
    private fun stream() {
        var current: AudioTrack? = null
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(envelope.rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val minimum = AudioTrack.getMinBufferSize(envelope.rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            current = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(format).setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(maxOf(minimum, envelope.rate / 100 * 2)).build()
            check(current.state == AudioTrack.STATE_INITIALIZED)
            synchronized(this) {
                if (closed) return
                track = current; current.play(); ready = true
            }
            val buffer = ShortArray(envelope.rate / 200) // 5 ms; blocking writes pace this thread.
            while (!closed) {
                synchronized(this) {
                    envelope.render(buffer)
                    if (!envelope.active) FeedbackAudioGate.release(this)
                }
                var offset = 0
                while (offset < buffer.size && !closed) {
                    val written = current.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_BLOCKING)
                    check(written > 0); offset += written
                }
            }
        } catch (_: Exception) { /* No audio device must never block Morse input. */ }
        finally {
            synchronized(this) { closed = true; ready = false; envelope.stop(); track = null }
            FeedbackAudioGate.release(this); outputs.remove(this)
            current?.let { runCatching { it.stop() }; runCatching { it.release() } }
        }
    }
    companion object {
        private val outputs = CopyOnWriteArraySet<MorseSidetone>()
        fun stopOthers(owner: Any? = null) { outputs.forEach { if (it !== owner) it.stop() } }
    }
}

// A platform boundary lets native gesture tests observe key-down/up without acoustic guesses.
internal val LocalMorseLiveFactory = staticCompositionLocalOf<() -> MorseLiveOutput> { { MorseSidetone() } }

@Composable internal fun rememberMorseLiveOutput(enabled: Boolean): MorseLiveOutput? {
    val context = LocalContext.current
    var output by remember { mutableStateOf<MorseLiveOutput?>(null) }
    val prefs = remember(context) { context.getSharedPreferences("hamigo", Context.MODE_PRIVATE) }
    var sound by remember(prefs) { mutableStateOf(FeedbackPreferences.read(prefs).sound) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == FeedbackPreferences.SOUND) {
                sound = FeedbackPreferences.read(prefs).sound
                if (!sound) output?.close()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var lifecycleGeneration by remember(lifecycle) { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        var wasResumed = resumed
        val observer = LifecycleEventObserver { _, _ ->
            val next = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (next != wasResumed) {
                lifecycleGeneration++; wasResumed = next; resumed = next
                if (!next) output?.close() // Stop immediately, before the next recomposition.
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val factory = LocalMorseLiveFactory.current
    DisposableEffect(enabled && sound && resumed, lifecycleGeneration, factory) {
        val current = if (enabled && sound && resumed) factory() else null
        output = current
        onDispose { current?.close(); if (output === current) output = null }
    }
    return output
}
