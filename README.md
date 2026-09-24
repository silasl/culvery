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

Debug builds seed a sample household on first launch so the app is usable before the setup wizard exists: **Alex** (Admin, PIN 1234), **Sam** (Adult, PIN 2468) and **Mia** (Child, PIN 1357), plus a "Sample calendar" connection showing the design hand-off's week. The seed only runs when there is no Admin with a PIN, so after upgrading from an older debug build clear the app's data (`adb shell pm clear uk.co.siland.culvery`). Release builds seed nothing and include no sample calendar.

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
| `:core:plugin` | `Capability`, `HomeCard`, `ProviderDescriptor`, `Connection`, `ConnectionHealth`, `ShellNavigator`, `Startable` |
| `:core:household` | People (with role and PIN hash), Family, home location — `household.db` |
| `:core:access` | Permissions, PIN hashing, lockout, 60 s session, PIN pad |
| `:capability:calendar` | Calendar contract, `calendar.db` cache, 5-minute sync, Home cards, Calendar tab |
| `:capability:calendar-testkit` | `CalendarProviderContractTest`, the tests every calendar provider must pass |
| `:provider:calendar-fake` | Debug-only sample calendar (the hand-off's week, relative to today) |

Rules, enforced when Gradle configures the project (`build-logic/convention/src/main/kotlin/ModuleBoundaries.kt`):
- `:core:*` depends only on `:core:*`.
- `:capability:X…` depends only on `:core:*` and its own family (`:capability:calendar-testkit` → `:capability:calendar`). Never on a provider, not even in tests.
- `:provider:X-…` depends only on `:core:*` and `:capability:X`, plus `:capability:X-testkit` in test configurations.
- `:app` may depend on anything.

Breaking a rule fails the build with `Module boundary: <from> must not depend on <to>`. Each module that stores data owns its own database file.

`calendar.db` stores user configuration (connections, mappings). Every schema version bump ships a Room `Migration` or `AutoMigration`; never use destructive fallback. Room exports each schema version to `<module>/schemas/`, and those files are committed.

## Adding a capability

1. Create `capability/<name>/build.gradle.kts` with `id("culvery.android.library")`, `id("culvery.android.compose")`, `id("culvery.hilt")`, and depend on `:core:plugin` (plus `:core:access` if it has actions).
2. Implement `Capability` (tab, icon, `order`, `hasTab`, Home `cards()`). Card and tab UI move the shell through `LocalShellNavigator.current.openTab(id)` / `.openSettings()`. Background work (sync loops) is a `Startable` bound `@IntoSet`; the app starts it once at launch.
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
   - `descriptor`: a stable, unique `id` such as `calendar.<name>` (it is stored with each connection, so never change it), a display name, a Material Symbols icon, and `features` (`READ`, plus `WRITE` once 2b adds writing).
   - `ConnectScreen(existing, onConnected, onCancel)`: collect whatever the service needs and call `onConnected(Connection(id, providerId = descriptor.id, label, config))`. Use a new UUID for `id` when `existing` is null; when it is non-null the user is reconnecting that connection, so keep `existing.id`. Keep secrets out of `config`.
   - `sources(conn)`: the calendars in this connection, with ids that never change between calls.
   - `sync(conn, source, range, cursor)` must:
     - with `cursor == null`, return every event that overlaps `range` (local dates in `range.zone`, end exclusive), and only those, with `fullReplace = true` and, if the service supports it, a cursor for next time;
     - with a cursor, return only changes (`upserts`, `removedIds`) with `fullReplace = false`, or `fullReplace = true` only if the service forces a full resync (e.g. Google's HTTP 410). These incremental upserts MAY lie outside `range` (Google's `syncToken` can't carry `timeMin`/`timeMax`); the app keeps them and filters by range when it reads;
     - give timed events as `EventTime.Timed(instant)` and all-day events as `EventTime.AllDay(date)` with an **exclusive** end date (a one-day event on the 23rd ends on the 24th);
     - expand recurring events into occurrences with distinct `remoteId`s and `recurring = true`;
     - throw `NeedsSignInException` for auth failures and `UnreachableException` for network failures, and nothing else.
   - `sources` and `sync` must be main-safe and cancellable: no uninterruptible blocking I/O. The app calls them on `Dispatchers.IO` under a 60-second timeout and treats a timeout as unreachable.
   The app handles storage, the sync schedule (every 5 minutes and on start), the window (yesterday to 14 days ahead) and error display.

3. **Register it** in the module:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   abstract class MyCalendarModule {
       @Binds @IntoSet abstract fun provider(impl: MyCalendarProvider): CalendarProvider
   }
   ```

4. **Prove it** with the shared contract suite in `src/test`:
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
   }
   ```
   Run `./gradlew :provider:calendar-<name>:testDebugUnitTest`. `:provider:calendar-fake` is a worked example.

5. **Ship it**: add `implementation(project(":provider:calendar-<name>"))` to `app/build.gradle.kts` (or `debugImplementation` for debug-only providers).

## Kiosk mode

Release builds pin the app to the screen (Android "screen pinning"). Leave properly via **Settings › Exit kiosk** (Admin PIN, always asked).

Screen pinning can also be undone by holding **Back + Overview**. To stop a child doing that, on the tablet: set a screen lock (PIN), then turn on **Settings › Security › Other security settings › Pin windows › Ask for PIN before unpinning**. Unpinning then drops to the lock screen.

A stronger device-owner lock is possible later; it is not built yet.

## PINs

- Everyone can have their own 4-digit PIN. PINs must be unique in the household because the PIN identifies the person. The pad submits on the 4th digit.
- Viewing never needs a PIN. Changing things does.
- A session lasts 60 seconds after the last touch. Tap your name in the rail to lock early. Settings closes when the session ends.
- Exiting kiosk and managing people always ask for a PIN, even mid-session.
- 5 wrong PINs lock the pad for 30 seconds, doubling each time up to 16 minutes. Only a PIN that is allowed to do the thing clears the count.
- This is kid-proofing, not strong security.

**Forgotten PIN:** an Admin can reset anyone's PIN in Settings. If every Admin has forgotten theirs, clear the app's data (Android Settings › Apps › Culvery › Storage › Clear data). That wipes all configuration and starts setup again.

## Licences

DM Sans: SIL Open Font License (`core/ui/licenses/OFL-DMSans.txt`). Material Symbols: Apache 2.0 (`core/ui/licenses/Apache-MaterialSymbols.txt`).
