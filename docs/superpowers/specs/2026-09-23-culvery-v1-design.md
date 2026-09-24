# Culvery v1 — Design Spec

**Date:** 2026-09-23
**Status:** Draft for review
**Visual reference:** [`docs/design/house_hub_handoff/`](../../design/house_hub_handoff/README.md) (high-fidelity; tokens, type, spacing and component specs there are authoritative for anything this spec does not override)

## 1. Intent

A full-screen, wall-mounted Android tablet app for running a family home. It is a personal project, but it must not be bespoke to one house:

- **Modular.** Each external service is a self-contained code module. Adding a service means writing a module and registering it — no edits to other modules.
- **Portable.** Nothing about a specific household (people, location, calendars, rooms) or any credential is compiled in. Moving house, or giving the app to a friend, means running first-run setup again.
- **Trivial to use, safe with kids.** Viewing never needs a PIN. Anything that changes state asks for a personal PIN, which both authorises the action and records who did it.

v1 ships the platform (shell, setup, settings, people & roles, kiosk, plugin contracts) with two capabilities: **Calendar** (Google read/write + ICS read-only) and **Weather** (Open-Meteo).

## 2. Target device

- Samsung Galaxy Tab A 10.1 (2019, SM-T510): Android 11 (API 30), 1920×1200 @ 1.5× = **1280×800 dp** (the design canvas), 2 GB RAM.
- `minSdk 29`, `compileSdk`/`targetSdk 35`. Landscape only. (Targeting 36 would make Android 16+ ignore the landscape lock on large screens; revisit when leaving the SM-T510.)
- Performance budget: flat colours, no blur, no layered translucency beyond the design's alpha chips.

## 3. Core concepts

| Concept | Meaning | Owned by |
|---|---|---|
| **Household** | People and location (name, lat/lon, timezone). Rooms are added when a capability needs them. | `:core:household` |
| **Person** | Name, colour, optional PIN, role. A built-in **Family** pseudo-person exists for shared events; it has no PIN or role. | `:core:household` |
| **Role** | `ADMIN`, `ADULT`, `CHILD` — a bundle of permissions (§8). Stored on the person row. | `:core:household` (enum), `:core:access` (meaning) |
| **Permission** | A named action declared by a capability (e.g. `calendar.event.create`). | declared by `:capability:*`, enforced by `:core:access` |
| **Capability** | A kind of thing the home can do (Calendar, Weather; later Lights, Music, Climate, Security). Owns its tab, Home cards, permissions and (later) holiday routines. | `:capability:*` |
| **Provider** | A service module implementing one capability's contract (Google Calendar, ICS). Declares its features (e.g. `READ`, `WRITE`). | `:provider:*` |
| **Connection** | A user-configured instance of a provider (e.g. one Google account, one ICS URL). A provider may have many. Credentials are stored per connection. | `:core:plugin` |

## 4. Module layout

```
:app                          Activity, kiosk, nav rail, theme, setup & settings shell, DI wiring
:core:ui                      design tokens (light/dark), DM Sans, Material Symbols Rounded, shared components
:core:household               Person (incl. role + PIN hash), Location, household repository, last-Admin rule
:core:access                  permission registry, PIN hashing, lockout, unlock session, PIN pad UI
:core:plugin                  ProviderDescriptor, Connection, ConnectionHealth, SecretStore, HomeCard contract
:capability:calendar          CalendarProvider contract, aggregation, cache, outbox, Calendar tab, Home cards, event editor
:capability:weather           WeatherProvider contract, cache, header widget, Forecast card
:provider:calendar-google     Google Calendar API v3 (read/write)
:provider:calendar-ics        ICS URL subscription (read-only)
:provider:calendar-fake       debug-only sample data matching the design (read/write, in-memory)
:provider:weather-openmeteo   Open-Meteo forecast + geocoding
```

**Dependency rules** (enforced by Gradle module visibility):

