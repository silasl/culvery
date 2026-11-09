#!/usr/bin/env python3
"""Writes Culvery's door chime (5 §4.1): a bright two-tone bell, E5 then C5, struck twice, as a 22.05 kHz 16-bit mono WAV.

Standard library only. Culvery's own sound, under the repository's licence: no third-party audio.
Usage, from the repo root: python tools/sound/door_chime.py
Writes: core/sound/src/main/res/raw/door_chime.wav
"""
import math
import pathlib

from wav_out import write_peak

RATE = 22050
LENGTH = 3.2
# Per-second decay of each partial: higher partials fade faster, so the strike is sharp and the tail stays clean.
PARTIALS = [(1.0, 1.0, 3.2), (2.0, 0.6, 4.0), (3.0, 0.4, 5.0), (4.2, 0.3, 6.5), (5.4, 0.15, 8.0)]  # (ratio, level, decay)
ATTACK = 0.002  # seconds
# (frequency in Hz, start in seconds): the ding-dong, then again.
NOTES = [(659.25, 0.0), (523.25, 0.45), (659.25, 1.7), (523.25, 2.15)]
HEADROOM = 0.95
OUT = pathlib.Path(__file__).resolve().parents[2] / "core" / "sound" / "src" / "main" / "res" / "raw" / "door_chime.wav"


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
