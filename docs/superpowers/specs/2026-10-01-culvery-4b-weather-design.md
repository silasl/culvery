# Culvery 4b: weather (design)

**Date:** 2026-10-01
**Status:** Draft, for the user's review
**Parent spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§4, §7, §9.1, §9.2)
**Previous plan:** `docs/superpowers/specs/2026-09-29-culvery-4a-setup-settings-design.md` (4a, setup and Settings)
**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (§8 says which items 4b takes)
**Builds on:** Plan 4a, merged to `main`.

## 1. Scope

4b adds:
- `:capability:weather`: the `WeatherProvider` contract, `weather.db`, the fetch loop, the Home header weather item and the Forecast card;
- the forecast call in `:provider:weather-openmeteo` (which has held only geocoding since 4a);
- two generic seams in `:core:plugin`: header items contributed by capabilities, and `Daylight` (today's sun times);
- `HouseholdZone` moved to `:core:household`, so the shell's clock, date and theme run in the household's zone;
- the theme switching at sunrise and sunset.

## 2. Decisions (agreed with the user)

| # | Decision |
|---|---|
| D1 | **Forecast card is REGULAR and shows 3 days** (today and the next two). Overrides v1 §9.2's "next 5 days". Row 2, col 3 stays empty in v1. |
| D2 | **No home location:** the header item is hidden and the Forecast cell shows a prompt to set the location, opening Settings (PIN as for the Connect-a-calendar card). |
| D3 | **Weather lives off the cache:** each fetch stores 7 days of daily and hourly data; the header shows the stored hour matching now; the card starts at today; past 2 h old the card says "Updated {x} ago"; with no data for today the header hides and the card says to check the Wi-Fi. |
| D4 | **°C only.** Stored in °C; °F could later be a display choice. |
| D5 | **Card rows:** day, icon, high / low. No chance of rain. |
| D6 | **Seams (approach A):** `Capability.headerItems()` and a `Daylight` interface in `:core:plugin`; `:app` never names the weather module. `HouseholdZone` moves to `:core:household`. |
| D7 | **No Connection for weather.** Open-Meteo needs no key; weather runs whenever a home location is set. No wizard step, no Settings page, no rail tab. |

## 3. Architecture

### 3.1 `:capability:weather` (new)

Depends on `:core:plugin`, `:core:household`, `:core:ui`. Never on a provider.

```kotlin
interface WeatherProvider {
    val descriptor: ProviderDescriptor            // "weather.openmeteo"
    /** Times are wall times in [zone]; temperatures °C. Throws WeatherUnavailableException on any failure; nothing else escapes. */
    suspend fun forecast(latitude: Double, longitude: Double, zone: ZoneId): Forecast
}

data class Forecast(val days: List<DailyWeather>, val hours: List<HourlyWeather>)

data class DailyWeather(
    val date: LocalDate,
    val condition: Condition,
    val high: Double,
    val low: Double,
    val sunrise: LocalTime?,          // null on a polar day or night
    val sunset: LocalTime?,
)

data class HourlyWeather(val start: LocalDateTime, val condition: Condition, val temperature: Double)

enum class Condition { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SHOWERS, SNOW, THUNDER }

class WeatherUnavailableException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)
```

- `Condition` is the capability's; each provider maps its own codes onto it.
- The capability receives `Set<WeatherProvider>` and uses the one bound. With none it never fetches (the card stays at Waiting once a location exists); with more than one it takes the first by `descriptor.id`.
- `WeatherCapability`: `id = "weather"`, `order = 60` (no rail position is used), `hasTab = flowOf(false)`, an empty `TabContent`, `cards()` with the Forecast card, `headerItems()` with the weather item.
- `WeatherUnavailableException` is the weather contract's own: the calendar's `UnreachableException` lives in `:capability:calendar`, which weather can't depend on.

### 3.2 `:core:plugin` additions

```kotlin
class HeaderItem(val id: String, val order: Int, val content: @Composable () -> Unit)

interface Capability : HomeCardContributor {
    // …
    /** Items on the right of Home's header, by [HeaderItem.order]: weather 10, later indoor climate 20. */
    fun headerItems(): Flow<List<HeaderItem>> = flowOf(emptyList())
}

/** Today's sunrise and sunset in the household's zone, or null when unknown. */
interface Daylight {
    val today: Flow<SunTimes?>
}
```

`:app` declares `@BindsOptionalOf Daylight`; `:capability:weather` binds it.

### 3.3 `:core:household`

`HouseholdZone` moves here from `:capability:calendar` unchanged (package `uk.co.siland.culvery.core.household`). The calendar's imports change; its behaviour doesn't.

### 3.4 `:provider:weather-openmeteo`

- Gains a dependency on `:capability:weather`; binds `WeatherProvider` `@IntoSet` alongside the existing `LocationSearch`.
- `GET https://api.open-meteo.com/v1/forecast` with `latitude`, `longitude`, `timezone={zone id}`, `forecast_days=7`, `daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset`, `hourly=temperature_2m,weather_code`. Times come back as local ISO date-times without offset.
- Its own OkHttp client with the calendar sync's timeouts (the town search keeps its shorter 10 s / 15 s client). Reuses the module's `Call.await` (cancellation cancels the call).
- WMO codes → `Condition`: 0 CLEAR; 1–2 PARTLY_CLOUDY; 3 CLOUDY; 45, 48 FOG; 51–57 DRIZZLE; 61–67 RAIN; 71–77, 85–86 SNOW; 80–82 SHOWERS; 95–99 THUNDER; anything else CLOUDY.
- Network failure, non-2xx, unreadable JSON, a missing required array, or daily/hourly arrays of unequal length → `WeatherUnavailableException`. Logs carry the exception type or HTTP code only; never the URL, the body, the coordinates or the zone.

### 3.5 Storage (`weather.db` v1)

- `fetch`: one row: `latitude`, `longitude`, `zoneId`, `fetchedAtMillis`.
- `day`: `date` (PK, ISO local date), `condition`, `high`, `low`, `sunrise` (nullable), `sunset` (nullable).
- `hour`: `start` (PK, ISO local date-time), `condition`, `temperature`.
- A successful fetch replaces all three tables in one transaction. A failed fetch leaves them alone.
- Stored data **matches** the home location when its latitude, longitude and zone id equal the current `HomeLocation`'s. Data that doesn't match is treated as absent, so after a move the old town's weather is never shown.

### 3.6 `WeatherSyncLoop`

`Startable`, on the `@ApplicationScope` scope, shaped like `CalendarSyncLoop`:
- Fetches as soon as a location is known, whenever latitude, longitude or zone change (`distinctUntilChanged`), and otherwise every **30 min**.
- After a failed fetch, the next one is in **5 min**; after a success, 30 min.
- With no location it waits for one and fetches nothing.
- A failure (an `Error` too) is logged by type and never ends the loop; a stray internal cancellation doesn't stop it (as the calendar loop).

### 3.7 What the UI reads

```kotlin
sealed interface WeatherView {
    data object NoLocation : WeatherView
    data object Waiting : WeatherView                      // location set, no matching data
    data object Expired : WeatherView                      // matching data, nothing for today
    data class Ready(
        val now: HourlyWeather?,                           // the stored hour containing now; null if missing
        val today: DailyWeather,
        val days: List<DailyWeather>,                      // today and up to the next two
        val fetchedAtMillis: Long,
    ) : WeatherView
}

fun weatherView(location: HomeLocation?, stored: StoredWeather?, now: LocalDateTime): WeatherView
```

- `WeatherRepository` exposes the location and stored data as flows; `weatherView` is pure and holds the rules.
- The composables supply `now` from a minute tick in the household zone (as the calendar's `rememberToday`).
- **`Daylight.today`**: today's `sunrise`/`sunset` from matching stored data, however old (sun times barely move day to day). `null` when there is no matching row for today, or either time is null. `ThemeSchedule` already falls back when `sunrise >= sunset`.

### 3.8 Shell (`:app`)

- `ShellViewModel` collects `headerItems()` from every capability as it does `cards()` (`retryWithBackoff`, starting empty), sorted by order, into `ShellUiState.headerItems`.
- `MinuteTicker` emits wall time in the household zone (`HouseholdZone.zone`), so the clock, the date and the theme agree with the calendar's "today".
- `scheduledDark = combine(now, daylight.today) { now, sun -> ThemeSchedule.isDark(now.toLocalTime(), sun) }`; with no `Daylight` bound, `sun` is always null (07:00 / 19:00). The preview reset on a schedule flip and the 400 ms transition are unchanged.

## 4. Screens

Hand-off visual language, light and dark. Layout numbers in a `WeatherDimens` object; none inline. All copy and layout values are provisional, for the end-of-v1 design review.

### 4.1 Header weather item

- Per hand-off §1: condition icon 44 dp in the sun amber `#E0B85B` (a `:core:ui` token, added if missing), **"17°"** 34 sp / 600 from `Ready.now`, **"High 19° · Low 11°"** 14 sp muted from `Ready.today`.
- Shown only when the view is `Ready` and `now` is not null; otherwise the header's right side is empty.
- Night icons (`clear_night`, `partly_cloudy_night`) when now is before today's sunrise or at/after its sunset; day icons otherwise or when sun times are unknown.
- The shell places header items to the right of the clock and date, bottom-aligned with the date, with a 1 dp `line` divider between items (none with a single item).

### 4.2 Forecast card

REGULAR, priority 40, so it is placed after Today (TALL, 100) and Coming up (WIDE, 50), at row 2, col 2.

- **Ready:** title "Forecast" (as Today's title); three rows: "Today", then short day names ("Fri", "Sat"), a 30 dp icon, the high (**"19°"**) and the low muted (**"11°"**). Each row is one TalkBack item: "Friday, partly cloudy, high 17°, low 10°". If fewer than three days remain, the rows that exist are shown. When the data is more than 2 h old, a muted bottom line: "Updated 3 h ago" in whole hours up to 23 h, then "Updated {n} day(s) ago".
- **Waiting:** "Getting the forecast…"
- **Expired:** "No forecast — check the tablet's Wi-Fi."
- **NoLocation:** "Add your home location to see the weather." and an **Open settings** pill (`ShellNavigator.openSettings()`; Settings opens on Home location, its first page).

Icons (day): CLEAR `sunny`, PARTLY_CLOUDY `partly_cloudy_day`, CLOUDY `cloud`, FOG `foggy`, DRIZZLE `rainy_light`, RAIN `rainy`, SHOWERS `rainy_heavy`, SNOW `weather_snowy`, THUNDER `thunderstorm`. The plan checks each glyph is in the bundled Material Symbols font and swaps any that isn't. The card always uses day icons.

Temperatures are rounded half-up to whole degrees; a value that rounds to zero shows "0°", never "-0°".

## 5. Errors and offline

- Fetch failing (any `WeatherUnavailableException`) → logged by type; the next try in 5 min; the UI carries on from the cache (D3).
- The store failing to write → logged by type; the old data stays.
- A day without sunrise or sunset → stored null; `Daylight` gives null; the theme uses 07:00 / 19:00 that day.
- A missing hour → only the header hides.
- No provider bound → the card shows Waiting once a location exists (debug and release always bind Open-Meteo).

## 6. Privacy

- Open-Meteo receives coordinates and the zone id, nothing that identifies the household. The README's privacy note says so.
- No coordinates, place name or zone id reaches a log (as 4a §3.8).

## 7. Testing

- **Unit (JUnit4 + Robolectric + Turbine):**
  - `weatherView`: each state; a location change hides old data; data that runs out of today → Expired; the 2 h age boundary; midnight moves "today" and drops a day; fewer than three days left; a missing hour.
  - `Daylight`: old matching data still gives today's times; non-matching or no data → null; a null sunrise or sunset → null.
  - `WeatherSyncLoop` (virtual time): a fetch on start, on a location change and every 30 min; 5 min after a failure; nothing without a location; survives an `Error` and a stray cancellation.
  - Store: replace is atomic; non-matching data reads as absent.
  - Shell: header items collected and ordered; the theme flips at sunset and sunrise in the household zone with a fake clock; falls back to 07:00 / 19:00 without `Daylight`; the ticker emits household-zone time.
  - `HomeCardPlacer`: Today + Coming up + Forecast (and Connect + Coming up + Forecast) place as in §4.2 (Plan 1 follow-up: mixed sizes).
- **Provider (MockWebServer):** a recorded Open-Meteo fixture parses into days and hours; the request carries every parameter in §3.4; WMO mapping including an unknown code; non-2xx, bad JSON, missing array and unequal arrays → `WeatherUnavailableException`; cancellation cancels the call; no coordinates or zone in the logs.
- **Roborazzi, light and dark:** the header with weather (day icon, night icon); the Forecast card in Ready, Ready with the age line, Waiting, Expired and NoLocation; the whole Home with three cards and the header.
- **Emulator walkthrough** with the real Open-Meteo: set the location and see weather; change town and see the old weather go and the new arrive; go offline (`svc wifi/data disable`) and restart, and see the cache carry on; move the emulator's time past sunset and see the theme flip.

## 8. Follow-ups: what 4b does

Takes: "Run the theme schedule in the household's timezone and feed it sunrise/sunset" (Plan 1, for Plan 4); `HomeCardPlacer` mixed-size tests (Plan 1, for Plan 2).
Leaves everything else for 4c and the end-of-v1 review, unchanged.

## 9. Review focus

- The old town's weather is never shown after a location change.
- The theme follows the household zone and today's sun times, and falls back cleanly with no data, no location, or a polar day.
- The loop can't stop for good and can't spin: at most one fetch per 5 min after failures.
- No coordinates, place name or zone reaches a log.
- `:app` never imports `:capability:weather`; the module boundary test covers the new module.

## 10. Out of scope

°F; chance of rain; a Weather tab or Settings page; tapping weather; hourly detail; weather alerts; indoor temperature (Climate); anything on the SM-T510 (4c).
