# Culvery Plan 20: Security and small fixes

Written on 2026-11-09 from the user's rulings of 2026-11-06 and 2026-11-09, revised after review the same day. Follow-ups items 3, 5, 11, 12, 13 and 16. The November 2026 review's security findings are in the private notes; this spec cites them by number only. What each finding is, where it is in the code, the exact fix and its tests are in the private companion, `culvery-notes/plan-20-security-detail.md`, which the builder reads first. Calls marked **(Claude's call)** were not put to the user; everything else is theirs.

The branch is `plan-20-security-and-fixes`, cut from `plan-19-power`, which isn't merged yet. Plan 19 merges first.

## 1. Intent

Close the review's open security findings that the user ruled on, and clear the small faults that have piled up since Plan 18.

Success: each finding in scope is fixed as ruled, with a test that fails without the fix. Home Assistant works over http or https; moving a link to https takes one Check and an Admin PIN. The small fixes are each visible in a test or at the walk-through.

### Goals
- Security: M2 and N1–N10, except N12, which Plan 19 did (§3).
- A shorter pause after ticking off a shopping item (§4.1, item 5).
- Plan 18's 25 deferred minors, 11a–11y (§4.2, item 11).
- Calendar M2 and calendar M3 (§4.3, §4.4, items 12 and 13).
- The Music card with nothing playing (§4.5, item 16).

### Non-goals
- October's other open findings (I2, I3, M5, M6, M7, M9, M10, M11). Not part of this plan (the user's scope).
- I1, N11 and N13. They stay accepted.
- Setting up https on the Home Assistant side. The household does that with Claude after the build (§5.3), not as part of it.
- Narrowing what Home Assistant's token can reach (follow-ups item 30).

### Anti-goals (a failure even if it works)
- A Home Assistant link that works today stops working on the day Plan 20 is installed.
- A PIN asked for something that didn't ask before, other than §3's: turning a PIN requirement on, plug choices and entity picks stay as they are.
- Security detail in the public repo: what a finding is, how it could be used, an address or a host name. Commits and comments say what changed for the household.
- A doorbell that plays nothing because a tablet sound failed.

## 2. Rulings (the user)

From 2026-11-06:
1. Scope: N1–N10 and M2. I1, N11 and N13 stay accepted; N12 is done in Plan 19.
2. N1: Culvery remembers Home Assistant's certificate on the first https Check, behind a fresh Admin PIN, as the Hue bridge's identity is kept, and refuses http once it has one.
3. M2: the Settings session ends as soon as Settings closes.
4. N3: "Allow the camera" asks for a fresh Admin PIN first, as "Open Android's settings" does. Re-pinning after Android's dialog is checked on the tablet.
5. N4: turning "Needs a PIN" off on a mode or a plug asks for a fresh Admin PIN; turning it on doesn't. Plug choices and entity picks are unchanged.
6. N2: all three parts.
7. N5: clean names only (no control or formatting characters, about 40 characters at most).
8. N6: a bounded queue, and a message over a set size closes the socket; Home Assistant then shows as unreachable.
9. N7: a placeholder in an older plan.
10. N8: fixture dates changed.
11. N9: a 10-minute minimum gap for bins on Update now, unless the last try failed.
12. N10: no address in the text form of connections and the bins connect steps.
13. Item 5: a shorter pause after a tick, with its own value.
14. Item 11: fix all 25, the private sound script included.
15. Item 16: hide the Music card's volume pill while nothing plays, with no mute button; "Shuffle everything" gets the room.

From 2026-11-09:
16. N1: Settings › Home Assistant gets "Forget this Home Assistant", behind a fresh Admin PIN. It clears the address, the token and the remembered certificate, so the link can be set up again over http or https.
17. N1: when the certificate changes, Home Assistant's problem line says so, and Check asks for a fresh Admin PIN to trust the new one.
18. Item 16 is closed except for "Shuffle everything"'s padding; the button keeps its own width.

## 3. Security

Each line is what the household sees or what changes in the code's shape. The detail is in the private companion, under the same numbers.

