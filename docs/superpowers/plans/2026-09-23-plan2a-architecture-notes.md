# Plan 2a (Calendar, read path): architecture notes

These are inputs for writing the Plan 2a implementation plan. Where they conflict with the spec, the spec is authoritative. They record decisions agreed with the user and the controller's design choices for 2a.

## Scope agreed with the user
- Plan 2 is split. **2a is the read path** (this plan); 2b is the write path.
- **2a:** connection/provider contracts; calendar contract; `calendar.db` cache; sync loop; shared provider contract test suite; fake provider; Home cards (Today, Coming up, Connect a calendar); Calendar tab week view (incl. "synced x ago" and the NeedsSignIn chip); shell navigation from cards; module-boundary guard; Roborazzi screenshot tests; Plan 1 polish follow-ups.
- **Moved to 2b:** the event detail sheet (read and edit), quick-add/edit sheet, master calendar, outbox, create/update/delete on providers, `.self`/`.own` permission checks, the calendar `PermissionSource`. Tapping an event in 2a does nothing.
- **Design:** approach C.
  - The controller designs the simple 2a surfaces in the existing visual language: Coming up card, Connect card, empty and NeedsSignIn states.
  - Claude Design designs the two sheets for 2b (brief: `docs/design/brief-calendar-sheets.md`).
  - 2a's first UI task produces screenshots for the user to approve.
- **Real providers** (Google, ICS) are Plan 3. 2a exercises everything through a debug-only fake provider.

## Modules added
```
:capability:calendar          contract types, calendar.db, sync engine, repository, Capability impl, cards, week view
:capability:calendar-testkit  abstract CalendarProviderContractTest (Android library; providers use it via testImplementation)
:provider:calendar-fake       debug-only fake provider with the hand-off's sample data
```
Dependency rules (enforced by the new guard):
- `:core:*` must not depend on `:app`, `:capability:*` or `:provider:*`.
- `:capability:*` must not depend on `:provider:*`.
- `:provider:X-…` may depend only on `:core:*`, on `:capability:X` and on `:capability:X-testkit` (test only). A provider's capability is its name up to the first `-`.
- Use `ProjectDependency.path` (Gradle 8.11+). `dependencyProject` is deprecated and must not be used.

## :core:plugin additions
- `ProviderDescriptor(id: String, displayName: String, icon: String, features: Set<Feature>)`
- `enum class Feature { READ, WRITE }`
- `data class Connection(id: String, providerId: String, label: String, config: Map<String, String>)`
- `sealed interface ConnectionHealth { Ok, Unreachable, NeedsSignIn, Error(message) }`, as in spec §5
- `interface ShellNavigator { fun openTab(id: String); fun openSettings() }` plus `val LocalShellNavigator = staticCompositionLocalOf<ShellNavigator> { error(...) }`
- `fun interface Startable { fun start() }`: multibound `Set<Startable>`, started once from `CulveryApp.onCreate`. This is how a capability starts its background sync without `:app` knowing about it. Add `@Multibinds Set<Startable>` in `AppModule`.
- Plan 1 follow-up: `HhIcon` gets an optional `contentDescription: String? = null`. When it is non-null, set semantics `contentDescription`; otherwise keep `clearAndSetSemantics {}`. This change is in `:core:ui`.

## Shell wiring (:app)
- `ShellViewModel` implements navigation. `openTab(id)` equals `selectTab(id)`; `openSettings()` already exists and goes through `authorise`.
- `MainActivity` provides `LocalShellNavigator` around `CulveryShell`, backed by the VM.
- `CulveryApp.onCreate` calls `start()` on every injected `Startable`, then runs the seed.
- The debug seed (`app/src/debug/DebugSeed.kt`) is extended:
  - people Alex (ADMIN, PIN 1234, `#4CB387`), Sam (ADULT, 2468, `#5B9BE0`) and Mia (CHILD, 1357, `#E07BA8`), replacing the single "Admin";
  - a fake connection, with its four fake sources mapped to Alex, Sam, Mia and Family.
- `:app` uses `debugImplementation(project(":provider:calendar-fake"))` and `implementation(project(":capability:calendar"))`.
- Fix the Plan 1 follow-up: the seed checks for an active Admin rather than an empty household.

