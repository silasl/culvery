# Culvery — Plan 4b: Weather Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (chosen: a sonnet implementer per task, then a reviewer per task; tasks marked **Review: opus** get an opus reviewer) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Once a home location is set, Home's header shows the weather now and a Forecast card shows today and the next two days, from Open-Meteo every 30 minutes and from the cache when offline; the clock, the date and the theme run in the household's time zone, and the theme turns dark at that day's sunset and light at sunrise.

**Architecture:** A new `:capability:weather` owns the `WeatherProvider` contract, `weather.db` (one fetch, replaced whole, recorded with the place it was fetched for), the fetch loop, the pure rules for what to show (`weatherView`), the header item and the Forecast card. `:provider:weather-openmeteo` adds the forecast call beside its town search. `:core:plugin` gains two generic seams, `Capability.headerItems()` and `Daylight`, plus one wall-time ticker; `HouseholdZone` moves to `:core:household`, so the shell's clock and theme use the household's zone. `:app` only collects header items, takes `Daylight` as optional, and never names the weather module (D6).

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00), Hilt 2.57.1 (KSP), Room 2.8.5, Coroutines 1.10.2, OkHttp 4.12.0 + MockWebServer 4.12.0, kotlinx.serialization 1.9.0, JUnit4 + Robolectric 4.16 + Truth + Turbine, Roborazzi 1.46.1. No new dependency.