### 3.1 M2: Settings signs out when it closes
Closing Settings ends the session, however it closes: Close, the idle timer, or Door history from over it. The session's timeout still goes back to the wall's two minutes first, then the session ends. The next thing that needs a PIN asks for one. Leaving for one of Android's screens from Settings and coming back keeps the session while Settings stays open. Choose home app signs out before Android's chooser opens. `ShellViewModel.kt`; `ShellViewModelTest.settingsSetsAndClearsItsTimeout` is rewritten to match.

### 3.2 N1 (with M8): Home Assistant over https
Settings › Home Assistant, as the household will use it:

- **Over http**, nothing changes.
- **Moving to https:** type Home Assistant's https address (no port typed means 443) and leave the token box empty. Check reads the certificate without sending the token, then shows the PIN pad with "Trust this Home Assistant" and the certificate's fingerprint under it, in lowercase hex in groups of four, so it can be compared before trusting. **(Claude's call:** the fingerprint on the PIN pad, and its format.) After the Admin PIN, Check sends the token and saves. The saved token carries over when the address keeps its host and only moves to https, on any port, and whenever the certificate matches the one remembered.
- **Linked over https,** the page says "Linked over https." and shows the fingerprint again.
- **After that,** an http address is refused: "This Home Assistant is linked over https. Type its address starting https://."
- **If the certificate changes,** the hoover row, the Plugs tab and Holiday's steps say "Home Assistant’s certificate has changed. Press Check in Settings › Home Assistant to trust the new one." On the page: "Home Assistant’s certificate has changed. Press Check to trust the new one." Check then shows the new fingerprint and asks the Admin PIN.
- **Forget this Home Assistant** (shown while linked) asks a fresh Admin PIN, then clears the address, the token and the remembered certificate, and says "Home Assistant forgotten." The page goes back to "Not linked yet…", and the link can be set up again over http or https. Chosen devices are kept. **(Claude's call:** the toast's wording, and keeping the choices.)

In the code:
- `HomeAssistantLink` and `HomeAssistantSettings` gain the fingerprint, and a `forget()`. The http refusal reads the stored fingerprint directly, not the assembled link.
- `homeAssistantUrl`: no port means 8123 for http and 443 for https.
- New `HomeAssistantTrust.kt` in `core/homeassistant`, built the way `HueTrust.kt` is.
- `HomeAssistantLinking.check` follows the order above, and the certificate is read with one request that needs no token.
- `HomeAssistantClient` uses the remembered certificate for every call and the socket, and keeps those connections apart from the first read's.
- `HaProblem.CERTIFICATE_CHANGED` is new. It's added to `LINK_PROBLEMS` (`HomeAssistant.kt`), `problemLine` (`HomeAssistantWords.kt`), and `pageProblemLine` and the status line in `OtherIntegrationsPage.kt`.
- The PIN pad (`AccessControl.authorise`) takes an optional detail line under its label, used only here. **(Claude's call.)**
- The network config's comment and the README's Home Assistant section are updated to match.
- M8's rule for http addresses is unchanged.

### 3.3 N2: the council's site, https only
The council's site joins the https-only list (`network_security_config.xml`, `CleartextTest`). Links read from its pages are followed only on its own site and scheme (`CouncilPages.kt`), and so are its redirects (a network interceptor in `councilHttpClient`). **(Claude's call:** the redirect rule.) Internet calls no longer follow a redirect that changes the scheme (`internetHttpClient()` in `core/net/.../Http.kt`). Open-Meteo's two clients (`OpenMeteoModule.kt`) are built from `internetHttpClient()` too, keeping their own timeouts. Nothing changes on screen; a page that breaks the rule reads as "Can’t read the council’s site", as any unreadable page does.

### 3.4 N3: Allow the camera asks for a fresh Admin PIN
Settings › Display › Allow the camera shows the PIN pad, saying "Allow the camera", before Android's camera dialog. The gate sits in `CameraPrompt.kt`, so anything else that opens the dialog is gated too. The tablet check (§5.3) covers re-pinning after each answer.

### 3.5 N4: turning "Needs a PIN" off asks for a fresh Admin PIN
On a mode (Settings › Modes) and a plug (Settings › Plugs), switching "Needs a PIN" off shows the PIN pad, saying "Turn off Needs a PIN". Refused, the switch stays on. Switching it on asks nothing.
- Plugs: `PlugActions.setNeedsPin`.
- Modes: a new `setNeedsPin(id, on)` in `StoredModeActions`, which asks the PIN first and then writes only that setting through `ModesStore`. The PIN is never asked inside a store write.
- `ModesStore.update` keeps a stored "Needs a PIN" that's on as it is, so other edits to a mode can't turn it off.

### 3.6 N5: clean names for tablet sounds
A name with formatting characters loses them, and the name is cut to 40 characters, ending in "…" when cut. A name with nothing left is "Unnamed sound". `cleanSoundName` in `core/sound/.../Sounds.kt` is used for the folder's sounds and for a gone file's name, so the two always agree. Two names that are the same once cleaned, ignoring case, show once within a folder. A cut can split an emoji made of several joined ones; it only affects names over 40 characters. Stored choices keep their ids.

### 3.7 N6: the Home Assistant socket's limits
The socket's queue holds at most 256 messages, and a message over 4 million characters closes it. Either way Home Assistant shows as unreachable and the socket reopens on its usual backoff. `HomeAssistantClient.kt`.

### 3.8 N7
A placeholder in an older plan.

### 3.9 N8
Fixture dates changed in the bins tests (`provider/calendar-midsussex` test resources and tests, and `DebugCouncilTest`).

### 3.10 N9: Update now and bins
Update now reads the council's site again only if the last try failed or was at least 10 minutes ago. Google calendars still sync at once. `MidSussexBinsProvider.due`.

### 3.11 N10: text forms
`Connection`, `Address` and the bins connect steps (`Pick`, `Ready`, `Done`) print no address, house, postcode or label, as `Birthday`, `Sound` and `ModeRowUi` already do. **(Claude's call:** `Address` and `Done` are added to the ruling's list; both carry the same values.)

## 4. Small fixes

### 4.1 A shorter pause after a tick (item 5)
A ticked item now stays on the list, struck through, for 6 s, and its Undo toast for the same 6 s. **(Claude's call:** 6 s, from 8 s. Plan 9's rule is at least 6 s for any toast, so this is the shortest pause that keeps it.)

- `ToastAction` gains `showMillis`, defaulting to `TOAST_WITH_ACTION_MS` (8 s), so every other toast with an action is unchanged. `ToastLayer` uses it.
- Shopping gets `TICK_UNDO_MS = 6_000`. It replaces `TOAST_WITH_ACTION_MS` in the tick's toast and the Undo window (`ShoppingActions`, its `latestTick` and the KDoc on `tick`), the refused-item rule's `undoGoneAt` in `ShoppingSync`, the KDoc on `ShoppingView`'s undoable tick, and the tests that wait for it: `ShoppingActionsTest` (the Undo window) and `ShoppingSyncTest` (the two refused-item cases). The row goes when its Undo goes (10 ruling 10).

### 4.2 Plan 18's minors (item 11)
Three of them look already fixed, wholly or in part, by commits made the same day the list was written (11c, 11j for the pads, 11r). For those the plan's first step is a test that proves it; if the test fails, the fix below applies.

**Sound generators** (`tools/sound/`)
- **11a.** The normalise-and-write code moves to one module, `tools/sound/wav_out.py`, which the four generators import. It scales by peak (as the chime does) or by loudness with a peak cap (as the others do), each generator choosing its own, so re-running all four rewrites the chime, the organ and the music box byte for byte. Only Happy Birthday changes (11b).
- **11b.** Happy Birthday reaches the chime's loudness. A gentle limiter in `wav_out.py` takes the note onsets down before the gain, applied only where the peak cap would otherwise win, so the loudness target is met with the peak still inside the headroom. **(Claude's call.)** Checked by ear at the walk-through for any harshness.