## Calendar contract (:capability:calendar, package `…capability.calendar`)
```kotlin
sealed interface EventTime { data class Timed(val instant: Instant) : EventTime; data class AllDay(val date: LocalDate) : EventTime }
data class CalendarSource(val id: String, val name: String, val writable: Boolean)
data class RemoteEvent(val remoteId: String, val title: String, val start: EventTime, val end: EventTime,
                       val recurring: Boolean, val forPerson: String? = null, val createdBy: String? = null)
data class DateRange(val start: LocalDate, val endExclusive: LocalDate)
@JvmInline value class SyncCursor(val value: String)
data class SyncResult(val upserts: List<RemoteEvent>, val removedIds: List<String>, val cursor: SyncCursor?, val fullReplace: Boolean)
class NeedsSignInException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
interface CalendarProvider {
    val descriptor: ProviderDescriptor
    @Composable fun ConnectScreen(onConnected: (Connection) -> Unit, onCancel: () -> Unit)
    suspend fun sources(conn: Connection): List<CalendarSource>
    suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult
}
```
- **All-day end** is exclusive (an all-day event on 23 Sep has start `AllDay(23)`, end `AllDay(24)`), matching Google and iCal. Document it on `RemoteEvent`.
- **`fullReplace = true`** means "the upserts are the complete set for this source and range; drop anything else". It is always true when the cursor is null.
- **Recurring events** come back already expanded. Each occurrence has a distinct `remoteId`.

## Contract test suite (:capability:calendar-testkit)
Abstract JUnit4 class `CalendarProviderContractTest`.
- **Subclasses provide:**
  - `provider()` and `connection()`
  - `range()` and `sourceWithEvents(): CalendarSource`
  - `outOfRangeEventTitle(): String?`: an event known to exist outside `range()`
  - `recurringTitle(): String?`
  - `simulateAuthFailure(): (() -> Unit)?` and `simulateUnreachable(): (() -> Unit)?` (null = not supported, skip that test)
- **Tests:**
  - `sourceIdsAreUniqueAndStable`: two `sources()` calls give equal id lists.
  - `syncReturnsOnlyEventsOverlappingRange`
  - `endIsNeverBeforeStart`
  - `allDayEventsUseExclusiveEndDates`: end is after start for all-day events.
  - `recurringOccurrencesHaveDistinctIds`
  - `firstSyncIsFullReplace`: cursor null in, `fullReplace` true out.
  - `syncWithReturnedCursorDoesNotRepeatUnchangedEvents`: skipped if the first cursor is null.
  - `authFailureThrowsNeedsSignIn`
  - `networkFailureThrowsUnreachable`
  - `noOtherExceptionEscapes`: covered by the two above.
- **Dependencies:** `api` junit, truth and kotlinx-coroutines-test. Tests use `runTest`.

## Fake provider (:provider:calendar-fake)
- **Descriptor:** id `calendar.fake`, name "Sample calendar (debug)", features `{READ}`.
- **`ConnectScreen`:** a single "Connect sample calendar" button that calls `onConnected(Connection(UUID, "calendar.fake", "Sample calendar", emptyMap()))`.
- **Sources:** `fake-alex` "Alex", `fake-sam` "Sam", `fake-mia` "Mia", `fake-family` "Family". None are writable in 2a.
- **Events are relative to "today"**, generated from an injectable `Clock` / `ZoneId` so tests are deterministic. The hand-off's Today list:
  - School run 07:45–08:30 (Sam)
  - Boiler service 10:00–11:00 (Family)
  - Swimming 16:00–17:00 (Mia)
  - Dinner with Jo & Priya 19:30–21:00 (Alex)

  plus a few events on the following six days (look at `docs/design/house_hub_handoff/screenshots/02-calendar-dark.png` and mirror it), and:
  - one all-day event (e.g. "Half term" for Family, spanning three days)
  - one weekly recurring event (e.g. "Piano" for Mia, every Tuesday 17:00–17:30), expanded
  - one event outside a 15-day window
- **Cursor:** the first sync returns `fullReplace = true` and cursor `"v1"`. A sync with `"v1"` returns no upserts and no removals, cursor `"v1"`.
- **Test hooks:** `failNextWith(Throwable)`, for contract tests only.
- Its own test class extends the contract test.

