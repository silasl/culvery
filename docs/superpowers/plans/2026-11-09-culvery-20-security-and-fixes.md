# Culvery Plan 20: Security and small fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. This plan is executed subagent-driven.

**Goal:** Close the November review's findings in scope (M2, N1–N10) as the user ruled, link Home Assistant over http or https, and clear the small faults since Plan 18: the shopping tick's pause, Plan 18's 25 minors, calendar M2 and M3, and the Music card's Shuffle button.

**Architecture:**
- Security tasks (1–14) each change one area. What each finding is, and the exact fix and tests, are in the private companion; this plan gives files, signatures, copy and commit messages only.
- Home Assistant over https is four tasks in order: the PIN pad's detail line (7), the stored link and address (8), the trusted client (9), Check and Forget (10), then the page, rows and docs (11).
- The sound fixes go library (15–16), player (17–18), pads (19), Settings (20), door (21), generators (22). Calendar M2 splits the drain's create into locked and unlocked steps (25), then keeps undone keys in the calendar database (26). Test-only fixes (28) run last, so the flake hunt covers the branch's own changes.

**Tech Stack:** Kotlin, coroutines, Compose, Hilt, Room, DataStore, OkHttp (MockWebServer over TLS), JUnit4, Robolectric, Roborazzi, Truth; Python 3 for `tools/sound/`.

**Spec:** `docs/superpowers/specs/2026-11-09-culvery-20-security-and-fixes-design.md`. Read it before any task. Its "(Claude's call)" items are settled; don't reopen them.

**Private companion:** `culvery-notes/plan-20-security-detail.md`, in the private notes folder next to the repo. Every security task (1–14, 29, 33) reads its section there first, then its entry under "Plan 20 tasks" at the end of that file, which gives the task's test names and checks.

## Global Constraints

- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`. Per task, run only the modules it names (`./gradlew :<module>:testDebugUnitTest`). `LogHygieneTest` runs with `./gradlew -p build-logic :convention:test`.
- **Branch:** `plan-20-security-and-fixes`. Don't switch branches, push or merge.
- **No deprecated APIs** without asking the user. minSdk 29, targetSdk and compileSdk 35. Check the IDE's deprecation marks on anything you reach for.
- **Logs carry only numbers, mode names and exception class names:** no names, addresses, URLs, tokens, fingerprints or file names. A new interpolated value is a qualified id added to `LogHygiene.ALLOWED` (`build-logic/convention`) with a KDoc line saying what it is, or a `.code` / `::class.simpleName`. `LogHygieneTest` must pass.
- **Release builds strip `Log.d`, `Log.v` and `Log.i`** (`app/proguard-rules.pro`). A line the tablet check reads must be `Log.w`.
- **Durations use a monotonic clock** (`SystemClock.elapsedRealtime` in the app, injected as `() -> Long`; the test's `currentTime` in tests), never the wall clock.
- **Hilt:** no constructor calls `Provider.get()`. A new dependency that would close a cycle goes through a `Startable` instead.
- Comments say only what the code can't. Files stay UTF-8 with their existing line endings.
- **Public repo:** findings by number only (M2, N1…). No code comment, KDoc, test name, commit message or doc in the repo says what a finding is, how it could be used, an address, a host name or a serial. Where the companion gives a test name, use it; where a public test name would describe a weakness, the companion gives a neutral one. No absolute user paths.
- **Commits:** plain imperative message saying what changed for the household, no Co-Authored-By or any other trailer. Use the message the task gives.
- **Copy** is the spec's, character for character, with its curly apostrophes (`’`) and `›`.
- **Robolectric DB paths break on long Gradle temp dirs:** if a temp dir is needed, use a short one such as `C:/t9`.
- **The tablet (SM-T510):** release builds with `adb -s <tablet serial> install -r` only, never uninstall. Write `<tablet serial>`, never a real serial. Ask the user before changing any tablet setting.
- **Roborazzi:** re-records rewrite unrelated baselines. Restore every one a task didn't mean to change, file by file, then run `verifyRoborazziDebug`. Baselines this plan means to move: the PIN pad with a detail line (Task 7, new), Settings › Home Assistant (Task 11), the tray's pads before the gate's first read (Task 19, new), the Music card (Task 24). Nothing else.
- **Tests that pass today** are labelled "regression guard" in their KDoc; they aren't expected to fail at the failing-test step.
- **Test certificates** reuse the Hue tests' keystores and `TestCerts` pattern (`provider/lights-hue/src/test/resources/tls/`, `.../lights_hue/TestCerts.kt`): copy them into the module's own test resources. No `okhttp-tls` dependency.

## Rulings made while planning (the spec is the authority)

1. **The PIN pad's detail line** is a last parameter, `detail: String? = null`, on `AccessControl.authorise`. Every implementation gains it: `DefaultAccessControl`, `FakeAccess` (`core/access` test fixtures), and the test fakes in `app/.../shell/Fakes.kt`, `ShellScreenshotTest.kt`, `capability/calendar/.../StubEditor.kt`, `capability/shopping/.../ShoppingConnectionsRig.kt` and `provider/plugs-homeassistant/.../PlugsSettingsSectionTest.kt`. `FakeAccess` records the last `detail` it was given.
2. **"Turn off Needs a PIN"** is one constant, `TURN_OFF_NEEDS_A_PIN`, in `core/access/.../AccessControl.kt` next to `pinReasonText`, shared by plugs and modes.
3. **The README has no Home Assistant section today** (only the modules table). Task 11 adds a short `## Home Assistant` section after `## PINs`, in household words, with "Linking over https" and "Forget this Home Assistant".
4. **The tablet comes before https.** Task 32 installs and runs every check that doesn't need https; Task 33 is the household's https set-up with the user, then the https checks on the tablet (spec §5.3's order).
5. **11j:** `SoundPlayer` takes an `io: CoroutineDispatcher` (`Dispatchers.IO` from its `@Inject` constructor), and `playDoorbell` and `playPad` become `suspend`, opening the file `withContext(io)`. Every wrapper of them (the door module's chime `play` and `preview` in `DoorModule.kt`, `PadTaps`' `play`) becomes `suspend` too.
6. **11n:** `HouseModeThirdTile` gains `faded: Boolean = false`, drawn at `ControlTokens.DISABLED_ALPHA` without disabling the tap. `PadTaps` exposes `val gateRead: StateFlow<Boolean>`, false until `gate.blocked` first emits.
7. **11v's "format check"**: the build has no formatter. It means imports in alphabetical order with no fully qualified names in code, in the test files Plan 18 added or changed (find them with `git log --name-only` over Plan 18's commits), plus the two named nits.
8. **Calendar M2's table** is `undone_creates` (`UndoneCreateEntity(key: String @PrimaryKey, connectionId: String, sourceId: String, series: Boolean, stray: Boolean)`), with `UndoneCreateDao` (`all()`, `upsert(e)`, `delete(key)`) on `CalendarDatabase`, version 10, `MIGRATION_9_10` in `Migrations.kt` and the exported `10.json` schema.
9. **Calendar M3's cap** is `internal const val COUNTED_LAST_CAP = 1_000` in `Recurrence.kt`.

