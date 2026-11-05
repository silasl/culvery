#!/usr/bin/env python3
"""Writes Culvery's Happy Birthday (18 §3): a music box playing the tune once through, in C at 3/4, as a 22.05 kHz 16-bit mono WAV.

Standard library only. The tune is traditional; the recording is Culvery's own, under the repository's licence.
Usage, from the repo root: python tools/sound/happy_birthday.py
Writes: core/sound/src/main/res/raw/happy_birthday.wav
"""
import math
import pathlib
import struct
import wave

RATE = 22050
BPM = 110
BEAT = 60.0 / BPM
TAIL = 0.6  # seconds of ring after the last note's beats
# Bright partials with fast decay, like a music box comb.
PARTIALS = [(1.0, 1.0, 3.0), (2.0, 0.5, 6.0), (3.0, 0.3, 9.0), (5.0, 0.15, 14.0)]  # (ratio, level, decay per second)
ATTACK = 0.002  # seconds
# (note, length in beats): each line is a pickup of one beat, then five more, six in all.
TUNE = [
    ("G4", 0.75), ("G4", 0.25), ("A4", 1), ("G4", 1), ("C5", 1), ("B4", 2),
    ("G4", 0.75), ("G4", 0.25), ("A4", 1), ("G4", 1), ("D5", 1), ("C5", 2),
    ("G4", 0.75), ("G4", 0.25), ("G5", 1), ("E5", 1), ("C5", 1), ("B4", 1), ("A4", 1),
    ("F5", 0.75), ("F5", 0.25), ("E5", 1), ("C5", 1), ("D5", 1), ("C5", 2),
]
LENGTH = sum(beats for _, beats in TUNE) * BEAT + TAIL
HEADROOM = 0.95
# Level the door chime reaches over its first 3 s (RMS, 16-bit units) times 1.25: every sound is as loud as the chime, not louder.
TARGET_RMS = 6970.0
RMS_SECONDS = 3.5
OUT = pathlib.Path(__file__).resolve().parents[2] / "core" / "sound" / "src" / "main" / "res" / "raw" / "happy_birthday.wav"
SEMITONES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def frequency(name):
    """Equal temperament, A4 = 440 Hz: "G4", "C#4"."""
    semitone = SEMITONES[name[0]] + name.count("#") + 12 * (int(name[-1]) + 1) - 69
    return 440.0 * 2 ** (semitone / 12)


def schedule():
    """(frequency, start in seconds) for each note."""
    at, notes = 0.0, []
    for name, beats in TUNE:
        notes.append((frequency(name), at))
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