## calendar.db (:capability:calendar, Room, `calendar.db`)
- **`connection`:** id PK, providerId, label, configJson, health (text: OK / UNREACHABLE / NEEDS_SIGN_IN / ERROR), healthMessage, lastSyncMillis (nullable).
- **`source`:** (connectionId, sourceId) PK, name, writable, visible (bool), personId (text; `"family"` allowed).
- **`event`:** (connectionId, sourceId, remoteId) PK; title; startInstant/startDate (nullable pair); endInstant/endDate; recurring; forPerson; createdBy. Index on `startSort` (epoch millis; for all-day, the start of that date in the household zone) to allow range queries.
- **`sync_state`:** (connectionId, sourceId) PK, cursor.
- **Store API** (`CalendarStore`):
  - `connections(): Flow<List<StoredConnection>>`
  - `addConnection`
  - `setSources(connectionId, sources, mapping)`
  - `setHealth`
  - `applySync(connectionId, sourceId, result, syncedAt)`: in a transaction, honouring `fullReplace` and `removedIds`
  - `cursor(connectionId, sourceId)`
  - `eventsBetween(startMillis, endMillis): Flow<List<StoredEvent>>`
  - `visibleSources(): Flow<…>`
- Export the schema to `capability/calendar/schemas/`.

## Sync engine
`CalendarSync` is a `@Singleton`, plus a `Startable` that runs the loop.
- `syncAll()` handles each connection independently. It finds the provider by `providerId` from the injected `Set<CalendarProvider>`; with no provider, it marks the connection ERROR "Provider not installed".
- For each **visible** source, it calls `provider.sync(conn, source, range, cursor)`.
  - The range runs from today − 1 day to today + 14 days in the household zone (`HouseholdRepository.location` timeZoneId, falling back to the system zone).
  - It then calls `applySync` and sets health OK and lastSyncMillis.
- **Errors are caught per connection:**
  - `NeedsSignInException` → NEEDS_SIGN_IN
  - `UnreachableException` → UNREACHABLE
  - anything else → ERROR (message). The cache is kept in every case.
- **Loop:** `syncAll()` on start, then every 5 minutes (`delay`), on `@ApplicationScope`.
- **Tests** use the fake provider class directly (a `testImplementation` dependency on `:provider:calendar-fake` is NOT allowed: capability → provider is banned, even in tests). The engine's tests define their own tiny in-test `CalendarProvider` fake. The fake provider module has its own tests.

## Repository / UI models
`CalendarRepository`:
- `day(date): Flow<List<EventUi>>` and `week(start: LocalDate): Flow<WeekUi>`
- `syncStatus: Flow<SyncStatusUi>`: last successful sync across connections, whether it is stale (> 30 min), and any connection needing sign-in, with its label
- `hasConnections: Flow<Boolean>`

Person resolution:
- A `forPerson` tag that matches an existing household `PersonId` wins.
- Otherwise use the source's mapped person.
- Otherwise Family.

`EventUi` has title, timeLabel (`"07:45–08:30"` or `"All day"`), person (`Person`), allDay, startSort.

Multi-day all-day events appear on every day they cover.

## Capability impl
`CalendarCapability : Capability`
- id `calendar`, label "Calendar", icon `calendar_month`, order 10.
- `hasTab` = `hasConnections`.
- `cards()`:
  - no connections → `[ConnectCalendarCard TALL priority 100]`
  - otherwise → `[TodayCard TALL 100, ComingUpCard WIDE 50]`
- Bind with `@Binds @IntoSet Capability` in `:capability:calendar`'s Hilt module, together with `@IntoSet Startable` for the sync loop and `@Multibinds Set<CalendarProvider>`.

## UI (hand-off values; see docs/design/house_hub_handoff/README.md)
- **Today card** (TALL): radius 26, padding 22.
  - Header: "Today" 19sp/700, plus a "Week" chip that calls `LocalShellNavigator.openTab("calendar")`.
  - Rows: bg `surf2`, radius 16, padding 12×14, 4 dp colour bar in the person colour, title 17sp/600, "07:45–08:30 · Sam" 14sp muted. Rows scroll vertically.
  - Empty state: "Nothing on today" in muted body text.
