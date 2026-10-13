# Culvery 4e: the device walk-through on the SM-T510

Plan: `docs/superpowers/plans/2026-10-12-culvery-04e-device-beta.md` (Tasks 2–10). Spec: `docs/superpowers/specs/2026-10-12-culvery-4e-device-beta-design.md` §3, §4, §6, §7, §8.

The tablet is the son's Samsung Galaxy Tab A 10.1 (SM-T510, Android 11, One UI), signed in to the family Google account. The controller drives it over USB with adb from Git Bash; the user does every physical step and types every PIN and password. Each session is 20–30 minutes and ends with the son's settings as they were and Culvery pinned in front, nobody signed in.

## Rules

### The ground rules (spec §3, verbatim as amended at plan review)

- The user does every physical step (Developer options, cable, reboots, the power cut, the phone) and types every PIN and password. The controller drives over adb.
- **The son's tablet:** the controller never clears another app's data, uninstalls anything but Culvery, or changes a setting a check doesn't need. Every changed setting (font scale, dark mode, the home app, TalkBack and its log level, Wi-Fi) is saved first and put back in the same session, even if the check fails.
- **No household names in logs or the transcript:** people, events, towns and the account are written as [person], [event], [town], [account]. Logs and screenshots are deleted once the session's notes are written; the transcript is checked for names before it is committed.
- **No PIN in a log:** TalkBack is off and the speech log stopped while a real PIN is typed (plan review, 2026-10-12; was: the speech log paused, 4d-2's rule).
- **USB debugging** is off during the family week (the PC's key is kept), on again only for the day 3–4 read and day 7 (plan review, 2026-10-12).
- **Offline** only with Wi-Fi off (`svc wifi disable`, then `enable`); never airplane mode.
- **Install** only signed releases with `adb install -r`. The first install is fresh (no Culvery on the tablet). Never uninstall Culvery once set up without the user's yes: it deletes the household.
- Sessions are 20–30 minutes. TalkBack's session is the shortest possible (the 4d-2 check was wearing).
- **Taste** (look and wording calls) goes to the end-of-v1 walk-through list, not into 4e's fixes.

### How the controller works

- **What the controller reads enters its session.** Tell the user this in Session 1: anything the controller reads (a log line, a screenshot, a UI dump) is in its conversation. So it greps logs and dumps for what a check needs rather than reading them whole, and opens a screenshot only when a check needs the controller's own eyes; otherwise the user looks at the tablet.
- **The serial.** Shell variables don't survive between the controller's commands. Session 1 writes the serial to `$TMP/culvery-4e-serial`; every block starts with `S=$(cat "$TMP/culvery-4e-serial")`.
- **Device paths** (`/sdcard/…`) appear only inside one quoted string after `shell` or `exec-out`: Git Bash rewrites an argument that starts with `/`.
- **Names.** One file for the whole plan, `$TMP/culvery-4e-names.txt`: every person, calendar, event title, town and the account address, one per line. The user gives the full list in Session 1; the controller adds any other name it sees. It is deleted only at the close-out.
- **Saving a setting** (once only: a second run never overwrites the first save). One file per setting, `$TMP/culvery-4e-saved-<name>`:
  ```bash
  S=$(cat "$TMP/culvery-4e-serial")
  f="$TMP/culvery-4e-saved-font_scale"
  [ -e "$f" ] || adb -s "$S" shell settings get system font_scale | tr -d '\r' > "$f"
  cat "$f"
  ```
  An unusual saved value (empty, an error message, a font scale outside 0.8–2, anything not shaped like the setting) is a stop and ask before changing anything.
- **Restoring a setting.** Put back the saved value (`null` means it wasn't there: `delete` it), read it back, compare; delete the saved file only when they match:
  ```bash
  S=$(cat "$TMP/culvery-4e-serial")
  f="$TMP/culvery-4e-saved-font_scale"; v=$(cat "$f")
  if [ "$v" = "null" ]; then adb -s "$S" shell settings delete system font_scale; else adb -s "$S" shell settings put system font_scale "$v"; fi
  now=$(adb -s "$S" shell settings get system font_scale | tr -d '\r')
  [ "$now" = "$v" ] && rm "$f" && echo "restored $v" || echo "MISMATCH: saved $v, now $now"
  ```
  A mismatch: put it back once more; a second mismatch is a stop and ask. The session isn't over while any `culvery-4e-saved-*` file it made is left.
- **The pin check** (end of every session, and wherever a step says so):
  ```bash
  S=$(cat "$TMP/culvery-4e-serial")
  adb -s "$S" shell dumpsys activity activities | tr -d '\r' | grep -E "mLockTaskModeState|mResumedActivity|topResumedActivity"
  adb -s "$S" shell uiautomator dump >/dev/null 2>&1 || adb -s "$S" shell uiautomator dump >/dev/null 2>&1
  adb -s "$S" exec-out "cat /sdcard/window_dump.xml" | grep -c -E 'text="(Sign out|Tablet lock)"'
  adb -s "$S" shell "rm -f /sdcard/window_dump.xml"
  ```
  Expected: `mLockTaskModeState=PINNED`, the resumed activity `uk.co.siland.culvery/.MainActivity`, and `0` (nobody signed in: no **Sign out** in the status bar; Settings closed: no **Tablet lock** page tab). If the dump fails twice, ask the user to look instead. If it isn't pinned: the user opens Culvery from the app list (and answers Android's pinning question); run it again. The cable comes out only after it passes.
- **The home-app check:**
  ```bash
  S=$(cat "$TMP/culvery-4e-serial")
  adb -s "$S" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -n 1
  ```
  Prints the home app's component (Samsung's starts `com.sec.android.app.launcher/`).
- **Culvery's process id** (it may take a moment after a start):
  ```bash
  S=$(cat "$TMP/culvery-4e-serial")
  for i in 1 2 3 4 5 6 7 8 9 10; do pid=$(adb -s "$S" shell pidof uk.co.siland.culvery | tr -d '\r'); [ -n "$pid" ] && break; sleep 1; done
  [ -n "$pid" ] && echo "pid $pid" || echo "Culvery isn't running: stop"
  ```
  Every block that needs `$pid` starts with these lines. Empty after 10 s: stop and look.
- **Install** (every install after the first; the first is §1.3):
  ```bash
  ./gradlew :app:assembleRelease
  S=$(cat "$TMP/culvery-4e-serial")
  adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk
  cp app/build/outputs/mapping/release/mapping.txt "$TMP/culvery-4e-mapping-$(git rev-parse --short HEAD).txt"
  ```
  "Release signing isn't set up": stop and ask (`docs/setup/release.md` §1). `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or any other failure: **stop and ask**. Never `adb uninstall uk.co.siland.culvery` without the user's yes to "Uninstalling Culvery deletes its people, PINs, Google connection and home location; you’d set it up again. Uninstall it?" Never `install -d`. Keep each installed build's mapping until the close-out.
- **Screenshots**, only when needed: `adb -s "$S" exec-out screencap -p > "$TMP/culvery-4e-s<N>-<item>.png"`.
- **`logcat -c`** is allowed up to Session 6. From Session 6 until day 7 has been read, nobody clears logs. A restart of the tablet also loses the crash buffer.
- **The fault loop.** Each fault found on the tablet (spec §6):
  1. A look or wording call is **taste**: write it under the session's "Taste". A fault that needs a design decision goes to the user before anything is written. A fault bigger than a session's fix goes under "Carried" with what was seen.
  2. Otherwise: a failing test in the owning module where code can show it (beside that module's existing tests), run to see it fail for the reason seen on the tablet; the fix; the module's tests; the gate `./gradlew testDebugUnitTest verifyRoborazziDebug` (and the plan's baseline check if an image changed); a commit with a plain message and no attribution trailer; the install block above; a recheck on the tablet.
  3. Write the commit and the recheck under the session's "Faults".
  A fix that would need a deprecated API: stop and ask.
- **Session start:**
  ```bash
  adb devices -l
  S=$(cat "$TMP/culvery-4e-serial")
  adb -s "$S" get-state
  adb -s "$S" shell dumpsys package uk.co.siland.culvery | grep -E "versionName|lastUpdateTime"
  touch "$TMP/culvery-4e-names.txt"
  ```
  Expected: the tablet as `device` (`model:SM_T510`), `device`, Culvery's version. Write the date and `git log -1 --format=%h` (the build installed) in the session's transcript.
- **Session end** (also for a session stopped early): the session's Restore; the pin check; the names check:
  ```bash
  w=docs/superpowers/plans/2026-10-12-4e-walkthrough.md
  [ -s "$TMP/culvery-4e-names.txt" ] && grep -n -i -F -f "$TMP/culvery-4e-names.txt" "$w"
  grep -n -E '[[:alnum:]._%+-]+@[[:alnum:].-]+' "$w"
  ```
  Expected: no output from either (replace each hit with its placeholder and run again). Then `rm -f "$TMP"/culvery-4e-s<N>-*` (the session's logs, dumps and screenshots).

## 1. Session 1: install and set up (about 30 minutes, tablet by the PC)

Before the user arrives the plan's Task 2 Step 1 has built the release and run the gate. **Changes no setting** (Developer options and USB debugging are the user's own switch-on, decision 7). Closes: 4c "the R8 build's sign-in, sync and weather"; 2a "an on-device pass of the calendar UI" (with Session 2); 4a "the wizard's steps with the Samsung keyboard"; 4c "`dataExtractionRules` … verify on the SM-T510" (the Android 11 half).

### 1.1 Developer options and USB debugging (the user)

1. On the tablet: **Settings › About tablet › Software information**, then tap **Build number** 7 times. Type the tablet's unlock PIN or pattern if it asks. It says "Developer mode has been turned on".
2. Back in **Settings**, **Developer options** is now at the bottom. Open it and turn on **USB debugging**; tap **OK**.
3. Plug the tablet into this PC. On the tablet, "Allow USB debugging?": tick **Always allow from this computer**, tap **Allow**.

The controller tells the user: "Anything I read from the tablet (logs, screen dumps, screenshots) ends up in my session, so I’ll search them for what each check needs and read as little as I can."
```bash
adb devices -l | tr -d '\r' | awk '/model:SM_T510/ { print $1 }' > "$TMP/culvery-4e-serial"
wc -l < "$TMP/culvery-4e-serial"
```
Expected: `1`. Zero or two: stop and look (`adb devices -l`).
```bash
S=$(cat "$TMP/culvery-4e-serial")
for p in ro.product.model ro.build.version.release ro.build.version.sdk ro.build.version.oneui ro.build.version.security_patch persist.sys.timezone; do echo "$p=$(adb -s "$S" shell getprop $p | tr -d '\r')"; done
adb -s "$S" shell wm size; adb -s "$S" shell wm density
adb -s "$S" shell settings get system font_scale
adb -s "$S" shell pm list packages | grep -i -E "talkback|launcher|inputmethod"
```
Record: model, Android and SDK (expected 11 / 30), One UI (`30100` is 3.1), patch, size, density, font scale, the tablet's zone, and the TalkBack, launcher and keyboard packages.

### 1.2 The names

Ask the user: "Please give me every name Culvery will show: each person, each calendar, the town, and the Google account’s address. I keep them in one file on this PC only to check the notes don’t contain them, and delete it at the end." Write them one per line to `$TMP/culvery-4e-names.txt`.

### 1.3 Pinning on this tablet, and install

```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" shell settings get system lock_to_app_enabled
adb -s "$S" shell settings get system lock_to_app_exit_locked
adb -s "$S" shell locksettings get-disabled
adb -s "$S" shell pm list packages uk.co.siland.culvery
```
Record the two pinning settings (Samsung's "Pin windows" and "Lock tablet when unpinning"), and ask the user whether the tablet has a screen lock (PIN, pattern or password) — `get-disabled` `false` with a lock set means unpinning can ask for it. **Change nothing** here: if pinning needs a setting changed (§1.5), that's decided then with the user's and the son's yes. The last command: no output expected; if Culvery is listed, stop and ask.

Tell the user: "On the first install Google Play Protect may ask about an unknown app: tap **Install anyway**, and **Don’t send** if it offers to send the app for scanning." (The verifier setting is not changed.)
```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" install -r app/build/outputs/apk/release/app-release.apk
cp app/build/outputs/mapping/release/mapping.txt "$TMP/culvery-4e-mapping-$(git rev-parse --short HEAD).txt"
adb -s "$S" shell dumpsys package uk.co.siland.culvery | grep -E "versionName|^ *flags=\["
```
Expected: `Success`; the `flags=[ … ]` line (the package's flags, not `pkgFlags`) **without** `ALLOW_BACKUP`.

### 1.4 The setup wizard (the user)

Tap **Culvery** in the app list, then:
1. **Welcome:** **Start setting up**.
2. **Home location:** type the town with the Samsung keyboard; pick it.
3. **You:** your name, colour, and your Admin PIN (choose it, then type it again).
4. **People:** each person with name, colour, role and (for Adults who add events) a PIN.
5. **Connect:** **Connect Google Calendar**, choose the family account. If Google says "Google hasn’t verified this app": **Advanced**, then **Go to Culvery**; tick both calendar boxes; **Continue**.
6. **Calendars:** who each calendar is for; the main calendar.
7. **Done:** don't tap **Choose home app** (decision 5). Open Culvery.

While the user is on step 5, the controller runs only the first line of the pin check. Expected `mLockTaskModeState=NONE`: Culvery pins only once setup is done (plan ruling 1). Record whether the chooser opened, roughly how fast, and that Culvery came back to Connect.

### 1.5 Checks

1. **Pinned after Done:** the pin check. Record whether Android asked to pin and what the user tapped. If it never reaches `PINNED` (with §1.3's settings as found), stop and ask: turning Samsung's "Pin windows" on is the user's and the son's call.
2. **The first sync:** the Calendar tab shows the week within a minute or so (record); the week line says "Updated from Google Calendar …".
3. **The weather:** the header's temperature and the Forecast card (record how long after Done).
4. **Warnings:**
   ```bash
   S=$(cat "$TMP/culvery-4e-serial")
   for i in 1 2 3 4 5 6 7 8 9 10; do pid=$(adb -s "$S" shell pidof uk.co.siland.culvery | tr -d '\r'); [ -n "$pid" ] && break; sleep 1; done
   adb -s "$S" logcat -d -v time --pid="$pid" '*:W' | sed -E 's/^[0-9-]+ [0-9:.]+ //' | sort | uniq -c | sort -rn | head -20
   ```
   Expected: none, or lines naming only exception types and HTTP codes. Record each kind once.

### 1.6 No backup (spec §5)

```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" shell bmgr enabled
adb -s "$S" shell bmgr backupnow uk.co.siland.culvery
```
Expected: never `Success` for `uk.co.siland.culvery` ("Backup is not allowed", or the package left out). If the backup manager is disabled, don't enable it: §1.3's `flags=[` line stands. Device transfer and the Android 12+ rules can't be tried on this tablet: record "not tried".

### 1.7 Not the home app

The home-app check: Samsung's launcher (decision 5). Then the session end (nothing to restore).

### 1.8 Transcript

- Date, build installed:
- Device (model, Android/SDK, One UI, patch, size, density, font scale, zone; TalkBack, launcher, keyboard packages):
- Pinning settings and screen lock:
- Install (Play Protect? `flags=[` without ALLOW_BACKUP?):
- Wizard (Connect unpinned; chooser speed; the Samsung keyboard in the town search and name fields):
- Pinned after Done:
- First sync, weather:
- Warnings:
- Backup:
- Home app:
- Faults:
- Carried:
- Taste:

## 2a. Session 2a: font scale, dark theme, splash, zone, bars

**Changes:** the font scale. Closes: E1, E8, E9, E4, E3, E2 (recorded only); 2b-1 "DM Sans on API 30"; 4b "the cold-start zone".

### 2a.0 Save

```bash
S=$(cat "$TMP/culvery-4e-serial")
f="$TMP/culvery-4e-saved-font_scale"
[ -e "$f" ] || adb -s "$S" shell settings get system font_scale | tr -d '\r' > "$f"
cat "$f"
```

### 2a.1 Font scale 1.3, then 1.5, and DM Sans (E1, E8, E9, 2b-1)

Android 11 scales text linearly (the emulator capped it). For `1.3`, then `1.5`:
```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" shell settings put system font_scale 1.3
```
The user looks (Culvery redraws itself) and says what they see: Home's clock and date, the header items, and whether the grid's cards overflow or clip (E1: the grid pushed off the screen is a fault: cap the clock); the **Calendar** tab's rail label whole (E8); week chips with real titles, three lines then "…" (E9; the full title is one tap away). At 1.3 the controller takes one screenshot of Home and compares it with `app/src/test/screenshots/home_fs15.png` for the DM Sans weights (the clock and titles medium, the body regular; bold text not cut short; 2b-1). Then put the font scale back with the Restore block (§2a.6) before going on.

### 2a.2 The dark theme (E4)

Culvery's theme follows sun times, not Android's dark mode, which isn't touched. The user taps the theme button in Culvery's status bar until it shows **Night**, then looks at Home, the Calendar tab and an event, against the dark baselines the controller opens for them side by side: `app/src/test/screenshots/home_calendar_dark.png`, `home_weather_dark.png`, `capability/calendar/src/test/screenshots/detail_editable_dark.png`. The controller takes a screenshot only if the user sees a difference worth comparing. The user taps the button back to **Auto**.

### 2a.3 The splash on a cold start in the day theme (E3, for W9)

In the day theme, the user watches the screen while:
```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" shell am force-stop uk.co.siland.culvery
adb -s "$S" shell am start -W -n uk.co.siland.culvery/.MainActivity | grep -E "TotalTime"
```
Record what the user saw between the black screen and Home (a dark flash? how long?) and the `TotalTime`. Then the pin check (force-stop ends pinning; the start pins again).

### 2a.4 The clock on a cold start (4b)

Compare the tablet's zone (§1.8) with the household's town's. The same zone: record "same zone: not reachable on this household" (plan ruling 4). Different: repeat §2a.3's commands while the user watches the clock, date and theme in the first second.

### 2a.5 The bars and the gesture handle (E2)

The user says which navigation the tablet uses (Settings › Display › Navigation bar: **Buttons** or **Swipe gestures**) without changing it, and whether the navigation bar, a gesture handle or the status bar shows over Culvery's grid while pinned. Record only.

### 2a.6 Restore

```bash
S=$(cat "$TMP/culvery-4e-serial")
f="$TMP/culvery-4e-saved-font_scale"; v=$(cat "$f")
if [ "$v" = "null" ]; then adb -s "$S" shell settings delete system font_scale; else adb -s "$S" shell settings put system font_scale "$v"; fi
now=$(adb -s "$S" shell settings get system font_scale | tr -d '\r')
[ "$now" = "$v" ] && rm "$f" && echo "restored $v" || echo "MISMATCH: saved $v, now $now"
```
Culvery's theme button on **Auto**. Then the session end.

### 2a.7 Transcript

- Date, build installed:
- Font scale 1.3 (clock, grid, rail, chips):
- Font scale 1.5 (same):
- DM Sans weights:
- Dark theme vs baselines:
- Splash (seen; TotalTime):
- Cold-start zone:
- Bars / handle (navigation type):
- Restored (read back):
- Faults:
- Carried:
- Taste:

## 2b. Session 2b: keyboard, status-bar touches, weather, PIN chooser

**Changes no setting.** Closes: 2b-2 "the add/edit sheet with the Samsung keyboard in lock-task", "its first Day row", "a scrim tap hides the keyboard", "the IME inset"; 2b-2 floating and split keyboards (closed: not wall use); 4a "Settings' pages with the Samsung keyboard", "the PIN chooser's two stages"; 4d-1 "a touch just outside Sign out and the theme toggle"; 4b "the weather at the tablet's density"; 2a (with 2a).

### 2b.1 The Samsung keyboard (2b-2, 4a)

The user, an Adult's PIN when asked:
1. Today's **+**: tap the title field; the keyboard comes up. Is the first Day row visible, half-hidden, or reachable by scrolling? Is **Add event** reachable above the keyboard?
2. Tap the dimmed area outside the sheet: the keyboard hides (record whether the sheet stays or closes). Close the sheet; nothing is added.
3. Settings (Admin PIN) › Home location: tap the town field, type a letter: the results show above the keyboard. Settings › People › a person: the name field stays visible above the keyboard. Change nothing; **Close**.

### 2b.2 Sign out and the theme toggle, a touch just outside (4d-1 ruling 8)

The user signs in (opens Settings with their PIN, closes it: **Sign out** shows).
```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" shell uiautomator dump >/dev/null 2>&1 || adb -s "$S" shell uiautomator dump >/dev/null 2>&1
adb -s "$S" exec-out "cat /sdcard/window_dump.xml" | grep -o -E '(text|content-desc)="(Sign out|Theme: [A-Za-z]+)"[^>]*bounds="[^"]*"'
adb -s "$S" shell "rm -f /sdcard/window_dump.xml"
```
(Two failed dumps: the user tries with a finger only.) From the theme button's `bounds="[x1,y1][x2,y2]"`, tap 4 pixels below its bottom edge at its middle: `adb -s "$S" shell input tap <(x1+x2)/2> <y2+4>`. Expected: the theme changes. Tap it back to **Auto** the same way. Then 4 pixels below **Sign out**: expected signed out. Then the user tries both with a finger just outside the words.

### 2b.3 The weather and the PIN chooser (4b, 4a)

1. The header weather and the Forecast card at the tablet's density, from across the room: readable? anything clipped? (taste notes to "Taste").
2. Settings (Admin PIN) › People › a person › **Change PIN** (or **Set PIN**): "Choose a 4-digit PIN"; the user types four digits that are nobody's PIN; "Enter it again". Clear? **Cancel**: nothing saved. Close Settings.

### 2b.4 Restore

Nothing changed: Culvery's theme on **Auto**, nobody signed in. The session end.

### 2b.5 Transcript

- Date, build installed:
- Keyboard (Day row, Add event reachable, scrim tap, town search, name field):
- Sign out / theme toggle, just outside:
- Weather at the density:
- PIN chooser stages:
- Faults:
- Carried:
- Taste:

## 3. Session 3: lock and restart

**Changes:** the home app (Culvery for §3.4–§3.6, Samsung's launcher again in §3.7). Closes: 4c "pinning on Android 11 after a reboot", "the home app after a real power cut", "the `onCreate` hand-over", E5 "One UI's default-apps menu"; 4d-1 "the unpinned window behind the home-role dialog" (observed; accepted 2026-10-06).

### 3.0 Save

```bash
S=$(cat "$TMP/culvery-4e-serial")
f="$TMP/culvery-4e-saved-home"
[ -e "$f" ] || adb -s "$S" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -n 1 > "$f"
cat "$f"
```
Expected: a component starting `com.sec.android.app.launcher/`. Anything else: stop and ask.

### 3.1 Pinning after a reboot (4c)

Ask "May I restart the tablet now?"; with a yes:
```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" reboot
adb -s "$S" wait-for-device
```
The user unlocks the tablet and opens **Culvery** from the app list. Record whether Android asks to pin and what the user taps; the pin check.

### 3.2 Use other apps and back (decision 4)

The user: Settings (Admin PIN) › **Tablet lock** › **Use other apps**. Record the toast's words (expected "You can use other apps now. Culvery locks again when it next opens."). The first line of the pin check: expected `NONE`. The user opens another app for one minute, then **Culvery** from the app list. The pin check: `PINNED`.

### 3.3 One UI's default-apps menu (E5)

The user: **Use other apps** (Admin PIN), then on the tablet **Settings › Apps**, and reads the path to the default apps (on One UI 3 usually **Choose default apps**) and the home-app entry inside it (**Home app** or **Home screen**). Change nothing; back to Culvery from the app list (the pin check). Record the exact path. If it isn't "Settings › Apps › Default apps": the plan's Task 6 Step 2.

### 3.4 Culvery as the home app, then a reboot (4c)

The user: Settings (Admin PIN) › Tablet lock › **Choose home app**; Android asks: **Culvery**, then **Always** (or **Set as default**). While Android's question shows, the first line of the pin check (expected `NONE`; accepted, 4d-1). The user taps **Sign out**. The home-app check: `uk.co.siland.culvery/.MainActivity`. Ask "May I restart the tablet now?"; with a yes, `adb -s "$S" reboot`, `adb -s "$S" wait-for-device`. The user unlocks it. Expected: Culvery, not Samsung's home screen. The pin check.

### 3.5 The nearest thing to a power cut (4c)

The user holds the **side key** and **Volume down** together for about 10 seconds, until the screen goes black and the Samsung logo shows, then lets go, and unlocks the tablet when it has started. `adb -s "$S" wait-for-device`, then the pin check. Expected: Culvery in front. Record any pinning question.

### 3.6 The hand-over (4c, `onCreate`)

```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n uk.co.siland.culvery/.MainActivity
adb -s "$S" shell dumpsys activity activities | tr -d '\r' | grep -c "uk.co.siland.culvery/.MainActivity"
adb -s "$S" shell dumpsys activity activities | tr -d '\r' | grep -E "Task id|mLockTaskModeState" | head -10
```
Expected: one Culvery task in front and the pin check passing. Record the counts; "not triggered" if no second Culvery appeared.

### 3.7 Back to Samsung's launcher (decision 5)

The user: Settings › Tablet lock › **Change home app** (a fresh Admin PIN; the pad says "Enter your PIN to use other apps."); Android asks: **One UI Home**, then **Always**. The user opens Culvery from the app list and taps **Sign out**.

### 3.8 Restore

```bash
S=$(cat "$TMP/culvery-4e-serial")
f="$TMP/culvery-4e-saved-home"; v=$(cat "$f")
now=$(adb -s "$S" shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -n 1)
[ "$now" = "$v" ] && rm "$f" && echo "restored $v" || echo "MISMATCH: saved $v, now $now"
```
A mismatch (§3.7 didn't finish or didn't take):
```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" shell cmd package set-home-activity "$(cat "$TMP/culvery-4e-saved-home")"
```
then the compare block again. Still a mismatch: the user sets it by hand (**Use other apps**, then Settings › Apps › Choose default apps › Home app › **One UI Home**), and the compare block once more; a third mismatch is a stop and ask. Then the session end.

### 3.9 Transcript

- Date, build installed:
- After a reboot (Samsung home): pinning question?
- Use other apps: toast words; pinned again?
- One UI's menu path (E5):
- Culvery as home: unpinned behind the dialog? After a reboot:
- Forced restart:
- Hand-over:
- Back to Samsung's launcher (read back; fallback used?):
- Faults:
- Carried:
- Taste:

## 4. Session 4: Google, a write, and the forecast offline

**Changes:** Wi-Fi (§4.2 only). Uses the family's Google Calendar only with the user's yes for each change, and puts it back. Closes: E6, E10; 4d-2 "a slow chooser can close Settings behind it"; 3a/4c "the chooser and consent in screen pinning"; 4c "an unshared calendar", "Delete this and following", "the R8 build's writes"; 4d-1 "`lastFailure` wiring" (the offline forecast).

### 4.1 The series first (4c)

Ask: "May I have you make a test event on your phone that repeats daily for 5 days in the family’s main calendar, called ‘4e series’, starting tomorrow? We delete it at the end." With a yes, the user makes it now; the tablet picks it up during the next steps (about 5 minutes).

### 4.2 The forecast offline, while the series syncs (4d-1)

Ask: "May I turn the tablet’s Wi-Fi off for about a minute?" With a yes:
```bash
S=$(cat "$TMP/culvery-4e-serial")
f="$TMP/culvery-4e-saved-wifi_on"
[ -e "$f" ] || adb -s "$S" shell settings get global wifi_on | tr -d '\r' > "$f"
cat "$f"
adb -s "$S" shell svc wifi disable
adb -s "$S" shell am force-stop uk.co.siland.culvery
adb -s "$S" shell am start -W -n uk.co.siland.culvery/.MainActivity | grep TotalTime
```
The user reads the Forecast card after 30 seconds (the last forecast, or "No forecast — check the tablet’s Wi-Fi. Culvery will try again."). Then:
```bash
S=$(cat "$TMP/culvery-4e-serial")
f="$TMP/culvery-4e-saved-wifi_on"; v=$(cat "$f")
[ "$v" = "1" ] && adb -s "$S" shell svc wifi enable
sleep 5
now=$(adb -s "$S" shell settings get global wifi_on | tr -d '\r')
[ "$now" = "$v" ] && rm "$f" && echo "restored $v" || echo "MISMATCH: saved $v, now $now"
```
The pin check (the restart re-pinned it).

### 4.3 Reconnect from Settings › Calendars (E6, 4d-2 §10)

```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" logcat -c
```
The user: Settings (Admin PIN) › **Calendars** › **Reconnect** (a fresh Admin PIN). While Google's chooser shows: the first line of the pin check (expected `NONE`). The user **waits 2 minutes 30 seconds in the chooser** (past Settings' 2-minute session), then chooses the account and finishes. Then:
```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" logcat -d -v time | grep -E "ActivityTaskManager: (START u0|Displayed)" | tail -10
```
Record the time from the tap to the chooser (the `START` and `Displayed` times), how the Connecting card looked, whether Settings was still open when Culvery came back, and the pin check's first line after (`PINNED`). **If Settings closed behind the chooser**, record it exactly and bring it to the user to rule on; no fix is written before the ruling.

### 4.4 The connection health (E10)

Settings › Calendars after a clean pass (the week line "Updated … just now" or "… minutes ago"): the account row's subtitle, with [account]. Expected: no red "not synced yet".

### 4.5 An unshared calendar, while the series waits (4c)

Ask: "Is there a calendar shared into the family account from another Google account that we can unshare for a few minutes and share again?" With a yes, for that [calendar]: first record (1) its share permission level as the owner sees it (Google Calendar on a computer, signed in as the owner › the calendar's **Settings and sharing** › **Share with specific people**: e.g. "See all event details") and (2) the person the calendar is for in Culvery's Settings › Calendars row. Then the user removes the family account from its sharing. Wait for the next pass (about 5 minutes). Record on the tablet: [calendar]'s row (refused while still listed: a read problem with **Hide this calendar**), and whether Google still lists it. The user may tap **Hide this calendar** (record). Then the user shares it again **at the recorded permission level**, shows it again in Settings › Calendars if hidden, and both are read back: the owner's sharing shows the same level; the row is for the same person. Record how long Google kept listing it if it stopped; otherwise check on day 7.

### 4.6 Delete this and following (4c)

The tablet shows all five "4e series" events (record the time since §4.1). On the phone, the user opens the third, **Delete**, **This and following events**. Within about 5 minutes the tablet shows only the first two (record). Then the user deletes the series (**All events**); the tablet shows none.

### 4.7 A write from the tablet (4c)

Ask: "May I have you add a test event called ‘4e test’ for today on the tablet, then delete it?" With a yes: Today's **+**, `4e test`, a time, **Add event** (an Adult's PIN). The phone shows it within a few minutes. On the tablet: open it › **Delete** › **Delete event** (PIN). The phone no longer shows it.

### 4.8 Restore

Wi-Fi read back in §4.2 (no `culvery-4e-saved-wifi_on` left). Confirm with the user: the calendar shared again at its level and shown for the same person; "4e series" and "4e test" gone from the phone and the tablet. The session end.

### 4.9 Transcript

- Date, build installed:
- Forecast offline:
- Reconnect (chooser speed; Connecting card; unpinned during; Settings after 2:30; pinned after):
- Connection health row:
- Unshared calendar (permission level and person before/after; refused while listed; the row; Hide; how long listed):
- Delete this and following (times):
- Write from the tablet:
- Faults:
- Carried:
- Taste:

## 5. Session 5: TalkBack (as short as possible)

**Changes:** the accessibility services, accessibility on, touch exploration, TalkBack's log output level. The controller drives; the user listens. **TalkBack is off whenever a real PIN is typed**, and the capture stopped. Closes: E7; from the 4d-2 transcript: the twice-said first opens, focus after an event or Settings closes, focus parked on a scrolled list, "You can try again." when a lockout ends (if time allows). Carried: the PIN pad over an edited sheet (a 10-minute wait) and the toast with TalkBack on.

### 5.0 Save

```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" shell pm list packages | grep -i talkback
adb -s "$S" shell dumpsys package com.samsung.android.accessibility.talkback | grep -o -E "[a-z.]+/[A-Za-z.]*TalkBackService" | head -1
adb -s "$S" shell dumpsys package com.google.android.marvin.talkback | grep -o -E "[a-z.]+/[A-Za-z.]*TalkBackService" | head -1
```
Write the installed one as a full `package/class` (expand a leading `.` in the class with the package) to `$TMP/culvery-4e-s5-tb.txt`. Neither installed: stop and ask.
```bash
S=$(cat "$TMP/culvery-4e-serial")
for k in enabled_accessibility_services accessibility_enabled touch_exploration_enabled; do
  f="$TMP/culvery-4e-saved-$k"
  [ -e "$f" ] || adb -s "$S" shell settings get secure "$k" | tr -d '\r' > "$f"
  echo "$k=$(cat "$f")"
done
adb -s "$S" shell settings get secure accessibility_shortcut_target_service
adb -s "$S" shell settings get secure accessibility_button_targets
```
Record the two shortcut values. Tell the user the way out if the cable drops with TalkBack on: "Plug the cable back in and I’ll turn it off. If that fails: if the shortcut above names TalkBack, hold **Volume up** and **Volume down** together for 3 seconds. Otherwise, in TalkBack, touch an item to select it and double-tap anywhere to open it: in Culvery, **Settings**, your PIN (TalkBack speaks the keys, but nothing records them once the cable is out), **Tablet lock** › **Use other apps**; then Android’s **Settings › Accessibility › Screen reader** and switch it off."

### 5.1 TalkBack's log to Verbose, with TalkBack off (the user)

The user: Settings (Admin PIN) › Tablet lock › **Use other apps**. On the tablet: **Settings › Accessibility › Screen reader** (or **TalkBack**) **› Settings › Advanced settings › Developer settings**. Read out the **Log output level** now; the controller writes it to `$TMP/culvery-4e-saved-log_level` (once only). Set **Verbose**; confirm the dialog. Back to Culvery from the app list (the pin check).

### 5.2 TalkBack on, and the speech capture

```bash
S=$(cat "$TMP/culvery-4e-serial")
tb=$(cat "$TMP/culvery-4e-s5-tb.txt"); prev=$(cat "$TMP/culvery-4e-saved-enabled_accessibility_services")
if [ "$prev" = "null" ] || [ -z "$prev" ]; then on="$tb"; else on="$prev:$tb"; fi
adb -s "$S" shell settings put secure enabled_accessibility_services "$on"
adb -s "$S" shell settings put secure accessibility_enabled 1
adb -s "$S" shell settings put secure touch_exploration_enabled 1
```
TalkBack says it is on. Start the capture in the background (the Bash tool's background option):
```bash
S=$(cat "$TMP/culvery-4e-serial")
pkg=$(cut -d/ -f1 "$TMP/culvery-4e-s5-tb.txt")
adb -s "$S" logcat -c
adb -s "$S" logcat -v time --pid="$(adb -s "$S" shell pidof "$pkg" | tr -d '\r')" >> "$TMP/culvery-4e-s5-talkback.log"
```
Read what it said: `grep -a -o "Speaking fragment text=.*" "$TMP/culvery-4e-s5-talkback.log" | tail -20`. If nothing matches after a move, find this build's wording with `grep -a -c -i -E "speak|utterance|tts"` and record the pattern that works. Never `uiautomator` while TalkBack is on (it suspends TalkBack).

**Turning TalkBack off for a PIN** (and on again):
```bash
S=$(cat "$TMP/culvery-4e-serial")
v=$(cat "$TMP/culvery-4e-saved-enabled_accessibility_services")
if [ "$v" = "null" ]; then adb -s "$S" shell settings delete secure enabled_accessibility_services; else adb -s "$S" shell settings put secure enabled_accessibility_services "$v"; fi
adb -s "$S" shell settings put secure touch_exploration_enabled 0
```
Stop the background capture first. Once the pad has gone: §5.2's first block (TalkBack on), then `logcat -c` and the capture again.

### 5.3 Moving with TalkBack

Try `adb input` first: next `adb -s "$S" shell input swipe 400 400 900 400 120`, previous `… 900 400 400 400 120`, open `adb -s "$S" shell "input tap 640 400; input tap 640 400"`, focus an item with one `input tap` on it. If TalkBack ignores it, the user moves: **swipe right** for the next item, **swipe left** for the previous, **double-tap** to open, touch an item to hear it; the controller says one move at a time.

### 5.4 The walk (E7)

Write what TalkBack said, in order, in §5.6, with placeholders. Anything read badly, twice, or over another announcement: the fault loop, or carried with what was heard.
1. **Cold start, first opens.** Stop the capture; `am force-stop uk.co.siland.culvery`; `logcat -c`; capture on; `am start -n uk.co.siland.culvery/.MainActivity`; the first line of the pin check. Open the first event (Today or the Calendar tab): its title said once or twice? Close it: where does focus land?
2. **Home.** One pass: rail tabs, clock, date, header weather (one item), Today, Coming up, the Forecast rows. **If `adb input` didn't work and the user is swiping, stop here and ask whether to go on.**
3. **Settings, and the first PIN pad.** Open Settings: the pad's opening said once or twice? Turn TalkBack off for the PIN; the user types their Admin PIN; TalkBack on, capture again. The page tabs, Home location, People (a row), Calendars scrolled with one swipe so focus parks on a lower row, Tablet lock. Touch nothing: Settings closes at 2 minutes (record the times: parked focus mustn't stretch it); where does focus land?
4. **A lockout's end** — only if under 20 minutes have passed since §5.2; otherwise carry it. Ask: "May I have you type five PINs that are nobody’s, so the pad locks for 30 seconds?" With a yes: TalkBack off; the user opens Settings and types five wrong PINs until it says to wait; TalkBack on, `logcat -c`, capture; touch the pad's message line once; wait. Expected: "You can try again." once. Cancel the pad.

### 5.5 Restore

Stop the background capture.
```bash
S=$(cat "$TMP/culvery-4e-serial")
for k in enabled_accessibility_services accessibility_enabled touch_exploration_enabled; do
  f="$TMP/culvery-4e-saved-$k"; v=$(cat "$f")
  if [ "$v" = "null" ]; then adb -s "$S" shell settings delete secure "$k"; else adb -s "$S" shell settings put secure "$k" "$v"; fi
  now=$(adb -s "$S" shell settings get secure "$k" | tr -d '\r')
  [ "$now" = "$v" ] && rm "$f" && echo "$k restored $v" || echo "$k MISMATCH: saved $v, now $now"
done
grep -a -c -E "Speaking fragment text=[0-9](,|\s*$)" "$TMP/culvery-4e-s5-talkback.log"
```
Expected: three "restored" lines; the digit check `0` (a key's speech is a lone digit; "1 of 4 digits entered" doesn't match). Any other count: `rm -f "$TMP/culvery-4e-s5-talkback.log"` unread, and write "log deleted: a key was captured". Then the user: **Use other apps** (Admin PIN) › Screen reader › Settings › Advanced settings › Developer settings › **Log output level** back to `$(cat "$TMP/culvery-4e-saved-log_level")`, and reads it back to the controller; when it matches, `rm "$TMP/culvery-4e-saved-log_level"`. Back to Culvery from the app list. The session end.

### 5.6 Transcript

- Date, build installed:
- TalkBack package and version; log level found and restored; shortcut values:
- Speech pattern on this build:
- Navigation (adb input, or the user's swipes):
- Cold start: first event open; focus after close:
- Home:
- First PIN pad; Settings; parked focus; closed at; focus after:
- Lockout end (or carried):
- Restored (read back); digit check:
- Faults:
- Carried (the PIN pad over an edited sheet; the toast with TalkBack on; anything else):
- Taste:

## 6. Session 6: performance, day 1

**Changes:** USB debugging, off at the end for the week. This is day 1 of the family week; from here until day 7 is read, nobody runs `logcat -c`. Closes: 4c "Appendix B's numbers on the SM-T510" (the subset font's saving is carried from the emulator: Dalvik 17.2 → 2.8 MB).

### 6.1 The measurements (4c Appendix B)

`tools/measure-release.sh` takes the serial as its argument, stops and starts only Culvery, and takes about three minutes; run it with a 5-minute timeout. It runs `logcat -c` before each measured start. If Android asks to pin at each start, the user leaves it (the next start replaces it) and answers it after the last.
```bash
S=$(cat "$TMP/culvery-4e-serial")
bash tools/measure-release.sh "$S" 2>&1 | tee "$TMP/culvery-4e-s6-perf.txt"
```
If it stops ("measure-release.sh stopped at line …"), check `adb devices` and run it again.

### 6.2 The running snapshot

```bash
S=$(cat "$TMP/culvery-4e-serial")
for i in 1 2 3 4 5 6 7 8 9 10; do pid=$(adb -s "$S" shell pidof uk.co.siland.culvery | tr -d '\r'); [ -n "$pid" ] && break; sleep 1; done
[ -n "$pid" ] || echo "Culvery isn't running: stop"
adb -s "$S" shell ps -p "$pid" -o PID,ELAPSED,TIME,RSS,NAME
echo "threads: $(adb -s "$S" shell ps -T -p "$pid" | tail -n +2 | wc -l)"
adb -s "$S" shell dumpsys meminfo uk.co.siland.culvery | grep -E "Dalvik Heap|Native Heap|TOTAL|Views:|Activities:"
adb -s "$S" shell dumpsys gfxinfo uk.co.siland.culvery | grep -E "Total frames rendered|Janky frames|50th percentile|90th percentile|99th percentile"
```
(If `ps` refuses a field, drop it.) Copy every number into §6.4.

### 6.3 USB debugging off for the week (the user)

After the pin check: the user turns **USB debugging** off (Settings › Developer options › USB debugging), which keeps this PC's key, and unplugs. It goes on again for the day 3–4 read and day 7.

### 6.4 Transcript

- Date (day 1), build installed:
- APK size; TotalTime ×5 (median); fully drawn ×5; skipped frames ×5; Play services crashes:
- gfxinfo (frames, janky, p50/p90); Dalvik / native PSS after the last start:
- Running snapshot (elapsed, CPU time, RSS, threads, Dalvik/native/TOTAL PSS, views, activities, frames, janky, p50/p90/p99):
- Against Appendix B (emulator); the font saving carried (17.2 → 2.8 MB Dalvik, emulator):
- USB debugging off:
- Faults:
- Carried:

## 7. The family week, the day 3–4 read and day 7

### 7.1 The week

Shared use (decision 4), Samsung's launcher as home. The son leaves Culvery with Settings › Tablet lock › **Use other apps** (an Admin types the PIN); Culvery pins again when it next comes to the front. The user notes anything odd with the time and what was on screen. The tablet stays on its charger; a restart loses the crash buffer (note when one happened).

### 7.2 Day 3 or 4: a short read (about 5 minutes)

Android keeps crash and ANR records for about three days, so they are read mid-week. The user turns **USB debugging** on and plugs in. The session start, then:
```bash
S=$(cat "$TMP/culvery-4e-serial")
adb -s "$S" logcat -b crash -d -v time > "$TMP/culvery-4e-s7-crash-mid.txt"
grep -c "Process: uk.co.siland.culvery" "$TMP/culvery-4e-s7-crash-mid.txt"
grep -c "Process: com.google.android.gms" "$TMP/culvery-4e-s7-crash-mid.txt"
adb -s "$S" shell dumpsys dropbox | grep -E "data_app_crash|data_app_anr|system_app_crash" | tail -40
adb -s "$S" shell dumpsys dropbox --print data_app_crash | grep -c "Process: uk.co.siland.culvery"
adb -s "$S" shell dumpsys dropbox --print data_app_anr | grep -c "uk.co.siland.culvery"
adb -s "$S" shell dumpsys dropbox --print system_app_crash | grep -c "Process: com.google.android.gms"
```
A Culvery crash: save its entry (`dumpsys dropbox --print data_app_crash`, the Culvery entry only) to `$TMP/culvery-4e-s7-stack.txt` and read the stack with the installed build's mapping:
```bash
"$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin/retrace.bat" "$TMP/culvery-4e-mapping-<commit>.txt" "$TMP/culvery-4e-s7-stack.txt"
```
Then the pin check, USB debugging off, unplug. Don't run `logcat -c`.

### 7.3 Day 7 (about 15 minutes)

USB debugging on, plugged in. In this order (the script clears logcat; a restart loses the week-old process):
1. **The session start.**
2. **The crash buffer and the dropbox records:** §7.2's block again, with `crash-day7` in the file name. Record the counts, each exception type once (`grep -A3 "Process: uk.co.siland.culvery" … | grep -E "Exception|Error" | sort | uniq -c`), and the crash buffer's oldest line's time (`head -n 1`). Play services crashes in a loop (3a): how many and when.
3. **Culvery's warnings still in the log** (they name no one):
   ```bash
   S=$(cat "$TMP/culvery-4e-serial")
   for i in 1 2 3 4 5 6 7 8 9 10; do pid=$(adb -s "$S" shell pidof uk.co.siland.culvery | tr -d '\r'); [ -n "$pid" ] && break; sleep 1; done
   adb -s "$S" logcat -d -v time --pid="$pid" '*:W' > "$TMP/culvery-4e-s7-warnings.txt"
   head -n 1 "$TMP/culvery-4e-s7-warnings.txt" | cut -c1-18
   sed -E 's/^[0-9-]+ [0-9:.]+ //' "$TMP/culvery-4e-s7-warnings.txt" | sort | uniq -c | sort -rn | head -20
   ```
   Record the oldest time and each kind with its count.
4. **The running snapshot** (§6.2's block) before anything restarts Culvery; compare with day 1. `ELAPSED` says whether the process ran all week.
5. **The sync health:** the user opens Settings (Admin PIN) › Calendars; the row's words with [account]. §4.5's calendar: still listed?
6. **The measurements:** §6.1 again; compare with day 1.
7. **The user's notes:** each becomes a fault (the loop), a follow-up with what was seen, or a taste item.
8. **The session end.** USB debugging stays on for the close-out.

### 7.4 Transcript

- Day 1 / day 3–4 / day 7 dates, build installed; any restart during the week:
- Crash buffer, mid-week and day 7 (counts, types, oldest line):
- Dropbox crashes and ANRs (Culvery, Play services):
- Warnings (oldest line; kinds and counts):
- Running snapshot, day 7 vs day 1:
- Sync health; the unshared calendar still listed?:
- Measurements, day 7 vs day 1:
- The user's notes, and what each became:
- Faults:
- Carried:
- Taste:

## 8. Close-out checklist (spec §8)

- [ ] Every transcript filled in (passed, fixed with its commit, or carried with a reason).
- [ ] No `culvery-4e-saved-*` file left in `$TMP` (each was deleted only after its value read back). The son's settings as found: font scale, Wi-Fi on, the three accessibility settings, TalkBack's log level, Samsung's launcher as home (the home-app check), Culvery's theme on Auto, Samsung's pinning settings untouched (or as the user and son decided in Session 1).
- [ ] The names check over the whole file, and over the branch's history of it (the plan's Task 10 Step 1).
- [ ] The installed build's mapping copied where the user keeps the release key (with their yes); then `rm -f "$TMP"/culvery-4e-*`, the names file included.
- [ ] The tablet: Culvery pinned in front, nobody signed in. Offered to the user: Developer options › **Revoke USB debugging authorisations**, then Developer options off (the switch at the top of Developer options) — only after TalkBack's log level is confirmed restored (its Developer settings disappear with Developer options).
- [ ] The follow-ups file updated; device owner listed as a post-v1 plan with its checks.
- [ ] Simplify pass; the final review with one fix round; the gate green; merged locally.
