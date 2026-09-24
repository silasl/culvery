# Culvery — Plan 2a: Calendar capability (read path) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Calendar capability reads events from calendar providers into a local `calendar.db`, syncs every 5 minutes, and shows them on Home (Today, Coming up, or Connect a calendar) and in a Calendar week-view tab, all exercised through a debug-only fake provider, with screenshot tests and a Gradle-enforced module boundary.

**Architecture:** `:core:plugin` gains the provider/connection contracts, a `ShellNavigator` composition local and a `Startable` hook. `:capability:calendar` owns the calendar contract, the Room cache, the sync engine (a `Startable` loop), the repository that turns cached events into UI models, the Home cards and the week view. Providers implement `CalendarProvider` in their own modules, bind themselves with `@IntoSet`, and prove themselves against the shared `CalendarProviderContractTest` from `:capability:calendar-testkit`. `:app` only wires: it provides the navigator, starts the `Startable`s and seeds debug data.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.x (KSP), Room 2.8.0, Coroutines/Flow, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1.

**Spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md`
**Architecture notes (agreed decisions for this plan):** `docs/superpowers/plans/2026-09-23-plan2a-architecture-notes.md`
**Design reference:** `docs/design/house_hub_handoff/README.md` (§2 Calendar, §7 Calendar sheets for the entry-point sizes and badges, and the Theming table), `Culvery.dc.html` (exact values), and `screenshots/calendar-sheets/01-home-dark.png`, `01-home-light.png`, `02-calendar-week-dark.png`, `02-calendar-week-light.png`. The older `screenshots/01-home-dark.png` and `02-calendar-dark.png` predate §7.

**Plan series:** 1 Foundation (done) · **2a Calendar read path (this plan)** · 2b Calendar write path (detail and quick-add sheets, master calendar, outbox, `.self`/`.own` checks) · 3 Google and ICS providers · 4 Weather, setup, settings, release.

**Task order and why it differs from the suggested order:**
1. Roborazzi first, so every later UI change is visible as a screenshot diff (Task 2 re-records the Plan 1 baselines on purpose).
2. Plan 1 polish.
3. The `:core:plugin` contracts and their shell wiring are **one task**. The contracts on their own have no behaviour to test; the shell wiring is what makes them testable.
4. The module-boundary guard comes before the first `:capability:*` module exists, so every new module is checked from its first line.
5. Calendar contract types.
6. Contract test suite.
7. Fake provider.
8. `calendar.db`.
9. Sync engine.
10. Repository.
11. Capability and Home cards.
12. Week view, then a user checkpoint.
13. App wiring and seed.
14. README.

**Deviations from the spec and notes (deliberate):**
- `ProviderDescriptor.icon` is a Material Symbols ligature `String`, not the spec's `IconRef`, the same as `Capability.icon`.
- `DateRange` carries the household `ZoneId`. Providers must turn dates into instants: Google needs `timeMin`/`timeMax`, and ICS has floating times. Without the zone they would guess.
- `CalendarStore.addConnection(connection, sources, mapping)` stores the connection and its sources in **one transaction**. Otherwise the sync loop, which reacts to new connections, could sync a connection that has no sources yet and then wait 5 minutes.
- A connection's source list is fixed when it is added. Editing sources, pruning removed ones and refreshing them from `provider.sources()` come in Plan 4 with the Connections settings (see `2026-09-23-plan1-followups.md`).
- `applySync` takes the `DateRange`, not `syncedAt`. `sync_state` records the window start **and zone**, and a stored cursor is used only while both are unchanged. The first sync each day, or after a time-zone change, is therefore a full resync, and day +14 appears after midnight. Health and time are set by a separate `markSynced`.
- `CalendarSync` syncs each source independently. A connection's health is the worst across its sources (NeedsSignIn > Unreachable > Error > Ok), and it is marked synced only when every source succeeded. Every provider call runs on `Dispatchers.IO` under a 60 s timeout; a timeout counts as Unreachable.
- `event` has an `endSort` column as well as `startSort`, so a multi-day all-day event that started before the queried window is still found.
- The sync loop runs `syncAll()` on start, every 5 minutes, **and whenever the set of connections changes**. A new connection therefore shows events at once.
- `SyncStatusUi.lastSyncMillis` is the **oldest** last-successful sync across connections, not the newest. One failing calendar must not hide behind a healthy one, and a connection that is failing before its first sync makes the status stale on its own. "Stale" and the "synced x ago" wording are pure functions of `(SyncStatusUi, nowMillis)`, so the UI can tick without a new database read.
- The subtitle reads "Family calendar · synced with {connection label} {x} ago" only when there is exactly one connection (the hand-off shows one Google connection), and "Family calendar · synced {x} ago" otherwise.
- The Coming up card uses **compact** rows (a smaller variant of the Today row), not the Today row style that spec §9.2 first asked for, so three days fit in the WIDE card. The user approved this, and §9.2 is amended to match.
- `CalendarProvider.ConnectScreen(existing, onConnected, onCancel)` has an `existing: Connection?` parameter that spec §5 does not list. Non-null means "reconnect this connection" (spec §9.6) and the provider must keep its id. It is added now because default arguments on abstract composables are unreliable.
- The fake provider's cursor is `"v1:<today>"`, not `"v1"`, so its relative sample data rolls over at midnight.
- The version-catalog aliases are `roborazzi-core` and `roborazzi-compose`, not `roborazzi`. If one alias is a prefix of another, Gradle forces `libs.roborazzi.asProvider()`.
- The guard also stops a capability depending on a capability of another family (`:capability:calendar` → `:capability:weather`), per spec §4 ("`:capability:*` → `:core:*` only"). A module may still depend on its own family (`:capability:calendar-testkit` → `:capability:calendar`).
- The Calendar tab always shows a rolling 7 days starting today, titled "This week", with no week navigation (spec §9.3 as amended, matching hand-off §7). The view never leaves the synced window (yesterday to 14 days ahead), so an empty column always means "nothing on".
- The Home Today card's **+** button, the Calendar tab's **Add event** button, the column add hints, the `lock` and `cloud_upload` badges and tapping an event are 2b. Tapping an event does nothing in 2a. The `repeat` badge is built now.
- The sync loop is started from `CulveryApp.onCreate` through `Startable`, not "with the Activity" (spec §7). The app is a single-activity foreground kiosk, so the effect is the same.
- `:app`'s `robolectric.properties` sets `application=android.app.Application`. Otherwise every Robolectric test in `:app` would boot the real Hilt app, with its databases, the sync loop and the seed.
- The text-metric tests in Task 2 and `CardsTest` in Task 11 (which checks rows fit the real card sizes) use `@GraphicsMode(NATIVE)` as well as the screenshot classes. Robolectric's legacy graphics fakes glyph widths, so an ellipsis or baseline test would pass or fail for the wrong reason.
- An inserted Task 11b fixed a Plan 1 bug: every DM Sans weight rendered at 400 on device. Root cause: `Font(resId, weight)` resolves to an overload whose variation settings are empty, so the variable font's `wght` axis was never set. Fix: each `Font` sets `FontVariation.weight(n)` explicitly, which needs `@OptIn(ExperimentalTextApi::class)`. The same opt-in `HhIcon` already uses since Plan 1; experimental, not deprecated. It has been verified on API 35 only. API 30 is checked at the 2b checkpoint.

**Review outcome (settled; do not reintroduce):**
- Rejected: T4, a real-thread Room test of the sync loop's connection flow.
- Rejected: S3, moving `connect()` into `CalendarSync` and deleting `CalendarSetup`.
- Rejected: S5, deleting the contract-suite self-test.
- Rejected: S9, one `days(today, 7)` flow feeding every surface.
- Rejected: S10, inlining `startAll`.
- Rejected: S11, merging Task 14 into 13 and Task 5 into 6.
- Rejected: R11, recomposition and identical-rewrite optimisations.
- Rejected: R13, treating a null input cursor as a full replace whatever the provider says.
- Rejected: B1, tightening the guard's `testFixtures*` and own-testkit loopholes.
- Deferred to Plan 3: R3, catching `Throwable` per connection and a logging `CoroutineExceptionHandler` on `@ApplicationScope`.
- Deferred to Plan 3: R9, backoff retries after boot or a Wi-Fi drop, and syncing on a visible-sources change.
- Deferred to Plan 4: R5, FK `CASCADE` and `applySync` returning early for a removed source (with source editing).
- Deferred to 2b: C1, an overlay/sheet host in `:core:plugin`.
- Deferred to 2b: C2, `EventRef` instead of the joined `EventUi.key`.
- Deferred to 2b: C4, a separate `CalendarWriter` interface for the write path.
- Deferred to 2b: C6, a `requestSync()` trigger on the loop.
- Deferred to 2b: C8, the outbox in its own table, merged in the repository.
- Deferred to 2b: R12, a `Mutex` around `syncAll`.
- Deferred to 2b: a `MigrationTestHelper` test for `calendar.db` (the v1 schema is exported now).

## Global Constraints

- Package root `uk.co.siland.culvery`; app name "Culvery".
- `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Stay on AGP 8.x, Gradle 8.13, Hilt 2.57.x. If a version fails to resolve, take the newest **patch** in the same minor line. Never move to a new major/minor (e.g. AGP 9, Hilt 2.59+) without asking the user.
- Design canvas 1280×800 dp; hand-off `docs/design/house_hub_handoff/README.md` is authoritative for colours, type, spacing, radii.
- No shadows; flat colours; no blur.
- No secrets, tokens or household data in source or build config.
- Do not use `androidx.security:security-crypto` / `EncryptedSharedPreferences` (deprecated). Do not use any other deprecated API without asking the user first. `@OptIn` to an *experimental* API is allowed where the plan says so.
- PINs are exactly 4 ASCII digits.
- Commit messages contain only the message — no `Co-Authored-By` or any attribution trailer.
- Module dependency rule: `:core:*` never depends on `:app`, `:capability:*` or `:provider:*`.
- The standard gate is `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test` or `check`: release unit tests lack the Compose test activity. A task that only touches one module runs that module's `testDebugUnitTest`, plus its `verifyRoborazziDebug` where it has screenshots.
- **Added in 2a:**
  - Pinned versions: AGP 8.13.0, Gradle 8.13, Kotlin 2.2.20, KSP 2.2.20-2.0.3, Compose BOM 2025.09.00, Room 2.8.0, Robolectric 4.16, Roborazzi 1.46.1.
    - If any API this plan uses shows a deprecation warning in these versions, stop and ask the user before continuing.
    - The steps name the APIs worth checking.
  - Module rules (enforced by the guard from Task 4):
    - `:capability:X…` depends only on `:core:*` and on `:capability:X…` (its own family).
    - `:provider:X-…` depends only on `:core:*` and `:capability:X`, plus `:capability:X-testkit` in test configurations only.
    - A module's family is its name up to the first `-`.
    - The guard reads `ProjectDependency.path`. Never use `ProjectDependency.dependencyProject`, which is deprecated.
  - Screenshots:
    - Roborazzi baselines live in `<module>/src/test/screenshots/`.
    - Record with `./gradlew <module>:recordRoborazziDebug`; verify with `./gradlew <module>:verifyRoborazziDebug`.
    - `@GraphicsMode(GraphicsMode.Mode.NATIVE)` goes only on test classes that capture screenshots or measure real text.
    - Baselines are recorded on Windows. Robolectric's native graphics differ by OS, so record and verify on Windows.
  - The one exception to "never plain `test`" is the JVM-only `build-logic` build: `./gradlew -p build-logic :convention:test`.
  - Colours and type:
    - Colours come only from `Culvery.colors` or a person's own colour. 2a adds exactly three tokens, from the hand-off Theming table: `danger`, `dangerSoft` and `dangerInk`.
    - The warning tone (stale sync subtitle, reconnect chip) is `danger`.
    - Text styles come from `HhType`, or are `.copy()`s of it named in `CalendarType`. Calendar layout values from the prototype live in `CalendarDimens`, not as inline numbers.
  - Storage: `calendar.db` stores user configuration (connections, mappings). Every schema version bump ships a Room `Migration` or `AutoMigration`; never use destructive fallback. Schemas are exported to `<module>/schemas/` and committed, starting at v1.
  - Calendar providers must be main-safe and cancellable. The sync engine runs every provider call on `Dispatchers.IO` under a 60 s timeout.
  - Debug sample people (Alex, Sam, Mia) and sample events live only in `app/src/debug` and in `:provider:calendar-fake`, which `:app` takes as `debugImplementation`.

## Review Focus

1. **The tablet runs past midnight.**
   - The sync window moves a day. The new day +14 must appear, and "today" must move on.
   - A stored cursor from yesterday must not be reused for today's window.
   - Tests:
     - Task 8 `cursorIsDroppedWhenTheWindowMoves`
     - Task 9 `aNewDayResyncsFromScratch`
     - Task 7 `cursorFromYesterdayGivesAFullReplace`
2. **Wi-Fi is down, or a token has expired.**
   - Events already on screen must stay; the connection is flagged, and the cache is not wiped.
   - Tests in Task 9:
     - `needsSignInKeepsCachedEventsAndFlagsConnection`
     - `unreachableKeepsCachedEvents`
3. **A multi-day all-day event (half term) is in the calendar.**
   - It must show on every day it covers, including when it started before the visible day or window.
   - It must not show on its exclusive end day.
   - Tests:
     - Task 10 `multiDayAllDayEventAppearsOnEveryDayItCovers`
     - Task 10 `multiDayEventStartingBeforeTheQueryShowsOnEveryVisibleDay`
     - Task 8 `eventsBetweenReturnsOverlapsInStartOrder`
4. **A calendar was just connected.**
   - Its events must appear within seconds, not after the next 5-minute tick.
   - Test: Task 9 `syncsAgainWhenAConnectionIsAdded`.
5. **A calendar is mapped to a person who was later removed, or an event is tagged with an unknown person.**
   - The event must show as Family (or as the source's person). It must never crash or vanish.
   - Tests in Task 10:
     - `sourceMappedToRemovedPersonShowsFamily`
     - `unknownTagFallsBackToSourceMapping`
6. **A provider hangs or times out** (a stalled socket, or a provider's own internal `withTimeout`).
   - That connection is flagged Unreachable after the timeout, the other connections still sync, and the 5-minute loop keeps ticking.
   - Tests:
     - Task 9 `hangingProviderTimesOutAsUnreachableAndTheNextSyncStillRuns`
     - Task 9 `providerTimeoutCancellationBecomesUnreachable`
     - Task 9 `aSyncThatTimesOutInternallyDoesNotStopTheLoop`

---

## File Structure

```
gradle/libs.versions.toml, build.gradle.kts, settings.gradle.kts          (modify: Roborazzi, new modules)
build-logic/convention/build.gradle.kts                                   (modify: test deps)
build-logic/convention/src/main/kotlin/ModuleBoundaries.kt                (create: rules + enforcement)
build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt  (modify: enforce)
build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt            (create)

core/ui/src/main/java/.../core/ui/HhIcon.kt                               (modify: contentDescription)
core/ui/src/main/java/.../core/ui/Colors.kt, Theme.kt                     (modify: danger, dangerSoft, dangerInk)
core/ui/src/test/java/.../core/ui/HhIconTest.kt                           (create)
core/ui/src/test/java/.../core/ui/ThemeTest.kt                            (modify: danger tokens)
core/plugin/src/main/java/.../core/plugin/Connections.kt                  (create: ProviderDescriptor, Feature, Connection, ConnectionHealth)
core/plugin/src/main/java/.../core/plugin/ShellNavigator.kt               (create: ShellNavigator, LocalShellNavigator)
core/plugin/src/main/java/.../core/plugin/Startable.kt                    (create)
core/plugin/src/test/java/.../core/plugin/HomeCardPlacerTest.kt           (modify: mixed + full grid)
core/access/src/main/java/.../core/access/ui/PinPad.kt                    (modify: backspace label)
core/access/src/test/resources/robolectric.properties                     (create)
core/access/src/test/java/.../core/access/ui/PinPadTest.kt                (modify: drop @Config, label test)

capability/calendar/                        contract, calendar.db, store, sync, repository, capability, cards, week view
  build.gradle.kts, schemas/…/1.json
  src/main/java/uk/co/siland/culvery/capability/calendar/
    CalendarContract.kt      EventTime, CalendarSource, RemoteEvent, DateRange, SyncCursor, SyncResult, exceptions, CalendarProvider
    db/CalendarDatabase.kt   entities, DAO, database
    Stored.kt                SourceMapping, StoredConnection, StoredSource, StoredEvent
    CalendarStore.kt         the only writer of calendar.db
    HouseholdZone.kt         household ZoneId with system fallback
    CalendarSync.kt          syncAll(): per source, on Dispatchers.IO, 60 s timeout per provider call
    CalendarSyncLoop.kt      Startable: on start, on connection change, every 5 min
    CalendarUi.kt            EventUi, DayUi, WeekUi, SyncStatusUi, per-day labels, subtitle, person resolution
    CalendarRepository.kt    Flows of UI models
    CalendarSetup.kt         connect a provider connection (debug seed now, setup wizard in Plan 4)
    CalendarCapability.kt    Capability impl, card ids
    di/CalendarModule.kt
    ui/CalendarType.kt (CalendarType, CalendarDimens), ui/Components.kt (HeaderChip, HeaderLink, ColourBar),
    ui/Now.kt, ui/TodayCard.kt, ui/ComingUpCard.kt, ui/ConnectCalendarCard.kt,
    ui/CardHosts.kt (TodayCardHost, ComingUpCardHost, WeekViewHost), ui/WeekView.kt
  src/test/resources/robolectric.properties
  src/test/java/uk/co/siland/culvery/capability/calendar/
    ContractTypesTest, TestDatabases, ScriptedProvider, CalendarStoreTest, HouseholdZoneTest,
    CalendarSyncTest, CalendarSyncLoopTest, SyncLabelsTest, PersonResolutionTest, CalendarRepositoryTest,
    CalendarCapabilityTest, CalendarSetupTest,
    ui/RecordingNavigator, ui/SampleUi, ui/NowTest, ui/CardsTest, ui/CardScreenshotTest, ui/WeekViewTest, ui/WeekScreenshotTest
  src/test/screenshots/*.png

capability/calendar-testkit/                abstract CalendarProviderContractTest (+ self-test with deliberately broken fixtures)
provider/calendar-fake/                     FakeCalendarProvider, SampleEvents, FakeCalendarModule, tests,
                                            src/test/resources/robolectric.properties

app/build.gradle.kts                                                     (modify)
app/src/main/java/uk/co/siland/culvery/CulveryApp.kt, MainActivity.kt, Startup.kt, di/AppModule.kt
app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt, shell/ui/HomeScreen.kt, shell/ui/NavRail.kt
app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt, app/src/release/java/uk/co/siland/culvery/DebugSeed.kt
app/src/test/resources/robolectric.properties
app/src/test/java/uk/co/siland/culvery/  StartupTest, shell/ShellViewModelTest,
                                          shell/ui/ShellScreenshotTest, shell/ui/ShellLayoutTest
app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt   (debug-only: uses the fake provider)
app/src/test/screenshots/*.png
README.md
```

`...` in paths above stands for `uk/co/siland/culvery`; every step below spells out the full path.

---

### Task 1: Roborazzi, Robolectric viewport default, baseline screenshots of the Plan 1 shell

**Files:**
- Modify: `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/src/test/resources/robolectric.properties`, `core/access/src/test/resources/robolectric.properties`
- Modify: `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/PinPadTest.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`
- Create (recorded): `app/src/test/screenshots/{home_empty_dark,home_empty_light,home_session_dark,settings_dark,pin_pad_dark,pin_pad_light}.png`

**Interfaces:**
- Consumes (existing code):
  - `CulveryShell(state: ShellUiState, onSelectTab: (String) -> Unit, onOpenSettings: () -> Unit, onLockSession: () -> Unit, onToggleThemePreview: () -> Unit, tabContent: @Composable (String) -> Unit)`
  - `ShellUiState(tabs, selectedTabId, session: SessionChip?, now: LocalDateTime, dark, previewing, homeCards, settingsOpen)`
  - `SessionChip(name: String, color: Long)`
  - `SettingsPlaceholder(onExitKiosk, onClose)`
  - `PinPadSheet(label: String, error: PinError?, lockedUntilMillis: Long?, onSubmit: (String) -> Unit, onCancel: () -> Unit)`
  - `CulveryTheme(dark, content)`
- Produces:
  - Catalog aliases:
    - `libs.roborazzi.core`
    - `libs.roborazzi.compose`
    - `libs.plugins.roborazzi`
  - Robolectric default viewport `w1280dp-h800dp-land-hdpi` in `:app` and `:core:access`.
  - `:app` Robolectric tests run with a plain `android.app.Application`.
  - Screenshot convention: `compose.onRoot()` (or a tagged node) `.captureRoboImage("src/test/screenshots/<name>.png")`.

- [ ] **Step 1: Add Roborazzi to the version catalog**

In `gradle/libs.versions.toml`:

- Under `[versions]`, after `androidxTestExtJunit = "1.3.0"`, add:
```toml
roborazzi = "1.46.1"
```
- Under `[libraries]`, after the `androidx-test-ext-junit` line, add:
```toml
roborazzi-core = { group = "io.github.takahirom.roborazzi", name = "roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { group = "io.github.takahirom.roborazzi", name = "roborazzi-compose", version.ref = "roborazzi" }
```
- Under `[plugins]`, after the `hilt` line, add:
```toml
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

- [ ] **Step 2: Declare the plugin in the root build**

Replace `build.gradle.kts` with:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
}
```

- [ ] **Step 3: Apply Roborazzi in `:app`**

Replace `app/build.gradle.kts` with:
```kotlin
plugins {
    id("culvery.android.application")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "uk.co.siland.culvery"
    defaultConfig {
        applicationId = "uk.co.siland.culvery"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures.buildConfig = true
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:plugin"))
    implementation(project(":core:household"))
    implementation(project(":core:access"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
```

Screenshots are verified by the standard gate (`testDebugUnitTest verifyRoborazziDebug`), not through `check`: `check` also runs `testReleaseUnitTest`, which cannot run Compose tests.

- [ ] **Step 4: Set the Robolectric defaults**

`app/src/test/resources/robolectric.properties`:
```properties
qualifiers=w1280dp-h800dp-land-hdpi
# Keep the real @HiltAndroidApp (databases, sync loop, debug seed) out of unit tests.
application=android.app.Application
```

`core/access/src/test/resources/robolectric.properties`:
```properties
qualifiers=w1280dp-h800dp-land-hdpi
```

In `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/PinPadTest.kt`, delete the line `@Config(qualifiers = "w1280dp-h800dp")` and the import `import org.robolectric.annotation.Config`.

- [ ] **Step 5: Confirm the PIN pad tests still pass with the module default**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass (7 in `PinPadTest`).

- [ ] **Step 6: Write the screenshot test**

`app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.access.ui.PinPadSheet
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.SessionChip
import uk.co.siland.culvery.shell.ShellUiState

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    private fun snap(
        name: String,
        dark: Boolean,
        state: ShellUiState = ShellUiState(now = at, dark = dark),
        overlay: @Composable () -> Unit = {},
    ) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.fillMaxSize()) {
                    CulveryShell(
                        state = state,
                        onSelectTab = {},
                        onOpenSettings = {},
                        onLockSession = {},
                        onToggleThemePreview = {},
                        tabContent = {},
                    )
                    overlay()
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun homeEmptyDark() = snap("home_empty_dark", dark = true)

    @Test
    fun homeEmptyLight() = snap("home_empty_light", dark = false)

    @Test
    fun homeWithSessionDark() = snap(
        "home_session_dark",
        dark = true,
        state = ShellUiState(now = at, dark = true, session = SessionChip("Admin", 0xFF4CB387)),
    )

    @Test
    fun settingsDark() = snap("settings_dark", dark = true) {
        SettingsPlaceholder(onExitKiosk = {}, onClose = {})
    }

    @Test
    fun pinPadDark() = snap("pin_pad_dark", dark = true) {
        PinPadSheet("Change settings", error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun pinPadLight() = snap("pin_pad_light", dark = false) {
        PinPadSheet("Change settings", error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }
}
```

Check before continuing:
- `com.github.takahirom.roborazzi.captureRoboImage` (the `SemanticsNodeInteraction` extension) must not be marked `@Deprecated` in 1.46.1. Check in the IDE, or look for a deprecation warning in the compile output of the next step.
- If it is deprecated, stop and ask the user.

- [ ] **Step 7: Run the tests without recording**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`. Plain unit-test runs render but do not write images.

- [ ] **Step 8: Record the baselines**

Run: `./gradlew :app:recordRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, and six PNGs in `app/src/test/screenshots/`.

- [ ] **Step 9: Look at every image**

Open each PNG (for example with the Read tool) and compare it against `docs/design/house_hub_handoff/screenshots/01-home-dark.png`. Each should show:
- the 30 dp status bar reading "11:54" and "Auto";
- the 108 dp rail with Home selected;
- the 104 sp clock "11:54" and "Wednesday 23 September";
- an empty grid.

The light images use the light tokens. `home_session_dark` shows the "Admin" chip, still truncated to "Adm…". Task 2 fixes that and the clock-to-date gap.

**If a PNG is blank or the text is missing,** native graphics did not run. Check `@GraphicsMode(GraphicsMode.Mode.NATIVE)` and the Robolectric version, then stop and ask. Do not continue with broken baselines.

- [ ] **Step 10: Verify against the baselines**

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/test core/access/src/test
git commit -m "Add Roborazzi screenshot tests and a module-wide Robolectric tablet viewport"
```

---
### Task 2: Plan 1 polish — Home header by baselines, session chip, icon labels, placer tests

**Files:**
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt` (`SessionChipView`)
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/HhIcon.kt`
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt` (backspace label)
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt`, `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Theme.kt` (danger tokens)
- Test: `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt` (create)
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/HhIconTest.kt` (create)
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ThemeTest.kt` (modify)
- Test: `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/PinPadTest.kt` (modify)
- Test: `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/HomeCardPlacerTest.kt` (modify)
- Re-record: `app/src/test/screenshots/home_*.png`

**Interfaces:**
- Consumes:
  - `HomeScreen(now: LocalDateTime, placements: List<HomePlacement>)`
  - `NavRail(tabs, selectedId, session: SessionChip?, onSelect, onOpenSettings, onLockSession)`
  - `HhType.clock`, `HhType.date`
  - `HhColors(bg, surf, surf2, surf3, line, ink, mute, accent, accentInk, accentSoft)`, `DarkColors`, `LightColors`, and `CulveryTheme`, which animates each token
- Produces:
  - `HhColors` gains `danger`, `dangerSoft` and `dangerInk` (after `accentSoft`), set in `DarkColors` and `LightColors` from the hand-off Theming table and animated by `CulveryTheme` like the other tokens. Task 12 uses them for the stale subtitle and the reconnect chip.
  - `@Composable fun HomeHeader(now: LocalDateTime, modifier: Modifier = Modifier)`. Test tags `home_clock` and `home_date`. The date's first baseline sits 44 dp below the clock's last baseline.
  - `HhIcon(name, size, filled, tint, modifier, contentDescription: String? = null)`. When `contentDescription` is non-null it is the node's only semantics; when it is null the icon is hidden from accessibility, as before.
  - The session chip no longer has a lock icon. It keeps tag `rail_session` and tap-to-lock, and gains the click label "Lock".

- [ ] **Step 1: Tag the existing header texts (no behaviour change)**

In `app/src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt`:

1. Add the import:
   ```kotlin
   import androidx.compose.ui.platform.testTag
   ```
2. Replace the two `Text(...)` lines with:
   ```kotlin
               Text(now.format(CLOCK), style = HhType.clock, color = c.ink, modifier = Modifier.testTag("home_clock"))
               Spacer(Modifier.height(12.dp))
               Text(now.format(DATE), style = HhType.date, color = c.mute, modifier = Modifier.testTag("home_date"))
   ```

- [ ] **Step 2: Write the failing layout tests**

`app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt`:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.HOME_TAB_ID
import uk.co.siland.culvery.shell.SessionChip

/** Needs real text metrics: legacy Robolectric graphics fakes glyph widths and font ascents. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dateBaselineSits44dpBelowClockBaseline() {
        compose.setContent {
            CulveryTheme(dark = true) { HomeScreen(LocalDateTime.of(2026, 9, 23, 11, 54), emptyList()) }
        }
        val clock = compose.onNodeWithTag("home_clock")
        val date = compose.onNodeWithTag("home_date")
        val clockBaseline = clock.getUnclippedBoundsInRoot().top + clock.getAlignmentLinePosition(LastBaseline)
        val dateBaseline = date.getUnclippedBoundsInRoot().top + date.getAlignmentLinePosition(FirstBaseline)
        assertThat((dateBaseline - clockBaseline).value).isWithin(1f).of(44f)
    }

    @Test
    fun sessionChipShowsAdminInFull() = assertChipNotEllipsised("Admin")

    @Test
    fun sessionChipShowsAlexInFull() = assertChipNotEllipsised("Alex")

    @Test
    fun tappingTheSessionChipLocks() {
        var locked = false
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(emptyList(), HOME_TAB_ID, SessionChip("Alex", 0xFF4CB387), {}, {}, { locked = true })
            }
        }
        compose.onNodeWithTag("rail_session").performClick()
        assertThat(locked).isTrue()
    }

    @Test
    fun sessionChipAnnouncesLockAsItsClickAction() {
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(emptyList(), HOME_TAB_ID, SessionChip("Alex", 0xFF4CB387), {}, {}, {})
            }
        }
        val onClick = compose.onNodeWithTag("rail_session").fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertThat(onClick.label).isEqualTo("Lock")
    }

    private fun assertChipNotEllipsised(name: String) {
        compose.setContent {
            CulveryTheme(dark = true) {
                NavRail(emptyList(), HOME_TAB_ID, SessionChip(name, 0xFF4CB387), {}, {}, {})
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts.single().isLineEllipsized(0)).isFalse()
    }
}
```

- [ ] **Step 3: Run the tests and check they fail for the right reason**

Run: `./gradlew :app:testDebugUnitTest --tests "*ShellLayoutTest*"`

Expected:
- `dateBaselineSits44dpBelowClockBaseline` FAILS. The measured gap is well above 45 dp (roughly 60–75 dp). Note the actual value in your report.
- `sessionChipShowsAdminInFull` FAILS because the text is ellipsised.
- `sessionChipAnnouncesLockAsItsClickAction` FAILS: the Plan 1 chip's click has no label, so `label` is null.
- `sessionChipShowsAlexInFull` and `tappingTheSessionChipLocks` may already pass.

If the header test passes at this point, stop and tell the user: the emulator gap has a cause that Robolectric does not reproduce.

- [ ] **Step 4: Place the date by baselines**

Replace `app/src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt` with:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.core.plugin.HomePlacement
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhType

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

// Hand-off CSS: clock line-height 0.9 (11 px below its baseline) + 12 px margin + the date's 21 px ascent.
private val CLOCK_TO_DATE_BASELINES = 44.dp

@Composable
fun HomeScreen(now: LocalDateTime, placements: List<HomePlacement>) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        HomeHeader(now, Modifier.fillMaxWidth())
        HomeGrid(placements, Modifier.fillMaxWidth().weight(1f))
    }
}