**Spec:** `docs/superpowers/specs/2026-10-01-culvery-4b-weather-design.md` (binding). Parent spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§4 module rules, §7 storage and sync, §9.1–9.2 shell and Home).
**Previous plan (format, constraints):** `docs/superpowers/plans/2026-09-29-culvery-04a-setup-settings.md`. **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (spec §8: 4b takes the theme in the household zone with sun times, and `HomeCardPlacer`'s mixed-size tests).

**Plan series:** 1 Foundation (done) · 2a · 2b-1 · 2b-2 · 3a · 4a (done) · **4b Weather (this plan)** · 4c Release and the on-device pass.

**Task order and why:** the move and the seams first (nothing else compiles without them), then the weather module from its pure rules outwards (rules, storage, provider, loop, repository, screens, wiring), then the shell, then the docs and the walkthrough. Every task ends green.

| # | Task | Review |
|---|---|---|
| 1 | Move `HouseholdZone` to `:core:household` (D6, §3.3) | sonnet |
| 2 | `:core:plugin` seams: `HeaderItem`, `headerItems()`, `Daylight`, `wallTimeEachMinute`, `rememberNowMillis` (moved from the calendar); the Forecast's placement (§3.2, §8) | sonnet |
| 3 | `:capability:weather`: module, contract, `weatherView` and the words, boundary tests (§3.1, §3.7, §4) | sonnet |
| 4 | `weather.db` and `WeatherStore`: atomic replace, matching (§3.5) | **opus** (state) |
| 5 | The Open-Meteo forecast (§3.4) | **opus** (parsing, logs) |
| 6 | `WeatherFetcher` and `WeatherSyncLoop` (§3.6) | **opus** (state, loop) |
| 7 | `WeatherRepository`: the view, the header, `Daylight` (§3.7) | **opus** (state) |
| 8 | The Forecast card and the header item, Roborazzi (§4) | sonnet |
| 9 | `WeatherCapability` and its Hilt bindings (§3.1) | sonnet |
| 10 | The shell: header items, the household-zone ticker, the sun-times theme (§3.8) | **opus** (theme, zone) |
| 11 | The walkthrough, the README privacy note and the follow-ups (§6, §7, §8) | sonnet |

## Plan review (2026-10-01)

The user's review of this plan ("all as recommended") is applied in the tasks below:
1. The household-zone ticker retries a failed zone read, so a Room failure can't crash the shell (Task 10, `HouseholdTickerTest.aFailedZoneReadIsRetriedAndTheClockCarriesOn`).
2. The forecast's client has a 60 s `callTimeout`, so a dripping body ends (Task 5, `aDrippingBodyEndsAsWeatherUnavailable`); the Google client's missing `callTimeout` is a 4c follow-up (Task 11).
3. The loop wakes only when the place really changes, so a flaky location read can't fetch every second (Task 6, `aFlakyLocationReadFetchesOnce`).
4. Review Focus 2 is now "a provider that repeats an hour can't break every fetch"; the clock-change hour offset is a known, untested gap left for a follow-up (ruling 2).
5. Tests that repeated another layer's are cut (matching, sun times, the age line, the zone): each rule is tested once where it lives, plus one test where it is wired.
6. No new minute tick: the calendar's `rememberNowMillis` moves to `:core:plugin` (Task 2) and the Forecast card uses it (Task 8).
7. The two new placer tests that repeated `mixedSizesFillTallThenWideThenRegularBesideIt` are dropped (Connect and Coming up never show together); one new case stays.
8. `CapabilityDefaultsTest` is dropped.
9. The rulings below say which are pinned by a test.
10. `zoneOrDevice(id)` in `:core:household` is the one zone parse (Tasks 1, 3).
11. The provider's day count is `DAYS_FETCHED`; `WeatherCapability`'s ids and orders are private.
12. The Home card radius is a `:core:ui` token, `ShellTokens.homeCardRadius`; the calendar's `CalendarDimens.cardRadius` aliases it (Task 8).
13. The forecast refuses an answer over 1 MiB and drops days and hours outside the seven it asked for (Task 5).
14. `WeatherFetcher` isn't a singleton; two comments that restated their code are cut.
15. A cold start shows the device's zone for a moment before Room answers: accepted, noted for 4c (Task 11).
Questions: the clock-change offset stays a follow-up (Task 11, with the fix); `:app`'s tests may import the weather UI (ruling 12); the card's icons are sun amber, provisional, on the end-of-v1 design review list.

## Rulings against the code

Where the spec is silent, ambiguous or doesn't fit the code or the real API, this plan rules as follows. Pinned by a test in the task named: 1, 3, 4, 5, 7, 8, 13. Not pinned by a test: 2 (a known gap), 6 (a design choice that no unit test can catch going wrong), 9 and 10 (checked by eye in the screenshots), 11 (docs), 12 (`AppSourceTest` pins only the main-code half), 14 and 15 (read in review).

1. **Open-Meteo never sends a null sunrise or sunset (Task 5).** Checked against the live API: on a polar night both are the day's `T00:00`; on a polar day sunrise is the day's `T00:00` and sunset the *next* day's `T00:00`. The provider keeps both times only when both fall on the day's own date with sunrise before sunset (or JSON gives a real `null`); otherwise both are null (§3.1's "null on a polar day or night"). Both or neither: `Daylight` needs both.
2. **Open-Meteo uses one UTC offset for a whole answer (Task 5, open question to the user).** Checked against the live API (`utc_offset_seconds` is one value; London's 26 October 2025 comes back as 24 hours, with sun times an hour late after the change). Hours therefore never repeat or skip in practice, but a fetch made before a clock change is an hour out for the days after it, until the next fetch (30 minutes, or the next fetch once back online). This is a known gap, untested and left for a follow-up (Task 11: ask in UTC with `timeformat=unixtime` and convert in the household's zone); correcting it is not in the spec. Separately, the store keeps one row per date and per hour start, so a provider that repeats an hour can't break every fetch (Review Focus 2).
3. **The 2 h age rule is the pure `updatedAgo(fetchedAtMillis, nowMillis)` beside `weatherView` (Task 3).** `Ready` keeps the spec's four fields; §7's "2 h age boundary" test is `WeatherWordsTest.theAgeLineAppearsOnlyPastTwoHours`.
4. **The header needs the current time, not just the stored hour (Tasks 3, 9).** Night icons depend on now against today's sunset, inside an hour. `headerWeather(view, now): HeaderWeather?` holds the rule, and `headerItems()` emits the weather item only while it is shown, so the shell never draws a divider beside an empty item (§4.1).
5. **"Now" is a flow, not only a composable tick (Tasks 2, 7, 8).** The header item's presence is a `Flow` (§3.2), so §3.7's minute tick is `wallTimeEachMinute(zones, clock)` in `:core:plugin`: the shell's `MinuteTicker`, `WeatherRepository.view`/`header` and `Daylight.today` all use it with `HouseholdZone.zone`. The card's age line uses the calendar's existing composable tick, `rememberNowMillis`, moved to `:core:plugin` (Task 2).
6. **The store is read as one snapshot (Task 4).** Room flows over three tables could pair a new `fetch` row with old `day` rows for a moment, which would show the old town's days under the new place. The store watches the `fetch` row (every replace rewrites it) and reads all three tables in one transaction.
7. **The fetch records the place it asked for, not the place now (Task 6).** `WeatherFetcher.fetch(place)` stores under the `WeatherPlace` it fetched for, so a fetch that finishes after a move is stored under the old coordinates and reads as absent (Review Focus 1); a location change mid-fetch runs another fetch straight after.
8. **A zone id that doesn't parse (Tasks 1, 3).** Weather asks in the device zone through the same `zoneOrDevice` as `HouseholdZone`, but stores under the location's own id string so the data still matches.
9. **Sun amber is `SunAmber` in `:core:ui`'s `Colors.kt`, not an `HhColors` member (Task 8).** It is the same in both themes, like `ShellTokens`' scrims; the hand-off gives one value.
10. **Copy and layout the spec doesn't give (Tasks 3, 8, 10):** every state of the card shows the title "Forecast"; TalkBack condition words are "clear", "partly cloudy", "cloudy", "fog", "drizzle", "rain", "showers", "snow", "thunderstorms"; the first row is spoken "Today, …"; the card's icons are sun amber like the header's; the header items sit 4 dp above the date's bottom, 28 dp apart, with a 1 × 48 dp divider (the hand-off's `padding-bottom: 4px`, `gap: 28px`, `height: 48px`).
11. **The README has no privacy note yet (Task 11).** §6's "The README's privacy note says so" becomes a new **Privacy** section.
12. **`:app`'s test sources may import the weather card and header item for the whole-Home screenshot (Task 10)**, as they already import the calendar's cards; `:app`'s main, debug and release code never names `:capability:weather` (`AppSourceTest`).
13. **`HomeCardPlacer`'s tests live in `:core:plugin` (Task 2)**, beside the placer, not in the shell task. The spec's Today + Coming up + Forecast case is the existing `mixedSizesFillTallThenWideThenRegularBesideIt`; Connect and Coming up never show together (`CalendarCapability.cards()`), so the new case is Connect + Forecast.
14. **A pass with no provider bound counts as done** (Task 6): the loop waits 30 minutes, not 5, as there is nothing to retry.
15. **`WeatherUnavailableException` is thrown without a cause by the provider (Task 5)**, as 4a's `LocationSearchException`: an `IOException`'s text can hold the URL, which holds the coordinates.

Nothing in this plan needs a deprecated API.

## Global Constraints

- Package root `uk.co.siland.culvery`. New packages: `uk.co.siland.culvery.capability.weather` (with `.db`, `.di`, `.ui`).
- `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Pinned versions as in `gradle/libs.versions.toml`; **no new dependency**. Never change a version.
- **Deprecated APIs:** use none without asking the user first. If any API this plan uses shows a deprecation warning, **stop and ask**. Watch: OkHttp 4's Java-style accessors (use `response.code`, `response.body`, `requestUrl`, `toHttpUrl()`); `CancellableContinuation.resume(value, onCancellation)` (`Call.await` uses `kotlin.coroutines.resume`); Material 3 `Divider` (the header's divider is a `Box`); `androidx.compose.ui.platform.LocalLifecycleOwner` (don't use). `@OptIn` to an experimental API only for `ExperimentalCoroutinesApi` in tests. **No `flatMapLatest` or `debounce`** in main code (experimental / preview): use `combine`.
- Design canvas 1280×800 dp. The hand-off is authoritative, then the spec's §4.
- **°C only (D4).** Stored and shown in °C; temperatures shown with `degrees()` (whole degrees, half up, never "-0°").
- **Layout numbers live in `WeatherDimens`** (`:capability:weather`), never inline in weather UI. The shell's new header numbers are named `private val`s in `HomeScreen.kt` beside `CLOCK_TO_DATE_BASELINES`, as that file does. Timing values are named constants.
- Colours come only from `Culvery.colors` and `SunAmber`. Text styles come from `HhType` (`headerValue`, `secondary`, `cardTitle`, `rowTitle`, `body`).
- **Copy (spec §4), exactly:**
  - Header: "{t}°" (e.g. "17°"); "High {h}° · Low {l}°".
  - Card: "Forecast"; "Today"; short day names ("Fri", "Sat"); "{h}°", "{l}°"; "Updated {n} h ago" (whole hours, up to 23 h); "Updated 1 day ago" / "Updated {n} days ago"; "Getting the forecast…"; "No forecast — check the tablet's Wi-Fi."; "Add your home location to see the weather."; "Open settings".
  - TalkBack, one per row: "{Day}, {condition}, high {h}°, low {l}°" (e.g. "Friday, partly cloudy, high 17°, low 10°"; the first row "Today, …").
- **Privacy in logs:** no coordinates, place names, zone ids, PINs or emails in any log. Log an exception's class name, never its message or the exception itself, wherever the message could hold one of these (all weather and Open-Meteo code). Every task with a failure path has a test that reads `ShadowLog`.
- **Storage:** `weather.db` v1 is a cache (§3.5). Room exports its schema to `capability/weather/schemas/`; commit `1.json`. A later schema bump ships a hand-written `Migration`, as the calendar's do. A capability never edits another module's schema.
- Module rules (`build-logic`'s `ModuleBoundaries`, unchanged):
  - `:capability:weather` depends on `:core:plugin`, `:core:household`, `:core:ui` only; never a provider, not even in tests.
  - `:provider:weather-openmeteo` depends on `:core:*` and `:capability:weather`.
  - `:app` depends on `:capability:weather` for Hilt alone; its main, debug and release code never imports it (D6).
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test` or `check`, except the JVM-only `build-logic` build: `./gradlew -p build-logic :convention:test`. A task runs its module's `testDebugUnitTest` while working; every task ends with the full gate before its commit.
- Screenshots: baselines in `<module>/src/test/screenshots/`, recorded and verified on Windows; record only the new images with `./gradlew <module>:recordRoborazziDebug --tests "<pattern>"`; look at every new image before committing (the step says what each must show); `@GraphicsMode(GraphicsMode.Mode.NATIVE)` only on classes that capture screenshots or measure text.
- Tests and threads (as 4a): Room runs on its own threads; wait for it in bounded real time (`withContext(Dispatchers.Default) { withTimeout(5_000) { … } }`) where a flow may not have caught up; asynchronous UI outcomes use `compose.waitUntil(5_000) { … }`; no fixed sleeps. A test that changes the JVM's default zone restores it in `@After`.
- Fixtures use London, Leeds, Wellington or other public places, never the household's own.
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on the current branch; never push. **Never** `git checkout -- .` or `git restore .`; to undo your own change, restore only the files you changed, after `git diff --name-only`.

## Review Focus

The five failures most likely to reach a household that no spec test pins, each pinned by the tests named in its owning task:

1. **A location change mid-fetch writes the old town's forecast after the move.** Expected: the old town's weather never shows; the new town is fetched straight after.
   - Task 6 `WeatherSyncLoopTest.aLocationChangeMidFetchFetchesTheNewPlaceRightAfter`; `WeatherFetcherTest.itStoresTheForecastUnderThePlaceItAskedFor`
   - Task 3 `WeatherViewTest.matchingComparesTheCoordinatesAndTheZoneNotTheName`, `theOldTownsWeatherIsWaitingAfterAMove`
   - Task 7 `WeatherRepositoryTest.aMoveHidesTheOldTownsWeatherAtOnce`
2. **A provider that repeats an hour can't break every fetch.** Expected: the repeated hour is stored once and the fetch is kept, rather than every replace failing on the hour's key.
   - Task 4 `WeatherStoreTest.aRepeatedHourIsStoredOnce`
   - Known gap, untested (ruling 2): Open-Meteo's single offset leaves data fetched before a clock change an hour out until the next fetch; a follow-up (Task 11).
3. **Midnight passes while the tablet is offline.** Expected: the card and header move to the new day from the cache, the theme uses the new day's sun times, and data that has run out says to check the Wi-Fi.
   - Task 7 `WeatherRepositoryTest.atMidnightTheCardMovesOnWithoutAFetch`, `daylightMovesToTomorrowsTimesAtMidnight`
   - Task 3 `WeatherViewTest.midnightMovesTodayOnAndDropsADay`
4. **Open-Meteo returns null entries in an array (a model's horizon) or polar sun times.** Expected: that day or hour is dropped, never "null°" or a crash; polar days have no sun times and the theme falls back.
   - Task 5 `OpenMeteoForecastTest.nullEntriesDropThatDayOrHourAndNothingElse`, `aPolarDayOrNightHasNoSunTimes`
5. **The tablet's own zone differs from the household's.** Expected: the clock, the date, the theme, the card's "today" and the forecast's times all follow the household's zone.
   - Task 10 `HouseholdTickerTest.theClockShowsTheHouseholdsTimeNotTheDevices` (the shell's clock, date and theme; the weather's "today" uses the same `wallTimeEachMinute` over `HouseholdZone.zone`)
   - Task 6 `WeatherFetcherTest.itAsksInTheHouseholdZoneNotTheDevices`
   - Task 3 `WeatherViewTest.anInvalidZoneIdAsksInTheDeviceZone`

The spec's own review focus (§9), each pinned:
- Old town never shown: Review Focus 1 above, and Task 3 `WeatherViewTest.theOldTownsWeatherIsWaitingAfterAMove`.
- The theme follows the household zone and today's sun times, and falls back cleanly: Task 10 `ShellViewModelTest.theThemeTurnsDarkAtTodaysSunsetAndLightAtSunrise`, `withoutDaylightTheThemeUsesSevenAndSeven`, `untilTheSunTimesAreKnownTheThemeUsesSevenAndSevenThenFollowsThem`; Task 10 `HouseholdTickerTest.aFailedZoneReadIsRetriedAndTheClockCarriesOn`; Task 7 `WeatherRepositoryTest.daylightMovesToTomorrowsTimesAtMidnight`; Task 3 `WeatherViewTest.oldMatchingDataStillGivesTodaysSunTimes`, `sunTimesNeedMatchingDataForToday`, `aMissingSunriseOrSunsetGivesNoSunTimes`.
- The loop can't stop for good and can't spin: Task 6 `WeatherSyncLoopTest.repeatedFailuresTryAtMostOnceEveryFiveMinutes`, `aFlakyLocationReadFetchesOnce`, `anErrorIsLoggedByTypeAndTheLoopGoesOn`, `aStrayCancellationDoesNotStopTheLoop`; Task 5 `aDrippingBodyEndsAsWeatherUnavailable`.
- No coordinates, place name or zone in a log: Task 5 `nothingLoggedHoldsTheCoordinatesOrTheZone`; Task 6 `WeatherSyncLoopTest.nothingLoggedHoldsThePlace`.
- `:app` never imports `:capability:weather`; the boundary test covers it: Task 3 `ModuleBoundariesTest.theWeatherModulesFollowTheRules`; Task 10 `AppSourceTest.theAppsOwnCodeNeverNamesTheWeatherCapability`.

---

## File Structure

```
settings.gradle.kts                                 (modify, Task 3: include :capability:weather)
build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt   (modify, Task 3)

core/household/src/main/java/uk/co/siland/culvery/core/household/HouseholdZone.kt   (moved in, Task 1)
core/household/src/test/java/uk/co/siland/culvery/core/household/HouseholdZoneTest.kt (moved in, Task 1)
capability/calendar/…  (Task 1: imports only; HouseholdZone.kt and HouseholdZoneTest.kt leave)
app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt, SampleRollbackTest.kt   (Task 1: imports)

core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/
  HeaderItem.kt (create), Daylight.kt (create), Capability.kt (modify), Runtime.kt (modify: wallTimeEachMinute)
core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/
  WallTimeTest.kt (create), HomeCardPlacerTest.kt (modify)
capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Now.kt, CalendarCapability.kt, ui/ReviewCalendars.kt;
  tests ui/NowTest.kt, ui/CardHostsMidnightRolloverTest.kt   (Task 2: rememberNowMillis moves to :core:plugin)
core/ui/src/main/java/uk/co/siland/culvery/core/ui/Shell.kt   (Task 8: ShellTokens.homeCardRadius)
capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt   (Task 8: cardRadius aliases it)

core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt   (modify, Task 8: SunAmber)

capability/weather/                                 (create)
  build.gradle.kts
  schemas/uk.co.siland.culvery.capability.weather.db.WeatherDatabase/1.json   (generated, committed)
  src/main/java/uk/co/siland/culvery/capability/weather/
    WeatherContract.kt   WeatherProvider, Forecast, DailyWeather, HourlyWeather, Condition, WeatherUnavailableException
    WeatherView.kt       WeatherPlace, StoredWeather, matching, WeatherView, weatherView, sunTimesOn, HeaderWeather, headerWeather
    WeatherWords.kt      the copy, degrees, highLow, updatedAgo, weatherIcon, conditionWords, dayLabel, rowDescription
    db/WeatherDatabase.kt, WeatherStore.kt                       (Task 4)
    WeatherFetcher.kt, WeatherSyncLoop.kt                        (Task 6)
    WeatherRepository.kt                                         (Task 7)
    ui/WeatherDimens.kt, ui/ForecastCard.kt, ui/WeatherHeaderItem.kt   (Task 8)
    WeatherCapability.kt, di/WeatherModule.kt                    (Task 9)
  src/test/resources/robolectric.properties
  src/test/java/uk/co/siland/culvery/capability/weather/
    SampleWeather.kt, WeatherViewTest.kt, WeatherWordsTest.kt    (Task 3)
    TestDatabases.kt, WeatherStoreTest.kt                        (Task 4)
    ScriptedWeatherProvider.kt, TestLogs.kt, WeatherFetcherTest.kt, WeatherSyncLoopTest.kt   (Task 6)
    WeatherRepositoryTest.kt                                     (Task 7)
    ui/RecordingNavigator.kt, ui/ForecastCardTest.kt, ui/WeatherScreenshotTest.kt   (Task 8)
    WeatherCapabilityTest.kt                                     (Task 9)
  src/test/screenshots/forecast_*.png, header_*.png              (Task 8)

provider/weather-openmeteo/
  build.gradle.kts                                   (modify, Task 5)
  src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/
    Http.kt (create: Answer, Call.await moved here), OpenMeteoLocationSearch.kt (modify), OpenMeteoForecast.kt (create),
    di/OpenMeteoModule.kt (modify)
  src/test/resources/forecast_london.json            (create)
  src/test/java/uk/co/siland/culvery/provider/weather_openmeteo/
    TestLogs.kt (create), OpenMeteoLocationSearchTest.kt (modify), OpenMeteoForecastTest.kt (create)

app/
  build.gradle.kts                                   (modify, Task 5: :capability:weather)
  src/main/java/uk/co/siland/culvery/shell/ShellUiState.kt, ShellViewModel.kt, MinuteTicker.kt   (modify, Task 10)
  src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt, CulveryShell.kt   (modify, Task 10)
  src/main/java/uk/co/siland/culvery/di/AppModule.kt (modify, Task 10)
  src/test/java/uk/co/siland/culvery/AppSourceTest.kt (create, Task 10)
  src/test/java/uk/co/siland/culvery/shell/Fakes.kt, ShellViewModelTest.kt (modify); HouseholdTickerTest.kt (create)
  src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt, ShellScreenshotTest.kt   (modify)
  src/test/screenshots/home_weather_dark.png, home_weather_light.png   (new)

README.md, docs/superpowers/plans/2026-09-23-plan1-followups.md   (modify, Task 11)
```

`…` in a path stands for the module's package directory; every step spells out the full path.

---

### Task 1: Move `HouseholdZone` to `:core:household` (D6, §3.3)

**Review:** sonnet.

**Files:**
- Move: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/HouseholdZone.kt` → `core/household/src/main/java/uk/co/siland/culvery/core/household/HouseholdZone.kt`
- Move: `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/HouseholdZoneTest.kt` → `core/household/src/test/java/uk/co/siland/culvery/core/household/HouseholdZoneTest.kt`
- Modify (imports only): in `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/`: `CalendarCapability.kt`, `CalendarEditor.kt`, `CalendarRepository.kt`, `CalendarSync.kt`, `HouseholdFollower.kt`, `ui/Now.kt`; in `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/`: `CalendarCapabilityTest.kt`, `CalendarEditorTest.kt`, `CalendarRepositoryTest.kt`, `CalendarSyncTest.kt`, `HouseholdFollowerTest.kt`, `StubEditor.kt`, `TestEngines.kt`, `ui/CalendarConnectHostTest.kt`, `ui/CardHostsMidnightRolloverTest.kt`, `ui/EventDetailHostTest.kt`, `ui/EventEditorHostTest.kt`, `ui/OpenEventTest.kt`; `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`, `SampleRollbackTest.kt`

**Interfaces:**
- Consumes: `HouseholdRepository.location: Flow<HomeLocation?>` (unchanged).
- Produces (package `uk.co.siland.culvery.core.household`):
  - `@Singleton class HouseholdZone @Inject constructor(household: HouseholdRepository) { val zone: Flow<ZoneId>; suspend fun current(): ZoneId }`, behaviour unchanged (the device zone without a location or with an id that doesn't parse)
  - `fun zoneOrDevice(id: String): ZoneId` — the zone [id] names, or the device's; the one zone parse, which weather uses too (Task 3)

- [ ] **Step 1: Move the test first**

Create `core/household/src/test/java/uk/co/siland/culvery/core/household/HouseholdZoneTest.kt`:
```kotlin
package uk.co.siland.culvery.core.household

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.db.HouseholdDatabase

@RunWith(AndroidJUnit4::class)
class HouseholdZoneTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var zone: HouseholdZone

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
Then delete the calendar's copy:
```bash
git rm capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/HouseholdZoneTest.kt
```

- [ ] **Step 2: Run the test to see it fail**

Run: `./gradlew :core:household:testDebugUnitTest --tests "*HouseholdZoneTest*"`
Expected: FAIL to compile with "Unresolved reference 'HouseholdZone'".

- [ ] **Step 3: Move the class**

```bash
git mv capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/HouseholdZone.kt core/household/src/main/java/uk/co/siland/culvery/core/household/HouseholdZone.kt
```
In the moved file change the package line, drop the import that is now same-package, and lift its private `parse` out as the public `zoneOrDevice` (the same behaviour; weather uses it in Task 3). Its whole content becomes:
```kotlin
package uk.co.siland.culvery.core.household

import java.time.DateTimeException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The zone [id] names, or the device's when it names none. */
fun zoneOrDevice(id: String): ZoneId =
    try {
        ZoneId.of(id)
    } catch (e: DateTimeException) {
        ZoneId.systemDefault()
    }

/** The household's time zone; the device zone until setup has set a location, or if the stored id is invalid. */
@Singleton
class HouseholdZone @Inject constructor(household: HouseholdRepository) {
    val zone: Flow<ZoneId> = household.location
        .map { location -> location?.let { zoneOrDevice(it.timeZoneId) } ?: ZoneId.systemDefault() }
        .distinctUntilChanged()

    suspend fun current(): ZoneId = zone.first()
}
```

- [ ] **Step 4: Point the calendar and the app's debug tests at the new package**

1. In each of these files (same package as the class was, so they had no import), add `import uk.co.siland.culvery.core.household.HouseholdZone` in its sorted place among the `uk.co.siland.culvery.core.household.*` imports (or after the last `uk.co.siland.culvery.core.*` import before it alphabetically):
   - `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`, `CalendarEditor.kt`, `CalendarRepository.kt`, `CalendarSync.kt`, `HouseholdFollower.kt`
   - `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/CalendarCapabilityTest.kt`, `CalendarEditorTest.kt`, `CalendarRepositoryTest.kt`, `CalendarSyncTest.kt`, `HouseholdFollowerTest.kt`, `StubEditor.kt`, `TestEngines.kt`
2. In each of these, replace `import uk.co.siland.culvery.capability.calendar.HouseholdZone` with `import uk.co.siland.culvery.core.household.HouseholdZone` and move it to its sorted place:
   - `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Now.kt`
   - `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/CalendarConnectHostTest.kt`, `CardHostsMidnightRolloverTest.kt`, `EventDetailHostTest.kt`, `EventEditorHostTest.kt`, `OpenEventTest.kt`
   - `app/src/testDebug/java/uk/co/siland/culvery/SampleAddTest.kt`, `SampleRollbackTest.kt`

Check nothing still names the old place:
```bash
grep -rn "capability.calendar.HouseholdZone" --include=*.kt .
```
Expected: no output. (If the compiler then names another file with "Unresolved reference 'HouseholdZone'", give it the same import; change nothing else.)

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:household:testDebugUnitTest :capability:calendar:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS (the three `HouseholdZoneTest` cases now in `:core:household`; the calendar's tests unchanged).

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`, no screenshot differences.

- [ ] **Step 7: Commit**

```bash
git add core/household capability/calendar app/src/testDebug
git commit -m "Move HouseholdZone to :core:household so the shell and weather can share it"
```

---

### Task 2: The `:core:plugin` seams, the shared clock tick and the Forecast's placement (D6, §3.2, §8)

**Review:** sonnet.

**Files:**
- Create: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/HeaderItem.kt`, `Daylight.kt`
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Capability.kt`, `Runtime.kt`
- Modify (`rememberNowMillis` moves out): `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Now.kt`, `CalendarCapability.kt`, `ui/ReviewCalendars.kt`; `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/NowTest.kt`, `ui/CardHostsMidnightRolloverTest.kt`
- Test: `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/WallTimeTest.kt` (create); `HomeCardPlacerTest.kt` (modify)

**Interfaces:**
- Consumes: `SunTimes(sunrise: LocalTime, sunset: LocalTime)`, `WallClock`, `HomeCardPlacer.place` (all existing); the calendar's internal `rememberNowMillis(clock, ticks)` and its private 30 s `everyTick` (`capability/calendar/.../ui/Now.kt`, after Task 1's import change).
- Produces (package `uk.co.siland.culvery.core.plugin`):
  - `class HeaderItem(val id: String, val order: Int, val content: @Composable () -> Unit)`
  - `Capability.headerItems(): Flow<List<HeaderItem>>`, default `flowOf(emptyList())`
  - `interface Daylight { val today: Flow<SunTimes?> }`
  - `fun wallTimeEachMinute(zones: Flow<ZoneId>, clock: WallClock): Flow<LocalDateTime>` — the time now in the latest zone, then at the start of every minute, and at once when the zone changes
  - `val nowTicks: Flow<Unit>` (every 30 s) and `@Composable fun rememberNowMillis(clock: WallClock, ticks: Flow<Unit> = nowTicks): Long` — moved unchanged from the calendar, now public; the calendar and the Forecast card (Task 8) both use it

- [ ] **Step 1: Write the failing tests**

Create `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/WallTimeTest.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WallTimeTest {
    // 09:59:30 UTC on 1 October 2026: 10:59:30 in London (BST), 22:59:30 in Wellington (NZDT).
    private val start = Instant.parse("2026-10-01T09:59:30Z").toEpochMilli()

    @Test
    fun itGivesTheTimeNowThenAtTheStartOfEachMinute() = runTest {
        val clock = WallClock { start + testScheduler.currentTime }
        val times = wallTimeEachMinute(flowOf(ZoneId.of("Europe/London")), clock).take(3).toList()
        assertThat(times).containsExactly(
            LocalDateTime.of(2026, 10, 1, 10, 59, 30),
            LocalDateTime.of(2026, 10, 1, 11, 0),
            LocalDateTime.of(2026, 10, 1, 11, 1),
        ).inOrder()
    }

    @Test
    fun aZoneChangeGivesTheTimeInTheNewZoneAtOnce() = runTest {
        val clock = WallClock { start + testScheduler.currentTime }
        val zones = MutableStateFlow(ZoneId.of("Europe/London"))
        wallTimeEachMinute(zones, clock).test {
            assertThat(awaitItem()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 59, 30))
            zones.value = ZoneId.of("Pacific/Auckland")
            assertThat(awaitItem()).isEqualTo(LocalDateTime.of(2026, 10, 1, 22, 59, 30))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

In `core/plugin/src/test/java/uk/co/siland/culvery/core/plugin/HomeCardPlacerTest.kt`, add before the class's closing brace (Today + Coming up + Forecast is already `mixedSizesFillTallThenWideThenRegularBesideIt`; Connect and Coming up never show together, so the one new case is the Forecast beside Connect, ruling 13):
```kotlin

    @Test
    fun withOnlyTheConnectCardTheForecastTakesTheFirstCellBesideIt() {
        val result = HomeCardPlacer.place(listOf(card("calendar.connect", TALL, 100), card("weather.forecast", REGULAR, 40)))
        assertThat(result.layout()["weather.forecast"]).isEqualTo(listOf(1, 0, 1, 1))
    }
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :core:plugin:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'wallTimeEachMinute'". (The placer case passes on its own once it compiles: the placer already does this; it pins it for the Forecast card.)

- [ ] **Step 3: Add the seams**

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/HeaderItem.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable

/** An item a capability puts on the right of Home's header (4b design D6, §3.2), placed by [order]. */
class HeaderItem(val id: String, val order: Int, val content: @Composable () -> Unit)
```

Create `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Daylight.kt`:
```kotlin
package uk.co.siland.culvery.core.plugin

import kotlinx.coroutines.flow.Flow

/** Today's sunrise and sunset in the household's zone, or null when unknown (4b design §3.2). `:app` takes it as optional. */
interface Daylight {
    val today: Flow<SunTimes?>
}
```

In `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Capability.kt`:
1. Add `import kotlinx.coroutines.flow.flowOf` after `import kotlinx.coroutines.flow.Flow`.
2. After `fun settingsPages(): List<SettingsPage> = emptyList()` add:
```kotlin

    /** Items on the right of Home's header, by [HeaderItem.order]: weather 10, later indoor climate 20. */
    fun headerItems(): Flow<List<HeaderItem>> = flowOf(emptyList())
```

Replace the whole of `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Runtime.kt` with:
```kotlin
package uk.co.siland.culvery.core.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Qualifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

fun interface WallClock {
    fun nowMillis(): Long
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

private const val MINUTE_MS = 60_000L
private const val NOW_TICK_MS = 30_000L

/**
 * The wall time in the latest zone from [zones]: now, then at the start of each minute, and at once when the zone
 * changes (4b design §3.8). Every zone in use is offset by whole minutes, so an epoch minute is a minute on its clock.
 */
fun wallTimeEachMinute(zones: Flow<ZoneId>, clock: WallClock): Flow<LocalDateTime> =
    combine(zones, minuteTicks(clock)) { zone, millis -> LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone) }

private fun minuteTicks(clock: WallClock): Flow<Long> = flow {
    while (true) {
        val now = clock.nowMillis()
        emit(now)
        delay(MINUTE_MS - Math.floorMod(now, MINUTE_MS))
    }
}

/** Every 30 s: how often [rememberNowMillis] reads the clock unless told otherwise. */
val nowTicks: Flow<Unit> = flow {
    while (true) {
        delay(NOW_TICK_MS)
        emit(Unit)
    }
}

/** Wall-clock time that refreshes on every [ticks] emission (every 30 s by default). */
@Composable
fun rememberNowMillis(clock: WallClock, ticks: Flow<Unit> = nowTicks): Long {
    val now by produceState(clock.nowMillis(), clock, ticks) {
        ticks.collect { value = clock.nowMillis() }
    }
    return now
}
```

- [ ] **Step 4: Point the calendar at the moved tick**

Replace the whole of `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/Now.kt` with (its `rememberNowMillis` and `everyTick` are now `:core:plugin`'s; the rest is unchanged):
```kotlin
package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.nowTicks
import uk.co.siland.culvery.core.plugin.rememberNowMillis

@Composable
internal fun rememberZoneId(zone: HouseholdZone): ZoneId {
    val z by zone.zone.collectAsState(initial = ZoneId.systemDefault())
    return z
}

internal fun todayIn(zone: ZoneId, nowMillis: Long): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

@Composable
internal fun rememberToday(zone: HouseholdZone, clock: WallClock, ticks: Flow<Unit> = nowTicks): LocalDate =
    todayIn(rememberZoneId(zone), rememberNowMillis(clock, ticks))
```

Then:
1. In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/CalendarCapability.kt`, replace `import uk.co.siland.culvery.capability.calendar.ui.rememberNowMillis` with `import uk.co.siland.culvery.core.plugin.rememberNowMillis`, in its sorted place.
2. In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/ReviewCalendars.kt`, `capability/calendar/src/test/java/uk/co/siland/culvery/capability/calendar/ui/NowTest.kt` and `ui/CardHostsMidnightRolloverTest.kt` (same package as the old function, so no import today), add `import uk.co.siland.culvery.core.plugin.rememberNowMillis` in its sorted place.

Check:
```bash
grep -rn "calendar.ui.rememberNowMillis\|everyTick" --include=*.kt .
```
Expected: no output.

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :core:plugin:testDebugUnitTest :capability:calendar:testDebugUnitTest`
Expected: PASS; the calendar's `NowTest` and midnight-rollover tests pass unchanged.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL` (every existing `Capability` gets the `headerItems()` default; the shell doesn't read header items yet; no screenshot changes).

- [ ] **Step 7: Commit**

```bash
git add core/plugin capability/calendar
git commit -m "Let capabilities add items to Home's header and supply today's sun times, tick the wall time in a zone, and share the clock tick"
```

---

### Task 3: `:capability:weather` — the module, the contract and what the weather shows (§3.1, §3.7, §4, D1–D5)

**Review:** sonnet.

**Files:**
- Modify: `settings.gradle.kts`, `build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt`
- Create: `capability/weather/build.gradle.kts`, `capability/weather/src/test/resources/robolectric.properties`
- Create: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherContract.kt`, `WeatherView.kt`, `WeatherWords.kt`
- Test: `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/SampleWeather.kt`, `WeatherViewTest.kt`, `WeatherWordsTest.kt` (create)

**Interfaces:**
- Consumes: `HomeLocation(name, latitude, longitude, timeZoneId)` (existing), `zoneOrDevice(id: String): ZoneId` (Task 1), `SunTimes`, `ProviderDescriptor` (existing).
- Produces (all in `uk.co.siland.culvery.capability.weather`):
  - Contract (§3.1, exactly): `interface WeatherProvider { val descriptor: ProviderDescriptor; suspend fun forecast(latitude: Double, longitude: Double, zone: ZoneId): Forecast }`; `data class Forecast(days: List<DailyWeather>, hours: List<HourlyWeather>)`; `data class DailyWeather(date: LocalDate, condition: Condition, high: Double, low: Double, sunrise: LocalTime?, sunset: LocalTime?)`; `data class HourlyWeather(start: LocalDateTime, condition: Condition, temperature: Double)`; `enum class Condition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, THUNDER }`; `class WeatherUnavailableException(message: String? = null, cause: Throwable? = null) : Exception`
  - `const val FORECAST_DAYS = 3`
  - `data class WeatherPlace(latitude: Double, longitude: Double, zoneId: String)` with `constructor(location: HomeLocation)` and `val zone: ZoneId` (`zoneOrDevice(zoneId)`)
  - `data class StoredWeather(place: WeatherPlace, fetchedAtMillis: Long, days: List<DailyWeather>, hours: List<HourlyWeather>)`
  - `fun StoredWeather?.matching(location: HomeLocation?): StoredWeather?`
  - `sealed interface WeatherView { NoLocation; Waiting; Expired; data class Ready(now: HourlyWeather?, today: DailyWeather, days: List<DailyWeather>, fetchedAtMillis: Long) }`
  - `fun weatherView(location: HomeLocation?, stored: StoredWeather?, now: LocalDateTime): WeatherView`
  - `fun sunTimesOn(location: HomeLocation?, stored: StoredWeather?, date: LocalDate): SunTimes?`
  - `data class HeaderWeather(condition: Condition, night: Boolean, temperature: Double, high: Double, low: Double)`; `fun headerWeather(view: WeatherView, now: LocalDateTime): HeaderWeather?`
  - Internal words (`WeatherWords.kt`): `FORECAST_TITLE`, `TODAY`, `GETTING_FORECAST`, `NO_FORECAST`, `ADD_LOCATION`, `OPEN_SETTINGS`, `STALE_AFTER_MS`; `degrees(Double): String`; `highLow(high: Double, low: Double): String`; `updatedAgo(fetchedAtMillis: Long, nowMillis: Long): String?`; `weatherIcon(Condition, night: Boolean): String`; `conditionWords(Condition): String`; `dayLabel(DailyWeather, isToday: Boolean): String`; `rowDescription(DailyWeather, isToday: Boolean): String`
  - Test fixtures (`SampleWeather.kt`, internal): `LONDON`, `LEEDS`, `WELLINGTON`, `THU`, `FRI`, `SAT`, `SUNRISE`, `SUNSET`, `day(…)`, `hoursOf(…)`, `forecast(from, days)`, `stored(location, forecast, fetchedAtMillis)`, `READY`

- [ ] **Step 1: Add the module**

In `settings.gradle.kts`, after `include(":capability:calendar-testkit")` add `include(":capability:weather")`.

Create `capability/weather/build.gradle.kts`:
```kotlin
plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
    alias(libs.plugins.roborazzi)
}

dependencies {
    // Capability, HeaderItem, Daylight, SunTimes and ProviderDescriptor appear in this module's public API.
    api(project(":core:plugin"))
    // HomeLocation and HouseholdZone appear in this module's public API (weatherView, WeatherRepository).
    api(project(":core:household"))
    implementation(project(":core:ui"))
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
```

Create `capability/weather/src/test/resources/robolectric.properties`:
```properties
qualifiers=w1280dp-h800dp-land-hdpi
```

- [ ] **Step 2: Write the failing tests**

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/SampleWeather.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import java.time.LocalDate
import java.time.LocalTime
import uk.co.siland.culvery.core.household.HomeLocation

internal val LONDON = HomeLocation("London", 51.5074, -0.1278, "Europe/London")
internal val LEEDS = HomeLocation("Leeds", 53.7997, -1.5492, "Europe/London")
internal val WELLINGTON = HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland")

/** Thursday 1 October 2026. */
internal val THU: LocalDate = LocalDate.of(2026, 10, 1)
internal val FRI: LocalDate = THU.plusDays(1)
internal val SAT: LocalDate = THU.plusDays(2)

internal val SUNRISE: LocalTime = LocalTime.of(7, 1)
internal val SUNSET: LocalTime = LocalTime.of(18, 38)

internal fun day(
    date: LocalDate,
    condition: Condition = Condition.PARTLY_CLOUDY,
    high: Double = 19.0,
    low: Double = 11.0,
    sunrise: LocalTime? = SUNRISE,
    sunset: LocalTime? = SUNSET,
) = DailyWeather(date, condition, high, low, sunrise, sunset)

internal fun hoursOf(date: LocalDate, condition: Condition = Condition.PARTLY_CLOUDY, temperature: Double = 17.0): List<HourlyWeather> =
    (0 until 24).map { HourlyWeather(date.atTime(it, 0), condition, temperature) }

/** [days] days from [from], each with its 24 hours. */
internal fun forecast(from: LocalDate = THU, days: Int = 7): Forecast {
    val dates = (0 until days).map { from.plusDays(it.toLong()) }
    return Forecast(dates.map { day(it) }, dates.flatMap { hoursOf(it) })
}

internal fun stored(location: HomeLocation = LONDON, forecast: Forecast = forecast(), fetchedAtMillis: Long = 0L) =
    StoredWeather(WeatherPlace(location), fetchedAtMillis, forecast.days, forecast.hours)

/** Thursday at 11:00, as the card and header screenshots show it. */
internal val READY = WeatherView.Ready(
    now = HourlyWeather(THU.atTime(11, 0), Condition.PARTLY_CLOUDY, 17.0),
    today = day(THU),
    days = listOf(
        day(THU),
        day(FRI, Condition.RAIN, high = 17.0, low = 10.0),
        day(SAT, Condition.CLEAR, high = 21.0, low = 12.0),
    ),
    fetchedAtMillis = 0L,
)
```

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherViewTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.SunTimes

class WeatherViewTest {
    private val thuMorning = THU.atTime(10, 30)

    @Test
    fun withoutALocationItAsksForOneWhateverIsStored() {
        assertThat(weatherView(null, stored(), thuMorning)).isEqualTo(WeatherView.NoLocation)
    }

    @Test
    fun aLocationWithNothingStoredIsWaiting() {
        assertThat(weatherView(LONDON, null, thuMorning)).isEqualTo(WeatherView.Waiting)
    }

    @Test
    fun theOldTownsWeatherIsWaitingAfterAMove() {
        assertThat(weatherView(LEEDS, stored(LONDON), thuMorning)).isEqualTo(WeatherView.Waiting)
        assertThat(weatherView(LONDON.copy(timeZoneId = "Europe/Dublin"), stored(LONDON), thuMorning)).isEqualTo(WeatherView.Waiting)
    }

    @Test
    fun aRenamedTownKeepsItsWeather() {
        assertThat(weatherView(LONDON.copy(name = "Westminster"), stored(LONDON), thuMorning)).isInstanceOf(WeatherView.Ready::class.java)
    }

    /** Review Focus 1: a fetch that lands after a move is stored under the place it asked for, so it doesn't match. */
    @Test
    fun matchingComparesTheCoordinatesAndTheZoneNotTheName() {
        val s = stored(LONDON)
        assertThat(s.matching(LONDON)).isNotNull()
        assertThat(s.matching(LONDON.copy(name = "Westminster"))).isNotNull()
        assertThat(s.matching(LEEDS)).isNull()
        assertThat(s.matching(LONDON.copy(latitude = 51.5075))).isNull()
        assertThat(s.matching(LONDON.copy(longitude = -0.1279))).isNull()
        assertThat(s.matching(LONDON.copy(timeZoneId = "Europe/Dublin"))).isNull()
        assertThat(s.matching(null)).isNull()
        assertThat((null as StoredWeather?).matching(LONDON)).isNull()
    }

    @Test
    fun readyStartsAtTodayWithTheNextTwoDaysAndTheHourContainingNow() {
        val view = weatherView(LONDON, stored(forecast = forecast(from = THU.minusDays(1))), thuMorning) as WeatherView.Ready
        assertThat(view.today.date).isEqualTo(THU)
        assertThat(view.days.map { it.date }).containsExactly(THU, FRI, SAT).inOrder()
        assertThat(view.now?.start).isEqualTo(THU.atTime(10, 0))
    }

    @Test
    fun theHourStartingNowIsTheOneShown() {
        val view = weatherView(LONDON, stored(), THU.atTime(11, 0)) as WeatherView.Ready
        assertThat(view.now?.start).isEqualTo(THU.atTime(11, 0))
    }

    @Test
    fun dataThatRunsOutBeforeTodayIsExpired() {
        assertThat(weatherView(LONDON, stored(forecast = forecast(from = THU.minusDays(7))), thuMorning)).isEqualTo(WeatherView.Expired)
    }

    @Test
    fun midnightMovesTodayOnAndDropsADay() {
        val data = stored(forecast = forecast(from = THU, days = 3))
        val beforeMidnight = weatherView(LONDON, data, THU.atTime(23, 59)) as WeatherView.Ready
        assertThat(beforeMidnight.days.map { it.date }).containsExactly(THU, FRI, SAT).inOrder()
        val afterMidnight = weatherView(LONDON, data, FRI.atStartOfDay()) as WeatherView.Ready
        assertThat(afterMidnight.today.date).isEqualTo(FRI)
        assertThat(afterMidnight.days.map { it.date }).containsExactly(FRI, SAT).inOrder()
        assertThat(afterMidnight.now?.start).isEqualTo(FRI.atStartOfDay())
    }

    @Test
    fun fewerThanThreeDaysLeftShowsThoseThatExist() {
        val view = weatherView(LONDON, stored(forecast = forecast(from = THU.minusDays(5))), thuMorning) as WeatherView.Ready
        assertThat(view.days.map { it.date }).containsExactly(THU, FRI).inOrder()
    }

    @Test
    fun aMissingHourLeavesNowEmptyAndTheRestReady() {
        val f = forecast()
        val data = stored(forecast = f.copy(hours = f.hours.filterNot { it.start == THU.atTime(10, 0) }))
        val view = weatherView(LONDON, data, thuMorning) as WeatherView.Ready
        assertThat(view.now).isNull()
        assertThat(view.today.date).isEqualTo(THU)
    }

    /** A day whose 01:00 never comes (the clocks go forward) has 00:00, then 02:00: the lookup leaves the gap empty. */
    @Test
    fun theHourContainingNowIsFoundAcrossAGap() {
        val hours = listOf(HourlyWeather(THU.atTime(0, 0), Condition.CLEAR, 9.0), HourlyWeather(THU.atTime(2, 0), Condition.CLEAR, 8.0))
        val data = stored(forecast = Forecast(listOf(day(THU)), hours))
        assertThat((weatherView(LONDON, data, THU.atTime(2, 15)) as WeatherView.Ready).now?.temperature).isEqualTo(8.0)
        assertThat((weatherView(LONDON, data, THU.atTime(1, 30)) as WeatherView.Ready).now).isNull()
    }

    @Test
    fun theFetchTimeIsCarriedForTheAgeLine() {
        val view = weatherView(LONDON, stored(fetchedAtMillis = 1_234L), thuMorning) as WeatherView.Ready
        assertThat(view.fetchedAtMillis).isEqualTo(1_234L)
    }

    @Test
    fun oldMatchingDataStillGivesTodaysSunTimes() {
        val data = stored(forecast = forecast(from = THU.minusDays(5)), fetchedAtMillis = 0L)
        assertThat(sunTimesOn(LONDON, data, THU)).isEqualTo(SunTimes(SUNRISE, SUNSET))
    }

    @Test
    fun sunTimesNeedMatchingDataForToday() {
        assertThat(sunTimesOn(LEEDS, stored(LONDON), THU)).isNull()
        assertThat(sunTimesOn(LONDON, null, THU)).isNull()
        assertThat(sunTimesOn(null, stored(), THU)).isNull()
        assertThat(sunTimesOn(LONDON, stored(forecast = forecast(from = THU.minusDays(7))), THU)).isNull()
    }

    @Test
    fun aMissingSunriseOrSunsetGivesNoSunTimes() {
        val noSunrise = stored(forecast = Forecast(listOf(day(THU, sunrise = null)), emptyList()))
        val noSunset = stored(forecast = Forecast(listOf(day(THU, sunset = null)), emptyList()))
        assertThat(sunTimesOn(LONDON, noSunrise, THU)).isNull()
        assertThat(sunTimesOn(LONDON, noSunset, THU)).isNull()
    }

    @Test
    fun theHeaderShowsOnlyWhenReadyWithTheHourNow() {
        assertThat(headerWeather(WeatherView.Waiting, thuMorning)).isNull()
        assertThat(headerWeather(WeatherView.Expired, thuMorning)).isNull()
        assertThat(headerWeather(WeatherView.NoLocation, thuMorning)).isNull()
        assertThat(headerWeather(READY.copy(now = null), thuMorning)).isNull()
        assertThat(headerWeather(READY, thuMorning))
            .isEqualTo(HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0))
    }

    @Test
    fun itIsNightBeforeSunriseAndFromSunset() {
        fun nightAt(hour: Int, minute: Int) = headerWeather(READY, THU.atTime(hour, minute))!!.night
        assertThat(nightAt(7, 0)).isTrue()
        assertThat(nightAt(7, 1)).isFalse()
        assertThat(nightAt(18, 37)).isFalse()
        assertThat(nightAt(18, 38)).isTrue()
    }

    @Test
    fun withoutSunTimesTheHeaderUsesDayIcons() {
        val unknown = READY.copy(today = day(THU, sunrise = null, sunset = null))
        assertThat(headerWeather(unknown, THU.atTime(23, 0))!!.night).isFalse()
    }

    @Test
    fun anInvalidZoneIdAsksInTheDeviceZone() {
        assertThat(WeatherPlace(LONDON.copy(timeZoneId = "Not/AZone")).zone).isEqualTo(java.time.ZoneId.systemDefault())
        assertThat(WeatherPlace(WELLINGTON).zone).isEqualTo(java.time.ZoneId.of("Pacific/Auckland"))
    }
}
```

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherWordsTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeatherWordsTest {
    private val hour = 3_600_000L

    @Test
    fun degreesAreWholeRoundedHalfUpAndNeverMinusZero() {
        assertThat(listOf(16.5, 16.49, 0.0, -0.4, -0.5, -1.5, -2.6).map(::degrees))
            .containsExactly("17°", "16°", "0°", "0°", "0°", "-1°", "-3°").inOrder()
    }

    @Test
    fun highAndLowReadAsTheHeaderLine() {
        assertThat(highLow(19.2, 10.6)).isEqualTo("High 19° · Low 11°")
    }

    @Test
    fun theAgeLineAppearsOnlyPastTwoHours() {
        val fetched = 1_000_000L
        fun ago(age: Long) = updatedAgo(fetched, fetched + age)
        assertThat(ago(2 * hour)).isNull()
        assertThat(ago(2 * hour + 1)).isEqualTo("Updated 2 h ago")
        assertThat(ago(3 * hour)).isEqualTo("Updated 3 h ago")
        assertThat(ago(24 * hour - 1)).isEqualTo("Updated 23 h ago")
        assertThat(ago(24 * hour)).isEqualTo("Updated 1 day ago")
        assertThat(ago(49 * hour)).isEqualTo("Updated 2 days ago")
    }

    @Test
    fun eachConditionHasItsIconAndOnlyClearAndPartlyCloudyChangeAtNight() {
        val day = Condition.entries.associateWith { weatherIcon(it, night = false) }
        assertThat(day).containsExactly(
            Condition.CLEAR, "sunny",
            Condition.PARTLY_CLOUDY, "partly_cloudy_day",
            Condition.CLOUDY, "cloud",
            Condition.FOG, "foggy",
            Condition.DRIZZLE, "rainy_light",
            Condition.RAIN, "rainy",
            Condition.SHOWERS, "rainy_heavy",
            Condition.SNOW, "weather_snowy",
            Condition.THUNDER, "thunderstorm",
        )
        val night = Condition.entries.associateWith { weatherIcon(it, night = true) }
        assertThat(night).isEqualTo(day + mapOf(Condition.CLEAR to "clear_night", Condition.PARTLY_CLOUDY to "partly_cloudy_night"))
    }

    @Test
    fun rowsAreLabelledTodayThenShortDayNames() {
        assertThat(dayLabel(day(THU), isToday = true)).isEqualTo("Today")
        assertThat(dayLabel(day(FRI), isToday = false)).isEqualTo("Fri")
        assertThat(dayLabel(day(SAT), isToday = false)).isEqualTo("Sat")
    }

    @Test
    fun eachRowReadsAsOneSentence() {
        assertThat(rowDescription(day(FRI, Condition.PARTLY_CLOUDY, high = 17.2, low = 10.4), isToday = false))
            .isEqualTo("Friday, partly cloudy, high 17°, low 10°")
        assertThat(rowDescription(day(THU, Condition.THUNDER, high = 19.0, low = 11.0), isToday = true))
            .isEqualTo("Today, thunderstorms, high 19°, low 11°")
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:weather:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'Condition'", "'DailyWeather'", "'WeatherView'", "'weatherView'", "'degrees'", "'updatedAgo'" (`HomeLocation` and `SunTimes` resolve through the module's `api` dependencies).

- [ ] **Step 4: Write the contract**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherContract.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

/** A source of forecasts (4b design §3.1), bound `@IntoSet` by a provider module. */
interface WeatherProvider {
    val descriptor: ProviderDescriptor

    /** Times are wall times in [zone]; temperatures °C. Throws WeatherUnavailableException on any failure; nothing else escapes. */
    suspend fun forecast(latitude: Double, longitude: Double, zone: ZoneId): Forecast
}

data class Forecast(val days: List<DailyWeather>, val hours: List<HourlyWeather>)

data class DailyWeather(
    val date: LocalDate,
    val condition: Condition,
    val high: Double,
    val low: Double,
    /** Null on a polar day or night. */
    val sunrise: LocalTime?,
    val sunset: LocalTime?,
)

data class HourlyWeather(val start: LocalDateTime, val condition: Condition, val temperature: Double)

/** The capability's own conditions; each provider maps its codes onto them. */
enum class Condition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, THUNDER }

/** The weather contract's own failure: the calendar's `UnreachableException` is out of this module's reach. */
class WeatherUnavailableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
```

- [ ] **Step 5: Write what the weather shows**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherView.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.zoneOrDevice
import uk.co.siland.culvery.core.plugin.SunTimes

/** The Forecast card's days: today and the next two (D1). */
const val FORECAST_DAYS = 3

/** Where a forecast is for: what a fetch records and matching compares (§3.5). The town's name is no part of it. */
data class WeatherPlace(val latitude: Double, val longitude: Double, val zoneId: String) {
    constructor(location: HomeLocation) : this(location.latitude, location.longitude, location.timeZoneId)

    /** The zone to ask in; the device's when [zoneId] doesn't parse, as `HouseholdZone` falls back. */
    val zone: ZoneId get() = zoneOrDevice(zoneId)
}

/** What `weather.db` holds: one fetch, made for [place]. */
data class StoredWeather(
    val place: WeatherPlace,
    val fetchedAtMillis: Long,
    val days: List<DailyWeather>,
    val hours: List<HourlyWeather>,
)

/** This data if it was fetched for [location]'s coordinates and zone, else null: another town's weather is never shown (§3.5). */
fun StoredWeather?.matching(location: HomeLocation?): StoredWeather? =
    this?.takeIf { location != null && it.place == WeatherPlace(location) }

/** What the card and header read (§3.7). */
sealed interface WeatherView {
    data object NoLocation : WeatherView

    /** A location is set; nothing stored matches it. */
    data object Waiting : WeatherView

    /** Matching data, but nothing for today. */
    data object Expired : WeatherView

    data class Ready(
        /** The stored hour containing now; null if missing. */
        val now: HourlyWeather?,
        val today: DailyWeather,
        /** Today and up to the next two. */
        val days: List<DailyWeather>,
        val fetchedAtMillis: Long,
    ) : WeatherView
}

fun weatherView(location: HomeLocation?, stored: StoredWeather?, now: LocalDateTime): WeatherView {
    if (location == null) return WeatherView.NoLocation
    val data = stored.matching(location) ?: return WeatherView.Waiting
    val date = now.toLocalDate()
    val ahead = data.days.filter { !it.date.isBefore(date) }.sortedBy { it.date }
    val today = ahead.firstOrNull()?.takeIf { it.date == date } ?: return WeatherView.Expired
    // The latest start at or before now within the hour: a skipped hour leaves a gap, not a wrong hour.
    val hour = data.hours.filter { !it.start.isAfter(now) && now.isBefore(it.start.plusHours(1)) }.maxByOrNull { it.start }
    return WeatherView.Ready(hour, today, ahead.take(FORECAST_DAYS), data.fetchedAtMillis)
}

/** [date]'s sun times from matching data however old (§3.7: they barely move day to day), or null. */
fun sunTimesOn(location: HomeLocation?, stored: StoredWeather?, date: LocalDate): SunTimes? {
    val day = stored.matching(location)?.days?.firstOrNull { it.date == date } ?: return null
    val sunrise = day.sunrise ?: return null
    val sunset = day.sunset ?: return null
    return SunTimes(sunrise, sunset)
}

/** What the header item shows (§4.1). */
data class HeaderWeather(
    val condition: Condition,
    val night: Boolean,
    val temperature: Double,
    val high: Double,
    val low: Double,
)

/** The header's weather, or null while it is hidden: shown only when [view] is Ready with an hour for now (§4.1). */
fun headerWeather(view: WeatherView, now: LocalDateTime): HeaderWeather? {
    val ready = view as? WeatherView.Ready ?: return null
    val hour = ready.now ?: return null
    val sunrise = ready.today.sunrise
    val sunset = ready.today.sunset
    val time = now.toLocalTime()
    val night = sunrise != null && sunset != null && (time < sunrise || time >= sunset)
    return HeaderWeather(hour.condition, night, hour.temperature, ready.today.high, ready.today.low)
}
```

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherWords.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal const val FORECAST_TITLE = "Forecast"
internal const val TODAY = "Today"
internal const val GETTING_FORECAST = "Getting the forecast…"
internal const val NO_FORECAST = "No forecast — check the tablet's Wi-Fi."
internal const val ADD_LOCATION = "Add your home location to see the weather."
internal const val OPEN_SETTINGS = "Open settings"

/** Past this the card says how old its data is (D3). */
internal const val STALE_AFTER_MS = 2 * 60 * 60_000L
private const val HOUR_MS = 60 * 60_000L
private const val HOURS_IN_DAY = 24L

private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE", Locale.UK)
private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE", Locale.UK)

/** Whole degrees, half up; an Int has no "-0" (§4.2). */
internal fun degrees(celsius: Double): String = "${celsius.roundToInt()}°"

internal fun highLow(high: Double, low: Double): String = "High ${degrees(high)} · Low ${degrees(low)}"

/** "Updated 3 h ago" once the data is past [STALE_AFTER_MS] old, else null (§4.2). */
internal fun updatedAgo(fetchedAtMillis: Long, nowMillis: Long): String? {
    val age = nowMillis - fetchedAtMillis
    if (age <= STALE_AFTER_MS) return null
    val hours = age / HOUR_MS
    if (hours < HOURS_IN_DAY) return "Updated $hours h ago"
    val days = hours / HOURS_IN_DAY
    return if (days == 1L) "Updated 1 day ago" else "Updated $days days ago"
}

/** Material Symbols names (§4.2), all checked present in the bundled font. */
internal fun weatherIcon(condition: Condition, night: Boolean): String = when (condition) {
    Condition.CLEAR -> if (night) "clear_night" else "sunny"
    Condition.PARTLY_CLOUDY -> if (night) "partly_cloudy_night" else "partly_cloudy_day"
    Condition.CLOUDY -> "cloud"
    Condition.FOG -> "foggy"
    Condition.DRIZZLE -> "rainy_light"
    Condition.RAIN -> "rainy"
    Condition.SHOWERS -> "rainy_heavy"
    Condition.SNOW -> "weather_snowy"
    Condition.THUNDER -> "thunderstorm"
}

internal fun conditionWords(condition: Condition): String = when (condition) {
    Condition.CLEAR -> "clear"
    Condition.PARTLY_CLOUDY -> "partly cloudy"
    Condition.CLOUDY -> "cloudy"
    Condition.FOG -> "fog"
    Condition.DRIZZLE -> "drizzle"
    Condition.RAIN -> "rain"
    Condition.SHOWERS -> "showers"
    Condition.SNOW -> "snow"
    Condition.THUNDER -> "thunderstorms"
}

internal fun dayLabel(day: DailyWeather, isToday: Boolean): String = if (isToday) TODAY else day.date.format(SHORT_DAY)

/** One TalkBack item per row: "Friday, partly cloudy, high 17°, low 10°" (§4.2). */
internal fun rowDescription(day: DailyWeather, isToday: Boolean): String {
    val name = if (isToday) TODAY else day.date.format(LONG_DAY)
    return "$name, ${conditionWords(day.condition)}, high ${degrees(day.high)}, low ${degrees(day.low)}"
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :capability:weather:testDebugUnitTest`
Expected: PASS (20 in `WeatherViewTest`, 6 in `WeatherWordsTest`).

- [ ] **Step 7: Cover the new module in the boundary test**

In `build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt`, before `@Test fun appMayDependOnAnything()` add:
```kotlin
    @Test
    fun theWeatherModulesFollowTheRules() {
        allowed(":capability:weather", ":core:household", "api")
        allowed(":capability:weather", ":core:ui")
        allowed(":provider:weather-openmeteo", ":capability:weather")
        allowed(":app", ":capability:weather")
        banned(":capability:weather", ":provider:weather-openmeteo")
        banned(":capability:weather", ":provider:weather-openmeteo", "testImplementation")
        banned(":capability:weather", ":capability:calendar")
        banned(":provider:calendar-google", ":capability:weather")
        banned(":core:plugin", ":capability:weather")
    }

```
Run: `./gradlew -p build-logic :convention:test`
Expected: PASS. (`ModuleBoundaries` needs no change: its rules are by kind and family; this pins them for the new module.)

- [ ] **Step 8: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts build-logic/convention/src/test/kotlin/ModuleBoundariesTest.kt capability/weather
git commit -m "Add :capability:weather with its provider contract and the rules for what the weather shows"
```

---

### Task 4: `weather.db` and `WeatherStore` — one fetch, replaced whole, matched by place (§3.5, D3)

**Review:** opus (state: atomicity, the snapshot read, matching).

**Files:**
- Create: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/db/WeatherDatabase.kt`, `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherStore.kt`
- Generated, commit: `capability/weather/schemas/uk.co.siland.culvery.capability.weather.db.WeatherDatabase/1.json`
- Test: `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/TestDatabases.kt`, `WeatherStoreTest.kt` (create)

**Interfaces:**
- Consumes: `WeatherPlace`, `StoredWeather`, `Forecast`, `DailyWeather`, `HourlyWeather`, `Condition` (Task 3). Matching is `StoredWeather?.matching` (Task 3, tested there); the store records the place it is given.
- Produces:
  - `@Entity("fetch") FetchEntity(id: Int = 0, latitude: Double, longitude: Double, zoneId: String, fetchedAtMillis: Long)`; `@Entity("day") DayEntity(date: String, condition: String, high: Double, low: Double, sunrise: String?, sunset: String?)`; `@Entity("hour") HourEntity(start: String, condition: String, temperature: Double)`
  - `@Dao interface WeatherDao` (`fetchChanges(): Flow<FetchEntity?>`, `fetch()`, `days()`, `hours()`, `clearFetch()`, `clearDays()`, `clearHours()`, `insertFetch(FetchEntity)`, `insertDays(List<DayEntity>)`, `insertHours(List<HourEntity>)`)
  - `abstract class WeatherDatabase : RoomDatabase { abstract fun weatherDao(): WeatherDao }`, version 1
  - `@Singleton class WeatherStore` — `internal constructor(db: WeatherDatabase, dao: WeatherDao)`; `@Inject constructor(db: WeatherDatabase)`; `val stored: Flow<StoredWeather?>`; `suspend fun replace(place: WeatherPlace, forecast: Forecast, fetchedAtMillis: Long)`
  - Test helper: `internal fun weatherDb(): WeatherDatabase`

- [ ] **Step 1: Write the failing tests**

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/TestDatabases.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase

internal fun weatherDb(): WeatherDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WeatherDatabase::class.java)
        .allowMainThreadQueries()
        .build()
```

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherStoreTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import android.database.sqlite.SQLiteFullException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.weather.db.HourEntity
import uk.co.siland.culvery.capability.weather.db.WeatherDao
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class WeatherStoreTest {
    private lateinit var db: WeatherDatabase
    private lateinit var store: WeatherStore

    @Before
    fun setUp() {
        db = weatherDb()
        store = WeatherStore(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun nothingIsStoredAtFirst() = runTest {
        assertThat(store.stored.first()).isNull()
    }

    @Test
    fun aReplaceStoresThePlaceTheTimeTheDaysAndTheHours() = runTest {
        val f = forecast()
        store.replace(WeatherPlace(LONDON), f, 1_000L)
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(51.5074, -0.1278, "Europe/London"))
        assertThat(s.fetchedAtMillis).isEqualTo(1_000L)
        assertThat(s.days).isEqualTo(f.days)
        assertThat(s.hours).isEqualTo(f.hours)
    }

    @Test
    fun unknownSunTimesComeBackUnknown() = runTest {
        val polar = Forecast(listOf(day(THU, sunrise = null, sunset = null)), hoursOf(THU))
        store.replace(WeatherPlace(LONDON), polar, 1_000L)
        val today = store.stored.first()!!.days.single()
        assertThat(today.sunrise).isNull()
        assertThat(today.sunset).isNull()
    }

    @Test
    fun aSecondReplaceLeavesNothingOfTheFirst() = runTest {
        store.replace(WeatherPlace(LONDON), forecast(from = THU.minusDays(3)), 1_000L)
        store.replace(WeatherPlace(LEEDS), forecast(from = THU, days = 2), 2_000L)
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(LEEDS))
        assertThat(s.fetchedAtMillis).isEqualTo(2_000L)
        assertThat(s.days.map { it.date }).containsExactly(THU, FRI).inOrder()
        assertThat(s.hours).hasSize(48)
    }

    @Test
    fun aFailedReplaceLeavesTheOldDataWhole() = runTest {
        store.replace(WeatherPlace(LONDON), forecast(), 1_000L)
        val failing = object : WeatherDao by db.weatherDao() {
            override suspend fun insertHours(rows: List<HourEntity>) {
                throw SQLiteFullException("disk full")
            }
        }
        val thrown = runCatching { WeatherStore(db, failing).replace(WeatherPlace(LEEDS), forecast(from = FRI), 2_000L) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(SQLiteFullException::class.java)
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(LONDON))
        assertThat(s.fetchedAtMillis).isEqualTo(1_000L)
        assertThat(s.days.first().date).isEqualTo(THU)
        assertThat(s.hours).hasSize(7 * 24)
    }

    /** Review Focus 2: a provider that repeats an hour can't break every fetch; the first is kept. */
    @Test
    fun aRepeatedHourIsStoredOnce() = runTest {
        val f = forecast(days = 1)
        store.replace(WeatherPlace(LONDON), f.copy(hours = f.hours + HourlyWeather(THU.atTime(1, 0), Condition.RAIN, 4.0)), 1_000L)
        val hours = store.stored.first()!!.hours
        assertThat(hours).hasSize(24)
        assertThat(hours.single { it.start == THU.atTime(1, 0) }.temperature).isEqualTo(17.0)
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :capability:weather:testDebugUnitTest --tests "*WeatherStoreTest*"`
Expected: FAIL to compile with "Unresolved reference 'WeatherDatabase'", "'WeatherStore'", "'WeatherDao'", "'HourEntity'".

- [ ] **Step 3: Write the database**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/db/WeatherDatabase.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** The one fetch the tables hold (4b design §3.5). */
@Entity(tableName = "fetch")
data class FetchEntity(
    @PrimaryKey val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
    val fetchedAtMillis: Long,
)

@Entity(tableName = "day")
data class DayEntity(
    /** ISO local date. */
    @PrimaryKey val date: String,
    /** A `Condition` name. */
    val condition: String,
    val high: Double,
    val low: Double,
    /** ISO local times; null when the sun doesn't rise or set that day. */
    val sunrise: String?,
    val sunset: String?,
)

@Entity(tableName = "hour")
data class HourEntity(
    /** ISO local date-time. */
    @PrimaryKey val start: String,
    val condition: String,
    val temperature: Double,
)

@Dao
interface WeatherDao {
    @Query("SELECT * FROM fetch LIMIT 1")
    fun fetchChanges(): Flow<FetchEntity?>

    @Query("SELECT * FROM fetch LIMIT 1")
    suspend fun fetch(): FetchEntity?

    @Query("SELECT * FROM day ORDER BY date")
    suspend fun days(): List<DayEntity>

    @Query("SELECT * FROM hour ORDER BY start")
    suspend fun hours(): List<HourEntity>

    @Query("DELETE FROM fetch")
    suspend fun clearFetch()

    @Query("DELETE FROM day")
    suspend fun clearDays()

    @Query("DELETE FROM hour")
    suspend fun clearHours()

    @Insert
    suspend fun insertFetch(row: FetchEntity)

    @Insert
    suspend fun insertDays(rows: List<DayEntity>)

    @Insert
    suspend fun insertHours(rows: List<HourEntity>)
}

/** v1. A cache, but a later version still ships a hand-written Migration, as the calendar's do. */
@Database(entities = [FetchEntity::class, DayEntity::class, HourEntity::class], version = 1, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
}
```

- [ ] **Step 4: Write the store**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherStore.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.weather.db.DayEntity
import uk.co.siland.culvery.capability.weather.db.FetchEntity
import uk.co.siland.culvery.capability.weather.db.HourEntity
import uk.co.siland.culvery.capability.weather.db.WeatherDao
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase

/** `weather.db` (4b design §3.5): the last successful fetch, whole. */
@Singleton
class WeatherStore internal constructor(private val db: WeatherDatabase, private val dao: WeatherDao) {
    @Inject
    constructor(db: WeatherDatabase) : this(db, db.weatherDao())

    /**
     * The stored fetch, or null before the first. Every replace rewrites the `fetch` row, so watching it sees each change;
     * the three tables are then read in one transaction, so one fetch's place is never paired with another's days.
     */
    val stored: Flow<StoredWeather?> = dao.fetchChanges().map { db.withTransaction { read() } }.distinctUntilChanged()

    /** Replaces everything with [forecast], fetched for [place], in one transaction; a repeated date or hour is kept once. */
    suspend fun replace(place: WeatherPlace, forecast: Forecast, fetchedAtMillis: Long) {
        db.withTransaction {
            dao.clearHours()
            dao.clearDays()
            dao.clearFetch()
            dao.insertFetch(
                FetchEntity(latitude = place.latitude, longitude = place.longitude, zoneId = place.zoneId, fetchedAtMillis = fetchedAtMillis),
            )
            dao.insertDays(forecast.days.distinctBy { it.date }.map { it.toEntity() })
            dao.insertHours(forecast.hours.distinctBy { it.start }.map { it.toEntity() })
        }
    }

    private suspend fun read(): StoredWeather? {
        val fetch = dao.fetch() ?: return null
        return StoredWeather(
            WeatherPlace(fetch.latitude, fetch.longitude, fetch.zoneId),
            fetch.fetchedAtMillis,
            dao.days().map { it.toDaily() },
            dao.hours().map { it.toHourly() },
        )
    }
}

private fun DailyWeather.toEntity() =
    DayEntity(date.toString(), condition.name, high, low, sunrise?.toString(), sunset?.toString())

private fun HourlyWeather.toEntity() = HourEntity(start.toString(), condition.name, temperature)

private fun DayEntity.toDaily() = DailyWeather(
    LocalDate.parse(date),
    conditionNamed(condition),
    high,
    low,
    sunrise?.let { LocalTime.parse(it) },
    sunset?.let { LocalTime.parse(it) },
)

private fun HourEntity.toHourly() = HourlyWeather(LocalDateTime.parse(start), conditionNamed(condition), temperature)

// A name this build doesn't know reads as cloudy, as an unknown provider code does.
private fun conditionNamed(name: String): Condition = Condition.entries.firstOrNull { it.name == name } ?: Condition.CLOUDY
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew :capability:weather:testDebugUnitTest`
Expected: PASS. KSP writes the schema while compiling; check it:
```bash
ls capability/weather/schemas/uk.co.siland.culvery.capability.weather.db.WeatherDatabase/
```
Expected: `1.json`.

- [ ] **Step 6: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add capability/weather
git commit -m "Store the forecast in weather.db, replaced whole by each fetch, and treat another place's forecast as absent"
```

---

### Task 5: The Open-Meteo forecast (§3.4, §6; rulings 1, 2, 15)

**Review:** opus (parsing untrusted input, log hygiene, cancellation).

**Files:**
- Modify: `provider/weather-openmeteo/build.gradle.kts`, `app/build.gradle.kts`
- Create: `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/Http.kt`, `OpenMeteoForecast.kt`
- Modify: `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearch.kt`, `di/OpenMeteoModule.kt`
- Create: `provider/weather-openmeteo/src/test/resources/forecast_london.json`
- Test: `provider/weather-openmeteo/src/test/java/uk/co/siland/culvery/provider/weather_openmeteo/TestLogs.kt`, `OpenMeteoForecastTest.kt` (create); `OpenMeteoLocationSearchTest.kt` (modify: its private log helper moves to `TestLogs.kt`)

**Interfaces:**
- Consumes: `WeatherProvider`, `Forecast`, `DailyWeather`, `HourlyWeather`, `Condition`, `WeatherUnavailableException` (Task 3); `SunTimes`, `ProviderDescriptor`, `Feature`, `WallClock` (`:core:plugin`; `WallClock` is bound by `:app`'s `AppModule`).
- Produces:
  - `const val OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"`; `const val OPEN_METEO_PROVIDER_ID = "weather.openmeteo"`; `internal const val MAX_FORECAST_BYTES = 1_048_576L`
  - `class OpenMeteoForecast(url: HttpUrl, client: OkHttpClient, clock: WallClock) : WeatherProvider` — keeps only the `DAYS_FETCHED` (7) dates from today in the zone asked for; refuses an answer over `MAX_FORECAST_BYTES`
  - `internal fun readForecast(answer: ForecastAnswer, today: LocalDate): Forecast`; `internal fun wmoCondition(code: Int): Condition`; `internal fun sunTimes(date: LocalDate, sunrise: String?, sunset: String?): SunTimes?`
  - `internal class Answer(val code: Int, val body: String)`; `internal class BodyTooLarge : IOException`; `internal suspend fun Call.await(maxBytes: Long? = null): Answer` (moved; without `maxBytes` it reads as before)
  - `internal fun forecastClient(): OkHttpClient` (`di/OpenMeteoModule.kt`): 15 s connect, 30 s read (the calendar sync's), 60 s whole call
  - `OpenMeteoModule` also provides `WeatherProvider` `@IntoSet` `@Singleton`, `forecast(clock: WallClock)`, on `forecastClient()`
  - Test helper: `internal fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1)`

- [ ] **Step 1: Depend on the contract**

In `provider/weather-openmeteo/build.gradle.kts`, after `implementation(project(":core:setup"))` add:
```kotlin
    implementation(project(":capability:weather"))
```

In `app/build.gradle.kts`, after `implementation(project(":capability:calendar"))` add:
```kotlin
    // Wired by Hilt alone: :app's own code never names it (4b design D6).
    implementation(project(":capability:weather"))
```

- [ ] **Step 2: Add the recorded answer**

Create `provider/weather-openmeteo/src/test/resources/forecast_london.json` with exactly this one line (Open-Meteo's answer for central London on 1 October 2026, for the request in §3.4, trimmed to its first 48 hours):
```json
{"latitude":51.51147,"longitude":-0.13078308,"generationtime_ms":0.5037784576416016,"utc_offset_seconds":3600,"timezone":"Europe/London","timezone_abbreviation":"GMT+1","elevation":16.0,"hourly_units":{"time":"iso8601","temperature_2m":"°C","weather_code":"wmo code"},"hourly":{"time":["2026-10-01T00:00","2026-10-01T01:00","2026-10-01T02:00","2026-10-01T03:00","2026-10-01T04:00","2026-10-01T05:00","2026-10-01T06:00","2026-10-01T07:00","2026-10-01T08:00","2026-10-01T09:00","2026-10-01T10:00","2026-10-01T11:00","2026-10-01T12:00","2026-10-01T13:00","2026-10-01T14:00","2026-10-01T15:00","2026-10-01T16:00","2026-10-01T17:00","2026-10-01T18:00","2026-10-01T19:00","2026-10-01T20:00","2026-10-01T21:00","2026-10-01T22:00","2026-10-01T23:00","2026-10-02T00:00","2026-10-02T01:00","2026-10-02T02:00","2026-10-02T03:00","2026-10-02T04:00","2026-10-02T05:00","2026-10-02T06:00","2026-10-02T07:00","2026-10-02T08:00","2026-10-02T09:00","2026-10-02T10:00","2026-10-02T11:00","2026-10-02T12:00","2026-10-02T13:00","2026-10-02T14:00","2026-10-02T15:00","2026-10-02T16:00","2026-10-02T17:00","2026-10-02T18:00","2026-10-02T19:00","2026-10-02T20:00","2026-10-02T21:00","2026-10-02T22:00","2026-10-02T23:00"],"temperature_2m":[18.3,16.7,16.1,15.6,15.5,14.8,14.4,14.2,14.0,14.9,15.9,17.2,18.7,19.4,19.9,20.3,20.3,19.9,19.5,18.9,18.2,17.5,16.7,15.8,15.2,14.7,14.3,14.0,13.9,13.7,13.8,13.7,13.8,14.8,16.8,18.3,19.2,19.1,19.8,19.8,20.1,19.7,19.2,18.3,17.6,17.1,16.6,16.2],"weather_code":[2,51,3,3,2,3,0,2,2,1,1,1,2,2,2,2,2,2,2,1,0,0,2,0,2,0,0,0,0,0,0,1,0,0,0,2,3,3,3,3,2,2,2,1,3,3,3,3]},"daily_units":{"time":"iso8601","weather_code":"wmo code","temperature_2m_max":"°C","temperature_2m_min":"°C","sunrise":"iso8601","sunset":"iso8601"},"daily":{"time":["2026-10-01","2026-10-02","2026-10-03","2026-10-04","2026-10-05","2026-10-06","2026-10-07"],"weather_code":[51,3,51,45,3,3,51],"temperature_2m_max":[20.3,20.1,19.9,20.8,21.2,20.2,17.9],"temperature_2m_min":[14.0,13.7,13.7,13.8,16.1,14.6,12.3],"sunrise":["2026-10-01T07:01","2026-10-02T07:02","2026-10-03T07:04","2026-10-04T07:06","2026-10-05T07:07","2026-10-06T07:09","2026-10-07T07:10"],"sunset":["2026-10-01T18:38","2026-10-02T18:36","2026-10-03T18:33","2026-10-04T18:31","2026-10-05T18:29","2026-10-06T18:26","2026-10-07T18:24"]}}
```
Save it as UTF-8 (it holds "°").

- [ ] **Step 3: Share the log check**

Create `provider/weather-openmeteo/src/test/java/uk/co/siland/culvery/provider/weather_openmeteo/TestLogs.kt`:
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo

import com.google.common.truth.Truth.assertWithMessage
import org.robolectric.shadows.ShadowLog

/**
 * Nothing logged under [tag], with its whole chain of causes, holds any of [secrets]; at least [minLines] were logged,
 * so a check that saw no log can't pass by default.
 */
internal fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1) {
    val logs = ShadowLog.getLogs().filter { it.tag == tag }
    assertWithMessage("lines logged under $tag").that(logs.size).isAtLeast(minLines)
    logs.forEach { log ->
        val text = "${log.msg} ${generateSequence(log.throwable) { it.cause }.joinToString(" ")}"
        secrets.forEach { assertWithMessage(text).that(text).doesNotContain(it) }
    }
}
```
In `provider/weather-openmeteo/src/test/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearchTest.kt`, delete the private `assertNoSecretsLogged` function at the end of the file (with its KDoc) and the import `com.google.common.truth.Truth.assertWithMessage`.

- [ ] **Step 4: Write the failing test**

Create `provider/weather-openmeteo/src/test/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoForecastTest.kt`:
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.Forecast
import uk.co.siland.culvery.capability.weather.HourlyWeather
import uk.co.siland.culvery.capability.weather.WeatherUnavailableException
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.provider.weather_openmeteo.di.forecastClient

/** One day and one hour, as the members of `daily` and `hourly`. */
private const val DAILY_ONE =
    """ "time":["2026-10-01"],"weather_code":[3],"temperature_2m_max":[19.0],"temperature_2m_min":[11.0],"sunrise":["2026-10-01T07:01"],"sunset":["2026-10-01T18:38"]"""
private const val HOURLY_ONE = """ "time":["2026-10-01T00:00"],"temperature_2m":[17.0],"weather_code":[3]"""

// Robolectric for android.util.Log; the server is a real MockWebServer on localhost.
@RunWith(AndroidJUnit4::class)
class OpenMeteoForecastTest {
    private lateinit var server: MockWebServer
    private lateinit var forecast: OpenMeteoForecast
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()

    // 10:00 on Thursday 1 October 2026 in London: the week asked for is 1–7 October, the fixture's.
    private val clock = WallClock { Instant.parse("2026-10-01T09:00:00Z").toEpochMilli() }

    @Before
    fun setUp() {
        ShadowLog.clear()
        server = MockWebServer()
        server.start()
        forecast = OpenMeteoForecast(server.url("/v1/forecast"), client, clock)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun answer(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun fixture(): String = checkNotNull(javaClass.classLoader?.getResource("forecast_london.json")) { "fixture missing" }.readText()

    private fun small(daily: String = DAILY_ONE, hourly: String = HOURLY_ONE) = """{"daily":{$daily},"hourly":{$hourly}}"""

    private suspend fun london(): Forecast = forecast.forecast(51.5074, -0.1278, ZoneId.of("Europe/London"))

    private suspend fun failure(): Throwable? =
        try {
            london()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e
        }

    @Test
    fun itAsksForSevenDaysOfDailyAndHourlyWeatherInTheZone() = runTest {
        answer(fixture())
        london()
        val url = server.takeRequest().requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/v1/forecast")
        assertThat(listOf("latitude", "longitude", "timezone", "forecast_days", "daily", "hourly").map { url.queryParameter(it) })
            .containsExactly(
                "51.5074",
                "-0.1278",
                "Europe/London",
                "7",
                "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset",
                "temperature_2m,weather_code",
            ).inOrder()
    }

    @Test
    fun theRecordedAnswerReadsIntoDaysAndHours() = runTest {
        answer(fixture())
        val f = london()
        assertThat(f.days).hasSize(7)
        assertThat(f.days.first())
            .isEqualTo(DailyWeather(LocalDate.of(2026, 10, 1), Condition.DRIZZLE, 20.3, 14.0, LocalTime.of(7, 1), LocalTime.of(18, 38)))
        assertThat(f.days[3])
            .isEqualTo(DailyWeather(LocalDate.of(2026, 10, 4), Condition.FOG, 20.8, 13.8, LocalTime.of(7, 6), LocalTime.of(18, 31)))
        assertThat(f.days.last().date).isEqualTo(LocalDate.of(2026, 10, 7))
        assertThat(f.hours).hasSize(48)
        assertThat(f.hours[0]).isEqualTo(HourlyWeather(LocalDateTime.of(2026, 10, 1, 0, 0), Condition.PARTLY_CLOUDY, 18.3))
        assertThat(f.hours[1]).isEqualTo(HourlyWeather(LocalDateTime.of(2026, 10, 1, 1, 0), Condition.DRIZZLE, 16.7))
        assertThat(f.hours.last().start).isEqualTo(LocalDateTime.of(2026, 10, 2, 23, 0))
    }

    @Test
    fun wmoCodesMapOntoConditions() {
        val expected = mapOf(
            0 to Condition.CLEAR,
            1 to Condition.PARTLY_CLOUDY, 2 to Condition.PARTLY_CLOUDY,
            3 to Condition.CLOUDY,
            45 to Condition.FOG, 48 to Condition.FOG,
            51 to Condition.DRIZZLE, 53 to Condition.DRIZZLE, 55 to Condition.DRIZZLE, 56 to Condition.DRIZZLE, 57 to Condition.DRIZZLE,
            61 to Condition.RAIN, 63 to Condition.RAIN, 65 to Condition.RAIN, 66 to Condition.RAIN, 67 to Condition.RAIN,
            71 to Condition.SNOW, 73 to Condition.SNOW, 75 to Condition.SNOW, 77 to Condition.SNOW, 85 to Condition.SNOW, 86 to Condition.SNOW,
            80 to Condition.SHOWERS, 81 to Condition.SHOWERS, 82 to Condition.SHOWERS,
            95 to Condition.THUNDER, 96 to Condition.THUNDER, 99 to Condition.THUNDER,
            // Anything else is cloudy.
            4 to Condition.CLOUDY, 44 to Condition.CLOUDY, 100 to Condition.CLOUDY, -1 to Condition.CLOUDY,
        )
        assertThat(expected.keys.associateWith(::wmoCondition)).isEqualTo(expected)
    }

    /** Ruling 1: Open-Meteo gives a polar night as midnight to midnight, and a polar day as midnight to the next midnight. */
    @Test
    fun aPolarDayOrNightHasNoSunTimes() = runTest {
        answer(
            small(
                daily = """ "time":["2026-10-01","2026-10-02"],"weather_code":[71,0],"temperature_2m_max":[-0.5,9.0],"temperature_2m_min":[-6.0,3.0],"sunrise":["2026-10-01T00:00","2026-10-02T00:00"],"sunset":["2026-10-01T00:00","2026-10-03T00:00"]""",
            ),
        )
        val days = london().days
        assertThat(days.map { it.sunrise }).containsExactly(null, null)
        assertThat(days.map { it.sunset }).containsExactly(null, null)
    }

    /** Review Focus 4: a null entry drops that day or hour; a null sun time leaves the day without sun times. */
    @Test
    fun nullEntriesDropThatDayOrHourAndNothingElse() = runTest {
        answer(
            small(
                daily = """ "time":["2026-10-01","2026-10-02","2026-10-03","2026-10-04"],"weather_code":[3,null,61,2],"temperature_2m_max":[19.0,18.0,null,17.0],"temperature_2m_min":[11.0,10.0,9.0,8.0],"sunrise":["2026-10-01T07:01","2026-10-02T07:02","2026-10-03T07:04",null],"sunset":["2026-10-01T18:38","2026-10-02T18:36","2026-10-03T18:33","2026-10-04T18:31"]""",
                hourly = """ "time":["2026-10-01T00:00","2026-10-01T01:00","2026-10-01T02:00"],"temperature_2m":[17.0,null,15.0],"weather_code":[3,3,null]""",
            ),
        )
        val f = london()
        assertThat(f.days).containsExactly(
            DailyWeather(LocalDate.of(2026, 10, 1), Condition.CLOUDY, 19.0, 11.0, LocalTime.of(7, 1), LocalTime.of(18, 38)),
            DailyWeather(LocalDate.of(2026, 10, 4), Condition.PARTLY_CLOUDY, 17.0, 8.0, null, null),
        ).inOrder()
        assertThat(f.hours).containsExactly(HourlyWeather(LocalDateTime.of(2026, 10, 1, 0, 0), Condition.CLOUDY, 17.0))
    }

    @Test
    fun anErrorAnswerIsWeatherUnavailable() = runTest {
        answer("""{"reason":"Latitude must be in range of -90 to 90°.","error":true}""", code = 400)
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer("{}", code = 500)
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun aDroppedConnectionIsWeatherUnavailable() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun anUnreadableAnswerIsWeatherUnavailable() = runTest {
        answer("<html>Gateway</html>")
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer(small(daily = DAILY_ONE.replace("[19.0]", "[\"warm\"]")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer(small(hourly = HOURLY_ONE.replace("2026-10-01T00:00", "yesterday")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun aMissingArrayIsWeatherUnavailable() = runTest {
        answer("""{"daily":{$DAILY_ONE}}""")
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer(small(daily = DAILY_ONE.substringBefore(""","sunset"""")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun arraysOfUnequalLengthAreWeatherUnavailable() = runTest {
        answer(small(daily = DAILY_ONE.replace(""""time":["2026-10-01"]""", """"time":["2026-10-01","2026-10-02"]""")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        answer(small(hourly = HOURLY_ONE.replace(""""weather_code":[3]""", """"weather_code":[3,3]""")))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    @Test
    fun cancellingTheForecastCancelsTheCall() = runTest {
        // Its own client with a long read timeout, so only cancelling the call can end it inside the second.
        val patient = OkHttpClient.Builder().readTimeout(Duration.ofSeconds(30)).build()
        val slow = OpenMeteoForecast(server.url("/v1/forecast"), patient, clock)
        // One byte every 3 s: the headers arrive, the body stalls.
        server.enqueue(MockResponse().setBody(fixture()).throttleBody(1, 3, TimeUnit.SECONDS))
        val returned = withContext(Dispatchers.Default) {
            val call = launch { slow.forecast(51.5074, -0.1278, ZoneId.of("Europe/London")) }
            checkNotNull(runInterruptible(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }) { "the forecast never reached the server" }
            call.cancel()
            withTimeoutOrNull(1_000) { call.join() } != null
        }
        assertThat(returned).isTrue()
        withContext(Dispatchers.Default) { withTimeout(1_000) { while (patient.dispatcher.runningCallsCount() > 0) delay(10) } }
    }

    @Test
    fun theForecastClientGivesUpOnAWholeCallAfterSixtySeconds() {
        assertThat(forecastClient().callTimeoutMillis).isEqualTo(60_000)
    }

    /** Plan review 2: each byte arrives inside the read timeout, so only the whole-call limit can end it. */
    @Test
    fun aDrippingBodyEndsAsWeatherUnavailable() = runTest {
        // The module's client with its 60 s call limit cut to 1 s, so the test doesn't wait a minute.
        val hurried = forecastClient().newBuilder().callTimeout(Duration.ofSeconds(1)).build()
        val dripping = OpenMeteoForecast(server.url("/v1/forecast"), hurried, clock)
        server.enqueue(MockResponse().setBody(fixture()).throttleBody(1, 300, TimeUnit.MILLISECONDS))
        val thrown = withContext(Dispatchers.Default) {
            withTimeout(10_000) { runCatching { dripping.forecast(51.5074, -0.1278, ZoneId.of("Europe/London")) }.exceptionOrNull() }
        }
        assertThat(thrown).isInstanceOf(WeatherUnavailableException::class.java)
    }

    /** Plan review 13. */
    @Test
    fun anAnswerOverOneMebibyteIsWeatherUnavailable() = runTest {
        val tooBig = "x".repeat(MAX_FORECAST_BYTES.toInt() + 1)
        answer(tooBig)
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
        // Without a length up front, the read still stops one byte past the limit.
        server.enqueue(MockResponse().setChunkedBody(tooBig, 64 * 1024))
        assertThat(failure()).isInstanceOf(WeatherUnavailableException::class.java)
    }

    /** Plan review 13: only the seven dates from today in the zone asked for are kept. */
    @Test
    fun daysAndHoursOutsideTheSevenAskedForAreDropped() = runTest {
        answer(
            small(
                daily = """ "time":["2026-09-30","2026-10-01","2026-10-08"],"weather_code":[3,3,3],"temperature_2m_max":[19.0,19.0,19.0],"temperature_2m_min":[11.0,11.0,11.0],"sunrise":[null,null,null],"sunset":[null,null,null]""",
                hourly = """ "time":["2026-09-30T23:00","2026-10-01T00:00","2026-10-07T23:00","2026-10-08T00:00"],"temperature_2m":[17.0,17.0,17.0,17.0],"weather_code":[3,3,3,3]""",
            ),
        )
        val f = london()
        assertThat(f.days.map { it.date }).containsExactly(LocalDate.of(2026, 10, 1))
        assertThat(f.hours.map { it.start })
            .containsExactly(LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 7, 23, 0))
            .inOrder()
    }

    @Test
    fun nothingLoggedHoldsTheCoordinatesOrTheZone() = runTest {
        answer("{}", code = 500)
        failure()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        failure()
        answer("""{"latitude":51.51147,"daily":{"time":"Europe/London"}}""")
        failure()
        assertNoSecretsLogged(
            TAG,
            listOf("51.5074", "51.51", "-0.1278", "0.1278", "Europe/London", "Europe%2FLondon", "London", "latitude"),
            minLines = 3,
        )
    }
}
```

- [ ] **Step 5: Run the test to see it fail**

Run: `./gradlew :provider:weather-openmeteo:testDebugUnitTest`
Expected: FAIL to compile with "Unresolved reference 'OpenMeteoForecast'", "'wmoCondition'".

- [ ] **Step 6: Move `Call.await` into its own file**

Create `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/Http.kt`:
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody

internal class Answer(val code: Int, val body: String)

/** A body longer than the caller allows. An IOException, so the caller treats it as a failed call. */
internal class BodyTooLarge : IOException("The answer is longer than allowed")

/**
 * Enqueues the call and suspends until its whole body is in, reading it on OkHttp's thread (3a's `Call.await`):
 * cancelling the coroutine cancels the call, which ends a stalled read at once. With [maxBytes], a longer body is
 * [BodyTooLarge], read no further than one byte past the limit.
 */
internal suspend fun Call.await(maxBytes: Long? = null): Answer = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = try {
                    response.use { Answer(it.code, it.body?.let { body -> read(body, maxBytes) }.orEmpty()) }
                } catch (e: Throwable) {
                    // After a cancel this is the closed socket and the continuation is already cancelled; anything else
                    // must reach the caller, or it would hang.
                    cont.resumeWithException(e)
                    return
                }
                cont.resume(answer)
            }
        },
    )
}

private fun read(body: ResponseBody, maxBytes: Long?): String {
    if (maxBytes == null) return body.string()
    if (body.contentLength() > maxBytes) throw BodyTooLarge()
    val source = body.source()
    if (source.request(maxBytes + 1)) throw BodyTooLarge()
    return source.buffer.readUtf8()
}
```

Replace the whole of `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoLocationSearch.kt` with (the same search, without `Answer` and `Call.await`):
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.core.setup.LocationSearchException
import uk.co.siland.culvery.core.setup.PlaceMatch

/** Open-Meteo's geocoding API: no key (4a design D2). */
const val OPEN_METEO_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"

/** The module's one log tag. */
internal const val TAG = "OpenMeteo"

private const val RESULTS = "5"

@Serializable
internal data class GeocodingAnswer(val results: List<GeocodingPlace> = emptyList())

@Serializable
internal data class GeocodingPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val admin1: String? = null,
    val country: String? = null,
)

private val GeocodingJson = Json { ignoreUnknownKeys = true }

/**
 * Town search over Open-Meteo's geocoding (4a design §3.8): up to five towns, English names; one without a time zone is
 * left out, as a home needs one. Nothing logged names the query, a town or its coordinates.
 */
class OpenMeteoLocationSearch(private val url: HttpUrl, private val client: OkHttpClient) : LocationSearch {
    override suspend fun search(query: String): List<PlaceMatch> {
        val request = Request.Builder()
            .url(
                url.newBuilder()
                    .addQueryParameter("name", query)
                    .addQueryParameter("count", RESULTS)
                    .addQueryParameter("language", "en")
                    .addQueryParameter("format", "json")
                    .build(),
            )
            .build()
        val answer = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "couldn't search".
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Town search failed (${e::class.simpleName})")
            throw LocationSearchException("Couldn't reach the town search")
        }
        if (answer.code !in 200..299) {
            Log.w(TAG, "Town search answered ${answer.code}")
            throw LocationSearchException("The town search answered ${answer.code}")
        }
        val parsed = try {
            GeocodingJson.decodeFromString(GeocodingAnswer.serializer(), answer.body)
        } catch (e: IllegalArgumentException) {
            // Not the exception itself: kotlinx.serialization quotes the body, which holds the towns.
            Log.w(TAG, "Town search sent an answer the tablet can't read (${e::class.simpleName})")
            throw LocationSearchException("The town search sent an answer the tablet can't read")
        }
        return parsed.results.mapNotNull { place ->
            place.timezone?.let { zone -> PlaceMatch(place.name, place.admin1, place.country, place.latitude, place.longitude, zone) }
        }
    }
}
```

- [ ] **Step 7: Write the forecast**

Create `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/OpenMeteoForecast.kt`:
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo

import android.util.Log
import java.io.IOException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.Forecast
import uk.co.siland.culvery.capability.weather.HourlyWeather
import uk.co.siland.culvery.capability.weather.WeatherProvider
import uk.co.siland.culvery.capability.weather.WeatherUnavailableException
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.core.plugin.WallClock

/** Open-Meteo's forecast API: no key (4b design D7). */
const val OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

const val OPEN_METEO_PROVIDER_ID = "weather.openmeteo"

/** The real answer is about 6 KB; anything past this isn't a forecast (plan review 13). */
internal const val MAX_FORECAST_BYTES = 1_048_576L

private const val DAYS_FETCHED = 7
private const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
private const val HOURLY = "temperature_2m,weather_code"

@Serializable
internal data class ForecastAnswer(val daily: DailyArrays? = null, val hourly: HourlyArrays? = null)

@Serializable
internal data class DailyArrays(
    val time: List<String?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
    @SerialName("temperature_2m_max") val high: List<Double?>? = null,
    @SerialName("temperature_2m_min") val low: List<Double?>? = null,
    val sunrise: List<String?>? = null,
    val sunset: List<String?>? = null,
)

@Serializable
internal data class HourlyArrays(
    val time: List<String?>? = null,
    @SerialName("temperature_2m") val temperature: List<Double?>? = null,
    @SerialName("weather_code") val weatherCode: List<Int?>? = null,
)

/** A required array is missing, or the arrays of one block differ in length (§3.4). */
internal class Unreadable : Exception("A forecast array is missing or the wrong length")

private val ForecastJson = Json { ignoreUnknownKeys = true }

/**
 * Seven days of daily and hourly weather, in local times of the zone asked for (4b design §3.4). Every failure is a
 * [WeatherUnavailableException] with fixed words and no cause; nothing logged holds the URL, the body, the coordinates
 * or the zone.
 */
class OpenMeteoForecast(
    private val url: HttpUrl,
    private val client: OkHttpClient,
    private val clock: WallClock,
) : WeatherProvider {
    override val descriptor = ProviderDescriptor(
        id = OPEN_METEO_PROVIDER_ID,
        displayName = "Open-Meteo",
        icon = "partly_cloudy_day",
        features = setOf(Feature.READ),
    )

    override suspend fun forecast(latitude: Double, longitude: Double, zone: ZoneId): Forecast {
        val request = Request.Builder()
            .url(
                url.newBuilder()
                    .addQueryParameter("latitude", latitude.toString())
                    .addQueryParameter("longitude", longitude.toString())
                    .addQueryParameter("timezone", zone.id)
                    .addQueryParameter("forecast_days", DAYS_FETCHED.toString())
                    .addQueryParameter("daily", DAILY)
                    .addQueryParameter("hourly", HOURLY)
                    .build(),
            )
            .build()
        val answer = try {
            client.newCall(request).await(MAX_FORECAST_BYTES)
        } catch (e: IOException) {
            // A cancelled caller gets its cancellation, never "unavailable".
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Forecast failed (${e::class.simpleName})")
            throw WeatherUnavailableException("Couldn't reach the forecast")
        }
        if (answer.code !in 200..299) {
            Log.w(TAG, "Forecast answered ${answer.code}")
            throw WeatherUnavailableException("The forecast answered ${answer.code}")
        }
        val today = Instant.ofEpochMilli(clock.nowMillis()).atZone(zone).toLocalDate()
        return try {
            readForecast(ForecastJson.decodeFromString(ForecastAnswer.serializer(), answer.body), today)
        } catch (e: IllegalArgumentException) {
            throw unreadable(e)
        } catch (e: DateTimeException) {
            throw unreadable(e)
        } catch (e: Unreadable) {
            throw unreadable(e)
        }
    }

    private fun unreadable(e: Exception): WeatherUnavailableException {
        // The type only: kotlinx.serialization and java.time quote the input, which holds the coordinates.
        Log.w(TAG, "Forecast sent an answer the tablet can't read (${e::class.simpleName})")
        return WeatherUnavailableException("The forecast sent an answer the tablet can't read")
    }
}

/**
 * Keeps only the [DAYS_FETCHED] dates from [today]: nothing else was asked for. Throws [Unreadable],
 * `IllegalArgumentException` or `DateTimeException` for an answer it can't read.
 */
internal fun readForecast(answer: ForecastAnswer, today: LocalDate): Forecast {
    val asked = today..today.plusDays(DAYS_FETCHED - 1L)
    val daily = answer.daily ?: throw Unreadable()
    val hourly = answer.hourly ?: throw Unreadable()
    val dates = daily.time.required()
    val dayCodes = daily.weatherCode.required(dates.size)
    val highs = daily.high.required(dates.size)
    val lows = daily.low.required(dates.size)
    val sunrises = daily.sunrise.required(dates.size)
    val sunsets = daily.sunset.required(dates.size)
    val starts = hourly.time.required()
    val temperatures = hourly.temperature.required(starts.size)
    val hourCodes = hourly.weatherCode.required(starts.size)

    // A null entry (past a model's horizon) drops that day or hour, never the whole answer.
    val days = dates.indices.mapNotNull { i ->
        val date = dates[i]?.let { LocalDate.parse(it) }?.takeIf { it in asked } ?: return@mapNotNull null
        val code = dayCodes[i] ?: return@mapNotNull null
        val high = highs[i] ?: return@mapNotNull null
        val low = lows[i] ?: return@mapNotNull null
        val sun = sunTimes(date, sunrises[i], sunsets[i])
        DailyWeather(date, wmoCondition(code), high, low, sun?.sunrise, sun?.sunset)
    }
    val hours = starts.indices.mapNotNull { i ->
        val start = starts[i]?.let { LocalDateTime.parse(it) }?.takeIf { it.toLocalDate() in asked } ?: return@mapNotNull null
        val code = hourCodes[i] ?: return@mapNotNull null
        val temperature = temperatures[i] ?: return@mapNotNull null
        HourlyWeather(start, wmoCondition(code), temperature)
    }
    return Forecast(days, hours)
}

private fun <T> List<T>?.required(): List<T> = this ?: throw Unreadable()

private fun <T> List<T>?.required(size: Int): List<T> = required().also { if (it.size != size) throw Unreadable() }

/**
 * Both times when the sun rises and sets on [date], else null (ruling 1): Open-Meteo gives a polar night as midnight to
 * midnight and a polar day as midnight to the next day's midnight.
 */
internal fun sunTimes(date: LocalDate, sunrise: String?, sunset: String?): SunTimes? {
    val rise = sunrise?.let { LocalDateTime.parse(it) } ?: return null
    val set = sunset?.let { LocalDateTime.parse(it) } ?: return null
    if (rise.toLocalDate() != date || set.toLocalDate() != date || rise >= set) return null
    return SunTimes(rise.toLocalTime(), set.toLocalTime())
}

/** WMO weather codes (4b design §3.4). */
internal fun wmoCondition(code: Int): Condition = when (code) {
    0 -> Condition.CLEAR
    1, 2 -> Condition.PARTLY_CLOUDY
    3 -> Condition.CLOUDY
    45, 48 -> Condition.FOG
    in 51..57 -> Condition.DRIZZLE
    in 61..67 -> Condition.RAIN
    in 71..77, 85, 86 -> Condition.SNOW
    in 80..82 -> Condition.SHOWERS
    in 95..99 -> Condition.THUNDER
    else -> Condition.CLOUDY
}
```

Replace the whole of `provider/weather-openmeteo/src/main/java/uk/co/siland/culvery/provider/weather_openmeteo/di/OpenMeteoModule.kt` with:
```kotlin
package uk.co.siland.culvery.provider.weather_openmeteo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.Duration
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import uk.co.siland.culvery.capability.weather.WeatherProvider
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.setup.LocationSearch
import uk.co.siland.culvery.provider.weather_openmeteo.OPEN_METEO_FORECAST_URL
import uk.co.siland.culvery.provider.weather_openmeteo.OPEN_METEO_GEOCODING_URL
import uk.co.siland.culvery.provider.weather_openmeteo.OpenMeteoForecast
import uk.co.siland.culvery.provider.weather_openmeteo.OpenMeteoLocationSearch

// A search is typed live: give up sooner than the calendar's sync does.
private val SEARCH_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val SEARCH_READ_TIMEOUT: Duration = Duration.ofSeconds(15)

// The forecast runs in the background, as the calendar's sync does: its timeouts, and a whole-call limit, so a body
// that drips in inside the read timeout can't hold the loop (plan review 2).
private val FORECAST_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
private val FORECAST_READ_TIMEOUT: Duration = Duration.ofSeconds(30)
private val FORECAST_CALL_TIMEOUT: Duration = Duration.ofSeconds(60)

internal fun forecastClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(FORECAST_CONNECT_TIMEOUT)
    .readTimeout(FORECAST_READ_TIMEOUT)
    .callTimeout(FORECAST_CALL_TIMEOUT)
    .build()

@Module
@InstallIn(SingletonComponent::class)
object OpenMeteoModule {
    // Each client stays inside its binding, as Google's does (4a ruling 3): no OkHttpClient in the graph to collide with.
    @Provides
    @Singleton
    fun locationSearch(): LocationSearch = OpenMeteoLocationSearch(
        OPEN_METEO_GEOCODING_URL.toHttpUrl(),
        OkHttpClient.Builder().connectTimeout(SEARCH_CONNECT_TIMEOUT).readTimeout(SEARCH_READ_TIMEOUT).build(),
    )

    @Provides
    @Singleton
    @IntoSet
    fun forecast(clock: WallClock): WeatherProvider = OpenMeteoForecast(OPEN_METEO_FORECAST_URL.toHttpUrl(), forecastClient(), clock)
}
```

- [ ] **Step 8: Run the tests to see them pass**

Run: `./gradlew :provider:weather-openmeteo:testDebugUnitTest`
Expected: PASS (16 in `OpenMeteoForecastTest`; the 7 `OpenMeteoLocationSearchTest` cases unchanged).

- [ ] **Step 9: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Hilt now holds a `Set<WeatherProvider>` with Open-Meteo's forecast in it; nothing asks for it until Task 9.

- [ ] **Step 10: Commit**

```bash
git add provider/weather-openmeteo app/build.gradle.kts
git commit -m "Fetch seven days of daily and hourly weather from Open-Meteo in the household's zone"
```

---

### Task 6: `WeatherFetcher` and `WeatherSyncLoop` (§3.6, §5; rulings 7, 8, 14)

**Review:** opus (a loop that must never stop or spin; the place a fetch is stored under).

**Files:**
- Create: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherFetcher.kt`, `WeatherSyncLoop.kt`
- Test: `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ScriptedWeatherProvider.kt`, `TestLogs.kt`, `WeatherFetcherTest.kt`, `WeatherSyncLoopTest.kt` (create)

**Interfaces:**
- Consumes: `WeatherProvider`, `WeatherPlace`, `WeatherStore.replace` (Tasks 3, 4); `HouseholdRepository.location`; `WallClock`, `@ApplicationScope CoroutineScope`, `Startable`, `retryWithBackoff` (`:core:plugin`).
- Produces:
  - `internal const val TAG = "Weather"` (the capability's one log tag)
  - `class WeatherFetcher @Inject constructor(providers: Set<@JvmSuppressWildcards WeatherProvider>, store: WeatherStore, clock: WallClock) { suspend fun fetch(place: WeatherPlace) }` (unscoped: only the loop holds one) — asks the first provider by `descriptor.id` in `place.zone` and stores under `place`; does nothing without a provider; throws on any failure, leaving the store alone
  - `const val WEATHER_REFRESH_MS = 30 * 60_000L`; `const val WEATHER_RETRY_MS = 5 * 60_000L`
  - `internal fun Flow<HomeLocation?>.places(): Flow<WeatherPlace?>`
  - `@Singleton class WeatherSyncLoop internal constructor(fetch: suspend (WeatherPlace) -> Unit, places: Flow<WeatherPlace?>, scope: CoroutineScope) : Startable`; `@Inject constructor(fetcher: WeatherFetcher, household: HouseholdRepository, @ApplicationScope scope: CoroutineScope)` — wakes only when the place really changes, however often the location read is retried
  - Test helpers: `internal class ScriptedWeatherProvider(id: String = "weather.test", answer: () -> Forecast = { forecast() })` with `val asked: MutableList<Triple<Double, Double, ZoneId>>`; `internal fun assertNoSecretsLogged(tag, secrets, minLines = 1)`

- [ ] **Step 1: Write the test helpers**

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ScriptedWeatherProvider.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import java.time.ZoneId
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor

/** Answers with [answer] (which may throw) and records each request. */
internal class ScriptedWeatherProvider(id: String = "weather.test", private val answer: () -> Forecast = { forecast() }) : WeatherProvider {
    override val descriptor = ProviderDescriptor(id, id, "cloud", setOf(Feature.READ))
    val asked = mutableListOf<Triple<Double, Double, ZoneId>>()

    override suspend fun forecast(latitude: Double, longitude: Double, zone: ZoneId): Forecast {
        asked += Triple(latitude, longitude, zone)
        return answer()
    }
}
```

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/TestLogs.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import com.google.common.truth.Truth.assertWithMessage
import org.robolectric.shadows.ShadowLog

/**
 * Nothing logged under [tag], with its whole chain of causes, holds any of [secrets]; at least [minLines] were logged,
 * so a check that saw no log can't pass by default.
 */
internal fun assertNoSecretsLogged(tag: String, secrets: List<String>, minLines: Int = 1) {
    val logs = ShadowLog.getLogs().filter { it.tag == tag }
    assertWithMessage("lines logged under $tag").that(logs.size).isAtLeast(minLines)
    logs.forEach { log ->
        val text = "${log.msg} ${generateSequence(log.throwable) { it.cause }.joinToString(" ")}"
        secrets.forEach { assertWithMessage(text).that(text).doesNotContain(it) }
    }
}
```

- [ ] **Step 2: Write the failing tests**

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherFetcherTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for Room.
@RunWith(AndroidJUnit4::class)
class WeatherFetcherTest {
    private lateinit var db: WeatherDatabase
    private lateinit var store: WeatherStore
    private val clock = WallClock { 5_000L }
    private val deviceZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        // The tablet's own zone is not the household's (Review Focus 5).
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        db = weatherDb()
        store = WeatherStore(db)
    }

    @After
    fun tearDown() {
        db.close()
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun itStoresTheForecastUnderThePlaceItAskedFor() = runTest {
        val provider = ScriptedWeatherProvider()
        WeatherFetcher(setOf(provider), store, clock).fetch(WeatherPlace(LONDON))
        assertThat(provider.asked).containsExactly(Triple(51.5074, -0.1278, ZoneId.of("Europe/London")))
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(LONDON))
        assertThat(s.fetchedAtMillis).isEqualTo(5_000L)
        assertThat(s.days).isEqualTo(forecast().days)
    }

    @Test
    fun itAsksInTheHouseholdZoneNotTheDevices() = runTest {
        val provider = ScriptedWeatherProvider()
        WeatherFetcher(setOf(provider), store, clock).fetch(WeatherPlace(WELLINGTON))
        assertThat(provider.asked.single().third).isEqualTo(ZoneId.of("Pacific/Auckland"))
    }


    @Test
    fun theFirstProviderByIdIsAsked() = runTest {
        val b = ScriptedWeatherProvider("weather.b")
        val a = ScriptedWeatherProvider("weather.a")
        WeatherFetcher(setOf(b, a), store, clock).fetch(WeatherPlace(LONDON))
        assertThat(a.asked).hasSize(1)
        assertThat(b.asked).isEmpty()
    }

    @Test
    fun withNoProviderNothingIsFetchedOrStored() = runTest {
        WeatherFetcher(emptySet(), store, clock).fetch(WeatherPlace(LONDON))
        assertThat(store.stored.first()).isNull()
    }

    @Test
    fun aFailedFetchThrowsAndLeavesTheStoreAlone() = runTest {
        store.replace(WeatherPlace(LONDON), forecast(), 1_000L)
        val offline = ScriptedWeatherProvider(answer = { throw WeatherUnavailableException("offline") })
        val thrown = runCatching { WeatherFetcher(setOf(offline), store, clock).fetch(WeatherPlace(LEEDS)) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(WeatherUnavailableException::class.java)
        val s = store.stored.first()!!
        assertThat(s.place).isEqualTo(WeatherPlace(LONDON))
        assertThat(s.fetchedAtMillis).isEqualTo(1_000L)
    }
}
```

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherSyncLoopTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.household.HomeLocation

// Robolectric only because the loop logs through android.util.Log.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WeatherSyncLoopTest {
    private val london = WeatherPlace(LONDON)
    private val leeds = WeatherPlace(LEEDS)

    @Before
    fun setUp() = ShadowLog.clear()

    private fun TestScope.loop(places: Flow<WeatherPlace?>, fetch: suspend (WeatherPlace) -> Unit) =
        WeatherSyncLoop(fetch, places, backgroundScope).start()

    private fun TestScope.after(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun itFetchesAtOnceWhenALocationIsKnownThenEveryThirtyMinutes() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        loop(MutableStateFlow(london)) { fetched += it }
        runCurrent()
        assertThat(fetched).containsExactly(london)
        after(WEATHER_REFRESH_MS - 1)
        assertThat(fetched).hasSize(1)
        after(1)
        assertThat(fetched).containsExactly(london, london)
    }

    @Test
    fun withoutALocationItFetchesNothingUntilOneIsSet() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val places = MutableStateFlow<WeatherPlace?>(null)
        loop(places) { fetched += it }
        runCurrent()
        after(3 * WEATHER_REFRESH_MS)
        assertThat(fetched).isEmpty()
        places.value = london
        runCurrent()
        assertThat(fetched).containsExactly(london)
    }

    @Test
    fun aLocationChangeFetchesAtOnce() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val places = MutableStateFlow<WeatherPlace?>(london)
        loop(places) { fetched += it }
        runCurrent()
        after(60_000)
        places.value = leeds
        runCurrent()
        assertThat(fetched).containsExactly(london, leeds).inOrder()
    }

    @Test
    fun aRenamedTownIsNotFetchedAgain() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val locations = MutableStateFlow<HomeLocation?>(LONDON)
        loop(locations.places()) { fetched += it }
        runCurrent()
        locations.value = LONDON.copy(name = "Westminster")
        runCurrent()
        assertThat(fetched).hasSize(1)
        locations.value = LONDON.copy(timeZoneId = "Europe/Dublin")
        runCurrent()
        assertThat(fetched).hasSize(2)
    }

    @Test
    fun afterAFailureTheNextTryIsInFiveMinutesThenThirtyAfterASuccess() = runTest {
        var calls = 0
        loop(MutableStateFlow(london)) {
            calls++
            if (calls == 1) throw WeatherUnavailableException("offline")
        }
        runCurrent()
        assertThat(calls).isEqualTo(1)
        after(WEATHER_RETRY_MS - 1)
        assertThat(calls).isEqualTo(1)
        after(1)
        assertThat(calls).isEqualTo(2)
        after(WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(2)
        after(WEATHER_REFRESH_MS - WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(3)
    }

    @Test
    fun repeatedFailuresTryAtMostOnceEveryFiveMinutes() = runTest {
        var calls = 0
        loop(MutableStateFlow(london)) {
            calls++
            throw WeatherUnavailableException("offline")
        }
        runCurrent()
        after(60 * 60_000L)
        assertThat(calls).isEqualTo(1 + 12)
    }

    @Test
    fun anErrorIsLoggedByTypeAndTheLoopGoesOn() = runTest {
        var calls = 0
        loop(MutableStateFlow(london)) {
            calls++
            if (calls == 1) throw StackOverflowError("a provider recursed")
        }
        runCurrent()
        after(WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(2)
        assertThat(ShadowLog.getLogs().filter { it.tag == TAG }.map { it.msg }).contains("Weather fetch failed (StackOverflowError)")
    }

    @Test
    fun aStrayCancellationDoesNotStopTheLoop() = runTest {
        var calls = 0
        loop(MutableStateFlow(london)) {
            calls++
            if (calls == 1) throw CancellationException("stray")
        }
        runCurrent()
        after(WEATHER_RETRY_MS)
        assertThat(calls).isEqualTo(2)
    }

    /** Review Focus 1: the running fetch isn't cancelled; the new place is fetched straight after it. */
    @Test
    fun aLocationChangeMidFetchFetchesTheNewPlaceRightAfter() = runTest {
        val gate = CompletableDeferred<Unit>()
        val fetched = mutableListOf<WeatherPlace>()
        val places = MutableStateFlow<WeatherPlace?>(london)
        loop(places) {
            fetched += it
            if (fetched.size == 1) gate.await()
        }
        runCurrent()
        places.value = leeds
        runCurrent()
        assertThat(fetched).containsExactly(london)
        gate.complete(Unit)
        runCurrent()
        assertThat(fetched).containsExactly(london, leeds).inOrder()
    }

    /** Plan review 3: a location read that keeps failing after its value must not wake the loop on each retry. */
    @Test
    fun aFlakyLocationReadFetchesOnce() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        val places = flow {
            emit(london)
            throw IllegalStateException("store hiccup")
        }
        loop(places) { fetched += it }
        runCurrent()
        after(60_000)
        assertThat(fetched).containsExactly(london)
    }

    @Test
    fun aLocationReadThatFailsIsRetried() = runTest {
        val fetched = mutableListOf<WeatherPlace>()
        var reads = 0
        val places = flow {
            if (reads++ == 0) throw IllegalStateException("store hiccup")
            emit(london)
        }
        loop(places) { fetched += it }
        runCurrent()
        assertThat(fetched).isEmpty()
        after(1_000)
        assertThat(fetched).containsExactly(london)
    }

    @Test
    fun nothingLoggedHoldsThePlace() = runTest {
        loop(MutableStateFlow(london)) { throw WeatherUnavailableException("offline at 51.5074,-0.1278 Europe/London") }
        runCurrent()
        after(WEATHER_RETRY_MS)
        assertNoSecretsLogged(TAG, listOf("51.5074", "-0.1278", "Europe/London", "London"), minLines = 2)
    }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:weather:testDebugUnitTest --tests "*WeatherFetcherTest*" --tests "*WeatherSyncLoopTest*"`
Expected: FAIL to compile with "Unresolved reference 'WeatherFetcher'", "'WeatherSyncLoop'", "'WEATHER_REFRESH_MS'", "'places'", "'TAG'".

- [ ] **Step 4: Write the fetcher**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherFetcher.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import javax.inject.Inject
import uk.co.siland.culvery.core.plugin.WallClock

/** One fetch: the forecast for a place, stored under that place (4b design §3.5, ruling 7). */
class WeatherFetcher @Inject constructor(
    providers: Set<@JvmSuppressWildcards WeatherProvider>,
    private val store: WeatherStore,
    private val clock: WallClock,
) {
    // With more than one bound, the first by id (§3.1).
    private val provider: WeatherProvider? = providers.minByOrNull { it.descriptor.id }

    /**
     * Asks for [place]'s forecast in its zone and stores it under [place], not the location now: a fetch that lands after
     * a move then never matches. Does nothing without a provider. Throws on any failure, leaving the store as it was.
     */
    suspend fun fetch(place: WeatherPlace) {
        val provider = provider ?: return
        val forecast = provider.forecast(place.latitude, place.longitude, place.zone)
        store.replace(place, forecast, clock.nowMillis())
    }
}
```

- [ ] **Step 5: Write the loop**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherSyncLoop.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.retryWithBackoff

/** The capability's one log tag. */
internal const val TAG = "Weather"

const val WEATHER_REFRESH_MS = 30 * 60_000L
const val WEATHER_RETRY_MS = 5 * 60_000L

/** The place to fetch for: a renamed town is the same place. */
internal fun Flow<HomeLocation?>.places(): Flow<WeatherPlace?> = map { location -> location?.let(::WeatherPlace) }

/**
 * Fetches as soon as a location is known and whenever its coordinates or zone change, then every
 * [WEATHER_REFRESH_MS], or [WEATHER_RETRY_MS] after a failure (4b design §3.6). Without a location it waits and fetches
 * nothing. A change that arrives mid-fetch runs one more fetch afterwards; it never cancels the running one.
 */
@Singleton
class WeatherSyncLoop internal constructor(
    private val fetch: suspend (WeatherPlace) -> Unit,
    private val places: Flow<WeatherPlace?>,
    private val scope: CoroutineScope,
) : Startable {
    @Inject
    constructor(fetcher: WeatherFetcher, household: HouseholdRepository, @ApplicationScope scope: CoroutineScope) :
        this(fetcher::fetch, household.location.places(), scope)

    private val wake = Channel<Unit>(Channel.CONFLATED)

    override fun start() {
        scope.launch {
            val latest = MutableStateFlow<WeatherPlace?>(null)
            launch {
                // Distinct after the retry: a read that fails after its value re-emits it on every retry (plan review 3).
                places.retryWithBackoff { Log.w(TAG, "Couldn't read the home location (${it::class.simpleName}); retrying") }
                    .distinctUntilChanged()
                    .collect {
                        latest.value = it
                        wake.trySend(Unit)
                    }
            }
            // Null: wait for a location, with no timer.
            var wait: Long? = null
            while (true) {
                val due = wait
                if (due == null) wake.receive() else withTimeoutOrNull(due) { wake.receive() }
                val place = latest.value
                wait = when {
                    place == null -> null
                    runFetch(place) -> WEATHER_REFRESH_MS
                    else -> WEATHER_RETRY_MS
                }
            }
        }
    }

    private suspend fun runFetch(place: WeatherPlace): Boolean =
        try {
            fetch(place)
            true
        } catch (e: CancellationException) {
            // Stops the loop only if it was really cancelled; a stray one (an internal timeout) must not.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "A weather fetch was cancelled internally")
            false
        } catch (e: Throwable) {
            // An Error too, as the calendar's loop. The type only: a message could hold the place.
            Log.w(TAG, "Weather fetch failed (${e::class.simpleName})")
            false
        }
}
```

- [ ] **Step 6: Run the tests to see them pass**

Run: `./gradlew :capability:weather:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add capability/weather
git commit -m "Fetch the weather once the home location is known or changes, every 30 minutes, and 5 minutes after a failure"
```

---

### Task 7: `WeatherRepository` — the view, the header and `Daylight` (§3.7; rulings 4, 5)

**Review:** opus (what shows after a move and at midnight).

**Files:**
- Create: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherRepository.kt`
- Test: `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherRepositoryTest.kt` (create)

**Interfaces:**
- Consumes: `weatherView`, `headerWeather`, `sunTimesOn` (Task 3); `WeatherStore.stored` (Task 4); `TAG` (Task 6); `HouseholdRepository.location`, `HouseholdZone.zone` (Task 1); `wallTimeEachMinute`, `Daylight`, `retryWithBackoff` (Task 2).
- Produces: `@Singleton class WeatherRepository : Daylight` — `internal constructor(location: Flow<HomeLocation?>, stored: Flow<StoredWeather?>, now: Flow<LocalDateTime>)`; `@Inject constructor(household: HouseholdRepository, store: WeatherStore, zone: HouseholdZone, clock: WallClock)`; `val view: Flow<WeatherView>`; `val header: Flow<HeaderWeather?>`; `override val today: Flow<SunTimes?>`.

- [ ] **Step 1: Write the failing test**

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherRepositoryTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.plugin.SunTimes

// Robolectric for android.util.Log, should a retry log. The rules are tested in WeatherViewTest; these test the wiring
// (a move hides the old town at once; midnight moves the card and the sun times on).
@RunWith(AndroidJUnit4::class)
class WeatherRepositoryTest {
    private val location = MutableStateFlow<HomeLocation?>(LONDON)
    private val stored = MutableStateFlow<StoredWeather?>(stored())
    private val now = MutableStateFlow(THU.atTime(10, 30))
    private val repo = WeatherRepository(location, stored, now)

    @Test
    fun theViewFollowsTheLocationAndTheData() = runTest {
        location.value = null
        stored.value = null
        repo.view.test {
            assertThat(awaitItem()).isEqualTo(WeatherView.NoLocation)
            location.value = LONDON
            assertThat(awaitItem()).isEqualTo(WeatherView.Waiting)
            stored.value = stored()
            assertThat((awaitItem() as WeatherView.Ready).today.date).isEqualTo(THU)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Review Focus 1. */
    @Test
    fun aMoveHidesTheOldTownsWeatherAtOnce() = runTest {
        assertThat(repo.view.first()).isInstanceOf(WeatherView.Ready::class.java)
        assertThat(repo.header.first()).isNotNull()
        assertThat(repo.today.first()).isNotNull()
        location.value = LEEDS
        assertThat(repo.view.first()).isEqualTo(WeatherView.Waiting)
        assertThat(repo.header.first()).isNull()
        assertThat(repo.today.first()).isNull()
    }

    /** Review Focus 3: the cache carries the card over midnight, and runs out into Expired. */
    @Test
    fun atMidnightTheCardMovesOnWithoutAFetch() = runTest {
        stored.value = stored(forecast = forecast(from = THU, days = 3))
        now.value = THU.atTime(23, 59)
        repo.view.test {
            assertThat((awaitItem() as WeatherView.Ready).days.map { it.date }).containsExactly(THU, FRI, SAT).inOrder()
            now.value = FRI.atStartOfDay()
            val friday = awaitItem() as WeatherView.Ready
            assertThat(friday.today.date).isEqualTo(FRI)
            assertThat(friday.days.map { it.date }).containsExactly(FRI, SAT).inOrder()
            now.value = SAT.plusDays(1).atStartOfDay()
            assertThat(awaitItem()).isEqualTo(WeatherView.Expired)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theHeaderTurnsToNightAtSunsetWithinTheHour() = runTest {
        now.value = THU.atTime(18, 37)
        repo.header.test {
            assertThat(awaitItem()!!.night).isFalse()
            now.value = THU.atTime(18, 38)
            assertThat(awaitItem()!!.night).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theHeaderHidesWhenTheHourIsMissing() = runTest {
        val f = forecast()
        stored.value = stored(forecast = f.copy(hours = f.hours.filterNot { it.start == THU.atTime(10, 0) }))
        assertThat(repo.view.first()).isInstanceOf(WeatherView.Ready::class.java)
        assertThat(repo.header.first()).isNull()
    }

    /** Review Focus 3. */
    @Test
    fun daylightMovesToTomorrowsTimesAtMidnight() = runTest {
        val f = forecast(days = 2)
        val friday = day(FRI, sunrise = LocalTime.of(7, 2), sunset = LocalTime.of(18, 36))
        stored.value = stored(forecast = f.copy(days = listOf(f.days[0], friday)))
        now.value = THU.atTime(23, 59)
        repo.today.test {
            assertThat(awaitItem()).isEqualTo(SunTimes(SUNRISE, SUNSET))
            now.value = FRI.atStartOfDay()
            assertThat(awaitItem()).isEqualTo(SunTimes(LocalTime.of(7, 2), LocalTime.of(18, 36)))
            cancelAndIgnoreRemainingEvents()
        }
    }

}
```

- [ ] **Step 2: Run the test to see it fail**

Run: `./gradlew :capability:weather:testDebugUnitTest --tests "*WeatherRepositoryTest*"`
Expected: FAIL to compile with "Unresolved reference 'WeatherRepository'".

- [ ] **Step 3: Write the repository**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherRepository.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import android.util.Log
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.wallTimeEachMinute

/**
 * What the weather UI and the theme read (4b design §3.7): the home location, the stored fetch and the household's wall
 * time, by the minute. The rules are the pure [weatherView], [headerWeather] and [sunTimesOn].
 */
@Singleton
class WeatherRepository internal constructor(
    location: Flow<HomeLocation?>,
    stored: Flow<StoredWeather?>,
    now: Flow<LocalDateTime>,
) : Daylight {
    @Inject
    constructor(household: HouseholdRepository, store: WeatherStore, zone: HouseholdZone, clock: WallClock) :
        this(household.location, store.stored, wallTimeEachMinute(zone.zone, clock))

    val view: Flow<WeatherView> = combine(location, stored, now) { l, s, n -> weatherView(l, s, n) }
        .distinctUntilChanged()
        .retryWithBackoff { Log.w(TAG, "Couldn't read the weather (${it::class.simpleName}); retrying") }

    /** The header's weather, or null while it is hidden (§4.1). */
    val header: Flow<HeaderWeather?> = combine(view, now) { v, n -> headerWeather(v, n) }.distinctUntilChanged()

    /** Today's sun times in the household's zone, from matching data however old (§3.7). */
    override val today: Flow<SunTimes?> =
        combine(location, stored, now.map { it.toLocalDate() }.distinctUntilChanged()) { l, s, date -> sunTimesOn(l, s, date) }
            .distinctUntilChanged()
            .retryWithBackoff { Log.w(TAG, "Couldn't read today's sun times (${it::class.simpleName}); retrying") }
}
```

- [ ] **Step 4: Run the test to see it pass**

Run: `./gradlew :capability:weather:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add capability/weather
git commit -m "Work out what the weather shows from the stored forecast, the home location and the household's time, and supply today's sun times"
```

---

### Task 8: The Forecast card and the header item (§4.1, §4.2, D1, D2, D5; rulings 9, 10)

**Review:** sonnet.

**Glyph check (done while writing this plan):** the bundled `core/ui/src/main/res/font/material_symbols_rounded.ttf` was read with a GSUB ligature parser (4 284 ligatures; checked against known present `calendar_add_on`, `backspace`, `home` and a made-up name, which was missing). All eleven names in §4.1–4.2 are present: `sunny`, `partly_cloudy_day`, `partly_cloudy_night`, `clear_night`, `cloud`, `foggy`, `rainy_light`, `rainy`, `rainy_heavy`, `weather_snowy`, `thunderstorm`. No substitution.

**Files:**
- Modify: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt`, `Shell.kt`
- Modify: `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt` (`cardRadius` aliases the new token)
- Create: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/WeatherDimens.kt`, `ui/ForecastCard.kt`, `ui/WeatherHeaderItem.kt`
- Test: `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/RecordingNavigator.kt`, `ui/ForecastCardTest.kt`, `ui/WeatherScreenshotTest.kt` (create)
- Screenshots (new): `capability/weather/src/test/screenshots/forecast_{ready,age,waiting,expired,no_location}_{dark,light}.png`, `header_{day,night}_{dark,light}.png`

**Interfaces:**
- Consumes: `WeatherView`, `HeaderWeather`, `DailyWeather` and the words (Task 3); `WeatherRepository.view` (Task 7); `LocalShellNavigator`, `WallClock`, `rememberNowMillis`, `nowTicks` (`:core:plugin`, Task 2); `HhCard`, `HhIcon`, `HhPillButton`, `HhType`, `Culvery.colors`, `ShellTokens` (`:core:ui`).
- Produces:
  - `val SunAmber: Color` in `uk.co.siland.culvery.core.ui` (`#E0B85B`, both themes)
  - `ShellTokens.homeCardRadius = 26.dp` (`:core:ui`): the Home cards' radius; `CalendarDimens.cardRadius` now aliases it
  - `@Composable fun ForecastCard(view: WeatherView, nowMillis: Long, modifier: Modifier = Modifier)` — public (the app's whole-Home screenshot uses it); test tags `weather_forecast`, `forecast_row`, `weather_age`
  - `@Composable internal fun ForecastCardHost(repo: WeatherRepository, clock: WallClock, ticks: Flow<Unit> = nowTicks)` — the age line moves on with `rememberNowMillis`; no tick of its own
  - `@Composable fun WeatherHeaderItem(weather: HeaderWeather, modifier: Modifier = Modifier)` — public; test tag `weather_header`
  - `internal object WeatherDimens`

- [ ] **Step 1: Add the sun amber and the Home card radius**

In `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt`, after `val LightColors = HhColors(…)` add:
```kotlin

/** The hand-off's weather icon colour (§1). The same in both themes, so not an HhColors token. */
val SunAmber = Color(0xFFE0B85B)
```

In `core/ui/src/main/java/uk/co/siland/culvery/core/ui/Shell.kt`, in `object ShellTokens`, after `const val TOAST_MILLIS = 3_500L` add:
```kotlin

    /** Home's cards (hand-off: Today's `border-radius: 26px`): the calendar's and the weather's. */
    val homeCardRadius = 26.dp
```

In `capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt`, replace
```kotlin
    // Shared card radius (Today, Coming up, Connect).
    val cardRadius = 26.dp
```
with
```kotlin
    // Shared card radius (Today, Coming up, Connect): Home's, as the weather card's.
    val cardRadius = ShellTokens.homeCardRadius
```
and add `import uk.co.siland.culvery.core.ui.ShellTokens` to that file's imports in its sorted place (if it isn't there already). The calendar's cards keep reading `CalendarDimens.cardRadius`; their pixels don't change.

- [ ] **Step 2: Write the failing tests**

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/RecordingNavigator.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.ui

import uk.co.siland.culvery.core.plugin.ShellNavigator

class RecordingNavigator : ShellNavigator {
    var settingsOpened = 0

    override fun openTab(id: String) = Unit

    override fun openSettings() {
        settingsOpened++
    }

    override fun exitKiosk() = Unit
}
```

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/ForecastCardTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.weather.LONDON
import uk.co.siland.culvery.capability.weather.READY
import uk.co.siland.culvery.capability.weather.THU
import uk.co.siland.culvery.capability.weather.WeatherRepository
import uk.co.siland.culvery.capability.weather.WeatherView
import uk.co.siland.culvery.capability.weather.stored
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.CulveryTheme

@RunWith(AndroidJUnit4::class)
class ForecastCardTest {
    @get:Rule val compose = createComposeRule()
    private val navigator = RecordingNavigator()
    private val fetched = 1_000_000L
    private val hour = 3_600_000L

    private fun show(view: WeatherView, nowMillis: Long = fetched) = compose.setContent {
        CompositionLocalProvider(LocalShellNavigator provides navigator) {
            CulveryTheme(dark = true) { ForecastCard(view, nowMillis) }
        }
    }

    @Test
    fun readyShowsTodayThenTheNextTwoDaysEachAsOneSpokenRow() {
        show(READY)
        compose.onNodeWithText("Forecast").assertExists()
        compose.onAllNodesWithTag("forecast_row").assertCountEquals(3)
        compose.onNodeWithContentDescription("Today, partly cloudy, high 19°, low 11°").assertExists()
        compose.onNodeWithContentDescription("Friday, rain, high 17°, low 10°").assertExists()
        compose.onNodeWithContentDescription("Saturday, clear, high 21°, low 12°").assertExists()
        // A row's own texts are folded into its description, not read one by one.
        compose.onAllNodesWithText("Fri", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun fewerDaysShowOnlyTheRowsThatExist() {
        show(READY.copy(days = READY.days.take(2)))
        compose.onAllNodesWithTag("forecast_row").assertCountEquals(2)
    }


    @Test
    fun waitingSaysItIsGettingTheForecast() {
        show(WeatherView.Waiting)
        compose.onNodeWithText("Getting the forecast…").assertExists()
        compose.onAllNodesWithTag("forecast_row").assertCountEquals(0)
    }

    @Test
    fun expiredSaysToCheckTheWifi() {
        show(WeatherView.Expired)
        compose.onNodeWithText("No forecast — check the tablet's Wi-Fi.").assertExists()
    }

    @Test
    fun noLocationOpensSettings() {
        show(WeatherView.NoLocation)
        compose.onNodeWithText("Add your home location to see the weather.").assertExists()
        compose.onNodeWithText("Open settings").performClick()
        assertThat(navigator.settingsOpened).isEqualTo(1)
    }

    /** The age rule is `WeatherWordsTest`'s; this pins that the card shows it and moves it on with the clock. */
    @Test
    fun theHostMovesTheAgeLineOnWithTheClock() {
        var now = fetched + 2 * hour
        val clock = WallClock { now }
        val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val repo = WeatherRepository(
            MutableStateFlow(LONDON),
            MutableStateFlow(stored(fetchedAtMillis = fetched)),
            MutableStateFlow(THU.atTime(10, 30)),
        )
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides navigator) {
                CulveryTheme(dark = true) { ForecastCardHost(repo, clock, ticks) }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("forecast_row").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("weather_age").assertDoesNotExist()
        now = fetched + 3 * hour
        ticks.tryEmit(Unit)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Updated 3 h ago").fetchSemanticsNodes().isNotEmpty() }
    }
}
```

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/ui/WeatherScreenshotTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.ui

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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.weather.Condition
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.READY
import uk.co.siland.culvery.capability.weather.WeatherView
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme

/** A REGULAR cell as the Home grid gives it on the 1280×800 canvas: half the WIDE card's 705 dp less the 14 dp gap. */
private val REGULAR_W = 345.dp
private val REGULAR_H = 279.dp
private const val THREE_HOURS = 3 * 3_600_000L

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val day = HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0)
    private val night = day.copy(night = true)

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalShellNavigator provides RecordingNavigator()) {
                CulveryTheme(dark = dark) {
                    Box(Modifier.testTag("shot").background(Culvery.colors.bg).padding(16.dp)) { content() }
                }
            }
        }
        compose.onNodeWithTag("shot").captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun card(name: String, dark: Boolean, view: WeatherView, nowMillis: Long = READY.fetchedAtMillis) =
        snap(name, dark) { Box(Modifier.size(REGULAR_W, REGULAR_H)) { ForecastCard(view, nowMillis) } }

    @Test fun forecastReadyDark() = card("forecast_ready_dark", true, READY)
    @Test fun forecastReadyLight() = card("forecast_ready_light", false, READY)
    @Test fun forecastAgeDark() = card("forecast_age_dark", true, READY, READY.fetchedAtMillis + THREE_HOURS)
    @Test fun forecastAgeLight() = card("forecast_age_light", false, READY, READY.fetchedAtMillis + THREE_HOURS)
    @Test fun forecastWaitingDark() = card("forecast_waiting_dark", true, WeatherView.Waiting)
    @Test fun forecastWaitingLight() = card("forecast_waiting_light", false, WeatherView.Waiting)
    @Test fun forecastExpiredDark() = card("forecast_expired_dark", true, WeatherView.Expired)
    @Test fun forecastExpiredLight() = card("forecast_expired_light", false, WeatherView.Expired)
    @Test fun forecastNoLocationDark() = card("forecast_no_location_dark", true, WeatherView.NoLocation)
    @Test fun forecastNoLocationLight() = card("forecast_no_location_light", false, WeatherView.NoLocation)
    @Test fun headerDayDark() = snap("header_day_dark", true) { WeatherHeaderItem(day) }
    @Test fun headerDayLight() = snap("header_day_light", false) { WeatherHeaderItem(day) }
    @Test fun headerNightDark() = snap("header_night_dark", true) { WeatherHeaderItem(night) }
    @Test fun headerNightLight() = snap("header_night_light", false) { WeatherHeaderItem(night) }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew :capability:weather:testDebugUnitTest --tests "*ForecastCardTest*"`
Expected: FAIL to compile with "Unresolved reference 'ForecastCard'", "'ForecastCardHost'", "'WeatherHeaderItem'".

- [ ] **Step 4: Write the dimensions**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/WeatherDimens.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.ui.unit.dp

/** Weather layout numbers (4b design §4); provisional, for the end-of-v1 design review. */
internal object WeatherDimens {
    // Card padding 20 × 22, as the calendar's cards; the radius is ShellTokens.homeCardRadius.
    val cardPaddingV = 20.dp
    val cardPaddingH = 22.dp

    // Title → first row 10; rows 44 tall, so three and the age line fit the 279 dp REGULAR cell; a 30 dp icon (§4.2)
    // 12 from the temperatures, each temperature right-aligned in 44.
    val titleGap = 10.dp
    val rowHeight = 44.dp
    val rowIcon = 30.dp
    val rowIconGap = 12.dp
    val temperatureWidth = 44.dp

    // The prompt's pill 18 below its line, as the Connect card's.
    val promptButtonTop = 18.dp

    // Header item (hand-off §1): a 44 dp icon 12 dp from the temperatures.
    val headerIcon = 44.dp
    val headerIconGap = 12.dp
}
```

- [ ] **Step 5: Write the header item**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/WeatherHeaderItem.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import uk.co.siland.culvery.capability.weather.HeaderWeather
import uk.co.siland.culvery.capability.weather.degrees
import uk.co.siland.culvery.capability.weather.highLow
import uk.co.siland.culvery.capability.weather.weatherIcon
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.SunAmber

/** Hand-off §1's weather block: the condition in sun amber, "17°", and "High 19° · Low 11°" (4b design §4.1). */
@Composable
fun WeatherHeaderItem(weather: HeaderWeather, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WeatherDimens.headerIconGap),
        modifier = modifier.testTag("weather_header"),
    ) {
        HhIcon(weatherIcon(weather.condition, weather.night), size = WeatherDimens.headerIcon, tint = SunAmber)
        Column {
            Text(degrees(weather.temperature), style = HhType.headerValue, color = c.ink)
            Text(highLow(weather.high, weather.low), style = HhType.secondary, color = c.mute)
        }
    }
}
```

- [ ] **Step 6: Write the card**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/ui/ForecastCard.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.capability.weather.ADD_LOCATION
import uk.co.siland.culvery.capability.weather.DailyWeather
import uk.co.siland.culvery.capability.weather.FORECAST_TITLE
import uk.co.siland.culvery.capability.weather.GETTING_FORECAST
import uk.co.siland.culvery.capability.weather.NO_FORECAST
import uk.co.siland.culvery.capability.weather.OPEN_SETTINGS
import uk.co.siland.culvery.capability.weather.WeatherRepository
import uk.co.siland.culvery.capability.weather.WeatherView
import uk.co.siland.culvery.capability.weather.dayLabel
import uk.co.siland.culvery.capability.weather.degrees
import uk.co.siland.culvery.capability.weather.rowDescription
import uk.co.siland.culvery.capability.weather.updatedAgo
import uk.co.siland.culvery.capability.weather.weatherIcon
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.nowTicks
import uk.co.siland.culvery.core.plugin.rememberNowMillis
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhCard
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.HhPillButton
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.ShellTokens
import uk.co.siland.culvery.core.ui.SunAmber

/**
 * The REGULAR Forecast card (4b design §4.2): today and up to two more days, each row day · icon · high · low and one
 * TalkBack item; an age line past 2 h; otherwise what to do. [nowMillis] is for the age line only.
 */
@Composable
fun ForecastCard(view: WeatherView, nowMillis: Long, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val navigator = LocalShellNavigator.current
    HhCard(
        modifier = modifier.fillMaxSize().testTag("weather_forecast"),
        radius = ShellTokens.homeCardRadius,
        padding = PaddingValues(horizontal = WeatherDimens.cardPaddingH, vertical = WeatherDimens.cardPaddingV),
    ) {
        Text(FORECAST_TITLE, style = HhType.cardTitle, color = c.ink)
        Spacer(Modifier.height(WeatherDimens.titleGap))
        when (view) {
            is WeatherView.Ready -> {
                view.days.forEachIndexed { i, day -> ForecastRow(day, isToday = i == 0) }
                Spacer(Modifier.weight(1f))
                updatedAgo(view.fetchedAtMillis, nowMillis)?.let {
                    Text(it, style = HhType.secondary, color = c.mute, modifier = Modifier.testTag("weather_age"))
                }
            }
            WeatherView.Waiting -> Text(GETTING_FORECAST, style = HhType.body, color = c.mute)
            WeatherView.Expired -> Text(NO_FORECAST, style = HhType.body, color = c.mute)
            WeatherView.NoLocation -> {
                Text(ADD_LOCATION, style = HhType.body, color = c.mute)
                Spacer(Modifier.height(WeatherDimens.promptButtonTop))
                HhPillButton(OPEN_SETTINGS, onClick = navigator::openSettings, primary = true)
            }
        }
    }
}

@Composable
private fun ForecastRow(day: DailyWeather, isToday: Boolean) {
    val c = Culvery.colors
    val description = rowDescription(day, isToday)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(WeatherDimens.rowHeight)
            .testTag("forecast_row")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(dayLabel(day, isToday), style = HhType.rowTitle, color = c.ink, modifier = Modifier.weight(1f))
        HhIcon(weatherIcon(day.condition, night = false), size = WeatherDimens.rowIcon, tint = SunAmber)
        Spacer(Modifier.width(WeatherDimens.rowIconGap))
        Text(degrees(day.high), style = HhType.rowTitle, color = c.ink, textAlign = TextAlign.End, modifier = Modifier.width(WeatherDimens.temperatureWidth))
        Text(degrees(day.low), style = HhType.rowTitle, color = c.mute, textAlign = TextAlign.End, modifier = Modifier.width(WeatherDimens.temperatureWidth))
    }
}

/** The card over the repository: nothing until the first view, and the age line moved on each [ticks]. */
@Composable
internal fun ForecastCardHost(repo: WeatherRepository, clock: WallClock, ticks: Flow<Unit> = nowTicks) {
    val view by repo.view.collectAsState(initial = null)
    val nowMillis = rememberNowMillis(clock, ticks)
    view?.let { ForecastCard(it, nowMillis) }
}
```

