# Culvery Plan 19: Power

Brainstormed with the user on 2026-11-06. Follow-ups items 1 and 2, and review finding N12. The November 2026 review (power findings P1–P9, runs B and C) is in the private notes; this spec cites its findings by number. Calls marked **(Claude's call)** were not put to the user; everything else is theirs.

## 1. Intent

The wall uses more power than it needs to. The review measured it on the tablet:

- Polling never stops. The dark screen and the night clock are a black layer over Home, so Home's cards keep the speakers polled every 2 s, and Hue, Google Tasks and Home Assistant followed, all night and through a holiday (P1, P8).
- Camera wake-up costs about 3.1 %/h on battery over a dark screen with the camera closed: about 2.1 %/h for the camera streaming and about 1 %/h for the wakes it causes. Decision 9's bar (8 §2.9) is 2 %/h.
- The camera fails with a device error up to every 10 minutes some afternoons and then stays closed for 5 minutes (P9), so camera wake-up is off for up to a third of that time.
- When a device or the internet is down, the speakers are retried every 2 s with a log line each time (P4), and an overnight outage can stop bins being read for the whole day (P5).
- Hue is read in full every 60 s on fresh connections, and a dead event stream would go unnoticed (P6).

The user's ruling on decision 9: keep camera wake-up on by default and fix its cost. Waking stays at about 1 s: the camera doesn't rest between looks.

Success: with the screen dark or on the night clock, Culvery sends nothing to the speakers, the Hue bridge, Google Tasks or Home Assistant. Each card is fresh within a moment of waking. The camera's failures are logged with their cause and cost minutes, not five. A re-run of runs B and C gives the numbers.

### Goals
- Pollers stop while the screen isn't lit and refresh at once on waking (§3).
- The camera's request is as cheap as the camera allows, and what it got is logged (§4.1).
- The camera's failures are logged with their code and retried sooner (§4.2).
- The camera stops when the wall is left with Use other apps (§4.3, N12).
- Backoff and quiet logs when something is down (§5).
- Bins read after an overnight outage the same day (§6).
- Hue read every 5 minutes, and a silent stream reopened (§7).
- Runs B and C re-run on the tablet (§8.3).

### Non-goals
- The camera resting between looks (the user, 2026-11-06: waking stays at about 1 s).
- A truly dark screen. It needs device owner (P7); the backlight at minimum stays.
- Pausing the internet loops while Android says the Wi-Fi has no working internet (P4 ruling).
- The hidden Home screen's drawing (P3, dropped: measured small).
- Reusing Sonos connections (Claude's call, §3.4).
- Calendar and weather loops. They are cheap and stay as they are.

### Anti-goals (a failure even if it works)
- A doorbell missed or late because the screen was dark. Ring alerts, door pruning and the doorbell's music quieter must keep working while the screen is dark.
- A card showing stale state for more than a moment after waking, with no sign it's being refreshed.
- Holiday mode's timed steps (Eco off at 00:01, the clean the day before) skipped because the screen is dark.
- Camera wake-up woken by nothing: the cheaper request must not make an empty hall wake the screen.
- Camera size, rate or error lines that carry anything but numbers and class names.

## 2. Rulings (the user, 2026-11-06)

1. Camera wake-up stays on by default; its cost is fixed instead (item 1).
2. P1: the pollers wait on a separate "screen lit" signal. Home stays composed while dark, so scroll positions are kept. P3 is dropped.
3. P8 is folded into P1: the Home Assistant socket closes while dark and reopens on waking.
4. P4: backoff on repeated failures, logging only the first failure and the recovery. No pause on an unvalidated network.
5. P5: an attempt that fails because the network or the council's site can't be reached doesn't count toward the day's four. A real failure to read the page still counts.
6. P6: full read every 5 minutes, and the stream reopened after a long silence.
7. N12: the camera stops when the wall is left with Use other apps.
8. Waking stays at about 1 s: no resting between looks.
9. If the re-run still shows more than 2 %/h extra with the camera on, camera wake-up stays on. The result is recorded with its numbers (§8.3).

## 3. The screen lit gate (P1, P8)

### 3.1 The signal
`core/plugin` gets a new signal, "the screen is lit": true while Culvery is in front (`AlertHost.inFront`) and the display state is `Screen.AWAKE`. It is false on the dark screen, on the night clock, through Holiday mode's dark screen, and whenever Culvery isn't in front. The app provides it from `DisplayState.screen`; capabilities only see the interface.

`AlertHost.inFront` keeps its meaning. Ring alerts, the doorbell's music quieter and door pruning still use it, so they work with the screen dark.

### 3.2 What waits on it
Each of these gates on lit instead of `inFront`, together with its existing count of screens watching:
- `MusicChecks` (playback every 2 s, layout every 30 s);
- `LightsFollow` (the event stream and the full read);
- `ShoppingChecks`;
- `HomeAssistant`'s socket (the hoover row and the Plugs tab).

The doorbell's music quieter asks the speakers what's playing when it runs (`playingNow()`), but it asks the group leaders from the layout as last read. With polling stopped, a regroup in the Sonos app while the screen was dark would be missed. So the quieter first reads the layout (`checkLayout()`, as `MusicHouseSeam.stop` already does) whenever the last layout read is more than 30 s old, inside the existing `QUIET_CAP_MS`. **(Claude's call.)** The tablet check (§8.2) times it; if the layout read can't fit the cap, the plan says so and the quieter falls back to the last layout.

One-off calls aren't gated: Holiday mode's timed steps, hoover Start, plug switching, music commands and Hue commands work whatever the screen shows.

### 3.3 On waking
Lit turning true restarts each gated loop at once: the speakers' layout then playback, a Hue full read and the stream, a Tasks check, the Home Assistant socket with its full read. While a card's first answer is on its way it shows its last known state, as today after a restart. **(Claude's call:** stop outright while dark rather than slow Sonos to 30 s, since the cards refresh within a moment of waking.)

### 3.4 Sonos connections
**(Claude's call)** The review suggested reusing Sonos connections. They stay one per call: a speaker drops idle keep-alives within seconds, and reusing one failed checks as unreachable (6572bbf3). With the gate, polling only runs while the screen is lit.

## 4. The camera

### 4.1 Cheaper request, and what the camera chose (P2)
The camera already opens at the smallest YUV size of at least 32×24, at the slowest frame-rate range it offers (`Camera2Frames.configure`). To that:
- On the repeating request, turn off noise reduction, edge enhancement and autofocus, each only where the camera lists the off mode as available. **(Claude's call)**
- When the camera opens, log once at warning level (so release builds show it): the chosen width and height, the frame-rate range, and which of the three modes were turned off. Numbers and mode names only.

The motion test isn't changed. Noise reduction off makes frames noisier, so §8.2's tablet check looks for wakes with nobody there.

### 4.2 Failures (P9)
- Log the cause: `CameraDevice.StateCallback.onError`'s error code, and a session that fails to configure, as numbers and class names. Today all of these log only "CameraStopped".
- Retry sooner after a device or service error (`ERROR_CAMERA_DEVICE`, `ERROR_CAMERA_SERVICE`, a session that fails to configure, the camera disconnected): after 10 s, doubling on each failure in a row up to the present 5 minutes. **(Claude's call:** 10 s and doubling.)
- A camera that's in use, disabled or at its limit (`ERROR_CAMERA_IN_USE`, `ERROR_MAX_CAMERAS_IN_USE`, `ERROR_CAMERA_DISABLED`) keeps the 5-minute wait. No front camera still means no retry.
- The run of failures resets after the camera has run 10 minutes without one.
- Closing after a device error blocked the presence thread for about 1 s on the tablet. That's acceptable; a close must still never wait on the camera's own thread.

### 4.3 Use other apps (N12)
`wantsCamera` also needs the wall not to have been left with Use other apps. Today the camera runs on while Culvery is still in front as the home app. The camera mark already follows the camera, so it goes too.

**(Claude's call)** The camera is off while the wall is unpinned by Use other apps, and back once it pins again. Today the only record is `ShellViewModel.kioskExited`, a view-model field that `DisplayState` can't see. It moves to a singleton that both read, keeping its present lifetime: set by Use other apps, cleared when Culvery returns to the front and re-pins. Like today's field, it's forgotten if the process dies, and the wall then re-pins as at any start.

## 5. Backoff and quiet logs (P4)

When a loop's calls fail several times in a row:
- The wait between tries doubles from the loop's normal interval, up to a cap: **(Claude's call)**
  - the speakers' playback check: 2 s up to 60 s (it's on screen, so it mustn't stay stale long);
  - the speakers' layout read and rescan: 30 s up to 5 minutes;
  - the Tasks check: 60 s up to 5 minutes.
- **(Claude's call)** The speakers' rescan and Hue's search after a failure (`RESCAN_GAP_MS`, `SEARCH_GAP_MS`) keep their present gaps and get only the quiet logging. They run only while a gated loop is failing, so with §3 they run only while the screen is lit. Backing them off would mean new calls on the provider contracts, and a speaker or bridge that moved address overnight could then stay unfound for minutes after waking.
- Opening the Music tab, a Shopping action that asks for a check, or waking (§3.3) resets the backoff and tries at once.
- Logging: the first failure in a run logs at warning, with the class name as now. Later ones in the same run log nothing. The first success after a run logs once at info, with how many failed. Use `FlowRetry.kt`'s helpers where they fit.

The cards' problem lines are unchanged: they show from the first failure, as today.

## 6. Bins after an outage (P5)

In `MidSussexBinsProvider`, a failed attempt counts toward `ATTEMPTS_A_DAY` only when the council's site answered and the page couldn't be read (`BinsReadFailed`). An attempt that failed because the network was down or the site couldn't be reached (`OfflineException`, and the connect and DNS failures that are unreachable today) keeps the hour's gap before the next try but doesn't use up one of the four. So after a night without internet, bins are read within an hour of it coming back. **(Claude's call)** The engine's timeout and any unexpected exception still count, so a site that hangs or a page that breaks the parser is still tried at most four times a day. That means splitting today's single catch, which files all three as unreachable.

## 7. Hue (P6)

- The full read (`READ_ALL_MS`) goes from 60 s to 5 minutes. The event stream already delivers changes.
- **(Claude's call)** Connections keep the present 5 s idle limit. The review suggested keeping them alive past the read, but the bridge closes idle connections, and with no retry a stale one fails the read (`HueClient.kt`'s note on `KEEP_ALIVE_MS`). At one read in 5 minutes, the handshakes cost little.
- The stream is reopened when nothing, events or keep-alive lines, has arrived for 10 minutes while the gate is open. **(Claude's call:** 10 minutes.) The builder first checks in the tablet's debug capture whether the bridge sends anything during a quiet spell. If it sends nothing for long spells, reopening on silence would cycle the stream, so then use twice the longest quiet gap seen, and say so in the plan.

## 8. Checks

### 8.1 Tests
- The lit signal: false on the dark screen, the night clock and Holiday's dark screen, and when not in front; true when awake in front.
- Each gated loop stops when lit goes false and runs again at once when it comes back. Ring alerts and the music quieter still work with lit false.
- The camera: the request's modes set only where available; the size and rate line; each error code's retry delay, the doubling, the cap and the reset; no camera after Use other apps.
- Backoff: the doublings and caps; a reset on a Music tab open, a Shopping ask and waking; one log line per run of failures and one on recovery.
- Bins: offline and unreachable attempts don't count, a failure to read the page does, and the hour's gap holds for both.
- Hue: the 5-minute read; the stream reopened after the silence and not before.
- `LogHygieneTest` passes with the new log lines.

### 8.2 Emulator walk-through, then the tablet
- Emulator: Home and the Music, Lights, Shopping and Plugs tabs fill on waking from the dark screen and from the night clock (debug clock). With the screen dark, the log shows no music, lights, shopping or Home Assistant calls. A doorbell by day still wakes the screen with the door card, and lowers playing music.
- The tablet (release `install -r`):
  - the camera's size, rate and modes line;
  - an hour of the dark screen with nobody there: no camera wakes;
  - walking up still wakes it in about 1 s;
  - a ring while the screen is dark and music plays: how long the quieter's layout read takes against its 500 ms cap;
  - any camera failures are logged with their code, and it's back within seconds.

### 8.3 Runs B and C again
As the review's Part 3 Stage 1, by day before about 15:30 on similar days, the tablet on battery, read from the battery history after each run:
- **B:** camera wake-up off, 2 h, nobody touching the wall.
- **C:** camera wake-up on, 2 h, people walking past as usual.

The user switches camera wake-up in Settings › Display (their Admin PIN), unplugs, and gives the times. The tablet charges on its own charger between runs.

The results:
- B against November's B (4.5 %/h) is what P1 saved.
- C against B is decision 9's drain test, with C split into the camera streaming and the wakes as in Part 3.
- If C is still more than 2 %/h over B, camera wake-up stays on (ruling 9). The numbers go in the follow-ups and in a note under 8 §2.9 in spec 8.

## 9. Left out on purpose
- The camera resting between looks after a quiet spell. It would cut the camera's cost more, but makes waking slower (ruling 8). If ruling 9's case comes up, it's the next idea to try.
- P3, the hidden Home screen's drawing (dropped).
- Pausing internet loops on an unvalidated network (P4 ruling).
- Reusing Sonos connections, and keeping Hue connections alive past the read (§3.4, §7).
- A truly dark screen (device owner).

## 10. Claude's calls, for checking
- Stop the gated loops outright while dark; no slow Sonos poll (§3.3).
- Sonos keeps one connection per call (§3.4).
- Noise reduction, edges and autofocus off where available (§4.1).
- Camera retry: 10 s doubling to 5 minutes after device errors; 5 minutes for in-use, disabled or at-limit; reset after 10 minutes clean (§4.2).
- The doorbell's quieter reads the layout first when it's more than 30 s old, within its 500 ms cap (§3.2).
- Bins: only network-down and unreachable failures are free; timeouts and unexpected errors still count (§6).
- Camera off from Use other apps until the wall pins again, via a singleton in place of the view-model field (§4.3).
- Backoff caps: 60 s for playback, 5 minutes for the rest; the speakers' and Hue's searches keep their gaps (§5).
- Hue keeps its 5 s idle limit; stream reopened after 10 minutes' silence, subject to the capture check (§7).