/** Clock and date, with the date placed by baseline because the clock's line-height trim doesn't apply reliably. */
@Composable
fun HomeHeader(now: LocalDateTime, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Layout(
        modifier = modifier,
        content = {
            Text(now.format(CLOCK), style = HhType.clock, color = c.ink, modifier = Modifier.testTag("home_clock"))
            Text(now.format(DATE), style = HhType.date, color = c.mute, modifier = Modifier.testTag("home_date"))
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val clock = measurables[0].measure(loose)
        val date = measurables[1].measure(loose)
        val dateY = clock[LastBaseline] + CLOCK_TO_DATE_BASELINES.roundToPx() - date[FirstBaseline]
        val width = constraints.constrainWidth(maxOf(clock.width, date.width))
        val height = constraints.constrainHeight(dateY + date.height)
        layout(width, height) {
            clock.place(0, 0)
            date.place(0, dateY)
        }
    }
}
```

`constrainWidth` / `constrainHeight` are the `Constraints` extension functions in `androidx.compose.ui.unit`. If the compiler resolves them as member functions instead, delete those two imports.

- [ ] **Step 5: Drop the lock icon from the session chip**

In `app/src/main/java/uk/co/siland/culvery/shell/ui/NavRail.kt`, replace the whole `SessionChipView` function with:
```kotlin
@Composable
private fun SessionChipView(chip: SessionChip, onLock: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .testTag("rail_session")
            .padding(bottom = 8.dp)
            .widthIn(max = 92.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surf2)
            .clickable(onClickLabel = "Lock", onClick = onLock)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(chip.color)))
        Text(
            chip.name,
            style = HhType.label,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
```
Keep the `HhIcon` import: the Settings button still uses it.

- [ ] **Step 6: Run the layout tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*ShellLayoutTest*"`
Expected: PASS (5 tests).

- [ ] **Step 7: Write the failing icon-label tests**

`core/ui/src/test/java/uk/co/siland/culvery/core/ui/HhIconTest.kt`:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HhIconTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun describedIconIsAnnouncedByItsDescriptionNotItsLigature() {
        compose.setContent { CulveryTheme(dark = true) { HhIcon("backspace", contentDescription = "Delete last digit") } }
        compose.onNodeWithContentDescription("Delete last digit").assertExists()
        compose.onNodeWithText("backspace").assertDoesNotExist()
    }

    @Test
    fun decorativeIconIsHidden() {
        compose.setContent { CulveryTheme(dark = true) { HhIcon("home") } }
        compose.onNodeWithText("home").assertDoesNotExist()
    }
}
```

Append this test to `PinPadTest` in `core/access/src/test/java/uk/co/siland/culvery/core/access/ui/PinPadTest.kt`, together with the import `import androidx.compose.ui.test.onNodeWithContentDescription`:
```kotlin
    @Test
    fun backspaceIsLabelled() {
        controller.open("Change settings", null, null)
        show()
        compose.onNodeWithContentDescription("Delete last digit").assertExists()
    }
```

- [ ] **Step 8: Run them to verify they fail**

Run: `./gradlew :core:ui:testDebugUnitTest :core:access:testDebugUnitTest`
Expected: FAIL. `HhIconTest` does not compile: "No parameter with name 'contentDescription'".

- [ ] **Step 9: Add `contentDescription` to `HhIcon` and label the backspace key**

In `core/ui/src/main/java/uk/co/siland/culvery/core/ui/HhIcon.kt`:

1. Add the import:
   ```kotlin
   import androidx.compose.ui.semantics.contentDescription
   ```
2. Replace the `HhIcon` function with:
   ```kotlin
   /** Renders a Material Symbols Rounded glyph by ligature name, e.g. "lightbulb". */
   @Composable
   fun HhIcon(
       name: String,
       size: Dp = 24.dp,
       filled: Boolean = false,
       tint: Color = Culvery.colors.ink,
       modifier: Modifier = Modifier,
       contentDescription: String? = null,
   ) {
       val sp = with(LocalDensity.current) { size.toSp() }
       Text(
           text = name,
           style = TextStyle(
               fontFamily = if (filled) SymbolsFilled else SymbolsOutline,
               fontSize = sp,
               lineHeight = sp,
               color = tint,
           ),
           maxLines = 1,
           // The ligature text means nothing to a screen reader: expose the description or nothing.
           modifier = modifier.clearAndSetSemantics {
               if (contentDescription != null) this.contentDescription = contentDescription
           },
       )
   }
   ```

In `core/access/src/main/java/uk/co/siland/culvery/core/access/ui/PinPad.kt`, replace `HhIcon("backspace", size = 30.dp, tint = c.mute)` with:
```kotlin
                    HhIcon("backspace", size = 30.dp, tint = c.mute, contentDescription = "Delete last digit")
```

- [ ] **Step 10: Run to verify they pass**

Run: `./gradlew :core:ui:testDebugUnitTest :core:access:testDebugUnitTest`
Expected: PASS: `HhIconTest` (2 tests), and `PinPadTest` (8 tests) plus the other `:core:access` tests.

- [ ] **Step 11: Write the failing danger-token test**

Append inside `ThemeTest` in `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ThemeTest.kt`, together with the import `import androidx.compose.ui.graphics.Color`:
```kotlin
    @Test
    fun dangerTokensMatchTheHandOff() {
        assertThat(listOf(DarkColors.danger, DarkColors.dangerSoft, DarkColors.dangerInk))
            .containsExactly(Color(0xFFEE7B6A), Color(0xFF3A211D), Color(0xFF1A0906)).inOrder()
        assertThat(listOf(LightColors.danger, LightColors.dangerSoft, LightColors.dangerInk))
            .containsExactly(Color(0xFFB83A28), Color(0xFFF7DFDA), Color(0xFFFFFFFF)).inOrder()
    }
```

Run: `./gradlew :core:ui:testDebugUnitTest`
Expected: FAIL to compile: "Unresolved reference: danger".

- [ ] **Step 12: Add the danger tokens and animate them**

Replace `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt` with:
```kotlin
package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class HhColors(
    val bg: Color,
    val surf: Color,
    val surf2: Color,
    val surf3: Color,
    val line: Color,
    val ink: Color,
    val mute: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val dangerInk: Color,
)

val DarkColors = HhColors(
    bg = Color(0xFF0E1011),
    surf = Color(0xFF1A1D1E),
    surf2 = Color(0xFF24282A),
    surf3 = Color(0xFF303537),
    line = Color(0xFF1F2324),
    ink = Color(0xFFF1F4F2),
    mute = Color(0xFF9AA3A0),
    accent = Color(0xFF4CB387),
    accentInk = Color(0xFF08170F),
    accentSoft = Color(0xFF173427),
    danger = Color(0xFFEE7B6A),
    dangerSoft = Color(0xFF3A211D),
    dangerInk = Color(0xFF1A0906),
)

val LightColors = HhColors(
    bg = Color(0xFFEDF0EE),
    surf = Color(0xFFFFFFFF),
    surf2 = Color(0xFFF1F4F2),
    surf3 = Color(0xFFDDE3E0),
    line = Color(0xFFDAE0DD),
    ink = Color(0xFF111514),
    mute = Color(0xFF5A6461),
    accent = Color(0xFF2E8A64),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFD3EDE1),
    danger = Color(0xFFB83A28),
    dangerSoft = Color(0xFFF7DFDA),
    dangerInk = Color(0xFFFFFFFF),
)
```

In `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Theme.kt`, replace the line `accentSoft = animated(target.accentSoft),` inside the `HhColors(...)` construction with:
```kotlin
        accentSoft = animated(target.accentSoft),
        danger = animated(target.danger),
        dangerSoft = animated(target.dangerSoft),
        dangerInk = animated(target.dangerInk),
```

Run: `./gradlew :core:ui:testDebugUnitTest`
Expected: PASS: `ThemeTest` (4 tests, including `darkThemeProvidesDarkTokens` and `lightThemeProvidesLightTokens`, which now also compare the danger tokens) and `HhIconTest` (2 tests).

- [ ] **Step 13: Add the mixed-size and full-grid placer tests**

These pin down existing behaviour, so they are expected to pass straight away. Append inside `HomeCardPlacerTest` in `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/HomeCardPlacerTest.kt`:
```kotlin
    @Test
    fun mixedSizesFillTallThenWideThenRegularBesideIt() {
        val result = HomeCardPlacer.place(
            listOf(card("today", TALL, 100), card("comingUp", WIDE, 50), card("r1", REGULAR, 20), card("r2", REGULAR, 10)),
        )
        assertThat(result.layout()).containsExactly(
            "today", listOf(0, 0, 1, 2),
            "comingUp", listOf(1, 0, 2, 1),
            "r1", listOf(1, 1, 1, 1),
            "r2", listOf(2, 1, 1, 1),
        )
    }

    @Test
    fun lowPriorityTallIsDroppedWhenRegularCardsTookTheLeftColumn() {
        val result = HomeCardPlacer.place(
            listOf(
                card("a", REGULAR, 90), card("b", REGULAR, 80), card("c", REGULAR, 70),
                card("d", REGULAR, 60), card("e", REGULAR, 50), card("t", TALL, 40), card("f", REGULAR, 30),
            ),
        )
        assertThat(result.map { it.card.id }).doesNotContain("t")
        assertThat(result.layout()["e"]).isEqualTo(listOf(0, 0, 1, 1))
        assertThat(result.layout()["f"]).isEqualTo(listOf(0, 1, 1, 1))
    }

    @Test
    fun fullGridDropsEverythingElse() {
        val six = listOf("a", "b", "c", "d", "e", "f").mapIndexed { i, id -> card(id, REGULAR, 60 - i * 10) }
        val result = HomeCardPlacer.place(six + card("late", REGULAR, 5) + card("tall", TALL, 1) + card("wide", WIDE, 2))
        assertThat(result.map { it.card.id }).containsExactly("a", "b", "c", "d", "e", "f")
        assertThat(result.layout()).containsExactly(
            "a", listOf(1, 0, 1, 1),
            "b", listOf(2, 0, 1, 1),
            "c", listOf(1, 1, 1, 1),
            "d", listOf(2, 1, 1, 1),
            "e", listOf(0, 0, 1, 1),
            "f", listOf(0, 1, 1, 1),
        )
    }
```

Run: `./gradlew :core:plugin:testDebugUnitTest`
Expected: PASS (10 tests).

- [ ] **Step 14: See the screenshot diff, then re-record**

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: FAIL for `home_empty_dark`, `home_empty_light` and `home_session_dark`: the header moved and the chip changed.

Run: `./gradlew :app:compareRoborazziDebug`. Open the `*_compare.png` files under `app/build/outputs/roborazzi/`. Expected changes:
- the date sits closer to the clock;
- the grid moves up by roughly the same amount;
- the chip reads "Admin" in full, with no lock icon.

Nothing else may change.

Run: `./gradlew :app:recordRoborazziDebug`, then `./gradlew :app:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 15: Run everything**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 16: Commit**

```bash
git add app core/ui core/access core/plugin
git commit -m "Place the Home date by baseline, stop truncating the session chip, label icons, add danger tokens"
```

---

### Task 3: `:core:plugin` provider/shell contracts, and the shell wiring that uses them

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Connections.kt`
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ShellNavigator.kt`
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Startable.kt`
- Create: `app/src/main/java/uk/co/siland/culvery/Startup.kt`
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`, `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`, `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`, `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/StartupTest.kt` (create), `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt` (modify)

**Interfaces:**
- Consumes:
  - `ShellViewModel.selectTab(id: String)`
  - `ShellViewModel.openSettings()`, which authorises `settings.manage`
  - `@ApplicationScope CoroutineScope`
- Produces (package `uk.co.siland.culvery.core.plugin`):
  - `enum class Feature { READ, WRITE }`
  - `data class ProviderDescriptor(val id: String, val displayName: String, val icon: String, val features: Set<Feature>)`
  - `data class Connection(val id: String, val providerId: String, val label: String, val config: Map<String, String>)`
  - `sealed interface ConnectionHealth`, with members:
    - `data object Ok`
    - `data object Unreachable`
    - `data object NeedsSignIn`
    - `data class Error(val message: String)`
  - `interface ShellNavigator { fun openTab(id: String); fun openSettings() }`
  - `val LocalShellNavigator: ProvidableCompositionLocal<ShellNavigator>`. It throws if not provided.
  - `fun interface Startable { fun start() }`
- Produces in `:app`:
  - `ShellViewModel : ShellNavigator`
  - `internal fun startAll(startables: Iterable<Startable>, onFailure: (Startable, Exception) -> Unit)`
  - `@Multibinds Set<Startable>` in `AppModule`
  - `MainActivity` provides `LocalShellNavigator`

- [ ] **Step 1: Write the contracts**

`core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Connections.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

enum class Feature { READ, WRITE }

/** A provider module's identity. [id] is stable and namespaced by capability, e.g. "calendar.google". */
data class ProviderDescriptor(
    val id: String,
    val displayName: String,
    /** Material Symbols ligature name. */
    val icon: String,
    val features: Set<Feature>,
)

/** One user-configured instance of a provider. [config] holds non-secret settings; secrets go in SecretStore (Plan 3). */
data class Connection(
    val id: String,
    val providerId: String,
    val label: String,
    val config: Map<String, String>,
)

sealed interface ConnectionHealth {
    data object Ok : ConnectionHealth
    data object Unreachable : ConnectionHealth
    data object NeedsSignIn : ConnectionHealth
    data class Error(val message: String) : ConnectionHealth
}
```

`core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/ShellNavigator.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.staticCompositionLocalOf

/** Lets capability UI move the shell without depending on :app. */
interface ShellNavigator {
    /** Selects a rail tab by capability id; ignored if that tab is not shown. */
    fun openTab(id: String)

    /** Opens Settings, asking for a PIN if needed. */
    fun openSettings()
}

val LocalShellNavigator = staticCompositionLocalOf<ShellNavigator> {
    error("LocalShellNavigator not provided: wrap the content in CompositionLocalProvider(LocalShellNavigator provides …)")
}
```

`core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Startable.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

/**
 * Bound `@IntoSet` by a capability that needs background work (e.g. a sync loop).
 * The app calls [start] once from Application.onCreate; it must return quickly and launch its work
 * on the @ApplicationScope scope.
 */
fun interface Startable {
    fun start()
}
```

- [ ] **Step 2: Stub `startAll` and write the failing tests**

`app/src/main/java/uk/co/siland/culvery/Startup.kt`:
```kotlin
package uk.co.siland.culvery

import uk.co.siland.culvery.core.plugin.Startable

internal fun startAll(startables: Iterable<Startable>, onFailure: (Startable, Exception) -> Unit): Unit = TODO()
```

`app/src/test/java/uk/co/siland/culvery/StartupTest.kt`:
```kotlin
package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.Startable

class StartupTest {
    @Test
    fun everyStartableIsStartedOnce() {
        var a = 0
        var b = 0
        startAll(listOf(Startable { a++ }, Startable { b++ })) { _, _ -> }
        assertThat(listOf(a, b)).containsExactly(1, 1)
    }

    @Test
    fun aFailingStartableDoesNotStopTheOthers() {
        val failures = mutableListOf<String>()
        var laterStarted = false
        startAll(listOf(Startable { error("boom") }, Startable { laterStarted = true })) { _, e ->
            failures += e.message.orEmpty()
        }
        assertThat(laterStarted).isTrue()
        assertThat(failures).containsExactly("boom")
    }
}
```

In `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`:

1. Add the import:
   ```kotlin
   import uk.co.siland.culvery.core.plugin.ShellNavigator
   ```
2. Append inside the class:
   ```kotlin
       @Test
       fun openTabSelectsThatTab() = runTest {
           val vm = vm(setOf(FakeCapability("calendar", order = 10, shown = true)))
           val navigator: ShellNavigator = vm
           vm.uiState.test {
               navigator.openTab("calendar")
               assertThat(expectMostRecentItem().selectedTabId).isEqualTo("calendar")
           }
       }

       @Test
       fun openTabForAHiddenTabStaysHome() = runTest {
           val vm = vm(setOf(FakeCapability("calendar", order = 10, shown = false)))
           vm.uiState.test {
               vm.openTab("calendar")
               assertThat(expectMostRecentItem().selectedTabId).isEqualTo(HOME_TAB_ID)
           }
       }
   ```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL. `ShellViewModelTest` does not compile ("Unresolved reference: openTab"; "Type mismatch: ShellViewModel is not ShellNavigator"). Once it compiles, `StartupTest` fails with `NotImplementedError`.

- [ ] **Step 4: Implement**

Replace the body of `app/src/main/java/uk/co/siland/culvery/Startup.kt` with:
```kotlin
package uk.co.siland.culvery

import uk.co.siland.culvery.core.plugin.Startable

/** One capability failing to start must not stop the others, or the kiosk would come up half-dead. */
internal fun startAll(startables: Iterable<Startable>, onFailure: (Startable, Exception) -> Unit) {
    for (startable in startables) {
        try {
            startable.start()
        } catch (e: Exception) {
            onFailure(startable, e)
        }
    }
}
```

In `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`:

1. Add the import:
   ```kotlin
   import uk.co.siland.culvery.core.plugin.ShellNavigator
   ```
2. Change the class header's supertype list from `) : ViewModel() {` to:
   ```kotlin
   ) : ViewModel(), ShellNavigator {
   ```
3. Change `fun openSettings() {` to `override fun openSettings() {`.
4. Add this method right after `selectTab`:
   ```kotlin
       override fun openTab(id: String) = selectTab(id)
   ```

In `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`:

1. Add the import:
   ```kotlin
   import uk.co.siland.culvery.core.plugin.Startable
   ```
2. Add this below the `capabilities()` multibinding:
   ```kotlin
       @Multibinds
       abstract fun startables(): Set<Startable>
   ```

Replace `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt` with:
```kotlin
package uk.co.siland.culvery

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

@HiltAndroidApp
class CulveryApp : Application() {
    @Inject lateinit var household: HouseholdRepository
    @Inject lateinit var pins: PinManager
    @Inject lateinit var startables: Set<@JvmSuppressWildcards Startable>
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        startAll(startables) { startable, e -> Log.e(TAG, "${startable.javaClass.name} failed to start", e) }
        appScope.launch { seedDebugData(household, pins) }
    }

    private companion object {
        const val TAG = "Culvery"
    }
}
```

In `app/src/main/java/uk/co/siland/culvery/MainActivity.kt`:

1. Add the imports:
   ```kotlin
   import androidx.compose.runtime.CompositionLocalProvider
   import uk.co.siland.culvery.core.plugin.LocalShellNavigator
   ```
2. Replace the `setContent { ... }` block with:
   ```kotlin
           setContent {
               val state by shell.uiState.collectAsStateWithLifecycle()
               CompositionLocalProvider(LocalShellNavigator provides shell) {
                   CulveryTheme(dark = state.dark) {
                       CulveryShell(
                           state = state,
                           onSelectTab = shell::selectTab,
                           onOpenSettings = shell::openSettings,
                           onLockSession = shell::lockSession,
                           onToggleThemePreview = shell::toggleThemePreview,
                           tabContent = { id -> capabilities.firstOrNull { it.id == id }?.TabContent() },
                       )
                       if (state.settingsOpen) {
                           SettingsPlaceholder(onExitKiosk = shell::exitKiosk, onClose = shell::closeSettings)
                       }
                       PinPadHost(pinPrompt)
                   }
               }
           }
   ```

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew :app:testDebugUnitTest :core:plugin:testDebugUnitTest`
Expected: PASS: `StartupTest` (2 tests), `ShellViewModelTest` (18 tests), `ShellScreenshotTest`, `ShellLayoutTest`, `HomeCardPlacerTest`.

- [ ] **Step 6: Build the app (checks the Hilt graph)**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. An empty `Set<Startable>` is valid thanks to `@Multibinds`.

- [ ] **Step 7: Commit**

```bash
git add core/plugin app
git commit -m "Add provider, connection, navigator and startable contracts; wire them into the shell"
```

---

### Task 4: Module-boundary guard in `build-logic`

**Files:**
- Modify: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/ModuleBoundaries.kt`
- Modify: `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`
- Test: `build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt`

**Interfaces:**
- Produces:
  - `object ModuleBoundaries { fun violation(from: String, to: String, configuration: String): String? }`. It returns null if allowed, or a message naming both modules.
  - `internal fun Project.enforceModuleBoundaries()`. It is applied by `culvery.android.library` to every library module and fails configuration on a forbidden `project(...)` dependency.
  - `:app` is not checked: it may depend on anything.

- [ ] **Step 1: Add test dependencies to build-logic**

Replace the `dependencies` block of `build-logic/convention/build.gradle.kts` with:
```kotlin
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
```

- [ ] **Step 2: Stub the rules**

`build-logic/convention/src/main/kotlin/ModuleBoundaries.kt`:
```kotlin
object ModuleBoundaries {
    fun violation(from: String, to: String, configuration: String): String? = TODO()
}
```

- [ ] **Step 3: Write the failing tests**

`build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt`:
```kotlin
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModuleBoundariesTest {
    private fun allowed(from: String, to: String, configuration: String = "implementation") =
        assertThat(ModuleBoundaries.violation(from, to, configuration)).isNull()

    private fun banned(from: String, to: String, configuration: String = "implementation") =
        assertThat(ModuleBoundaries.violation(from, to, configuration)).isNotNull()

    @Test
    fun coreMayDependOnCore() = allowed(":core:access", ":core:household", "api")

    @Test
    fun coreMayNotDependOnAppCapabilityOrProvider() {
        banned(":core:ui", ":app")
        banned(":core:plugin", ":capability:calendar")
        banned(":core:plugin", ":provider:calendar-fake", "testImplementation")
    }

    @Test
    fun capabilityMayDependOnCore() = allowed(":capability:calendar", ":core:household", "api")

    @Test
    fun capabilityMayNotDependOnAProviderEvenInTests() {
        banned(":capability:calendar", ":provider:calendar-fake")
        banned(":capability:calendar", ":provider:calendar-fake", "testImplementation")
    }

    @Test
    fun capabilityMayNotDependOnAnotherCapability() = banned(":capability:calendar", ":capability:weather")

    @Test
    fun testkitMayDependOnItsOwnCapability() = allowed(":capability:calendar-testkit", ":capability:calendar", "api")

    @Test
    fun providerMayDependOnCoreAndItsOwnCapability() {
        allowed(":provider:calendar-fake", ":core:ui")
        allowed(":provider:calendar-fake", ":capability:calendar")
    }

    @Test
    fun providerMayUseItsTestkitOnlyInTests() {
        allowed(":provider:calendar-fake", ":capability:calendar-testkit", "testImplementation")
        allowed(":provider:calendar-fake", ":capability:calendar-testkit", "testDebugImplementation")
        banned(":provider:calendar-fake", ":capability:calendar-testkit", "implementation")
    }

    @Test
    fun providerMayNotDependOnOtherCapabilitiesOrProviders() {
        banned(":provider:calendar-fake", ":capability:weather")
        banned(":provider:weather-openmeteo", ":capability:calendar")
        banned(":provider:calendar-google", ":provider:calendar-fake")
        banned(":provider:calendar-fake", ":app")
    }

    @Test
    fun appMayDependOnAnything() = allowed(":app", ":provider:calendar-fake", "debugImplementation")

    @Test
    fun messageNamesBothModulesAndTheConfiguration() {
        assertThat(ModuleBoundaries.violation(":core:ui", ":app", "implementation"))
            .isEqualTo("Module boundary: :core:ui must not depend on :app (in 'implementation'). See README › Modules.")
    }
}
```

- [ ] **Step 4: Run to verify they fail**

`build-logic` is a plain JVM build, so its task is `test`. It is the only exception to the `testDebugUnitTest` rule.

Run: `./gradlew -p build-logic :convention:test`
Expected: FAIL. 11 tests fail with `NotImplementedError`.

- [ ] **Step 5: Implement the rules and the enforcement**

Replace `build-logic/convention/src/main/kotlin/ModuleBoundaries.kt` with:
```kotlin
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Spec §4 dependency rules. A module's family is its name up to the first '-':
 * ":provider:calendar-google" and ":capability:calendar-testkit" are both family "calendar".
 */
object ModuleBoundaries {
    fun violation(from: String, to: String, configuration: String): String? {
        val allowed = when (kind(from)) {
            "core" -> kind(to) == "core"
            "capability" -> kind(to) == "core" || (kind(to) == "capability" && family(to) == family(from))
            "provider" -> kind(to) == "core" ||
                to == ":capability:${family(from)}" ||
                (to == ":capability:${family(from)}-testkit" && isTestOnly(configuration))
            else -> true
        }
        return if (allowed) null else "Module boundary: $from must not depend on $to (in '$configuration'). See README › Modules."
    }

    private fun kind(path: String) = path.removePrefix(":").substringBefore(':')

    private fun family(path: String) = path.substringAfterLast(':').substringBefore('-')

    private fun isTestOnly(configuration: String) =
        configuration.startsWith("test") || configuration.startsWith("androidTest")
}

/** Fails configuration as soon as a forbidden project(...) dependency is declared. */
internal fun Project.enforceModuleBoundaries() {
    val from = path
    configurations.configureEach {
        val configuration = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            // ProjectDependency.path (Gradle 8.11+); dependencyProject is deprecated.
            val to = this.path
            ModuleBoundaries.violation(from, to, configuration)?.let { throw GradleException(it) }
        }
    }
}
```

Replace `build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt` with:
```kotlin
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> {
            namespace = "uk.co.siland.culvery." +
                path.removePrefix(":").replace(':', '.').replace('-', '_')
            configureAndroid(this)
        }
        enforceModuleBoundaries()
    }
}
```


- [ ] **Step 6: Run to verify they pass**

Run: `./gradlew -p build-logic :convention:test`
Expected: PASS (11 tests).

- [ ] **Step 7: Check the guard fires in a real build, then revert**

1. Append `dependencies { implementation(project(":app")) }` to the end of `core/ui/build.gradle.kts`.
2. Run: `./gradlew :core:ui:help`
   Expected: FAIL during configuration with `Module boundary: :core:ui must not depend on :app (in 'implementation'). See README › Modules.`
3. Delete the line you added, then run `git diff --name-only -- core/ui/build.gradle.kts`.
   Expected: no output.
4. Run: `./gradlew :core:ui:help`
   Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Run everything**

Run: `./gradlew assembleDebug testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. No existing module breaks a rule.

- [ ] **Step 9: Commit**

```bash
git add build-logic
git commit -m "Enforce module dependency boundaries in the library convention plugin"
```

---

### Task 5: `:capability:calendar` — the calendar contract types

**Files:**
- Modify: `settings.gradle.kts`
- Create: `capability/calendar/build.gradle.kts`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ContractTypesTest.kt`

**Interfaces:**
- Consumes:
  - `ProviderDescriptor`, `Connection` (Task 3)
- Produces (package `uk.co.siland.culvery.capability.calendar`):
  - `sealed interface EventTime { data class Timed(val instant: Instant); data class AllDay(val date: LocalDate) }`
  - `fun EventTime.instantIn(zone: ZoneId): Instant`. An all-day date means midnight in `zone`.
  - `fun spanOverlaps(start: Long, end: Long, windowStart: Long, windowEnd: Long): Boolean`. The overlap is half-open. A zero-length span counts when it starts inside the window.
  - `data class CalendarSource(val id: String, val name: String, val writable: Boolean)`
  - `data class RemoteEvent(val remoteId: String, val title: String, val start: EventTime, val end: EventTime, val recurring: Boolean, val forPerson: String? = null, val createdBy: String? = null)`. `end` is exclusive.
  - `data class DateRange(val start: LocalDate, val endExclusive: LocalDate, val zone: ZoneId)`. It has `startInstant`, `endInstant` and `fun overlaps(start: EventTime, end: EventTime): Boolean`, and requires `endExclusive > start`.
  - `@JvmInline value class SyncCursor(val value: String)`
  - `data class SyncResult(val upserts: List<RemoteEvent>, val removedIds: List<String>, val cursor: SyncCursor?, val fullReplace: Boolean)`
  - `class NeedsSignInException(message: String? = null, cause: Throwable? = null) : Exception`
  - `class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception`
  - `interface CalendarProvider`:
    - `val descriptor: ProviderDescriptor`
    - `@Composable fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit)`. Non-null `existing` means "reconnect this connection": the provider must report a `Connection` with the same `id`.
    - `suspend fun sources(conn: Connection): List<CalendarSource>`
    - `suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult`
    - KDoc contract: implementations are main-safe and cancellable (no uninterruptible blocking I/O); the engine enforces a 60 s timeout. With a cursor, incremental upserts MAY lie outside `range`.

- [ ] **Step 1: Add the module**

Append to `settings.gradle.kts`:
```kotlin
include(":capability:calendar")
```

`capability/calendar/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
}

dependencies {
    api(project(":core:plugin"))
}
```

- [ ] **Step 2: Write the contract with the time logic stubbed**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarContract.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

sealed interface EventTime {
    data class Timed(val instant: Instant) : EventTime
    data class AllDay(val date: LocalDate) : EventTime
}

/** The instant this time starts at; an all-day date starts at midnight in [zone]. */
fun EventTime.instantIn(zone: ZoneId): Instant = TODO()

/** Half-open overlap of [start, end) with [windowStart, windowEnd); a zero-length span counts if it starts inside. */
fun spanOverlaps(start: Long, end: Long, windowStart: Long, windowEnd: Long): Boolean = TODO()

data class CalendarSource(val id: String, val name: String, val writable: Boolean)

/**
 * One concrete occurrence from a provider.
 *
 * [end] is exclusive: an all-day event on 23 September has start AllDay(23 Sep) and end AllDay(24 Sep),
 * as in Google Calendar and iCalendar. [start] and [end] are both Timed or both AllDay.
 * Recurring events arrive already expanded, one RemoteEvent per occurrence, each with its own [remoteId],
 * and [recurring] set. [forPerson] and [createdBy] are household PersonId values when the provider stores them.
 */
data class RemoteEvent(
    val remoteId: String,
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val recurring: Boolean,
    val forPerson: String? = null,
    val createdBy: String? = null,
)

/** Local dates in the household's [zone]; [endExclusive] is the first day not included. */
data class DateRange(val start: LocalDate, val endExclusive: LocalDate, val zone: ZoneId) {
    init {
        require(endExclusive.isAfter(start)) { "DateRange must end after it starts: $start..$endExclusive" }
    }

    val startInstant: Instant get() = start.atStartOfDay(zone).toInstant()
    val endInstant: Instant get() = endExclusive.atStartOfDay(zone).toInstant()

    fun overlaps(start: EventTime, end: EventTime): Boolean = spanOverlaps(
        start.instantIn(zone).toEpochMilli(),
        end.instantIn(zone).toEpochMilli(),
        startInstant.toEpochMilli(),
        endInstant.toEpochMilli(),
    )
}

@JvmInline
value class SyncCursor(val value: String)

/**
 * [fullReplace] = true means [upserts] is the complete set for this source and range: drop everything else.
 * It must be true whenever the request's cursor was null, and false for an incremental result.
 */
data class SyncResult(
    val upserts: List<RemoteEvent>,
    val removedIds: List<String>,
    val cursor: SyncCursor?,
    val fullReplace: Boolean,
)

class NeedsSignInException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class UnreachableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

/**
 * A calendar service. Implementations live in :provider:calendar-* and bind themselves with
 * `@Binds @IntoSet`. Behaviour is pinned by CalendarProviderContractTest in :capability:calendar-testkit.
 *
 * - [sources] and [sync] throw only [NeedsSignInException] (auth) or [UnreachableException] (network).
 * - They must be main-safe and cancellable: no uninterruptible blocking I/O. The sync engine calls them on
 *   Dispatchers.IO under a 60 s timeout, and a call that times out counts as Unreachable.
 * - [sync] without a cursor returns only events that overlap the range. With a cursor, incremental upserts
 *   MAY lie outside the range (Google's syncToken can't carry timeMin/timeMax); the store keeps them and
 *   queries filter by range.
 */