- [ ] **Step 7: Run the tests to see them pass**

Run: `./gradlew :capability:weather:testDebugUnitTest :core:ui:testDebugUnitTest :capability:calendar:testDebugUnitTest`
Expected: PASS (the new screenshots have no baselines yet, which `testDebugUnitTest` doesn't check).

- [ ] **Step 8: Record and look at the screenshots**

Run: `./gradlew :capability:weather:recordRoborazziDebug --tests "*WeatherScreenshotTest*"`

Open each new PNG in `capability/weather/src/test/screenshots/` and check:
- `forecast_ready_*`: "Forecast", then rows "Today", "Fri", "Sat"; amber 30 dp icons (partly cloudy sun, rain, sun); highs "19°", "17°", "21°" in ink and lows "11°", "10°", "12°" muted, right-aligned in two columns; no age line.
- `forecast_age_*`: the same, with "Updated 3 h ago" muted at the bottom.
- `forecast_waiting_*`: "Forecast", then "Getting the forecast…" muted.
- `forecast_expired_*`: "Forecast", then "No forecast — check the tablet's Wi-Fi." muted.
- `forecast_no_location_*`: "Forecast", "Add your home location to see the weather.", and a green **Open settings** pill.
- `header_day_*`: the amber `partly_cloudy_day` (44 dp), "17°" large and "High 19° · Low 11°" small and muted beside it; `header_night_*`: the same with `partly_cloudy_night` (a moon).
- Nothing clipped or overlapping; every icon a glyph, never a ligature word.

- [ ] **Step 9: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; no other module's baselines change (`SunAmber` is new and nothing else uses it; the calendar's cards keep 26 dp through the alias).

