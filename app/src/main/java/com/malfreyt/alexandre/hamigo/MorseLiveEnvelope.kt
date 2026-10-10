package com.malfreyt.alexandre.hamigo

import kotlin.math.sin

/** Sample-clock timing: live keying has no playback preamble; presets stay 1/3 units. */
internal class MorseLiveEnvelope(val rate: Int = 48000) {
    private var held = false
    private val pulses = ArrayDeque<Int>()
    private var remaining = 0
    private var gap = 0
    private var level = 0.0
    private var phase = 0.0
    private val ramp = rate * 6 / 1000
    @Synchronized fun key(down: Boolean) { held = down }
    @Synchronized fun signal(symbol: Char) {
        if ((symbol == '.' || symbol == '-') && pulses.size < 32)
            pulses.addLast(rate * MorseSignalAudio.DOT_MS / 1000 * if (symbol == '.') 1 else 3)
    }
    @Synchronized fun stop() { held = false; pulses.clear(); remaining = 0; gap = 0 }
    @get:Synchronized val active get() = held || remaining > 0 || pulses.isNotEmpty() || level > 0.0
    @Synchronized fun render(into: ShortArray) {
        into.indices.forEach { i ->
            if (remaining == 0 && gap == 0 && pulses.isNotEmpty()) remaining = pulses.removeFirst()
            val on = held || remaining > 0
            level = (level + if (on) 1.0 / ramp else -1.0 / ramp).coerceIn(0.0, 1.0)
            into[i] = (sin(phase) * 7000 * level).toInt().toShort()
            phase = (phase + 2 * Math.PI * 700 / rate) % (2 * Math.PI)
            if (remaining > 0 && --remaining == 0) gap = rate * MorseSignalAudio.DOT_MS / 1000
            else if (gap > 0) gap--
        }
    }
}