interface CalendarProvider {
    val descriptor: ProviderDescriptor

    /** Non-null [existing] means "reconnect this connection" (spec §9.6): report a Connection with the same id. */
    @Composable
    fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit)

    suspend fun sources(conn: Connection): List<CalendarSource>

    suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult
}
```

- [ ] **Step 3: Write the failing tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ContractTypesTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test

class ContractTypesTest {
    private val london = ZoneId.of("Europe/London")
    private val range = DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 30), london)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        EventTime.Timed(LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(london).toInstant())

    private fun allDay(month: Int, day: Int) = EventTime.AllDay(LocalDate.of(2026, month, day))

    @Test
    fun rangeMustEndAfterItStarts() {
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 9, 23), london)
        }
    }

    @Test
    fun allDayStartsAtMidnightInTheGivenZone() {
        assertThat(allDay(10, 24).instantIn(london)).isEqualTo(Instant.parse("2026-10-23T23:00:00Z"))
    }

    @Test
    fun timedEventIsTheSameInstantInAnyZone() {
        val t = at(24, 9)
        assertThat(t.instantIn(ZoneId.of("Asia/Tokyo"))).isEqualTo(t.instant)
    }

    @Test
    fun timedEventInsideTheRangeOverlaps() {
        assertThat(range.overlaps(at(24, 9), at(24, 10))).isTrue()
    }

    @Test
    fun eventEndingAtTheRangeStartDoesNotOverlap() {
        assertThat(range.overlaps(at(22, 23), at(23, 0))).isFalse()
    }

    @Test
    fun allDayEventOnTheExclusiveEndDayDoesNotOverlap() {
        assertThat(range.overlaps(allDay(9, 30), allDay(10, 1))).isFalse()
        assertThat(range.overlaps(allDay(9, 29), allDay(9, 30))).isTrue()
    }

    @Test
    fun multiDayAllDayEventStartingBeforeTheRangeOverlaps() {
        assertThat(range.overlaps(allDay(9, 20), allDay(9, 25))).isTrue()
    }

    @Test
    fun zeroLengthEventAtTheRangeStartOverlaps() {
        assertThat(range.overlaps(at(23, 0), at(23, 0))).isTrue()
    }
}
```

- [ ] **Step 4: Run to verify they fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: FAIL. Every test except `rangeMustEndAfterItStarts` fails with `NotImplementedError`.

- [ ] **Step 5: Implement the two functions**

In `CalendarContract.kt`, replace the two stubbed functions with:
```kotlin
/** The instant this time starts at; an all-day date starts at midnight in [zone]. */
fun EventTime.instantIn(zone: ZoneId): Instant = when (this) {
    is EventTime.Timed -> instant
    is EventTime.AllDay -> date.atStartOfDay(zone).toInstant()
}

/** Half-open overlap of [start, end) with [windowStart, windowEnd); a zero-length span counts if it starts inside. */
fun spanOverlaps(start: Long, end: Long, windowStart: Long, windowEnd: Long): Boolean =
    start < windowEnd && (end > windowStart || start >= windowStart)
```

- [ ] **Step 6: Run to verify they pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS (8 tests).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts capability/calendar
git commit -m "Add the calendar provider contract"
```

---

### Task 6: `:capability:calendar-testkit` — the shared provider contract suite

**Files:**
- Modify: `settings.gradle.kts`
- Create: `capability/calendar-testkit/build.gradle.kts`
- Create: `capability/calendar-testkit/src/main/java/uk/co/siland/culvery/capability/calendar_testkit/CalendarProviderContractTest.kt`
- Create (test fixtures, excluded from direct test discovery): `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyProvider.kt`, `.../fixtures/TinyContracts.kt`
- Test: `capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/ContractSuiteSelfTest.kt`

**Interfaces:**
- Consumes: everything in `CalendarContract.kt` (Task 5).
- Produces:
  - `abstract class CalendarProviderContractTest`. Subclasses implement these required hooks, each `protected abstract`:
    - `fun provider(): CalendarProvider`
    - `fun connection(): Connection`
    - `fun range(): DateRange`
    - `fun sourceWithEvents(): CalendarSource`
  - and may override these optional hooks, each `protected open` with default `null`, so adding a hook later never breaks a subclass:
    - `fun outOfRangeEventTitle(): String? = null`
    - `fun recurringTitle(): String? = null`
    - `fun simulateAuthFailure(): (() -> Unit)? = null`
    - `fun simulateUnreachable(): (() -> Unit)? = null`
  - Returning null from an optional hook skips the matching check.
  - `syncReturnsOnlyEventsOverlappingRange` checks cursorless (first) syncs only: with a cursor, upserts may lie outside the range.
  - The hooks are called on a fresh test-class instance per test (JUnit4), so a subclass can keep its provider in a field.
  - The 10 inherited tests are:
    - `sourceIdsAreUniqueAndStable`
    - `syncReturnsOnlyEventsOverlappingRange`
    - `endIsNeverBeforeStart`
    - `allDayEventsUseExclusiveEndDates`
    - `remoteIdsAreUniqueWithinASource`
    - `recurringOccurrencesHaveDistinctIds`
    - `firstSyncIsFullReplace`
    - `syncWithReturnedCursorDoesNotRepeatUnchangedEvents`
    - `authFailureThrowsNeedsSignIn`
    - `networkFailureThrowsUnreachable`
  - Providers use it as `testImplementation(project(":capability:calendar-testkit"))`. The testkit exposes junit, truth and kotlinx-coroutines-test as `api`.

- [ ] **Step 1: Add the module**

Append to `settings.gradle.kts`:
```kotlin
include(":capability:calendar-testkit")
```

`capability/calendar-testkit/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
}

dependencies {
    api(project(":capability:calendar"))
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // Deliberately broken providers: ContractSuiteSelfTest runs them through JUnitCore and expects failures.
    exclude("**/fixtures/**")
}
```

- [ ] **Step 2: Write the contract class with every check stubbed**

`capability/calendar-testkit/src/main/java/uk/co/siland/culvery/capability/calendar_testkit/CalendarProviderContractTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar_testkit

import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.core.plugin.Connection

abstract class CalendarProviderContractTest {
    protected abstract fun provider(): CalendarProvider
    protected abstract fun connection(): Connection
    protected abstract fun range(): DateRange
    protected abstract fun sourceWithEvents(): CalendarSource
    protected open fun outOfRangeEventTitle(): String? = null
    protected open fun recurringTitle(): String? = null
    protected open fun simulateAuthFailure(): (() -> Unit)? = null
    protected open fun simulateUnreachable(): (() -> Unit)? = null

    @Test fun sourceIdsAreUniqueAndStable() = runTest { TODO() }
    @Test fun syncReturnsOnlyEventsOverlappingRange() = runTest { TODO() }
    @Test fun endIsNeverBeforeStart() = runTest { TODO() }
    @Test fun allDayEventsUseExclusiveEndDates() = runTest { TODO() }
    @Test fun remoteIdsAreUniqueWithinASource() = runTest { TODO() }
    @Test fun recurringOccurrencesHaveDistinctIds() = runTest { TODO() }
    @Test fun firstSyncIsFullReplace() = runTest { TODO() }
    @Test fun syncWithReturnedCursorDoesNotRepeatUnchangedEvents() = runTest { TODO() }
    @Test fun authFailureThrowsNeedsSignIn() = runTest { TODO() }
    @Test fun networkFailureThrowsUnreachable() = runTest { TODO() }
}
```

- [ ] **Step 3: Write the fixtures: a well-behaved tiny provider and six broken variants**

`capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyProvider.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar_testkit.fixtures

import androidx.compose.runtime.Composable
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

/** A minimal provider whose flags each break one rule of the contract. */
class TinyProvider(
    private val leakOutOfRange: Boolean = false,
    private val partialFirstSync: Boolean = false,
    private val repeatOnCursor: Boolean = false,
    private val rawNetworkErrors: Boolean = false,
    private val inclusiveAllDayEnd: Boolean = false,
    private val rawAuthErrors: Boolean = false,
) : CalendarProvider {
    override val descriptor = ProviderDescriptor("calendar.tiny", "Tiny", "event", setOf(Feature.READ))

    private var failNext: Throwable? = null

    fun failNextWith(error: Throwable) {
        failNext = error
    }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
    }

    override suspend fun sources(conn: Connection) = listOf(SOURCE)

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        failNext?.let { error ->
            failNext = null
            throw when {
                rawNetworkErrors && error is UnreachableException -> IOException("socket closed")
                rawAuthErrors && error is NeedsSignInException -> IllegalStateException("HTTP 401")
                else -> error
            }
        }
        if (cursor != null && !repeatOnCursor) return SyncResult(emptyList(), emptyList(), cursor, fullReplace = false)
        val events = all(range.zone).filter { leakOutOfRange || range.overlaps(it.start, it.end) }
        return SyncResult(events, emptyList(), SyncCursor("c1"), fullReplace = !partialFirstSync)
    }

    private fun all(zone: ZoneId): List<RemoteEvent> {
        fun at(date: LocalDate, hour: Int) = EventTime.Timed(date.atTime(hour, 0).atZone(zone).toInstant())
        val d = LocalDate.of(2026, 9, 24)
        val far = LocalDate.of(2026, 11, 7)
        return listOf(
            RemoteEvent("walk", "Walk", at(d, 9), at(d, 10), recurring = false),
            RemoteEvent("yoga-1", "Yoga", at(d, 18), at(d, 19), recurring = true),
            RemoteEvent("yoga-2", "Yoga", at(d.plusDays(7), 18), at(d.plusDays(7), 19), recurring = true),
            RemoteEvent("holiday", "Holiday", EventTime.AllDay(d), EventTime.AllDay(d.plusDays(2)), recurring = false),
            // An inclusive end on a one-day event equals its start date.
            RemoteEvent("bins", "Bins", EventTime.AllDay(d), EventTime.AllDay(if (inclusiveAllDayEnd) d else d.plusDays(1)), recurring = false),
            RemoteEvent("far", "Far away", at(far, 9), at(far, 10), recurring = false),
        )
    }

    companion object {
        val SOURCE = CalendarSource("tiny", "Tiny", writable = false)
    }
}
```

`capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/fixtures/TinyContracts.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar_testkit.fixtures

import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar_testkit.CalendarProviderContractTest
import uk.co.siland.culvery.core.plugin.Connection

abstract class TinyContract(private val tiny: TinyProvider) : CalendarProviderContractTest() {
    override fun provider() = tiny
    override fun connection() = Connection("c", "calendar.tiny", "Tiny", emptyMap())
    override fun range() = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), ZoneId.of("Europe/London"))
    override fun sourceWithEvents() = TinyProvider.SOURCE
    override fun outOfRangeEventTitle() = "Far away"
    override fun recurringTitle() = "Yoga"
    override fun simulateAuthFailure() = { tiny.failNextWith(NeedsSignInException("expired")) }
    override fun simulateUnreachable() = { tiny.failNextWith(UnreachableException("offline")) }
}

class GoodContract : TinyContract(TinyProvider())
class LeakingContract : TinyContract(TinyProvider(leakOutOfRange = true))
class PartialFirstSyncContract : TinyContract(TinyProvider(partialFirstSync = true))
class RepeatingContract : TinyContract(TinyProvider(repeatOnCursor = true))
class RawErrorContract : TinyContract(TinyProvider(rawNetworkErrors = true))
class InclusiveAllDayEndContract : TinyContract(TinyProvider(inclusiveAllDayEnd = true))
class RawAuthErrorContract : TinyContract(TinyProvider(rawAuthErrors = true))
```

- [ ] **Step 4: Write the failing self-test**

`capability/calendar-testkit/src/test/java/uk/co/siland/culvery/capability/calendar_testkit/ContractSuiteSelfTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar_testkit

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.JUnitCore
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.GoodContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.InclusiveAllDayEndContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.LeakingContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.PartialFirstSyncContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RawAuthErrorContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RawErrorContract
import uk.co.siland.culvery.capability.calendar_testkit.fixtures.RepeatingContract

/** Proves the suite passes a correct provider and catches each kind of broken one. */
class ContractSuiteSelfTest {
    /** Names of the failed checks; each must have failed on an assertion, not crashed. */
    private fun failuresOf(contract: Class<*>): List<String> {
        val failures = JUnitCore.runClasses(contract).failures
        failures.forEach { f ->
            assertWithMessage("${f.description.methodName} should fail an assertion, not crash:\n${f.trace}")
                .that(f.exception).isInstanceOf(AssertionError::class.java)
        }
        return failures.map { it.description.methodName }
    }

    @Test
    fun wellBehavedProviderPassesEveryCheck() {
        val result = JUnitCore.runClasses(GoodContract::class.java)
        assertThat(result.failures.map { "${it.description.methodName}: ${it.message}" }).isEmpty()
        assertThat(result.runCount).isEqualTo(10)
        // A skipped check would otherwise count as a pass.
        assertThat(result.assumptionFailureCount).isEqualTo(0)
    }

    @Test
    fun leakedOutOfRangeEventIsCaught() {
        assertThat(failuresOf(LeakingContract::class.java)).containsExactly("syncReturnsOnlyEventsOverlappingRange")
    }

    @Test
    fun partialFirstSyncIsCaught() {
        assertThat(failuresOf(PartialFirstSyncContract::class.java)).containsExactly("firstSyncIsFullReplace")
    }

    @Test
    fun repeatingUnchangedEventsOnACursorIsCaught() {
        assertThat(failuresOf(RepeatingContract::class.java))
            .containsExactly("syncWithReturnedCursorDoesNotRepeatUnchangedEvents")
    }

    @Test
    fun rawNetworkExceptionIsCaught() {
        assertThat(failuresOf(RawErrorContract::class.java)).containsExactly("networkFailureThrowsUnreachable")
    }

    @Test
    fun inclusiveAllDayEndIsCaught() {
        assertThat(failuresOf(InclusiveAllDayEndContract::class.java)).containsExactly("allDayEventsUseExclusiveEndDates")
    }

    @Test
    fun rawAuthExceptionIsCaught() {
        assertThat(failuresOf(RawAuthErrorContract::class.java)).containsExactly("authFailureThrowsNeedsSignIn")
    }
}
```

- [ ] **Step 5: Run to verify it fails**

Run: `./gradlew :capability:calendar-testkit:testDebugUnitTest`
Expected: FAIL. `wellBehavedProviderPassesEveryCheck` lists 10 `NotImplementedError` failures, and the other six fail in `failuresOf` because the stubbed checks crash with `NotImplementedError` instead of failing an assertion. The fixture classes do not run on their own; the `exclude` in the build file stops that.

- [ ] **Step 6: Implement the checks**

Replace `CalendarProviderContractTest.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar_testkit

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.instantIn
import uk.co.siland.culvery.core.plugin.Connection

/**
 * Every calendar provider subclasses this in its own tests (spec §11). Invariants are checked on every
 * source; fixture-specific checks (out-of-range, recurring, cursor) use [sourceWithEvents].
 *
 * Range checks apply to cursorless (first) syncs only. With a cursor, incremental upserts MAY lie outside
 * the range (Google's syncToken can't carry timeMin/timeMax); the store keeps them and queries filter.
 *
 * Optional hooks are `open` with a null default, so a new hook never breaks an existing subclass.
 */
abstract class CalendarProviderContractTest {
    protected abstract fun provider(): CalendarProvider
    protected abstract fun connection(): Connection
    /** The window the app would ask for, e.g. yesterday to 14 days ahead. */
    protected abstract fun range(): DateRange
    /** A source with events inside [range]. */
    protected abstract fun sourceWithEvents(): CalendarSource
    /** An event in [sourceWithEvents] that lies outside [range] but within 60 days of it; null to skip. */
    protected open fun outOfRangeEventTitle(): String? = null
    /** A recurring event with at least two occurrences in [range] in [sourceWithEvents]; null to skip. */
    protected open fun recurringTitle(): String? = null
    /** Makes the next call fail as an expired sign-in would; null if the provider can't simulate it. */
    protected open fun simulateAuthFailure(): (() -> Unit)? = null
    /** Makes the next call fail as a network outage would; null if the provider can't simulate it. */
    protected open fun simulateUnreachable(): (() -> Unit)? = null

    private val subject by lazy { provider() }
    private val conn by lazy { connection() }
    private val window by lazy { range() }

    private suspend fun firstSync(source: CalendarSource = sourceWithEvents(), range: DateRange = window): SyncResult =
        subject.sync(conn, source, range, null)

    private suspend fun everySourceFirstSync(): List<RemoteEvent> =
        subject.sources(conn).flatMap { firstSync(it).upserts }

    private suspend fun failureOfFirstSync(): Throwable? =
        try {
            firstSync()
            null
        } catch (e: Throwable) {
            e
        }

    @Test
    fun sourceIdsAreUniqueAndStable() = runTest {
        val first = subject.sources(conn).map { it.id }
        val second = subject.sources(conn).map { it.id }
        assertThat(first).containsNoDuplicates()
        assertThat(second).containsExactlyElementsIn(first).inOrder()
        assertThat(first).contains(sourceWithEvents().id)
    }

    /** Cursorless syncs only: see the class comment. */
    @Test
    fun syncReturnsOnlyEventsOverlappingRange() = runTest {
        assertWithMessage("fixture: sourceWithEvents() has no events in range()").that(firstSync().upserts).isNotEmpty()
        everySourceFirstSync().forEach { e ->
            assertWithMessage("'${e.title}' (${e.start} to ${e.end}) is outside $window")
                .that(window.overlaps(e.start, e.end)).isTrue()
        }
        val title = outOfRangeEventTitle() ?: return@runTest
        val wide = DateRange(window.start.minusDays(60), window.endExclusive.plusDays(60), window.zone)
        assertWithMessage("fixture: '$title' should exist within 60 days of range()")
            .that(firstSync(range = wide).upserts.map { it.title }).contains(title)
        assertThat(firstSync().upserts.map { it.title }).doesNotContain(title)
    }

    @Test
    fun endIsNeverBeforeStart() = runTest {
        everySourceFirstSync().forEach { e ->
            assertWithMessage("'${e.title}': start and end must both be timed or both all-day")
                .that(e.start::class).isEqualTo(e.end::class)
            assertWithMessage("'${e.title}': end is before start")
                .that(e.end.instantIn(window.zone)).isAtLeast(e.start.instantIn(window.zone))
        }
    }

    @Test
    fun allDayEventsUseExclusiveEndDates() = runTest {
        everySourceFirstSync().forEach { e ->
            val start = e.start as? EventTime.AllDay ?: return@forEach
            val end = e.end as? EventTime.AllDay ?: return@forEach
            assertWithMessage("'${e.title}': an all-day end date is exclusive, so it must be after the start date")
                .that(end.date).isGreaterThan(start.date)
        }
    }

    @Test
    fun remoteIdsAreUniqueWithinASource() = runTest {
        subject.sources(conn).forEach { source ->
            assertWithMessage("source ${source.id}").that(firstSync(source).upserts.map { it.remoteId }).containsNoDuplicates()
        }
    }

    @Test
    fun recurringOccurrencesHaveDistinctIds() = runTest {
        val title = recurringTitle()
        assumeTrue("provider has no recurring fixture", title != null)
        val occurrences = firstSync().upserts.filter { it.title == title }
        assertThat(occurrences.size).isAtLeast(2)
        assertThat(occurrences.all { it.recurring }).isTrue()
        assertThat(occurrences.map { it.remoteId }).containsNoDuplicates()
    }

    @Test
    fun firstSyncIsFullReplace() = runTest {
        subject.sources(conn).forEach { source ->
            assertWithMessage("source ${source.id}").that(firstSync(source).fullReplace).isTrue()
        }
    }

    @Test
    fun syncWithReturnedCursorDoesNotRepeatUnchangedEvents() = runTest {
        val first = firstSync()
        assumeTrue("provider returns no cursor", first.cursor != null)
        val second = subject.sync(conn, sourceWithEvents(), window, first.cursor)
        assertThat(second.upserts).isEmpty()
        assertThat(second.removedIds).isEmpty()
        assertWithMessage("an unchanged incremental result must not be a full replace: it would wipe the cache")
            .that(second.fullReplace).isFalse()
    }

    @Test
    fun authFailureThrowsNeedsSignIn() = runTest {
        val simulate = simulateAuthFailure()
        assumeTrue("provider cannot simulate an auth failure", simulate != null)
        simulate!!.invoke()
        assertThat(failureOfFirstSync()).isInstanceOf(NeedsSignInException::class.java)
    }

    @Test
    fun networkFailureThrowsUnreachable() = runTest {
        val simulate = simulateUnreachable()
        assumeTrue("provider cannot simulate a network failure", simulate != null)
        simulate!!.invoke()
        assertThat(failureOfFirstSync()).isInstanceOf(UnreachableException::class.java)
    }
}
```

- [ ] **Step 7: Run to verify it passes**

Run: `./gradlew :capability:calendar-testkit:testDebugUnitTest`
Expected: PASS (7 tests in `ContractSuiteSelfTest`). If a broken fixture trips more than its one check, read the listed method names. Fix the fixture, not the expectation, unless the extra failure is a genuine second breach.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts capability/calendar-testkit
git commit -m "Add the shared calendar provider contract test suite"
```

---

### Task 7: `:provider:calendar-fake` — debug sample provider

**Files:**
- Modify: `settings.gradle.kts`
- Create: `provider/calendar-fake/build.gradle.kts`
- Create: `provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`, `SampleEvents.kt`, `di/FakeCalendarModule.kt`
- Create: `provider/calendar-fake/src/test/resources/robolectric.properties`
- Test: `provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderContractTest.kt`, `FakeCalendarProviderTest.kt`, `ConnectScreenTest.kt`

**Interfaces:**
- Consumes:
  - the Task 5 contract, including `ConnectScreen(existing, onConnected, onCancel)`
  - `CalendarProviderContractTest` (Task 6), with required hooks `provider`, `connection`, `range`, `sourceWithEvents` and optional `open` hooks `outOfRangeEventTitle`, `recurringTitle`, `simulateAuthFailure`, `simulateUnreachable`
  - `HhPillButton(text, onClick, modifier, primary)`
  - `CulveryTheme`
- Produces (package `uk.co.siland.culvery.provider.calendar_fake`):
  - `@Singleton class FakeCalendarProvider(clock: Clock) : CalendarProvider`
    - `@Inject constructor()` uses `Clock.systemUTC()`.
    - `fun failNextWith(error: Throwable)`
    - Companion constants: `ID = "calendar.fake"`, `SOURCE_ALEX = "fake-alex"`, `SOURCE_SAM = "fake-sam"`, `SOURCE_MIA = "fake-mia"`, `SOURCE_FAMILY = "fake-family"`, `SOURCE_SCHOOL = "fake-school"` (named "School terms"), and `SOURCES: List<CalendarSource>` in that order, all read-only.
    - Sync behaviour: the first sync gives `fullReplace = true` and cursor `"v1:<today>"`. The same cursor on the same day gives no changes; on another day, a full replace.
    - `ConnectScreen` reports a new `Connection` with a random id, or, when `existing` is non-null, one with `existing.id`.
  - The sample data mirrors `docs/design/house_hub_handoff/screenshots/calendar-sheets/02-calendar-week-dark.png`, relative to today (day 0), with the prototype's durations:

    | Day | Events (source) |
    |---|---|
    | 0 | School run 07:45–08:30 (Sam), Boiler service 10:00–11:00 (Family), Plumber quote call 13:00–13:30 (Family), Swimming 16:00–17:00 (Mia, weekly), Dinner with Jo & Priya 19:30–21:00 (Alex) |
    | +1 | Office day 09:00–17:00 (Alex), Football 18:00–19:00 (Mia) |
    | +2 | Bin day, all day (Family, weekly), Dentist 12:30–13:30 (Sam) |
    | +3 | INSET day — no school, all day (School terms), Piano 15:30–16:30 (Mia, weekly), Book club 20:00–22:00 (Sam) |
    | +4 | Pizza night 19:00–23:00 (Family) |
    | +5 | Parkrun 09:30–11:00 (Alex), Birthday party 14:00–17:00 (Mia) |
    | +6 | Sunday lunch at Gran's 12:00–15:00 (Family) |
    | +8 to +10 | Half term, all day, three days (Family) |
    | +20 | School trip (Mia), outside the sync window: the contract suite's out-of-range fixture |

  - Weekly events are expanded from last week to three weeks ahead, one occurrence per week with its own id, as a provider must.
  - `FakeCalendarModule` binds it `@IntoSet CalendarProvider`.

- [ ] **Step 1: Add the module**

Append to `settings.gradle.kts`:
```kotlin
include(":provider:calendar-fake")
```

`provider/calendar-fake/build.gradle.kts`:
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

`provider/calendar-fake/src/test/resources/robolectric.properties`:
```properties
qualifiers=w1280dp-h800dp-land-hdpi
```

- [ ] **Step 2: Write the provider with sync stubbed, and the sample data**

`provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/SampleEvents.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.RemoteEvent
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_ALEX
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_FAMILY
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_MIA
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_SAM
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider.Companion.SOURCE_SCHOOL

/**
 * The hand-off's week (screenshots/calendar-sheets/02-calendar-week-dark.png, durations from Culvery.dc.html),
 * relative to today, plus the fixtures the contract suite needs.
 */
internal object SampleEvents {
    private class Timed(
        val id: String,
        val source: String,
        val title: String,
        val day: Long,
        val start: LocalTime,
        val minutes: Long,
        val weekly: Boolean = false,
    )

    private class AllDay(
        val id: String,
        val source: String,
        val title: String,
        val day: Long,
        val days: Long = 1,
        val weekly: Boolean = false,
    )

    private fun t(hour: Int, minute: Int) = LocalTime.of(hour, minute)

    private val timed = listOf(
        Timed("school-run", SOURCE_SAM, "School run", 0, t(7, 45), 45),
        Timed("boiler", SOURCE_FAMILY, "Boiler service", 0, t(10, 0), 60),
        Timed("plumber", SOURCE_FAMILY, "Plumber quote call", 0, t(13, 0), 30),
        Timed("swimming", SOURCE_MIA, "Swimming", 0, t(16, 0), 60, weekly = true),
        Timed("dinner", SOURCE_ALEX, "Dinner with Jo & Priya", 0, t(19, 30), 90),
        Timed("office", SOURCE_ALEX, "Office day", 1, t(9, 0), 480),
        Timed("football", SOURCE_MIA, "Football", 1, t(18, 0), 60),
        Timed("dentist", SOURCE_SAM, "Dentist", 2, t(12, 30), 60),
        Timed("piano", SOURCE_MIA, "Piano", 3, t(15, 30), 60, weekly = true),
        Timed("book-club", SOURCE_SAM, "Book club", 3, t(20, 0), 120),
        Timed("pizza", SOURCE_FAMILY, "Pizza night", 4, t(19, 0), 240),
        Timed("parkrun", SOURCE_ALEX, "Parkrun", 5, t(9, 30), 90),
        Timed("party", SOURCE_MIA, "Birthday party", 5, t(14, 0), 180),
        Timed("lunch", SOURCE_FAMILY, "Sunday lunch at Gran's", 6, t(12, 0), 180),
        // Outside a 15-day window: the contract suite's out-of-range fixture.
        Timed("school-trip", SOURCE_MIA, "School trip", 20, t(8, 30), 420),
    )

    private val allDay = listOf(
        AllDay("bins", SOURCE_FAMILY, "Bin day", 2, weekly = true),
        AllDay("inset", SOURCE_SCHOOL, "INSET day — no school", 3),
        // After the visible week, so the week view matches the hand-off.
        AllDay("half-term", SOURCE_FAMILY, "Half term", 8, days = 3),
    )

    /** Day offsets of each occurrence: a weekly event runs from last week to three weeks ahead. */
    private fun occurrences(day: Long, weekly: Boolean): List<Long> =
        if (weekly) (-1..3).map { week -> day + 7L * week } else listOf(day)

    fun forSource(sourceId: String, today: LocalDate, zone: ZoneId): List<RemoteEvent> = buildList {
        timed.filter { it.source == sourceId }.forEach { e ->
            occurrences(e.day, e.weekly).forEach { offset ->
                val date = today.plusDays(offset)
                val start = date.atTime(e.start).atZone(zone).toInstant()
                add(
                    RemoteEvent(
                        "${e.id}-$date",
                        e.title,
                        EventTime.Timed(start),
                        EventTime.Timed(start.plusSeconds(e.minutes * 60)),
                        recurring = e.weekly,
                    ),
                )
            }
        }
        allDay.filter { it.source == sourceId }.forEach { e ->
            occurrences(e.day, e.weekly).forEach { offset ->
                val date = today.plusDays(offset)
                add(
                    RemoteEvent(
                        "${e.id}-$date",
                        e.title,
                        EventTime.AllDay(date),
                        EventTime.AllDay(date.plusDays(e.days)),
                        recurring = e.weekly,
                    ),
                )
            }
        }
    }
}
```

`provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProvider.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.SyncCursor
import uk.co.siland.culvery.capability.calendar.SyncResult
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.ui.HhPillButton

/** Debug-only sample data matching the design hand-off, generated relative to today so it never goes stale. */
@Singleton
class FakeCalendarProvider(private val clock: Clock) : CalendarProvider {
    @Inject constructor() : this(Clock.systemUTC())

    override val descriptor = ProviderDescriptor(ID, "Sample calendar (debug)", "event", setOf(Feature.READ))

    @Volatile private var failNext: Throwable? = null

    /** For contract tests: the next sources() or sync() call throws [error]. */
    fun failNextWith(error: Throwable) {
        failNext = error
    }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HhPillButton(
                "Connect sample calendar",
                onClick = {
                    val id = existing?.id ?: UUID.randomUUID().toString()
                    onConnected(Connection(id, ID, "Sample calendar", emptyMap()))
                },
                primary = true,
            )
            HhPillButton("Cancel", onClick = onCancel)
        }
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> {
        throwIfFailing()
        return SOURCES
    }

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult =
        TODO()

    private fun throwIfFailing() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    companion object {
        const val ID = "calendar.fake"
        const val SOURCE_ALEX = "fake-alex"
        const val SOURCE_SAM = "fake-sam"
        const val SOURCE_MIA = "fake-mia"
        const val SOURCE_FAMILY = "fake-family"
        const val SOURCE_SCHOOL = "fake-school"

        val SOURCES = listOf(
            CalendarSource(SOURCE_ALEX, "Alex", writable = false),
            CalendarSource(SOURCE_SAM, "Sam", writable = false),
            CalendarSource(SOURCE_MIA, "Mia", writable = false),
            CalendarSource(SOURCE_FAMILY, "Family", writable = false),
            CalendarSource(SOURCE_SCHOOL, "School terms", writable = false),
        )
    }
}
```

`provider/calendar-fake/src/main/java/uk/co/siland/culvery/provider/calendar_fake/di/FakeCalendarModule.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class FakeCalendarModule {
    @Binds
    @IntoSet
    abstract fun provider(impl: FakeCalendarProvider): CalendarProvider
}
```

- [ ] **Step 3: Write the failing tests**

`provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderContractTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.capability.calendar.CalendarSource
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.NeedsSignInException
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar_testkit.CalendarProviderContractTest
import uk.co.siland.culvery.core.plugin.Connection

class FakeCalendarProviderContractTest : CalendarProviderContractTest() {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val fake = FakeCalendarProvider(Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone))

    override fun provider() = fake
    override fun connection() = Connection("c1", FakeCalendarProvider.ID, "Sample calendar", emptyMap())
    override fun range() = DateRange(today.minusDays(1), today.plusDays(15), zone)
    override fun sourceWithEvents() = CalendarSource(FakeCalendarProvider.SOURCE_MIA, "Mia", writable = false)
    override fun outOfRangeEventTitle() = "School trip"
    override fun recurringTitle() = "Piano"
    override fun simulateAuthFailure() = { fake.failNextWith(NeedsSignInException("expired")) }
    override fun simulateUnreachable() = { fake.failNextWith(UnreachableException("offline")) }
}
```

`provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/FakeCalendarProviderTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake

import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.capability.calendar.DateRange
import uk.co.siland.culvery.capability.calendar.EventTime
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.capability.calendar.instantIn
import uk.co.siland.culvery.core.plugin.Connection

class FakeCalendarProviderTest {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 9, 23)
    private val conn = Connection("c1", FakeCalendarProvider.ID, "Sample calendar", emptyMap())
    private val hm = DateTimeFormatter.ofPattern("HH:mm")

    private fun providerOn(date: LocalDate) =
        FakeCalendarProvider(Clock.fixed(date.atTime(10, 0).atZone(zone).toInstant(), zone))

    private fun windowFrom(date: LocalDate) = DateRange(date.minusDays(1), date.plusDays(15), zone)

    private fun time(t: EventTime) = t.instantIn(zone).atZone(zone)

    private fun source(id: String) = FakeCalendarProvider.SOURCES.first { it.id == id }

    @Test
    fun sourcesAreThePeopleFamilyAndSchoolTermsAllReadOnly() = runTest {
        val sources = providerOn(today).sources(conn)
        assertThat(sources.map { it.name }).containsExactly("Alex", "Sam", "Mia", "Family", "School terms").inOrder()
        assertThat(sources.none { it.writable }).isTrue()
    }

    @Test
    fun todayMatchesTheHandOff() = runTest {
        val fake = providerOn(today)
        val todays = fake.sources(conn)
            .flatMap { s -> fake.sync(conn, s, windowFrom(today), null).upserts.map { s.id to it } }
            .filter { (_, e) -> e.start is EventTime.Timed && time(e.start).toLocalDate() == today }
            .sortedBy { (_, e) -> e.start.instantIn(zone) }
            .map { (source, e) -> "${time(e.start).format(hm)}–${time(e.end).format(hm)} ${e.title} ($source)" }
        assertThat(todays).containsExactly(
            "07:45–08:30 School run (fake-sam)",
            "10:00–11:00 Boiler service (fake-family)",
            "13:00–13:30 Plumber quote call (fake-family)",
            "16:00–17:00 Swimming (fake-mia)",
            "19:30–21:00 Dinner with Jo & Priya (fake-alex)",
        ).inOrder()
    }

    @Test
    fun pianoRepeatsWeeklyOnMiasCalendar() = runTest {
        val fake = providerOn(today)
        val piano = fake.sync(conn, source(FakeCalendarProvider.SOURCE_MIA), windowFrom(today), null).upserts
            .filter { it.title == "Piano" }
        assertThat(piano.map { time(it.start).toLocalDateTime().toString() })
            .containsExactly("2026-09-26T15:30", "2026-10-03T15:30").inOrder()
        assertThat(piano.all { it.recurring }).isTrue()
    }

    @Test
    fun swimmingAndBinDayRepeatWeekly() = runTest {
        val fake = providerOn(today)
        val swimming = fake.sync(conn, source(FakeCalendarProvider.SOURCE_MIA), windowFrom(today), null).upserts
            .filter { it.title == "Swimming" }
        assertThat(swimming.map { time(it.start).toLocalDate().toString() })
            .containsExactly("2026-09-23", "2026-09-30", "2026-10-07").inOrder()
        assertThat(swimming.all { it.recurring }).isTrue()

        val bins = fake.sync(conn, source(FakeCalendarProvider.SOURCE_FAMILY), windowFrom(today), null).upserts
            .filter { it.title == "Bin day" }
        assertThat(bins.map { it.start }).containsExactly(
            EventTime.AllDay(LocalDate.of(2026, 9, 25)),
            EventTime.AllDay(LocalDate.of(2026, 10, 2)),
        ).inOrder()
        assertThat(bins.map { it.end }).containsExactly(
            EventTime.AllDay(LocalDate.of(2026, 9, 26)),
            EventTime.AllDay(LocalDate.of(2026, 10, 3)),
        ).inOrder()
        assertThat(bins.all { it.recurring }).isTrue()
    }

    @Test
    fun insetDayComesFromTheReadOnlySchoolTermsCalendar() = runTest {
        val school = source(FakeCalendarProvider.SOURCE_SCHOOL)
        val events = providerOn(today).sync(conn, school, windowFrom(today), null).upserts
        assertThat(school.name).isEqualTo("School terms")
        assertThat(school.writable).isFalse()
        assertThat(events.map { Triple(it.title, it.start, it.end) }).containsExactly(
            Triple("INSET day — no school", EventTime.AllDay(LocalDate.of(2026, 9, 26)), EventTime.AllDay(LocalDate.of(2026, 9, 27))),
        )
    }

    @Test
    fun halfTermIsAThreeDayAllDayEventAfterTheVisibleWeek() = runTest {
        val fake = providerOn(today)
        val halfTerm = fake.sync(conn, source(FakeCalendarProvider.SOURCE_FAMILY), windowFrom(today), null).upserts
            .single { it.title == "Half term" }
        assertThat(halfTerm.start).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 10, 1)))
        assertThat(halfTerm.end).isEqualTo(EventTime.AllDay(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun cursorFromYesterdayGivesAFullReplace() = runTest {
        val source = FakeCalendarProvider.SOURCES.first()
        val yesterday = providerOn(today).sync(conn, source, windowFrom(today), null)
        val tomorrow = today.plusDays(1)
        val next = providerOn(tomorrow).sync(conn, source, windowFrom(tomorrow), yesterday.cursor)
        assertThat(next.fullReplace).isTrue()
        assertThat(next.upserts).isNotEmpty()
        assertThat(next.cursor).isNotEqualTo(yesterday.cursor)
    }

    @Test
    fun failNextWithFailsOnlyTheNextCall() = runTest {
        val fake = providerOn(today)
        fake.failNextWith(UnreachableException("offline"))
        val first = runCatching { fake.sources(conn) }
        assertThat(first.exceptionOrNull()).isInstanceOf(UnreachableException::class.java)
        assertThat(fake.sources(conn)).hasSize(5)
    }
}
```

`provider/calendar-fake/src/test/java/uk/co/siland/culvery/provider/calendar_fake/ConnectScreenTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.calendar_fake

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class ConnectScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun connectButtonReportsASampleConnection() {
        var connected: Connection? = null
        compose.setContent {
            CulveryTheme(dark = true) {
                FakeCalendarProvider().ConnectScreen(existing = null, onConnected = { connected = it }, onCancel = {})
            }
        }
        compose.onNodeWithText("Connect sample calendar").performClick()
        assertThat(connected?.providerId).isEqualTo(FakeCalendarProvider.ID)
        assertThat(connected?.label).isEqualTo("Sample calendar")
        assertThat(connected?.id).isNotEmpty()
    }

    @Test
    fun reconnectKeepsTheExistingConnectionId() {
        val existing = Connection("debug-sample", FakeCalendarProvider.ID, "Sample calendar", emptyMap())
        var connected: Connection? = null
        compose.setContent {
            CulveryTheme(dark = true) {
                FakeCalendarProvider().ConnectScreen(existing = existing, onConnected = { connected = it }, onCancel = {})
            }
        }
        compose.onNodeWithText("Connect sample calendar").performClick()
        assertThat(connected?.id).isEqualTo("debug-sample")
    }

    @Test
    fun cancelReportsCancel() {
        var cancelled = false
        compose.setContent {
            CulveryTheme(dark = true) {
                FakeCalendarProvider().ConnectScreen(existing = null, onConnected = {}, onCancel = { cancelled = true })
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertThat(cancelled).isTrue()
    }
}
```

- [ ] **Step 4: Run to verify they fail**

Run: `./gradlew :provider:calendar-fake:testDebugUnitTest`
Expected: FAIL. Every test that calls `sync` (most of the contract suite, and the `FakeCalendarProviderTest` data tests) fails with `NotImplementedError`. `sourceIdsAreUniqueAndStable`, `sourcesAreThePeopleFamilyAndSchoolTermsAllReadOnly`, `failNextWithFailsOnlyTheNextCall` and the three `ConnectScreenTest` tests pass.

- [ ] **Step 5: Implement `sync`**

In `FakeCalendarProvider.kt`:

1. Add the import:
   ```kotlin
   import java.time.LocalDate
   ```
2. Replace the stubbed `sync` with:
   ```kotlin
       override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
           throwIfFailing()
           val today = LocalDate.now(clock.withZone(range.zone))
           // The cursor carries the date, so the relative sample data is replaced once a day.
           val current = SyncCursor("v1:$today")
           if (cursor == current) return SyncResult(emptyList(), emptyList(), current, fullReplace = false)
           val events = SampleEvents.forSource(source.id, today, range.zone).filter { range.overlaps(it.start, it.end) }
           return SyncResult(events, emptyList(), current, fullReplace = true)
       }
   ```

- [ ] **Step 6: Run to verify they pass**

Run: `./gradlew :provider:calendar-fake:testDebugUnitTest`
Expected: PASS: 10 contract tests, 8 `FakeCalendarProviderTest`, 3 `ConnectScreenTest`.

The guard's rule "a capability may not depend on a provider, even in tests" is already proven by Task 4's manual check and `capabilityMayNotDependOnAProviderEvenInTests`; there is no second manual check here.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts provider/calendar-fake
git commit -m "Add the debug sample calendar provider with the hand-off's week"
```

---

### Task 8: `calendar.db` and `CalendarStore`

**Files:**
- Modify: `capability/calendar/build.gradle.kts`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Stored.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`
- Create: `capability/calendar/src/test/resources/robolectric.properties`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/TestDatabases.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`
- Generated, commit it: `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/1.json`

**Interfaces:**
- Consumes:
  - the Task 5 contract
  - `Connection`, `ConnectionHealth` (Task 3)
  - `PersonId` (`:core:household`; `PersonId.FAMILY`)
- Produces (package `uk.co.siland.culvery.capability.calendar`):
  - `data class SourceMapping(val person: PersonId, val visible: Boolean)` with `SourceMapping.Default` = Family, visible
  - `data class StoredConnection(val connection: Connection, val health: ConnectionHealth, val lastSyncMillis: Long?)`
  - `data class StoredSource(val connectionId: String, val source: CalendarSource, val mapping: SourceMapping)`
  - `data class StoredEvent(val connectionId: String, val sourceId: String, val remoteId: String, val title: String, val start: EventTime, val end: EventTime, val recurring: Boolean, val forPerson: String?, val createdBy: String?, val sourcePerson: PersonId, val startSort: Long, val endSort: Long)`
  - `@Singleton class CalendarStore @Inject constructor(db: CalendarDatabase)`:
    - `fun connections(): Flow<List<StoredConnection>>`
    - `fun connectionIds(): Flow<List<String>>`, distinct and ordered by id
    - `suspend fun connectionsNow(): List<StoredConnection>`
    - `suspend fun addConnection(connection: Connection, sources: List<CalendarSource>, mapping: Map<String, SourceMapping>)`, the single transactional insert of a connection and its sources. A source missing from `mapping` gets `SourceMapping.Default`. There is no source editing in 2a (Plan 4).
    - `suspend fun visibleSourcesFor(connectionId: String): List<StoredSource>`
    - `suspend fun setHealth(connectionId: String, health: ConnectionHealth)`
    - `suspend fun markSynced(connectionId: String, atMillis: Long)`, which also sets health OK
    - `suspend fun cursor(connectionId: String, sourceId: String, range: DateRange): SyncCursor?`. It returns null when the stored window start or zone differs from `range`'s.
    - `suspend fun applySync(connectionId: String, sourceId: String, range: DateRange, result: SyncResult)`, done in one transaction. It honours `fullReplace` and `removedIds` (deleted in chunks of 500, below SQLite's variable limit), and stores the cursor with `"${range.start}|${range.zone.id}"`.
    - `fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<StoredEvent>>`. It returns overlapping events from visible sources only, ordered by `startSort` then `title`.
  - `CalendarDatabase` (Room, file `calendar.db`, `exportSchema = true`, version 1), provided `@Singleton` by `CalendarModule` without any destructive-migration fallback. `CalendarModule` also declares `@Multibinds Set<CalendarProvider>`.
  - Test helper `calendarDb(): CalendarDatabase`, an in-memory database for tests.

- [ ] **Step 1: Add Hilt, Room and household to the module**

Replace `capability/calendar/build.gradle.kts` with:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
}

dependencies {
    api(project(":core:plugin"))
    // PersonId and Person appear in this module's public API (SourceMapping, EventUi).
    api(project(":core:household"))
}
```

`capability/calendar/src/test/resources/robolectric.properties`:
```properties
qualifiers=w1280dp-h800dp-land-hdpi
```

- [ ] **Step 2: Write the database**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/db/CalendarDatabase.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "connection")
data class ConnectionEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val label: String,
    val configJson: String,
    /** OK, UNREACHABLE, NEEDS_SIGN_IN or ERROR. */
    val health: String,
    val healthMessage: String?,
    val lastSyncMillis: Long?,
)

@Entity(tableName = "source", primaryKeys = ["connectionId", "sourceId"])
data class SourceEntity(
    val connectionId: String,
    val sourceId: String,
    val name: String,
    val writable: Boolean,
    val visible: Boolean,
    /** A household PersonId value, or "family". */
    val personId: String,
)

/** Timed events set the *Instant columns; all-day events set the *Date columns (ISO dates, end exclusive). */
@Entity(
    tableName = "event",
    primaryKeys = ["connectionId", "sourceId", "remoteId"],
    indices = [Index("startSort")],
)
data class EventEntity(
    val connectionId: String,
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val startInstant: Long?,
    val startDate: String?,
    val endInstant: Long?,
    val endDate: String?,
    val recurring: Boolean,
    val forPerson: String?,
    val createdBy: String?,
    /** Epoch millis; for all-day events, midnight of the date in the household zone at sync time. */
    val startSort: Long,
    val endSort: Long,
)

@Entity(tableName = "sync_state", primaryKeys = ["connectionId", "sourceId"])
data class SyncStateEntity(
    val connectionId: String,
    val sourceId: String,
    val cursor: String?,
    /** The window the cursor belongs to, as "<ISO start date>|<zone id>", e.g. "2026-09-22|Europe/London". */
    val rangeStart: String,
)

data class EventRow(
    @Embedded val event: EventEntity,
    val sourcePersonId: String,
)

@Dao
interface CalendarDao {
    @Query("SELECT * FROM connection ORDER BY label, id")
    fun connections(): Flow<List<ConnectionEntity>>

    @Query("SELECT id FROM connection ORDER BY id")
    fun connectionIds(): Flow<List<String>>

    @Query("SELECT * FROM connection ORDER BY label, id")
    suspend fun allConnections(): List<ConnectionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConnection(connection: ConnectionEntity)

    @Query("UPDATE connection SET health = :health, healthMessage = :message WHERE id = :id")
    suspend fun setHealth(id: String, health: String, message: String?)

    @Query("UPDATE connection SET health = 'OK', healthMessage = NULL, lastSyncMillis = :at WHERE id = :id")
    suspend fun markSynced(id: String, at: Long)

    @Query("SELECT * FROM source WHERE connectionId = :connectionId ORDER BY name")
    suspend fun sources(connectionId: String): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Query("DELETE FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun deleteEventsForSource(connectionId: String, sourceId: String)

    @Query("DELETE FROM event WHERE connectionId = :connectionId AND sourceId = :sourceId AND remoteId IN (:ids)")
    suspend fun deleteEvents(connectionId: String, sourceId: String, ids: List<String>)

    @Upsert
    suspend fun upsertEvents(events: List<EventEntity>)

    @Query(
        """
        SELECT e.*, s.personId AS sourcePersonId FROM event e
        JOIN source s ON s.connectionId = e.connectionId AND s.sourceId = e.sourceId
        WHERE s.visible = 1 AND e.startSort < :end AND (e.endSort > :start OR e.startSort >= :start)
        ORDER BY e.startSort, e.title
        """,
    )
    fun eventsBetween(start: Long, end: Long): Flow<List<EventRow>>

    @Query("SELECT * FROM sync_state WHERE connectionId = :connectionId AND sourceId = :sourceId")
    suspend fun syncState(connectionId: String, sourceId: String): SyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: SyncStateEntity)
}

/**
 * Holds user configuration (connections, source mappings), not just a cache: every version bump ships a
 * Migration or AutoMigration, never a destructive fallback. The schema JSON under schemas/ is committed.
 */
@Database(
    entities = [ConnectionEntity::class, SourceEntity::class, EventEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
}
```

- [ ] **Step 3: Write the stored models and a stubbed store**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/Stored.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** Who a source's events belong to when they carry no person tag, and whether the source is shown. */
data class SourceMapping(val person: PersonId, val visible: Boolean) {
    companion object {
        val Default = SourceMapping(PersonId.FAMILY, visible = true)
    }
}

data class StoredConnection(
    val connection: Connection,
    val health: ConnectionHealth,
    val lastSyncMillis: Long?,
)

data class StoredSource(
    val connectionId: String,
    val source: CalendarSource,
    val mapping: SourceMapping,
)

data class StoredEvent(
    val connectionId: String,
    val sourceId: String,
    val remoteId: String,
    val title: String,
    val start: EventTime,
    val end: EventTime,
    val recurring: Boolean,
    val forPerson: String?,
    val createdBy: String?,
    val sourcePerson: PersonId,
    val startSort: Long,
    val endSort: Long,
)
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

@Singleton
class CalendarStore @Inject constructor(private val db: CalendarDatabase) {
    fun connections(): Flow<List<StoredConnection>> = TODO()
    fun connectionIds(): Flow<List<String>> = TODO()
    suspend fun connectionsNow(): List<StoredConnection> = TODO()
    suspend fun addConnection(connection: Connection, sources: List<CalendarSource>, mapping: Map<String, SourceMapping>): Unit = TODO()
    suspend fun visibleSourcesFor(connectionId: String): List<StoredSource> = TODO()
    suspend fun setHealth(connectionId: String, health: ConnectionHealth): Unit = TODO()
    suspend fun markSynced(connectionId: String, atMillis: Long): Unit = TODO()
    suspend fun cursor(connectionId: String, sourceId: String, range: DateRange): SyncCursor? = TODO()
    suspend fun applySync(connectionId: String, sourceId: String, range: DateRange, result: SyncResult): Unit = TODO()
    fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<StoredEvent>> = TODO()
}
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Multibinds
    abstract fun providers(): Set<CalendarProvider>

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): CalendarDatabase =
            Room.databaseBuilder(context, CalendarDatabase::class.java, "calendar.db").build()
    }
}
```

Never add `fallbackToDestructiveMigration()` to this builder: `calendar.db` holds user configuration (Global Constraints).

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/TestDatabases.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase

internal fun calendarDb(): CalendarDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CalendarDatabase::class.java)
        .allowMainThreadQueries()
        .build()
```

- [ ] **Step 4: Write the failing store tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarStoreTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

@RunWith(AndroidJUnit4::class)
class CalendarStoreTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private val zone = ZoneId.of("Europe/London")
    private val window = DateRange(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 10, 8), zone)
    private val conn = Connection("c1", "calendar.test", "Test", mapOf("url" to "https://example.com/a?b=1&c=\"d\""))

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() = db.close()

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDate.of(2026, 9, day).atTime(hour, minute).atZone(zone).toInstant()

    private fun timed(id: String, title: String, day: Int, hour: Int, minutes: Long = 60) =
        RemoteEvent(id, title, EventTime.Timed(at(day, hour)), EventTime.Timed(at(day, hour).plusSeconds(minutes * 60)), recurring = false)

    private fun allDay(id: String, title: String, fromDay: Int, toDayExclusive: Int) = RemoteEvent(
        id, title,
        EventTime.AllDay(LocalDate.of(2026, 9, fromDay)),
        EventTime.AllDay(LocalDate.of(2026, 9, toDayExclusive)),
        recurring = false,
    )

    private fun full(vararg events: RemoteEvent) = SyncResult(events.toList(), emptyList(), SyncCursor("k1"), fullReplace = true)

    private fun millis(day: Int) = LocalDate.of(2026, 9, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private suspend fun connect(vararg sourceIds: String, mapping: Map<String, SourceMapping> = emptyMap()) =
        store.addConnection(conn, sourceIds.map { CalendarSource(it, it.uppercase(), writable = false) }, mapping)

    private suspend fun titlesBetween(fromDay: Int, toDayExclusive: Int) =
        store.eventsBetween(millis(fromDay), millis(toDayExclusive)).first().map { it.title }

    @Test
    fun newConnectionIsOkNeverSyncedAndKeepsItsConfig() = runTest {
        connect("s1")
        val stored = store.connections().first().single()
        assertThat(stored.connection).isEqualTo(conn)
        assertThat(stored.health).isEqualTo(ConnectionHealth.Ok)
        assertThat(stored.lastSyncMillis).isNull()
        assertThat(store.connectionIds().first()).containsExactly("c1")
    }

    @Test
    fun healthRoundTripsIncludingTheErrorMessage() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.Error("quota exceeded"))
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Error("quota exceeded"))
        store.setHealth("c1", ConnectionHealth.NeedsSignIn)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.NeedsSignIn)
        store.setHealth("c1", ConnectionHealth.Unreachable)
        assertThat(store.connectionsNow().single().health).isEqualTo(ConnectionHealth.Unreachable)
    }

    @Test
    fun markSyncedSetsOkAndTheTime() = runTest {
        connect("s1")
        store.setHealth("c1", ConnectionHealth.NeedsSignIn)
        store.markSynced("c1", 1234L)
        val stored = store.connectionsNow().single()
        assertThat(stored.health).isEqualTo(ConnectionHealth.Ok)
        assertThat(stored.lastSyncMillis).isEqualTo(1234L)
    }

    @Test
    fun fullReplaceDropsEventsMissingFromTheNewSet() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "Old", 23, 9), timed("b", "Kept", 23, 10)))
        store.applySync("c1", "s1", window, full(timed("b", "Kept", 23, 10), timed("c", "New", 23, 11)))
        assertThat(titlesBetween(23, 24)).containsExactly("Kept", "New").inOrder()
    }

    @Test
    fun incrementalSyncUpsertsAndRemoves() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "Gone", 23, 9), timed("b", "Before", 23, 10)))
        store.applySync(
            "c1", "s1", window,
            SyncResult(listOf(timed("b", "After", 23, 10), timed("c", "Added", 23, 11)), listOf("a"), SyncCursor("k2"), fullReplace = false),
        )
        assertThat(titlesBetween(23, 24)).containsExactly("After", "Added").inOrder()
    }

    @Test
    fun eventsBetweenReturnsOverlapsInStartOrder() = runTest {
        connect("s1")
        store.applySync(
            "c1", "s1", window,
            full(
                timed("late", "Late", 23, 19),
                timed("early", "Early", 23, 7),
                timed("tomorrow", "Tomorrow", 24, 10),
                allDay("half", "Half term", 21, 25),
            ),
        )
        assertThat(titlesBetween(23, 24)).containsExactly("Half term", "Early", "Late").inOrder()
    }

    @Test
    fun allDayEventIsNotReturnedOnItsExclusiveEndDay() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(allDay("bins", "Bin day", 23, 24)))
        assertThat(titlesBetween(23, 24)).containsExactly("Bin day")
        assertThat(titlesBetween(24, 25)).isEmpty()
    }

    @Test
    fun hiddenSourcesAreExcluded() = runTest {
        connect("s1", "s2", mapping = mapOf("s2" to SourceMapping(PersonId.FAMILY, visible = false)))
        store.applySync("c1", "s1", window, full(timed("a", "Shown", 23, 9)))
        store.applySync("c1", "s2", window, full(timed("b", "Hidden", 23, 10)))
        assertThat(titlesBetween(23, 24)).containsExactly("Shown")
        assertThat(store.visibleSourcesFor("c1").map { it.source.id }).containsExactly("s1")
    }

    @Test
    fun unmappedSourceDefaultsToFamilyAndVisible() = runTest {
        connect("s1", mapping = emptyMap())
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        assertThat(store.visibleSourcesFor("c1").single().mapping).isEqualTo(SourceMapping.Default)
        assertThat(store.eventsBetween(millis(23), millis(24)).first().single().sourcePerson).isEqualTo(PersonId.FAMILY)
    }

    @Test
    fun mappedSourceCarriesItsPerson() = runTest {
        connect("s1", mapping = mapOf("s1" to SourceMapping(PersonId("alex"), visible = true)))
        store.applySync("c1", "s1", window, full(timed("a", "Walk", 23, 9)))
        assertThat(store.eventsBetween(millis(23), millis(24)).first().single().sourcePerson).isEqualTo(PersonId("alex"))
    }

    @Test
    fun removalsBeyondSqlitesVariableLimitAreApplied() = runTest {
        connect("s1")
        val many = (1..1_200).map { timed("e$it", "Event $it", 23, 9) }
        store.applySync("c1", "s1", window, full(*many.toTypedArray(), timed("keep", "Keep", 23, 10)))
        store.applySync(
            "c1", "s1", window,
            SyncResult(emptyList(), many.map { it.remoteId }, SyncCursor("k2"), fullReplace = false),
        )
        assertThat(titlesBetween(23, 24)).containsExactly("Keep")
    }

    @Test
    fun eventTimesRoundTrip() = runTest {
        connect("s1")
        val timedEvent = timed("a", "Walk", 23, 9, minutes = 45)
        val allDayEvent = allDay("b", "Holiday", 23, 26)
        store.applySync("c1", "s1", window, full(timedEvent, allDayEvent))
        val stored = store.eventsBetween(millis(23), millis(24)).first().associateBy { it.remoteId }
        assertThat(stored.getValue("a").start).isEqualTo(timedEvent.start)
        assertThat(stored.getValue("a").end).isEqualTo(timedEvent.end)
        assertThat(stored.getValue("b").start).isEqualTo(allDayEvent.start)
        assertThat(stored.getValue("b").end).isEqualTo(allDayEvent.end)
    }

    @Test
    fun cursorIsStoredPerSource() = runTest {
        connect("s1", "s2")
        store.applySync("c1", "s1", window, full(timed("a", "One", 23, 9)))
        assertThat(store.cursor("c1", "s1", window)).isEqualTo(SyncCursor("k1"))
        assertThat(store.cursor("c1", "s2", window)).isNull()
    }

    @Test
    fun cursorIsDroppedWhenTheWindowMoves() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "One", 23, 9)))
        val nextDay = DateRange(window.start.plusDays(1), window.endExclusive.plusDays(1), zone)
        assertThat(store.cursor("c1", "s1", nextDay)).isNull()
    }

    @Test
    fun cursorIsDroppedWhenTheZoneChanges() = runTest {
        connect("s1")
        store.applySync("c1", "s1", window, full(timed("a", "One", 23, 9)))
        val sameDatesElsewhere = DateRange(window.start, window.endExclusive, ZoneId.of("Pacific/Auckland"))
        assertThat(store.cursor("c1", "s1", sameDatesElsewhere)).isNull()
        assertThat(store.cursor("c1", "s1", window)).isEqualTo(SyncCursor("k1"))
    }
}
```

- [ ] **Step 5: Run to verify they fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarStoreTest*"`
Expected: FAIL. All 15 tests fail with `NotImplementedError`. Room's KSP step must succeed; a Room compile error here means the DAO is wrong, so fix it first.

- [ ] **Step 6: Implement the store**

Replace `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarStore.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.ConnectionEntity
import uk.co.siland.culvery.capability.calendar.db.EventEntity
import uk.co.siland.culvery.capability.calendar.db.EventRow
import uk.co.siland.culvery.capability.calendar.db.SourceEntity
import uk.co.siland.culvery.capability.calendar.db.SyncStateEntity
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** The only writer of calendar.db. UI reads go through CalendarRepository. */
@Singleton
class CalendarStore @Inject constructor(private val db: CalendarDatabase) {
    private val dao = db.calendarDao()

    fun connections(): Flow<List<StoredConnection>> = dao.connections().map { rows -> rows.map { it.toStored() } }

    fun connectionIds(): Flow<List<String>> = dao.connectionIds().distinctUntilChanged()

    suspend fun connectionsNow(): List<StoredConnection> = dao.allConnections().map { it.toStored() }

    /**
     * One transaction, so the sync loop never sees a connection without its sources. A source missing from
     * [mapping] shows as Family. Source editing and pruning come with Plan 4's Connections settings.
     */
    suspend fun addConnection(connection: Connection, sources: List<CalendarSource>, mapping: Map<String, SourceMapping>) =
        db.withTransaction {
            dao.insertConnection(
                ConnectionEntity(
                    id = connection.id,
                    providerId = connection.providerId,
                    label = connection.label,
                    configJson = encodeConfig(connection.config),
                    health = ConnectionHealth.Ok.code(),
                    healthMessage = null,
                    lastSyncMillis = null,
                ),
            )
            dao.insertSources(
                sources.map { s ->
                    val m = mapping[s.id] ?: SourceMapping.Default
                    SourceEntity(connection.id, s.id, s.name, s.writable, m.visible, m.person.value)
                },
            )
        }

    suspend fun visibleSourcesFor(connectionId: String): List<StoredSource> =
        dao.sources(connectionId).filter { it.visible }.map { it.toStored() }

    suspend fun setHealth(connectionId: String, health: ConnectionHealth) =
        dao.setHealth(connectionId, health.code(), (health as? ConnectionHealth.Error)?.message)

    suspend fun markSynced(connectionId: String, atMillis: Long) = dao.markSynced(connectionId, atMillis)

    /**
     * Null when nothing is stored or the cursor belongs to a different window or zone, forcing a full resync.
     * A zone change must resync: all-day events are stored at midnight in the zone they were synced in.
     */
    suspend fun cursor(connectionId: String, sourceId: String, range: DateRange): SyncCursor? =
        dao.syncState(connectionId, sourceId)
            ?.takeIf { it.rangeStart == range.cursorKey() }
            ?.cursor
            ?.let(::SyncCursor)

    suspend fun applySync(connectionId: String, sourceId: String, range: DateRange, result: SyncResult) =
        db.withTransaction {
            if (result.fullReplace) {
                dao.deleteEventsForSource(connectionId, sourceId)
            } else {
                result.removedIds.chunked(REMOVE_CHUNK).forEach { dao.deleteEvents(connectionId, sourceId, it) }
            }
            dao.upsertEvents(result.upserts.map { it.toEntity(connectionId, sourceId, range.zone) })
            dao.upsertSyncState(SyncStateEntity(connectionId, sourceId, result.cursor?.value, range.cursorKey()))
        }

    fun eventsBetween(startMillis: Long, endMillis: Long): Flow<List<StoredEvent>> =
        dao.eventsBetween(startMillis, endMillis).map { rows -> rows.map { it.toStored() } }

    private companion object {
        // SQLite allows 999 bound variables per statement on older Android versions.
        const val REMOVE_CHUNK = 500
    }
}

private fun DateRange.cursorKey(): String = "$start|${zone.id}"

internal fun ConnectionHealth.code(): String = when (this) {
    ConnectionHealth.Ok -> "OK"
    ConnectionHealth.Unreachable -> "UNREACHABLE"
    ConnectionHealth.NeedsSignIn -> "NEEDS_SIGN_IN"
    is ConnectionHealth.Error -> "ERROR"
}

internal fun healthOf(code: String, message: String?): ConnectionHealth = when (code) {
    "UNREACHABLE" -> ConnectionHealth.Unreachable
    "NEEDS_SIGN_IN" -> ConnectionHealth.NeedsSignIn
    "ERROR" -> ConnectionHealth.Error(message ?: "Unknown error")
    else -> ConnectionHealth.Ok
}

private fun encodeConfig(config: Map<String, String>): String = JSONObject(config).toString()

private fun decodeConfig(json: String): Map<String, String> {
    val o = JSONObject(json)
    return o.keys().asSequence().associateWith { o.getString(it) }
}

private fun ConnectionEntity.toStored() = StoredConnection(
    Connection(id, providerId, label, decodeConfig(configJson)),
    healthOf(health, healthMessage),
    lastSyncMillis,
)

private fun SourceEntity.toStored() = StoredSource(
    connectionId,
    CalendarSource(sourceId, name, writable),
    SourceMapping(PersonId(personId), visible),
)

private fun RemoteEvent.toEntity(connectionId: String, sourceId: String, zone: ZoneId) = EventEntity(
    connectionId = connectionId,
    sourceId = sourceId,
    remoteId = remoteId,
    title = title,
    startInstant = (start as? EventTime.Timed)?.instant?.toEpochMilli(),
    startDate = (start as? EventTime.AllDay)?.date?.toString(),
    endInstant = (end as? EventTime.Timed)?.instant?.toEpochMilli(),
    endDate = (end as? EventTime.AllDay)?.date?.toString(),
    recurring = recurring,
    forPerson = forPerson,
    createdBy = createdBy,
    startSort = start.instantIn(zone).toEpochMilli(),
    endSort = end.instantIn(zone).toEpochMilli(),
)

private fun timeOf(instant: Long?, date: String?): EventTime =
    if (instant != null) EventTime.Timed(Instant.ofEpochMilli(instant)) else EventTime.AllDay(LocalDate.parse(requireNotNull(date)))

private fun EventRow.toStored() = StoredEvent(
    connectionId = event.connectionId,
    sourceId = event.sourceId,
    remoteId = event.remoteId,
    title = event.title,
    start = timeOf(event.startInstant, event.startDate),
    end = timeOf(event.endInstant, event.endDate),
    recurring = event.recurring,
    forPerson = event.forPerson,
    createdBy = event.createdBy,
    sourcePerson = PersonId(sourcePersonId),
    startSort = event.startSort,
    endSort = event.endSort,
)
```