- [ ] **Step 10: Commit**

```bash
git add core/ui/src/main/java/uk/co/siland/culvery/core/ui/Colors.kt core/ui/src/main/java/uk/co/siland/culvery/core/ui/Shell.kt capability/calendar/src/main/java/uk/co/siland/culvery/capability/calendar/ui/CalendarType.kt capability/weather
git commit -m "Show the Forecast card and the header's weather, light and dark"
```

---

### Task 9: `WeatherCapability` and its Hilt bindings (§3.1, D7)

**Review:** sonnet.

**Files:**
- Create: `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherCapability.kt`, `di/WeatherModule.kt`
- Test: `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherCapabilityTest.kt` (create)

**Interfaces:**
- Consumes: `WeatherRepository` (Task 7), `ForecastCardHost`, `WeatherHeaderItem` (Task 8), `WeatherSyncLoop`, `WeatherProvider` (Tasks 3, 6), `WeatherDatabase` (Task 4); `Capability`, `HomeCard`, `HeaderItem`, `Startable`, `Daylight`, `WallClock` (`:core:plugin`).
- Produces:
  - Private to `WeatherCapability.kt` (nothing outside reads them; tests compare the literal values): `WEATHER_ID = "weather"`, `FORECAST_CARD_ID = "weather.forecast"`, `FORECAST_PRIORITY = 40`, `HEADER_ITEM_ID = "weather"`, `HEADER_ITEM_ORDER = 10`
  - `@Singleton class WeatherCapability @Inject constructor(repo: WeatherRepository, clock: WallClock) : Capability` — `id "weather"`, `label "Weather"`, `icon "partly_cloudy_day"`, `order 60`, `hasTab flowOf(false)`, empty `TabContent`, `cards()` the Forecast card, `headerItems()` the weather item while shown
  - `WeatherModule`: `@Multibinds Set<WeatherProvider>`; `Capability` and `Startable` (`WeatherSyncLoop`) `@IntoSet`; `Daylight` → `WeatherRepository`; `weather.db`

