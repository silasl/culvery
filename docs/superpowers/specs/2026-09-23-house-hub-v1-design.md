# House Hub v1 — Design Spec

**Date:** 2026-09-23
**Status:** Draft for review
**Visual reference:** [`docs/design/house_hub_handoff/`](../../design/house_hub_handoff/README.md) (high-fidelity; tokens, type, spacing and component specs there are authoritative for anything this spec does not override)

## 1. Intent

A full-screen, wall-mounted Android tablet app for running a family home. It is a personal project, but it must not be bespoke to one house:

- **Modular.** Each external service is a self-contained code module. Adding a service means writing a module and registering it — no edits to other modules.
- **Portable.** Nothing about a specific household (people, location, calendars, rooms) or any credential is compiled in. Moving house, or giving the app to a friend, means running first-run setup again.
- **Kid-proof enough.** Configuration and leaving the app sit behind an optional PIN.

v1 ships the platform (shell, setup, settings, PIN, kiosk, plugin contracts) with two capabilities: **Calendar** (Google + ICS providers) and **Weather** (Open-Meteo).

## 2. Target device

- Samsung Galaxy Tab A 10.1 (2019, SM-T510): Android 11 (API 30), 1920×1200 @ 1.5× = **1280×800 dp** (the design canvas), 2 GB RAM.
- `minSdk 29`, `targetSdk` = latest stable. Landscape only.
- Performance budget: flat colours, no blur, no layered translucency beyond the design's alpha chips.

## 3. Core concepts

| Concept | Meaning | Owned by |
|---|---|---|
| **Household** | People (name, colour) and location (name, lat/lon, timezone). Rooms are added when a capability needs them. | `:core:household` |
| **Capability** | A kind of thing the home can do (Calendar, Weather; later Lights, Music, Climate, Security). Owns its tab, its Home cards, and (later) its holiday routines. | `:capability:*` |
| **Provider** | A service module implementing one capability's contract (Google Calendar, ICS). Declares its features (e.g. `READ`, `WRITE`). | `:provider:*` |
| **Connection** | A user-configured instance of a provider (e.g. one Google account, one ICS URL). A provider may have many. Credentials are stored per connection. | `:core:plugin` |

## 4. Module layout

```
:app                          Activity, kiosk, nav rail, theme, setup & settings shell, DI wiring
:core:ui                      design tokens (light/dark), DM Sans, Material Symbols Rounded, shared components
:core:household               Person, Location, household repository
:core:plugin                  ProviderDescriptor, Connection, ConnectionHealth, SecretStore, PinGate, HomeCard contract
:capability:calendar          CalendarProvider contract, aggregation, cache, Calendar tab, Home cards
:capability:weather           WeatherProvider contract, cache, header widget, Forecast card
:provider:calendar-google     Google Calendar API v3
:provider:calendar-ics        ICS URL subscription (read-only)
:provider:calendar-fake       debug-only sample data matching the design
:provider:weather-openmeteo   Open-Meteo forecast + geocoding
```

**Dependency rules** (enforced by Gradle module visibility):

- `:provider:*` → `:core:*` and its own `:capability:*` contract only.
- `:capability:*` → `:core:*` only. Never a provider.
- `:app` → everything; it only wires, never contains capability logic.
- Providers register themselves via Hilt multibinding (`@IntoSet`). Capabilities receive `Set<XProvider>` and never enumerate providers by name.

## 5. Contracts

Indicative shapes; exact signatures are settled in the implementation plan.

```kotlin
// :core:plugin
data class ProviderDescriptor(
    val id: String,               // stable, e.g. "calendar.google"
    val displayName: String,
    val icon: IconRef,
    val features: Set<Feature>,
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

interface PinGate {
    suspend fun requireUnlock(): Boolean     // shows PIN pad if set and not within grace window
}

interface HomeCardContributor {
    fun cards(): Flow<List<HomeCard>>        // HomeCard = size hint (TALL | WIDE | REGULAR) + priority + @Composable content
}

// :capability:calendar
interface CalendarProvider {
    val descriptor: ProviderDescriptor
    @Composable fun ConnectScreen(onConnected: (Connection) -> Unit, onCancel: () -> Unit)
    suspend fun sources(conn: Connection): List<CalendarSource>
    suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult
}
```

**Behavioural contract for `CalendarProvider`** (verified by the shared contract test suite, §10):

- Recurring events are returned **expanded** into concrete occurrences within `range`, honouring exceptions and overrides.
- Timed events carry an absolute instant; all-day events carry a local date with no time.
- Deleted/cancelled occurrences are reported as removals in incremental results.
- Auth failures throw a typed `NeedsSignInException`; network failures throw `UnreachableException`. No other exception escapes.

## 6. Storage and secrets

- **Room** database: household, connections, calendar source → person mappings, cached events, cached weather, sync cursors, last-sync timestamps.
- **Secrets** (OAuth tokens; later app passwords): encrypted with Tink AEAD, key held in Android Keystore. Stored in a DataStore file separate from Room.
- **Not used:** `androidx.security:security-crypto` / `EncryptedSharedPreferences` (deprecated).
- **PIN:** stored as salted PBKDF2 hash, never plaintext.

## 7. Sync

- In-app coroutine loops, started with the Activity (the app is a foreground kiosk). No WorkManager in v1.
- **Calendar:** each connection syncs independently every 5 min and on app start. Window: today −1 day to +14 days.
  - Google: incremental via `syncToken`; full resync on HTTP 410.
  - ICS: conditional GET (`ETag` / `If-Modified-Since`); parse and expand with ical4j.
- **Weather:** every 30 min. Supplies current conditions, daily high/low, 5-day forecast, and **sunrise/sunset**.
- UI reads only from Room via `Flow`. Network never blocks rendering.

