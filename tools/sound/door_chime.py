#!/usr/bin/env python3
"""Writes Culvery's door chime (5 §4.1): two soft bell tones, E5 then C5, 1.6 s, as a 22.05 kHz 16-bit mono WAV.

Standard library only. Culvery's own sound, under the repository's licence: no third-party audio.
Usage, from the repo root: python tools/sound/door_chime.py
Writes: capability/door/src/main/res/raw/door_chime.wav
"""
import math
import pathlib
import struct
import wave

RATE = 22050
LENGTH = 1.6
DECAY = 3.0  # per second
# (frequency in Hz, start in seconds)
NOTES = [(659.25, 0.0), (523.25, 0.45)]
OUT = pathlib.Path(__file__).resolve().parents[2] / "capability" / "door" / "src" / "main" / "res" / "raw" / "door_chime.wav"


def sample(t):
    value = 0.0
    for freq, start in NOTES:
        if t >= start:
            u = t - start
            # The note and a quieter octave, fading: a bell rather than a beep.
            value += (math.sin(2 * math.pi * freq * u) + 0.3 * math.sin(4 * math.pi * freq * u)) * math.exp(-DECAY * u)
    return value


def main():
    frames = [sample(i / RATE) for i in range(int(LENGTH * RATE))]
    peak = max(abs(f) for f in frames)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(RATE)
        out.writeframes(b"".join(struct.pack("<h", int(f / peak * 0.8 * 32767)) for f in frames))


if __name__ == "__main__":
    main()