- [ ] **Step 1: Write the failing test**

Create `capability/weather/src/test/java/uk/co/siland/culvery/capability/weather/WeatherCapabilityTest.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.WallClock

// Robolectric for android.util.Log (the repository's retries).
@RunWith(AndroidJUnit4::class)
class WeatherCapabilityTest {
    private val location = MutableStateFlow<HomeLocation?>(LONDON)
    private val stored = MutableStateFlow<StoredWeather?>(stored())
    private val capability = WeatherCapability(WeatherRepository(location, stored, MutableStateFlow(THU.atTime(10, 30))), WallClock { 0L })

    @Test
    fun itHasNoTabAndSitsAfterTheRailCapabilities() = runTest {
        assertThat(capability.id).isEqualTo("weather")
        assertThat(capability.order).isEqualTo(60)
        assertThat(capability.hasTab.first()).isFalse()
    }

    @Test
    fun itAlwaysOffersTheForecastCardAsARegularCardAfterTheCalendars() = runTest {
        location.value = null
        val card = capability.cards().first().single()
        assertThat(card.id).isEqualTo("weather.forecast")
        assertThat(card.size).isEqualTo(HomeCardSize.REGULAR)
        assertThat(card.priority).isEqualTo(40)
    }

    @Test
    fun theHeaderItemIsThereOnlyWhileTheWeatherShows() = runTest {
        val item = capability.headerItems().first().single()
        assertThat(item.id).isEqualTo("weather")
        assertThat(item.order).isEqualTo(10)
        stored.value = null
        assertThat(capability.headerItems().first()).isEmpty()
        location.value = null
        assertThat(capability.headerItems().first()).isEmpty()
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

Run: `./gradlew :capability:weather:testDebugUnitTest --tests "*WeatherCapabilityTest*"`
Expected: FAIL to compile with "Unresolved reference 'WeatherCapability'".

- [ ] **Step 3: Write the capability**

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/WeatherCapability.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.capability.weather.ui.ForecastCardHost
import uk.co.siland.culvery.capability.weather.ui.WeatherHeaderItem
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.WallClock

private const val WEATHER_ID = "weather"
private const val FORECAST_CARD_ID = "weather.forecast"

/** After Today (100) and Coming up (50): row 2, col 2 (§4.2). */
private const val FORECAST_PRIORITY = 40
private const val HEADER_ITEM_ID = "weather"
private const val HEADER_ITEM_ORDER = 10

/** Weather on Home (4b design §3.1): no tab, no Settings page, no connection (D7); runs whenever a location is set. */
@Singleton
class WeatherCapability @Inject constructor(
    private val repo: WeatherRepository,
    private val clock: WallClock,
) : Capability {
    override val id = WEATHER_ID
    override val label = "Weather"
    override val icon = "partly_cloudy_day"

    /** No rail position is used. */
    override val order = 60
    override val hasTab: Flow<Boolean> = flowOf(false)

    private val forecastCard = HomeCard(FORECAST_CARD_ID, HomeCardSize.REGULAR, FORECAST_PRIORITY) { ForecastCardHost(repo, clock) }

    override fun cards(): Flow<List<HomeCard>> = flowOf(listOf(forecastCard))

    // Present only while shown, so the shell never draws a divider beside an empty item (ruling 4).
    override fun headerItems(): Flow<List<HeaderItem>> = repo.header.map { weather ->
        listOfNotNull(weather?.let { HeaderItem(HEADER_ITEM_ID, HEADER_ITEM_ORDER) { WeatherHeaderItem(it) } })
    }

    @Composable
    override fun TabContent() = Unit
}
```

