# Mustang Micro Plus: measuring output level

`scripts/mmp_level.py` checks what a preset change does to the amp's output, by
recording the amp's USB output while the preset is auditioned on a reserved slot
(default 100). Nothing is written to the amp, and it ends on the original preset.

## What the USB recording carries

* **The amp's output, but only while its plug is in an instrument.** Unplugged, the
  USB recording is exact digital silence, with or without the noise gate.
* **Not USB playback.** Audio played to the amp over USB is heard in the headphones
  but does not pass the amp model and does not come back over USB. So the computer
  cannot feed a test signal that way.
* **A constant line at 2666.7 Hz (48000/18) and its harmonics,** in every recording. It
  is probably USB or display interference. The analysis notches it out.

## Noise method (works with the guitar connected, volume at zero)

The test preset has the amp's noise gate off (`gatePreset: "off"`), so the amp's own
input noise passes every stage. Its level follows everything after the input.

**Level changes within a preset are exact.** 80S Rock, 2026-11-10, measured twice
per offset:

| Amp volume | Measured | Expected |
| --- | --- | --- |
| +3 dB | +3.1 dB | +3.0 |
| +6 dB (volume +1 dB: above 0 dB is accepted) | +5.9 dB | +6.0 |
| −3 dB | −2.7 dB | −3.0 |
| −6 dB | −5.8 dB | −6.0 |

The spread between the two measurements was at most 0.1 dB, and the reference drifted
+0.13 dB over the whole test. `mmp_level.py delta PRESET.json` repeats this check and
fails on more than 1 dB error.

**Across presets the noise method is wrong for matching loudness.** Tiny noise does
not drive a distorting amp or a compressor the way a guitar does. The survey of all 71
factory presets (`mmp_level.py survey`) spans 59 dB: Sustaind −114.6 dBFS, Jazz
Clean −102.3, Metal 2000 −58.0, Early Eddie −55.7 dBFS. Matching on that would turn
Metal 2000 down by 31.6 dB. Fender's own volumes (Metal 2000 −9.8 dB, Jazz Clean
−14.0 dB) show the factory presets were balanced far more closely by ear. The
survey file records `"method": "noise"` for this reason.

## Signal method (needs a cable, not tested yet)

A test tone from the computer's headphone output (`--signal-sink <PipeWire sink>`)
goes into a 6.35 mm mono socket that the amp is plugged into, via an adapter cable
with a 3.5 mm stereo plug and a 6.35 mm socket. The tone is guitar-like: decaying
plucked notes with harmonics, at −20 dBFS RMS. Every preset then gets a real playing
signal, so `survey` gives levels that can be matched.

## Reliability

Now and then an audition is not confirmed. In one survey slot 26 hung; otherwise it
happened about once in 25, independent of the preset (all sizes from 9 to 12 frames
went through). `AuditionController.audition` sends it again, up to three times.
