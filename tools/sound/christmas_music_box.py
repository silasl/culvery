#!/usr/bin/env python3
"""Writes Culvery's Christmas music box (18 §3): a music box playing the opening line of We Wish You a Merry Christmas, as a 22.05 kHz 16-bit mono WAV.

Standard library only. The tune is traditional; the recording is Culvery's own, under the repository's licence.
Usage, from the repo root: python tools/sound/christmas_music_box.py
Writes: core/sound/src/main/res/raw/christmas_music_box.wav
"""
import math
import pathlib
import struct
import wave

RATE = 22050
BPM = 150
BEAT = 60.0 / BPM
LENGTH = 4.8
FADE = 0.4  # seconds, ending at LENGTH
# Bright partials with fast decay, like a music box comb.
PARTIALS = [(1.0, 1.0, 4.0), (3.0, 0.35, 9.0), (6.0, 0.15, 14.0)]  # (ratio, level, decay per second)
ATTACK = 0.002  # seconds
# (frequency in Hz, length in beats): D G G A G F# E E, an octave above D4.
TUNE = [
    (587.32, 1), (784.0, 1), (784.0, 0.5), (880.0, 0.5),
    (784.0, 0.5), (739.98, 0.5), (659.26, 1), (659.26, 2),
]
HEADROOM = 0.95
# Level the door chime reaches over its first 3 s (RMS, 16-bit units) times 1.25: every sound is as loud as the chime, not louder.
TARGET_RMS = 6970.0
RMS_SECONDS = 3.5
OUT = pathlib.Path(__file__).resolve().parents[2] / "core" / "sound" / "src" / "main" / "res" / "raw" / "christmas_music_box.wav"


def schedule():
    """(frequency, start in seconds) for each note."""
    at, notes = 0.0, []
    for freq, beats in TUNE:
        notes.append((freq, at))
        at += beats * BEAT
    return notes


NOTES = schedule()


def sample(t):
    value = 0.0
    for freq, start in NOTES:
        if t >= start:
            u = t - start
            ramp = min(1.0, u / ATTACK)
            for ratio, level, decay in PARTIALS:
                value += level * ramp * math.sin(2 * math.pi * freq * ratio * u) * math.exp(-decay * u)
    return value


def main():
    frames = []
    for i in range(int(LENGTH * RATE)):
        t = i / RATE
        fade = 0.5 * (1 + math.cos(math.pi * min(1.0, (t - (LENGTH - FADE)) / FADE))) if t > LENGTH - FADE else 1.0
        frames.append(sample(t) * fade)
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
