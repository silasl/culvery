# Culvery

A wall-mounted Android tablet app for running a family home. Built as a set of modules so new services (calendars, lights, cameras…) plug in without touching the rest of the app, and nothing about a particular household is baked in.

- Design reference: `docs/design/house_hub_handoff/`
- Spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md`
- Plans: `docs/superpowers/plans/`

## Build and run

Requirements: JDK 17, Android SDK platform 35.

```bash
./gradlew assembleDebug testDebugUnitTest
./gradlew :app:installDebug
```

Use `testDebugUnitTest`, not `test` — release unit tests don't include the Compose test activity.

Debug builds seed a sample household on first launch so the app is usable before the setup wizard exists: **Alex** (Admin, PIN 1234), **Sam** (Adult, PIN 2468) and **Mia** (Child, PIN 1357), plus a "Sample calendar" connection showing the design hand-off's week. Its "Family calendar" is the household's master calendar, so events can be added there, and its events edited, deleted and assigned, on the tablet. The sample calendar keeps changes in memory and forgets them when the app restarts. People are only seeded when there is no Admin with a PIN; an install carried over from an earlier build (Plan 1 or 2a) also keeps its stored calendar names, so the sample calendar may show "Family" instead of "Family calendar" — clear the app's data (`adb shell pm clear uk.co.siland.culvery`) for a clean sample. Release builds seed nothing and include no sample calendar.

To see what the tablet does while a calendar can't be reached, a debug build can take the sample calendar offline and bring it back: `adb shell am broadcast -n uk.co.siland.culvery/.DebugOfflineReceiver --ez offline true` (or `false`). Changes made meanwhile show as syncing and are sent once it is back. This switch is debug-only and reached only over adb; it has no counterpart in the app's UI.

To connect a real Google account, first set up a Google Cloud project with an Android client for your debug key (`docs/setup/google-calendar.md`), add the family's Google account to the device (Settings › Accounts), then Settings › Connect Google Calendar (Admin PIN). Connecting removes the sample calendar, with its events and queued changes, for good. There is no disconnect yet: to undo a connection (the wrong account, or to get the sample back), clear the app's data. Release builds offer Google Calendar only, and its Connect fails until the release key's SHA-1 has its own Android client (Plan 4). A calendar is mapped to a person when its name contains theirs as a whole word, so a name that is also a common word ("May", "Will") can map a calendar like "May half term" to that person; Plan 4's Settings will let you change it.

## Screenshot tests

Compose screens are checked against PNG baselines with [Roborazzi](https://github.com/takahirom/roborazzi) under Robolectric at the tablet's 1280×800 dp.

```bash
./gradlew testDebugUnitTest verifyRoborazziDebug   # the standard gate: unit tests, then compare against the baselines
./gradlew compareRoborazziDebug                    # write *_compare.png diffs to <module>/build/outputs/roborazzi/
./gradlew recordRoborazziDebug                     # accept the current rendering as the new baselines
```

Baselines live in `<module>/src/test/screenshots/` and are committed. Re-record only after looking at the diff. The baselines are recorded on Windows: Robolectric's native graphics render slightly differently on other operating systems, so record and verify on Windows. Don't rely on `./gradlew check`: it also runs release unit tests, which cannot run Compose tests.

DM Sans is a variable font: `Font(resId, weight)` alone leaves its `wght` axis at the file's default, so every weight would render the same. Each `Font` in `core/ui`'s `Type.kt` sets `variationSettings` explicitly — don't drop that when adding weights.

## Modules

| Module | Purpose |
|---|---|
| `:app` | Activity, kiosk mode, nav rail, Home grid, wiring only |
| `:core:ui` | Design tokens, DM Sans, Material Symbols, shared components |
| `:core:plugin` | `Capability`, `HomeCard`, `ProviderDescriptor`, `Connection`, `ConnectionHealth`, `ShellNavigator`, `OverlayHost`, `Toaster`, `Startable` |
| `:core:household` | People (with role and PIN hash), Family, home location — `household.db` |
| `:core:access` | Permissions, PIN hashing, lockout, 2-minute session, PIN pad |
| `:capability:calendar` | Calendar contract (read and write), `calendar.db` cache and outbox, 5-minute sync, event editor, Home cards, Calendar tab, event detail and add/edit sheets |
| `:capability:calendar-testkit` | `CalendarProviderContractTest`, the tests every calendar provider must pass |
| `:provider:calendar-fake` | Debug-only sample calendar (the hand-off's week, relative to today) |
| `:provider:calendar-google` | Google Calendar API v3 over OkHttp; sign-in and tokens through Play services, nothing stored |

Rules, enforced when Gradle configures the project (`build-logic/convention/src/main/kotlin/ModuleBoundaries.kt`):
- `:core:*` depends only on `:core:*`.
- `:capability:X…` depends only on `:core:*` and its own family (`:capability:calendar-testkit` → `:capability:calendar`). Never on a provider, not even in tests.
- `:provider:X-…` depends only on `:core:*` and `:capability:X`, plus `:capability:X-testkit` in test configurations.
- `:app` may depend on anything.

Breaking a rule fails the build with `Module boundary: <from> must not depend on <to>`. Each module that stores data owns its own database file.

`calendar.db` stores user configuration (connections, mappings, the master calendar) and queued changes (the `outbox` table; the `event` table is only ever a copy of what the provider has). A queued change's 48-hour age doesn't count time its connection spent waiting for sign-in, so a lapse over a weekend drops nothing. Every schema version bump ships a hand-written Room `Migration` in `db/Migrations.kt` with a `MigrationTestHelper` test in `CalendarMigrationTest`; never use destructive fallback. `CalendarMigrationTest` runs that helper with the driver-based `AndroidSQLiteDriver`, because androidx.sqlite 2.6.x's default driver mis-handles Windows paths. Room exports each schema version to `<module>/schemas/`, and those files are committed.

## Adding a capability

1. Create `capability/<name>/build.gradle.kts` with `id("culvery.android.library")`, `id("culvery.android.compose")`, `id("culvery.hilt")`, and depend on `:core:plugin` (plus `:core:access` if it has actions).
2. Implement `Capability` (tab, icon, `order`, `hasTab`, Home `cards()`). Card and tab UI move the shell through `LocalShellNavigator.current.openTab(id)` / `.openSettings()`. Sheets open through `LocalOverlayHost.current.show { … }` (draw them with `HhSheet`). Short messages go through the injected `Toaster`, from the code that knows the outcome (a view model or an `@ApplicationScope` job), so the toast still shows if the sheet has closed. Background work (sync loops) is a `Startable` bound `@IntoSet`; the app starts it once at launch.
3. Bind it: `@Binds @IntoSet abstract fun bind(impl: MyCapability): Capability` in a Hilt module.
4. If it has actions, implement `PermissionSource` and bind it `@IntoSet` too; call `AccessControl.authorise("<name>.<action>")` before acting.
5. `include(":capability:<name>")` in `settings.gradle.kts` and add it to `:app` dependencies.

No other module changes.

## Adding a calendar provider

A provider connects one kind of calendar service (Google, ICS, CalDAV…) to the Calendar capability. Nothing outside the new module changes except `settings.gradle.kts` and `:app`'s dependencies.

1. **Module.** Create `provider/calendar-<name>/build.gradle.kts`:
   ```kotlin
   plugins {
       id("culvery.android.library")
       id("culvery.android.compose")
       id("culvery.hilt")
   }

   dependencies {
       implementation(project(":capability:calendar"))
       implementation(project(":core:plugin"))
       implementation(project(":core:ui"))
       testImplementation(project(":capability:calendar-testkit"))
   }
   ```
   and `include(":provider:calendar-<name>")` in `settings.gradle.kts`.

2. **Implement `CalendarProvider`:**
   - `descriptor`: a stable, unique `id` such as `calendar.<name>` (it is stored with each connection, so never change it), a display name, a Material Symbols icon, and `features` (`READ`, plus `WRITE` if it implements `CalendarWriter`).
   - `ConnectScreen(existing, onConnected, onCancel)`: collect whatever the service needs and call `onConnected(Connection(id, providerId = descriptor.id, label, config))`. Use a new UUID for `id` when `existing` is null; when it is non-null the user is reconnecting that connection, so keep `existing.id`. Keep secrets out of `config`. Put the signed-in account in `config[CONFIG_ACCOUNT]` for Settings to show. Set `ProviderDescriptor.userConnectable = false` for a provider nobody should connect from Settings (the debug sample).
   - `sources(conn)`: the calendars in this connection, with ids that never change between calls. Set `shown` (ticked in the service) and `primary` (the account's own calendar, at most one): connecting maps every source by name to a person or Family, shows the ticked ones, and makes the primary the master. A daily refresh follows later ticks, additions and removals.
   - `sync(conn, source, range, cursor)` must:
     - with `cursor == null`, return every event that overlaps `range` (local dates in `range.zone`, end exclusive), and only those, with `fullReplace = true` and, if the service supports it, a cursor for next time;
     - with a cursor, return only changes (`upserts`, `removedIds`) with `fullReplace = false`, or `fullReplace = true` only if the service forces a full resync (e.g. Google's HTTP 410). These incremental upserts MAY lie outside `range` (Google's `syncToken` can't carry `timeMin`/`timeMax`); the app keeps them and filters by range when it reads;
     - give timed events as `EventTime.Timed(instant)` and all-day events as `EventTime.AllDay(date)` with an **exclusive** end date (a one-day event on the 23rd ends on the 24th);
     - expand recurring events into occurrences with distinct `remoteId`s and `recurring = true`;
     - throw `NeedsSignInException` for auth failures and `UnreachableException` for network failures, or `SourceGoneException` (an `UnreachableException`) when the source itself has gone (the app then refreshes the sources), and nothing else. Set `recurrenceRule` to the series' RRULE line when you know it: the detail sheet describes it ("Every week").
   - `sources` and `sync` must be main-safe and cancellable: no uninterruptible blocking I/O. The app calls them on `Dispatchers.IO` under a 60-second timeout and treats a timeout as unreachable.
   The app handles storage, the sync schedule (every 5 minutes and on start), the window (yesterday to 14 days ahead) and error display.

3. **Writing (optional).** If the service can write, declare `Feature.WRITE` and implement `CalendarWriter` (often on the same class):
   - `providerId` equals `descriptor.id`.
   - `create`, `update` and `delete` write to one source. `forPerson` and `createdBy` are household person ids to store with the event (Google: `extendedProperties.private`), and the next sync must return them unchanged. Never write names.
   - `update(…, fields)` changes only the given `EventField`s (`TITLE`, `TIMES`, `FOR_PERSON`) and keeps everything else, the `createdBy` tag included (Google: a PATCH of just those keys). The tablet sends only what the person touched, so a change made on a phone to anything else is kept.
   - `find` returns the event as the service holds it, or null when it doesn't exist or was deleted.
   - `EventDraft.forPersonColor` is the person's colour; use it if the service can colour events (Google: the nearest `colorId`).
   - Throw `WriteRejectedException` for a permanent refusal (including a source that is unknown or read-only), `NeedsSignInException` for auth failures, and `UnreachableException` for network failures and for "try later" answers (Google: 429, 403 rate limits, 5xx). Nothing else.
   - Deleting an event that is already gone succeeds (Google: treat 404 and 410 on a delete as success).
   - `create` takes a client key: use it as the event's id, so the returned `remoteId` equals it, and when a create repeats a key already used on that source, return the event that key made instead of making a second (Google: `events.insert` with `id = clientKey`; a 409 means it exists, so fetch and return it). The app sends a create again with the same key when it never heard back, so this is what stops duplicates.
   - A create never recreates a deleted event: when the key belonged to an event since deleted, throw `WriteRejectedException` (Google: the 409's event is cancelled). Nor does an update: if the service would accept a change to a deleted event (Google answers a PATCH on one with 200), look the event up first and refuse with `EVENT_GONE`.
   - Every call must return promptly when its caller is cancelled: cancel the network call with it (OkHttp: enqueue inside `suspendCancellableCoroutine` and cancel from `invokeOnCancellation`), and keep the calls main-safe (read bodies off the main thread).

   Bind it next to the provider: `@Binds @IntoSet abstract fun writer(impl: MyCalendarProvider): CalendarWriter`. The app writes only to the household's master calendar, and only non-recurring events. It tries the writer for 10 seconds, then queues the change and retries with backoff (30 s, 1 min, 2 min, then every 5 min), delivering each event's changes in order. A change still queued after 48 hours (not counting time the connection waited for sign-in) is dropped with a toast.

4. **Register it** in the module:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   abstract class MyCalendarModule {
       @Binds @IntoSet abstract fun provider(impl: MyCalendarProvider): CalendarProvider
   }
   ```

