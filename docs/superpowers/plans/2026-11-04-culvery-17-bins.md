# Culvery Plan 17: Bin collections — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mid Sussex bin collections show on Culvery's calendar, one neutral chip per collection day with a stripe per bin, read from the council's site once a day, with "Can’t read the bin dates" shown where calendar problems already show.

**Architecture:**
- A new read-only provider, `:provider:calendar-midsussex`, walks the council site's three pages with OkHttp and jsoup. It keeps its last read in a small DataStore and answers the engine's 5-minute passes from that, reading again only on a new day, an hour after a failure, or an Update now.
- The calendar engine gains a `bins` field on events (DB 8 → 9).
- A new health, `Unreadable(line)`, reaches the Settings row and the Calendar tab's line.
- The chip sites draw bin stripes from `BinInks` (core/ui) and per-kind choices kept in a calendar-module DataStore.

**Tech Stack:** Kotlin, Compose, Hilt, Room, DataStore, OkHttp 4.12.0, jsoup (new, MIT), JUnit4, Robolectric, MockWebServer, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-11-04-culvery-17-bins-design.md` (8d96707f).

## Global Constraints

- The house's address (house name or number, postcode, the council's address line) is personal data. It goes in `Connection.config` only, never in logs, committed files, tests, fixtures or screenshots. Tests use the made-up address `1 Example Road`, postcode `ZZ99 9ZZ`, and hand-written pages. They never use a copy of the real page.
- Never use config key `account` (`CONFIG_ACCOUNT`): Settings prints it. Keys are `house`, `postcode` and `address`.
- Logs: `Bins read: ${collections.size} collections` and `Bins read failed: ${kind.name}` only. No URL, no `Track`, no address.
- At most one read a day, plus at most 3 retries an hour apart after a failure, plus Update now. A read is 3 requests.
- A failed read never changes stored events. A good read is `fullReplace = true`.
- Wall words: `Bins: Food, Garden, Recycling, Waste` order; the line `Can’t read the bin dates.` (curly apostrophe, as house copy).
- No deprecated APIs without asking the user. Files stay UTF-8 with their existing line endings. Comments say only what code can't.
- jsoup is a new dependency, MIT-licensed, which allows shipping. It's a library, not a font, sound or icon set, so the user's ask-first rule doesn't apply. Use `org.jsoup:jsoup:1.18.1`.
- Commits have no trailers of any kind.
- Roborazzi re-records rewrite unrelated baselines. Restore every one this task didn't mean to change, file by file, then run `verifyRoborazziDebug`.

## Rulings against the spec (made while planning; the spec is the authority)

1. **Update now:** it exists only on the Calendar tab, not in Settings (spec 3.5 says both). Connecting does the first read at once. Settings gets no new button. Cost if wrong: one button later.
2. **The Family filter:** there isn't one. Spec 3.3's "Family's filter" just means tagged for Family.
3. **The sheet's "From Mid Sussex District Council" line (spec 4.3):** the source is named `Mid Sussex District Council`, so the sheet's existing "Created by" row says it. There's no new line.
4. **Colour settings (spec 4.4):** there's no per-calendar page. The bin colour rows sit under the bins calendar's card on Settings › Calendars.
5. **Unknown bin names in Settings:** they're read from the events now held. No separate list is kept.

## Review Focus

1. A cached answer clearing the failure line. Between reads, a stored failure must keep being thrown (Task 6: `aStoredFailureIsThrownUntilAGoodRead`).
2. An app restart costing an extra council read. The last read is persisted (Task 6: `aRestartAnswersFromTheStoredRead`).
3. The council listing two addresses, or the stored one gone. Address choice is by exact stored line (Task 5: `theStoredAddressIsChosenByItsLine`, `aGoneAddressIsAFailure`).
4. Black at night vanishing on the dark chip (Task 1: `everyBinInkStandsOffTheChip`).
5. Bin titles turning up as event-title suggestions in the editor (Task 2: `binEventsAreNotRemembered`).

---

### Task 1: Bin inks

**Files:**
- Create: `core/ui/src/main/java/uk/co/siland/culvery/core/ui/BinInks.kt`
- Modify: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/ContrastTest.kt`
- Test: `core/ui/src/test/java/uk/co/siland/culvery/core/ui/BinInksTest.kt`