- `:provider:*` → `:core:*` and its own `:capability:*` contract only.
- `:capability:*` → `:core:*` only. Never a provider.
- `:app` → everything; it only wires, never contains capability logic.
- Providers and permission declarations register via Hilt multibinding (`@IntoSet`). Capabilities receive `Set<XProvider>` and never enumerate providers by name.

## 5. Contracts

Indicative shapes; exact signatures are settled in the implementation plan.

```kotlin
// :core:plugin
data class ProviderDescriptor(
    val id: String,               // stable, e.g. "calendar.google"
    val displayName: String,
    val icon: IconRef,
    val features: Set<Feature>,   // READ, WRITE
)
data class Connection(val id: String, val providerId: String, val label: String, val config: Map<String, String>)
sealed interface ConnectionHealth {
    data object Ok : ConnectionHealth
    data object Unreachable : ConnectionHealth
    data object NeedsSignIn : ConnectionHealth
    data class Error(val message: String) : ConnectionHealth
}

interface SecretStore {                     // Keystore-backed, per-connection
    suspend fun put(connectionId: String, key: String, value: String)
    suspend fun get(connectionId: String, key: String): String?
    suspend fun clear(connectionId: String)
}

interface HomeCardContributor {
    fun cards(): Flow<List<HomeCard>>        // HomeCard = size hint (TALL | WIDE | REGULAR) + priority + @Composable content
}

// :core:access
data class PermissionDef(val id: String, val label: String, val defaultRoles: Set<Role>)
interface PermissionSource { val permissions: List<PermissionDef> }   // one per capability, @IntoSet

interface AccessControl {
    val session: StateFlow<Person?>                        // currently identified person, null when locked
    suspend fun authorise(permission: String): Person?     // shows PIN pad if needed; null if cancelled/denied
    fun lock()
}

// :capability:calendar
interface CalendarProvider {
    val descriptor: ProviderDescriptor
    @Composable fun ConnectScreen(onConnected: (Connection) -> Unit, onCancel: () -> Unit)
    suspend fun sources(conn: Connection): List<CalendarSource>   // each source reports writable: Boolean
    suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult
    // WRITE feature only:
    suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEventRef
    suspend fun update(conn: Connection, source: CalendarSource, ref: RemoteEventRef, draft: EventDraft)
    suspend fun delete(conn: Connection, source: CalendarSource, ref: RemoteEventRef)
}

data class EventDraft(
    val title: String,
    val start: EventTime,          // Timed(instant) | AllDay(localDate)
    val end: EventTime,
    val forPerson: PersonId,       // Family allowed
    val createdBy: PersonId,
)
```

**Behavioural contract for `CalendarProvider`** (verified by the shared contract test suite, §11):

- Recurring events are returned **expanded** into concrete occurrences within `range`, honouring exceptions and overrides, and marked `recurring = true`.
- Timed events carry an absolute instant; all-day events carry a local date with no time.
- Deleted/cancelled occurrences are reported as removals in incremental results.
- Events carry `forPerson` / `createdBy` when the provider can store them (Google: yes; ICS: never). Missing tags resolve to the source's mapped person.
- A write followed by a sync returns the written event with identical title, times and tags.
- Auth failures throw a typed `NeedsSignInException`; network failures throw `UnreachableException`. No other exception escapes.

## 6. Calendar model

- **Sources.** Every calendar source from every connection is mapped in setup to a person or Family, and shown or hidden.
- **Master calendar.** Exactly one writable source is designated the household's master calendar. It is the **only** calendar the tablet writes to. In v1 it must come from a provider with `WRITE` (Google). All other sources are read-only on the tablet regardless of provider capability.
- **Person tagging (Google).** Events written by Culvery carry Google `extendedProperties.private`:
  - `culvery.person` = person id the event is for (or `family`)
  - `culvery.createdBy` = person id of the author
  - `colorId` is also set to the nearest Google event colour to the person's colour, so phones show a matching colour. Person ids are household-local UUIDs; names are never written.