Create `capability/weather/src/main/java/uk/co/siland/culvery/capability/weather/di/WeatherModule.kt`:
```kotlin
package uk.co.siland.culvery.capability.weather.di

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
import uk.co.siland.culvery.capability.weather.WeatherCapability
import uk.co.siland.culvery.capability.weather.WeatherProvider
import uk.co.siland.culvery.capability.weather.WeatherRepository
import uk.co.siland.culvery.capability.weather.WeatherSyncLoop
import uk.co.siland.culvery.capability.weather.db.WeatherDatabase
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.Startable

@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherModule {
    // With no provider bound the set is empty and the card waits (§5).
    @Multibinds
    abstract fun providers(): Set<WeatherProvider>

    @Binds
    @IntoSet
    abstract fun capability(impl: WeatherCapability): Capability

    @Binds
    @IntoSet
    abstract fun syncLoop(impl: WeatherSyncLoop): Startable

    @Binds
    abstract fun daylight(impl: WeatherRepository): Daylight

    companion object {
        @Provides
        @Singleton
        fun database(@ApplicationContext context: Context): WeatherDatabase =
            Room.databaseBuilder(context, WeatherDatabase::class.java, "weather.db").build()
    }
}
```

- [ ] **Step 4: Run the test to see it pass**

Run: `./gradlew :capability:weather:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Hilt now resolves `WeatherCapability` (in `Set<Capability>`), `WeatherSyncLoop` (in `Set<Startable>`), `WeatherFetcher` (`Set<WeatherProvider>` holding Open-Meteo), `WeatherStore`, `WeatherDatabase`, `WeatherRepository`, `HouseholdZone` and `Daylight`. If Hilt names a missing binding, it is one of these; stop and report rather than adding a binding this plan doesn't list. The shell already places the Forecast card from `cards()`; it shows header items from Task 10.

- [ ] **Step 6: Commit**

```bash
git add capability/weather
git commit -m "Add the weather capability to Home: the Forecast card, the header item and the fetch loop"
```

---

### Task 10: The shell — header items, the household-zone clock and the sun-times theme (§3.8, §4.1, D6; ruling 12)

**Review:** opus (the theme and the zone the whole shell runs in).

**Files:**
- Modify: `app/src/main/java/uk/co/siland/culvery/shell/ShellUiState.kt`, `ShellViewModel.kt`, `MinuteTicker.kt`, `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt`, `app/src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt`, `CulveryShell.kt`
- Test: `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt`, `ShellViewModelTest.kt`, `ui/ShellLayoutTest.kt`, `ui/ShellScreenshotTest.kt` (modify); `app/src/test/java/uk/co/siland/culvery/shell/HouseholdTickerTest.kt`, `app/src/test/java/uk/co/siland/culvery/AppSourceTest.kt` (create)
- Screenshots (new): `app/src/test/screenshots/home_weather_dark.png`, `home_weather_light.png`

**Interfaces:**
- Consumes: `HeaderItem`, `Daylight`, `wallTimeEachMinute` (Task 2); `HouseholdZone` (Task 1); `ThemeSchedule.isDark(LocalTime, SunTimes?)` (existing, unchanged); in tests only, `ForecastCard`, `WeatherHeaderItem` and the weather model (Tasks 3, 8).
- Produces:
  - `ShellUiState.headerItems: List<HeaderItem>` (default empty), sorted by order then id
  - `ShellViewModel(capabilities, ticker: MinuteTicker, access: AccessControl, daylight: Optional<Daylight>)`
  - `internal fun householdTicker(zones: Flow<ZoneId>, clock: WallClock): MinuteTicker` (`MinuteTicker.kt`): `wallTimeEachMinute` over [zones], a failed zone read retried with `retryWithBackoff` (logged by type), so a Room failure can't end the clock or crash the shell
  - `AppModule.minuteTicker(zone: HouseholdZone, clock: WallClock): MinuteTicker` = `householdTicker(zone.zone, clock)`; `@BindsOptionalOf Daylight`
  - `fun HomeScreen(now: LocalDateTime, placements: List<HomePlacement>, headerItems: List<HeaderItem> = emptyList())`; `fun HomeHeader(now, items, modifier)`; test tags `home_header_items`, `home_header_divider`, `home_header_{id}`
  - `SystemMinuteTicker` is removed

- [ ] **Step 1: Extend the fakes**

In `app/src/test/java/uk/co/siland/culvery/shell/Fakes.kt`:
1. Add imports `uk.co.siland.culvery.core.plugin.Daylight`, `uk.co.siland.culvery.core.plugin.HeaderItem`, `uk.co.siland.culvery.core.plugin.SunTimes`.
2. Replace `FakeCapability` with:
```kotlin
class FakeCapability(
    override val id: String,
    override val order: Int,
    shown: Boolean,
    private val cardList: List<HomeCard> = emptyList(),
    private val headerList: List<HeaderItem> = emptyList(),
) : Capability {
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    val shownFlow = MutableStateFlow(shown)
    override val hasTab: Flow<Boolean> = shownFlow
    override fun cards(): Flow<List<HomeCard>> = flowOf(cardList)
    override fun headerItems(): Flow<List<HeaderItem>> = flowOf(headerList)
    @Composable override fun TabContent() {}
}
```
3. Add at the end of the file:
```kotlin