## Review Focus

1. **A household linked over http installs Plan 20 and never opens Settings:** the hoover row, the Plugs tab and the socket work as before, with no Check (Task 8: `aLinkSavedBeforePlan20StillLinks`, regression guard).
2. **An address typed with a capitalised scheme or a trailing slash** after the link moved to https: read the same as its lower-case form, so the https-only rule holds (Task 8: `theSchemeIsReadWhateverItsCase`).
3. **Forget while the socket is open and the Plugs tab is showing:** the socket closes and every row shows "not linked" at once, not after a timeout (Task 11: `forgetClosesTheSocketAndTheRowsSayNotLinked`).
4. **A phone's series with an enormous COUNT** (2,000,000,000) on each rule: the label comes back at once and nothing throws (Task 27: `aHugeCountReturnsAtOnce`).
5. **An Undo of a create the service refused while it was on its way:** no stray is kept and nothing is deleted later (Task 25: `anUndoneCreateTheServiceRefusedLeavesNoStray`).

---

### Task 1: N7, a placeholder in an older plan

**Files:** the one named in the companion's N7 section.

- [ ] **Step 1:** Make the companion's N7 change.
- [ ] **Step 2:** Run the companion's N7 check (a `git grep`). Expect no match.
- [ ] **Step 3:** Commit: `Plan 9c-1c: use a placeholder for a device value`.

### Task 2: N8, fixture dates

**Files:** `provider/calendar-midsussex/src/test/resources/council/*.html`, the provider's tests that read them (`CouncilPagesTest`, `MidSussexBinsProviderTest`, `MidSussexBinsProviderContractTest`, `MidSussexConnectScreenTest`, `FakeCouncil`), `app/src/testDebug/java/uk/co/siland/culvery/DebugCouncilTest.kt`.

- [ ] **Step 1:** Re-run the companion's N8 grep and change every hit it names, with the tests' clocks and expectations.
- [ ] **Step 2:** Run `./gradlew :provider:calendar-midsussex:testDebugUnitTest` and `./gradlew :app:testDebugUnitTest --tests '*DebugCouncilTest*'`. Expect PASS.
- [ ] **Step 3:** Commit: `Use other dates in the bins test fixtures`.

### Task 3: N10, text forms without an address

**Files:**
- Modify: `core/plugin/src/main/java/uk/co/siland/culvery/core/plugin/Connections.kt` (`Connection`), `provider/calendar-midsussex/.../CouncilPages.kt` (`Address`), `provider/calendar-midsussex/.../MidSussexConnectScreen.kt` (`Step.Pick`, `Step.Ready`, `Step.Done`).
- Test: `core/plugin/src/test/.../ConnectionsTest.kt` (create if none), `provider/calendar-midsussex/src/test/.../CouncilPagesTest.kt`, `MidSussexConnectScreenTest.kt`.

**Interfaces:**
- Produces: `toString` overrides: `Connection(id=…, provider=…)`; `Address`; `Pick`, `Ready`, `Done` as their names alone.

- [ ] **Step 1:** Write the companion's N10 tests (one per type, sample `1 Example Road`, `ZZ1 1ZZ`: `assertThat(x.toString()).doesNotContain(...)` for each value).
- [ ] **Step 2:** Run `./gradlew :core:plugin:testDebugUnitTest :provider:calendar-midsussex:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Keep addresses out of connections' and bins steps' text forms`.

### Task 4: N9, Update now and bins

**Files:** `provider/calendar-midsussex/.../MidSussexBinsProvider.kt` (`due`); test `MidSussexBinsProviderTest.kt`.

**Interfaces:**
- Produces: `internal const val UPDATE_NOW_GAP_MS = 600_000L`.

- [ ] **Step 1:** Write the companion's N9 tests.
- [ ] **Step 2:** Run `./gradlew :provider:calendar-midsussex:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement as the companion says.
- [ ] **Step 4:** Run the same and `./gradlew :capability:calendar:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Read the council's site on Update now at most every 10 minutes, unless the last read failed`.

### Task 5: N2, the council's site and internet redirects

**Files:**
- Modify: `app/src/main/res/xml/network_security_config.xml` (the https-only list only; Task 11 rewords its comment), `core/net/src/main/java/uk/co/siland/culvery/core/net/Http.kt` (`internetHttpClient()`), `provider/calendar-midsussex/.../CouncilPages.kt`, `CouncilClient.kt` (`councilHttpClient`), `provider/weather-openmeteo/.../di/OpenMeteoModule.kt`.
- Test: `app/src/test/java/uk/co/siland/culvery/CleartextTest.kt`, `core/net/src/test/.../HttpTest.kt`, `provider/calendar-midsussex/src/test/.../CouncilPagesTest.kt`, `CouncilClientTest.kt`, `provider/weather-openmeteo/src/test/.../di/OpenMeteoModuleTest.kt` (new). TLS keystores copied into `core/net` and `provider/weather-openmeteo` test resources if their tests need them.

**Interfaces:**
- Produces: Open-Meteo's two clients built as `internetHttpClient().newBuilder()` with their present `connectTimeout`/`readTimeout`. A page that breaks the new rule reads as `BinsReadFailed`, so the household sees "Can’t read the council’s site".

- [ ] **Step 1:** Write the companion's N2 tests.
- [ ] **Step 2:** Run `./gradlew :core:net:testDebugUnitTest :provider:calendar-midsussex:testDebugUnitTest :provider:weather-openmeteo:testDebugUnitTest` and `./gradlew :app:testDebugUnitTest --tests '*CleartextTest*'`. Expect FAIL.
- [ ] **Step 3:** Implement the companion's four parts.
- [ ] **Step 4:** Run the same, plus `./gradlew :core:google:testDebugUnitTest :provider:shopping-google-tasks:testDebugUnitTest` (Google uses `internetHttpClient()`). Expect PASS.
- [ ] **Step 5:** Commit: `Read the council's site over https only, and keep internet redirects on https`.

### Task 6: N6, the Home Assistant socket's limits