- **Untagged master events** (added from a phone) display as Family. Any Adult can assign them to a person on the tablet, which writes the tag.
- **Editable on the tablet:** non-recurring events on the master calendar. Recurring events are view-only on the tablet in v1 (the detail sheet says "Edit in Google Calendar").

## 7. Storage, secrets and sync

**Storage**
- **Room**, one database per module that stores data: `household.db` (people with role + PIN hash, location); each capability its own (e.g. `calendar.db`: connections, source mappings, master-calendar pointer, cached events, outbox, sync cursors; `weather.db`). A capability never edits another module's schema.
- **Lockout counter:** a small `SharedPreferences` file in `:core:access`.
- **Secrets** (OAuth tokens; later app passwords): encrypted with Tink AEAD, key held in Android Keystore, in a DataStore file separate from Room.
- **Not used:** `androidx.security:security-crypto` / `EncryptedSharedPreferences` (deprecated).
- **PINs:** salted PBKDF2 hash per person, never plaintext.

**Sync**
- In-app coroutine loops started with the Activity (the app is a foreground kiosk). No WorkManager in v1.
- **Calendar read:** each connection syncs independently every 5 min and on app start. Window: today −1 day to +14 days.
  - Google: incremental via `syncToken`; full resync on HTTP 410.
  - ICS: conditional GET (`ETag` / `If-Modified-Since`); parse and expand with ical4j.
- **Calendar write — save flow and outbox** (refined by the 2b-1 design): a save first tries the provider directly (≤10 s) while the sheet shows "Saving…". Accepted → local copy updated. Rejected → the sheet stays open with the error and the input kept. Offline or timed out → queued in a separate `outbox` table and shown with a syncing mark; the queue drains before each sync with backoff. A later rejection drops the change and a toast explains why. Auth failure pauses the queue and flags the connection `NeedsSignIn`.
- **Weather:** every 30 min. Supplies current conditions, daily high/low, 5-day forecast, and **sunrise/sunset**.
- UI reads only from Room via `Flow`. Network never blocks rendering.

## 8. People, roles and access

**Roles and permissions (v1)**

| Permission | Declared by | ADMIN | ADULT | CHILD |
|---|---|---|---|---|
| `settings.manage` (connections, household, master calendar) | core | ✓ | | |
| `people.manage` (add/edit people, roles, reset PINs) | core | ✓ | | |
| `kiosk.exit` | core | ✓ | | |
| `calendar.event.create` — any person | calendar | ✓ | ✓ | |
| `calendar.event.create.self` — only for themselves | calendar | ✓ | ✓ | ✓ |
| `calendar.event.edit` — any master-calendar event | calendar | ✓ | ✓ | |
| `calendar.event.edit.own` — only events they created | calendar | ✓ | ✓ | ✓ |

Role → permission bundles come from each `PermissionDef.defaultRoles`; they are not editable in v1. Future capabilities add rows (e.g. `holiday.start`, `security.disarm`) without touching `:core:access`.

**Identification & session**
- PINs are exactly **4 digits** and **unique within the household**, so entering a PIN identifies the person. No "who are you?" step. The pad submits automatically on the 4th digit.
- Viewing never requires a PIN. Any mutating action calls `AccessControl.authorise(permission)`:
  - If someone is identified and has the permission → proceed as them.
  - Otherwise → PIN pad. A valid PIN without the permission shows "Sam can't do that".
  - **Fresh-PIN permissions** (`kiosk.exit`, `people.manage`) always show the PIN pad, even during an active session.