`androidx.room.withTransaction` is what `HouseholdRepository` already uses. If Room 2.8.0 marks it deprecated, stop and ask.

- [ ] **Step 7: Run to verify they pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS: `CalendarStoreTest` (15 tests) and `ContractTypesTest` (8 tests). `capability/calendar/schemas/uk.co.siland.culvery.capability.calendar.db.CalendarDatabase/1.json` now exists (the `culvery.room` convention sets `room.schemaLocation`). Commit it: it is the baseline every later migration is written against.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar
git commit -m "Add calendar.db and CalendarStore"
```

---

### Task 9: Sync engine and the 5-minute loop

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/HouseholdZone.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoop.kt`
- Modify: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/TestDatabases.kt`
- Create: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedProvider.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/HouseholdZoneTest.kt`, `CalendarSyncTest.kt`, `CalendarSyncLoopTest.kt`

**Interfaces:**
- Consumes:
  - `CalendarStore` (Task 8)
  - `HouseholdRepository`, with `val location: Flow<HomeLocation?>` and `suspend fun setLocation(HomeLocation)`
  - `HomeLocation(name, latitude, longitude, timeZoneId)`
  - `WallClock { nowMillis() }`
  - `@ApplicationScope CoroutineScope`
  - `Startable`
- Produces:
  - `@Singleton class HouseholdZone @Inject constructor(household: HouseholdRepository)`:
    - `val zone: Flow<ZoneId>`. It uses the household's `timeZoneId`, and the system zone if that is missing or invalid.
    - `suspend fun current(): ZoneId`
  - `const val SYNC_PAST_DAYS = 1L`, `const val SYNC_FUTURE_DAYS = 14L`, `const val PROVIDER_TIMEOUT_MS = 60_000L`
  - `@Singleton class CalendarSync` with `suspend fun syncAll()`:
    - Constructor for Hilt: `@Inject constructor(store: CalendarStore, providers: Set<@JvmSuppressWildcards CalendarProvider>, zone: HouseholdZone, clock: WallClock)`, which uses `Dispatchers.IO` and `PROVIDER_TIMEOUT_MS`.
    - Constructor for tests: `internal constructor(store, providers, zone, clock, io: CoroutineContext, timeoutMillis: Long)`. Tests pass `EmptyCoroutineContext`, so provider calls and timeouts run on the test's virtual time.
    - It syncs every connection's **visible** sources for the window today−1 up to (but not including) today+15, in the household zone.
    - It handles each connection independently, and each source within a connection independently.
    - Every provider call runs as `withContext(io) { withTimeout(timeoutMillis) { … } }`.
    - Per source: success → OK; `NeedsSignInException` → NeedsSignIn; `UnreachableException` → Unreachable; `TimeoutCancellationException` → Unreachable ("timed out", logged); any other `CancellationException` → `currentCoroutineContext().ensureActive()` (rethrows if the sync itself was cancelled), otherwise Unreachable; any other exception → `Error(message)`.
    - Per connection: health is the worst across its sources (NeedsSignIn > Unreachable > Error > Ok). `markSynced` only if every source succeeded; otherwise `setHealth(worst)`.
    - No provider → `Error("Provider not installed")`.
    - The cache is always kept.
  - `const val SYNC_INTERVAL_MS = 300_000L`
  - `class CalendarSyncLoop : Startable`:
    - Constructor for Hilt: `@Inject constructor(sync: CalendarSync, store: CalendarStore, @ApplicationScope scope: CoroutineScope)`.
    - Constructor for tests: `internal constructor(syncAll: suspend () -> Unit, connectionIds: Flow<List<String>>, scope: CoroutineScope, intervalMillis: Long = SYNC_INTERVAL_MS)`.
    - It syncs on start, whenever the connection ids change, and every interval after the last sync. A failing sync does not stop the loop, and neither does a stray `CancellationException` (the loop checks `ensureActive()` and only stops when it is really cancelled).
  - Test helpers:
    - `householdDb(): HouseholdDatabase`
    - `ScriptedProvider(id: String, sourceList: List<CalendarSource> = emptyList())`, with `events`, `failWith`, `failFor: Map<String, Throwable>` (by source id), `hang: Boolean` and `calls: List<SyncCall>`
    - `data class SyncCall(connectionId, sourceId, range, cursor)`
    - `suspend fun timeoutCancellation(): TimeoutCancellationException`, a real one (its constructor is internal)

- [ ] **Step 1: Add the test helpers**

Replace `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/TestDatabases.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

internal fun calendarDb(): CalendarDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CalendarDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal fun householdDb(): HouseholdDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
        .allowMainThreadQueries()
        .build()
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ScriptedProvider.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.compose.runtime.Composable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

internal data class SyncCall(val connectionId: String, val sourceId: String, val range: DateRange, val cursor: SyncCursor?)

/** An in-test provider (the capability may not depend on :provider:calendar-fake, even in tests). */
internal class ScriptedProvider(
    id: String,
    var sourceList: List<CalendarSource> = emptyList(),
) : CalendarProvider {
    override val descriptor = ProviderDescriptor(id, id, "event", setOf(Feature.READ))
    val calls = mutableListOf<SyncCall>()
    var failWith: Throwable? = null
    /** Per-source failures, by source id; they win over [failWith]. */
    var failFor: Map<String, Throwable> = emptyMap()
    /** Never returns, like a stalled socket. */
    var hang = false
    var events: (CalendarSource) -> List<RemoteEvent> = { emptyList() }

    @Composable
    override fun ConnectScreen(existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit) {
    }

    override suspend fun sources(conn: Connection): List<CalendarSource> = sourceList

    override suspend fun sync(conn: Connection, source: CalendarSource, range: DateRange, cursor: SyncCursor?): SyncResult {
        calls += SyncCall(conn.id, source.id, range, cursor)
        if (hang) awaitCancellation()
        (failFor[source.id] ?: failWith)?.let { throw it }
        return SyncResult(events(source), emptyList(), SyncCursor("k${calls.size}"), fullReplace = cursor == null)
    }
}

/** A real TimeoutCancellationException, as a provider's own internal withTimeout would throw. */
internal suspend fun timeoutCancellation(): TimeoutCancellationException =
    runCatching { withTimeout(1) { awaitCancellation() } }.exceptionOrNull() as TimeoutCancellationException
```

- [ ] **Step 2: Stub the production classes**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/HouseholdZone.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.household.HouseholdRepository

@Singleton
class HouseholdZone @Inject constructor(household: HouseholdRepository) {
    val zone: Flow<ZoneId> = TODO()

    suspend fun current(): ZoneId = TODO()
}
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSync.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import uk.co.siland.culvery.core.plugin.WallClock

const val SYNC_PAST_DAYS = 1L
const val SYNC_FUTURE_DAYS = 14L
const val PROVIDER_TIMEOUT_MS = 60_000L

@Singleton
class CalendarSync internal constructor(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val zone: HouseholdZone,
    private val clock: WallClock,
    private val io: CoroutineContext,
    private val timeoutMillis: Long,
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        zone: HouseholdZone,
        clock: WallClock,
    ) : this(store, providers, zone, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS)

    suspend fun syncAll(): Unit = TODO()
}
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoop.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

const val SYNC_INTERVAL_MS = 5 * 60_000L

class CalendarSyncLoop internal constructor(
    private val syncAll: suspend () -> Unit,
    private val connectionIds: Flow<List<String>>,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SYNC_INTERVAL_MS,
) : Startable {
    @Inject
    constructor(sync: CalendarSync, store: CalendarStore, @ApplicationScope scope: CoroutineScope) :
        this(sync::syncAll, store.connectionIds(), scope)

    override fun start(): Unit = TODO()
}
```

- [ ] **Step 3: Write the failing tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/HouseholdZoneTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

@RunWith(AndroidJUnit4::class)
class HouseholdZoneTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var zone: HouseholdZone

    @Before
    fun setUp() {
        db = householdDb()
        household = HouseholdRepository(db)
        zone = HouseholdZone(household)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun noLocationUsesTheSystemZone() = runTest {
        assertThat(zone.current()).isEqualTo(ZoneId.systemDefault())
    }

    @Test
    fun usesTheHouseholdZone() = runTest {
        household.setLocation(HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland"))
        assertThat(zone.current()).isEqualTo(ZoneId.of("Pacific/Auckland"))
    }

    @Test
    fun invalidZoneFallsBackToTheSystemZone() = runTest {
        household.setLocation(HomeLocation("Nowhere", 0.0, 0.0, "Not/AZone"))
        assertThat(zone.current()).isEqualTo(ZoneId.systemDefault())
    }
}
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room and for android.util.Log (the engine logs timeouts).
@RunWith(AndroidJUnit4::class)
class CalendarSyncTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository

    private val london = ZoneId.of("Europe/London")
    private var now = Instant.parse("2026-09-23T11:00:00Z")
    private val clock = WallClock { now.toEpochMilli() }
    private val s1 = CalendarSource("s1", "One", writable = false)
    private val s2 = CalendarSource("s2", "Two", writable = false)
    private val a = ScriptedProvider("calendar.a")
    private val b = ScriptedProvider("calendar.b")

    @Before
    fun setUp() {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    /** Provider calls stay on the test dispatcher, so the 1 s timeout runs on virtual time. */
    private suspend fun engine(): CalendarSync {
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        return CalendarSync(store, setOf(a, b), HouseholdZone(household), clock, EmptyCoroutineContext, timeoutMillis = 1_000)
    }

    private suspend fun connect(id: String, providerId: String, vararg sources: CalendarSource, mapping: Map<String, SourceMapping> = emptyMap()) =
        store.addConnection(Connection(id, providerId, id.uppercase(), emptyMap()), sources.toList(), mapping)

    private fun swim() = RemoteEvent(
        "swim", "Swim",
        EventTime.Timed(Instant.parse("2026-09-23T15:00:00Z")),
        EventTime.Timed(Instant.parse("2026-09-23T16:00:00Z")),
        recurring = false,
    )

    private suspend fun cachedTitles() =
        store.eventsBetween(Instant.parse("2026-09-23T00:00:00Z").toEpochMilli(), Instant.parse("2026-09-24T00:00:00Z").toEpochMilli())
            .first().map { it.title }

    private suspend fun health(id: String) = store.connectionsNow().single { it.connection.id == id }.health

    @Test
    fun syncStoresEventsAndMarksTheConnectionOk() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        engine().syncAll()
        assertThat(cachedTitles()).containsExactly("Swim")
        val stored = store.connectionsNow().single()
        assertThat(stored.health).isEqualTo(ConnectionHealth.Ok)
        assertThat(stored.lastSyncMillis).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun windowIsYesterdayToTwoWeeksAheadInTheHouseholdZone() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        household.setLocation(HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland"))
        now = Instant.parse("2026-09-23T13:00:00Z") // already 24 September in Auckland
        sync.syncAll()
        val auckland = ZoneId.of("Pacific/Auckland")
        assertThat(a.calls.single().range).isEqualTo(DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 9), auckland))
    }

    @Test
    fun returnedCursorIsPassedToTheNextSync() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        sync.syncAll()
        sync.syncAll()
        assertThat(a.calls.map { it.cursor }).containsExactly(null, SyncCursor("k1")).inOrder()
    }

    @Test
    fun aNewDayResyncsFromScratch() = runTest {
        connect("c1", "calendar.a", s1)
        val sync = engine()
        sync.syncAll()
        now = Instant.parse("2026-09-24T11:00:00Z")
        sync.syncAll()
        assertThat(a.calls[1].cursor).isNull()
        assertThat(a.calls[1].range).isEqualTo(DateRange(LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 9), london))
    }

    @Test
    fun needsSignInKeepsCachedEventsAndFlagsConnection() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = engine()
        sync.syncAll()
        a.failWith = NeedsSignInException("token expired")
        now = now.plusSeconds(300)
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
        assertThat(cachedTitles()).containsExactly("Swim")
        assertThat(store.connectionsNow().single().lastSyncMillis).isEqualTo(Instant.parse("2026-09-23T11:00:00Z").toEpochMilli())
    }

    @Test
    fun unreachableKeepsCachedEvents() = runTest {
        connect("c1", "calendar.a", s1)
        a.events = { listOf(swim()) }
        val sync = engine()
        sync.syncAll()
        a.failWith = UnreachableException("no network")
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun unexpectedExceptionBecomesErrorWithItsMessage() = runTest {
        connect("c1", "calendar.a", s1)
        a.failWith = IllegalStateException("quota exceeded")
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Error("quota exceeded"))
    }

    @Test
    fun missingProviderMarksTheConnectionError() = runTest {
        connect("c1", "calendar.gone", s1)
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Error("Provider not installed"))
    }

    @Test
    fun oneFailingConnectionDoesNotStopTheOthers() = runTest {
        connect("c1", "calendar.a", s1)
        connect("c2", "calendar.b", s1)
        a.failWith = UnreachableException()
        b.events = { listOf(swim()) }
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(health("c2")).isEqualTo(ConnectionHealth.Ok)
        assertThat(cachedTitles()).containsExactly("Swim")
    }

    @Test
    fun hiddenSourcesAreNotSynced() = runTest {
        connect("c1", "calendar.a", s1, s2, mapping = mapOf("s2" to SourceMapping(PersonId.FAMILY, visible = false)))
        engine().syncAll()
        assertThat(a.calls.map { it.sourceId }).containsExactly("s1")
    }

    @Test
    fun oneFailingSourceDoesNotBlockItsSiblingsButHoldsBackMarkSynced() = runTest {
        connect("c1", "calendar.a", s1, s2)
        a.events = { listOf(swim()) }
        a.failFor = mapOf("s1" to UnreachableException("s1 down"))
        engine().syncAll()
        assertThat(a.calls.map { it.sourceId }).containsExactly("s1", "s2")
        assertThat(cachedTitles()).containsExactly("Swim")
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(store.connectionsNow().single().lastSyncMillis).isNull()
    }

    @Test
    fun connectionHealthIsTheWorstAcrossItsSources() = runTest {
        connect("c1", "calendar.a", s1, s2)
        a.failFor = mapOf("s1" to IllegalStateException("quota exceeded"), "s2" to NeedsSignInException("expired"))
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.NeedsSignIn)
    }

    @Test
    fun hangingProviderTimesOutAsUnreachableAndTheNextSyncStillRuns() = runTest {
        connect("c1", "calendar.a", s1)
        connect("c2", "calendar.b", s1)
        a.hang = true
        b.events = { listOf(swim()) }
        val sync = engine()
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        assertThat(health("c2")).isEqualTo(ConnectionHealth.Ok)
        a.hang = false
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
    }

    @Test
    fun providerTimeoutCancellationBecomesUnreachable() = runTest {
        connect("c1", "calendar.a", s1)
        a.failWith = timeoutCancellation()
        val sync = engine()
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
        a.failWith = null
        sync.syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Ok)
    }

    @Test
    fun strayCancellationFromAProviderBecomesUnreachable() = runTest {
        connect("c1", "calendar.a", s1)
        a.failWith = CancellationException("provider cancelled its own job")
        engine().syncAll()
        assertThat(health("c1")).isEqualTo(ConnectionHealth.Unreachable)
    }
}
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSyncLoopTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith

// Robolectric only because the loop logs through android.util.Log when a sync throws.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarSyncLoopTest {
    @Test
    fun syncsOnStartAndEveryFiveMinutes() = runTest {
        var count = 0
        CalendarSyncLoop({ count++ }, MutableStateFlow(listOf("c1")), backgroundScope).start()
        runCurrent()
        assertThat(count).isEqualTo(1)
        advanceTimeBy(SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
        advanceTimeBy(SYNC_INTERVAL_MS - 1)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun syncsAgainWhenAConnectionIsAdded() = runTest {
        var count = 0
        val ids = MutableStateFlow(emptyList<String>())
        CalendarSyncLoop({ count++ }, ids, backgroundScope).start()
        runCurrent()
        assertThat(count).isEqualTo(1)
        ids.value = listOf("c1")
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aFailingSyncDoesNotStopTheLoop() = runTest {
        var count = 0
        CalendarSyncLoop({ count++; if (count == 1) error("database locked") }, MutableStateFlow(listOf("c1")), backgroundScope).start()
        runCurrent()
        advanceTimeBy(SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aSyncThatTimesOutInternallyDoesNotStopTheLoop() = runTest {
        var count = 0
        val syncAll: suspend () -> Unit = {
            count++
            if (count == 1) withTimeout(1_000) { awaitCancellation() }
        }
        CalendarSyncLoop(syncAll, MutableStateFlow(listOf("c1")), backgroundScope).start()
        runCurrent()
        // The first sync times out at 1 s; the next one is due an interval after that.
        advanceTimeBy(1_000 + SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun aStrayCancellationDoesNotStopTheLoop() = runTest {
        var count = 0
        val syncAll: suspend () -> Unit = {
            count++
            if (count == 1) throw CancellationException("stray")
        }
        CalendarSyncLoop(syncAll, MutableStateFlow(listOf("c1")), backgroundScope).start()
        runCurrent()
        advanceTimeBy(SYNC_INTERVAL_MS)
        runCurrent()
        assertThat(count).isEqualTo(2)
    }
}
```

- [ ] **Step 4: Run to verify they fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: FAIL. The `HouseholdZoneTest`, `CalendarSyncTest` and `CalendarSyncLoopTest` tests fail with `NotImplementedError`. The earlier tests still pass.

- [ ] **Step 5: Implement**

Replace `HouseholdZone.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.DateTimeException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository

/** The household's time zone; the device zone until setup has set a location, or if the stored id is invalid. */
@Singleton
class HouseholdZone @Inject constructor(household: HouseholdRepository) {
    val zone: Flow<ZoneId> = household.location
        .map { location -> location?.timeZoneId?.let(::parse) ?: ZoneId.systemDefault() }
        .distinctUntilChanged()

    suspend fun current(): ZoneId = zone.first()

    private fun parse(id: String): ZoneId? =
        try {
            ZoneId.of(id)
        } catch (e: DateTimeException) {
            null
        }
}
```

Replace `CalendarSync.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth
import uk.co.siland.culvery.core.plugin.WallClock

const val SYNC_PAST_DAYS = 1L
const val SYNC_FUTURE_DAYS = 14L
const val PROVIDER_TIMEOUT_MS = 60_000L

@Singleton
class CalendarSync internal constructor(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
    private val zone: HouseholdZone,
    private val clock: WallClock,
    private val io: CoroutineContext,
    private val timeoutMillis: Long,
) {
    @Inject
    constructor(
        store: CalendarStore,
        providers: Set<@JvmSuppressWildcards CalendarProvider>,
        zone: HouseholdZone,
        clock: WallClock,
    ) : this(store, providers, zone, clock, Dispatchers.IO, PROVIDER_TIMEOUT_MS)

    /**
     * Syncs each connection, and each source within it, independently. A failure only flags that connection
     * (with its worst source's health) and never clears its cache.
     */
    suspend fun syncAll() {
        val window = currentWindow()
        store.connectionsNow().forEach { sync(it.connection, window) }
    }

    private suspend fun currentWindow(): DateRange {
        val z = zone.current()
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(z).toLocalDate()
        return DateRange(today.minusDays(SYNC_PAST_DAYS), today.plusDays(SYNC_FUTURE_DAYS + 1), z)
    }

    private suspend fun sync(conn: Connection, window: DateRange) {
        val provider = providers.firstOrNull { it.descriptor.id == conn.providerId }
        if (provider == null) {
            store.setHealth(conn.id, ConnectionHealth.Error("Provider not installed"))
            return
        }
        var worst: ConnectionHealth = ConnectionHealth.Ok
        for (stored in store.visibleSourcesFor(conn.id)) {
            val health = syncSource(provider, conn, stored.source, window)
            if (health.severity() > worst.severity()) worst = health
        }
        if (worst == ConnectionHealth.Ok) {
            store.markSynced(conn.id, clock.nowMillis())
        } else {
            store.setHealth(conn.id, worst)
        }
    }

    private suspend fun syncSource(
        provider: CalendarProvider,
        conn: Connection,
        source: CalendarSource,
        window: DateRange,
    ): ConnectionHealth =
        try {
            val cursor = store.cursor(conn.id, source.id, window)
            val result = callProvider { provider.sync(conn, source, window, cursor) }
            store.applySync(conn.id, source.id, window, result)
            ConnectionHealth.Ok
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "${conn.label} / ${source.name}: timed out", e)
            ConnectionHealth.Unreachable
        } catch (e: CancellationException) {
            // Rethrows if this sync was really cancelled; otherwise the provider leaked a stray cancellation.
            currentCoroutineContext().ensureActive()
            ConnectionHealth.Unreachable
        } catch (e: NeedsSignInException) {
            ConnectionHealth.NeedsSignIn
        } catch (e: UnreachableException) {
            ConnectionHealth.Unreachable
        } catch (e: Exception) {
            ConnectionHealth.Error(e.message ?: e.javaClass.simpleName)
        }

    /** Every provider call (sync now, sources() when Plan 4 refreshes them) goes through here. */
    private suspend fun <T> callProvider(block: suspend () -> T): T =
        withContext(io) { withTimeout(timeoutMillis) { block() } }

    private fun ConnectionHealth.severity(): Int = when (this) {
        ConnectionHealth.NeedsSignIn -> 3
        ConnectionHealth.Unreachable -> 2
        is ConnectionHealth.Error -> 1
        ConnectionHealth.Ok -> 0
    }

    private companion object {
        const val TAG = "CalendarSync"
    }
}
```

Replace `CalendarSyncLoop.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import android.util.Log
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable

const val SYNC_INTERVAL_MS = 5 * 60_000L

/** Syncs on start, whenever a connection is added or removed, and then every [intervalMillis]. */
class CalendarSyncLoop internal constructor(
    private val syncAll: suspend () -> Unit,
    private val connectionIds: Flow<List<String>>,
    private val scope: CoroutineScope,
    private val intervalMillis: Long = SYNC_INTERVAL_MS,
) : Startable {
    @Inject
    constructor(sync: CalendarSync, store: CalendarStore, @ApplicationScope scope: CoroutineScope) :
        this(sync::syncAll, store.connectionIds(), scope)

    override fun start() {
        scope.launch {
            connectionIds.distinctUntilChanged().collectLatest {
                while (true) {
                    try {
                        syncAll()
                    } catch (e: CancellationException) {
                        // Stops the loop only if it was really cancelled; a stray one (an internal timeout) must not.
                        currentCoroutineContext().ensureActive()
                        Log.w(TAG, "Calendar sync was cancelled internally", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "Calendar sync failed", e)
                    }
                    delay(intervalMillis)
                }
            }
        }
    }

    private companion object {
        const val TAG = "CalendarSync"
    }
}
```

- [ ] **Step 6: Run to verify they pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS:
- `HouseholdZoneTest` (3 tests)
- `CalendarSyncTest` (15 tests)
- `CalendarSyncLoopTest` (5 tests)
- all earlier tests

`withContext`, `withTimeout`, `currentCoroutineContext` and `CoroutineContext.ensureActive` are stable coroutine APIs. If any shows a deprecation warning, stop and ask.

- [ ] **Step 7: Commit**

```bash
git add capability/calendar
git commit -m "Add the calendar sync engine and its 5-minute loop"
```

---

### Task 10: `CalendarRepository`, UI models and person resolution

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SyncLabelsTest.kt`, `PersonResolutionTest.kt`, `CalendarRepositoryTest.kt`

**Interfaces:**
- Consumes:
  - `CalendarStore`: `connections()`, `connectionIds()`, `eventsBetween()`
  - `StoredEvent`
  - `HouseholdZone.zone`
  - `HouseholdRepository`: `people` and `peopleWithFamily` (`Flow<List<Person>>`)
  - `Person(id: PersonId, name: String, color: Long)`, `Person.Family`
- Produces (package `uk.co.siland.culvery.capability.calendar`):
  - `data class EventUi(val key: String, val title: String, val timeLabel: String, val startLabel: String, val person: Person, val allDay: Boolean, val recurring: Boolean, val startSort: Long)`. Labels are per day (computed when the repository slices events into days):
    - one-day timed event: `timeLabel` `"07:45–08:30"` (en dash), `startLabel` `"07:45"`;
    - all-day event: both `"All day"`, `allDay = true`;
    - multi-day timed event: first day `"22:00–"`, middle days `"All day"` (with `allDay = true`, so they sort with the all-day events), last day `"until 01:00"`; `startLabel` equals `timeLabel` on each of these days. An end at exactly midnight ends on the day before.
  - `data class DayUi(val date: LocalDate, val events: List<EventUi>)`. All-day events come first, then by start, then by title.
  - `data class WeekUi(val start: LocalDate, val days: List<DayUi>, val people: List<Person>)`. `people` holds the household in order, then Family.
  - `data class SyncStatusUi(val lastSyncMillis: Long?, val needsSignIn: List<String>, val connectionLabels: List<String>, val failingBeforeFirstSync: Boolean)`:
    - `lastSyncMillis` is the oldest last-successful sync among connections that have synced;
    - `needsSignIn` and `connectionLabels` hold connection labels, in the store's order (label, then id);
    - `failingBeforeFirstSync` is true when some connection has never synced and its health is not Ok.
  - `fun SyncStatusUi.isStaleAt(nowMillis: Long): Boolean` = `failingBeforeFirstSync || isStale(lastSyncMillis, nowMillis)`
  - `const val ALL_DAY_LABEL = "All day"`, `const val STALE_AFTER_MS = 1_800_000L`
  - `fun syncedLabel(lastSyncMillis: Long?, nowMillis: Long, connectionLabel: String? = null): String`. It returns `"not synced yet"`, or `"synced"` (plus `" with {connectionLabel}"` when given) followed by one of:
    - `"just now"` under 1 min
    - `"{n} min ago"` under 1 h
    - `"{n} h ago"` under 48 h
    - `"{n} days ago"` from 48 h
  - `fun weekSubtitle(sync: SyncStatusUi, nowMillis: Long): String` = `"Family calendar · " + syncedLabel(…, connectionLabels.singleOrNull())`, so it names the connection only when there is exactly one.
  - `fun isStale(lastSyncMillis: Long?, nowMillis: Long): Boolean`. It is true only when a sync time exists and is more than 30 min old.
  - `fun resolvePerson(forPerson: String?, sourcePerson: PersonId, people: Map<PersonId, Person>): Person`. `people` must include Family.
  - `@Singleton class CalendarRepository @Inject constructor(store: CalendarStore, household: HouseholdRepository, zone: HouseholdZone)`:
    - `val hasConnections: Flow<Boolean>`
    - `val syncStatus: Flow<SyncStatusUi>`
    - `fun day(date: LocalDate): Flow<List<EventUi>>`
    - `fun days(start: LocalDate, count: Int): Flow<List<DayUi>>`
    - `fun week(start: LocalDate): Flow<WeekUi>`
  - A multi-day event appears on every day it covers.

- [ ] **Step 1: Write the UI models with the logic stubbed**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarUi.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.ZoneId
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

const val ALL_DAY_LABEL = "All day"
const val STALE_AFTER_MS = 30 * 60_000L

data class EventUi(
    /** Stable across days and syncs: connectionId/sourceId/remoteId. */
    val key: String,
    val title: String,
    /** This day's slice: "07:45–08:30", "22:00–" (first day of several), "until 01:00" (last day), or "All day". */
    val timeLabel: String,
    /** The week chip's label: "07:45" for a one-day event, otherwise the same as [timeLabel]. */
    val startLabel: String,
    val person: Person,
    /** True for all-day events and for the middle days of a multi-day timed event; they sort first. */
    val allDay: Boolean,
    val recurring: Boolean,
    val startSort: Long,
)

data class DayUi(val date: LocalDate, val events: List<EventUi>)

/** [people] is the legend: household members in order, then Family. */
data class WeekUi(val start: LocalDate, val days: List<DayUi>, val people: List<Person>)

/**
 * [lastSyncMillis] is the oldest successful sync across connections, so one failing calendar can't hide.
 * [connectionLabels] names every connection. [failingBeforeFirstSync] is true when a connection has never
 * synced and is not Ok, which makes the status stale however recent the others are.
 */
data class SyncStatusUi(
    val lastSyncMillis: Long?,
    val needsSignIn: List<String>,
    val connectionLabels: List<String>,
    val failingBeforeFirstSync: Boolean,
)

fun syncedLabel(lastSyncMillis: Long?, nowMillis: Long, connectionLabel: String? = null): String = TODO()

fun isStale(lastSyncMillis: Long?, nowMillis: Long): Boolean = TODO()

fun SyncStatusUi.isStaleAt(nowMillis: Long): Boolean = TODO()

fun weekSubtitle(sync: SyncStatusUi, nowMillis: Long): String = TODO()

/** A tag naming a real person (or Family) wins; otherwise the source's person; otherwise Family. */
fun resolvePerson(forPerson: String?, sourcePerson: PersonId, people: Map<PersonId, Person>): Person = TODO()

internal fun StoredEvent.toUi(date: LocalDate, zone: ZoneId, people: Map<PersonId, Person>): EventUi = TODO()
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarRepository.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.household.HouseholdRepository

@Singleton
class CalendarRepository @Inject constructor(
    private val store: CalendarStore,
    private val household: HouseholdRepository,
    private val zone: HouseholdZone,
) {
    val hasConnections: Flow<Boolean> get() = TODO()
    val syncStatus: Flow<SyncStatusUi> get() = TODO()
    fun day(date: LocalDate): Flow<List<EventUi>> = TODO()
    fun days(start: LocalDate, count: Int): Flow<List<DayUi>> = TODO()
    fun week(start: LocalDate): Flow<WeekUi> = TODO()
}
```

