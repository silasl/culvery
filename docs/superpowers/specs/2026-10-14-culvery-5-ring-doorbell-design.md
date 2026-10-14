# Culvery 5: the Ring doorbell

Brainstormed 2026-10-14. The first outside service after the calendar and the weather, and the test of how later ones (Sonos, Hue, Arlo, the thermostat, Holiday mode) plug in. Follow-ups file: `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("Feature ideas", the Ring line).

The user ended 4e's family week early the same day: 4e merged to main, and four pending changes fold into this plan, built before Ring (§A). There are no fixed test periods any more: the tablet runs the latest release, and its logs are read whenever it is plugged in while work is under way.

## 1. Purpose and outcome

When someone presses the Ring doorbell, the wall tablet tells the room: a card over whatever is showing, with Ring's snapshot when it sends one, and a chime. A Door tab keeps today's and yesterday's rings and motion, with a badge for what nobody has looked at yet.

Done means: a press reaches the wall within a few seconds of Ring's own notification, while the tablet is pinned; motion is recorded without interrupting; the parser is fixed from real captures on the SM-T510 (§8.3); the four additions in §A built; a final review, a local merge, and the whole build installed on the tablet.

## 2. Decisions (the user, 2026-10-14)

1. **Job:** tell the room, keep a history, and make a sound. No live view or talk from the wall.
2. **Events:** a ring raises the card and the chime; motion goes into the history only.
3. **Sound:** Culvery plays its own chime, with a volume in Settings. The Ring app's own notification sound on the tablet is turned off at setup so the two don't double up.
4. **History:** a Door tab on the rail (slot 50, reserved for Security), today and yesterday.
5. **Badge:** unseen rings as a count, unseen motion alone as a dot; both clear when someone opens the Door tab; a ring whose card was dismissed with Got it counts as seen. The badge is a general rail feature any capability can use.
6. **Night:** quiet hours in Settings, 21:00–07:00 by default, fixed times rather than sunset. In quiet hours the card and badge still show; no chime.
7. **Culvery not in front** (the son using other apps): record only. No card, no chime, no "display over other apps" permission.
8. **Snapshots:** on the card and in the history, kept 48 hours in Culvery's private storage, then deleted with their entry.
9. **The card:** closes after 2 minutes or on Got it. A second ring while it shows refreshes it and chimes again.
10. **The card's layer:** its own alert layer above sheets, Settings and the wizard, below the PIN pad. It never replaces or closes an open sheet, so unsaved edits survive a ring.
11. **Ring sign-in on the tablet:** the household's call. The spec's advice is a **Ring Shared User** (notifications and live view, no settings, no device removal, no billing), so the owner's login never goes on the son's tablet.
12. **Samples:** the parser starts from best-guess fixtures with a fallback, and is fixed from real captures on the tablet after 4e closes (§8.3).
13. **Route:** read the Ring app's notifications on the tablet (approach A below). No Ring credentials in Culvery.

### Approaches weighed

- **A. Read Ring's notifications (chosen).** The Ring app on the tablet; a `NotificationListenerService` in Culvery, allowed once in Android's Notification access. No credentials, no server, works while pinned. Depends on Ring's notification format; §6 covers drift.
- **B. Unofficial Ring login** (as `ring-client-api` does). Richer data, but a Ring password and refresh token on the wall, a private API that changes without notice, and against Ring's terms.
- **C. A bridge** (IFTTT or Home Assistant pushing to the tablet). Stable triggers, but a hub or cloud account and an inbound channel to the tablet: a whole system for one doorbell.

A keeps the provider seam: if Ring ever publishes an API, a second provider replaces the notification reader without touching the card, tab, badge or chime.

## 3. Modules

### 3.1 `:capability:door`

The capability: `id = "door"`, label "Door", icon `doorbell`, rail order 50. `hasTab` is true once a Ring connection exists (§5.5). It owns:

- `DoorEvent`: id, time (epoch millis), kind `RING` or `MOTION`, device name as Ring gives it, `unrecognised` (true for a Ring alert the parser couldn't place; shown as a ring, §4), snapshot file or none, seen.
- `DoorEventSink`: what a provider calls with what it heard (§4.1–4.2).
- The Room store `door.db` (one table) and the snapshot files in `files/door/`.
- The alert decision, the chime, the card, the Door tab, the badge and Settings › Door.

### 3.2 `:provider:door-ring`

- `RingNotificationListener`: the `NotificationListenerService`, declared in this module's manifest (merged into the app), guarded by `BIND_NOTIFICATION_LISTENER_SERVICE`.
- A thin adapter from `StatusBarNotification` to `RingNotice` (package, key, channel id, category, title, text, big text, picture or none, post time), so the parser needs no Android.
- `RingParser`: `RingNotice` → `Ring`, `Motion`, `UnknownRingAlert` or `Ignore` (Ring's app-update, account and promotional notifications).
- `ProviderDescriptor` `door.ring`, "Ring". It never sees Ring credentials.

### 3.3 `:core:plugin`

Two additions every later capability can use:

- `Capability.badge: Flow<RailBadge?>`, default none; `RailBadge` is `Count(n)` or `Dot`, with the words TalkBack says.
- `AlertHost` (with `LocalAlertHost`): `show(content)` and `dismiss()` for the alert layer (§5.1). Separate from `OverlayHost`, whose `show` replaces the open sheet.

### 3.4 `:app`

- `NavRail` draws a tab's badge and adds its words to the tab's spoken label.
- The shell draws the alert layer between the overlays (sheets, Settings, the wizard) and the PIN pad.
- `KioskLifecycle.openHomeAppScreen` becomes `openSystemScreen(missing, launch)`: unpin, open a system screen, pin again on return; on `ActivityNotFoundException`, pin again and say `missing`. The home-app requests and Connect Ring's notification-access step both use it.

### 3.5 Backup

`door.db` and `files/door/` sit in the `database` and `file` domains, which 4e's data extraction rules (merged to main 2026-10-14) exclude from cloud backup and device transfer. No new rule.

## 4. Data flow

1. **Listen.** `onNotificationPosted` returns at once for any package but Ring's (`com.ringapp`). Nothing else on the tablet is read or stored. Removals are ignored.
2. **Parse.** The adapter builds a `RingNotice`; `RingParser` places it. `Ignore` stops here.
3. **Merge.** Events are keyed by the notification's `key`. A post with a key already seen in the last 2 minutes updates that event (Ring's rich notifications post text first, then again with the snapshot) and does not chime again. Otherwise a new event.
4. **Snapshot.** When the notice carries a picture, it is scaled to at most 640 px wide and saved as JPEG at `files/door/<eventId>.jpg`. A failed save keeps the event without a picture.
5. **Store and prune.** Each insert, and once a day, deletes events older than 48 hours and their snapshot files. Files with no event (left by a crash) go at the same time.
6. **Alert.** For a new `RING` (or an `UnknownRingAlert`), and only while Culvery's activity is resumed:
   - the card shows (or refreshes, if one is up: new time, device and snapshot) and its 2-minute timer restarts;
   - the chime plays unless it is quiet hours, or the chime played less than 10 seconds ago.
   While Culvery is not resumed the event is only stored. `MOTION` never alerts.
7. **Seen.** Opening the Door tab marks every event seen. Got it marks the card's ring seen. The 2-minute close leaves it unseen.
8. **Badge.** Unseen rings (unrecognised ones included) → `Count(n)`; no unseen rings but unseen motion → `Dot`; else none.

### 4.1 The chime

A short bundled sound played with `AudioAttributes.USAGE_NOTIFICATION_EVENT`. Culvery's volume setting (default 100 %) scales it within the tablet's notification volume, so the tablet's own volume and Do Not Disturb still apply. Quiet hours use the household's time zone and may cross midnight (From 21:00, Until 07:00 means 21:00–06:59).

### 4.2 Last heard

The store keeps the time of the last Ring notice of any kind (including `Ignore`), for "Last heard from Ring" (§6).

## 5. Screens

Layout and copy are provisional until the user's end-of-v1 design walk-through.

### 5.1 The door card

On the alert layer, top centre, about half the screen wide: the doorbell icon, **Someone's at the door** (an unrecognised alert: **Ring alert**), the device name as Ring gives it, the time, the snapshot if any, and one button, **Got it**. TalkBack announces it as urgent. The card doesn't count as a touch for the sheet or Settings beneath, so their own timeouts run on; touching the card doesn't reach them. The PIN pad draws above it.

### 5.2 The Door tab

**Today** and **Yesterday**, newest first. Each row: the time, "Ring" or "Motion" ("Ring alert" for unrecognised), the device name, a snapshot thumbnail. Tapping a thumbnail opens it full size in a sheet (`OverlayHost`). Empty: "No rings or motion today." A status line at the top shows only when something is wrong (§6). Open to everyone; no PIN.

### 5.3 The rail badge

A count bubble or a dot on the Door tab's icon. TalkBack: "Door, 2 unseen rings", "Door, 1 unseen ring", "Door, new motion".

### 5.4 Settings › Door

Admin-only, like the rest of Settings.

- **Chime volume:** a slider, and **Play chime**.
- **Quiet hours:** a switch (on by default), From and Until.
- **Ring:** the status (§6), "Last heard from Ring: Tuesday 14:02", and the connect steps (§5.5).
- **Check Ring's notifications:** a switch, off by default, that turns itself off after 30 minutes. While on, each Ring notice's shape is written to logcat (§8.3). In the release build so it can run on the tablet without a debug install.

### 5.5 Connecting Ring

From Settings › Door, not the first-run wizard (Ring is optional). Two steps, each ticked when done:

1. **Ring app installed and signed in.** Culvery checks the package is installed. The hint: sign in as a Ring Shared User, and turn off the Ring app's notification sound.
2. **Allow notification access.** Opens Android's notification-access screen for Culvery through `openSystemScreen` (§3.4).

With both ticked, the `door.ring` connection is saved and the Door tab appears. Turning access off later leaves the connection and shows the status (§6).

## 6. When things go wrong

The Door tab's status line and Settings › Door show the same message.

| What happens | How Culvery notices | What it says |
|---|---|---|
| Notification access off | On each resume, and on `onListenerDisconnected` | "Culvery can't see Ring's notifications. Allow notification access in Settings › Door." |
| Ring app removed | Package check on each resume | "The Ring app isn't on this tablet. Install it and sign in, then check Settings › Door." |
| Ring's format changed | The last 3 Ring alerts were all unrecognised | "Culvery can't fully read Ring's notifications, so rings show as 'Ring alert'." They still alert. |
| Listener unbound by Android | `onListenerDisconnected` | `requestRebind`; no message unless access is also off |
| Snapshot not saved | Exception on save | Event kept without a picture; a log line without personal data |

Culvery can't see the Ring app signed out or Ring's notifications turned off; "Last heard from Ring" makes a long silence visible.

**Privacy.** Only Ring's package is read. Nothing goes over the network. Logs carry the parse outcome and kind only, never title, text or device names (a device can be named after a person). The capture (§8.3) is the one exception, off by default, on for 30 minutes at most, to logcat only.

## 7. Tests

- **Parser** (JVM): fixtures for a ring, motion, a ring then its snapshot update, an unknown Ring alert, and Ring's ignorable notifications. Best guesses until §8.3 replaces them with redacted captures.
- **Logic:** merge on key (within and after 2 minutes); prune with snapshot files and orphans; unseen counts and the dot; Got it vs the 2-minute close; quiet hours across midnight; the 10-second chime limit; record-only when not resumed; the three-unrecognised status; last heard.
- **UI** (Robolectric; Roborazzi for the card, the tab, the badge and Settings › Door): the card over an edited event sheet leaves the edits and the sheet's timer alone; the PIN pad above the card; the card's and the badge's TalkBack words; the tab's empty state.
- **`openSystemScreen`:** the existing home-app tests pass unchanged; a new test for the notification-access path, including pinning again when no screen opens.
- **Debug fake rings:** debug builds also read notifications from `com.android.shell` tagged `culvery-fake-ring` as Ring's, so `adb shell cmd notification post …` rings the emulator.

## 8. Rollout

### 8.1 Emulator walk-through

On a **second AVD**. The current emulator holds a release build with the household, and a debug install would need an uninstall that wipes it. The Ring app isn't needed (fake rings, §7), so the Play services fault on the API 35 image doesn't matter.

### 8.2 Before the tablet

With the household's yes for the Ring app. 4e's tablet rules apply: save and restore every setting, never touch other apps' data, install signed releases with `install -r`, never uninstall Culvery.

### 8.3 On the tablet

1. The household installs the Ring app and signs in (a Shared User is the advice), and turns off its notification sound.
2. Install the release build; connect Ring in Settings › Door.
3. Turn on **Check Ring's notifications**; the user presses the bell and walks past the camera. Read the captures over the cable. Anything copied to a file uses placeholders ([device], [person]) and passes the names check first.
4. Fix the parser and its fixtures from the captures; rebuild and install.
5. From then on, the tablet's logs are read whenever it is plugged in while work is under way.

## A. Additions folded into Plan 5

Built first, before Ring; small and independent of it. Sizes and wording are starting values to judge on the wall.

### A.1 The logo on the rail

The bird mark, about 48 dp, above the Home tab. Not a button; TalkBack skips it.

### A.2 Home: a smaller clock, larger Today events

- The clock from 104 to 88 sp; the date unchanged; the header shrinks to match.
- Today's rows: the title from 17 to 20 sp (`HhType.rowTitle` or a Today-only style, whichever keeps other rows as they are), the time line from 14 to 16 sp.

### A.3 A failed Update now says why

After a failed pass the week's line says what went wrong and what to do, from the outcome the sync pass already reports:

| Outcome | The line says |
|---|---|
| Offline | "The tablet is offline. Check the Wi-Fi, then Update now." |
| Google didn't answer | "Google didn't answer. Try Update now in a few minutes." |
| Sign-in needed | "Google needs you to sign in again. Reconnect in Settings › Calendars." (the Reconnect chip shows as now) |

Update now stays beside it. No new network code.

### A.4 Repeating events on the tablet

New events only.

- **Time:** chips **All day** (default) and **Pick time…**; a picked time shows the Length chips. The Morning, Afternoon and Evening slots go.
- **Repeats:** Never (default), Every day, Every week, Every 2 weeks, Every month, Every year.
- **Ends** (only when it repeats): Never, or On a date (date picker; not before the event's day).
- **Save** writes `RRULE:FREQ=…;INTERVAL=n` plus `UNTIL` for an end date: a date (`UNTIL=20261231`) for an all-day series, the end of that day in the household zone as UTC (`UNTIL=20261231T235959Z` in GMT; an hour earlier in BST) for a timed one. What it writes reads back through `repeatsLabel` as a simple label, never "Yes".
- Editing an existing series' time or title stays on a phone; the detail sheet's line about it stays.

Tests: the RRULE for each choice and both UNTIL forms; round trip through `repeatsLabel`; the chips' defaults; Ends hidden for Never; an end date before the event's day refused.

## 9. Not in this plan

- Live view or talk from the wall.
- Arlo, Sonos, Hue, the thermostat, Holiday mode (each its own plan; Arlo would be a second provider under Door).
- A Ring login in Culvery (approach B).
- Drawing over other apps.
- Editing a repeating series' time or title on the tablet (§A.4).
- Per-device settings (which Ring devices alert); every Ring doorbell alerts and every camera's motion is recorded.
