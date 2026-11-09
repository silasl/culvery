"""Shared output code for the sound generators: scales a list of float frames to 16-bit and writes a mono WAV.

Standard library only; imported by the generators beside it.
"""
import math
import struct
import wave

PEAK = 32767
# Where the limiter starts bending, as a share of the peak cap.
KNEE = 0.6
BISECTIONS = 40


def rms(frames, seconds, rate):
    """RMS of the first `seconds` of `frames`."""
    held = frames[:int(seconds * rate)]
    return math.sqrt(sum(f * f for f in held) / len(held))


def _write(path, ints, rate):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as out:
        out.setnchannels(1)
        out.setsampwidth(2)
        out.setframerate(rate)
        out.writeframes(b"".join(struct.pack("<h", i) for i in ints))


def write_peak(path, frames, rate, headroom):
    """Scales so the loudest frame sits at `headroom` of full scale."""
    peak = max(abs(f) for f in frames)
    _write(path, [int(f / peak * headroom * PEAK) for f in frames], rate)


def _limited(x, cap):
    """Passes `x` up to the knee, then bends smoothly towards `cap`, which it never reaches."""
    knee = KNEE * cap
    over = abs(x) - knee
    if over <= 0:
        return x
    return math.copysign(knee + (cap - knee) * math.tanh(over / (cap - knee)), x)


def write_loud(path, frames, rate, target_rms, rms_seconds, headroom, limit=False):
    """Scales to `target_rms` over the first `rms_seconds`, unless that would put the peak past `headroom`.

    Then the peak wins, or with `limit` the note onsets are limited and the gain raised until `target_rms` is met.
    """
    cap = headroom * PEAK
    peak = max(abs(f) for f in frames)
    gain = target_rms / rms(frames, rms_seconds, rate)
    if limit and gain * peak > cap:
        def level(g):
            return rms([_limited(f * g, cap) for f in frames[:int(rms_seconds * rate)]], rms_seconds, rate)

        low, high = gain, gain
        while level(high) < target_rms:
            high *= 1.5
        for _ in range(BISECTIONS):
            mid = (low + high) / 2
            low, high = (mid, high) if level(mid) < target_rms else (low, mid)
        gain = (low + high) / 2
        scaled = [_limited(f * gain, cap) for f in frames]
    else:
        gain = min(gain, cap / peak)
        scaled = [f * gain for f in frames]
    _write(path, [int(f) for f in scaled], rate)