5. **Prove it** with the shared contract suite in `src/test`:
   ```kotlin
   class MyCalendarProviderContractTest : CalendarProviderContractTest() {
       override fun provider() = …            // a provider backed by recorded fixtures, not the network
       override fun connection() = …
       override fun range() = DateRange(start, start.plusDays(16), zone)
       override fun sourceWithEvents() = …
       // Optional: leave any of these out (they default to null) and the matching check is skipped.
       override fun outOfRangeEventTitle() = …
       override fun recurringTitle() = …
       override fun simulateAuthFailure() = { … }
       override fun simulateUnreachable() = { … }
       // WRITE providers only; the ten write checks fail if these are missing.
       override fun writer() = …
       override fun writableSource() = …
   }
   ```
   Run `./gradlew :provider:calendar-<name>:testDebugUnitTest`. `:provider:calendar-fake` and `:provider:calendar-google` (through a fake Google server on `MockWebServer`) are worked examples. The suite has no cancellation check: prove it in the provider's own tests against its HTTP double, as `GoogleApiTest.cancellingTheCallerCancelsTheHttpCall` does.

6. **Ship it**: add `implementation(project(":provider:calendar-<name>"))` to `app/build.gradle.kts` (or `debugImplementation` for debug-only providers).

## Kiosk mode