**Interfaces:**
- Produces: `data class BinInk(val id: String, val name: String, val day: Color, val night: Color)`; `object BinInks { val all: List<BinInk>; fun byId(id: String?): BinInk?; fun colour(ink: BinInk?, c: HhColors): Color }`. `colour(null, c)` returns `c.mute`, which means unknown. Night or day is decided by `c` being a dark set: compare `c.bg` luminance < 0.5.
- Ink ids, with the names Settings shows: `blue` "Blue", `black` "Black", `brown` "Brown", `green` "Green", `grey` "Grey", `yellow` "Yellow", `purple` "Purple".
- Starting values: blue day `0xFF1F5FBF` night `0xFF6FA8F0`; black day `0xFF1B1D1E` night `0xFF9BA1A4`; brown day `0xFF7A4A21` night `0xFFC08A5A`; green day `0xFF2E7D32` night `0xFF6CC070`; grey day `0xFF6B7275` night `0xFFB4BABD`; yellow day `0xFF9A7B00` night `0xFFE0C440`; purple day `0xFF6A3FA0` night `0xFFB08CE0`. Tune any value the test rejects, keeping its hue.

- [ ] **Step 1: Write the failing tests.**
  - `BinInksTest`:
    - `sevenInksWithUniqueIds` checks the ids above in order.
    - `colourPicksNightOnADarkSet` checks `colour(black, DarkColors) == black.night` and `colour(black, LightColors) == black.day`.
    - `noInkIsMute` checks `colour(null, DarkColors) == DarkColors.mute`.
  - `ContrastTest.everyBinInkStandsOffTheChip`: for each `(name, set)` in `themes` and each ink, `check("$name ${ink.id} on surf2", BinInks.colour(ink, set), set.surf2, NON_TEXT)`. Do the same on `set.surf`.
- [ ] **Step 2:** Run `./gradlew :core:ui:testDebugUnitTest --tests '*BinInks*' --tests '*ContrastTest*'`. Expect FAIL (unresolved).
- [ ] **Step 3:** Implement `BinInks.kt` per Interfaces, with a KDoc pointing at ContrastTest as BirthdayInks does.
- [ ] **Step 4:** Run again. Expect PASS. If a contrast check fails, change only that ink's value.
- [ ] **Step 5:** Commit: `git commit -m "Add the bin inks"`.

### Task 2: Bins on events (engine and storage)

**Files:**
- Modify: `capability/calendar/.../CalendarContract.kt` (RemoteEvent), `Stored.kt` (StoredEvent), `db/CalendarDatabase.kt` (EventEntity, version 9), `db/Migrations.kt`, `CalendarStore.kt` (toEntity/toStored, the `remember` call at ~293), `CalendarUi.kt` (EventUi, toUi)
- Create: `capability/calendar/.../Bins.kt`
- Generated: `capability/calendar/schemas/.../9.json`
- Test: `capability/calendar/src/test/.../BinsTest.kt`, `CalendarMigrationTest.kt`, `CalendarStoreTest.kt` (or the store test that covers `applySync`)

**Interfaces:**
- Produces:
  - `RemoteEvent.bins: List<String>? = null` and `StoredEvent.bins: List<String>?`. Each entry is a kind key (`food`, `garden`, `recycling`, `waste`), or the council's name as written for an unknown kind.
  - `EventEntity.bins: String? = null`, stored newline-joined.
  - `MIGRATION_8_9` = `ALTER TABLE event ADD COLUMN bins TEXT`, added to `ALL_MIGRATIONS`.
  - In `Bins.kt`:
    - `enum class BinKind(val key: String, val label: String, val defaultInk: String) { FOOD("food","Food","green"), GARDEN("garden","Garden","brown"), RECYCLING("recycling","Recycling","blue"), WASTE("waste","Waste","black") }`
    - `fun binKindOf(key: String): BinKind?`
    - `fun binLabel(key: String): String`: a kind's label, else the key itself.
    - `fun orderedBins(keys: Collection<String>): List<String>`: the known kinds in enum order, then unknowns alphabetically, with duplicates removed.
    - `fun binsTitle(keys: Collection<String>): String` = `"Bins: " + orderedBins(keys).joinToString(", ") { binLabel(it) }`.
  - `EventUi.bins: List<String>?`, non-null for a bins event. Its stripe colours are resolved at draw time (Task 4).