**Sound library** (`core/sound/.../SoundLibrary.kt`, `Sounds.kt`)
- **11c.** The first folder read can't replace a newer one. Today's newest-read-wins counter appears to cover it; a test that holds the first read back while a later refresh lands proves it.
- **11d.** The library says when its first read has landed. Until then Settings shows a chosen tablet sound by its name with no note, instead of "Not on this tablet any more" (Settings › Door and the sound buttons in Settings › Modes).
- **11e.** A read that fails is caught: logged by class name, the last list kept, and the first read counted as landed.
- **11f.** The shared-name check ignores case, and runs on the cleaned name (§3.6).
- **11g.** A theme folder is found whatever its case (`Halloween/` as well as `halloween/`).
- **11h.** Names must be unique within the main folder and within each theme folder, not across them, so a file in `halloween/` is no longer dropped for sharing a name with one outside it. Settings shows the theme one with "On this tablet · Halloween". **(Claude's call.)**

**Sound player** (`core/sound/.../SoundPlayer.kt`, `capability/party/.../PadTaps.kt`)
- **11i.** A tablet doorbell sound that fails after it starts gives way to the classic chime once, unless another ring has started since.
- **11j.** Opening a sound happens off the caller's thread for every play: the plays become `suspend` and open on an injected IO dispatcher. Settings' Play chime no longer opens a file on the main thread. `PadTaps` drops its own `withContext`.
- **11k.** A stop tap while a pad is still opening stops it: stopping counts, as a ring does, and a pad that opened after a stop doesn't start. A second tap on the same pad while it's opening counts as a stop (`PadTaps` knows which pad is opening).
- **11l.** A pad doesn't start while the doorbell's own sound is opening or playing, whatever the pad gate says.

**Pads** (`capability/party/.../PadGate.kt`, `PadTaps.kt`, `core/ui/.../HouseMode.kt`)
- **11m.** The gate is read again on each minute boundary, so quiet hours and night start and end on the minute.
- **11n.** Until the gate's first read, pads are drawn faded with no line under them. A tap was already checked against the gate.
- **11o.** The tray's small tiles (the pads and Taekwondo) flash `accentSoft` for 150 ms on a tap, as the redesign's handoff ("Actions and sounds fire once, with a 150 ms press flash") and 18 §4.4 ask. Mode tiles toggle and show their own state instead, which the handoff gives them; they don't flash. **(Claude's call:** reading the handoff that way.)
- **11p.** A Play in Settings that fails says so in a toast: "Couldn’t play {name}. Copy the file to the tablet’s sounds folder again." for a tablet sound, "Couldn’t play {name}. Restart Culvery and try again." for a built-in one. **(Claude's call:** the wording.)

**Door** (`capability/door/.../DoorSettings.kt`)
- **11q.** A stored doorbell choice of Happy Birthday plays the classic chime, as a gone file does. Happy Birthday still plays on a birthday through Follow the theme.
- **11r.** `goneName` and `goneFileName` are already one function, `soundNameFromId` (df6744cb). Nothing to do but close it.

**Tests**
- **11s.** The door module's tests and `StoredModePageTest` are run under full-project load (`./gradlew test` three times). Each flaky test's cause is found and fixed: real-time waits become test-clock waits or bounded `waitUntil`s. No retries and no `@Ignore`.
- **11t.** `testSounds()` (`capability/door/src/test/.../TestDoor.kt`) and `DoorQuietnessTest`'s `first { }` waits get timeouts (5 s), so a hang fails with a message.
- **11u.** `home_tray_full` (`app/src/test/.../ShellScreenshotTest.kt`) passes the 2 modes that fit, and the tray fixture fails a test that asks for more than fit.
- **11v.** Plan 18's files pass the format check; `Context` is imported, not written out, in `BuiltInSoundTest` and `ChimeTest`; `SoundPlayer`'s lock comment becomes one line.

**The private sound script (11w, 11x, 11y)** is fixed in the private notes, as the private companion says. It never enters the repo.

### 4.3 Calendar M2: Undo against a create that's landing (item 12)
Two faults: an Undo that races a create landing at that moment leaves the event on the week until the next sync; and what Undo remembers is lost on restart, so a create that later lands stays, and its title is remembered.

**The drain's create, step by step** (`CalendarSync.deliver`; `CalendarWriteLock` is the lock Undo already holds):
1. Under the lock: check the create row is still queued (Undo may have dropped it), mark its key as on its way, release the lock.
2. Send the create, with no lock held.
3. Under the lock: if Undo has taken the key, mark the create as a stray; else store the event and complete the row. Release the lock.
4. With no lock held: delete a stray (now, or on the next pass if that fails).

No network call runs under the lock, and no locked step calls the drain's `drop()`, which takes the lock itself (the lock isn't re-entrant).