/** A capability whose header items fail once, as a store hiccup would, then show [items]. */
class FlakyHeaderCapability(override val id: String, override val order: Int, private val items: List<HeaderItem>) : Capability {
    private var failures = 1
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    override val hasTab: Flow<Boolean> = flowOf(false)
    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())
    override fun headerItems(): Flow<List<HeaderItem>> = flow {
        if (failures-- > 0) throw IllegalStateException("store hiccup")
        emit(items)
    }
    @Composable override fun TabContent() {}
}

/** Today's sun times, settable. */
class FakeDaylight(sun: SunTimes?) : Daylight {
    val sun = MutableStateFlow(sun)
    override val today: Flow<SunTimes?> = this.sun
}
```

- [ ] **Step 2: Write the failing view-model tests**

In `app/src/test/java/uk/co/siland/culvery/shell/ShellViewModelTest.kt`:
1. Add imports `java.time.LocalTime`, `java.util.Optional`, `uk.co.siland.culvery.core.plugin.Daylight`, `uk.co.siland.culvery.core.plugin.HeaderItem`, `uk.co.siland.culvery.core.plugin.SunTimes`.
2. Replace `private fun vm(caps: Set<Capability> = emptySet()) = ShellViewModel(caps, { ticks }, access)` with:
```kotlin
    private fun vm(caps: Set<Capability> = emptySet(), daylight: Daylight? = null) =
        ShellViewModel(caps, { ticks }, access, Optional.ofNullable(daylight))
```
3. In `settingsNeverOpenWithoutSession`, replace `ShellViewModel(emptySet(), { ticks }, noSessionAccess)` with `ShellViewModel(emptySet(), { ticks }, noSessionAccess, Optional.empty())`.
4. Add before the class's closing brace:
```kotlin

    private val sunrise = LocalTime.of(6, 50)
    private val sunset = LocalTime.of(19, 20)

    @Test
    fun headerItemsComeFromEveryCapabilityInTheirOrder() = runTest {
        val vm = vm(
            setOf(
                FakeCapability("climate", order = 40, shown = false, headerList = listOf(HeaderItem("climate", 20) {})),
                FakeCapability("weather", order = 60, shown = false, headerList = listOf(HeaderItem("weather", 10) {})),
            ),
        )
        vm.uiState.test {
            assertThat(expectMostRecentItem().headerItems.map { it.id }).containsExactly("weather", "climate").inOrder()
        }
    }

    @Test
    fun headerItemsWhoseFlowFailsComeBackAfterTheRetry() = runTest {
        val vm = vm(setOf(FlakyHeaderCapability("weather", order = 60, listOf(HeaderItem("weather", 10) {}))))
        vm.uiState.test {
            assertThat(expectMostRecentItem().headerItems).isEmpty()
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().headerItems.map { it.id }).containsExactly("weather")
        }
    }

    @Test
    fun theThemeTurnsDarkAtTodaysSunsetAndLightAtSunrise() = runTest {
        val vm = vm(daylight = FakeDaylight(SunTimes(sunrise, sunset)))
        vm.uiState.test {
            ticks.value = noon.with(LocalTime.of(19, 19))
            assertThat(expectMostRecentItem().dark).isFalse()
            ticks.value = noon.with(sunset)
            assertThat(expectMostRecentItem().dark).isTrue()
            ticks.value = noon.plusDays(1).with(LocalTime.of(6, 49))
            assertThat(expectMostRecentItem().dark).isTrue()
            ticks.value = noon.plusDays(1).with(sunrise)
            assertThat(expectMostRecentItem().dark).isFalse()
        }
    }

    @Test
    fun withoutDaylightTheThemeUsesSevenAndSeven() = runTest {
        val vm = vm()
        vm.uiState.test {
            ticks.value = noon.with(LocalTime.of(18, 59))
            assertThat(expectMostRecentItem().dark).isFalse()
            ticks.value = noon.with(LocalTime.of(19, 0))
            assertThat(expectMostRecentItem().dark).isTrue()
            ticks.value = noon.plusDays(1).with(LocalTime.of(6, 59))
            assertThat(expectMostRecentItem().dark).isTrue()
            ticks.value = noon.plusDays(1).with(LocalTime.of(7, 0))
            assertThat(expectMostRecentItem().dark).isFalse()
        }
    }

    @Test
    fun untilTheSunTimesAreKnownTheThemeUsesSevenAndSevenThenFollowsThem() = runTest {
        val daylight = FakeDaylight(null)
        val vm = vm(daylight = daylight)
        vm.uiState.test {
            ticks.value = noon.with(LocalTime.of(19, 10))
            assertThat(expectMostRecentItem().dark).isTrue()
            daylight.sun.value = SunTimes(sunrise, sunset)
            assertThat(expectMostRecentItem().dark).isFalse()
        }
    }
```

Create `app/src/test/java/uk/co/siland/culvery/shell/HouseholdTickerTest.kt`:
```kotlin
package uk.co.siland.culvery.shell

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.household.HomeLocation
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.di.AppModule

/** Review Focus 5: the shell's clock, date and theme run in the household's zone, not the tablet's. Robolectric for Room and Log. */
@RunWith(AndroidJUnit4::class)
class HouseholdTickerTest {
    private val deviceZone = TimeZone.getDefault()
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository

    // 12:00 UTC on 1 October 2026: 08:00 that day in New York, 01:00 the next day in Wellington.
    private val clock = WallClock { Instant.parse("2026-10-01T12:00:00Z").toEpochMilli() }

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        household = HouseholdRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        TimeZone.setDefault(deviceZone)
    }

    @Test
    fun theClockShowsTheHouseholdsTimeNotTheDevices() = runTest {
        household.setLocation(HomeLocation("Wellington", -41.29, 174.78, "Pacific/Auckland"))
        val first = AppModule.minuteTicker(HouseholdZone(household), clock).ticks().first()
        assertThat(first).isEqualTo(LocalDateTime.of(2026, 10, 2, 1, 0))
    }

    /** Plan review 1: the zone comes from Room, which can fail; the clock must carry on, not crash the shell. */
    @Test
    fun aFailedZoneReadIsRetriedAndTheClockCarriesOn() = runTest {
        ShadowLog.clear()
        var reads = 0
        val zones = flow {
            if (reads++ == 0) throw IllegalStateException("database locked")
            emit(ZoneId.of("Pacific/Auckland"))
        }
        val first = householdTicker(zones, clock).ticks().first()
        assertThat(first).isEqualTo(LocalDateTime.of(2026, 10, 2, 1, 0))
        assertThat(ShadowLog.getLogs().map { it.msg })
            .contains("Couldn't read the household's time zone (IllegalStateException); retrying")
    }
}
```

Create `app/src/test/java/uk/co/siland/culvery/AppSourceTest.kt`:
```kotlin
package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** 4b design D6, §9: `:app` wires the weather capability through Hilt alone and never names it in its own code. */
class AppSourceTest {
    @Test
    fun theAppsOwnCodeNeverNamesTheWeatherCapability() {
        // Unit tests run in the module's directory, as the screenshot paths rely on.
        val files = listOf("src/main", "src/debug", "src/release")
            .map(::File)
            .flatMap { root -> root.walk().filter { it.isFile && it.extension == "kt" }.toList() }
        assertThat(files).isNotEmpty()
        assertThat(files.filter { it.readText().contains("uk.co.siland.culvery.capability.weather") }.map { it.path }).isEmpty()
    }
}
```

- [ ] **Step 3: Write the failing layout and screenshot tests**

In `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt`:
1. Add imports `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.size`, `androidx.compose.ui.Modifier`, `androidx.compose.ui.test.assertCountEquals`, `androidx.compose.ui.test.onAllNodesWithTag`, `androidx.compose.ui.test.onRoot`, `androidx.compose.ui.unit.dp`, `uk.co.siland.culvery.core.plugin.HeaderItem`.
2. Add before the class's closing brace:
```kotlin

    private fun item(id: String, order: Int) = HeaderItem(id, order) { Box(Modifier.size(200.dp, 60.dp)) }

    private fun home(vararg items: HeaderItem) = compose.setContent {
        CulveryTheme(dark = true) { HomeScreen(at, emptyList(), items.toList()) }
    }

    @Test
    fun headerItemsSitAtTheRightFourAboveTheDatesBottom() {
        home(item("weather", 10))
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val date = compose.onNodeWithTag("home_date").getUnclippedBoundsInRoot()
        val items = compose.onNodeWithTag("home_header_items").getUnclippedBoundsInRoot()
        assertThat(items.right.value).isWithin(1f).of(root.right.value)
        assertThat((date.bottom - items.bottom).value).isWithin(1f).of(4f)
    }

    @Test
    fun oneItemHasNoDivider() {
        home(item("weather", 10))
        compose.onAllNodesWithTag("home_header_divider").assertCountEquals(0)
    }

    @Test
    fun twoItemsShowInOrderWithOneDividerBetween() {
        home(item("weather", 10), item("climate", 20))
        compose.onAllNodesWithTag("home_header_divider").assertCountEquals(1)
        val weather = compose.onNodeWithTag("home_header_weather").getUnclippedBoundsInRoot()
        val divider = compose.onNodeWithTag("home_header_divider").getUnclippedBoundsInRoot()
        val climate = compose.onNodeWithTag("home_header_climate").getUnclippedBoundsInRoot()
        assertThat(weather.right.value).isLessThan(divider.left.value)
        assertThat(divider.right.value).isLessThan(climate.left.value)
    }

    @Test
    fun withoutItemsTheHeaderIsAsBefore() {
        home()
        compose.onAllNodesWithTag("home_header_divider").assertCountEquals(0)
        compose.onNodeWithTag("home_clock").assertExists()
    }
```

In `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellScreenshotTest.kt`:
1. Add imports `java.time.LocalTime`, `uk.co.siland.culvery.capability.weather.Condition`, `uk.co.siland.culvery.capability.weather.DailyWeather`, `uk.co.siland.culvery.capability.weather.HeaderWeather`, `uk.co.siland.culvery.capability.weather.HourlyWeather`, `uk.co.siland.culvery.capability.weather.WeatherView`, `uk.co.siland.culvery.capability.weather.ui.ForecastCard`, `uk.co.siland.culvery.capability.weather.ui.WeatherHeaderItem`, `uk.co.siland.culvery.core.plugin.HeaderItem` (ruling 12: test code only).
2. After `calendarHome(…)` add:
```kotlin

    private val forecastDays = listOf(
        DailyWeather(at.toLocalDate(), Condition.PARTLY_CLOUDY, 19.0, 11.0, LocalTime.of(6, 50), LocalTime.of(19, 2)),
        DailyWeather(at.toLocalDate().plusDays(1), Condition.RAIN, 17.0, 10.0, LocalTime.of(6, 52), LocalTime.of(19, 0)),
        DailyWeather(at.toLocalDate().plusDays(2), Condition.CLEAR, 21.0, 12.0, LocalTime.of(6, 53), LocalTime.of(18, 58)),
    )

    /** The whole Home with Today, Coming up, Forecast and the header's weather (4b design §7). */
    private fun weatherHome(dark: Boolean) = calendarHome(dark).copy(
        homeCards = HomeCardPlacer.place(
            listOf(
                HomeCard("calendar.today", HomeCardSize.TALL, 100) { TodayCard(today) },
                HomeCard("calendar.comingUp", HomeCardSize.WIDE, 50) { ComingUpCard(comingUp) },
                HomeCard("weather.forecast", HomeCardSize.REGULAR, 40) {
                    ForecastCard(
                        WeatherView.Ready(HourlyWeather(at.withMinute(0), Condition.PARTLY_CLOUDY, 17.0), forecastDays[0], forecastDays, 0L),
                        nowMillis = 0L,
                    )
                },
            ),
        ),
        headerItems = listOf(
            HeaderItem("weather", 10) { WeatherHeaderItem(HeaderWeather(Condition.PARTLY_CLOUDY, night = false, temperature = 17.0, high = 19.0, low = 11.0)) },
        ),
    )
```
3. After `homeWithCalendarLight()` add:
```kotlin

    @Test
    fun homeWithWeatherDark() = snap("home_weather_dark", dark = true, state = weatherHome(dark = true))

    @Test
    fun homeWithWeatherLight() = snap("home_weather_light", dark = false, state = weatherHome(dark = false))
```

- [ ] **Step 4: Run the tests to see them fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL to compile with "Too many arguments" for `ShellViewModel`, "Unresolved reference 'headerItems'" on `ShellUiState`, "Too many arguments" for `HomeScreen`, "Unresolved reference 'minuteTicker'" on `AppModule` and "Unresolved reference 'householdTicker'" (`AppSourceTest` alone would pass).

- [ ] **Step 5: Carry header items in the state**

In `app/src/main/java/uk/co/siland/culvery/shell/ShellUiState.kt`, add `import uk.co.siland.culvery.core.plugin.HeaderItem` and, after `val homeCards: List<HomePlacement> = emptyList(),`, add:
```kotlin
    /** Home's header items, by order (4b design §3.8). */
    val headerItems: List<HeaderItem> = emptyList(),