- [ ] **Step 1: Write the failing tests.**
  - `BinsTest`:
    - `titleOrdersKnownThenUnknown`: `binsTitle(listOf("waste","Clinical Waste","food")) == "Bins: Food, Waste, Clinical Waste"`.
    - `oneBinStillSaysBins`: `binsTitle(listOf("garden")) == "Bins: Garden"`.
  - `CalendarMigrationTest`: `migrate8To9KeepsEventsAndAddsBins`, following the 7→8 test's shape.
  - Store tests:
    - `binsRoundTripThroughTheStore`: an applied `RemoteEvent(bins = listOf("food","recycling"))` reads back with the same list.
    - `binEventsAreNotRemembered`: after applying a bins event, `rememberedTitles()` has no `"Bins: …"` title.
- [ ] **Step 2:** Run `./gradlew :capability:calendar:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement the field through RemoteEvent → toEntity → EventEntity → EventRow.toStored → StoredEvent → toUi.
  - Bump the version to 9, build to generate `9.json`, and copy it where the migration test reads schemas (as for 8).
  - Filter bins events (`bins != null`) out of `rememberedFrom(...)`.
- [ ] **Step 4:** Run `./gradlew :capability:calendar:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Carry bins on calendar events"`.

### Task 3: A "can't read" health and the Update now signal

**Files:**
- Modify: `core/plugin/.../Connections.kt` (ConnectionHealth), `capability/calendar/.../CalendarContract.kt`, `CalendarStore.kt` (code/healthOf), `CalendarSync.kt` (healthAfter, severity), `CalendarRepository.kt` (problemOf), `CalendarUi.kt` (SyncProblem, problemLine), `ui/ReviewCalendars.kt` (healthWords), `CalendarEditor.kt` (updateNow)
- Create: `capability/calendar/.../UpdateRequests.kt`
- Test: the existing tests for each touched function (CalendarSyncTest, CalendarRepositoryTest, CalendarUiTest, ReviewCalendarsTest, CalendarStoreTest), plus `UpdateRequestsTest`

**Interfaces:**
- Produces:
  - `ConnectionHealth.Unreadable(val line: String)`, persisted as code `"UNREADABLE"` with `line` in `healthMessage`.
  - `class UnreadableException(val line: String, message: String? = null) : Exception(message)` in CalendarContract.kt. The KDoc names it as the third exception `sources`/`sync` may throw: the service answered, but not in a shape the provider can read; `line` is the wall's words.
  - `CalendarSync.healthAfter`: `UnreadableException` → `ConnectionHealth.Unreadable(e.line)`, logged as `"${conn.id}: a source couldn’t be read"`. Severity 1, like Error.
  - `SyncProblem.Unreadable(val line: String)`. `problemOf` order: SignIn > Offline > NoAnswer > Unreadable. `problemLine(Unreadable) = line`.
  - `healthWords(Unreadable) = line`, drawn in the danger colour like NeedsSignIn.
  - `@Singleton class UpdateRequests @Inject constructor() { val last: StateFlow<Long?>; fun request(atMillis: Long) }`. `CalendarEditor.updateNow()` calls `request(clock.millis())` before `requestSync()`.

- [ ] **Step 1: Write the failing tests.**
  - `anUnreadableSyncStoresItsLine`: a provider throwing `UnreadableException("Can’t read the bin dates.")` leaves the connection `Unreadable` with that line, and the events stay unchanged.
  - `healthRoundTripsUnreadable`: through `code()` and `healthOf`.
  - `problemOfReportsUnreadableLast`: Unreadable plus Offline gives Offline; Unreadable alone gives Unreadable.
  - `weekLineShowsTheUnreadableLine` and `settingsRowShowsTheUnreadableLineInDanger`.
  - `updateNowRecordsARequest`.