**Undo** (`CalendarEditor.undo`, under the lock as now): a create that isn't on its way is dropped and its key let go at once. One that is on its way leaves the key to the drain, which settles it in step 3.

**Restart.** `UndoneKeys` keeps its in-memory map and writes it through to the calendar database (version 9 to 10, a new table, with a migration and its `MigrationTestHelper` test). It depends on the DAO, not on `CalendarStore`, and loads the table before the first drain pass. After a restart nothing is on its way, so each stored key is treated as a stray: the next drain deletes it, where gone counts as done. **(Claude's call.)**

**Sync** (`CalendarSync`) leaves out any event whose id is a stray waiting to be deleted, and, for a series, every instance of it (an instance whose recurring event id is that key, or whose id starts with the key and `_`). So it isn't shown or remembered. **(Claude's call.)**

### 4.4 Calendar M3: a counted series (item 13)
The detail sheet's "· 12 times · last on …" no longer lists every date to find the last (`Repeats.kt`, `Recurrence.kt`).
- Daily, weekly, fortnightly and monthly-on-a-weekday ("the last Thursday", "the 2nd Tuesday") series always have a date in each period, so their last date is worked out directly.
- Monthly-on-a-date and yearly series can skip a month or a year (the 31st, 29 February). They're counted only up to 1,000 times. Above that the label is the count alone, with no last date: "Every month, on the 31st · 10,000 times". **(Claude's call:** 1,000, and writing counts of 1,000 or more with a comma.)

