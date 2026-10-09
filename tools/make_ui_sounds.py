"""Original Hamigo foley, reproducible without recordings or a tone generator.

Noise excites damped resonators and Karplus-Strong strings. Mechanical switch
contacts, wooden knocks, elastic springs and a little receiver hiss form the
palette. No external sample, speech service or copyrighted sound is used.
Run with Python + NumPy; pass an output directory (normally app/src/main/res/raw).
"""
import json
import math
import sys
import wave
from pathlib import Path
import numpy as np

RATE = 32000
rng = np.random.default_rng(4409)


def lowpass(x, cutoff):
    a = 1 - math.exp(-2 * math.pi * cutoff / RATE)
    result = np.empty_like(x)
    previous = 0.0
    for i, sample in enumerate(x):
        previous += a * (sample - previous)
        result[i] = previous
    return result


def contact(duration=.085):
    # Two imperfect contacts with a softly damped plastic enclosure.
    n = int(RATE * duration)
    t = np.arange(n) / RATE
    noise = lowpass(rng.normal(size=n), 2200)
    envelope = np.exp(-t / .008) + .37 * np.exp(-np.maximum(0, t - .013) / .004) * (t >= .013)
    body = lowpass(rng.normal(size=n), 450) * np.exp(-t / .02)
    return .6 * noise * envelope + .8 * body


def string(freq, duration, damping=.996, softness=1800):
    # A noise impulse travels around a damped string delay line; no oscillator.
    length = max(8, round(RATE / freq))
    ring = lowpass(rng.uniform(-1, 1, length), softness)
    ring -= ring.mean()
    n = int(duration * RATE)
    result = np.empty(n)
    for i in range(n):
        slot = i % length
        value = ring[slot]
        ring[slot] = damping * (value + ring[(slot + 1) % length]) * .5
        result[i] = value
    t = np.arange(n) / RATE
    result *= np.minimum(1, t / .003) * np.minimum(1, (duration - t) / .03)
    return result


def mix(duration, parts):
    result = np.zeros(int(duration * RATE))
    for time, data, level in parts:
        start = int(time * RATE)
        end = min(len(result), start + len(data))
        result[start:end] += data[:end-start] * level
    result -= result.mean()
    fade = min(400, len(result) // 4)
    result[:fade] *= np.linspace(0, 1, fade)
    result[-fade:] *= np.linspace(1, 0, fade)
    return result


def spring(duration=.31):
    # A changing elastic delay makes a tiny cartoon wobble, not a sine sweep.
    ring = rng.uniform(-1, 1, 160)
    result = np.zeros(int(duration * RATE))
    for i in range(len(result)):
        t = i / RATE
        length = int(65 + 62 * (t / duration) + 7 * math.sin(t * 23))
        slot = i % length
        result[i] = ring[slot]
        ring[slot] = .992 * (ring[slot] + ring[(slot + 1) % length]) * .5
    return lowpass(result, 2200) * np.minimum(1, np.arange(len(result)) / 96)


def palette():
    click = mix(.08, [(0, contact(.08), 1)])
    success = mix(.51, [(0, contact(), .16), (0, string(587.33, .45), 1), (.105, string(880, .4), .9)])
    error = mix(.28, [(0, string(146.83, .24, .976, 650), 1.2), (.075, contact(.12), .28), (.08, string(130.81, .2, .974, 600), .9)])
    complete = mix(.98, [(0, contact(), .18)] + [(i * .13, string(f, .78-i*.1, .998, 2700), .78) for i, f in enumerate([587.33, 739.99, 880, 1174.66])])
    finish = mix(.54, [(0, contact(), .12), (0, string(440, .49, .994, 1400), .8), (.1, string(587.33, .43, .994, 1400), .55)])
    pico = mix(.34, [(0, spring(), .85), (.03, contact(.12), .1)])
    return {"ui_click": (click, .19), "ui_success": (success, .48), "ui_error": (error, .34), "ui_complete": (complete, .46), "ui_finish": (finish, .38), "ui_pico": (pico, .32)}


if __name__ == "__main__":
    destination = Path(sys.argv[1])
    destination.mkdir(parents=True, exist_ok=True)
    report = {}
    for name, (data, peak) in palette().items():
        data *= peak / max(np.max(np.abs(data)), 1e-6)
        pcm = np.round(data * 32767).astype("<i2")
        with wave.open(str(destination / (name + ".wav")), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(pcm.tobytes())
        report[name] = {"duration_ms": round(len(data)/RATE*1000), "peak": round(float(np.max(np.abs(data))), 3), "rms": round(float(np.sqrt(np.mean(data**2))), 4)}
    print(json.dumps(report, indent=2))
