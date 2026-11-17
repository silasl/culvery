#!/usr/bin/env python3
"""Writes Culvery's reminder chime: two soft bell notes, G5 then C6, as a 22.05 kHz 16-bit mono WAV.

Standard library only. Culvery's own sound, under the repository's licence: no third-party audio.
Usage, from the repo root: python tools/sound/reminder_chime.py
Writes: core/sound/src/main/res/raw/reminder_chime.wav
"""
import math
import pathlib

from wav_out import write_peak

RATE = 22050
LENGTH = 1.1
# (ratio, level, decay per second): higher partials fade faster.
PARTIALS = [(1.0, 1.0, 4.0), (2.0, 0.5, 6.0), (3.0, 0.25, 8.0)]
ATTACK = 0.002  # seconds
# (frequency in Hz, start in seconds)
NOTES = [(783.99, 0.0), (1046.50, 0.18)]
HEADROOM = 0.95
OUT = pathlib.Path(__file__).resolve().parents[2] / "core" / "sound" / "src" / "main" / "res" / "raw" / "reminder_chime.wav"


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
    frames = [sample(i / RATE) for i in range(int(LENGTH * RATE))]
    write_peak(OUT, frames, RATE, HEADROOM)


if __name__ == "__main__":
    main()