**Files:** `core/homeassistant/.../HomeAssistantClient.kt` (`registry()`, `follow()`, the listener's `onMessage`); `LogHygieneTest.kt` if the size line interpolates a value; test `core/homeassistant/src/test/.../HomeAssistantSocketTest.kt`.

**Interfaces:**
- Produces: `internal const val SOCKET_QUEUE = 256`, `internal const val MAX_SOCKET_MESSAGE_CHARS = 4_000_000`. Either limit ends the follow flow with `HomeAssistantException(HaProblem.UNREACHABLE)`; the socket reopens on its usual backoff.

- [ ] **Step 1:** Write the companion's N6 tests.
- [ ] **Step 2:** Run `./gradlew :core:homeassistant:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement as the companion says.
- [ ] **Step 4:** Run the same and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 5:** Commit: `Limit how much Home Assistant's live link can queue`.

### Task 7: The PIN pad's detail line

**Files:**
- Modify: `core/access/src/main/java/uk/co/siland/culvery/core/access/AccessControl.kt`, `DefaultAccessControl.kt` (`promptUntilResolved`), `PinPromptController.kt` (`PinRequest`), `ui/PinPad.kt`, and every implementation in ruling 1.
- Test: `core/access/src/test/.../DefaultAccessControlTest.kt`, `app/src/test/.../shell/ui/ModalLayersTest.kt`, `ShellScreenshotTest.kt` (a new `pin_pad_detail` snap).

**Interfaces:**
- Produces: `suspend fun authorise(vararg anyOf: String, reason, allow, refusal, label: String? = null, detail: String? = null): Authorised?`. `PinRequest.detail: String?`. The pad draws `detail` under its label in `HhType.secondary`, tagged `pin_detail`, read by TalkBack after the label. Nothing passes a detail yet.

- [ ] **Step 1:** Write:
  - `DefaultAccessControlTest.aDetailReachesThePinRequest` (`authorise(..., detail = "abcd ef01")`: the pending `PinRequest.detail` is `"abcd ef01"`); `noDetailByDefault` (regression guard: null).
  - `ModalLayersTest.theDetailShowsUnderTheLabel` (`onNodeWithTag("pin_detail")` has the text; absent when null).
  - The `pin_pad_detail` screenshot, with label "Trust this Home Assistant" and detail `"Certificate fingerprint\n0001 0203 0405 0607 0809 0a0b 0c0d 0e0f 1011 1213 1415 1617 1819 1a1b 1c1d 1e1f"`.
- [ ] **Step 2:** Run `./gradlew :core:access:testDebugUnitTest` and `./gradlew :app:testDebugUnitTest --tests '*ModalLayersTest*'`. Expect FAIL.
- [ ] **Step 3:** Implement, and add the parameter to every fake (ruling 1).
- [ ] **Step 4:** Run `./gradlew testDebugUnitTest` (every module compiles against the new signature), then record the new baseline with `./gradlew :app:recordRoborazziDebug --tests '*ShellScreenshotTest*'` and restore any other baseline that moved. Expect PASS.
- [ ] **Step 5:** Commit: `Show a detail line under the PIN pad's label`.

### Task 8: N1, the stored link and the address

**Files:**
- Modify: `core/homeassistant/.../HomeAssistantLink.kt` (`homeAssistantUrl`, the link), `HomeAssistantSettings.kt`.
- Test: `HomeAssistantAddressTest.kt`, `HomeAssistantSettingsTest.kt`, `HaRedactTest.kt` (or the link's `toString` test).

**Interfaces:**
- Produces (exact shapes in the companion's N1 "Storing the pin"):
  - `data class HomeAssistantLink(val baseUrl: HttpUrl, val token: String, val pin: String?)`; `toString` names none of the three.
  - `HomeAssistantSettings`: the pin saved and read with the address and token; `val storedPin: Flow<String?>`; `suspend fun forget()`.
  - `homeAssistantUrl`: no port typed means 8123 for http and 443 for https.

- [ ] **Step 1:** Write the companion's N1 tests for `HomeAssistantAddressTest`, `HomeAssistantSettingsTest` and the text form, and:
  - `aLinkSavedBeforePlan20StillLinks` (regression guard: a record with only `address` and `token`, as Plan 19 wrote it, reads as a link with `pin == null`).
  - `theSchemeIsReadWhateverItsCase` (`HTTPS://h`, `Https://H/` and `https://h` all give `https://h/`; `HTTP://h` gives `http://h:8123/`). Task 10's https-only rule reads the scheme from this parsed URL, never from the typed text.
- [ ] **Step 2:** Run `./gradlew :core:homeassistant:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Update callers of `HomeAssistantLink(` (`FakeHomeAssistantApi`, `TestHomeAssistant` and the providers' tests) with `pin = null`.
- [ ] **Step 4:** Run the same and `./gradlew :provider:plugs-homeassistant:testDebugUnitTest :provider:hoover-homeassistant:testDebugUnitTest :provider:heating-homeassistant:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Keep Home Assistant's certificate with its link, and use port 443 for https`.

### Task 9: N1, trust and the client

**Files:**
- Create: `core/homeassistant/src/main/java/uk/co/siland/culvery/core/homeassistant/HomeAssistantTrust.kt`, `core/homeassistant/src/test/resources/tls/` (Hue's two keystores), `core/homeassistant/src/test/.../TestCerts.kt`.
- Modify: `HomeAssistantClient.kt` (`HomeAssistantApi` gains `certificate`; the clients per link), `HaProblem` (`CERTIFICATE_CHANGED`), `FakeHomeAssistantApi.kt`.
- Test: `HomeAssistantTrustTest.kt` (new), `HomeAssistantClientTest.kt`, `HomeAssistantSocketTest.kt`.

**Interfaces:**
- Produces:
  - In `HomeAssistantTrust.kt`, built as `HueTrust.kt` is: the trust managers and verifier the companion names, and `fun fingerprint(pin: String): String`: lowercase hex of the SHA-256 the pin encodes, in 16 groups of four, joined by single spaces.
  - `suspend fun HomeAssistantApi.certificate(base: HttpUrl): String` (a member of the interface; the fake returns a settable value).
  - `enum class HaProblem { …, CERTIFICATE_CHANGED }`.

- [ ] **Step 1:** Write the companion's N1 tests for `HomeAssistantTrustTest`, `HomeAssistantClientTest` and the socket, and `theFingerprintIsLowercaseHexInGroupsOfFour`: `fingerprint("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")` is `"0001 0203 0405 0607 0809 0a0b 0c0d 0e0f 1011 1213 1415 1617 1819 1a1b 1c1d 1e1f"`.
- [ ] **Step 2:** Run `./gradlew :core:homeassistant:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement as the companion says.
- [ ] **Step 4:** Run the same and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 5:** Commit: `Use Home Assistant's remembered certificate for every call`.

### Task 10: N1, Check and Forget

**Files:** `core/homeassistant/.../HomeAssistantLinking.kt`, `HomeAssistantWords.kt`; test `HomeAssistantLinkingTest.kt`.

**Interfaces:**
- Consumes: `authorise(..., detail)` (Task 7), the link and settings (Task 8), `certificate`, `fingerprint` and `CERTIFICATE_CHANGED` (Task 9).
- Produces:
  - `HomeAssistantLinking.check(address, token): CheckOutcome` in the companion's order.
  - `suspend fun HomeAssistantLinking.forget(): Boolean`: a fresh Admin PIN labelled `FORGET_HOME_ASSISTANT`, then `settings.forget()`; false when refused.
  - Words: `TRUST_HOME_ASSISTANT = "Trust this Home Assistant"`, `CERTIFICATE_FINGERPRINT = "Certificate fingerprint"` (the detail is `"$CERTIFICATE_FINGERPRINT\n${fingerprint(pin)}"`), `USE_HTTPS = "This Home Assistant is linked over https. Type its address starting https://."`, `CERTIFICATE_CHANGED_ON_PAGE = "Home Assistant’s certificate has changed. Press Check to trust the new one."`, `FORGET_HOME_ASSISTANT = "Forget this Home Assistant"`, `HOME_ASSISTANT_FORGOTTEN = "Home Assistant forgotten."`.

- [ ] **Step 1:** Write the companion's `HomeAssistantLinkingTest` list.
- [ ] **Step 2:** Run `./gradlew :core:homeassistant:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement as the companion says.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Link Home Assistant over https: Check shows the fingerprint and asks the Admin PIN`.

### Task 11: N1, the page, the rows and the docs

**Files:**
- Modify: `core/homeassistant/.../HomeAssistant.kt` (`LINK_PROBLEMS`), `HomeAssistantWords.kt` (`problemLine`), `ui/OtherIntegrationsPage.kt` (`pageProblemLine`, the status line, the linked line, Forget), `app/src/main/res/xml/network_security_config.xml` (its comment, per the companion), `README.md` (ruling 3).
- Test: `HomeAssistantTest.kt`, `ui/OtherIntegrationsTest.kt`, the page's screenshot test, the hoover and plugs providers' problem-line tests.

**Interfaces:**
- Consumes: `forget()`, the words (Task 10).
- Produces:
  - `problemLine(CERTIFICATE_CHANGED)` = `"Home Assistant’s certificate has changed. Press Check in Settings › Home Assistant to trust the new one."`; `pageProblemLine` gives `CERTIFICATE_CHANGED_ON_PAGE`.
  - Linked over https, the page shows `"Linked over https."` with `CERTIFICATE_FINGERPRINT` and the fingerprint under it; over http, `"Linked."` as now.
  - A quiet `FORGET_HOME_ASSISTANT` button (tag `ha_forget`) while linked; on success the toast `HOME_ASSISTANT_FORGOTTEN` and the page's "Not linked yet…" state. Chosen devices are kept.

- [ ] **Step 1:** Write:
  - `HomeAssistantTest.aChangedCertificateIsTheLinksProblem` (the fake API throws `CERTIFICATE_CHANGED`: `problem` is it); `forgetClosesTheSocketAndTheRowsSayNotLinked` (watching, socket open; `settings.forget()`: the follow flow is cancelled and `problem` is `NOT_LINKED` with no time passing).
  - `OtherIntegrationsTest`: `linkedOverHttpsShowsTheFingerprint`, `linkedOverHttpSaysLinked` (regression guard), `forgetShowsOnlyWhileLinked`, `forgetRefusedKeepsTheLink`, `forgetSaysForgottenAndShowsNotLinked`, `theChangedCertificateLineShows`.
  - Screenshots: linked over https with the fingerprint and Forget; the changed-certificate line.
- [ ] **Step 2:** Run `./gradlew :core:homeassistant:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Reword the config comment and add the README section.
- [ ] **Step 4:** Run the same, the three HA providers' tests, `./gradlew :capability:holiday:testDebugUnitTest :capability:plugs:testDebugUnitTest`, `./gradlew :app:testDebugUnitTest --tests '*CleartextTest*'`; record the page's baselines and restore any others. Expect PASS.
- [ ] **Step 5:** Commit: `Show the https link, a changed certificate and Forget in Settings › Home Assistant`.

### Task 12: M2, Settings signs out when it closes

**Files:** `app/src/main/java/uk/co/siland/culvery/shell/ShellViewModel.kt`; test `app/src/test/.../shell/ShellViewModelTest.kt` (the fake `AccessControl` in `Fakes.kt` records `lock()` if it doesn't yet).

- [ ] **Step 1:** Rewrite `settingsSetsAndClearsItsTimeout` and write the companion's other M2 tests.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*ShellViewModel*'`. Expect FAIL.
- [ ] **Step 3:** Implement as the companion says.
- [ ] **Step 4:** Run `./gradlew :app:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Sign out when Settings closes`.

### Task 13: N3, Allow the camera asks for a fresh Admin PIN

**Files:** `app/src/main/java/uk/co/siland/culvery/display/CameraPrompt.kt`, `MainActivity.kt` (where it builds the prompt and collects `camera.requests`); test `app/src/test/.../display/CameraPromptTest.kt`.

**Interfaces:**
- Produces: `CameraPrompt(access: CameraAccess, mayUnpin: suspend () -> Boolean, openSystemScreen, returnToPinning)`; `suspend fun request(launch: () -> Unit)`. `MainActivity` passes `{ access.authorise(CorePermissions.CONNECTIONS_MANAGE, label = ALLOW_THE_CAMERA) != null }`.

- [ ] **Step 1:** Write the companion's N3 tests.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*CameraPrompt*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same and `./gradlew :app:assembleDebug`. Expect PASS.
- [ ] **Step 5:** Commit: `Ask for a fresh Admin PIN before Allow the camera`.

### Task 14: N4, turning "Needs a PIN" off

**Files:**
- Modify: `core/access/.../AccessControl.kt` (`TURN_OFF_NEEDS_A_PIN = "Turn off Needs a PIN"`), `capability/plugs/.../PlugActions.kt`, `capability/plugs/.../ui/PlugsSettings.kt`, `capability/party/.../StoredModeActions.kt`, `ModesStore.kt`, `ui/StoredModePage.kt`.
- Test: `PlugActionsTest.kt`, `PlugsSettingsTest.kt`, `StoredModeActionsTest.kt`, `ModesStoreTest.kt`, `ui/StoredModePageTest.kt`.

**Interfaces:**
- Produces: `PlugActions.setNeedsPin(entityId, on)` asks when `on` is false; `suspend fun StoredModeActions.setNeedsPin(id: String, on: Boolean): Boolean` (false when refused); `suspend fun ModesStore.setNeedsPin(id: String, on: Boolean)` writing only that mode's PIN key; `ModesStore.update` keeps a stored "Needs a PIN" that's on. `StoredModePage`'s switch calls `setNeedsPin`.

- [ ] **Step 1:** Write the companion's N4 tests.
- [ ] **Step 2:** Run `./gradlew :capability:plugs:testDebugUnitTest :capability:party:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. The PIN is never asked inside a store write.
- [ ] **Step 4:** Run the same and `./gradlew :capability:holiday:testDebugUnitTest :core:house:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Ask for a fresh Admin PIN to turn off Needs a PIN`.

### Task 15: N5 with 11f, 11g, 11h: clean sound names, case and theme folders

**Files:** `core/sound/src/main/java/uk/co/siland/culvery/core/sound/Sounds.kt` (`readSoundsFolder`, `soundNameFromId`), `SoundWords.kt`; the Settings rows that label a tablet sound (`capability/door/.../ui/DoorSettingsPage.kt`, `capability/party/.../ui/SoundButtonsSection.kt`); test `core/sound/src/test/.../SoundsTest.kt`, the two pages' tests.

**Interfaces:**
- Produces:
  - `fun cleanSoundName(raw: String): String` (the companion's N5 rules), `const val UNNAMED_SOUND = "Unnamed sound"`, `const val SOUND_NAME_MAX = 40`. Used by `readSoundsFolder` and `soundNameFromId`.
  - Duplicates are judged on the cleaned name ignoring case, within the main folder and within each theme folder on its own (11f, 11h).
  - A theme folder is matched whatever its case (11g).
  - A theme sound's Settings line is `"On this tablet · ${theme display name}"`, e.g. `"On this tablet · Halloween"`.

- [ ] **Step 1:** Write the companion's N5 tests in `SoundsTest`, and:
  - `aThemeFolderIsFoundWhateverItsCase` (`Halloween/howl.mp3` has `theme == HALLOWEEN`)
  - `sameNamesDifferingInCaseShowOnce` (`Howl.mp3`, `howl.mp3` in the main folder: one, the first by path)
  - `aThemeSoundSharingAMainFolderNameIsKept` (`howl.mp3` and `halloween/howl.mp3`: both)
  - The pages: a Halloween tablet sound's row says `"On this tablet · Halloween"`.
- [ ] **Step 2:** Run `./gradlew :core:sound:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. Stored ids don't change.
- [ ] **Step 4:** Run the same and `./gradlew :capability:door:testDebugUnitTest :capability:party:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Tidy tablet sound names, and show theme sounds that share a name`.

### Task 16: 11c, 11d, 11e: the sounds folder's first read

**Files:** `core/sound/.../SoundLibrary.kt`; `capability/door/.../ui/DoorSettingsPage.kt`, `capability/party/.../ui/SoundButtonsSection.kt` (and `ModesWords.goneSound`'s caller); tests `SoundLibraryTest.kt`, the two pages' tests.

**Interfaces:**
- Produces: `val SoundLibrary.firstReadDone: StateFlow<Boolean>`. Until true, the pages show a chosen tablet sound by its name (`soundNameFromId`) with no note. A failed read logs `Log.w(TAG, "The sounds folder couldn't be read (${e::class.simpleName})")`, keeps the last list and sets `firstReadDone`.

- [ ] **Step 1:** Write:
  - `aLaterRefreshWinsOverAHeldFirstRead` (11c: the first read held on a gate; a `refresh()` lands; the first is released: the list is the later one). Regression guard unless it fails; if it fails, fix it here.
  - `theFirstReadSaysWhenItLands` (false, then true after it).
  - `aFailedReadKeepsTheListAndCountsAsLanded` (the root throws: list unchanged, `firstReadDone` true, one warning line naming the class).
  - Pages: before the first read, a chosen `file:` id shows its name and no `NOT_ON_THIS_TABLET`; after it, with the file gone, the note shows (regression guard).
- [ ] **Step 2:** Run `./gradlew :core:sound:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same, `./gradlew :capability:door:testDebugUnitTest :capability:party:testDebugUnitTest` and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 5:** Commit: `Wait for the sounds folder before saying a sound is gone`.

### Task 17: 11j, sounds open off the caller's thread

**Files:** `core/sound/.../SoundPlayer.kt`, `capability/door/.../di/DoorModule.kt` (the chime's `play` and `preview`) and the interface they implement, `capability/door/.../ui/DoorSettingsPage.kt` (Play chime drops its `Dispatchers.IO`), `capability/party/.../PadTaps.kt` (drops its `withContext(io)`); tests `SoundPlayerTest.kt`, `PadTapsTest.kt`, the door tests that play.

**Interfaces:**
- Produces: ruling 5. `SoundPlayer internal constructor(..., io: CoroutineDispatcher)`; `suspend fun playDoorbell(sound, volume): Boolean`; `suspend fun playPad(pad, sound, volume, capMs = PAD_CAP_MS): Boolean`. `SoundPlayer`'s lock comment becomes one line (11v).

- [ ] **Step 1:** Write `SoundPlayerTest.aFileOpensOnTheInjectedDispatcher` (a test dispatcher that records the thread name: the open runs there, never on the caller's) and `PadTapsTest.aTapDoesNotSwitchDispatcherItself` (regression guard on the new shape: `PadTaps` takes no `io`).
- [ ] **Step 2:** Run `./gradlew :core:sound:testDebugUnitTest :capability:party:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same and `./gradlew :capability:door:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Open sounds off the main thread`.

### Task 18: 11i, 11k, 11l: the player's fallbacks and stops

**Files:** `core/sound/.../SoundPlayer.kt`, `capability/party/.../PadTaps.kt`; tests `SoundPlayerTest.kt`, `PadTapsTest.kt`.

**Interfaces:**
- Produces:
  - 11i: a tablet doorbell sound whose player errors after starting plays the classic chime once, unless `ringStarted()` was called since.
  - 11k: `stopPads()` counts like a ring: a pad whose open finishes after a stop doesn't start. `PadTaps` keeps `opening: Int?`; a tap on the pad that's opening calls `stop()` instead.
  - 11l: `playPad` returns false while the doorbell's sound is opening or playing.

- [ ] **Step 1:** Write `aDoorbellSoundThatFailsMidwayFallsBackToTheChimeOnce`, `noFallbackAfterANewerRing`, `aStopWhileAPadOpensKeepsItFromStarting`, `aSecondTapOnAnOpeningPadStopsIt`, `aPadWaitsWhileTheDoorbellOpens`, `aPadWaitsWhileTheDoorbellPlays`.
- [ ] **Step 2:** Run `./gradlew :core:sound:testDebugUnitTest :capability:party:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same and `./gradlew :capability:door:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Fall back to the chime once, and let a stop tap stop a pad that's opening`.

### Task 19: 11m, 11n, 11o: the pads on the tray

**Files:** `capability/party/.../PadGate.kt`, `PadTaps.kt`, `capability/party/.../ui/SoundPadTile.kt`, `capability/taekwondo/.../ui/TaekwondoTile.kt`, `core/ui/.../HouseMode.kt` (`HouseModeThirdTile`); tests `PadGateTest.kt`, `PadTapsTest.kt`, `core/ui` tile test, `ShellScreenshotTest.kt` (a new `home_tray_pads_unread` snap).

**Interfaces:**
- Produces:
  - 11m: `PadGate.blocked` re-reads at each minute boundary of the clock (the first wait is to the next whole minute, then `MINUTE_MS`).
  - 11n: ruling 6; `PadTaps.line` is null until `gateRead`.
  - 11o: `HouseModeThirdTile` flashes `accentSoft` for `PRESS_FLASH_MS = 150L` on a tap. Mode tiles (`HouseModeTile`) don't flash.

- [ ] **Step 1:** Write `theGateIsReadOnTheMinute` (clock at 21:59:30, night from 22:00: blocked turns `NIGHT` at 22:00:00, not 22:00:30), `padsAreFadedWithNoLineUntilTheGateIsRead`, `aTapFlashesForAHundredAndFiftyMs` (the flash colour at 0 ms and gone at 150 ms, under the compose test clock), `aModeTileDoesNotFlash` (regression guard), the screenshot.
- [ ] **Step 2:** Run `./gradlew :capability:party:testDebugUnitTest :core:ui:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same, `./gradlew :capability:taekwondo:testDebugUnitTest`, and record the new baseline (restore any other). Expect PASS.
- [ ] **Step 5:** Commit: `Read the pads' rules on the minute, fade the pads until read, and flash a tray tile on a tap`.

### Task 20: 11p, a failed Play in Settings says so

**Files:** `capability/door/.../ui/DoorSettingsPage.kt` (Play chime), `capability/party/.../ui/SoundButtonsSection.kt` (the pads' Play), `core/sound/.../SoundWords.kt`; the two pages' tests.

**Interfaces:**
- Produces: `fun couldntPlay(sound: Sound): String` in `SoundWords.kt`: `"Couldn’t play ${sound.name}. Copy the file to the tablet’s sounds folder again."` for a tablet sound, `"Couldn’t play ${sound.name}. Restart Culvery and try again."` for a built-in. Shown with the page's `Toaster` when the play returns false.

- [ ] **Step 1:** Write `couldntPlayNamesTheFix` (both forms) and, on each page, `aFailedPlayShowsItsToast` (a player that returns false: the toaster got the line).
- [ ] **Step 2:** Run `./gradlew :core:sound:testDebugUnitTest :capability:door:testDebugUnitTest :capability:party:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Say when a sound in Settings can't play`.

### Task 21: 11q, 11r: the door's stored choice

**Files:** `capability/door/.../DoorSettings.kt` (`doorbellSound`); test `DoorSettingsTest.kt`.

- [ ] **Step 1:** Write `aStoredHappyBirthdayChoicePlaysTheClassicChime` (prefs doorbell `BuiltInSound.HAPPY_BIRTHDAY.id`, no birthday: `CLASSIC`) and `aBirthdayStillPlaysHappyBirthday` (regression guard: `birthday = true` with Follow the theme). 11r: confirm `goneName`/`goneFileName` are gone (`git grep -n "goneName\|goneFileName"` returns nothing); nothing else to do.
- [ ] **Step 2:** Run `./gradlew :capability:door:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Play the classic chime for a stored Happy Birthday doorbell`.

### Task 22: 11a, 11b: the sound generators

**Files:** Create `tools/sound/wav_out.py`. Modify the four generators in `tools/sound/`, `core/sound/src/main/res/raw/happy_birthday.wav`.

**Interfaces:**
- Produces in `wav_out.py`: `rms(frames, seconds, rate) -> float`; `write_peak(path, frames, rate, headroom)` (the chime's way); `write_loud(path, frames, rate, target_rms, rms_seconds, headroom, limit=False)` (the others'). With `limit=True`, a gentle limiter on the note onsets, applied only where the peak cap would otherwise win, so `target_rms` is met with the peak inside `headroom`.

- [ ] **Step 1:** Move the code; run all four generators. `git status --short core/sound/src/main/res/raw` shows nothing. If it shows a file, the move changed its output: fix before going on.
- [ ] **Step 2:** Turn on `limit=True` in `happy_birthday.py` only; run it.
- [ ] **Step 3:** Check with a one-off Python line using `wav_out.rms`: Happy Birthday's RMS over `RMS_SECONDS` is within 2 % of `TARGET_RMS`, its peak at most `HEADROOM * 32767`. `git status` shows only `happy_birthday.wav` changed. Write the two numbers under this step.
- [ ] **Step 4:** Run `./gradlew :core:sound:testDebugUnitTest :capability:door:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Share the sound generators' output code, and bring Happy Birthday up to the chime's loudness`.

### Task 23: Item 5, a shorter pause after a tick

**Files:** `core/plugin/.../Overlays.kt` (`ToastAction`), `app/src/main/java/uk/co/siland/culvery/shell/ui/OverlayLayers.kt` (`ToastLayer`), `capability/shopping/.../ShoppingActions.kt`, `ShoppingSync.kt`, `ShoppingView.kt`; tests `OverlayLayersTest.kt`, `ShoppingActionsTest.kt`, `ShoppingSyncTest.kt`.

**Interfaces:**
- Produces: `data class ToastAction(val label: String, val onClick: () -> Unit, val showMillis: Long = TOAST_WITH_ACTION_MS)`; `ToastLayer` waits `showMillis` for a toast with an action. `const val TICK_UNDO_MS = 6_000L` in `ShoppingActions.kt`, used for the tick's toast, `latestTick`, `undoGoneAt` and the KDocs the spec names.

- [ ] **Step 1:** Write:
  - `OverlayLayersTest.aToastActionsOwnTimeIsKept` (`showMillis = 6_000`: shown at 5 999 ms, gone at 6 000 ms) and `anotherActionToastStillLastsEightSeconds` (regression guard).
  - `ShoppingActionsTest`: the Undo window's test moves to `TICK_UNDO_MS` (Undo works at 5 999 ms, not at 6 000 ms); `theTicksToastShowsForSixSeconds` (its `ToastAction.showMillis` is 6 000).
  - `ShoppingSyncTest`: the two refused-item cases end at `TICK_UNDO_MS`.
- [ ] **Step 2:** Run `./gradlew :capability:shopping:testDebugUnitTest` and `./gradlew :app:testDebugUnitTest --tests '*OverlayLayersTest*'`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same and `./gradlew :core:plugin:testDebugUnitTest`. Expect PASS.
- [ ] **Step 5:** Commit: `Keep a ticked shopping item for 6 seconds`.

### Task 24: Item 16, Shuffle everything's padding

**Files:** `capability/music/.../ui/MusicCard.kt` (drop `compact = true` on `ShuffleButton`); tests `ui/MusicCardTest.kt`, the card's screenshot test.

- [ ] **Step 1:** Write `shuffleEverythingKeepsItsOwnWidth` (nothing playing: `music_card_shuffle`'s width is less than the card's). `theCardFitsTheSideBySideRow` stays as it is.
- [ ] **Step 2:** Run `./gradlew :capability:music:testDebugUnitTest`. Expect PASS for the width test (regression guard) and the others.
- [ ] **Step 3:** Implement; re-record the card's baselines (restore any other).
- [ ] **Step 4:** Run `./gradlew :capability:music:testDebugUnitTest verifyRoborazziDebug`. Expect PASS.
- [ ] **Step 5:** Commit: `Give Shuffle everything its full padding on the Music card`.

### Task 25: Calendar M2, an Undo that races a create

**Files:** `capability/calendar/.../CalendarSync.kt` (`deliver`, `undoneCreate`), `CalendarEditor.kt` (`undo`), `UndoneKeys.kt`, `CalendarWriteLock.kt`; tests `CalendarSyncTest.kt`, `CalendarEditorTest.kt`.

**Interfaces:**
- Produces:
  - `CalendarWriteLock.held: Boolean` (`internal`, `mutex.isLocked`), for tests.
  - `UndoneKeys`: `fun sending(key: String)`, `fun isSending(key: String): Boolean`, and `sent(key)` clearing it; `add` stays.
  - `deliver`'s CREATE in the spec's four steps (§4.3). `undo` drops a create that isn't on its way and lets its key go at once; one on its way leaves the key to the drain.

- [ ] **Step 1:** Write in `CalendarSyncTest`, with a fake writer whose `create` waits on a gate:
  - `anUndoWhileTheCreateIsSentDeletesItWhenItLands` (Undo during step 2: the event never reaches the store; the stray is deleted the same pass)
  - `anUndoAfterTheCreateLandedDeletesTheEvent` (the other order: the normal delete path; the week loses it)
  - `anUndoOfAQueuedCreateLetsTheKeyGo` (`undone.pending` empty, no create sent)
  - `noWriterCallRunsUnderTheLock` (each fake writer call asserts `!writeLock.held`)
  - `aFailedStrayDeleteIsTriedNextPass` (regression guard)
  - `anUndoneCreateTheServiceRefusedLeavesNoStray` (Review Focus 5: the create is `Rejected` after Undo: `strayCreates` empty, no delete sent)
- [ ] **Step 2:** Run `./gradlew :capability:calendar:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement. No locked step calls `drop()`.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Settle an Undo that lands at the same moment as its event`.

### Task 26: Calendar M2, undone creates across a restart

**Files:**
- Modify: `capability/calendar/.../db/CalendarDatabase.kt` (ruling 8), `db/Migrations.kt`, `UndoneKeys.kt` (takes `UndoneCreateDao`), `CalendarSync.kt` (loads before the first pass; leaves strays out), the calendar `di` module (provides the DAO), `capability/calendar/schemas/.../10.json` (exported).
- Test: `CalendarMigrationTest.kt`, `UndoneKeysTest.kt` (new), `CalendarSyncTest.kt`.

**Interfaces:**
- Produces: `UndoneKeys @Inject constructor(dao: UndoneCreateDao)`; `suspend fun load()` (once; `CalendarSync.syncAll` calls it first); `add`, `stray`, `letGo`, `done` become `suspend` and write through. After `load()` every stored key is a stray. `CalendarSync` leaves out an event whose id is a stray's key, and any instance whose recurring event id is that key or whose id starts with `"${key}_"`.

- [ ] **Step 1:** Write `migrate9To10` (the table exists, empty, with the four columns plus `stray`); `aStoredStraySurvivesARestartAndIsDeletedOnTheNextPass` (a new `UndoneKeys` over the same database: the first drain sends the delete; a 404 counts as done); `aStrayIsNotShownBySync`; `aStraySeriesInstancesAreNotShown` (`key_20270101T090000Z` and one with `recurringEventId == key`).
- [ ] **Step 2:** Run `./gradlew :capability:calendar:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same and `./gradlew :app:testDebugUnitTest --tests '*Wiring*'`. Expect PASS.
- [ ] **Step 5:** Commit: `Remember undone events across a restart`.

### Task 27: Calendar M3, a counted series' last date

**Files:** `capability/calendar/.../Recurrence.kt`, `Repeats.kt`; tests `RepeatsTest.kt`, `RecurrenceTest.kt`.

**Interfaces:**
- Produces: `internal fun lastOccurrence(choice: RepeatChoice, first: LocalDate, count: Int, nth: Int): LocalDate?`: worked out directly for daily, weekly, fortnightly and monthly-on-a-weekday; counted for monthly-on-a-date and yearly up to `COUNTED_LAST_CAP`, null above it. `repeatsLabel` uses it and writes counts of 1,000 or more with a comma (`"%,d"`, `Locale.UK`).

- [ ] **Step 1:** Write:
  - `theDirectLastDateMatchesTheListedOne`: for daily, weekly, fortnightly and monthly on the last Thursday, COUNT 10 000, `lastOccurrence` equals `occurrences(c, first, 10_000, nth).last()`.
  - `underTheCapTheLastDateIsTheListedOne`: monthly on the 31st and yearly on 29 February, COUNT 1 000.
  - `aboveTheCapTheLabelIsTheCountAlone`: monthly on the 31st, COUNT 10 000: `"Every month, on the 31st · 10,000 times"`; yearly on 29 February ends `" · 10,000 times"`.
  - `aTwelveTimeSeriesStillSaysItsLastDate` (regression guard).
  - `aHugeCountReturnsAtOnce` (`@Test(timeout = 1_000)`: COUNT 2 000 000 000 for each of the six rules; no exception).
- [ ] **Step 2:** Run `./gradlew :capability:calendar:testDebugUnitTest`. Expect FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the same. Expect PASS.
- [ ] **Step 5:** Commit: `Work out a counted series' last date without listing every date`.

### Task 28: 11s, 11t, 11u, 11v: the tests

**Files:** `capability/door/src/test/.../TestDoor.kt`, `DoorQuietnessTest.kt`, the door module's other tests, `capability/party/src/test/.../ui/StoredModePageTest.kt`, `app/src/test/.../shell/ui/ShellScreenshotTest.kt` (`trayHome`, the `home_tray_full_*` snaps), `core/sound/src/test/.../BuiltInSoundTest.kt`, `capability/door/src/test/.../ChimeTest.kt`, Plan 18's test files (ruling 7).

- [ ] **Step 1 (11t):** Give `testSounds()` and `DoorQuietnessTest`'s `first { }` waits a 5 s timeout (`withTimeout(5_000)`), each failing with a message naming what it waited for.
- [ ] **Step 2 (11u):** `trayHome` fails with `require` when asked for more modes than fit (2); the `home_tray_full_*` snaps pass `modes = 2`. Write `theTrayFixtureRefusesMoreModesThanFit`. The baselines shouldn't move; if they do, look before accepting.
- [ ] **Step 3 (11s):** Run `./gradlew test` three times. List each test that failed in any run, under this step. For each, find the cause and fix it: a real-time wait becomes a test-clock wait or a bounded `waitUntil`. No retries, no `@Ignore`. Then three more runs with no failure.
- [ ] **Step 4 (11v):** `Context` imported in `BuiltInSoundTest` and `ChimeTest`; imports ordered in Plan 18's files.
- [ ] **Step 5:** Run `./gradlew testDebugUnitTest verifyRoborazziDebug`. Expect PASS.
- [ ] **Step 6:** Commit: `Steady the door and mode tests, and tidy Plan 18's tests`.

### Task 29: 11w, 11x, 11y: the private sound script

**Files:** `culvery-notes/sounds/prepare.sh` only. Nothing in the repo; no commit (the notes folder isn't a git repository).

- [ ] **Step 1:** Make the companion's 11w, 11x and 11y changes.
- [ ] **Step 2:** Run the companion's checks for them (its "Plan 20 tasks", Task 29) and write the results there.

### Task 30: Simplify the branch

- [ ] **Step 1:** Run the `simplify` skill over the whole branch (`git diff plan-19-power...plan-20-security-and-fixes`).
- [ ] **Step 2:** Run `./gradlew testDebugUnitTest verifyRoborazziDebug` and `./gradlew -p build-logic :convention:test`. Expect PASS.
- [ ] **Step 3:** Commit, if anything changed: `Simplify Plan 20`.

### Task 31: Emulator walk-through

Walk-through rule: collect faults and put them to the user before fixing any. Claude enters the debug sample PINs.

- [ ] **Step 1:** Open by reading the user the spec's §6 "Left out on purpose", one line each: October's other findings and I1, N11, N13; https on Home Assistant's side inside the build; a mute button on the Music card; TalkBack's longer toast time for the shopping tick.
- [ ] **Step 2:** `./gradlew :app:assembleDebug`, then `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk` (if it refuses, stop and ask; never uninstall).
- [ ] **Step 3:** Tick each of spec §5.2, one line of what was seen:
  1. Settings: open with the Admin PIN, close, reopen: it asks again. Door history from the door card over Settings: Settings goes and so does the session.
  2. Allow the camera (camera not yet allowed): the PIN pad says "Allow the camera"; cancel: nothing opens.
  3. Modes and Plugs: "Needs a PIN" off asks, on doesn't. Rename a mode that needs a PIN: it still does.
  4. Home Assistant with the emulator's existing link: Check links; the hoover row and Plugs tab work. Forget: the PIN pad, "Home Assistant forgotten.", not linked; link it again.
  5. Shopping: tick Milk; the row and Undo go together after about 6 s; Undo within it brings Milk back.
  6. Home with nothing playing: Play and "Shuffle everything", no pill; play something: the pill is back.
  7. Tray: a pad tap flashes; a second tap on an opening pad stops it; pads faded with no line just after start; Settings › Door with a chosen tablet sound shows no "Not on this tablet any more" while starting.
  8. Sounds: push a file whose name has formatting characters and one with a 60-character name to the emulator's sounds folder: Settings shows them clean and cut. `Halloween/` with a main-folder name: both show, the theme one labelled. A broken file's Play shows its toast.
  9. Happy Birthday, the chime and the music box by ear, one after another: as loud as the chime, no harshness.
  10. Calendar: add an event and Undo at once, several times: the week never keeps it. A phone's long counted series shows its Repeats line.
- [ ] **Step 4:** Put any faults to the user. Fix only what they choose, each as its own commit.

### Task 32: On the tablet

- [ ] **Step 1:** `./gradlew :app:assembleRelease` (signed as before), then `adb -s <tablet serial> install -r app/build/outputs/apk/release/app-release.apk`. Never uninstall.
- [ ] **Step 2:** With the user, tick each, one line of what was seen:
  1. The existing Home Assistant link works on the day of the install: the hoover row, the Plugs tab, Holiday's steps.
  2. Everything in Task 31 that needs the real wall: the PIN pads, the tray flash, the Music card.
  3. Allow the camera: after Android's dialog, Allow and then Deny, the wall pins again.
  4. Choose home app: back from Android's chooser, the next Settings action asks the PIN.
  5. The companion's tablet checks that don't need https, and the user's hand check for I1 (its "Plan 20 tasks", Task 32).
- [ ] **Step 3:** Put any faults to the user before fixing any.

### Task 33: Home Assistant over https, with the user

The household's step, after Task 32. Claude walks the user through it; nothing here is done without them.

- [ ] **Step 1:** Follow the companion's N1 "The household's side" steps with the user, checking each as it says. Stop and ask at any step that doesn't hold.
- [ ] **Step 2:** On the tablet: Settings › Home Assistant, the https address, token box empty, Check. Compare the fingerprint on the PIN pad with the companion's command on the PC before the Admin PIN. Then the hoover row, the Plugs tab and Holiday's link work, and the old http address is refused.
- [ ] **Step 3:** Forget and link again once, over https.
- [ ] **Step 4:** The log while linking and forgetting shows no token, fingerprint or address (the companion's log check).
- [ ] **Step 5:** Write the results in the companion's "Plan 20 tasks", Task 33. Nothing is committed.