## 8. UI

### 8.1 Shell
- **Nav rail** (per hand-off): Home first, then each capability with ≥1 connection, in fixed order Calendar → Lights → Music → Climate → Security. v1 shows Home + Calendar.
- **Rail footer:** gear button (Settings, PIN-gated) occupying the hand-off's Holiday button slot. Holiday button is out of v1.
- **Theme:** auto switches to dark at local sunset and light at sunrise (from weather data); falls back to 07:00/19:00 if weather has never synced. 400 ms colour transition. Status-bar theme preview from the hand-off is kept.

### 8.2 Home
- Header per hand-off: clock (104 sp), date, weather block. Indoor-temperature block hidden until a Climate capability exists.
- Grid `1.15fr 1fr 1fr` × 2 rows, filled from `HomeCardContributor`s by size hint and priority:
  - **Today** (Calendar, TALL, col 1) — per hand-off.
  - **Coming up** (Calendar, WIDE, row 1 cols 2–3) — next 2–3 days grouped by day, same event-row style as Today.
  - **Forecast** (Weather, REGULAR/WIDE, row 2) — next 5 days: day, icon, high/low.
- When later capabilities add cards (e.g. Scenes, WIDE with higher priority), placement is recomputed; no Home code changes.

### 8.3 Calendar tab
Per hand-off §2 (week view, 7 columns, person legend, person-coloured chips, "synced x ago"). Person colours come from Household, not constants.

### 8.4 First-run setup (new; hand-off visual language)
Full-screen wizard, one step per screen, progress dots, Back/Next:
1. **Welcome**
2. **Home location** — town search via Open-Meteo geocoding → sets location + timezone.
3. **People** — add/edit names; colour chosen from the design palette (`#4CB387`, `#5B9BE0`, `#E07BA8`, `#E0A85B`, plus extras). A built-in "Family" entry always exists.
4. **PIN** — 4–6 digits, confirm; "Skip" allowed.
5. **Connect services** — providers grouped by capability. Choosing one shows its `ConnectScreen`; afterwards pick sources and assign each to a person/Family.
6. **Done**

### 8.5 Settings (PIN-gated)
Reuses setup screens: Household, People, Connections (list with health, add, remove, reconnect, re-map sources), PIN (set/change/remove), Exit kiosk.

### 8.6 PIN
- Numeric pad sheet, design styling. 2-minute grace window after success.
- Gates: Settings, Exit kiosk. Capabilities may call `PinGate.requireUnlock()` for sensitive actions (future: Holiday, alarm).
- 5 wrong attempts → 30 s lockout, doubling.
- Recovery: clear app data (removes all configuration). Documented in README.

### 8.7 Error / offline states (new)
- Cached content always shown.
- "Synced x ago" turns warning-toned past 30 min stale.
- Connection with `NeedsSignIn` → chip on the Calendar tab header ("Google needs reconnecting") → Settings › Connections (via PIN).
- No connections for a capability → its tab is absent. If Calendar has no connections, Home shows a "Connect a calendar" card (opens Settings via PIN) in the Today slot.

## 9. Kiosk

- Immersive (system bars hidden via `WindowInsetsControllerCompat`), `FLAG_KEEP_SCREEN_ON`, landscape lock.
- Screen pinning via `startLockTask()`; exit via Settings › Exit kiosk (PIN).
- Optional stronger lock: device-owner provisioning with a one-time `adb shell dpm set-device-owner` — documented, not required.

## 10. Testing

- **Unit (JUnit5 + Turbine):** event aggregation and person tagging, PIN hashing/grace/lockout, theme scheduling with a fake clock, Home card placement.
- **Provider contract suite:** abstract `CalendarProviderContractTest` in a shared test-fixtures module; every calendar provider subclasses it with its own fixtures.
  - ICS fixtures: weekly RRULE with EXDATE and RECURRENCE-ID override, all-day multi-day event, TZID vs floating vs UTC times, a real UK school-term and council-bin feed.
  - Google fixtures: recorded API JSON for list, incremental, cancelled occurrence, 410 resync.
- **Screenshot (Roborazzi):** Home, Calendar, setup steps, PIN pad — light and dark — using `:provider:calendar-fake`.
- **Manual:** install on the SM-T510; verify scale matches hand-off screenshots.

## 11. Credentials and distribution

- No secrets or tokens in source or build config. Google Sign-In on Android uses an OAuth client bound to package name + signing certificate SHA-1 in the developer's Google Cloud project.
- Distribution: signed release APK sideloaded. A friend installs it and connects their own accounts.
- **Risk — verify first:** `calendar.readonly` / `calendar.events` are sensitive scopes. An unverified app is capped at 100 users and shows an "unverified app" screen. Apps in **Testing** publishing status may have grants expire after 7 days. The first implementation task is a spike confirming how this applies to the Android `AuthorizationClient` flow and choosing the publishing status that avoids weekly re-sign-in.

## 12. Names

- App name **House Hub**; package `uk.co.siland.househub`.

## 13. Out of scope for v1

Lights, Music, Climate, Security capabilities; Holiday mode and its rail button; Outlook and CalDAV/iCloud providers; creating/editing events on the tablet; screensaver and night dimming; WorkManager/background sync; multi-tablet sync; config export/import.

## 14. Future modules (planned order, each its own spec → plan cycle)

1. Outlook (Microsoft Graph) and CalDAV (iCloud and others) calendar providers
2. Lights capability + Philips Hue provider (introduces Rooms in Household)
3. Holiday mode (core orchestration; capabilities contribute routines)
4. Security capability + Arlo provider (flag: no official public API)
5. Music (Sonos), Climate (vendor TBD)