- [ ] **Step 2:** Run `./gradlew :capability:calendar:testDebugUnitTest :core:plugin:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Grep for every exhaustive `when` over `ConnectionHealth` and `SyncProblem`, and add the branch. The line comes from the provider, so no bins wording enters the engine.
- [ ] **Step 4:** Run again. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Show a calendar that can't be read"`.

### Task 4: Bin colours and the chips

**Files:**
- Create: `capability/calendar/.../BinColours.kt` (DataStore file `bin_colours`)
- Modify: `ui/WeekView.kt` (EventChip ~376-395), `ui/TodayCard.kt` (~241-257), `ui/ComingUpCard.kt` (~103-125), `ui/EventDetailSheet.kt` (~222-229), `ui/WeekLane.kt` (~86-100, fallback only), `Who.kt` (whoLine), `ui/Spoken.kt`
- Test: `BinColoursTest`, the chip/row UI tests in the calendar module, and new Roborazzi shots `week_bins_day`, `week_bins_night`, `today_bins_night`

**Interfaces:**
- Consumes: `BinInks` (Task 1); `EventUi.bins`, `BinKind`, `orderedBins`, `binLabel` (Task 2).
- Produces:
  - `@Singleton class BinColours @Inject constructor(...) { val current: StateFlow<Map<String, String>>; suspend fun set(kind: String, inkId: String); fun inkFor(kind: String): BinInk? }`. `inkFor` returns the stored choice, else `binKindOf(kind)?.defaultInk`, else null.
  - A composable helper `binStripes(bins: List<String>): List<Color>` = `orderedBins(bins).map { BinInks.colour(colours.inkFor(it), Culvery.colors) }`, provided through a CompositionLocal or a parameter, matching how the module already passes `people`.