- [ ] **Step 2: Write the failing tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/SyncLabelsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncLabelsTest {
    private val now = 1_000_000_000_000L
    private fun minutesAgo(m: Long) = now - m * 60_000

    @Test
    fun underAMinuteIsJustNow() = assertThat(syncedLabel(now - 59_000, now)).isEqualTo("synced just now")

    @Test
    fun minutesUnderAnHour() {
        assertThat(syncedLabel(minutesAgo(1), now)).isEqualTo("synced 1 min ago")
        assertThat(syncedLabel(minutesAgo(59), now)).isEqualTo("synced 59 min ago")
    }

    @Test
    fun hoursUnderFortyEightThenDays() {
        assertThat(syncedLabel(minutesAgo(60), now)).isEqualTo("synced 1 h ago")
        assertThat(syncedLabel(minutesAgo(150), now)).isEqualTo("synced 2 h ago")
        assertThat(syncedLabel(minutesAgo(48 * 60 - 1), now)).isEqualTo("synced 47 h ago")
        assertThat(syncedLabel(minutesAgo(48 * 60), now)).isEqualTo("synced 2 days ago")
    }

    @Test
    fun neverSyncedSaysSo() = assertThat(syncedLabel(null, now)).isEqualTo("not synced yet")

    @Test
    fun syncTimeInTheFutureReadsJustNow() = assertThat(syncedLabel(now + 3_600_000, now)).isEqualTo("synced just now")

    @Test
    fun namesTheConnectionWhenGivenOne() {
        assertThat(syncedLabel(minutesAgo(2), now, "Google")).isEqualTo("synced with Google 2 min ago")
        assertThat(syncedLabel(now, now, "Google")).isEqualTo("synced with Google just now")
    }

    @Test
    fun subtitleNamesTheConnectionOnlyWhenThereIsExactlyOne() {
        val one = SyncStatusUi(minutesAgo(2), emptyList(), listOf("Google"), failingBeforeFirstSync = false)
        assertThat(weekSubtitle(one, now)).isEqualTo("Family calendar · synced with Google 2 min ago")
        val two = one.copy(connectionLabels = listOf("Google", "School"))
        assertThat(weekSubtitle(two, now)).isEqualTo("Family calendar · synced 2 min ago")
    }

    @Test
    fun staleOnlyAfterThirtyMinutes() {
        assertThat(isStale(minutesAgo(30), now)).isFalse()
        assertThat(isStale(minutesAgo(30) - 1, now)).isTrue()
    }

    @Test
    fun neverSyncedIsNotStale() = assertThat(isStale(null, now)).isFalse()

    @Test
    fun aConnectionFailingBeforeItsFirstSyncMakesTheStatusStale() {
        val status = SyncStatusUi(minutesAgo(1), emptyList(), listOf("Google", "School"), failingBeforeFirstSync = true)
        assertThat(status.isStaleAt(now)).isTrue()
        assertThat(status.copy(failingBeforeFirstSync = false).isStaleAt(now)).isFalse()
    }
}
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/PersonResolutionTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

class PersonResolutionTest {
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val people = listOf(Person.Family, alex, sam).associateBy { it.id }

    @Test
    fun taggedPersonWins() {
        assertThat(resolvePerson("sam", alex.id, people)).isEqualTo(sam)
    }

    @Test
    fun taggedFamilyWinsOverTheSourceMapping() {
        assertThat(resolvePerson("family", alex.id, people)).isEqualTo(Person.Family)
    }

    @Test
    fun unknownTagFallsBackToSourceMapping() {
        assertThat(resolvePerson("someone-removed", alex.id, people)).isEqualTo(alex)
    }

    @Test
    fun untaggedUsesSourceMapping() {
        assertThat(resolvePerson(null, sam.id, people)).isEqualTo(sam)
    }

    @Test
    fun sourceMappedToRemovedPersonShowsFamily() {
        assertThat(resolvePerson(null, PersonId("removed"), people)).isEqualTo(Person.Family)
    }
}
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarRepositoryTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.ConnectionHealth

@RunWith(AndroidJUnit4::class)
class CalendarRepositoryTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var household: HouseholdRepository
    private lateinit var repo: CalendarRepository
    private lateinit var alex: Person
    private lateinit var sam: Person

    private val london = ZoneId.of("Europe/London")
    private val window = DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 15), london)
    private fun sept(day: Int) = LocalDate.of(2026, 9, day)

    @Before
    fun setUp() = runTest {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        household = HouseholdRepository(householdDb)
        repo = CalendarRepository(store, household, HouseholdZone(household))
        household.setLocation(HomeLocation("London", 51.5, -0.12, "Europe/London"))
        alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        sam = household.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        store.addConnection(
            Connection("c1", "calendar.test", "Google", emptyMap()),
            listOf(CalendarSource("s-alex", "Alex", writable = false), CalendarSource("s-family", "Family", writable = false)),
            mapOf("s-alex" to SourceMapping(alex.id, visible = true)),
        )
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    private fun timed(title: String, day: Int, hour: Int, minute: Int, minutes: Long, forPerson: String? = null): RemoteEvent {
        val start = sept(day).atTime(hour, minute).atZone(london).toInstant()
        return RemoteEvent(title, title, EventTime.Timed(start), EventTime.Timed(start.plusSeconds(minutes * 60)), recurring = false, forPerson = forPerson)
    }

    private fun allDay(title: String, fromDay: Int, toDayExclusive: Int) =
        RemoteEvent(title, title, EventTime.AllDay(sept(fromDay)), EventTime.AllDay(sept(toDayExclusive)), recurring = false)

    private fun local(title: String, start: LocalDateTime, end: LocalDateTime) = RemoteEvent(
        title, title,
        EventTime.Timed(start.atZone(london).toInstant()),
        EventTime.Timed(end.atZone(london).toInstant()),
        recurring = false,
    )

    private fun allDayOn(title: String, date: LocalDate) =
        RemoteEvent(title, title, EventTime.AllDay(date), EventTime.AllDay(date.plusDays(1)), recurring = false)

    private suspend fun titlesAndLabels(start: LocalDate, count: Int) =
        repo.days(start, count).first().map { d -> d.events.map { "${it.title} ${it.timeLabel}" } }

    private suspend fun put(sourceId: String, vararg events: RemoteEvent) =
        store.applySync("c1", sourceId, window, SyncResult(events.toList(), emptyList(), null, fullReplace = true))

    @Test
    fun dayListsEventsInStartOrderWithLabelsAndPeople() = runTest {
        put("s-alex", timed("Dinner with Jo & Priya", 23, 19, 30, 90))
        put("s-family", timed("School run", 23, 7, 45, 45, forPerson = sam.id.value))
        val day = repo.day(sept(23)).first()
        assertThat(day.map { Triple(it.title, it.timeLabel, it.person.name) }).containsExactly(
            Triple("School run", "07:45–08:30", "Sam"),
            Triple("Dinner with Jo & Priya", "19:30–21:00", "Alex"),
        ).inOrder()
        assertThat(day.map { it.startLabel }).containsExactly("07:45", "19:30").inOrder()
    }

    @Test
    fun untaggedEventOnAnUnmappedSourceIsFamily() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60))
        assertThat(repo.day(sept(23)).first().single().person).isEqualTo(Person.Family)
    }

    @Test
    fun allDayEventsComeFirstAndSayAllDay() = runTest {
        put("s-family", timed("Boiler service", 23, 10, 0, 60), allDay("Bin day", 23, 24))
        val day = repo.day(sept(23)).first()
        assertThat(day.map { it.title }).containsExactly("Bin day", "Boiler service").inOrder()
        assertThat(day.first().timeLabel).isEqualTo("All day")
        assertThat(day.first().startLabel).isEqualTo("All day")
        assertThat(day.first().allDay).isTrue()
    }

    @Test
    fun multiDayAllDayEventAppearsOnEveryDayItCovers() = runTest {
        put("s-family", allDay("Half term", 21, 25))
        val days = repo.days(sept(20), 6).first()
        assertThat(days.map { d -> d.date.dayOfMonth to d.events.map { it.title } }).containsExactly(
            20 to emptyList<String>(),
            21 to listOf("Half term"),
            22 to listOf("Half term"),
            23 to listOf("Half term"),
            24 to listOf("Half term"),
            25 to emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun multiDayEventStartingBeforeTheQueryShowsOnEveryVisibleDay() = runTest {
        put("s-family", allDay("Half term", 21, 25))
        val days = repo.days(sept(22), 4).first()
        assertThat(days.map { d -> d.date.dayOfMonth to d.events.map { it.title } }).containsExactly(
            22 to listOf("Half term"),
            23 to listOf("Half term"),
            24 to listOf("Half term"),
            25 to emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun overnightEventReadsFromThenUntil() = runTest {
        put("s-alex", timed("Night shift", 23, 22, 0, 180))
        val days = repo.days(sept(23), 2).first()
        assertThat(days.map { d -> d.events.map { "${it.timeLabel} / ${it.startLabel}" } }).containsExactly(
            listOf("22:00– / 22:00–"),
            listOf("until 01:00 / until 01:00"),
        ).inOrder()
    }

    @Test
    fun threeDayTimedEventReadsFromThenAllDayThenUntil() = runTest {
        // Monday 21 September 09:00 to Wednesday 23 September 17:00.
        put("s-alex", timed("Conference", 21, 9, 0, (2 * 24 + 8) * 60L))
        put("s-family", timed("Breakfast", 22, 8, 0, 60))
        assertThat(titlesAndLabels(sept(21), 4)).containsExactly(
            listOf("Conference 09:00–"),
            listOf("Conference All day", "Breakfast 08:00–09:00"),
            listOf("Conference until 17:00"),
            emptyList<String>(),
        ).inOrder()
        assertThat(repo.day(sept(22)).first().first().allDay).isTrue()
    }

    @Test
    fun recurringFlagReachesTheUi() = runTest {
        put("s-family", timed("Swimming", 23, 16, 0, 60).copy(recurring = true), timed("Boiler service", 23, 10, 0, 60))
        assertThat(repo.day(sept(23)).first().associate { it.title to it.recurring })
            .containsExactly("Boiler service", false, "Swimming", true)
    }

    @Test
    fun autumnClockChangeKeepsEventsOnTheirDayWithLocalLabels() = runTest {
        // Clocks go back at 02:00 BST on Sunday 25 October 2026.
        val change = LocalDate.of(2026, 10, 25)
        put("s-family", allDayOn("Clocks back", change), local("Night feed", change.atTime(0, 30), change.atTime(3, 0)))
        assertThat(titlesAndLabels(change.minusDays(1), 3)).containsExactly(
            emptyList<String>(),
            listOf("Clocks back All day", "Night feed 00:30–03:00"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun springClockChangeKeepsEventsOnTheirDayWithLocalLabels() = runTest {
        // Clocks go forward at 01:00 GMT on Sunday 28 March 2027.
        val change = LocalDate.of(2027, 3, 28)
        put("s-family", allDayOn("Clocks forward", change), local("Night feed", change.atTime(0, 30), change.atTime(3, 0)))
        assertThat(titlesAndLabels(change.minusDays(1), 3)).containsExactly(
            emptyList<String>(),
            listOf("Clocks forward All day", "Night feed 00:30–03:00"),
            emptyList<String>(),
        ).inOrder()
    }

    @Test
    fun eventsUseTheHouseholdZone() = runTest {
        household.setLocation(HomeLocation("Tokyo", 35.68, 139.69, "Asia/Tokyo"))
        val start = Instant.parse("2026-09-23T00:30:00Z")
        put("s-alex", RemoteEvent("x", "Breakfast", EventTime.Timed(start), EventTime.Timed(start.plusSeconds(3600)), recurring = false))
        assertThat(repo.day(sept(23)).first().single().timeLabel).isEqualTo("09:30–10:30")
    }

    @Test
    fun weekHasSevenDaysAndALegendOfPeopleThenFamily() = runTest {
        val week = repo.week(sept(23)).first()
        assertThat(week.start).isEqualTo(sept(23))
        assertThat(week.days.map { it.date }).isEqualTo((0L..6L).map { sept(23).plusDays(it) })
        assertThat(week.people.map { it.name }).containsExactly("Alex", "Sam", "Family").inOrder()
    }

    @Test
    fun hasConnectionsFollowsTheStore() = runTest {
        val emptyDb = calendarDb()
        val emptyStore = CalendarStore(emptyDb)
        val emptyRepo = CalendarRepository(emptyStore, household, HouseholdZone(household))
        assertThat(emptyRepo.hasConnections.first()).isFalse()
        emptyStore.addConnection(Connection("c9", "calendar.test", "Other", emptyMap()), emptyList(), emptyMap())
        assertThat(emptyRepo.hasConnections.first()).isTrue()
        emptyDb.close()
    }

    @Test
    fun syncStatusUsesTheStalestConnectionAndListsThoseNeedingSignIn() = runTest {
        store.addConnection(Connection("c2", "calendar.test", "School", emptyMap()), emptyList(), emptyMap())
        store.markSynced("c1", 5_000L)
        store.markSynced("c2", 2_000L)
        store.setHealth("c1", ConnectionHealth.NeedsSignIn)
        val status = repo.syncStatus.first()
        assertThat(status.lastSyncMillis).isEqualTo(2_000L)
        assertThat(status.needsSignIn).containsExactly("Google")
        assertThat(status.connectionLabels).containsExactly("Google", "School").inOrder()
        assertThat(status.failingBeforeFirstSync).isFalse()
    }

    @Test
    fun aConnectionFailingBeforeItsFirstSyncMakesTheStatusStale() = runTest {
        store.addConnection(Connection("c2", "calendar.test", "School", emptyMap()), emptyList(), emptyMap())
        store.markSynced("c1", 5_000L)
        store.setHealth("c2", ConnectionHealth.Unreachable)
        val status = repo.syncStatus.first()
        assertThat(status.lastSyncMillis).isEqualTo(5_000L)
        assertThat(status.failingBeforeFirstSync).isTrue()
        assertThat(status.isStaleAt(5_000L)).isTrue()
    }
}
```

`setUp` uses `runTest` so it can call suspend functions. The `@Before` function returns `TestResult`, which on the JVM is `Unit`, so JUnit4 accepts it.

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*SyncLabelsTest*" --tests "*PersonResolutionTest*" --tests "*CalendarRepositoryTest*"`
Expected: FAIL with `NotImplementedError`: 10 label tests, 5 resolution tests and 15 repository tests.

- [ ] **Step 4: Implement the labels, resolution and mapping**

In `CalendarUi.kt`:

1. Add the imports:
   ```kotlin
   import java.time.LocalTime
   import java.time.format.DateTimeFormatter
   ```
2. Replace the six stubbed functions with:
   ```kotlin
   fun syncedLabel(lastSyncMillis: Long?, nowMillis: Long, connectionLabel: String? = null): String {
       if (lastSyncMillis == null) return "not synced yet"
       val synced = if (connectionLabel == null) "synced" else "synced with $connectionLabel"
       // A wall clock that jumped backwards gives a negative age; treat it as fresh.
       val minutes = (nowMillis - lastSyncMillis).coerceAtLeast(0) / 60_000
       val hours = minutes / 60
       val ago = when {
           minutes < 1 -> "just now"
           minutes < 60 -> "$minutes min ago"
           hours < 48 -> "$hours h ago"
           else -> "${hours / 24} days ago"
       }
       return "$synced $ago"
   }

   fun isStale(lastSyncMillis: Long?, nowMillis: Long): Boolean =
       lastSyncMillis != null && nowMillis - lastSyncMillis > STALE_AFTER_MS

   fun SyncStatusUi.isStaleAt(nowMillis: Long): Boolean =
       failingBeforeFirstSync || isStale(lastSyncMillis, nowMillis)

   fun weekSubtitle(sync: SyncStatusUi, nowMillis: Long): String =
       "Family calendar · ${syncedLabel(sync.lastSyncMillis, nowMillis, sync.connectionLabels.singleOrNull())}"

   /** A tag naming a real person (or Family) wins; otherwise the source's person; otherwise Family. */
   fun resolvePerson(forPerson: String?, sourcePerson: PersonId, people: Map<PersonId, Person>): Person =
       forPerson?.let { people[PersonId(it)] } ?: people[sourcePerson] ?: Person.Family

   private val HOURS_MINUTES = DateTimeFormatter.ofPattern("HH:mm")

   private class Slice(val timeLabel: String, val startLabel: String, val allDay: Boolean)

   /** How the event reads on [date]. A timed event over several days reads "22:00–", then "All day", then "until 01:00". */
   private fun StoredEvent.sliceOn(date: LocalDate, zone: ZoneId): Slice {
       if (start is EventTime.AllDay) return Slice(ALL_DAY_LABEL, ALL_DAY_LABEL, allDay = true)
       val from = start.instantIn(zone).atZone(zone)
       val to = end.instantIn(zone).atZone(zone)
       val endsAtMidnight = to.toLocalTime() == LocalTime.MIDNIGHT
       // An event ending at exactly midnight ends on the day before.
       val lastDay = if (endsAtMidnight && to.toLocalDate().isAfter(from.toLocalDate())) to.toLocalDate().minusDays(1) else to.toLocalDate()
       val startClock = from.format(HOURS_MINUTES)
       val endClock = to.format(HOURS_MINUTES)
       return when {
           lastDay == from.toLocalDate() -> Slice("$startClock–$endClock", startClock, allDay = false)
           date == from.toLocalDate() -> Slice("$startClock–", "$startClock–", allDay = false)
           date == lastDay && !endsAtMidnight -> Slice("until $endClock", "until $endClock", allDay = false)
           else -> Slice(ALL_DAY_LABEL, ALL_DAY_LABEL, allDay = true)
       }
   }

   internal fun StoredEvent.toUi(date: LocalDate, zone: ZoneId, people: Map<PersonId, Person>): EventUi {
       val slice = sliceOn(date, zone)
       return EventUi(
           key = "$connectionId/$sourceId/$remoteId",
           title = title,
           timeLabel = slice.timeLabel,
           startLabel = slice.startLabel,
           person = resolvePerson(forPerson, sourcePerson, people),
           allDay = slice.allDay,
           recurring = recurring,
           startSort = startSort,
       )
   }
   ```

- [ ] **Step 5: Implement the repository**

Replace `CalendarRepository.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar

import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.ConnectionHealth

/** Read-only view of the cache as UI models. The UI never touches the network. */
@Singleton
class CalendarRepository @Inject constructor(
    private val store: CalendarStore,
    private val household: HouseholdRepository,
    private val zone: HouseholdZone,
) {
    val hasConnections: Flow<Boolean> = store.connectionIds().map { it.isNotEmpty() }.distinctUntilChanged()

    val syncStatus: Flow<SyncStatusUi> = store.connections().map { connections ->
        SyncStatusUi(
            lastSyncMillis = connections.mapNotNull { it.lastSyncMillis }.minOrNull(),
            needsSignIn = connections.filter { it.health == ConnectionHealth.NeedsSignIn }.map { it.connection.label },
            connectionLabels = connections.map { it.connection.label },
            failingBeforeFirstSync = connections.any { it.lastSyncMillis == null && it.health != ConnectionHealth.Ok },
        )
    }.distinctUntilChanged()

    fun day(date: LocalDate): Flow<List<EventUi>> = days(date, 1).map { it.single().events }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun days(start: LocalDate, count: Int): Flow<List<DayUi>> = zone.zone.flatMapLatest { z ->
        combine(store.eventsBetween(millis(start, z), millis(start.plusDays(count.toLong()), z)), household.peopleWithFamily) { events, people ->
            val byId = people.associateBy { it.id }
            (0 until count).map { i ->
                val date = start.plusDays(i.toLong())
                val dayStart = millis(date, z)
                val dayEnd = millis(date.plusDays(1), z)
                DayUi(
                    date,
                    events.filter { spanOverlaps(it.startSort, it.endSort, dayStart, dayEnd) }
                        .map { it.toUi(date, z, byId) }
                        .sortedWith(compareByDescending<EventUi> { it.allDay }.thenBy { it.startSort }.thenBy { it.title }),
                )
            }
        }
    }

    fun week(start: LocalDate): Flow<WeekUi> =
        combine(days(start, 7), household.people) { days, people -> WeekUi(start, days, people + Person.Family) }

    private fun millis(date: LocalDate, z: ZoneId) = date.atStartOfDay(z).toInstant().toEpochMilli()
}
```

`flatMapLatest` is `@ExperimentalCoroutinesApi` (experimental, not deprecated). The `@OptIn` is intended.

- [ ] **Step 6: Run to verify they pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS, including 10 + 5 + 15 new tests.

- [ ] **Step 7: Commit**

```bash
git add capability/calendar
git commit -m "Add CalendarRepository with person resolution and sync status"
```

---

### Task 11: `CalendarCapability` and the Home cards (Today, Coming up, Connect a calendar)

**Files:**
- Modify: `capability/calendar/build.gradle.kts`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/di/CalendarModule.kt`
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`, `ui/Components.kt`, `ui/Now.kt`, `ui/TodayCard.kt`, `ui/ComingUpCard.kt`, `ui/ConnectCalendarCard.kt`, `ui/CardHosts.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingNavigator.kt`, `ui/SampleUi.kt`, `ui/CardsTest.kt`, `ui/NowTest.kt`, `ui/CardScreenshotTest.kt`, and `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt`
- Create (recorded): `capability/calendar/src/test/screenshots/{today_dark,today_light,today_empty_dark,coming_up_dark,coming_up_light,coming_up_busy_dark,connect_dark,connect_light}.png`

**Interfaces:**
- Consumes:
  - `CalendarRepository`: `day`, `days`, `hasConnections` (Task 10)
  - `EventUi`, `DayUi`
  - `HouseholdZone.zone`
  - `WallClock`
  - `Capability`, `HomeCard`, `HomeCardSize`, `LocalShellNavigator`, `ShellNavigator` (`:core:plugin`)
  - `HhCard(modifier, radius, color, padding, content)`, `HhPillButton`, `HhIcon(…, contentDescription)`, `HhType`, `Culvery.colors` (including `danger`, `dangerSoft` from Task 2), `CulveryTheme`
  - `EventUi(key, title, timeLabel, startLabel, person, allDay, recurring, startSort)` (Task 10)
  - `CalendarSyncLoop` (Task 9)
- Produces:
  - `const val CALENDAR_TAB_ID = "calendar"`, `CONNECT_CARD_ID = "calendar.connect"`, `TODAY_CARD_ID = "calendar.today"`, `COMING_UP_CARD_ID = "calendar.comingUp"` (package `…capability.calendar`)
  - `@Singleton class CalendarCapability @Inject constructor(repo: CalendarRepository, zone: HouseholdZone, clock: WallClock) : Capability`:
    - id `calendar`, label "Calendar", icon `calendar_month`, order 10
    - `hasTab = repo.hasConnections`
    - `cards()` gives `[Connect TALL 100]` when there are no connections, otherwise `[Today TALL 100, ComingUp WIDE 50]`
  - Public stateless composables (package `…capability.calendar.ui`). Each reads `LocalShellNavigator`:
    - `TodayCard(events: List<EventUi>?, modifier: Modifier = Modifier)`. Null means loading.
    - `ComingUpCard(days: List<DayUi>?, modifier: Modifier = Modifier)`. `days[0]` is tomorrow.
    - `ConnectCalendarCard(modifier: Modifier = Modifier)`
  - Internal helpers:
    - `HeaderChip(text, onClick, modifier)`: a visible `surf2` pill, 44 dp tall, radius 22, 16 dp horizontal padding, 14 sp / 600, with the background on the 44 dp box (hand-off §7 "Week" chip).
    - `HeaderLink(text, onClick, modifier)`: plain muted 14 sp text with a 44 dp touch target (the hand-off's "3 lights on ›" pattern).
    - `ColourBar(color, width)`
    - `CalendarType`: `strong14`, `small12`, `chipTime`, `chipTitle`, `weekTitle`, `subtitle`, `legend`, `pill`, `link`
    - `CalendarDimens`: every prototype layout value the cards and the week view use (see Step 2)
    - `rememberNowMillis(clock)`, `rememberZoneId(zone)`, `todayIn(zone, nowMillis)`, `rememberToday(zone, clock)`
  - Coming up shows at most `MAX_ROWS = 2` rows per day, then "+N more" (arithmetic in Step 6).
  - Today rows and (Task 12) week chips show `HhIcon("repeat", …, tint = mute, contentDescription = "Repeats")` when `recurring`.
  - `CalendarModule` binds:
    - `@IntoSet Capability` (`CalendarCapability`)
    - `@IntoSet Startable` (`CalendarSyncLoop`)
    - `@Multibinds Set<CalendarProvider>`
    - the database
  - Test helpers: `RecordingNavigator` and `SampleUi`. `SampleUi` has `TODAY`, `NOW`, the people, `event(title, time, person, recurring = false)`, `allDay(title, person, recurring = false)`, `today`, `comingUp`, `comingUpBusy` and `week`, mirroring `screenshots/calendar-sheets/02-calendar-week-dark.png`.

- [ ] **Step 1: Add `:core:ui` and Roborazzi to the module**

Replace `capability/calendar/build.gradle.kts` with:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
    alias(libs.plugins.roborazzi)
}

dependencies {
    api(project(":core:plugin"))
    // PersonId and Person appear in this module's public API (SourceMapping, EventUi).
    api(project(":core:household"))
    implementation(project(":core:ui"))
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
```

- [ ] **Step 2: Write the shared UI pieces**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.co.siland.culvery.core.ui.HhType

/** Calendar text styles from the hand-off and Culvery.dc.html that HhType has no name for; all derived from HhType. */
internal object CalendarType {
    /** 14 sp / 600: day labels, compact titles. */
    val strong14 = HhType.secondary.copy(fontWeight = FontWeight.W600)

    /** 12 sp / 400: compact times, "+N more". */
    val small12 = HhType.secondary.copy(fontSize = 12.sp)

    /** 12 sp / 700: week-view chip times. */
    val chipTime = HhType.labelSmall

    /** 14 sp / 600 at 1.25 line height: week-view chip titles. */
    val chipTitle = strong14.copy(lineHeight = 17.5.sp)

    /** 34 sp / 700, −0.5 tracking: "This week". */
    val weekTitle = HhType.screenTitle.copy(letterSpacing = (-0.5).sp)

    /** 15 sp / 400: the week-view subtitle. */
    val subtitle = HhType.secondary.copy(fontSize = 15.sp)

    /** 14 sp / 500: legend names. */
    val legend = HhType.secondary.copy(fontWeight = FontWeight.W500)

    /** 14 sp / 600: the "Week" pill and the reconnect chip. */
    val pill = strong14

    /** 14 sp / 400: header text links ("Week ›"). */
    val link = HhType.secondary
}

/** Layout values from Culvery.dc.html, named once so no card or view carries magic numbers. */
internal object CalendarDimens {
    // Pills and touch targets (hand-off §7: "Week" chip 44 dp tall, radius 22).
    val touchTarget = 44.dp
    val pillRadius = 22.dp
    val pillPaddingH = 16.dp

    // Today card: header→first row = 10 gap + 4 margin; row bar→text 14; time line 3 below the title.
    val todayHeaderGap = 14.dp
    val todayRowGap = 10.dp
    val todayBarGap = 14.dp
    val todayTimeTop = 3.dp
    val todayBadge = 20.dp

    // Coming up card: padding 20 × 22, like the hand-off's WIDE Scenes card.
    val comingUpPaddingV = 20.dp
    val comingUpPaddingH = 22.dp

    // Connect card: padding 20 × 22, 4 dp above the subtitle, like the hand-off's Holiday tile.
    val connectPaddingV = 20.dp
    val connectPaddingH = 22.dp
    val connectSubtitleTop = 4.dp

    // Week view header: subtitle 4 below the title; legend 18 between people, 7 dot→name, 24 to the
    // right-hand button slot (2b's Add event); reconnect chip 8 below the subtitle.
    val subtitleTop = 4.dp
    val legendGap = 18.dp
    val legendDotGap = 7.dp
    val legendDot = 10.dp
    val legendNameMax = 120.dp
    val headerTrailingGap = 24.dp
    val reconnectTop = 8.dp
    val reconnectIcon = 20.dp

    // Week columns: header inset 4 at the sides and bottom; chip title 2 below the time; badge 15.
    val columnHeaderInset = 4.dp
    val columnGap = 8.dp
    val chipTitleTop = 2.dp
    val chipBadge = 15.dp

    /** Chip tint: the person's colour at 0x2E alpha on dark, 0x26 on light. */
    const val CHIP_ALPHA_DARK = 0x2E / 255f
    const val CHIP_ALPHA_LIGHT = 0x26 / 255f
}
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Components.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import uk.co.siland.culvery.core.ui.Culvery

/** Card-header pill ("Week"): hand-off §7, 44 dp tall, radius 22, `surf2` on the whole 44 dp box. */
@Composable
internal fun HeaderChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.surf2)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        Text(text, style = CalendarType.pill, color = c.ink, maxLines = 1)
    }
}

/** Card-header text link ("Week ›"): muted text, as the hand-off's "3 lights on ›", with a 44 dp touch target. */
@Composable
internal fun HeaderLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier.heightIn(min = CalendarDimens.touchTarget).clickable(onClick = onClick),
    ) {
        Text(text, style = CalendarType.link, color = c.mute, maxLines = 1)
    }
}

/** The person-colour bar at the start of an event row; fills the row's intrinsic height. */
@Composable
internal fun ColourBar(color: Color, width: Dp) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(width / 2))
            .background(color),
    )
}
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Now.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import uk.co.siland.culvery.capability.calendar.HouseholdZone
import uk.co.siland.culvery.core.plugin.WallClock

private const val TICK_MS = 30_000L

/** Wall-clock time that refreshes every 30 s, so "today" and "synced x ago" move on while the screen is up. */
@Composable
internal fun rememberNowMillis(clock: WallClock): Long {
    val now by produceState(clock.nowMillis(), clock) {
        while (true) {
            delay(TICK_MS)
            value = clock.nowMillis()
        }
    }
    return now
}

@Composable
internal fun rememberZoneId(zone: HouseholdZone): ZoneId {
    val z by zone.zone.collectAsState(initial = ZoneId.systemDefault())
    return z
}

internal fun todayIn(zone: ZoneId, nowMillis: Long): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

@Composable
internal fun rememberToday(zone: HouseholdZone, clock: WallClock): LocalDate =
    todayIn(rememberZoneId(zone), rememberNowMillis(clock))
```

- [ ] **Step 3: Stub the cards and the capability**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/TodayCard.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uk.co.siland.culvery.capability.calendar.EventUi

@Composable
fun TodayCard(events: List<EventUi>?, modifier: Modifier = Modifier): Unit = TODO()
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ComingUpCard.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uk.co.siland.culvery.capability.calendar.DayUi

@Composable
fun ComingUpCard(days: List<DayUi>?, modifier: Modifier = Modifier): Unit = TODO()
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ConnectCalendarCard.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ConnectCalendarCard(modifier: Modifier = Modifier): Unit = TODO()
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CardHosts.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi

@Composable
internal fun TodayCardHost(repo: CalendarRepository, today: LocalDate) {
    val events: List<EventUi>? by remember(today) { repo.day(today) }.collectAsState(initial = null)
    TodayCard(events)
}

@Composable
internal fun ComingUpCardHost(repo: CalendarRepository, today: LocalDate) {
    val days: List<DayUi>? by remember(today) { repo.days(today.plusDays(1), 3) }.collectAsState(initial = null)
    ComingUpCard(days)
}
```

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.WallClock

const val CALENDAR_TAB_ID = "calendar"
const val CONNECT_CARD_ID = "calendar.connect"
const val TODAY_CARD_ID = "calendar.today"
const val COMING_UP_CARD_ID = "calendar.comingUp"

@Singleton
class CalendarCapability @Inject constructor(
    private val repo: CalendarRepository,
    private val zone: HouseholdZone,
    private val clock: WallClock,
) : Capability {
    override val id = CALENDAR_TAB_ID
    override val label = "Calendar"
    override val icon = "calendar_month"
    override val order = 10
    override val hasTab: Flow<Boolean> = repo.hasConnections

    override fun cards(): Flow<List<HomeCard>> = TODO()

    // Task 12 replaces this with the week view.
    @Composable
    override fun TabContent() {
        Box(Modifier.fillMaxSize())
    }
}
```

- [ ] **Step 4: Write the test helpers and the failing tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/RecordingNavigator.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import uk.co.siland.culvery.core.plugin.ShellNavigator

class RecordingNavigator : ShellNavigator {
    val tabs = mutableListOf<String>()
    var settingsOpened = 0

    override fun openTab(id: String) {
        tabs += id
    }

    override fun openSettings() {
        settingsOpened++
    }
}
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/SampleUi.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import java.time.Instant
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.ALL_DAY_LABEL
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId

/** The hand-off's sample week (Wednesday 23 September 2026, calendar-sheets/02-calendar-week-dark.png) as UI models. */
object SampleUi {
    val TODAY: LocalDate = LocalDate.of(2026, 9, 23)
    val NOW: Long = Instant.parse("2026-09-23T10:54:00Z").toEpochMilli()

    val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)
    val family = Person.Family
    val people = listOf(alex, sam, mia, family)

    fun event(title: String, time: String, person: Person, recurring: Boolean = false) =
        EventUi(title, title, time, time.substringBefore('–'), person, allDay = false, recurring = recurring, startSort = 0)

    fun allDay(title: String, person: Person, recurring: Boolean = false) =
        EventUi(title, title, ALL_DAY_LABEL, ALL_DAY_LABEL, person, allDay = true, recurring = recurring, startSort = 0)

    private fun day(offset: Long, vararg events: EventUi) = DayUi(TODAY.plusDays(offset), events.toList())

    val today = listOf(
        event("School run", "07:45–08:30", sam),
        event("Boiler service", "10:00–11:00", family),
        event("Plumber quote call", "13:00–13:30", family),
        event("Swimming", "16:00–17:00", mia, recurring = true),
        event("Dinner with Jo & Priya", "19:30–21:00", alex),
    )

    val comingUp = listOf(
        day(1, event("Office day", "09:00–17:00", alex), event("Football", "18:00–19:00", mia)),
        day(2, allDay("Bin day", family, recurring = true), event("Dentist", "12:30–13:30", sam)),
        day(
            3,
            allDay("INSET day — no school", family),
            event("Piano", "15:30–16:30", mia, recurring = true),
            event("Book club", "20:00–22:00", sam),
        ),
    )

    val comingUpBusy = listOf(
        day(
            1,
            event("Office day", "09:00–17:30", alex),
            event("Swim club", "16:00–17:00", mia),
            event("Football", "18:00–19:00", mia),
            event("Parents' evening", "19:00–20:00", sam),
        ),
        day(2),
        day(3, event("Piano", "15:30–16:00", mia)),
    )

    val week = WeekUi(
        TODAY,
        listOf(
            day(0, *today.toTypedArray()),
            comingUp[0],
            comingUp[1],
            comingUp[2],
            day(4, event("Pizza night", "19:00–23:00", family)),
            day(5, event("Parkrun", "09:30–11:00", alex), event("Birthday party", "14:00–17:00", mia)),
            day(6, event("Sunday lunch at Gran's", "12:00–15:00", family)),
        ),
        people,
    )
}
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Native graphics: the sized tests check that rows fit the real card sizes, which needs real text metrics. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardsTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { content() }
        }
    }

    /** The card at the size the Home grid gives it on the 1280×800 canvas. */
    private fun showSized(width: Int, height: Int, content: @Composable () -> Unit) = show {
        Box(Modifier.size(width.dp, height.dp)) { content() }
    }

    @Test
    fun todayRowShowsTimeAndPerson() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("School run").assertExists()
        compose.onNodeWithText("07:45–08:30 · Sam").assertExists()
    }

    @Test
    fun emptyTodaySaysNothingOnToday() {
        show { TodayCard(emptyList()) }
        compose.onNodeWithText("Nothing on today").assertExists()
    }

    @Test
    fun loadingTodayShowsNoEmptyMessage() {
        show { TodayCard(null) }
        compose.onNodeWithText("Nothing on today").assertDoesNotExist()
    }

    @Test
    fun weekChipOnTodayOpensTheCalendarTab() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Week").performClick()
        assertThat(navigator.tabs).containsExactly(CALENDAR_TAB_ID)
    }

    @Test
    fun weekChipOnTodayIsAFullSizePill() {
        show { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Week").assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun todayShowsTheFourthRowInTheTallCard() {
        showSized(397, 572) { TodayCard(SampleUi.today) }
        compose.onNodeWithText("Swimming").assertIsDisplayed()
    }

    @Test
    fun recurringTodayRowShowsTheRepeatBadge() {
        show { TodayCard(SampleUi.today) }
        compose.onAllNodesWithContentDescription("Repeats").assertCountEquals(1)
    }

    @Test
    fun comingUpLabelsTomorrowThenWeekdays() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("Tomorrow").assertExists()
        compose.onNodeWithText("Friday").assertExists()
        compose.onNodeWithText("Saturday").assertExists()
    }

    @Test
    fun comingUpShowsTwoRowsAndAVisibleMoreInTheWideCard() {
        showSized(705, 279) { ComingUpCard(SampleUi.comingUpBusy) }
        compose.onNodeWithText("Swim club").assertIsDisplayed()
        compose.onNodeWithText("Football").assertDoesNotExist()
        compose.onNodeWithText("+2 more").assertIsDisplayed()
    }

    @Test
    fun comingUpEmptyDaySaysFree() {
        show { ComingUpCard(SampleUi.comingUpBusy) }
        compose.onNodeWithText("Free").assertExists()
    }

    @Test
    fun weekLinkOnComingUpOpensTheCalendarTab() {
        show { ComingUpCard(SampleUi.comingUp) }
        compose.onNodeWithText("Week ›").assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("Week ›").performClick()
        assertThat(navigator.tabs).containsExactly(CALENDAR_TAB_ID)
    }

    @Test
    fun connectCardOpensSettings() {
        show { ConnectCalendarCard() }
        compose.onNodeWithText("Connect a calendar").assertExists()
        compose.onNodeWithText("Open settings").performClick()
        assertThat(navigator.settingsOpened).isEqualTo(1)
    }
}
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class CalendarCapabilityTest {
    private lateinit var calendar: CalendarDatabase
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var store: CalendarStore
    private lateinit var capability: CalendarCapability

    @Before
    fun setUp() {
        calendar = calendarDb()
        householdDb = householdDb()
        store = CalendarStore(calendar)
        val household = HouseholdRepository(householdDb)
        val zone = HouseholdZone(household)
        capability = CalendarCapability(CalendarRepository(store, household, zone), zone, WallClock { 0L })
    }

    @After
    fun tearDown() {
        calendar.close()
        householdDb.close()
    }

    private suspend fun connect() =
        store.addConnection(Connection("c1", "calendar.test", "Test", emptyMap()), emptyList(), emptyMap())

    @Test
    fun railEntryIsCalendarAtOrderTen() {
        assertThat(capability.id).isEqualTo("calendar")
        assertThat(capability.label).isEqualTo("Calendar")
        assertThat(capability.icon).isEqualTo("calendar_month")
        assertThat(capability.order).isEqualTo(10)
    }

    @Test
    fun noConnectionsGivesTheConnectCardAndNoTab() = runTest {
        val cards = capability.cards().first()
        assertThat(cards.map { Triple(it.id, it.size, it.priority) })
            .containsExactly(Triple(CONNECT_CARD_ID, HomeCardSize.TALL, 100))
        assertThat(capability.hasTab.first()).isFalse()
    }

    @Test
    fun aConnectionGivesTodayAndComingUpAndTheTab() = runTest {
        connect()
        val cards = capability.cards().first()
        assertThat(cards.map { Triple(it.id, it.size, it.priority) }).containsExactly(
            Triple(TODAY_CARD_ID, HomeCardSize.TALL, 100),
            Triple(COMING_UP_CARD_ID, HomeCardSize.WIDE, 50),
        ).inOrder()
        assertThat(capability.hasTab.first()).isTrue()
    }
}
```

- [ ] **Step 5: Run to verify they fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CardsTest*" --tests "*CalendarCapabilityTest*"`
Expected: FAIL:
- The 12 `CardsTest` tests fail with `NotImplementedError` thrown during composition.
- The two `cards()` tests fail with `NotImplementedError`.
- `railEntryIsCalendarAtOrderTen` passes.

- [ ] **Step 6: Implement the cards**

Replace `ui/TodayCard.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType

/** Hand-off Home "Today" card. [events] null while loading: shows nothing rather than a false "Nothing on today". */
@Composable
fun TodayCard(events: List<EventUi>?, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(modifier = modifier.fillMaxSize().testTag("calendar_today"), radius = 26.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Today", style = HhType.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
            HeaderChip("Week", onClick = { navigator.openTab(CALENDAR_TAB_ID) })
        }
        Spacer(Modifier.height(CalendarDimens.todayHeaderGap))
        when {
            events == null -> {}
            events.isEmpty() -> Text("Nothing on today", style = HhType.body, color = c.mute)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CalendarDimens.todayRowGap),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(events, key = { it.key }) { TodayRow(it) }
            }
        }
    }
}

@Composable
private fun TodayRow(event: EventUi) {
    val c = Culvery.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surf2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        ColourBar(Color(event.person.color), width = 4.dp)
        Spacer(Modifier.width(CalendarDimens.todayBarGap))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = HhType.rowTitle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(CalendarDimens.todayTimeTop))
            Text(
                "${event.timeLabel} · ${event.person.name}",
                style = HhType.secondary,
                color = c.mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (event.recurring) {
            HhIcon(
                "repeat",
                size = CalendarDimens.todayBadge,
                tint = c.mute,
                modifier = Modifier.align(Alignment.CenterVertically),
                contentDescription = "Repeats",
            )
        }
    }
}
```

Replace `ui/ComingUpCard.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.CALENDAR_TAB_ID
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhType

/**
 * Rows per day before "+N more". The WIDE card is 279 dp tall: 279 − 2 × 20 padding − 44 header − 6 gap = 189 dp
 * for a day column. The day label is about 19 dp, each compact row about 50 dp (8 + 18 + 16 + 8) plus a 6 dp gap,
 * and "+N more" about 16 dp plus its gap. Three rows need 19 + 3 × 56 + 22 = 209 dp and do not fit; two need
 * 19 + 2 × 56 + 22 = 153 dp, which leaves room for font rounding. So 2.
 */
private const val MAX_ROWS = 2
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.UK)

/** The next three days, [days] starting with tomorrow; null while loading. */
@Composable
fun ComingUpCard(days: List<DayUi>?, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_coming_up"),
        radius = 26.dp,
        padding = PaddingValues(horizontal = CalendarDimens.comingUpPaddingH, vertical = CalendarDimens.comingUpPaddingV),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Coming up", style = HhType.cardTitle, color = c.ink, modifier = Modifier.weight(1f))
            HeaderLink("Week ›", onClick = { navigator.openTab(CALENDAR_TAB_ID) })
        }
        Spacer(Modifier.height(6.dp))
        if (days != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                days.forEachIndexed { i, day ->
                    DayColumn(
                        label = if (i == 0) "Tomorrow" else day.date.format(WEEKDAY),
                        events = day.events,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayColumn(label: String, events: List<EventUi>, modifier: Modifier) {
    val c = Culvery.colors
    // Scrolls only if a tall font scale makes three rows overflow the card.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.verticalScroll(rememberScrollState())) {
        Text(label, style = CalendarType.strong14, color = c.ink)
        if (events.isEmpty()) Text("Free", style = HhType.secondary, color = c.mute)
        events.take(MAX_ROWS).forEach { CompactRow(it) }
        if (events.size > MAX_ROWS) {
            Text("+${events.size - MAX_ROWS} more", style = CalendarType.small12, color = c.mute)
        }
    }
}

@Composable
private fun CompactRow(event: EventUi) {
    val c = Culvery.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surf2)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        ColourBar(Color(event.person.color), width = 3.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(event.title, style = CalendarType.strong14, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(event.timeLabel, style = CalendarType.small12, color = c.mute, maxLines = 1)
        }
    }
}
```

Replace `ui/ConnectCalendarCard.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhType

/** Takes the Today slot until a calendar is connected (spec §9.2). Laid out like the hand-off's Holiday tile. */
@Composable
fun ConnectCalendarCard(modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(
        modifier = modifier.fillMaxSize().testTag("calendar_connect"),
        radius = 26.dp,
        padding = PaddingValues(horizontal = CalendarDimens.connectPaddingH, vertical = CalendarDimens.connectPaddingV),
    ) {
        HhIcon("calendar_add_on", size = 34.dp, tint = c.accent)
        Spacer(Modifier.weight(1f))
        Text("Connect a calendar", style = HhType.cardTitle, color = c.ink)
        Spacer(Modifier.height(CalendarDimens.connectSubtitleTop))
        Text("Add your family's calendars in Settings to see them here.", style = HhType.secondary, color = c.mute)
        Spacer(Modifier.height(18.dp))
        HhPillButton("Open settings", onClick = navigator::openSettings, primary = true)
    }
}
```

In `CalendarCapability.kt`:

1. Add the imports:
   ```kotlin
   import kotlinx.coroutines.flow.map
   import uk.co.siland.culvery.capability.calendar.ui.ComingUpCardHost
   import uk.co.siland.culvery.capability.calendar.ui.ConnectCalendarCard
   import uk.co.siland.culvery.capability.calendar.ui.TodayCardHost
   import uk.co.siland.culvery.capability.calendar.ui.rememberToday
   import uk.co.siland.culvery.core.plugin.HomeCardSize
   ```
2. Replace `override fun cards(): Flow<List<HomeCard>> = TODO()` with:
   ```kotlin
       override fun cards(): Flow<List<HomeCard>> = repo.hasConnections.map { connected ->
           if (!connected) {
               listOf(HomeCard(CONNECT_CARD_ID, HomeCardSize.TALL, 100) { ConnectCalendarCard() })
           } else {
               listOf(
                   HomeCard(TODAY_CARD_ID, HomeCardSize.TALL, 100) { TodayCardHost(repo, rememberToday(zone, clock)) },
                   HomeCard(COMING_UP_CARD_ID, HomeCardSize.WIDE, 50) { ComingUpCardHost(repo, rememberToday(zone, clock)) },
               )
           }
       }
   ```

Replace `di/CalendarModule.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import uk.co.siland.culvery.capability.calendar.CalendarCapability
import uk.co.siland.culvery.capability.calendar.CalendarProvider
import uk.co.siland.culvery.capability.calendar.CalendarSyncLoop
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {
    @Multibinds
    abstract fun providers(): Set<CalendarProvider>

    @Binds
    @IntoSet
    abstract fun capability(impl: CalendarCapability): Capability

    @Binds
    @IntoSet
    abstract fun syncLoop(impl: CalendarSyncLoop): Startable

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): CalendarDatabase =
            Room.databaseBuilder(context, CalendarDatabase::class.java, "calendar.db").build()
    }
}
```

- [ ] **Step 7: Run to verify they pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS: `CardsTest` (12 tests), `CalendarCapabilityTest` (3 tests), and all earlier tests.

If `comingUpShowsTwoRowsAndAVisibleMoreInTheWideCard` fails because "+2 more" is not displayed, the real text metrics are larger than the Step 6 estimate. Report the measured heights and stop; do not lower the card's padding or font sizes to make it fit.

- [ ] **Step 8: Pin the midnight rollover**

`rememberNowMillis` ticks every 30 s through `produceState`'s `delay`, which runs on the test's `mainClock`, so the clock can be driven past midnight without waiting. This pins behaviour Step 2 already implements, so it is expected to pass straight away.

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/NowTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class NowTest {
    @get:Rule val compose = createComposeRule()
    private val london = ZoneId.of("Europe/London")

    @Test
    fun todayRollsOverAtMidnight() {
        var now = LocalDateTime.of(2026, 9, 23, 23, 59, 50).atZone(london).toInstant().toEpochMilli()
        val clock = WallClock { now }
        compose.mainClock.autoAdvance = false
        compose.setContent { Text(todayIn(london, rememberNowMillis(clock)).toString()) }
        compose.onNodeWithText("2026-09-23").assertExists()

        now += 20_000 // 00:00:10 on the 24th
        compose.mainClock.advanceTimeBy(30_000)
        compose.onNodeWithText("2026-09-24").assertExists()
    }
}
```

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*NowTest*"`
Expected: PASS.

**If it fails with the text still "2026-09-23"**, `mainClock` is not driving `produceState`'s `delay` in this Compose version. Then inject the tick instead, and say so in your report:
1. In `ui/Now.kt`, replace `rememberNowMillis` with:
   ```kotlin
   /** Wall-clock time that refreshes on every [ticks] emission (every 30 s by default). */
   @Composable
   internal fun rememberNowMillis(clock: WallClock, ticks: Flow<Unit> = everyTick): Long {
       val now by produceState(clock.nowMillis(), clock, ticks) {
           ticks.collect { value = clock.nowMillis() }
       }
       return now
   }

   private val everyTick: Flow<Unit> = flow {
       while (true) {
           delay(TICK_MS)
           emit(Unit)
       }
   }
   ```
   with the imports `kotlinx.coroutines.flow.Flow` and `kotlinx.coroutines.flow.flow`.
2. In `NowTest`, add `val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)` (import `kotlinx.coroutines.flow.MutableSharedFlow`), call `rememberNowMillis(clock, ticks)`, and replace `compose.mainClock.advanceTimeBy(30_000)` with `ticks.tryEmit(Unit)` followed by `compose.waitForIdle()`. Remove the `autoAdvance` line.
3. Re-run the command above. Expected: PASS.

- [ ] **Step 9: Write the screenshot test**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CardScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** Card sizes as the Home grid gives them on the 1280×800 canvas. */
private val TALL_W = 397.dp
private val TALL_H = 572.dp
private val WIDE_W = 705.dp
private val WIDE_H = 279.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(name: String, dark: Boolean, width: Dp, height: Dp, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.testTag("shot").background(Culvery.colors.bg).padding(16.dp)) {
                        Box(Modifier.size(width, height)) { content() }
                    }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun todayDark() = snap("today_dark", true, TALL_W, TALL_H) { TodayCard(SampleUi.today) }
    @Test fun todayLight() = snap("today_light", false, TALL_W, TALL_H) { TodayCard(SampleUi.today) }
    @Test fun todayEmptyDark() = snap("today_empty_dark", true, TALL_W, TALL_H) { TodayCard(emptyList()) }
    @Test fun comingUpDark() = snap("coming_up_dark", true, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUp) }
    @Test fun comingUpLight() = snap("coming_up_light", false, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUp) }
    @Test fun comingUpBusyDark() = snap("coming_up_busy_dark", true, WIDE_W, WIDE_H) { ComingUpCard(SampleUi.comingUpBusy) }
    @Test fun connectDark() = snap("connect_dark", true, TALL_W, TALL_H) { ConnectCalendarCard() }
    @Test fun connectLight() = snap("connect_light", false, TALL_W, TALL_H) { ConnectCalendarCard() }
}
```

- [ ] **Step 10: Record and look at every image**

Run: `./gradlew :capability:calendar:recordRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, and 8 PNGs in `capability/calendar/src/test/screenshots/`.