- Session lasts **2 min after the last authorised action** (a PIN-gated action that succeeded; touching the screen does not extend it), then locks (amended by the 2b-1 design, per hand-off §7). While signed in, the status bar shows `account_circle · {name} · {role}` and a "Sign out" link that locks immediately.
- People without a PIN can be tagged on events but cannot act.
- **Lockout:** 5 consecutive wrong PINs → 30 s lockout, doubling per further failure up to 16 min. Reset only by a PIN that is *authorised* for the requested action (a valid PIN that isn't allowed neither resets nor counts). Per device, survives restart.
- **Settings closes** when the session ends.
- **Security posture:** this is kid-proofing, not strong authentication. Documented as such.
- **Recovery:** an Admin can reset anyone's PIN. If every Admin forgets theirs, clear app data (wipes all configuration). At least one Admin with a PIN must always exist — the last Admin cannot be demoted or deleted.

## 9. UI

### 9.1 Shell
- **Nav rail** (per hand-off): Home first, then each capability with ≥1 connection, in fixed order Calendar → Lights → Music → Climate → Security. v1 shows Home + Calendar.
- **Signed-in indicator** in the status bar (hand-off §7), not the rail. The Plan 1 rail chip is removed in 2b-1.
- **Rail footer:** gear button (Settings, `settings.manage`) occupying the hand-off's Holiday button slot. Holiday button is out of v1.
- **Theme:** auto switches to dark at local sunset and light at sunrise (from weather data); falls back to 07:00/19:00 if weather has never synced. 400 ms colour transition. Status-bar theme preview from the hand-off is kept.

### 9.2 Home
- Header per hand-off: clock (104 sp), date, weather block. Indoor-temperature block hidden until a Climate capability exists.
- Grid `1.15fr 1fr 1fr` × 2 rows, filled from `HomeCardContributor`s by size hint and priority:
  - **Today** (Calendar, TALL, col 1) — per hand-off, plus a **+** button in the card header.
  - **Coming up** (Calendar, WIDE, row 1 cols 2–3) — next 3 days grouped by day, in compact event rows (a smaller variant of the Today row, two per day, then "+N more") so three days fit in the WIDE card.
  - **Forecast** (Weather, REGULAR/WIDE, row 2) — next 5 days: day, icon, high/low.
- When later capabilities add cards (e.g. Scenes, WIDE with higher priority), placement is recomputed; no Home code changes.
- If no calendar connection exists, a "Connect a calendar" card (→ Settings) takes the Today slot.

### 9.3 Calendar tab
- Per hand-off §2 (week view, 7 columns, person legend, person-coloured chips, "synced x ago"). Person colours come from Household.
- **+** button in the header (next to the legend); tapping empty space in a day column opens quick-add with that day preset.
- Tapping an event opens the **event detail sheet**: title, time, for whom, created by, source calendar. Edit/Delete shown only when the event is editable (§6) — the permission check happens on tap, not on display.
- Rolling 7 days starting today (matches hand-off §7); no week navigation — the view never leaves the synced window.

### 9.4 Quick-add / edit sheet (new; hand-off sheet styling, right-side 600 dp)
Order is chosen so the common case is: tap +, type title, tap a person, tap Save.
1. **Title** — text field, focused on open, keyboard shown.
2. **Who** — person chips (Family + people). Preselected to the identified person if any, else Family. For a Child, only their own chip is enabled.
3. **Day** — chips: Today, Tomorrow, next 5 weekdays by name, "Pick date…" (date picker).
4. **Time** — chips: All day, Morning (09:00), Afternoon (14:00), Evening (18:00), "Pick time…"; duration chips 30 min / 1 h / 2 h (default 1 h). Hidden for All day.
5. **Save** — calls `authorise(calendar.event.create[.self])`; PIN pad appears here if nobody is identified. The authorised person becomes `createdBy`.

Recurring events, reminders, locations, attendees and descriptions are not offered in v1.

### 9.5 First-run setup (new; hand-off visual language)
Full-screen wizard, one step per screen, progress dots, Back/Next:
1. **Welcome**
2. **Home location** — town search via Open-Meteo geocoding → sets location + timezone.
3. **You** — the first person: name, colour, PIN (required). Created as Admin.
4. **Household** — add others: name, colour (design palette `#4CB387`, `#5B9BE0`, `#E07BA8`, `#E0A85B`, plus extras), role, optional PIN.
5. **Connect services** — providers grouped by capability. Choosing one shows its `ConnectScreen`; afterwards pick visible sources and map each to a person or Family.
6. **Master calendar** — pick one writable source (only shown if one exists; skippable, which hides all + buttons until set).
7. **Done**

### 9.6 Settings (`settings.manage` / `people.manage`)
Reuses setup screens: Location, People & roles (add/edit/remove, reset PIN), Connections (health, add, remove, reconnect, re-map sources), Master calendar, Exit kiosk (`kiosk.exit`).

### 9.7 Error / offline states (new)
- Cached content always shown.
- "Synced x ago" turns warning-toned past 30 min stale.
- Pending outbox writes show a small sync mark on the event; failed writes roll back with a snackbar.
- Connection with `NeedsSignIn` → chip on the Calendar tab header ("Google needs reconnecting") → Settings › Connections.

## 10. Kiosk

- Immersive (system bars hidden via `WindowInsetsControllerCompat`), `FLAG_KEEP_SCREEN_ON`, landscape lock.
- Screen pinning via `startLockTask()`; exit via Settings › Exit kiosk.
- Optional stronger lock: device-owner provisioning with a one-time `adb shell dpm set-device-owner` — documented, not required.

## 11. Testing

- **Unit (JUnit4 + Robolectric + Turbine):** event aggregation and person resolution (tagged, untagged, source-mapped), outbox ordering/backoff/rollback, permission resolution per role (incl. `.self` / `.own` rules), PIN hashing/uniqueness/session timeout/lockout, last-Admin protection, theme scheduling with a fake clock, Home card placement.
- **Provider contract suite:** abstract `CalendarProviderContractTest` in a shared test-fixtures module; every calendar provider subclasses it. Write-path tests run only for providers declaring `WRITE`.
  - ICS fixtures: weekly RRULE with EXDATE and RECURRENCE-ID override, all-day multi-day event, TZID vs floating vs UTC times, a real UK school-term and council-bin feed.
  - Google fixtures: recorded API JSON for list, incremental, cancelled occurrence, 410 resync, insert/patch/delete with extended properties.
- **Screenshot (Roborazzi):** Home, Calendar, quick-add sheet, event detail, PIN pad, setup steps — light and dark — using `:provider:calendar-fake`.
- **Manual:** install on the SM-T510; verify scale matches hand-off screenshots; add/edit/delete against a real test Google calendar.

## 12. Credentials and distribution

- No secrets or tokens in source or build config. Google sign-in on Android uses an OAuth client bound to package name + signing certificate SHA-1 in the developer's Google Cloud project.
- Scopes: `calendar.readonly` (list calendars, read events) + `calendar.events` (write events). The broader `calendar` scope is not requested.
- Distribution: signed release APK sideloaded. A friend installs it and connects their own accounts.
- **Risk — verify first:** calendar scopes are sensitive. An unverified app is capped at 100 users and shows an "unverified app" screen. Apps in **Testing** publishing status may have grants expire after 7 days. The first implementation task is a spike confirming how this applies to the Android `AuthorizationClient` flow and choosing the publishing status that avoids weekly re-sign-in.

## 13. Names

- App name **Culvery**; package `uk.co.siland.culvery`.

## 14. Out of scope for v1

Lights, Music, Climate, Security capabilities; Holiday mode and its rail button; Outlook and CalDAV/iCloud providers; writing to any calendar other than the master; creating or editing recurring events; reminders, locations, attendees, descriptions; editable role → permission mappings; screensaver and night dimming; WorkManager/background sync; multi-tablet sync; config export/import.

## 15. Future modules (planned order, each its own spec → plan cycle)

1. Outlook (Microsoft Graph) and CalDAV (iCloud and others) calendar providers — CalDAV/Graph could also serve as a master calendar via their own tagging mechanism (X-properties / Graph extended properties)
2. Lights capability + Philips Hue provider (introduces Rooms in Household)
3. Holiday mode (core orchestration; capabilities contribute routines and permissions)
4. Security capability + Arlo provider (flag: no official public API)
5. Music (Sonos), Climate (vendor TBD)