### 4.5 The Music card with nothing playing (item 16)
Closed apart from one change (the user, 2026-11-09). Since 51766191 the volume pill is gone while nothing plays, under the same rule the card uses for "Nothing playing" (`HomePick.nothingPlaying`). There's no mute button.

What's left: "Shuffle everything" loses its narrow padding (`compact`), now that the row has room, and keeps its own width rather than stretching across the card. The card's screenshots and `theCardFitsTheSideBySideRow` are re-recorded and still pass.

## 5. Checks

### 5.1 Tests
Each security fix has the tests the private companion lists. The test certificates reuse the Hue tests' keystores and their `TestCerts` pattern; no new test library. In public terms:
- M2: Settings' close signs out, in each way it closes; Choose home app signs out first; a wall session from before Settings isn't touched by the view model's start.
- N1: http still links with no certificate remembered; https shows the fingerprint and asks the PIN before the token is sent; a refused PIN saves nothing; the token carries over on the move to https and when the certificate matches; https with no port is 443; http is refused once linked over https, including when only the remembered certificate is stored; a changed certificate shows its line on the rows and the page and asks the PIN at the next Check; Forget asks the PIN and clears everything; the remembered-certificate calls never reuse the first read's connection.
- N2: scheme-changing redirects aren't followed by any internet client, Open-Meteo's included; the council's off-site links and redirects are refused, with the council's fake site.
- N3, N4: the PIN pad shows before the dialog or the write; refused, nothing happens; switching "Needs a PIN" on never asks; another edit to a mode keeps its PIN setting.
- N5, N6, N9, N10: as §3.6, §3.7, §3.10 and §3.11.
- Item 5: the toast, the Undo and the row all end at 6 s; another toast with an action still lasts 8 s.
- Item 11: one test per minor where code changes, each failing without its fix; the generators re-run with only `happy_birthday.wav` changed.
- Calendar M2: the race in both orders; an Undo of a create not on its way lets the key go; a stored stray survives a restart and is deleted on the next pass; a stray series' instances aren't shown; the migration test.
- Calendar M3: COUNT=10000 daily, weekly, monthly on the last Thursday, monthly on the 31st and yearly on 29 February. The label is right where it shows a last date, is the count alone above the cap, and the call is fast.
- `LogHygieneTest` passes with any new log lines.

