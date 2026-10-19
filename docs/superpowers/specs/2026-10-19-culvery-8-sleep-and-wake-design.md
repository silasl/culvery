# Culvery 8: Sleep and wake

Brainstormed 2026-10-19. Follow-ups file: `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("Feature ideas", the Sleep and wake line).

Branch `plan-8-sleep-wake`, from `main` at 9990fee, in the worktree `culvery-sleep`.

## 1. Purpose and outcome

Today Culvery keeps the screen on all the time (`FLAG_KEEP_SCREEN_ON` in `MainActivity`), at whatever brightness the tablet is set to. On the wall that means:

- **at night** the screen lights up the room when everyone is in bed (the bigger problem);
- **in the day** it is on when nobody is looking, using power and wearing the screen.

Sleep and wake fixes both:

- **Night:** a dim clock, only the time, between set hours.
- **Day:** the screen goes dark when nobody has been around for a while, and wakes as someone walks up, seen by the front camera.
- **Awake:** brightness follows the time of day, softer from sunset.
- **Heat:** Culvery records when the tablet gets warm or throttles, so the camera's cost can be measured and the tablet's old "stuck at minimum brightness" episodes explained.

Done means: all of the above works on the household's SM-T510, the camera's power and heat measured there against today's always-on screen (§7), a final review, a local merge, and the build installed on the tablet.

**The tablet has no light sensor.** The SM-T510's only sensors are the accelerometer, its tilt and motion detectors and a Wi-Fi grip sensor (checked with `dumpsys sensorservice`, 2026-10-19). Brightness therefore follows the time of day, not the room.

## 2. Decisions (the user, 2026-10-19)

1. **The problem:** both night and day, night the bigger part.
2. **Night shows a dim clock:** only the time, large and very dim on black. Nothing else (no date, no doorbell dot).
3. **Night is set times** in Settings, default 22:00 to 07:00.
4. **At night only a tap wakes the wall.** The camera is off at night and never wakes it.
5. **Daytime presence comes from the front camera**, checked on the tablet only.
6. **Daytime sleep is a dark screen** after **5 minutes** with nobody around (adjustable in Settings). Walking up, or a tap, wakes it.
7. **The doorbell:** in the day it wakes the screen and shows the door card as usual. At night it is recorded in the Door tab only: the wall stays on the clock and the chime stays silent.
8. **While awake, brightness follows the time of day:** a daytime level, and a softer level from sunset to sunrise (the sun times Culvery already has for its theme). Both are set in Settings › Display. The softer level also applies when a tap wakes the night clock. (The user first chose "follow the room's light"; the tablet has no light sensor, so the user chose this instead.)
9. **If the tablet measurement shows the camera runs warm or costs noticeable power,** the plan still ships, with camera wake-up **off** by default: the day screen still sleeps, and a tap wakes it.
10. **Heat is recorded:** Culvery logs the tablet's thermal state changes and, now and then, the battery temperature (numbers only).

### Approaches weighed (presence)

- **A. The front camera (chosen):** wakes as someone walks up; a camera on the wall, so privacy is designed in (§4.3).
- **B. A Hue motion sensor near the wall:** no camera, but needs a sensor placed there (the household's are in other rooms).
- **C. No touches for a while:** simple, but nobody would see the time at a glance as they pass.

## 3. Modules

The builder settles names and placement; these are the pieces.

- **`DisplayState`** (in the shell, `app` or a small `core` module): the one place that decides what the screen is doing: **Awake**, **Asleep** (day, dark) or **NightClock**. Its inputs are the clock and the night times, presence events, touches (from the existing `ShellUserActivity`), whether Culvery is in front, whether a sheet, the PIN pad or the door card is showing, and doorbell rings.
- **`Presence`**: the camera reader (CameraX `ImageAnalysis` or Camera2, the builder's call), reporting "someone moved" events. It runs only when §4.3 allows.
- **`ScreenBrightness`**: the daytime or evening level by the sun times (`ThemeSchedule` already has them), applied to Culvery's own window.
- **`HeatLog`**: thermal status changes from `PowerManager` (`addThermalStatusListener`, API 29+) and the battery temperature from the battery broadcast, logged as numbers (§4.7).
- **The night clock screen** and the **dark screen**: full-screen layers in the shell, above Home and its overlays, below the PIN pad.
- **Settings › Display:** a new Settings page (Admin only, like the rest of Settings), placed before Music.

## 4. How it works

### 4.1 The states

| From | Happens | To |
|---|---|---|
| Awake (day) | 5 min with no presence and no touch, no sheet/PIN pad/door card open | Asleep |
| Asleep | presence, or a tap | Awake |
| Asleep | the doorbell rings (day) | Awake, door card shows |
| any | night starts | NightClock |
| NightClock | a tap | Awake (night) |
| Awake (night) | 1 min with no touch, no sheet/PIN pad open | NightClock *(the 1 minute is my call)* |
| NightClock | the doorbell rings | NightClock; the ring is recorded in the Door tab only |
| NightClock or Awake (night) | night ends | Awake (day) |

- A tap that wakes the screen only wakes it: it is not passed on to whatever lies under it. *(My call: a sleepy tap shouldn't press a button.)*
- While Culvery is not in front (the son using other apps), none of this runs: no camera, Android's own screen timeout and brightness apply.
- The PIN pad, an open sheet, the door card, Settings and the setup wizard keep the wall awake. They follow their own existing timeouts. *(Settings and the wizard are my call: an Admin mid-edit shouldn't lose the screen, and Settings already closes itself when left.)*

### 4.2 Dark, not off

"Asleep" is a black screen with the window's backlight at its lowest (`WindowManager.LayoutParams.screenBrightness` at its minimum), `FLAG_KEEP_SCREEN_ON` kept. *(My call.)* Truly switching the screen off would let Android lock the tablet, leave screen pinning and stop the camera, so walking up couldn't wake it.

The night clock uses the same low window brightness, with the time in a dim colour on black. The builder sets the exact brightness and colour so it is readable at arm's length in a dark room and lights nothing else; both are provisional until the user's design walk-through. The SM-T510 is an LCD, so black still glows a little at the lowest backlight. If the walk-through finds the night clock still lights the room, the first lever is a dimmer clock colour. Changing Android's own brightness (`WRITE_SETTINGS`, a system screen opened while pinned) stays out of this plan unless the walk-through says otherwise. *(My call.)*

### 4.3 The camera, privacy first

- On only while the state is **Awake (day)** or **Asleep**, Culvery is in front, and camera wake-up is switched on in Settings. Off at night, in other apps, and when switched off.
- Low resolution and the lowest frame rate the camera offers; Culvery looks for movement about **once a second** (frame differencing on a small greyscale image is enough; the builder chooses). No face detection, no recognition.
- **Nothing leaves the reader:** no frame or image is stored, written to disk, logged, cached or sent. The reader emits only "someone moved" with a timestamp. A test guards that the presence code has no file, network or log path for image data.
- **A small camera mark** shows at the screen's edge whenever the camera is running, because Android 11 shows no indicator. *(My call: a small icon in a corner, provisional for the design walk-through.)*
- The camera permission is asked once, from Settings › Display, by an Admin. Without it, camera wake-up stays off and Settings says why.

### 4.4 Brightness while awake

`ScreenBrightness` sets **Culvery's own window brightness** to the daytime level between sunrise and sunset, and the evening level otherwise, with a gentle change at each switch (the builder's call, for example over a minute). It never changes Android's brightness setting: the tablet is the user's son's, and his own setting (manual, about 80 % today) applies whenever he uses other apps. *(My call.)* Defaults are the builder's, tuned on the tablet; the evening level is low enough not to dazzle when a tap wakes the night clock. Without the household's location (no sun times), the daytime level applies until it's set.

### 4.5 The doorbell

The door alert (Plan 5) already knows quiet hours for the chime. This plan adds:

- **Day, Asleep:** a ring wakes the wall (Awake) and the door card shows as today.
- **Night (NightClock, or Awake at night):** the ring is recorded in the Door tab with its rail badge, as when Culvery isn't in front; no card, no chime, the clock stays. *(Awake at night is my call, to keep "night" one rule.)*

The doorbell's own quiet hours setting stays for the daytime case it already covers; the builder notes any overlap in Settings' copy.

A door card already showing when night starts stays until it closes as usual (Got it or its 2 minutes); the clock follows. *(My call.)*

### 4.7 Heat

`HeatLog` runs while Culvery is alive. It logs one line on each thermal status change (`THERMAL_STATUS_*` as a number or name) and the battery temperature in tenths of a degree every 15 minutes and on each status change. Numbers only, no names. The tablet walk-through reads these to measure the camera (§7), and later, if the screen gets stuck dim again, they show whether heat caused it. Release builds keep these lines (warning level for a status above none, so they survive R8's stripping of info logs; the builder confirms against the release logging rules).

### 4.6 Settings › Display

Admin only.

- **Night clock:** from [time] to [time], default 22:00 to 07:00. The same times can cross midnight.
- **Brightness:** a daytime level and an evening level (from sunset), each a slider.
- **Sleep when nobody's around:** after [minutes], default 5 (choices such as 2, 5, 10, 15).
- **Wake when someone walks up:** a switch, on by default once the camera permission is granted; with it off, the day screen still sleeps after the delay and a tap wakes it.
- The camera permission prompt, and a line when it was refused: how to allow it.

## 5. When things go wrong

| What happens | What Culvery does |
|---|---|
| No camera permission | Camera wake-up off; Settings says so and how to allow it; the day screen sleeps and wakes by tap |
| The camera fails or is taken by another app | Treated as no presence; wakes by tap; one log line with the exception class |
| No sun times yet (no location) | The daytime brightness applies |
| The tablet gets hot | Logged (§4.7); nothing else changes |
| The clock changes (zone, DST) | Night times are wall-clock times in the household's zone, re-checked on each change |

**Privacy.** Logs carry state changes and outcomes only, never image data. Camera frames are never stored or sent (§4.3).

## 6. Tests

- **The state machine** (JVM, fake clock): every row of §4.1 at its boundaries (1 ms before and at 5 minutes, at 22:00, across midnight), the doorbell both ways, sheets and the PIN pad holding it awake, not in front means nothing runs, a waking tap not passed on.
- **Presence:** the reader runs only in the allowed states; frame differencing on synthetic frames; the guard that no image path leads to a file, the network or a log.
- **Brightness:** day and evening levels at sunrise and sunset boundaries; no location; the change applied to the window only.
- **Heat:** a status change and the 15-minute battery reading each give one line, numbers only (`LogHygieneTest` covers it).
- **UI** (Robolectric; Roborazzi for the night clock in a dark room, the camera mark, Settings › Display with and without permission).

## 7. Rollout

- **Emulator:** the states, Settings and the night clock (its camera is a virtual one; presence is checked on the tablet).
- **On the tablet:** a release `install -r`; the camera's wake-up distance and speed; the night clock seen in the dark room; **power and heat measured** over an afternoon against today's always-on screen (the plan states how, using §4.7's lines and the battery's discharge with the charger off for a set time). If the camera runs warm or costs noticeable power, camera wake-up ships off by default (decision 9). Then the logs are read whenever it's plugged in.
- **Not code:** the tablet's own battery-protection setting (Samsung's "Protect battery", charging to 85 %) would keep an always-charging tablet cooler. It's the user's son's tablet, so it's the user's choice; the walk-through mentions it.

## 8. Not in this plan

- The whole-app Holiday mode (it will use Asleep or switch the screen off while away).
- A Hue motion sensor as a presence source (approach B), for later if the camera disappoints.
- Device owner (would allow a true screen-off with re-pinning).
- Anything on the night clock beyond the time.