```

Replace the whole of `app/src/main/java/uk/co/siland/culvery/shell/MinuteTicker.kt` with:
```kotlin
package uk.co.siland.culvery.shell

import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.wallTimeEachMinute

/** The wall time, each minute; `AppModule` gives it in the household's zone. */
fun interface MinuteTicker {
    fun ticks(): Flow<LocalDateTime>
}

/**
 * The wall time in the latest of [zones] (4b design §3.8). The zone is read from Room, which can fail: the read is
 * retried, so the clock never stops and `ShellViewModel.now` never sees the failure (plan review 1).
 */
internal fun householdTicker(zones: Flow<ZoneId>, clock: WallClock): MinuteTicker = MinuteTicker {
    val retried = zones.retryWithBackoff { Log.w(TAG, "Couldn't read the household's time zone (${it::class.simpleName}); retrying") }
    wallTimeEachMinute(retried, clock)
}

private const val TAG = "MinuteTicker"
```

- [ ] **Step 6: Tick in the household zone and take `Daylight` as optional**

Replace the whole of `app/src/main/java/uk/co/siland/culvery/di/AppModule.kt` with:
```kotlin
package uk.co.siland.culvery.di

import android.util.Log
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.Startable
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.shell.MinuteTicker
import uk.co.siland.culvery.shell.ShellToasts
import uk.co.siland.culvery.shell.householdTicker

/**
 * An application job's uncaught failure is logged and the process lives on; with the SupervisorJob its siblings keep
 * running (3a design §3.12).
 */
internal val LoggingExceptionHandler = CoroutineExceptionHandler { _, e -> Log.e("Culvery", "An application job failed", e) }

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Multibinds
    abstract fun capabilities(): Set<Capability>

    @Multibinds
    abstract fun startables(): Set<Startable>

    @Binds
    abstract fun toaster(impl: ShellToasts): Toaster

    /** Bound by the weather capability; without it the theme keeps 07:00 / 19:00 (4b design §3.8). */
    @BindsOptionalOf
    abstract fun daylight(): Daylight

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + LoggingExceptionHandler)

        @Provides
        fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }

        /** The clock, the date and the theme in the household's zone, as the calendar's "today" (4b design §3.8). */
        @Provides
        fun minuteTicker(zone: HouseholdZone, clock: WallClock): MinuteTicker = householdTicker(zone.zone, clock)
    }
}
```

- [ ] **Step 7: Collect header items and follow today's sun times**

Replace the whole of `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt` with:
```kotlin
package uk.co.siland.culvery.shell

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomePlacement
import uk.co.siland.culvery.core.plugin.ShellNavigator
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.core.plugin.retryWithBackoff

@HiltViewModel
class ShellViewModel @Inject constructor(
    capabilities: Set<@JvmSuppressWildcards Capability>,
    ticker: MinuteTicker,
    private val access: AccessControl,
    daylight: Optional<Daylight>,
) : ViewModel(), ShellNavigator {
    private val ordered = capabilities.sortedBy { it.order }
    private val selected = MutableStateFlow(HOME_TAB_ID)
    private val settingsOpen = MutableStateFlow(false)
    private val previewing = MutableStateFlow(false)
    private val kioskExitEvents = Channel<Unit>(Channel.BUFFERED)
    val kioskExit: Flow<Unit> = kioskExitEvents.receiveAsFlow()

    private val now: StateFlow<LocalDateTime> =
        ticker.ticks().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    // Without Daylight, or until today's times are known, ThemeSchedule's 07:00 / 19:00 applies (4b design §3.8).
    private val sunToday: Flow<SunTimes?> = daylight.orElse(null)?.today
        ?.retryWithBackoff { Log.w(TAG, "Couldn't read today's sun times (${it::class.simpleName}); retrying") }
        ?.onStart { emit(null) }
        ?: flowOf(null)

    private val scheduledDark: StateFlow<Boolean> =
        combine(now, sunToday) { time, sun -> ThemeSchedule.isDark(time.toLocalTime(), sun) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSchedule.isDark(LocalTime.now(), null))

    private val tabs: Flow<List<TabItem>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                ordered.map { cap ->
                    // onStart after the retry: a retry must not hide a tab that was showing.
                    cap.hasTab.retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read whether it has a tab; retrying", it) }
                        .onStart { emit(false) }
                        .map { shown -> if (shown) TabItem(cap.id, cap.label, cap.icon) else null }
                },
            ) {
                it.filterNotNull()
            }
        }

    private val placements: Flow<List<HomePlacement>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                ordered.map { cap ->
                    cap.cards().retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its Home cards; retrying", it) }
                        .onStart { emit(emptyList()) }
                },
            ) { lists -> HomeCardPlacer.place(lists.toList().flatten()) }
        }

    private val headerItems: Flow<List<HeaderItem>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                ordered.map { cap ->
                    cap.headerItems()
                        .retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its header items (${it::class.simpleName}); retrying") }
                        .onStart { emit(emptyList()) }
                },
            ) { lists -> lists.toList().flatten().sortedWith(compareBy({ it.order }, { it.id })) }
        }

    val uiState: StateFlow<ShellUiState> =
        combine(
            combine(tabs, selected, access.session, placements, settingsOpen) { tabs, sel, session, cards, settings ->
                ShellUiState(
                    tabs = tabs,
                    selectedTabId = if (sel == HOME_TAB_ID || tabs.any { it.id == sel }) sel else HOME_TAB_ID,
                    session = session?.let { SessionUi(it.person.name, roleLabel(it.role)) },
                    homeCards = cards,
                    settingsOpen = settings && session != null,
                )
            },
            now,
            scheduledDark,
            previewing,
            headerItems,
        ) { state, time, scheduled, preview, header ->
            state.copy(now = time, dark = scheduled != preview, previewing = preview, headerItems = header)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState(dark = scheduledDark.value))

    init {
        viewModelScope.launch { scheduledDark.drop(1).collect { previewing.value = false } }
        viewModelScope.launch { access.session.collect { if (it == null) settingsOpen.value = false } }
    }

    fun selectTab(id: String) {
        selected.value = id
    }

    override fun openTab(id: String) = selectTab(id)

    override fun openSettings() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.SETTINGS_MANAGE) != null) settingsOpen.value = true
        }
    }

    fun closeSettings() {
        settingsOpen.value = false
    }

    override fun exitKiosk() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.KIOSK_EXIT) != null) {
                settingsOpen.value = false
                access.lock()
                kioskExitEvents.send(Unit)
            }
        }
    }

    fun signOut() = access.lock()

    fun toggleThemePreview() {
        previewing.value = !previewing.value
    }
}

internal fun roleLabel(role: Role): String = when (role) {
    Role.ADMIN -> "Admin"
    Role.ADULT -> "Adult"
    Role.CHILD -> "Child"
}

private const val TAG = "ShellViewModel"
```

- [ ] **Step 8: Lay out the header items**

Replace the whole of `app/src/main/java/uk/co/siland/culvery/shell/ui/HomeScreen.kt` with:
```kotlin
package uk.co.siland.culvery.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
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
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomePlacement
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhType

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

// Hand-off CSS: clock line-height 0.9 (11 px below its baseline) + 12 px margin + the date's 21 px ascent.
private val CLOCK_TO_DATE_BASELINES = 44.dp

// Hand-off §1: header items 28 apart, a 1 × 48 `line` divider between two, their row 4 above the date's bottom.
private val HEADER_ITEM_GAP = 28.dp
private val HEADER_DIVIDER_WIDTH = 1.dp
private val HEADER_DIVIDER_HEIGHT = 48.dp
private val HEADER_ITEMS_BOTTOM = 4.dp

@Composable
fun HomeScreen(now: LocalDateTime, placements: List<HomePlacement>, headerItems: List<HeaderItem> = emptyList()) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        HomeHeader(now, headerItems, Modifier.fillMaxWidth())
        HomeGrid(placements, Modifier.fillMaxWidth().weight(1f))
    }
}

/**
 * Clock and date, with the date placed by baseline because the clock's line-height trim doesn't apply reliably; the
 * capabilities' items at the right, their bottom [HEADER_ITEMS_BOTTOM] above the date's (4b design §4.1).
 */
@Composable
fun HomeHeader(now: LocalDateTime, items: List<HeaderItem>, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    Layout(
        modifier = modifier,
        content = {
            Text(now.format(CLOCK), style = HhType.clock, color = c.ink, modifier = Modifier.testTag("home_clock"))
            Text(now.format(DATE), style = HhType.date, color = c.mute, modifier = Modifier.testTag("home_date"))
            HeaderItems(items)
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val clock = measurables[0].measure(loose)
        val date = measurables[1].measure(loose)
        val right = measurables[2].measure(loose)
        val dateY = clock[LastBaseline] + CLOCK_TO_DATE_BASELINES.roundToPx() - date[FirstBaseline]
        val dateBottom = dateY + date.height
        val width = constraints.constrainWidth(maxOf(clock.width, date.width))
        val height = constraints.constrainHeight(maxOf(dateBottom, right.height))
        layout(width, height) {
            clock.place(0, 0)
            date.place(0, dateY)
            right.place(width - right.width, maxOf(0, dateBottom - HEADER_ITEMS_BOTTOM.roundToPx() - right.height))
        }
    }
}

@Composable
private fun HeaderItems(items: List<HeaderItem>) {
    val c = Culvery.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HEADER_ITEM_GAP),
        modifier = Modifier.testTag("home_header_items"),
    ) {
        items.forEachIndexed { i, item ->
            key(item.id) {
                if (i > 0) {
                    Box(Modifier.testTag("home_header_divider").size(HEADER_DIVIDER_WIDTH, HEADER_DIVIDER_HEIGHT).background(c.line))
                }
                Box(Modifier.testTag("home_header_${item.id}")) { item.content() }
            }
        }
    }
}
```

In `app/src/main/java/uk/co/siland/culvery/shell/ui/CulveryShell.kt`, replace `HomeScreen(state.now, state.homeCards)` with `HomeScreen(state.now, state.homeCards, state.headerItems)`.

- [ ] **Step 9: Run the tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (the two new screenshots have no baselines yet, which `testDebugUnitTest` doesn't check). `ShellLayoutTest.dateBaselineSits44dpBelowClockBaseline` still passes: an empty item row is 0 × 0.

- [ ] **Step 10: Record and look at the whole-Home screenshots**

Run: `./gradlew :app:recordRoborazziDebug --tests "*ShellScreenshotTest.homeWithWeather*"`

Open `app/src/test/screenshots/home_weather_dark.png` and `home_weather_light.png` and check: the clock and date at the left as in `home_calendar_*`; the weather item at the far right, its bottom just above the date's, the amber icon, "17°" and "High 19° · Low 11°"; Today in column 1, Coming up across columns 2–3 of row 1, the Forecast card at row 2, column 2 (rows "Today", "Thu", "Fri"), and row 2, column 3 empty (D1). Then confirm the existing baselines didn't move: `./gradlew :app:verifyRoborazziDebug` passes.

- [ ] **Step 11: Run the gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`. Hilt resolves `Optional<Daylight>` (present: `WeatherModule` binds it) and `MinuteTicker` from `HouseholdZone` and `WallClock`.

- [ ] **Step 12: Commit**

```bash
git add app
git commit -m "Show capabilities' items in Home's header, keep the clock and theme in the household's zone, and switch the theme at sunrise and sunset"
```

---

### Task 11: The emulator walkthrough, the README and the follow-ups (§6, §7, §8; ruling 11)

**Review:** sonnet.

**Files:**
- Modify (after the checkpoint): `README.md`, `docs/superpowers/plans/2026-09-23-plan1-followups.md`

**Interfaces:**
- Consumes: everything above; the debug build's **Use a sample household** (London).
- Produces: documentation only.

Who does what: the implementer checks the emulator and installs the build (Steps 1–2) and stops. **The controller** runs the walkthrough with the user (Step 3), driving the tablet with `adb` and screenshots. The emulator is the **already-running API 35 Google Play emulator, `emulator-5554`**; don't start or wipe another. To go offline use `adb -s emulator-5554 shell svc wifi disable` and `svc data disable` (`enable` to come back), never airplane mode. Use only public towns (London, Edinburgh, Tokyo), never the household's own.

- [ ] **Step 1: Check the emulator**

```bash
adb devices
adb -s emulator-5554 shell getprop ro.build.version.sdk
```
Expected: `emulator-5554 device`, `35`. If `adb` isn't on PATH, use `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb"`. If the emulator isn't running, stop and ask the user to start it.

- [ ] **Step 2: Install over the existing app**

```bash
./gradlew :app:installDebug
adb -s emulator-5554 shell am force-stop uk.co.siland.culvery
adb -s emulator-5554 shell am start -n uk.co.siland.culvery/.MainActivity
adb -s emulator-5554 logcat -d | grep -iE "FATAL|IllegalStateException|Weather|OpenMeteo" | tail -20
adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4b-install.png"
```
Expected: the app opens (Home, or the wizard on a fresh install) with no crash; any `Weather`/`OpenMeteo` lines name only exception types or HTTP codes. **Stop and report** Steps 1–2 with the screenshot; the implementer does not go on.

- [ ] **Step 3: STOP — the controller runs the walkthrough and the USER CHECKPOINT with the user**

The controller, not the implementer, does this, and does not start Step 4 until the user approves. Take `adb -s emulator-5554 exec-out screencap -p > "$TMP/culvery-4b-<n>.png"` at each numbered item and send them with the report. Ask the user before each step that changes the household's data.

1. **No location.** `adb -s emulator-5554 shell pm clear uk.co.siland.culvery`; start it; in the wizard tap **Start**, then **Skip for now** on Home location, then make a test Admin (the user types the PIN) and skip the rest. Home: no weather in the header; the Forecast card at row 1, column 2 (no calendar, so no Coming up) says "Add your home location to see the weather." with **Open settings**. Tap it: the PIN pad, then Settings on **Home location**.
2. **Set a location, see weather.** In Settings › Home location choose London; close Settings. Within a minute the header shows the weather (icon, "{t}°", "High … · Low …") and the card shows "Today" and the next two days.
3. **Change town.** Go offline (`svc wifi disable; svc data disable`), then Settings › Home location › Edinburgh: at once the header empties and the card says "Getting the forecast…" (London's weather is gone). Back online (`svc wifi enable; svc data enable`): Edinburgh's weather arrives within 5 minutes (the retry after the offline attempt).
4. **The cache carries on offline.** Offline again; `am force-stop uk.co.siland.culvery`; start it: the header and the card still show Edinburgh's weather. Stay online again afterwards.
5. **The theme at sunset, in the household's zone.** First try `adb -s emulator-5554 root`; if it is refused (Google Play images refuse it), instead set the home location to a town where the sun has already set or not yet risen (e.g. Tokyo during the UK's afternoon): the status bar's clock and Home's clock jump to that town's time, and the theme turns dark with "Auto" in the status bar, within a minute. Set it back to Edinburgh: light again (if it is daytime there). If root works instead, turn off automatic time (`adb -s emulator-5554 shell settings put global auto_time 0`), set the clock a minute past today's sunset in the household's zone with `adb -s emulator-5554 shell date MMDDhhmmYYYY.00` (the emulator's own zone; work the value out first), see the theme turn dark within a minute, then restore automatic time (`settings put global auto_time 1`).
6. **No coordinates in the log.**
   ```bash
   adb -s emulator-5554 logcat -d | grep -E "Weather|OpenMeteo|ShellViewModel" | tail -40
   ```
   No line holds a coordinate, a town name or a zone id.
7. **The sample household.** `pm clear`; Welcome › **Use a sample household**: Home shows Today, Coming up, the Forecast card at row 2, column 2 and London's weather in the header.

Send the user these images, dark and light, from `capability/weather/src/test/screenshots/` and `app/src/test/screenshots/`: `forecast_*`, `header_*`, `home_weather_*`, and the walkthrough screenshots. Name the parts that are this plan's own choices, which the spec doesn't give (all provisional, for the end-of-v1 review):
- the card's rows 44 dp, the title in every state, the icons in sun amber in the card as in the header, temperatures right-aligned in 44 dp columns;
- the TalkBack words for each condition, and "Today, …" for the first row;
- the header items 4 dp above the date's bottom, 28 dp apart, a 1 × 48 dp divider (from the hand-off's CSS);
- Open-Meteo's single UTC offset per answer (ruling 2): after a clock change, data fetched before it is an hour out until the next fetch.

Ask: "Do these match what you want? Any changes before I update the README?"

- **If the user asks for changes:** make them, re-record only the affected images with `--tests`, look at them, run `./gradlew testDebugUnitTest verifyRoborazziDebug`, re-send them, and commit with a message describing the change. Repeat until approved.
- **When approved:** continue to Step 4.

- [ ] **Step 4: Update the README**

In `README.md`:
1. **Build and run.** After the paragraph starting "In debug builds Welcome also offers **Use a sample household**", add:
```markdown
Once a home location is set, Home's header shows the weather now and the Forecast card shows today and the next two days, from Open-Meteo every 30 minutes (5 minutes after a failed try); offline, both carry on from the last forecast, and after a change of town the old town's weather is never shown. The clock, the date and the theme follow the household's time zone, and the theme turns dark at that day's sunset and light at sunrise (07:00 and 19:00 until the first forecast arrives, and on a day without a sunset).
```
2. **Modules.** After the `:capability:calendar-testkit` row add:
```markdown
| `:capability:weather` | Weather contract (`WeatherProvider`), `weather.db` (the last forecast, for the place it was fetched for), the 30-minute fetch, the header item, the Forecast card, today's sun times (`Daylight`) |
```
   Replace the `:provider:weather-openmeteo` row with:
```markdown
| `:provider:weather-openmeteo` | Town search (geocoding) and the forecast through Open-Meteo (no key) |
```
   In the `:core:plugin` row, add `HeaderItem`, `Daylight` after `HomeCard`. In the `:core:household` row, after "home location" add ", the household's time zone (`HouseholdZone`)".
3. **Adding a capability.** In step 2, after the sentence about `setupSteps()` and `settingsPages()`, add: "Items on the right of Home's header come from `headerItems()` (placed by `HeaderItem.order`: weather 10, then indoor climate 20)."
4. **Privacy.** Before `## Licences` add:
```markdown
## Privacy

Open-Meteo (town search and weather; no key, no account) receives the town typed into the search, and then the home's coordinates and time zone with each forecast request, every 30 minutes. Nothing that identifies the household or its people is sent to it. Neither the town, the coordinates nor the time zone is written to the app's log.

```

- [ ] **Step 5: Check the README reads sensibly**

Read `README.md` through once: the tables are balanced and:
```bash
grep -n "the forecast comes in Plan 4b" README.md
```
Expected: no output.

- [ ] **Step 6: Update the follow-ups**

In `docs/superpowers/plans/2026-09-23-plan1-followups.md`:
1. Under "## For Plan 2 (Calendar capability)", delete "`HomeCardPlacer` has no tests with mixed sizes or a full grid. Add them once real cards exist.": it was already met before 4b (`HomeCardPlacerTest.mixedSizesFillTallThenWideThenRegularBesideIt` and `fullGridDropsEverythingElse`); Task 2 adds only the Forecast beside Connect.
2. Under "## For Plan 4 (weather, setup, settings, release)", delete "Run the theme schedule in the household's timezone (`HomeLocation.timeZoneId`) and feed it sunrise/sunset." (Tasks 2, 7, 10).
3. Add at the end of the file:
```markdown

## From Plan 4b (deferred)

**For Plan 4c**
- The weather on the SM-T510: the header and the Forecast card at its density, and the fetch over a whole day on the wall.
- The calendar's Google client has no `callTimeout` either (only connect 15 s and read 30 s, `GoogleCalendarModule`), so a body that drips in can hold a sync pass until the engine's own timeout; give it a whole-call limit as the forecast's (4b plan review 2).
- On a cold start the clock, the date and the theme show the device's zone for a moment, until Room answers with the household's (`ShellViewModel.now` starts from `LocalDateTime.now()`). Accepted in 4b; check on the SM-T510 whether it shows.

**For the end-of-v1 design and UX review**
- All 4b layout, colour and copy choices are provisional: the Forecast card's rows, its title in every state, the card's icons in sun amber like the header's, the TalkBack condition words, the header items' spacing and divider.

**Later**
- Open-Meteo answers with one UTC offset for the whole forecast, so data fetched before a clock change is an hour out for the days after it until the next fetch; offline across a clock change, the header's hour and the theme's sunset are an hour out. Known and untested in 4b (ruling 2). Fix: ask with `timeformat=unixtime` and convert each time in the household's zone (dates from the daily rows' own instants).
```
   and add under it any item you or the user noted during this plan that was deferred rather than fixed.

- [ ] **Step 7: Commit**

```bash
git add README.md docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Document the weather, the household-zone clock and theme, and what Open-Meteo receives"
```

---

## Spec coverage (4b design → tasks)

| Design | Where |
|---|---|
| §1 scope | Tasks 1–11 |
| D1 Forecast REGULAR, 3 days; row 2 col 3 empty | Tasks 2 (the placer: the existing `mixedSizesFillTallThenWideThenRegularBesideIt` plus the Connect case), 3 (`FORECAST_DAYS`), 8, 9 (priority 40), 10 (whole-Home screenshot) |
| D2 no location: header hidden, prompt opens Settings (PIN) | Tasks 3 (`NoLocation`), 8 (`noLocationOpensSettings`), 9 (no header item); walkthrough item 1 |
| D3 the cache: 7 days daily and hourly; the hour now; card from today; age line past 2 h; Expired | Tasks 3 (`weatherView`, `updatedAgo`), 4, 5 (`DAYS_FETCHED`, the seven dates kept), 7, 8 (`theHostMovesTheAgeLineOnWithTheClock`) |
| D4 °C only | Tasks 3 (`degrees`), 5 (°C from the API); Global Constraints |
| D5 rows: day, icon, high / low | Tasks 3, 8 |
| D6 `headerItems()` and `Daylight`; `:app` never names weather; `HouseholdZone` to `:core:household` | Tasks 1, 2, 9, 10 (`AppSourceTest`); Task 3 (`ModuleBoundariesTest`) |
| D7 no Connection, no wizard step, no Settings page, no tab | Task 9 (`hasTab` false; no `setupSteps`/`settingsPages`) |
| §3.1 the module, its contract, `Set<WeatherProvider>` (none: Waiting; several: first by id), `WeatherCapability` (60, no tab) | Tasks 3, 6 (`theFirstProviderByIdIsAsked`, `withNoProviderNothingIsFetchedOrStored`), 9 |
| §3.2 `HeaderItem`, `Capability.headerItems()`, `Daylight`; `@BindsOptionalOf` | Tasks 2, 10 |
| §3.3 `HouseholdZone` moved, behaviour unchanged; its parse shared as `zoneOrDevice` | Task 1 (used in Task 3) |
| §3.4 the request, its own client (15 s / 30 s, plus a 60 s whole call), `Call.await`, WMO mapping, failures (and a body over 1 MiB), logs | Task 5 (rulings 1, 2, 15; plan review 2, 13) |
| §3.5 `weather.db` v1, one-transaction replace, failed fetch leaves it, matching | Tasks 4, 6 (ruling 7) |
| §3.6 `WeatherSyncLoop`: on start, on change (only a real change, plan review 3), 30 min, 5 min after failure, waits without a location, survives an `Error` and a stray cancellation | Task 6 |
| §3.7 `WeatherView`, `weatherView`, the repository's flows, the household-zone minute, `Daylight.today` | Tasks 3, 7 (ruling 5); the card's tick is Task 2's `rememberNowMillis` |
| §3.8 header items collected and ordered; `MinuteTicker` in the household zone (a failed zone read retried); `scheduledDark` from `Daylight`; preview reset and 400 ms unchanged | Task 10 |
| §4.1 header item: icon 44 dp amber, "17°", "High · Low", shown only when Ready with now, night icons, placement and divider | Tasks 3 (`headerWeather`), 8 (the amber token; the card radius token), 9, 10 (ruling 10) |
| §4.2 the card: states, copy, TalkBack rows, age line, icons, rounding | Tasks 3, 8 (glyph check: all present) |
| §5 errors and offline | Tasks 4, 5, 6, 7; walkthrough items 3–4 |
| §6 privacy: what Open-Meteo receives; nothing in logs | Tasks 5, 6 (log tests), 11 (README, ruling 11) |
| §7 testing: unit, provider, Roborazzi light and dark, emulator walkthrough | every task; Task 11 |
| §8 follow-ups taken | The placer's mixed sizes were already covered (Task 11 Step 6 marks it met); the theme in the zone with sun times: Tasks 2, 7, 10 |
| §9 review focus | Review Focus above |
| §10 out of scope | nothing here adds °F, chance of rain, a tab or page, tapping, hourly detail, alerts or Climate |
