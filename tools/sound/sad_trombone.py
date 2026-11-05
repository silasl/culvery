#!/usr/bin/env python3
"""Writes Culvery's sad trombone (18 §3): a brass tone falling D4, C#4, C4, then B3 held with a slow vibrato, as a 22.05 kHz 16-bit mono WAV.

Standard library only. Culvery's own sound, under the repository's licence: no third-party audio.
Usage, from the repo root: python tools/sound/sad_trombone.py
Writes: core/sound/src/main/res/raw/sad_trombone.wav
"""
import math
import pathlib
import struct
import wave

RATE = 22050
LENGTH = 3.2
# Odd and even harmonics; from the 3rd up they swell over WAH seconds at the start of each note.
HARMONICS = [(n, 1.0 / n ** 0.8) for n in range(1, 9)]  # (harmonic, level)
WAH = 0.1  # seconds
WAH_START = 0.15  # share of its level an upper harmonic starts at
ATTACK = 0.03  # seconds
RELEASE = 0.03  # seconds
VIBRATO_HZ = 6.0
VIBRATO_CENTS = 30.0
# (frequency in Hz, start in seconds, length in seconds, vibrato): D4 C#4 C4, then B3 held.
NOTES = [
    (293.66, 0.00, 0.45, False), (277.18, 0.45, 0.45, False), (261.63, 0.90, 0.45, False), (246.94, 1.35, 1.60, True),
]
HEADROOM = 0.95
# Level the door chime reaches over its first 3 s (RMS, 16-bit units) times 1.25: every sound is as loud as the chime, not louder.
TARGET_RMS = 6970.0
RMS_SECONDS = 3.5
OUT = pathlib.Path(__file__).resolve().parents[2] / "core" / "sound" / "src" / "main" / "res" / "raw" / "sad_trombone.wav"


def envelope(u, length, last):
    """The last note holds to its length, then fades out to LENGTH; the others lift just as the next one starts."""
    attack = min(1.0, u / ATTACK)
    if last:
        into = u - length
        fade = LENGTH - NOTES[-1][1] - length
        return attack * (0.5 * (1 + math.cos(math.pi * min(1.0, into / fade))) if into > 0 else 1.0)
    return attack * max(0.0, min(1.0, 1.0 - (u - length) / RELEASE))


def phase(freq, u, vibrato):
    """Radians at u seconds; the vibrato is a swing of VIBRATO_CENTS in frequency, integrated into the phase."""
    turns = freq * u
    if vibrato:
        swing = freq * VIBRATO_CENTS * math.log(2) / 1200.0 / (2 * math.pi * VIBRATO_HZ)
        turns += swing * (1 - math.cos(2 * math.pi * VIBRATO_HZ * u))
    return 2 * math.pi * turns


def sample(t):
    value = 0.0
    for index, (freq, start, length, vibrato) in enumerate(NOTES):
        u = t - start
        if u < 0:
            continue
        amp = envelope(u, length, index == len(NOTES) - 1)
        if amp <= 0.0:
            continue
        swell = WAH_START + (1 - WAH_START) * math.sin(0.5 * math.pi * min(1.0, u / WAH))
        p = phase(freq, u, vibrato)
        for n, level in HARMONICS:
            value += amp * level * (swell if n >= 3 else 1.0) * math.sin(n * p)
    return value


def main():
    frames = [sample(i / RATE) for i in range(int(LENGTH * RATE))]
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