- Rules at every draw site when `event.bins != null`:
  - The surface is `surf2`. `StripedBar(binStripes(bins), …)` replaces the people bar, with the shared-chip edge width and padding.
  - No person dots. `Icons.DELETE` (`HhIcon`, the chip's dot size) leads the title on the week chip.
  - The who line and the spoken name say "Bins", never "Family".

- [ ] **Step 1: Write the failing tests.**
  - `BinColoursTest`:
    - `defaultsAreTheUsersBins`: food→green, garden→brown, recycling→blue, waste→black.
    - `aChoiceOverridesTheDefault`.
    - `anUnknownKindHasNoInk`.
  - UI tests:
    - `aBinsChipIsNeutralWithAStripePerBin`: four bins give four `event_bar` segments in the order Food, Garden, Recycling, Waste. Assert by colour at the bar's quarter points, as BirthdaySkyTest reads pixels.
    - `aBinsChipNeverSaysFamily`: its text and content description omit "Family".
  - Roborazzi: a week with a four-bin Monday and a one-bin day, by day and by night.
- [ ] **Step 2:** Run `./gradlew :capability:calendar:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the tests. Record Roborazzi, restore the unrelated baselines, verify. Look at the three new shots. Black must be plainly visible at night.
- [ ] **Step 5:** Commit: `git commit -m "Draw bin collections with a stripe per bin"`.

### Task 5: Reading the council's pages

**Files:**
- Create: the `provider/calendar-midsussex/` module (`build.gradle.kts` shaped like calendar-fake's, plus `libs.okhttp`, `libs.jsoup`, `:core:net` if its `await` is used; test deps `:capability:calendar-testkit`, `libs.okhttp.mockwebserver`, Robolectric as Google's).
- Create: `.../provider/calendar_midsussex/CouncilPages.kt`
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml` (jsoup 1.18.1)
- Test: `CouncilPagesTest.kt` with hand-written fixtures in `src/test/resources/council/`: `start.html`, `addresses_two.html`, `addresses_none.html`, `collections.html` (a four-bin Monday, a food-only Monday and an unknown "Domestic Clinical Waste Service"), `collections_empty.html`, `collections_missing.html`, `bad_date.html`, `waste_hint.html`. Every address is made up.

**Interfaces:**
- Produces, all pure functions on HTML strings, with `baseUrl` for resolving links:
  - `enum class BinsFailure { NO_START_LINK, NO_ADDRESS_LIST, ADDRESS_GONE, NO_COLLECTIONS, BAD_DATE, NO_SERVICES }`
  - `class BinsReadFailed(val kind: BinsFailure) : Exception(kind.name)`
  - `fun searchUrl(startHtml: String, baseUrl: HttpUrl): HttpUrl`: the "View my collections" link with `seq=1` set to `seq=2`. Throws `BinsReadFailed(NO_START_LINK)`.
  - `data class Address(val line: String, val url: HttpUrl)`; `fun addresses(listHtml: String, baseUrl: HttpUrl): List<Address>`: the links in `div#property_list`, `line` = the link text trimmed. Throws `NO_ADDRESS_LIST` when the div is missing; an empty list is allowed.
  - `fun pick(addresses: List<Address>, line: String): Address`: an exact match, else `BinsReadFailed(ADDRESS_GONE)`.
  - `data class Collection(val date: LocalDate, val kinds: List<String>)`; `fun collections(houseHtml: String): List<Collection>`: one per date, sorted, kinds as keys (spec 3.1).
    - Match case-insensitively on the words `food`, `garden`, `recycling`, `refuse`; `refuse` → `waste`.
    - Otherwise use the council's name as written, trimmed (spec 3.1).
    - `span#waste-hint` → `NO_SERVICES`; no rows → `NO_COLLECTIONS`; an unparseable `dd/MM/yyyy` → `BAD_DATE`.
  - The form field names `address_name_number` and `address_postcode` are constants here.

- [ ] **Step 1: Write the failing tests.**
  - `searchUrlSwapsSeq`.
  - `noStartLinkFails`.
  - `twoAddressesAreListed`.
  - `noAddressesIsAnEmptyList`.
  - `theStoredAddressIsChosenByItsLine`.
  - `aGoneAddressIsAFailure`.
  - `collectionsGroupByDay`: the four-bin Monday is one Collection with kinds `[food, garden, recycling, waste]`.
  - `anUnknownNameKeepsTheCouncilsWords`: gives `"Domestic Clinical Waste Service"`.
  - `emptyMissingBadDateAndHintEachFail`: each with its kind.
- [ ] **Step 2:** Run `./gradlew :provider:calendar-midsussex:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement with jsoup.
- [ ] **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Read Mid Sussex's collection pages"`.

### Task 6: The provider, its cadence and its stored read

**Files:**
- Create: `.../calendar_midsussex/MidSussexBinsProvider.kt`, `BinsReads.kt` (DataStore file `bins_reads`, kotlinx serialization), `CouncilClient.kt`, `di/MidSussexModule.kt`
- Modify: `app/build.gradle.kts` (`implementation(project(":provider:calendar-midsussex"))`), `build-logic/convention/src/test/kotlin/LogHygieneTest.kt` and `LogHygiene.ALLOWED` (add `collections.size` with a KDoc line)
- Test: `MidSussexBinsProviderContractTest.kt` (extends `CalendarProviderContractTest`, MockWebServer serving Task 5's fixtures), `MidSussexBinsProviderTest.kt`

**Interfaces:**
- Consumes: Task 5's functions, `UnreadableException` and `UpdateRequests` (Task 3), `RemoteEvent.bins` and `binsTitle` (Task 2), `WallClock` and `HouseholdZone` as `CalendarSync` takes them.
- Produces:
  - `ProviderDescriptor("calendar.midsussex", "Mid Sussex bins", Icons.DELETE, setOf(Feature.READ), connectHint = "Enter the house name or number and the postcode, as on the council’s site.")`.
  - `sources()` is static, with no request: `listOf(CalendarSource("bins", "Mid Sussex District Council", writable = false, shown = true, primary = true))`.
  - `sync()` returns, from the stored read, one `RemoteEvent` per collection overlapping `range`:
    - `remoteId = "bins-<yyyy-MM-dd>"`, all-day, end = next day, `recurring = false`;
    - `forPerson = "family"`, `title = binsTitle(kinds)`, `bins = orderedBins(kinds)`;
    - `cursor = null`, `fullReplace = true`.
  - `CouncilClient(baseUrl: HttpUrl, client: Lazy<OkHttpClient>)`:
    - `suspend fun read(house: String, postcode: String, line: String): List<Collection>` does the three steps with a fresh in-memory `CookieJar` per read.
    - It also has `suspend fun search(house, postcode): List<Address>` for Task 7.
    - The client has connect 15 s, read 30 s, call 60 s, User-Agent `Mozilla/5.0 (Linux; Android) Culvery/<AppVersion>`, and redirects on.
    - Network `IOException` → `UnreachableException(msg, cause)`. HTTP 5xx/429 → `UnreachableException`. Other non-2xx → `BinsReadFailed(NO_START_LINK)` at step 1, else the step's kind.
  - The base URL `https://waste.services.midsussex.gov.uk/` is `@Provides`'d with a qualifier `@CouncilBaseUrl` and replaced in tests.
  - The client adds every interceptor in `@CouncilInterceptors Set<Interceptor>`. The module declares it with `@Multibinds`, so it's empty unless the debug build adds one (Task 8).
- Cadence (`BinsReads` holds `lastAttemptMillis`, `lastGoodDay`, `failuresToday`, `failureDay`, `failure: BinsFailure?`, `collections`). A sync reads the council when any of these hold:
  1. Nothing is stored.
  2. `lastGoodDay` < today (household zone), and either no failure today or ≥ 60 min since the last attempt with `failuresToday` < 4 (one daily attempt plus 3 retries).
  3. `UpdateRequests.last` > `lastAttemptMillis`.
- After a read decision:
  - A good read stores the collections and clears the failure, then logs `Bins read: ${collections.size} collections`.
  - A `BinsReadFailed` stores the kind, then logs `Bins read failed: ${kind.name}`.
  - When the stored state has a failure and no good read since, `sync` throws `UnreadableException("Can’t read the bin dates.")`. This applies between attempts too.
  - `UnreachableException` passes through and isn't stored as a failure, but counts as an attempt for the hourly gap.

- [ ] **Step 1: Write the failing tests.**
  - The contract test: `range()` is the fixture's four weeks. `networkFailureThrowsUnreachable` shuts the server down.
  - `MidSussexBinsProviderTest`, with a fake clock and MockWebServer:
    - `oneReadADay`: three syncs in a day give three requests total; the next day, three more.
    - `aStoredFailureIsThrownUntilAGoodRead`.
    - `retriesAnHourApartThreeTimes`.
    - `updateNowReadsAtOnce`.
    - `aRestartAnswersFromTheStoredRead`: a new provider instance on the same DataStore makes no request.
    - `aGoodReadDropsACancelledCollection`.
    - `theAddressIsNeverLogged`: under Robolectric's `ShadowLog`, no log line contains `Example` or `ZZ99`.
  - `LogHygieneTest`: the two new lines pass; `MidSussexBinsProvider.kt` is in `theScanReachesTheShippedCode`.
- [ ] **Step 2:** Run `./gradlew :provider:calendar-midsussex:testDebugUnitTest :build-logic:convention:test`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add the Mid Sussex bins calendar"`.

### Task 7: Connecting, and bin colours in Settings

**Files:**
- Modify: `MidSussexBinsProvider.kt` (ConnectScreen), `capability/calendar/.../ui/ReviewCalendars.kt` (bin colour rows under a source whose events carry bins; empty-state text no longer Google-only), `CalendarReview.kt` (exposes the bin kinds held: known four plus unknowns seen in stored events)
- Test: `MidSussexConnectScreenTest.kt` (Robolectric Compose), `ReviewCalendarsTest`, a Roborazzi shot `settings_calendars_bins`

**Interfaces:**
- ConnectScreen, inside `ConnectingCard`:
  1. Two `HhTextField`s, "House name or number" and "Postcode", then `HhButton("Find the address", tone = Primary)`.
  2. If the search finds 0 addresses: "No address found. Check the house name and postcode." If it finds 1, go to step 3. If it finds 2 or more, show a list of `HhListRow`s (the council's lines) to pick from.
  3. Read that address's collections and show `"Next: <EEEE d MMMM> — <labels>"`, then `HhButton("Add calendar")` and `HhButton("Cancel")`.
  4. On Add: `Connection(existing?.id ?: UUID, "calendar.midsussex", "Mid Sussex bins", mapOf("house" to …, "postcode" to …, "address" to line))`.
  - A failure during the search or read shows `"Can’t read the council’s site."` with Try again.
- Settings, under the bins calendar's card: a "Bin colours" section with one row per kind (label, swatch). A tap opens `HhChoiceChip`s for `BinInks.all` and saves via `BinColours.set`.
- The Settings row and every string show "Mid Sussex bins". None shows the config values.

- [ ] **Step 1: Write the failing tests.**
  - `oneAddressGoesStraightToTheNextCollection`.
  - `twoAddressesAskWhichOne`.
  - `noAddressSaysSo`.
  - `addStoresHousePostcodeAndLine`: the config keys, with no `account`.
  - `binColourRowsListKnownAndSeenKinds`.
  - `choosingAnInkSavesIt`.
  - `theRowNeverShowsTheAddress`.
- [ ] **Step 2:** Run the two modules' tests. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Record and verify Roborazzi as in Global Constraints.
- [ ] **Step 5:** Commit: `git commit -m "Connect the bins calendar and choose bin colours"`.

### Task 8: A debug sample council

**Files:**
- Create: `app/src/debug/.../DebugCouncil.kt`, an OkHttp `Interceptor` serving copies of Task 5's fixtures from `app/src/debug/assets/council/` for postcode `ZZ99 9ZZ`. It's bound into the provider's client via `@IntoSet @CouncilInterceptors` (Task 6). Release adds nothing.
- Add a debug broadcast `uk.co.siland.culvery.debug.COUNCIL --es page missing|good|unknown`, like DebugClockReceiver, to switch the collections page.
- Modify: `app/src/debug/.../DebugSeed.kt` `removeSampleWhenReplaced`, so that only a connection whose provider declares `Feature.WRITE` replaces the sample.
- Test: `DebugSeedTest.aBinsConnectionKeepsTheSample`, `DebugCouncilTest.servesThePagesForTheSamplePostcodeOnly`

- [ ] **Step 1:** Write the failing tests.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*DebugSeed*' --tests '*DebugCouncil*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expect PASS.
- [ ] **Step 5:** Commit: `git commit -m "Add a sample council for the debug build"`.

### Task 9: Simplify

- [ ] **Step 1:** Run the `simplify` skill on `git diff main...HEAD`. Keep files UTF-8 with their line endings, and keep personal data out.
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest verifyRoborazziDebug :build-logic:convention:test --continue`. Rerun load flakes in isolation.
- [ ] **Step 3:** Commit: `git commit -m "Simplify the bins code"`.

### Task 10: The emulator walk-through (spec §7, items 1–7)

**Rules:**
- `culvery-5` only, a debug build, `install -r`.
- The debug sample household plus the sample council (`1 Example Road`, `ZZ99 9ZZ`).
- Claude enters the debug sample PINs.
- Faults are collected and asked about before fixing.

- [ ] **Step 1:** Walk spec §7 items 1–7, one line each. Use the COUNCIL broadcast for items 4 and 5. Use logcat for item 6's request count and item 7's names check.
- [ ] **Step 2:** Run the fault loop, then put everything back: disconnect the bins calendar, debug clock 0, `emu kill`.
- [ ] **Step 3:** Record under "Plan 17 (bins): emulator walk-through" in the follow-ups file, with no address. Commit: `git commit -am "Record Plan 17's emulator walk-through"`.

### Task 11: Final review, merge, tablet

- [ ] **Step 1:** Run the final whole-branch review (opus).
- [ ] **Step 2:** Merge with `git merge --ff-only plan-17-bins` only on the user's say. Push only on their say.
- [ ] **Step 3:** Build the release and `install -r` on the tablet. Copy the mapping to `culvery-notes/mappings/`.
- [ ] **Step 4:** On the tablet, ask the user for the address, connect it, and check that the first read matches the council's site (spec §7 item 8).
