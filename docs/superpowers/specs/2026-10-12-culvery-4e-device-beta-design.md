# Culvery 4e: the device beta on the SM-T510

Brainstormed 2026-10-12. The last plan before the user's end-of-v1 design walk-through. Follow-ups file: `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("For Plan 4e" lists).

## 1. Purpose and outcome

Culvery has only run on the API 35 emulator. 4e puts the signed release on the real tablet, a Samsung Galaxy Tab A 10.1 (SM-T510, Android 11, One UI), runs the device checks the earlier plans queued, fixes what they find, and ends after a week of real family use.

Done means: every check in §4 run and recorded (passed, fixed, or listed with a reason); the one planned code change (§5) merged; a week of family use with a day-1 and a day-7 snapshot compared (§7); the follow-ups file's 4e items closed or carried; a final review and a local merge.

## 2. Decisions (the user, 2026-10-12)

1. **Goal:** on the wall, in family use: the checks, their fixes, then a week of real use as the beta.
2. **Lock mode: screen pinning.** The tablet is the user's son's and can't be factory reset yet. It is already signed in to the family Google account that Culvery will use.
3. **Device owner** (the 4d ruling's wall setup) waits until everything else is done and tested, as its own small plan, probably after v1. 4e doesn't touch it.
4. **Shared use during the week:** the son keeps using the tablet; he leaves Culvery through Settings › Tablet lock › Use other apps (an Admin's PIN), and Culvery pins again when it next comes to the front.
5. **Home app:** Samsung's launcher stays the home app for the test phase. Culvery is made the home app only for the restart and power-cut checks (§4.3), then switched back.
6. **Planned code:** only the backup and transfer exclusion (§5). The clock set back during sleep, the wizard PIN pad hardening, the soak script and the code backlog stay in the follow-ups.
7. **Connection:** a USB cable to this PC, with Developer options and USB debugging on.
8. **Approach:** one walk-through document run in short sessions, with faults fixed as they are found (approach A).

## 3. Ground rules (every session)

- The user does every physical step (Developer options, cable, reboots, the power cut, the phone) and types every PIN and password. The controller drives over adb.
- **The son's tablet:** the controller never clears another app's data, uninstalls anything but Culvery, or changes a setting a check doesn't need. Every changed setting (font scale, dark mode, the home app, TalkBack and its log level, Wi-Fi) is saved first and put back in the same session, even if the check fails.
- **No household names in logs or the transcript:** people, events, towns and the account are written as [person], [event], [town], [account]. Logs and screenshots are deleted once the session's notes are written; the transcript is checked for names before it is committed.
- **No PIN in a log:** TalkBack is off and the speech log stopped while a real PIN is typed (plan review, 2026-10-12; was: the speech log paused, 4d-2's rule).
- **USB debugging** is off during the family week (the PC's key is kept), on again only for the day 3–4 read and day 7 (plan review, 2026-10-12).
- **Offline** only with Wi-Fi off (`svc wifi disable`, then `enable`); never airplane mode.
- **Install** only signed releases with `adb install -r`. The first install is fresh (no Culvery on the tablet). Never uninstall Culvery once set up without the user's yes: it deletes the household.
- Sessions are 20–30 minutes. TalkBack's session is the shortest possible (the 4d-2 check was wearing).
- **Taste** (look and wording calls) goes to the end-of-v1 walk-through list, not into 4e's fixes.

## 4. The sessions

Each item names the follow-up it closes (E-numbers from the 4d triage; the rest by plan).

### 4.1 Session 1: install and set up (about 30 minutes, tablet by the PC)

1. The user turns on Developer options and USB debugging, plugs in and accepts this PC's key. The controller records the Android and One UI versions, the screen size and density.
2. Build and install the signed release (`docs/setup/release.md` §3). Same key and release OAuth client as 4c.
3. The user runs the setup wizard for real: the home location, the real people with their colours and roles, the Admin PIN, and Connect with the family account already on the tablet. The household is the one used for the family week.
4. Checks: Connect in screen pinning (the chooser opens outside the pin; Culvery pins again when back in front); the first sync; the weather arrives; the R8 build's sign-in, sync and weather work (4c). The write (an event added and deleted, with the user's yes) moves to Session 4 (plan review, 2026-10-12).
5. Culvery is not made the home app (decision 5).

### 4.2 Session 2: look and layout

Run as two sessions, each with its own save and restore (plan review, 2026-10-12): **2a** font scale (with DM Sans), the dark theme, the splash, the cold-start zone and the bars; **2b** the keyboard, Sign out and the theme toggle, the weather, the PIN chooser. The navigation bar is only recorded, not changed; Android's dark mode is not touched; Samsung's floating and split keyboards are closed as not wall use (the scrim tap stays) (plan review, 2026-10-12).

- Font scale 1.3 and 1.5 (Android 11 scales linearly; the emulator capped it): the clock and the Home grid (E1; cap the clock if the grid overflows), the rail's "Calendar" label (E8), week chip titles with real data (E9). Put the scale back.
- The dark theme against the dark screenshots (E4).
- The splash on a cold start in the day theme (E3, to inform W9).
- The gesture handle over the grid in the release build (E2).
- A cold start: does the clock, date or theme show the device's zone before the household's (4b)?
- The Samsung keyboard: the add/edit sheet's first Day row with the keyboard up (2b-2), the town search, the person sheet's name field (4a).
- The status bar's Sign out and theme toggle: a touch just outside them still lands (4d-1 ruling 8).
- The weather header and the Forecast card at the tablet's density (4b).
- The PIN chooser's two stages read clearly (4a).

### 4.3 Session 3: lock and restart

- Pinning on Android 11: after a reboot, does Culvery ask to pin again when opened (4c)?
- Use other apps, a few minutes in another app, back to Culvery: it pins again; its toast's words.
- One UI's name for the default-apps menu (E5): if it isn't "Settings › Apps › Default apps", correct `HOME_APP_HOW_TO` (`SetupCopy.kt`) with a test.
- Choose home app (an Admin signed in), then `adb reboot`: it lands in Culvery. Then the nearest a battery tablet gets to a power cut: the user forces it off (power and volume down held) and starts it again: it lands in Culvery (4c). Then Change home app back to Samsung's launcher (a fresh Admin PIN) and sign out.
- The `onCreate` hand-over (a second Culvery started while Culvery is home) if a launcher start triggers it on One UI (4c; unit-tested only).

### 4.4 Session 4: Google

- Reconnect from Settings › Calendars: the chooser outside the pin, its speed, the Connecting card while waiting (E6); Settings mustn't close behind a slow chooser (4d-2 §10). If it does, fix it.
- The stored connection health after a clean pass clears "not synced yet" (E10).
- With the user's yes for each: a calendar unshared from the account in Google Calendar (refused while still listed; how long Google keeps listing it; the row and **Hide this calendar**) (4c); "Delete this and following" on a series from the phone: the trailing events leave the tablet (4c).

### 4.5 Session 5: TalkBack (as short as possible)

TalkBack on, driven by the controller; the user only listens. One walk of Home, an event, the PIN pad and Settings (E7), plus what the emulator check left: what is said when a lockout ends ("You can try again."), focus after Settings or a sheet closes (the status-bar clock on the emulator), the twice-said first opens, the PIN pad over an edited sheet outliving Settings by a second, focus parked on a scrolled list (4d-2 follow-ups). Speech is read from TalkBack's verbose log (set back to its saved level afterwards). On the emulator `adb input` didn't reach TalkBack; on the device, try `adb input` first, then ask the user to swipe. Trimmed (plan review, 2026-10-12): TalkBack is off whenever a real PIN is typed; if `adb input` fails the session stops after Home and asks; the lockout's end is heard only if under 20 minutes have passed; the PIN pad over an edited sheet (a 10-minute wait) and the toast with TalkBack on are carried.

### 4.6 Session 6: performance, day 1

`tools/measure-release.sh` on the tablet: `am start -W` TotalTime (5 runs), skipped frames, `dumpsys gfxinfo`, Dalvik and native PSS, threads; the subset font's memory saving (4c Appendix B). Saved for day 7. The font saving is carried from the emulator (Dalvik 17.2 → 2.8 MB), not re-measured on the tablet (plan review, 2026-10-12).

## 5. The planned code change: no backup or transfer

- Add `app/src/main/res/xml/data_extraction_rules.xml` excluding every domain (`root`, `file`, `database`, `sharedpref`, `external`, and `device_root`, `device_file`, `device_database`, `device_sharedpref`: nine, plan review, 2026-10-12) from `cloud-backup` and `device-transfer`, and set `android:dataExtractionRules` on `<application>`. Keep `allowBackup="false"`, which is what Android 11 (the SM-T510) and earlier honour for both; the rules cover Android 12 and later, where transfer ignores `allowBackup` for apps targeting 31+.
- A unit test fails if the manifest loses either attribute or the rules file loses an exclusion.
- `docs/privacy.html`: "Culvery tells Android not to include its data in app backup or device transfer, so Android does not copy it to a cloud backup or a new tablet." (plan review, 2026-10-12)
- On the tablet: `adb shell bmgr backupnow uk.co.siland.culvery` doesn't back Culvery up.

## 6. Faults found on the tablet

Each fault: a test where code can show it, the fix, the gate (`./gradlew testDebugUnitTest verifyRoborazziDebug`), a commit, a new signed install (`install -r`, keeping the household), and a recheck on the tablet. A fault that needs a design decision goes to the user before it is fixed. A fault that would take more than a session's fix becomes a follow-up with what was seen.

## 7. The family week

- Shared use (decision 4), Samsung's launcher as home. The user notes anything odd with the time and what was on screen; no daily routine is asked for.
- Day 7, by USB (about 15 minutes): the session 6 snapshot again, compared with day 1; Android's crash buffer (`logcat -b crash`) for Culvery; Culvery's warnings since day 1 (they name no one); the sync health in Settings › Calendars; the user's notes, each becoming a fix (§6), a follow-up or a taste item.
- The emulator Play services crash-loop after a network change (3a) is watched for in the crash buffer and the sync health.

## 8. Close-out

The transcript goes in the walk-through document; the 4e items come off the follow-ups file (closed, or carried with a reason); device owner is listed as a post-v1 plan with its checks (provisioning on a reset tablet, lock-task during the chooser, reboot without a pinning prompt, `docs/setup/release.md` §6). Then a simplify pass over the branch, the final opus review with one fix round, and a local merge.

## 9. Not in 4e

Device owner; the clock set back during sleep (`elapsedRealtime`); the wizard PIN pad hardening; the soak sampling script; the Tests and hardening backlog from 4a–4d; an API 30 emulator pass; the design walk-through's taste items (W1–W34).