Release builds pin the app to the screen (Android "screen pinning"). Leave properly via **Settings › Exit kiosk** (Admin PIN, always asked).

Screen pinning can also be undone by holding **Back + Overview**. To stop a child doing that, on the tablet: set a screen lock (PIN), then turn on **Settings › Security › Other security settings › Pin windows › Ask for PIN before unpinning**. Unpinning then drops to the lock screen.

A stronger device-owner lock is possible later; it is not built yet.

## PINs

- Everyone can have their own 4-digit PIN. PINs must be unique in the household because the PIN identifies the person. The pad submits on the 4th digit.
- Viewing never needs a PIN. Changing things does.
- A session lasts 2 minutes after the last PIN-checked action; touching the screen doesn't extend it. While someone is signed in, the status bar shows their name and role and a **Sign out** link. Settings closes when the session ends.
- Calendar changes follow the roles: Admins and Adults can add events for anyone, and edit, delete and assign any event on the master calendar; a Child can add events only for themselves, edit or delete only events they added (and can't move one to someone else), and can't assign. A refused change says why in a toast and signs the person out, so the next tap asks for a PIN. Events from other calendars, and repeating events, can't be changed on the tablet, and an event over several days can have only its title and who changed.
- Exiting kiosk and managing people always ask for a PIN, even mid-session.
- 5 wrong PINs lock the pad for 30 seconds, doubling each time up to 16 minutes. Only a PIN that is allowed to do the thing clears the count.
- This is kid-proofing, not strong security.

**Forgotten PIN:** an Admin can reset anyone's PIN in Settings. If every Admin has forgotten theirs, clear the app's data (Android Settings › Apps › Culvery › Storage › Clear data). That wipes all configuration and starts setup again.

## Licences

DM Sans: SIL Open Font License (`core/ui/licenses/OFL-DMSans.txt`). Material Symbols: Apache 2.0 (`core/ui/licenses/Apache-MaterialSymbols.txt`).