Open each image and compare it with `docs/design/house_hub_handoff/screenshots/calendar-sheets/01-home-dark.png` (and `01-home-light.png` for the light variants).

Today card:
- "Today" in 19 sp bold, with a `surf2` "Week" pill on the right: 44 dp tall, fully rounded, 14 sp / 600. (The green **+** beside it in the hand-off is 2b.)
- Five `surf2` rows, radius 16, starting 14 dp below the header. Each has a 4 dp bar in the person's colour (Sam blue, Family amber, Family amber, Mia pink, Alex green), 14 dp from a 17 sp title, and a muted "07:45–08:30 · Sam" line 3 dp below it.
- Swimming has a muted 20 dp `repeat` icon at its right edge, centred vertically.

Coming up card (the controller's own design; no hand-off equivalent):
- Padding 20 × 22. "Coming up" on the left, a muted plain "Week ›" on the right (no pill).
- Three equal columns: Tomorrow, Friday, Saturday. Compact rows with a 3 dp bar, at most two per day.
- Saturday shows "INSET day — no school" and "Piano", then a **visible** "+1 more".
- In `coming_up_busy_dark`, the Tomorrow column shows "Office day", "Swim club" and a **visible** "+2 more", and the Friday column says "Free".

Connect card:
- Padding 20 × 22. A green `calendar_add_on` icon at the top, the text at the bottom with the subtitle 4 dp below the title, and a green "Open settings" pill.

Light variants use light tokens.

Run: `./gradlew :capability:calendar:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**

```bash
git add capability/calendar
git commit -m "Add the calendar capability with Today, Coming up and Connect cards"
```

---

### Task 12: Calendar tab week view, screenshots, and the user checkpoint

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CardHosts.kt` (`WeekViewHost`)
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt` (`TabContent`)
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekViewTest.kt`, `ui/WeekScreenshotTest.kt`
- Create (recorded): `capability/calendar/src/test/screenshots/{week_dark,week_light,week_stale_dark,week_needs_sign_in_dark}.png`

**Interfaces:**
- Consumes:
  - `WeekUi`, `DayUi`, `EventUi` (with `recurring`)
  - `SyncStatusUi(lastSyncMillis, needsSignIn, connectionLabels, failingBeforeFirstSync)`, `SyncStatusUi.isStaleAt(nowMillis)`, `weekSubtitle(sync, nowMillis)` (Task 10)
  - `CalendarRepository.week`, `CalendarRepository.syncStatus`
  - `rememberNowMillis`, `rememberZoneId`, `todayIn`
  - `CalendarType` (`weekTitle`, `subtitle`, `legend`, `pill`, `strong14`, `chipTime`, `chipTitle`), `CalendarDimens` (Task 11)
  - `Culvery.colors.danger`, `dangerSoft` (Task 2), `HhIcon(…, contentDescription)`, `HhCard`
  - `LocalShellNavigator`
  - `SampleUi`, `RecordingNavigator` (tests)
- Produces (package `…capability.calendar.ui`):
  - `data class WeekViewState(val week: WeekUi, val today: LocalDate, val sync: SyncStatusUi, val nowMillis: Long)`
  - `@Composable fun WeekView(state: WeekViewState, modifier: Modifier = Modifier)`. The title is always "This week"; there is no week navigation. Test tags:
    - `week_day_<ISO date>` on each column
    - `week_subtitle`
  - `internal fun reconnectLabel(labels: List<String>): String`: "{label} needs reconnecting" for one, "{n} calendars need reconnecting" for more.
  - `@Composable internal fun WeekViewHost(repo: CalendarRepository, today: LocalDate, nowMillis: Long)` in `CardHosts.kt`. It shows `repo.week(today)`, so the view is always today plus six days.
  - `CalendarCapability.TabContent()` shows `WeekViewHost`.

- [ ] **Step 1: Stub the week view and its host**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/WeekView.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi

data class WeekViewState(val week: WeekUi, val today: LocalDate, val sync: SyncStatusUi, val nowMillis: Long)

@Composable
fun WeekView(state: WeekViewState, modifier: Modifier = Modifier): Unit = TODO()

internal fun reconnectLabel(labels: List<String>): String = TODO()
```

- [ ] **Step 2: Write the failing tests**

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekViewTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class WeekViewTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val now = SampleUi.NOW

    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { content() }
        }
    }

    private fun sync(
        agoMinutes: Long = 2,
        needsSignIn: List<String> = emptyList(),
        connections: List<String> = listOf("Google"),
    ) = SyncStatusUi(now - agoMinutes * 60_000, needsSignIn, connections, failingBeforeFirstSync = false)

    private fun state(sync: SyncStatusUi = sync()) = WeekViewState(SampleUi.week, SampleUi.TODAY, sync, now)

    @Test
    fun showsSevenDaysStartingTodayAsThisWeek() {
        show { WeekView(state()) }
        (0L..6L).forEach { compose.onNodeWithTag("week_day_${SampleUi.TODAY.plusDays(it)}").assertExists() }
        compose.onNodeWithText("This week").assertExists()
    }

    @Test
    fun subtitleNamesTheOnlyConnection() {
        show { WeekView(state()) }
        compose.onNodeWithText("Family calendar · synced with Google 2 min ago").assertExists()
    }

    @Test
    fun staleSubtitleStillShowsTheAge() {
        show { WeekView(state(sync(agoMinutes = 45))) }
        compose.onNodeWithText("Family calendar · synced with Google 45 min ago").assertExists()
    }

    @Test
    fun allDayEventsSayAllDay() {
        show { WeekView(state()) }
        compose.onAllNodesWithText("All day").assertCountEquals(2)
    }

    @Test
    fun recurringChipsShowTheRepeatBadge() {
        show { WeekView(state()) }
        // Swimming, Bin day and Piano.
        compose.onAllNodesWithContentDescription("Repeats").assertCountEquals(3)
    }

    @Test
    fun legendNamesEveryoneAndFamily() {
        show { WeekView(state()) }
        listOf("Alex", "Sam", "Mia", "Family").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun reconnectChipOpensSettings() {
        show { WeekView(state(sync(agoMinutes = 0, needsSignIn = listOf("Google")))) }
        compose.onNodeWithText("Google needs reconnecting").assertHeightIsAtLeast(44.dp)
        compose.onNodeWithText("Google needs reconnecting").performClick()
        assertThat(navigator.settingsOpened).isEqualTo(1)
    }

    @Test
    fun severalCalendarsNeedingSignInShareOneChip() {
        val status = sync(needsSignIn = listOf("Google", "School"), connections = listOf("Google", "School"))
        show { WeekView(state(status)) }
        compose.onNodeWithText("2 calendars need reconnecting").assertExists()
        compose.onNodeWithText("Google needs reconnecting").assertDoesNotExist()
    }

    @Test
    fun noReconnectChipWhenEveryConnectionIsHealthy() {
        show { WeekView(state()) }
        compose.onNodeWithText("needs reconnecting", substring = true).assertDoesNotExist()
        compose.onNodeWithText("need reconnecting", substring = true).assertDoesNotExist()
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*WeekViewTest*"`
Expected: FAIL. All 9 tests fail with `NotImplementedError`.

- [ ] **Step 4: Implement the week view and its host**

Replace `ui/WeekView.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi
import uk.co.siland.culvery.capability.calendar.isStaleAt
import uk.co.siland.culvery.capability.calendar.weekSubtitle
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType

private val SHORT_WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.UK)

data class WeekViewState(val week: WeekUi, val today: LocalDate, val sync: SyncStatusUi, val nowMillis: Long)

internal fun reconnectLabel(labels: List<String>): String =
    if (labels.size == 1) "${labels.single()} needs reconnecting" else "${labels.size} calendars need reconnecting"

/**
 * Hand-off §2 and §7: a rolling seven days from today, person-coloured chips and the sync state. There is no week
 * navigation, so the view never leaves the synced window. The legend keeps a 24 dp gap to the right edge, where
 * 2b's Add event button goes.
 */
@Composable
fun WeekView(state: WeekViewState, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(CalendarDimens.headerTrailingGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text("This week", style = CalendarType.weekTitle, color = c.ink)
                Spacer(Modifier.height(CalendarDimens.subtitleTop))
                Text(
                    weekSubtitle(state.sync, state.nowMillis),
                    style = CalendarType.subtitle,
                    color = if (state.sync.isStaleAt(state.nowMillis)) c.danger else c.mute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("week_subtitle"),
                )
                if (state.sync.needsSignIn.isNotEmpty()) {
                    Spacer(Modifier.height(CalendarDimens.reconnectTop))
                    ReconnectChip(reconnectLabel(state.sync.needsSignIn), onClick = navigator::openSettings)
                }
            }
            Legend(state.week.people, Modifier.padding(end = CalendarDimens.headerTrailingGap))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            state.week.days.forEach { day ->
                DayColumn(day, isToday = day.date == state.today, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun DayColumn(day: DayUi, isToday: Boolean, modifier: Modifier) {
    val c = Culvery.colors
    val shape = RoundedCornerShape(22.dp)
    val inset = CalendarDimens.columnHeaderInset
    HhCard(
        modifier = modifier
            .testTag("week_day_${day.date}")
            .then(if (isToday) Modifier.border(2.dp, c.accent, shape) else Modifier),
        radius = 22.dp,
        padding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
    ) {
        Row(Modifier.padding(start = inset, end = inset, bottom = inset)) {
            Text(
                if (isToday) "Today" else day.date.format(SHORT_WEEKDAY),
                style = CalendarType.strong14,
                color = if (isToday) c.accent else c.mute,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(6.dp))
            Text(day.date.dayOfMonth.toString(), style = HhType.dateNumber, color = c.ink, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.height(CalendarDimens.columnGap))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CalendarDimens.columnGap),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(day.events, key = { it.key }) { EventChip(it) }
        }
    }
}

@Composable
private fun EventChip(event: EventUi) {
    val c = Culvery.colors
    val colour = Color(event.person.color)
    val tint = if (c.bg.luminance() < 0.5f) CalendarDimens.CHIP_ALPHA_DARK else CalendarDimens.CHIP_ALPHA_LIGHT
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colour.copy(alpha = tint))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(event.startLabel, style = CalendarType.chipTime, color = colour, maxLines = 1, modifier = Modifier.weight(1f))
            if (event.recurring) {
                HhIcon("repeat", size = CalendarDimens.chipBadge, tint = c.mute, contentDescription = "Repeats")
            }
        }
        Spacer(Modifier.height(CalendarDimens.chipTitleTop))
        Text(event.title, style = CalendarType.chipTitle, color = c.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Legend(people: List<Person>, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(CalendarDimens.legendGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        people.forEach { person ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(CalendarDimens.legendDotGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(CalendarDimens.legendDot).clip(CircleShape).background(Color(person.color)))
                Text(
                    person.name,
                    style = CalendarType.legend,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = CalendarDimens.legendNameMax),
                )
            }
        }
    }
}

/** One chip for every connection that needs signing in again: hand-off `dangerSoft` pill, 44 dp, radius 22. */
@Composable
private fun ReconnectChip(label: String, onClick: () -> Unit) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .height(CalendarDimens.touchTarget)
            .clip(RoundedCornerShape(CalendarDimens.pillRadius))
            .background(c.dangerSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = CalendarDimens.pillPaddingH),
    ) {
        HhIcon("sync_problem", size = CalendarDimens.reconnectIcon, tint = c.danger)
        Text(
            label,
            style = CalendarType.pill,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
```

`Modifier.border` draws after its content, so placing it before `HhCard`'s clip and background gives the hand-off's 2 dp **inset** ring on top of the card surface. `Color.luminance()` tells the themes apart without a dark flag: `bg` is near-black in dark and near-white in light.

Replace `ui/CardHosts.kt` with:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import java.time.LocalDate
import uk.co.siland.culvery.capability.calendar.CalendarRepository
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.capability.calendar.WeekUi

@Composable
internal fun TodayCardHost(repo: CalendarRepository, today: LocalDate) {
    val events: List<EventUi>? by remember(today) { repo.day(today) }.collectAsState(initial = null)
    TodayCard(events)
}

@Composable
internal fun ComingUpCardHost(repo: CalendarRepository, today: LocalDate) {
    val days: List<DayUi>? by remember(today) { repo.days(today.plusDays(1), 3) }.collectAsState(initial = null)
    ComingUpCard(days)
}

/** Today plus six days; [today] moves at midnight, so the week rolls with it. Shows nothing until both flows load. */
@Composable
internal fun WeekViewHost(repo: CalendarRepository, today: LocalDate, nowMillis: Long) {
    val week: WeekUi? by remember(today) { repo.week(today) }.collectAsState(initial = null)
    val sync: SyncStatusUi? by repo.syncStatus.collectAsState(initial = null)
    val w = week ?: return
    val s = sync ?: return
    WeekView(WeekViewState(w, today, s, nowMillis))
}
```

In `CalendarCapability.kt`:

1. Add the imports:
   ```kotlin
   import uk.co.siland.culvery.capability.calendar.ui.WeekViewHost
   import uk.co.siland.culvery.capability.calendar.ui.rememberNowMillis
   import uk.co.siland.culvery.capability.calendar.ui.rememberZoneId
   import uk.co.siland.culvery.capability.calendar.ui.todayIn
   ```
2. Remove the now-unused imports of `Box`, `fillMaxSize` and `Modifier`.
3. Replace the `TabContent` function and its comment with:
   ```kotlin
       @Composable
       override fun TabContent() {
           val now = rememberNowMillis(clock)
           WeekViewHost(repo, today = todayIn(rememberZoneId(zone), now), nowMillis = now)
       }
   ```

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS: `WeekViewTest` (9 tests) and all earlier tests.

- [ ] **Step 6: Write the screenshot test**

Every state is captured in dark; the default state also in light. The stale and needs-sign-in states only change the header, which the dark shots already show.

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/WeekScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.SyncStatusUi
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** The tab content area: 1280 − 108 rail − 2 × 28 padding wide; 800 − 30 status − 24 − 22 high. */
private val CONTENT_W = 1116.dp
private val CONTENT_H = 724.dp

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeekScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val now = SampleUi.NOW

    private fun snap(name: String, dark: Boolean, sync: SyncStatusUi) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.testTag("shot").background(Culvery.colors.bg).size(CONTENT_W, CONTENT_H)) {
                        WeekView(WeekViewState(SampleUi.week, SampleUi.TODAY, sync, now))
                    }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private val fresh = SyncStatusUi(now - 2 * 60_000, emptyList(), listOf("Google"), failingBeforeFirstSync = false)
    private val stale = fresh.copy(lastSyncMillis = now - 45 * 60_000)
    private val needsSignIn = fresh.copy(needsSignIn = listOf("Google"))

    @Test fun weekDark() = snap("week_dark", true, fresh)
    @Test fun weekLight() = snap("week_light", false, fresh)
    @Test fun weekStaleDark() = snap("week_stale_dark", true, stale)
    @Test fun weekNeedsSignInDark() = snap("week_needs_sign_in_dark", true, needsSignIn)
}
```

- [ ] **Step 7: Record and look at every image**

Run: `./gradlew :capability:calendar:recordRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, and 4 new PNGs (12 in total in `capability/calendar/src/test/screenshots/`).

Open each image and compare it with `docs/design/house_hub_handoff/screenshots/calendar-sheets/02-calendar-week-dark.png` (and `02-calendar-week-light.png` for `week_light`).

Header:
- "This week" at 34 sp bold with slightly tight tracking, and "Family calendar · synced with Google 2 min ago" at 15 sp, muted, 4 dp beneath it.
- The legend (Alex, Sam, Mia, Family; 10 dp dots, 14 sp / 500 names) sits on the right, bottom-aligned with the title block, 24 dp in from the right edge. There are no chevrons and no "Today" pill. (The hand-off's green "Add event" button is 2b.)

Columns:
- Seven equal columns with 10 dp gaps, radius 22. The day label sits 4 dp in from the column's sides.
- The first column reads "Today 23", with "Today" in green and a 2 dp green ring inside the edge.
- Chips are tinted in the person's colour (a little stronger in dark than in light), with a 12 sp bold time in that colour and a 14 sp title 2 dp below it. Long titles such as "Dinner with Jo & Priya" and "Sunday lunch at Gran's" wrap to two lines.
- Swimming (Today), Bin day (Fri) and Piano (Sat) show a small muted `repeat` icon at the right of the time.
- Fri starts with a Family "All day" Bin day chip and Sat with an "All day" "INSET day — no school" chip. (The hand-off's `lock` badge on INSET day is 2b.)

Variants:
- Stale: the subtitle is coral (`danger`).
- Needs sign-in: a coral-tinted (`dangerSoft`) "Google needs reconnecting" pill with a coral `sync_problem` icon sits under the subtitle, and the columns start lower.
- The header must not wrap or clip in any variant. If it does, note which variant.

Run: `./gradlew :capability:calendar:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add capability/calendar
git commit -m "Add the calendar week view tab"
```

- [ ] **Step 9: USER CHECKPOINT — send the screenshots for approval. Do not start Task 13 before the user replies.**

Send the user these 12 images from `capability/calendar/src/test/screenshots/`:
- `today_dark.png`, `today_light.png`, `today_empty_dark.png`
- `coming_up_dark.png`, `coming_up_light.png`, `coming_up_busy_dark.png`
- `connect_dark.png`, `connect_light.png`
- `week_dark.png`, `week_light.png`, `week_stale_dark.png`, `week_needs_sign_in_dark.png`

Also send the two hand-off references: `docs/design/house_hub_handoff/screenshots/calendar-sheets/01-home-dark.png` and `02-calendar-week-dark.png`.

With them, name the parts that are the controller's own design, which the hand-off does not specify:
- the Coming up card, including its compact rows (two per day, then "+N more");
- the Connect card;
- the empty Today state;
- the stale subtitle and the reconnect chip under the subtitle.

List the known differences that are **2b work, not defects**, so the user does not flag them:
- the **+** button on the Today card, the **Add event** button on the Calendar header and the faint `add` hints at the foot of each column;
- the `lock` and `cloud_upload` chip badges (only `repeat` is built);
- the design's 2-minute session and status-bar sign-in with "Sign out", against the built 60 s session and rail chip;
- the new PIN pad layout ("Who's this?", 76 dp keys, Cancel as a key, `danger` error).

Also list anything you noted in Task 11 Step 10 or Step 7 above.

Ask: "Do these match what you want? Any changes before I wire them into the app?"

- **If the user asks for changes:** make them, run `./gradlew :capability:calendar:recordRoborazziDebug`, look at the changed images, then run `./gradlew :capability:calendar:testDebugUnitTest :capability:calendar:verifyRoborazziDebug`. Re-send the changed images and commit with a message describing the change. Repeat until the user approves.
- **When approved:** continue to Task 13.

---

### Task 13: Debug seed, app wiring and on-emulator verification

**Files:**
- Create: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSetup.kt`
- Test: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSetupTest.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt`, `app/src/release/java/uk/co/siland/culvery/DebugSeed.kt`, `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`
- Test: `app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt` (create), `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt` (replace)
- Create (recorded): `app/src/test/screenshots/{home_calendar_dark,home_calendar_light}.png`

**Interfaces:**
- Consumes:
  - `CalendarStore.addConnection` and `connectionsNow()`
  - `CalendarProvider.sources`
  - `SourceMapping`
  - `FakeCalendarProvider`: `ID`, `SOURCE_ALEX`, `SOURCE_SAM`, `SOURCE_MIA`, `SOURCE_FAMILY`, `SOURCE_SCHOOL`
  - `CalendarStore.visibleSourcesFor(connectionId)`
  - `EventUi(key, title, timeLabel, startLabel, person, allDay, recurring, startSort)`
  - `HouseholdRepository`: `credentials()`, `addPerson`, `people`
  - `PinManager.setPin(id, pin)` and `identify(pin)`
  - `PinHasher()`
  - `TodayCard`, `ComingUpCard`, `EventUi`, `DayUi`
  - `HomeCardPlacer.place`, `LocalShellNavigator`
  - `startAll`
- Produces:
  - `@Singleton class CalendarSetup @Inject constructor(store: CalendarStore, providers: Set<@JvmSuppressWildcards CalendarProvider>)`:
    - `suspend fun hasConnections(): Boolean`
    - `suspend fun connect(connection: Connection, mapping: Map<String, SourceMapping>)`. It reads the provider's sources and stores everything in one transaction. It throws `IllegalArgumentException` for an unknown provider, and stores nothing in that case.
  - `suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager, calendar: CalendarSetup)`, with the same signature in debug and release. In debug it:
    - adds the people, unless an active Admin already exists:
      - Alex: ADMIN, PIN 1234, `#4CB387`
      - Sam: ADULT, PIN 2468, `#5B9BE0`
      - Mia: CHILD, PIN 1357, `#E07BA8`
    - adds the sample connection `debug-sample`, unless a calendar connection already exists. Its sources map fake-alex/sam/mia to the people with those names, and fake-family and fake-school ("School terms") to Family.
  - `:app` depends on `:capability:calendar` (implementation) and `:provider:calendar-fake` (`debugImplementation`).

