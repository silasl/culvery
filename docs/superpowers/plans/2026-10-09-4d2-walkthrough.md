# Culvery 4d-2: the TalkBack check and the wording walk-through

Plan: `docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md`, Task 17. Spec: §8.6, Decision 7.

Part A is run by the controller with TalkBack on, reading what TalkBack says from the emulator's logs. Part B is the user's own walk-through of the same screens, without TalkBack, for the wording.

## Rules for both parts

- The **already-running** API 35 Google Play emulator `emulator-5554`. If `adb devices` doesn't list it, ask the user to start it; don't start another.
- **Never clear Culvery's data or wipe the emulator** without the user's explicit yes to the exact question in A.1.
- **Ask before** adding an event or changing the home location, and undo it afterwards as the user prefers.
- **The user types every PIN.** The controller never types one, with or without TalkBack.
- **No PIN reaches the logs.** TalkBack speaks every key the user touches. Before any PIN entry the controller stops the background speech capture (A.2's `logcat`, or `uiautomator events`), and starts it again with `adb -s emulator-5554 logcat -c` first only once the pad has gone. The one exception is A.4 path 4, which uses a throwaway PIN (A.4).
- **The transcript names nothing of the household's.** Event titles, people's names, towns and account addresses are written as "[event]", "[person]", "[town]", "[account]"; a key's speech from the pad is never written down. Before the transcript is committed, the controller greps it for every name and title it saw (Task 17 Step 9).
- **Settings are put back.** TalkBack's three accessibility settings and its Log output level (A.2, A.6), and, for A.4 path 12, the tablet's automatic date and time, are saved before they are changed and restored afterwards, even if a path fails.
- **Offline** only with `adb -s emulator-5554 shell svc wifi disable` and `svc data disable` (`enable` to come back); never airplane mode.
- **Install** only a signed release with `adb install -r`; never uninstall or clear data without the yes in A.1.
- Screenshots: `adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4d2-<item>.png"`.

## A. TalkBack (the controller)

### A.1 Install the signed release

```bash
adb devices
./gradlew :app:assembleRelease
adb -s emulator-5554 install -r app/build/outputs/apk/release/app-release.apk
```
If `assembleRelease` stops with "Release signing isn't set up", stop and ask the user (the four `culvery.release.*` lines go in their own `~/.gradle/gradle.properties`, `docs/setup/release.md` §1). If `install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a debug build is installed: stop and ask. Installing the release then needs `adb uninstall uk.co.siland.culvery`, which deletes the household (people, PINs, the Google connection, the home location); ask exactly: "The emulator has a debug build. Installing the release means uninstalling it, which deletes Culvery's people, PINs, Google connection and home location; you'd run setup and reconnect Google afterwards. Uninstall it?" Without a clear yes, run Part A on the debug build instead (`ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug`) and note that in the transcript.
A release pins the screen: to reach Android's own settings during the check, use Settings › Tablet lock › Use other apps (the user's PIN).

### A.2 Turn TalkBack on and its speech log

```bash
adb -s emulator-5554 shell pm list packages | grep -i talkback
adb -s emulator-5554 shell settings get secure enabled_accessibility_services > "$TMP/culvery-a11y-before.txt"
adb -s emulator-5554 shell settings get secure accessibility_enabled >> "$TMP/culvery-a11y-before.txt"
adb -s emulator-5554 shell settings get secure touch_exploration_enabled >> "$TMP/culvery-a11y-before.txt"
cat "$TMP/culvery-a11y-before.txt"
```
Expected: `package:com.google.android.marvin.talkback`. If it's missing, stop and ask the user (the image has no TalkBack). Keep the three saved values for A.6.
```bash
adb -s emulator-5554 shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService
adb -s emulator-5554 shell settings put secure accessibility_enabled 1
adb -s emulator-5554 shell settings put secure touch_exploration_enabled 1
```
TalkBack speaks "TalkBack on". Its speech goes to the log only at the verbose level: in TalkBack's settings (Settings › Accessibility › TalkBack › Settings › Advanced settings › Developer settings, shown once Android's developer options are on, which they are on the emulator) note the **Log output level** it shows now (write it in A.5; A.6 puts it back), then set it to **Verbose**. Then start the log in the background:
```bash
adb -s emulator-5554 logcat -c
adb -s emulator-5554 logcat -v brief --pid="$(adb -s emulator-5554 shell pidof com.google.android.marvin.talkback | tr -d '\r')" > "$TMP/culvery-talkback.log"
```
and read what it said with:
```bash
grep -i -E "speak|utterance|tts" "$TMP/culvery-talkback.log" | tail -20
```
If nothing is logged after a swipe, use the accessibility-event dump instead: `adb -s emulator-5554 shell uiautomator events > "$TMP/culvery-a11y-events.log"` in the background, reading each event's `Text` and `ContentDescription`. Check right after starting it that TalkBack still speaks (`settings get secure enabled_accessibility_services` still names it); if the dump turned TalkBack off, stop the dump, turn TalkBack on again (the three `put` lines) and ask the user how to go on.

### A.3 Moving with TalkBack

`adb -s emulator-5554 shell wm size` gives the screen; take a screenshot to place swipes on the content, away from the status bar. Try, in this order, and keep the first that TalkBack accepts (record which in the transcript):
1. Keyboard: next `adb -s emulator-5554 shell input keycombination KEYCODE_ALT_LEFT KEYCODE_DPAD_RIGHT`, previous `… KEYCODE_ALT_LEFT KEYCODE_DPAD_LEFT`, activate `… KEYCODE_ALT_LEFT KEYCODE_ENTER`.
2. Gestures: next `adb -s emulator-5554 shell input swipe 400 400 900 400 120`, previous `… 900 400 400 400 120`, activate a double-tap in one shell call: `adb -s emulator-5554 shell "input tap 640 400; input tap 640 400"`.
To put TalkBack's focus on one item, a single `input tap` on it (touch exploration focuses what is touched).

### A.4 The paths (spec §8.6)

For each, note what TalkBack said, in order, in the transcript below; anything that reads badly, twice, or over another announcement is fixed (with a test, the gate and a commit) or listed with a reason.
1. **Home.** Header weather (one item: "17 degrees, sunny, high 19, low 11" in the plan's words), the clock and date, Today's rows ("09:30, Swimming, Sam's event…" one item each, with "open event details"), Today's +, Coming up rows and their day headings, the Forecast rows (one item per day), the rail tabs ("Home, tab, selected").
2. **An event.** Open one: the pane title "Event details"; the rows ("Time, …", "Who, …" merged); Change and Delete with their roles; Delete › the confirm card › "Keep" (never Delete).
3. **Adding an event** (ask first; it adds to the household's calendar; delete it afterwards as the user says). Add event: pane title "Add event"; the field read as "Event name"; the Who chips as radio buttons with "selected"; Pick date… (pane "Pick a date", a day cell "Tuesday 7 October, today"); Pick time… ("Hour up", the value spoken as it changes); Add event; the toast ("Event added on …"), read once.
   **Field names (the check Task 5 Step 0 deferred to here).** Focus the "Event name" field and type `Swim` (`adb -s emulator-5554 shell input text Swim`; nothing is saved), move away and back. Kept: TalkBack says "Event name", "Swim" and "edit box"; write "Field names: content description kept the value" and go on. Lost: it says "Event name, edit box" with no "Swim". Then apply Step 0's fallback: in `HhTextField`'s `decorationBox`, beside the placeholder, add `Box(Modifier.semantics { text = AnnotatedString(label) })` while `value` isn't empty, keep `error(error)`, and change the field-name test assertions from `ContentDescription` to `SemanticsMatcher("named $label") { label in it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { t -> t.text } }`. A failing test first, the module's tests, the gate, a commit; check on the emulator again, and if the value is still lost, stop and ask the user. Record the outcome in A.5.
4. **The PIN pad, with a throwaway PIN.** Ask: "May I have you add a person called Test, an Adult, with a PIN you don't use anywhere, so I can listen to the PIN pad? I'll ask you to remove Test afterwards." Without a yes, skip this path and the user listens to it instead (TalkBack speaks aloud on the emulator) and says what they heard; nothing is logged. With a yes, capture paused, the user adds Test (their own PIN asks first). Then, capture running: open Settings: pane "Enter your PIN"; the keys as buttons; the user types Test's first digit: "1 of 4 digits entered"; the user types a PIN no one has: "Wrong PIN — try again" (listen whether the dots' "0 of 4 digits entered" is spoken as well, and how many times); the user types Test's whole PIN: "That PIN can’t do this. Ask an Admin." (Test may not change settings; no name is said, decision A); if the user agrees to see the lockout (five wrong or refused PINs lock the pad for 30 seconds), the lockout is spoken once, not every second. When the lockout ends, write down exactly what is said (the dots' "Wrong PIN — try again" may be read again with no new attempt). Cancel; capture paused; the user removes Test (their own PIN).
5. **Settings › People.** The page list as tabs ("People, tab, selected"); a row ("Sam, Adult · PIN set" with "change Sam's details"); Move Sam down: TalkBack's focus stays on Sam's row in its new place; the row's custom actions (TalkBack's actions menu, or `adb … input keycombination KEYCODE_ALT_LEFT KEYCODE_SPACE` for the menu) list "Move Sam up" / "Move Sam down"; Sam's sheet: the swatches as radio buttons with names ("Blue, selected", "Green, used by Alex").
6. **The Settings warning.** In Settings, touch nothing for 1 minute 40 seconds (with a long "Time to take action", longer; TalkBack itself must stay quiet, so no swipes): TalkBack says "Settings closes in 20 seconds. Touch the screen to stay." once. Then one TalkBack swipe: the warning goes and Settings stays open past the 2 minutes (this is the only check that Compose sends TalkBack's events through the content view). Then leave it: Settings closes 2 minutes later.
7. **What appears, said once.** Compose speaks a live region when a node it already has changes; these appear new, so listen whether TalkBack says each as it appears, and how often: a toast (path 3's "Event added on …"); in Settings › Home location, typing "Xqz" (nothing is saved): "Searching…", then "No towns match “Xqz” — …" (the always-present status line); a person sheet's name the same as someone's, then Save (ask first; a refused save changes nothing): "Someone is already called …" (the always-present message line). Any that TalkBack doesn't say: listed in the follow-ups with what was heard (the toast, the two warnings, the editor's failure card and the Saving pill appear new and have no always-present slot: a slot in their layer would hide the shell under it from TalkBack).

8. **Blank message lines.** The person sheet, the You step and the location pane keep a blank `HhMessageLine` while empty. Swipe through the person sheet (ask first: open Settings › People › a person, or Add person; nothing is saved) and listen whether the blank line is an empty TalkBack stop (a stop where nothing is said). If it is, a fix needs a deprecated API: do not write one; stop and ask the user first, and record what was heard.
9. **Single reads.** A week chip (Calendar tab), a Today row and the header weather are each read once: swipe to each and write down every word, counting repeats (the weather: the temperature or the condition said twice is a fault; "°" spoken twice, or the High/Low read again as a second item).
10. **Focus parked.** In Settings, park TalkBack's focus on the clock (it is outside Settings' page; tap it), then on a scrolled list inside Settings (the People list, scrolled with one swipe), and touch nothing else. Settings must still close at 2 minutes (with "Settings closes in 20 seconds. Touch the screen to stay." once at 1:40); parked focus and TalkBack's re-announcing do not stretch it.
11. **Pane titles.** Open the nested panes and listen which title is announced: the PIN chooser over a person sheet (Settings › People › a person › Set PIN or Change PIN; open the pad only, type nothing, Cancel) and the date or time picker over the event editor (A.4 path 3). Record whether it is the nested pane's title or the one behind it ("Choose a PIN", "Pick a date" against the sheet's).
12. **Sleep past the session.** With TalkBack on, the user opens Settings (their PIN, capture paused). Try `adb -s emulator-5554 root` first; if it is refused, skip this path before changing anything. Then save the date setting: `adb -s emulator-5554 shell settings get global auto_time >> "$TMP/culvery-a11y-before.txt"`, then `settings put global auto_time 0`. Sleep the tablet: `adb -s emulator-5554 shell input keyevent KEYCODE_SLEEP`. Move the date forward 3 minutes (`adb -s emulator-5554 shell date MMDDhhmmYYYY.ss` with a time 3 minutes on). If `adb root` was refused (a Google Play image), skip this path and ask the user how to go on; Settings' own date screen would end the Culvery session first. Wake it: `input keyevent KEYCODE_WAKEUP`. Settings must already be closed when the screen comes back, before any touch (take a screenshot first; no tap). Then put the time back (set `auto_time` to its saved value, the fourth line of the saved file, which also re-syncs the clock; or set `date` to the real time).
13. **Sheets inside Settings.** A sheet in Settings never closes by itself. In Settings › People open a person's sheet (ask first), type one letter in the name (an edit: the 10-minute hold), touch nothing: the sheet is still open at 2 minutes 1 second, the warning ("Settings closes in 20 seconds. Touch the screen to stay.") shows at 9 minutes 40 seconds, and Settings closes with the sheet at 10 minutes. Then, only if the user agrees to the wait, repeat with the PIN pad opened over the edited sheet (Change PIN or Set PIN; type nothing): the pad outlives the Settings session by a second, then waits its own 2 minutes and cancels. Record the times seen.
14. **Connection health.** Read it from Settings › Calendars' row, not from `run-as` (which fails on a release build): the row's health words and the week line's "Out of date" (focus the line: it is read on focus only). Confirm the "Out of date" cause: expected on the emulator is a pass with a failing calendar or unreachable Play services never stamping the last sync (D37, E10). Write the row's words and whether they fit; no account address.

### A.5 Transcript

Run 2026-10-12 on the signed release, with the controller driving and household names written as [person], [event], [town], [calendar], [account].

Build: signed release, `install -r` over the existing household (2026-10-12).
Speech capture: TalkBack 15 Log output level VERBOSE (was ERROR; restore in A.6), `logcat --pid` of TalkBack, lines `Speaking fragment text=`.
`uiautomator events` suspends TalkBack (Bound services empty while it runs) — not used.
Navigation: `adb shell input` taps/swipes don't reach TalkBack's touch explorer; the emulator console's virtual touchscreen (`event mouse x y 0 1/0`, over the console socket so a double-tap is fast enough) works. Keyboard Alt+arrows didn't move focus.
TalkBack's declared interactiveUiTimeout is 10000 ms (so recommended timeout with TalkBack on and no "Time to take action" set ≈ 10 s → Culvery's timeout stays 2 min).

#### Path 1 Home
- Home tab: "selected, Home, Tab, 1 of 2, In list, 2 items" ✓
- Order: Home tab → clock → header weather → Calendar tab → date → Today heading → Add event → See the week → Coming up heading → "Nothing on today. Tap + to add something." → day headings/rows → Forecast → Settings. FAULT: rail tabs interleave with the header (geometric order); Today's empty line read after "Coming up".
- Header weather: "Now 10°, cloudy, high 16°, low 7°, 10°, High 16° · Low 7°". FAULT: description then the visible text again.
- Coming up row: "[event], [person], Tuesday 16:10, [event], 16:10–16:25 · [person]". FAULT: read twice. Not clickable (no role/hint) — by design (rows aren't tappable).
- Day headings: "Tomorrow, Tuesday 13 October, Heading", "Wednesday 14 October, Heading" ✓; "Nothing on" ✓
- Forecast rows: "Today, cloudy, high 16°, low 7°", "Tuesday, rain, high 17°, low 9°" ✓ one item each
- Today +: "Add event, Button" ✓; "See the week, Button" ✓; "Settings, Button" ✓
- No Today rows today (nothing on).

#### Calendar tab (paths 1/9)
- Activating Calendar tab: "Calendar", "This week", "selected".
- Order: Earlier week (disabled) → Later week → "This week, Heading" → legend "Colours: [person], blue; …" → Add event → "Updated from Google Calendar just now" → Calendar tab (rail again mid-page) → day headings Mon…Sun all in a row → Settings. FAULT: the chips are skipped when swiping on from the headings; after a chip, the order goes to all seven "Add event on <day>" buttons. Columns need to be traversal groups (heading, chips, + per day). Rail needs to be its own group.
- Week chip (path 9): "[event], [person], Wednesday 09:00, 09:00 · [person], [event], Button". FAULT: read twice (description + merged texts).
- "Add event on Monday 12 October, Button" ✓

#### Path 2 An event
- Open: "[event], Heading" ×2 — the pane title "Event details" is not announced on first open. FAULT: heading twice.
- Rows: "Close, Button"; "When, Wed 14 Oct · 09:00–10:00"; "For, [person]"; "Created by, [person]"; "Calendar, [calendar]"; "Delete, Button"; "Change, Button" ✓ one item each.
- Delete → PIN pad (Delete needs a PIN): "Enter your PIN", "Who’s this?, Heading" ×2. FAULT: opening announcement twice.
- Pad: reason line "Enter your PIN to delete this event. It also records who made the change."; "0 of 4 digits entered"; keys "1, Button" … "0, Button"; "Cancel, Button"; "Delete last digit, Button, disabled" ✓
- Cancel: "Event details" (pane title, said on return) then focus back on "Delete, Button" ✓
- Confirm card / Keep: not reached yet (needs the user's PIN).

#### Settings (paths 5, 6, 7, 14)
- Opening (TalkBack was off for the PIN): focus on "Settings, Heading".
- Order: "Settings, Heading" → page heading → page content → the page tabs ("selected, Home location, Tab, 1 of 4, In list, 4 items", "People, Tab, 2 of 4", …) → "Close, Button". FAULT: the page is read before the page list; the list should follow the title.
- Home location: "Selected, [town]" ✓; field "Edit box, Town or city, Town or city" FAULT (label twice when empty); note line ✓.
- Path 7 search: "Xqz" matched a real place (results not announced); "Qzxjvq": "No towns match “Qzxjvq” — check the spelling or try a nearby town." once ✓. "Searching…" not heard (too brief, or not announced). Nothing saved.
- People (path 5): tab activation "People, Tab, 2 of 4", "People", "selected"; "People, Heading"; row "[person], Adult · No PIN — can’t add events, Button" then hint "Double-tap to change [person]’s details, Actions available…" ✓; "already first, Move [person] up, Button, disabled" ✓; "Move [person] down, Button" ✓; "Add person, Button" ✓.
- Move down: toast "[person] moved down." once; focus stays on the moved row's Move down ("already last") ✓. Moved back up the same way ("[person] moved up.") — order restored.
- Custom actions (swipe down on the row): "Move [person] down", "Activate" ✓.
- Calendars (path 14): "Google Calendar · [account]"; "Disconnect Google Calendar, [account], Disconnect, Button" FAULT (visible "Disconnect" again); "Updated 3 minutes ago" (health fits: synced; no Out of date today); "[calendar]"; "collapsed, Family, Button" + "Double-tap to change who [calendar] is for" ✓; "always shown: it’s the main calendar, Show [calendar], Show, Switch, disabled" FAULT (visible "Show" again); "Main" and "New events go here" as two stops (minor).
- Path 6 warning: idle from 11:42:05 → "Settings closes in 20 seconds. Touch the screen to stay." once at ~1:40 ✓; Settings closed at ~2:00 ✓. On close: nothing announced; TalkBack focus went to the system status-bar clock ("11:43"). FAULT (minor): focus leaves the app on close. Swipe-to-stay half not yet run.

#### Person sheet (paths 5, 7, 8, 11, 13)
- Open: "[person]" (pane title) then "[person], Heading" ✓ (once).
- "Close, Button"; "Name, Heading"; field "[person], Edit box, Name" → Field names: content description kept the value ✓ (Task 5 Step 0 fallback not needed).
- Swatches: "used by [person], Green, Radio button, disabled, 1 of 8, In list, 8 items"; "Selected, Blue, Radio button, 2 of 8"; "Not selected, Pink, Radio button, 3 of 8" … ✓
- Role: "Selected, Adult — can add and change any event, Radio button, 2 of 3" ✓; "PIN, Heading"; "Set PIN, Button"; "Without a PIN, [person] can only look."; "Remove person, Button"; "Save changes, Button".
- Path 8: no empty stop for the blank message line ✓.
- Path 11: Set PIN → "Choose a 4-digit PIN" (nested pane's own title) then "0 of 4 digits entered" ✓. Cancel → "Culvery", focus on "Name, Heading" (minor: not back on Set PIN).
- Path 7 duplicate name + Save changes: "Error: Someone is already called [person]." then "Someone is already called [person]." FAULT: said twice (field error + message line).
- Path 13: edited sheet, last touch ~11:58:50; still open at 12:01:13 (2:20) ✓.
- Path 13: warning "Settings closes in 20 seconds. Touch the screen to stay." at 12:08:34 (≈9:42 after the last touch) ✓; Settings closed with the sheet at ≈12:08:54 (≈10:03) ✓; edits lost (name unchanged) ✓. Focus after close: "Settings, Button" ✓.

#### Path 3 Adding an event (test event "Swim", deleted afterwards)
- Editor: "Add event, Heading"; "Close, Button"; summary "Today · 14:00–15:00 · Family"; field "Editing, Edit box, Event name, What’s happening?" (placeholder is a useful hint here); after typing: "Editing, Swim, Edit box, Event name" → Field names: content description kept the value ✓.
- Who chips: "Selected, Family, Radio button, 1 of 3, In list, 3 items" … ✓; Day chips radio buttons ✓; "Pick date…, Button"; Time chips "Not selected, Morning, 09:00, Radio button, 2 of 4" ✓.
- Date picker: "Pick a date" (nested pane title) then "Mon 12 Oct – Sun 15 Nov"; "Earlier weeks, Button", "Later weeks, Button"; weekday letters Mon…Sun each a stop (minor: 7 extra stops); cells "Not selected, Tuesday 13 October, Button", "Selected, Monday 12 October, today, Button" ✓. After picking: focus on "Morning" (minor: not back on Pick date…).
- Time picker: on open "Pick a time", "Minute 0", "Hour 14", "Pick a time, Heading" FAULT (values spoken on opening); order Hour up → Minute up → Hour 14 → "Colon" → Minute 0 → Hour down → Minute down: FAULT ("Colon" a stop; columns read across, not up/value/down). A press: "Minute 15", then "Minute 30" — said once each ✓. Cancel → back on "Pick time…" ✓.
- The editor and its PIN pad timed out while the user was away (expected); redone with TalkBack off. Toast "Event added on …" not heard (TalkBack off for the PIN); toasts were heard once elsewhere ("[person] moved down.", "Event deleted.").

#### Path 2 (continued) on the test event
- Open (second time): "Double-tap to open event details", "Event details", "Swim, Heading" — once each ✓ (the first-open double didn't repeat).
- Delete (signed in, no PIN): the confirm card appears but isn't announced; focus moves to the title "Swim, Heading". FAULT: the card should be announced (or take focus).
- Card: "Delete this event?"; "“Swim” will be removed from Google Calendar for everyone."; "Keep, Button"; "Delete event, Button" ✓. Keep → focus on "Change, Button" ✓.
- Delete event: "Event deleted." once ✓; then "Culvery", "13:16" — FAULT: focus falls to the system status-bar clock when the sheet closes.

#### Fixes (1575429, de812fd) and the recheck on the new release build
- Home: "selected, Home, Tab …", "Calendar, Tab, 2 of 2", "Settings, Button" → "13:32" → "Monday 12 October" → "Now 12°, cloudy, high 15°, low 7°" (once) → "Today, Heading" → Add event → See the week → "Nothing on today. Tap + to add something." → "Coming up, Heading" → "Tomorrow, Tuesday 13 October, Heading" → "[event], Family, Tuesday 16:10" (once) → … → Forecast ✓. (de812fd: the header items row needed to be a traversal group of its own for its index to apply.)
- Week: per day "Today, Monday 12 October, Heading" → chips "[event], Family, Tuesday 16:10, Button" (once) → "Add event on <day>, Button" ✓.
- Settings: "Settings, Heading" → the four page tabs → "Close, Button" → the page ✓; field "Edit box, Town or city" (once) ✓; "Disconnect Google Calendar, [account], Button" ✓; "always shown: it’s the main calendar, Show [calendar], Switch, disabled" ✓.

#### Paths 6 and 10 (new build)
- Idle from 13:34:46: "Settings closes in 20 seconds. Touch the screen to stay." at 13:36:26 (1:40) ✓; one swipe at 13:36:28 ("Main") kept Settings open past 2:00 ✓ (open at 13:37:05).
- Then a tap on the status-bar clock and nothing else: warning again at 13:38:10, closed at 13:38:29 (2:01 after the swipe) — parked focus didn't stretch it ✓. Focus after close: the status-bar clock again (listed).
- Focus parked on a scrolled Settings list: not run (the People list is too short to scroll).

#### Path 4 The PIN pad (throwaway person "Test", Adult, PIN 1234 — the user agreed to the controller pressing its keys; the user's double-click didn't reach TalkBack)
- Open (signed out): "0 of 4 digits entered", "Enter your PIN", "Who’s this?, Heading".
- First digit: "1 of 4 digits entered" once ✓.
- A PIN no one has (1999): "Wrong PIN — try again." once; the dots' "0 of 4" not said again ✓. Repeat wrong PINs: "0 of 4 digits entered, Error: Wrong PIN — try again." once per try ✓.
- Test's PIN: FAULT on the first build — the refusal showed but wasn't said: the keys went disabled at the fourth digit and the pad was rebuilt for each try, and TalkBack's re-reading of the focused key ("4, Button") flushed the queued message. Fixed in 4c9c0f1; then: "0 of 4 digits entered", "That PIN can’t do this. Ask an Admin." ✓ (no name).
- Lockout: the 4c9c0f1 change first made a lock arriving on a retry invisible (the countdown kept its stale 0; PINs typed while locked were still discarded) — caught here, fixed in the same commit. Then: "0 of 4 digits entered, Error: Too many tries — wait 240 seconds." once; keys disabled; the line ticks on screen without being re-read ✓ (240 s because earlier failures doubled the lock). The end of the lock wasn't heard: the check was stopped at the user's request.
- Idle pad closed at 2 minutes → focus back on "Settings, Button" ✓.

#### Not run
- Path 12 (sleep past the session): `adb root` refused on the Google Play image.
- Path 13's second half (PIN pad over an edited sheet): stopped at the user's request.
- Path 7's toast "Event added on …" with TalkBack on (the PIN was typed with TalkBack off).

#### Restored
- TalkBack's three settings put back (enabled_accessibility_services deleted, accessibility_enabled 0, touch_exploration_enabled 0).
- Still to restore: TalkBack's Log output level back to ERROR (needs Use other apps); the person "Test" to remove (the user).


### A.6 Turn TalkBack off and restore

First set TalkBack's Log output level back to the value A.5 noted. Then:
```bash
adb -s emulator-5554 shell settings put secure enabled_accessibility_services "<first saved value>"
adb -s emulator-5554 shell settings put secure accessibility_enabled "<second saved value>"
adb -s emulator-5554 shell settings put secure touch_exploration_enabled "<third saved value>"
```
If path 12 changed `auto_time`, put it back to the fourth saved line too. A saved value of `null` means the setting wasn't there: use `adb -s emulator-5554 shell settings delete secure <name>` instead of its `put` (`auto_time` is a global setting: `settings delete global auto_time`). Stop the background `logcat` (and `uiautomator events`). Then, once the transcript has been checked for names (Task 17 Step 9), delete what the check left behind:
```bash
rm -f "$TMP/culvery-talkback.log" "$TMP/culvery-a11y-events.log" "$TMP/culvery-a11y-before.txt" "$TMP"/culvery-4d2-*.png "$TMP/culvery-names.txt"
adb -s emulator-5554 logcat -c
```

## B. The wording walk-through (the user)

Without TalkBack. The user goes through these screens and says what reads wrong; the controller fixes it (test, gate, commit) or lists it in the follow-ups.
1. **Home:** the week line ("Updated from Google Calendar 5 minutes ago", or "Out of date — …" with its icon), "Nothing on today. Tap + to add something.", "+2 more in Calendar", the Forecast card’s words; with the user’s yes, the offline Forecast: go offline (`svc wifi disable`, `svc data disable`), wait for the next fetch, read "No forecast — check the tablet’s Wi-Fi. Culvery will try again.", then `enable` both.
2. **An event:** "Created by", "Added from a phone", "Change", Delete’s confirm and its "Keep".
3. **Add and change an event** (ask first): "Add event" sheet and button, "Choose who it’s for" when a Child can’t, the locked dates line on a multi-day event ("change dates in Google Calendar on your phone"), "Save changes", "Changes saved", and the 10-minute hold: type a title, leave it, read "This closes in 20 seconds. Touch the screen to keep your changes." at 9 minutes 40 seconds (only if the user wants to wait).
4. **The PIN pad:** "Enter your PIN to …" reasons, "Wrong PIN — try again", the lockout’s words.
5. **Settings:** People (rows "Adult · No PIN — can’t add events", the sheet’s "Without a PIN, Sam can only look."), Home location ("Used for the clock’s time zone and the weather."; type a town to see "Searching…"; with the user’s yes to change the location, choose one and change it back), Calendars ("Main", "Use for new events", "Choose who each calendar is for…"), Tablet lock (the line, the how-to line, "Use other apps" and its toast "You can use other apps now. Culvery locks again when it next opens."; in the release build this unpins; the tablet pins again when Culvery next comes to the front).
6. **The Settings warning** without TalkBack: "Settings closes in 20 seconds. Touch the screen to stay."

7. **Wording carried from the task reviews (Part B, for the user to rule on):**
   - A lost delete’s toast says "couldn’t be saved … and was lost".
   - Two connect failures that aren’t network failures (no primary calendar, a read that isn’t a 2xx) still say "check the tablet’s Wi-Fi".
   - A failed remove says "Couldn’t save Sam’s details".
   - Searching clears the town list on each keystroke, so the rows vanish while you type.
   - "Out of date" is read on focus only: it isn’t announced when the week goes stale while you look at it.
   - The Settings › Calendars empty-state line names Google Calendar even when no provider can connect (unreachable in a release build).
   - The person sheet’s blank message line pushes "Set PIN" to the scroll edge above the confirm card.

Ask: "Do the words read right? Anything to change before 4d-2 is done?"

**Outcome (2026-10-12):** the user went through Part B and ruled the words fine, §7's carried items included (kept as they are). Rulings in the same pass: the calendar's refusal toasts keep the person's name; the PIN pad's refusal differs from a wrong PIN on purpose (it names no one and counts towards the lockout); a long lockout says minutes ("wait 4 minutes", 58acff7); the end of a lockout says "You can try again." instead of repeating the last refusal.