### 5.2 Emulator walk-through
- Settings: open with the Admin PIN, close, then reopen: it asks again. Door history from the door card while Settings is open: Settings goes and so does the session.
- Settings › Display › Allow the camera (emulator with the camera not yet allowed): the PIN pad says "Allow the camera"; cancel and nothing opens.
- Settings › Modes and Settings › Plugs: "Needs a PIN" off asks; on doesn't. Rename a mode that needs a PIN: it still does.
- Settings › Home Assistant with the emulator's existing link: Check still links, the hoover row and the Plugs tab work. Forget: the PIN pad, then not linked; link it again.
- Shopping: tick Milk; the row and the Undo go together after about 6 s; Undo within it brings Milk back.
- Home with nothing playing: Play and "Shuffle everything", no pill; play something and the pill is back.
- Tray: a pad tap flashes; a second tap on a pad while it opens stops it; pads faded with no line for the moment after start; Settings › Door with a chosen tablet sound shows no "Not on this tablet any more" while starting.
- Sounds: push a file whose name has formatting characters, and one with a 60-character name, to the emulator's sounds folder; Settings shows them clean and cut. A file in `Halloween/` with the same name as one in the main folder shows both. A broken file's Play in Settings shows its toast.
- Happy Birthday, the chime and the music box by ear, one after another: Happy Birthday as loud as the chime, with no harshness.
- Calendar: add an event, Undo it at once, several times; the week never keeps it. A phone's long counted series shows its Repeats line.

### 5.3 The tablet
Release install (`install -r`), then:
- Everything in §5.2 that needs the real wall: the PIN pads, the tray flash, the Music card.
- Allow the camera: after Android's dialog, Allow and then Deny, the wall pins again.
- Choose home app: back from Android's chooser, the next Settings action asks the PIN.
- The existing Home Assistant link works on the day of the install.
- **With the user, after the checks above:** the household sets up https for Home Assistant, with Claude walking them through it and checking the steps the private companion lists. Then Settings › Home Assistant: the https address, Check, compare the fingerprint on the PIN pad, the Admin PIN; the hoover row, the Plugs tab and Holiday's link work. The old http address is refused. Forget and link again once.
- The log while linking and forgetting shows no token, fingerprint or address.
- The private companion's own tablet checks, including the user's hand check for I1.

## 6. Left out on purpose
- October's other open findings, and I1, N11, N13 (the user's scope).
- Setting up https on the Home Assistant side inside the build. It's the household's step, with Claude, at the end.
- A mute button on the Music card (item 16's ruling).
- TalkBack's longer toast time for the shopping tick. Today TalkBack can hold an action toast past the row's Undo window; Plan 20 leaves that as it is.

## 7. Claude's calls, for checking
- The fingerprint shows on the PIN pad before the token is sent, in lowercase hex in groups of four, through a new optional detail line on the PIN pad (§3.2).
- Forget keeps the chosen devices, and says "Home Assistant forgotten." (§3.2).
- The council's redirects are held to its own site and scheme (§3.3).
- `Address` and the connect screen's `Done` step get a plain text form as well (§3.11).
- The shopping tick's pause is 6 s, Plan 9's toast minimum (§4.1).
- Happy Birthday reaches the chime's loudness through a limiter on its onsets (§4.2, 11b).
- Same-named files in a theme folder and the main folder both show, the theme one labelled (§4.2, 11h).
- Mode tiles don't flash; only the small tray tiles do (§4.2, 11o).
- The wording of a failed Play in Settings (§4.2, 11p).
- Stored undone keys are all treated as strays after a restart, and sync leaves those events and their series' instances out (§4.3).
- Monthly-on-a-date and yearly series count up to 1,000 times for the last date; counts of 1,000 or more get a comma (§4.4).