- **Coming up card** (WIDE; the controller's design):
  - Header "Coming up" 19sp/700, plus a "Week ›" chip that opens the tab.
  - Three equal columns for the next three days (Tomorrow, then weekday names). Each column: day label 14sp/600, then up to 3 compact rows (bg `surf2`, radius 12, padding 8×10, 3 dp colour bar, title 14sp/600 one line ellipsis, time 12sp muted), then "+N more" 12sp muted when there are more.
  - Empty column: "Free" muted.
- **Connect a calendar card** (TALL; the controller's design):
  - `HhIcon("calendar_add_on", 34.dp, tint=accent)`, title "Connect a calendar" 19sp/700, body "Add your family's calendars in Settings to see them here." 14sp muted.
  - `HhPillButton("Open settings", primary = true)` calls `LocalShellNavigator.openSettings()`.
- **Week view** (the Calendar tab):
  - Header row: "This week" 34sp/700; subtitle "Family calendar · synced {x} ago" 14sp muted. The subtitle turns the warning tone when stale; there is no warning token, so use `accent` for the text — do not invent a colour.
  - Right side of the header: the person legend (10 dp dot + name 13sp/600), plus chevron buttons (`chevron_left` / `chevron_right`, 44 dp) and a "Today" pill.
  - Seven equal columns, gap 10. Each is a card with radius 22 and padding 14×10: day label 14sp/600 ("Today" in `accent` for today) and date number 24sp/700. Today's column has a 2 dp inset accent ring.
  - Event chips: background = person colour at 16% alpha, radius 12, padding 8×10; time 12sp/700 in the person colour ("All day" for all-day events); title 14sp/600. Columns scroll.
  - A NeedsSignIn chip appears in the header when any connection needs sign-in: "{label} needs reconnecting", which calls `openSettings()`.
- **"synced x ago" wording:** "just now" (< 1 min), "{n} min ago", "{n} h ago".

## Plan 1 follow-ups included in 2a
1. **Home header clock-to-date gap** (on the emulator it is about 55 dp against about 12 dp in the hand-off).
   - Target: the date's first baseline sits **44 dp below the clock's last baseline**. This is derived from the prototype CSS: clock line-height 0.9, 12 px margin, date 21 px, DM Sans metrics.
   - Implement a small custom `Layout` (`HomeHeaderLayout`) that places the date by baselines.
   - Test with `getAlignmentLinePosition(FirstBaseline/LastBaseline)` on test-tagged nodes, ±1 dp.
2. **Session chip truncation.** Remove the lock icon from the chip, keep the tap-to-lock action, and cap the padding at 10/8.
   - Test: "Admin" and "Alex" are not ellipsised. Read the `TextLayoutResult` via `SemanticsActions.GetTextLayoutResult` and assert `isLineEllipsized(0) == false`.
3. **`HomeCardPlacer` tests:** a mixed-sizes case and a full-grid case.
4. **`HhIcon` `contentDescription`** (see above).
5. **Robolectric viewport default.** Add `src/test/resources/robolectric.properties` with `qualifiers=w1280dp-h800dp-land-hdpi` in every module with Compose UI tests (`:core:access`, `:app`, `:capability:calendar`), and remove the per-class `@Config(qualifiers=…)` from `PinPadTest`.

## Roborazzi
- Add to the catalog: `roborazzi = "1.46.1"`, libraries `roborazzi` and `roborazzi-compose`, plugin `io.github.takahirom.roborazzi`. Declare the plugin `apply false` in the root.
- Apply it in `:app` and `:capability:calendar`. Use `@GraphicsMode(NATIVE)` on screenshot test classes only.
- Baselines go in `src/test/screenshots/`. Record with `./gradlew recordRoborazziDebug` and verify with `verifyRoborazziDebug`. Make `check` depend on `verifyRoborazziDebug` in those modules.
- **Screenshots:**
  - `:app`: Home (empty; with the calendar cards via fake UI state), dark and light; PIN pad.
  - `:capability:calendar`: Today card, Coming up card, Connect card, week view (normal, stale, needs-sign-in), dark and light.
- **First UI checkpoint:** after the cards and week view are recorded, the plan tells the executor to send the screenshots to the user for approval before continuing.

## Constraints carried from Plan 1 (Global Constraints apply unchanged)
- Commit messages carry no attribution trailers.
- No deprecated APIs.
- `testDebugUnitTest`, never `test`.
- AGP 8.13 / Gradle 8.13 / Hilt 2.57.x / Kotlin 2.2.20 / SDK 35.
- 4-digit PINs.
- Keep in mind: `:capability:calendar` applies `culvery.android.library`, `culvery.android.compose`, `culvery.hilt` and `culvery.room`, and depends on `:core:plugin`, `:core:household`, `:core:ui` and `:core:access` (for `AccessControl` later; include it now only if used — it is NOT used in 2a, so leave it out).
