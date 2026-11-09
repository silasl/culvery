#!/usr/bin/env python3
"""Writes Culvery's haunted organ doorbell (14 §4.6): an organ playing a minor-key descending phrase, once, as a 22.05 kHz 16-bit mono WAV.

Standard library only. Culvery's own sound, under the repository's licence: no third-party audio.
Usage, from the repo root: python tools/sound/door_organ.py
Writes: core/sound/src/main/res/raw/door_organ.wav
"""
import math
import pathlib

from wav_out import write_loud

RATE = 22050
LENGTH = 4.0
# The 2nd to 4th harmonics carry the tune on a small speaker; the sub-octave and the 6th only colour it.
PARTIALS = [(0.5, 0.30), (1.0, 0.55), (2.0, 1.0), (3.0, 0.80), (4.0, 0.50), (6.0, 0.20)]  # (ratio, level)
ATTACK = 0.02  # seconds
RELEASE = 0.03  # seconds
FADE = 0.3  # seconds, ending at LENGTH
TREMOLO_HZ = 5.5
TREMOLO_DEPTH = 0.04
# (frequency in Hz, start in seconds, length in seconds): A5 G5 A5, then the descent to D5.
NOTES = [
    (880.00, 0.00, 0.14), (783.99, 0.14, 0.12), (880.00, 0.26, 0.86),
    (783.99, 1.30, 0.14), (698.46, 1.44, 0.14), (659.25, 1.58, 0.14), (587.33, 1.72, 0.30), (554.37, 2.02, 0.40), (587.33, 2.42, 1.20),
]
HEADROOM = 0.95
# Level the door chime reaches over its first 3 s (RMS, 16-bit units) times 1.25: the organ is as loud as the chime, not louder.
TARGET_RMS = 6970.0
RMS_SECONDS = 3.5
OUT = pathlib.Path(__file__).resolve().parents[2] / "core" / "sound" / "src" / "main" / "res" / "raw" / "door_organ.wav"


def envelope(u, length, last):
    """Organ keys sound at full level while held, so each note is a short attack and release; the last one holds into the closing fade."""
    attack = min(1.0, u / ATTACK)
    if last:
        into = u + NOTES[-1][1] - (LENGTH - FADE)
        return attack * ((0.5 * (1 + math.cos(math.pi * min(1.0, into / FADE)))) ** 2 if into > 0 else 1.0)
    return attack * max(0.0, min(1.0, 1.0 - (u - length) / RELEASE))


def sample(t):
    value = 0.0
    for index, (freq, start, length) in enumerate(NOTES):
        u = t - start
        if u < 0:
            continue
        amp = envelope(u, length, index == len(NOTES) - 1)
        if amp <= 0.0:
            continue
        for ratio, level in PARTIALS:
            value += level * amp * math.sin(2 * math.pi * freq * ratio * u)
    return value * (1 - TREMOLO_DEPTH + TREMOLO_DEPTH * math.sin(2 * math.pi * TREMOLO_HZ * t))


def main():
    frames = [sample(i / RATE) for i in range(int(LENGTH * RATE))]
    write_loud(OUT, frames, RATE, TARGET_RMS, RMS_SECONDS, HEADROOM)


if __name__ == "__main__":
    main()
