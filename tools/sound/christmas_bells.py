#!/usr/bin/env python3
"""Writes Culvery's Christmas bells (18 §3): bright bells ringing the first line of a sleigh-ride chorus over a sleigh-bell shimmer, as a 22.05 kHz 16-bit mono WAV.

Standard library only. The tune is traditional; the recording is Culvery's own, under the repository's licence.
Usage, from the repo root: python tools/sound/christmas_bells.py
Writes: core/sound/src/main/res/raw/christmas_bells.wav
"""
import math
import pathlib
import random
import struct
import wave

RATE = 22050
BPM = 140
BEAT = 60.0 / BPM
LENGTH = 7.4
FADE = 0.4  # seconds, ending at LENGTH
# The door chime's bell: higher partials fade faster, so the strike is sharp and the tail stays clean.
PARTIALS = [(1.0, 1.0, 3.2), (2.0, 0.6, 4.0), (3.0, 0.4, 5.0), (4.2, 0.3, 6.5), (5.4, 0.15, 8.0)]  # (ratio, level, decay per second)
ATTACK = 0.002  # seconds
# (frequency in Hz, length in beats): E E E, E E E, E G C D, E held.
TUNE = [
    (659.25, 1), (659.25, 1), (659.25, 2),
    (659.25, 1), (659.25, 1), (659.25, 2),
    (659.25, 1), (783.99, 1), (523.25, 1.5), (587.33, 0.5),
    (659.25, 4),
]
SHIMMER_BEATS = 13  # a shake on every beat from the first note to the start of the last
SHIMMER_LEVEL = 0.25  # against a bell strike of 1
SHIMMER_ACCENT = 1.25  # the first beat of each bar
SHIMMER_HZ = 6000.0  # band centre
SHIMMER_Q = 1.5
SHIMMER_DECAY = 70.0  # per second: a short shake
HEADROOM = 0.95
# Level the door chime reaches over its first 3 s (RMS, 16-bit units) times 1.25: every sound is as loud as the chime, not louder.
TARGET_RMS = 6970.0
RMS_SECONDS = 3.5
OUT = pathlib.Path(__file__).resolve().parents[2] / "core" / "sound" / "src" / "main" / "res" / "raw" / "christmas_bells.wav"


def schedule():
    """(frequency, start in seconds) for each note."""
    at, notes = 0.0, []
    for freq, beats in TUNE:
        notes.append((freq, at))
        at += beats * BEAT
    return notes


NOTES = schedule()


def bells(t):
    value = 0.0
    for freq, start in NOTES:
        if t >= start:
            u = t - start
            ramp = min(1.0, u / ATTACK)
            for ratio, level, decay in PARTIALS:
                value += level * ramp * math.sin(2 * math.pi * freq * ratio * u) * math.exp(-decay * u)
    return value


def shimmer(count):
    """White noise through a band-pass biquad (RBJ cookbook), shaped into one short burst per beat."""
    rng = random.Random(18)
    w0 = 2 * math.pi * SHIMMER_HZ / RATE
    alpha = math.sin(w0) / (2 * SHIMMER_Q)
    b0, b2, a0, a1, a2 = alpha, -alpha, 1 + alpha, -2 * math.cos(w0), 1 - alpha
    x1 = x2 = y1 = y2 = 0.0
    out = []
    for i in range(count):
        t = i / RATE
        beat = int(t / BEAT + 1e-9)
        x0 = rng.uniform(-1.0, 1.0)
        y0 = (b0 * x0 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, x0, y1, y0
        if beat < SHIMMER_BEATS:
            burst = math.exp(-SHIMMER_DECAY * (t - beat * BEAT)) * (SHIMMER_ACCENT if beat % 4 == 0 else 1.0)
            out.append(y0 * burst * SHIMMER_LEVEL)
        else:
            out.append(0.0)
    return out


def main():
    count = int(LENGTH * RATE)
    noise = shimmer(count)
    frames = []
    for i in range(count):
        t = i / RATE
        fade = 0.5 * (1 + math.cos(math.pi * min(1.0, (t - (LENGTH - FADE)) / FADE))) if t > LENGTH - FADE else 1.0
        frames.append((bells(t) + noise[i]) * fade)
    peak = max(abs(f) for f in frames)
    held = frames[:int(RMS_SECONDS * RATE)]
    gain = min(TARGET_RMS / math.sqrt(sum(f * f for f in held) / len(held)), HEADROOM * 32767 / peak)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(f * gain)) for f in frames))


if __name__ == "__main__":
    main()