- [ ] **Step 1: Stub `CalendarSetup` and write its failing tests**

`capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarSetup.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.Connection

@Singleton
class CalendarSetup @Inject constructor(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
) {
    suspend fun hasConnections(): Boolean = TODO()

    suspend fun connect(connection: Connection, mapping: Map<String, SourceMapping>): Unit = TODO()
}
```

`capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarSetupTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection

@RunWith(AndroidJUnit4::class)
class CalendarSetupTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private val provider = ScriptedProvider(
        "calendar.a",
        sourceList = listOf(CalendarSource("s1", "Alex", writable = false), CalendarSource("s2", "Family", writable = false)),
    )

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun connectStoresTheConnectionWithMappedSources() = runTest {
        val setup = CalendarSetup(store, setOf(provider))
        assertThat(setup.hasConnections()).isFalse()
        setup.connect(Connection("c1", "calendar.a", "A", emptyMap()), mapOf("s1" to SourceMapping(PersonId("alex"), visible = true)))
        assertThat(setup.hasConnections()).isTrue()
        assertThat(store.visibleSourcesFor("c1").associate { it.source.id to it.mapping.person }).containsExactly(
            "s1", PersonId("alex"),
            "s2", PersonId.FAMILY,
        )
    }

    @Test
    fun unknownProviderIsRejectedAndNothingIsStored() = runTest {
        val setup = CalendarSetup(store, setOf(provider))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { setup.connect(Connection("c1", "calendar.missing", "X", emptyMap()), emptyMap()) }
        }
        assertThat(setup.hasConnections()).isFalse()
    }
}
```

Run: `./gradlew :capability:calendar:testDebugUnitTest --tests "*CalendarSetupTest*"`
Expected: FAIL with `NotImplementedError`.

- [ ] **Step 2: Implement `CalendarSetup`**

Replace the class body in `CalendarSetup.kt`:
```kotlin
/** Adds a provider connection with its sources. Used by the debug seed now and by setup/settings in Plan 4. */
@Singleton
class CalendarSetup @Inject constructor(
    private val store: CalendarStore,
    private val providers: Set<@JvmSuppressWildcards CalendarProvider>,
) {
    suspend fun hasConnections(): Boolean = store.connectionsNow().isNotEmpty()

    /** Sources missing from [mapping] show as Family. */
    suspend fun connect(connection: Connection, mapping: Map<String, SourceMapping>) {
        val provider = providers.firstOrNull { it.descriptor.id == connection.providerId }
            ?: throw IllegalArgumentException("No calendar provider ${connection.providerId}")
        store.addConnection(connection, provider.sources(connection), mapping)
    }
}
```

Run: `./gradlew :capability:calendar:testDebugUnitTest`
Expected: PASS (all tests in the module, including 2 in `CalendarSetupTest`).

- [ ] **Step 3: Wire the modules into `:app`**

Replace `app/build.gradle.kts` with:
```kotlin
plugins {
    id("culvery.android.application")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "uk.co.siland.culvery"
    defaultConfig {
        applicationId = "uk.co.siland.culvery"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures.buildConfig = true
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:plugin"))
    implementation(project(":core:household"))
    implementation(project(":core:access"))
    implementation(project(":capability:calendar"))
    // Sample data only; release builds have no calendar provider until Plan 3.
    debugImplementation(project(":provider:calendar-fake"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
```

- [ ] **Step 4: Write the failing seed test**

The test goes in `src/testDebug`, not `src/test`: it uses `FakeCalendarProvider`, which only debug builds have, so a release unit-test compile must never see it.

`app/src/testDebug/java/uk/co/siland/culvery/DebugSeedTest.kt`:
```kotlin
package uk.co.siland.culvery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.CalendarStore
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.core.access.PinHasher
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

@RunWith(AndroidJUnit4::class)
class DebugSeedTest {
    private lateinit var householdDb: HouseholdDatabase
    private lateinit var calendarDb: CalendarDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private lateinit var store: CalendarStore
    private lateinit var setup: CalendarSetup

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        householdDb = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        calendarDb = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).allowMainThreadQueries().build()
        household = HouseholdRepository(householdDb)
        pins = PinManager(household, PinHasher())
        store = CalendarStore(calendarDb)
        setup = CalendarSetup(store, setOf(FakeCalendarProvider()))
    }

    @After
    fun tearDown() {
        householdDb.close()
        calendarDb.close()
    }

    @Test
    fun seedsAlexSamAndMiaWithTheirRolesAndPins() = runTest {
        seedDebugData(household, pins, setup)
        assertThat(household.people.first().map { it.name }).containsExactly("Alex", "Sam", "Mia").inOrder()
        assertThat(pins.identify("1234")?.let { it.person.name to it.role }).isEqualTo("Alex" to Role.ADMIN)
        assertThat(pins.identify("2468")?.let { it.person.name to it.role }).isEqualTo("Sam" to Role.ADULT)
        assertThat(pins.identify("1357")?.let { it.person.name to it.role }).isEqualTo("Mia" to Role.CHILD)
    }

    private suspend fun seededSources() = store.visibleSourcesFor(store.connectionsNow().single().connection.id)

    @Test
    fun sampleSourcesAreMappedToThePeople() = runTest {
        seedDebugData(household, pins, setup)
        val byName = household.people.first().associate { it.name to it.id }
        val mapping = seededSources().associate { it.source.id to it.mapping.person }
        assertThat(mapping).containsExactly(
            FakeCalendarProvider.SOURCE_ALEX, byName.getValue("Alex"),
            FakeCalendarProvider.SOURCE_SAM, byName.getValue("Sam"),
            FakeCalendarProvider.SOURCE_MIA, byName.getValue("Mia"),
            FakeCalendarProvider.SOURCE_FAMILY, PersonId.FAMILY,
            FakeCalendarProvider.SOURCE_SCHOOL, PersonId.FAMILY,
        )
    }

    @Test
    fun seedIsIdempotent() = runTest {
        seedDebugData(household, pins, setup)
        seedDebugData(household, pins, setup)
        assertThat(household.people.first()).hasSize(3)
        assertThat(store.connectionsNow()).hasSize(1)
    }

    @Test
    fun existingActiveAdminIsLeftAlone() = runTest {
        val admin = household.addPerson("Admin", 0xFF4CB387, Role.ADMIN)
        pins.setPin(admin.id, "9999")
        seedDebugData(household, pins, setup)
        assertThat(household.people.first().map { it.name }).containsExactly("Admin")
        assertThat(seededSources().map { it.mapping.person }.toSet()).containsExactly(PersonId.FAMILY)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "*DebugSeedTest*"`
Expected: FAIL to compile: `seedDebugData` has no `calendar` parameter.

- [ ] **Step 5: Rewrite the seeds and inject `CalendarSetup`**

Replace `app/src/debug/java/uk/co/siland/culvery/DebugSeed.kt` with:
```kotlin
package uk.co.siland.culvery

import kotlinx.coroutines.flow.first
import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.capability.calendar.SourceMapping
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Connection
import uk.co.siland.culvery.provider.calendar_fake.FakeCalendarProvider

private class SeedPerson(val name: String, val color: Long, val role: Role, val pin: String, val source: String)

private val SEED_PEOPLE = listOf(
    SeedPerson("Alex", 0xFF4CB387, Role.ADMIN, "1234", FakeCalendarProvider.SOURCE_ALEX),
    SeedPerson("Sam", 0xFF5B9BE0, Role.ADULT, "2468", FakeCalendarProvider.SOURCE_SAM),
    SeedPerson("Mia", 0xFFE07BA8, Role.CHILD, "1357", FakeCalendarProvider.SOURCE_MIA),
)

private const val DEBUG_CONNECTION_ID = "debug-sample"

/**
 * Debug builds only: the hand-off's family and a sample calendar, so the app is usable before the setup wizard
 * exists. Guarded on an active Admin (not an empty household) so it never adds a second set of people.
 */
suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager, calendar: CalendarSetup) {
    if (household.credentials().none { it.isActiveAdmin }) {
        SEED_PEOPLE.forEach { p ->
            val person = household.addPerson(p.name, p.color, p.role)
            pins.setPin(person.id, p.pin)
        }
    }
    if (!calendar.hasConnections()) {
        val byName = household.people.first().associate { it.name to it.id }
        val mapping = SEED_PEOPLE.associate { p ->
            p.source to SourceMapping(byName[p.name] ?: PersonId.FAMILY, visible = true)
        } + mapOf(
            FakeCalendarProvider.SOURCE_FAMILY to SourceMapping(PersonId.FAMILY, visible = true),
            FakeCalendarProvider.SOURCE_SCHOOL to SourceMapping(PersonId.FAMILY, visible = true),
        )
        calendar.connect(Connection(DEBUG_CONNECTION_ID, FakeCalendarProvider.ID, "Sample calendar", emptyMap()), mapping)
    }
}
```

Replace `app/src/release/java/uk/co/siland/culvery/DebugSeed.kt` with:
```kotlin
package uk.co.siland.culvery

import uk.co.siland.culvery.capability.calendar.CalendarSetup
import uk.co.siland.culvery.core.access.PinManager
import uk.co.siland.culvery.core.household.HouseholdRepository

@Suppress("UNUSED_PARAMETER")
suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager, calendar: CalendarSetup) = Unit
```

In `app/src/main/java/uk/co/siland/culvery/CulveryApp.kt`:

1. Add the import:
   ```kotlin
   import uk.co.siland.culvery.capability.calendar.CalendarSetup
   ```
2. Add the field below `pins`:
   ```kotlin
       @Inject lateinit var calendarSetup: CalendarSetup
   ```
3. Change the seed call to:
   ```kotlin
           appScope.launch { seedDebugData(household, pins, calendarSetup) }
   ```

`startAll(startables)` stays before the seed. The sync loop reacts to the seeded connection as soon as it is committed.

- [ ] **Step 6: Run to verify the seed tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*DebugSeedTest*"`
Expected: PASS (4 tests).

- [ ] **Step 7: Add the Home-with-calendar screenshots**

Replace `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt` with:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.DayUi
import uk.co.siland.culvery.capability.calendar.EventUi
import uk.co.siland.culvery.capability.calendar.ui.ComingUpCard
import uk.co.siland.culvery.capability.calendar.ui.TodayCard
import uk.co.siland.culvery.core.access.ui.PinPadSheet
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.ShellNavigator
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.shell.SessionChip
import uk.co.siland.culvery.shell.ShellUiState
import uk.co.siland.culvery.shell.TabItem

private object NoNavigation : ShellNavigator {
    override fun openTab(id: String) = Unit
    override fun openSettings() = Unit
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val at = LocalDateTime.of(2026, 9, 23, 11, 54)

    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val sam = Person(PersonId("sam"), "Sam", 0xFF5B9BE0)
    private val mia = Person(PersonId("mia"), "Mia", 0xFFE07BA8)

    private fun event(title: String, time: String, person: Person, recurring: Boolean = false) =
        EventUi(title, title, time, time.substringBefore('–'), person, allDay = false, recurring = recurring, startSort = 0)

    private fun allDay(title: String, person: Person, recurring: Boolean = false) =
        EventUi(title, title, "All day", "All day", person, allDay = true, recurring = recurring, startSort = 0)

    private val today = listOf(
        event("School run", "07:45–08:30", sam),
        event("Boiler service", "10:00–11:00", Person.Family),
        event("Plumber quote call", "13:00–13:30", Person.Family),
        event("Swimming", "16:00–17:00", mia, recurring = true),
        event("Dinner with Jo & Priya", "19:30–21:00", alex),
    )

    private val comingUp = listOf(
        DayUi(LocalDate.of(2026, 9, 24), listOf(event("Office day", "09:00–17:00", alex), event("Football", "18:00–19:00", mia))),
        DayUi(
            LocalDate.of(2026, 9, 25),
            listOf(allDay("Bin day", Person.Family, recurring = true), event("Dentist", "12:30–13:30", sam)),
        ),
        DayUi(
            LocalDate.of(2026, 9, 26),
            listOf(
                allDay("INSET day — no school", Person.Family),
                event("Piano", "15:30–16:30", mia, recurring = true),
                event("Book club", "20:00–22:00", sam),
            ),
        ),
    )

    private fun calendarHome(dark: Boolean) = ShellUiState(
        now = at,
        dark = dark,
        tabs = listOf(TabItem("calendar", "Calendar", "calendar_month")),
        homeCards = HomeCardPlacer.place(
            listOf(
                HomeCard("calendar.today", HomeCardSize.TALL, 100) { TodayCard(today) },
                HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) { ComingUpCard(comingUp) },
            ),
        ),
    )

    private fun snap(
        name: String,
        dark: Boolean,
        state: ShellUiState = ShellUiState(now = at, dark = dark),
        overlay: @Composable () -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides NoNavigation) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.fillMaxSize()) {
                        CulveryShell(
                            state = state,
                            onSelectTab = {},
                            onOpenSettings = {},
                            onLockSession = {},
                            onToggleThemePreview = {},
                            tabContent = {},
                        )
                        overlay()
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun homeEmptyDark() = snap("home_empty_dark", dark = true)

    @Test
    fun homeEmptyLight() = snap("home_empty_light", dark = false)

    @Test
    fun homeWithSessionDark() = snap(
        "home_session_dark",
        dark = true,
        state = ShellUiState(now = at, dark = true, session = SessionChip("Admin", 0xFF4CB387)),
    )

    @Test
    fun homeWithCalendarDark() = snap("home_calendar_dark", dark = true, state = calendarHome(dark = true))

    @Test
    fun homeWithCalendarLight() = snap("home_calendar_light", dark = false, state = calendarHome(dark = false))

    @Test
    fun settingsDark() = snap("settings_dark", dark = true) {
        SettingsPlaceholder(onExitKiosk = {}, onClose = {})
    }

    @Test
    fun pinPadDark() = snap("pin_pad_dark", dark = true) {
        PinPadSheet("Change settings", error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }

    @Test
    fun pinPadLight() = snap("pin_pad_light", dark = false) {
        PinPadSheet("Change settings", error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {})
    }
}
```

- [ ] **Step 8: Check the old images did not change, then record the two new ones**

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: the six existing screenshots match their baselines. The only failures are `homeWithCalendarDark` and `homeWithCalendarLight`, which have no baseline yet (Roborazzi reports the missing reference). Any other failure means the shell changed by accident: run `./gradlew :app:compareRoborazziDebug`, look at the `*_compare.png` files under `app/build/outputs/roborazzi/`, and investigate before continuing. Do not re-record the old baselines.

Record only the two new images:
Run: `./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest.homeWithCalendar*"`
Expected: `BUILD SUCCESSFUL`, and `home_calendar_dark.png` and `home_calendar_light.png` in `app/src/test/screenshots/`.

Run: `./gradlew :app:verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL` (all eight).

Open both new images next to `docs/design/house_hub_handoff/screenshots/calendar-sheets/01-home-dark.png`:
- The rail shows Home and Calendar.
- The Today card fills column 1 across both rows.
- Coming up fills row 1 of columns 2–3.
- Row 2 of columns 2–3 is empty. Weather arrives in Plan 4.

- [ ] **Step 9: Build both variants and run everything**

Run: `./gradlew :app:assembleDebug :app:assembleRelease testDebugUnitTest verifyRoborazziDebug`

Expected: `BUILD SUCCESSFUL`. This is the first build that compiles the Hilt graph with the calendar bindings. The release build has an empty `Set<CalendarProvider>`, which is valid through `@Multibinds`, and no fake provider.

If Hilt reports a missing binding, the message names it. Every binding this plan needs is declared in:
- `AppModule`: `WallClock`, `@ApplicationScope`, `Set<Startable>`
- `CalendarModule`: the database, `Set<CalendarProvider>`, `Capability`, `Startable`
- `FakeCalendarModule`: the fake provider

- [ ] **Step 10: Verify on the emulator**

1. Start the emulator **in the background**, or the shell blocks until the emulator exits. In Git Bash the trailing `&` does it; with an agent's shell tool, also set its run-in-background option.
   ```bash
   "$LOCALAPPDATA/Android/Sdk/emulator/emulator" -avd Culvery_Tablet_API_30 -no-snapshot-save > /dev/null 2>&1 &
   ```
2. Wait for it to boot:
   ```bash
   adb wait-for-device && adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
   ```
3. Install, clear old data and start the app:
   ```bash
   ./gradlew :app:installDebug
   adb shell pm clear uk.co.siland.culvery
   adb shell am start -n uk.co.siland.culvery/.MainActivity
   ```

`pm clear` removes any Plan 1 "Admin". The new seed runs only when there is no active Admin, so without the clear there would be no Alex, Sam or Mia.

If `adb` is not on PATH, use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb"`.

Take a screenshot for your report:
```bash
adb exec-out screencap -p > "$TMP/culvery-home.png"
```

Check each item and report any that fail:
1. Within a few seconds of launch, the rail shows **Home** and **Calendar**. The loop syncs as soon as the seed adds the connection; it does not wait 5 minutes.
2. Today card: School run 07:45–08:30 · Sam, Boiler service 10:00–11:00 · Family, Plumber quote call 13:00–13:30 · Family, Swimming 16:00–17:00 · Mia (with a `repeat` icon), Dinner with Jo & Priya 19:30–21:00 · Alex. Each has its person's colour bar. The "Week" pill is a full 44 dp `surf2` pill.
3. Coming up card: Tomorrow (Office day, Football), then two weekday columns (Bin day "All day" and Dentist; INSET day — no school and Piano, then "+1 more"). The header link is a plain muted "Week ›".
4. Tap **Week** on the Today card. The Calendar tab opens, showing:
   - "This week" and "Family calendar · synced with Sample calendar just now";
   - no chevrons and no "Today" pill;
   - seven columns starting today, with today's column ringed;
   - Bin day "All day" in column 3 and INSET day "All day" in column 4;
   - a `repeat` icon on Swimming, Bin day and Piano.
5. Tap **Home** in the rail, then **Settings**, and enter `1234`. Settings opens and the rail chip reads "**Alex**" in full.
6. Lock, then try Settings with `2468` (Sam). The pad says "Sam can't do that".
7. Compare the Home header gap and the grid position with `docs/design/house_hub_handoff/screenshots/calendar-sheets/01-home-dark.png`. The date now sits close under the clock. Report the gap you see.
8. Leave the app open for 6 minutes, then open the Calendar tab. The subtitle reads "synced with Sample calendar just now" or "… 1 min ago", never more than 5 min ago. This confirms the loop is running.

If the emulator cannot start, say so and report Step 9 as the gate.

- [ ] **Step 11: Commit**

```bash
git add capability/calendar app
git commit -m "Wire the calendar into the app with a debug family and sample calendar"
```

---

### Task 14: README — modules, debug family, screenshots, "Adding a calendar provider"

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: the names above:
  - `CalendarProvider`, `CalendarProviderContractTest`, `ProviderDescriptor`, `Feature`
  - `SyncResult.fullReplace`, `NeedsSignInException`, `UnreachableException`
  - `Startable`, `LocalShellNavigator`
  - `ModuleBoundaries`
- Produces: documentation only.

- [ ] **Step 1: Update the README**

Make these edits to `README.md`:

1. **Build and run.** Replace the paragraph starting "Debug builds create an **Admin**" with:
```markdown
Debug builds seed a sample household on first launch so the app is usable before the setup wizard exists: **Alex** (Admin, PIN 1234), **Sam** (Adult, PIN 2468) and **Mia** (Child, PIN 1357), plus a "Sample calendar" connection showing the design hand-off's week. The seed only runs when there is no Admin with a PIN, so after upgrading from an older debug build clear the app's data (`adb shell pm clear uk.co.siland.culvery`). Release builds seed nothing and include no sample calendar.
```

2. **Screenshots.** Add this section after "Build and run":
````markdown
## Screenshot tests

Compose screens are checked against PNG baselines with [Roborazzi](https://github.com/takahirom/roborazzi) under Robolectric at the tablet's 1280×800 dp.

```bash
./gradlew testDebugUnitTest verifyRoborazziDebug   # the standard gate: unit tests, then compare against the baselines
./gradlew compareRoborazziDebug                    # write *_compare.png diffs to <module>/build/outputs/roborazzi/
./gradlew recordRoborazziDebug                     # accept the current rendering as the new baselines
```

Baselines live in `<module>/src/test/screenshots/` and are committed. Re-record only after looking at the diff. The baselines are recorded on Windows: Robolectric's native graphics render slightly differently on other operating systems, so record and verify on Windows. Don't rely on `./gradlew check`: it also runs release unit tests, which cannot run Compose tests.
````

3. **Modules.** Replace the "Modules" section (the table and the "Rules:" paragraph) with:
```markdown
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
```

4. **Adding a capability.** Replace step 2 of "Adding a capability" with:
```markdown
2. Implement `Capability` (tab, icon, `order`, `hasTab`, Home `cards()`). Card and tab UI move the shell through `LocalShellNavigator.current.openTab(id)` / `.openSettings()`. Background work (sync loops) is a `Startable` bound `@IntoSet`; the app starts it once at launch.
```

5. **Adding a calendar provider.** Add this section after "Adding a capability":
````markdown
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
````

- [ ] **Step 2: Final check**

Run: `./gradlew assembleDebug testDebugUnitTest verifyRoborazziDebug`, then `./gradlew -p build-logic :convention:test`
Expected: `BUILD SUCCESSFUL` for both.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "Document the calendar modules, screenshot tests and adding a calendar provider"
```
