# Culvery — Plan 4e: the device beta on the SM-T510 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Task 1 is a subagent task; Tasks 2–10 are run by the controller with the user at the tablet, following the walk-through (a fault fix may be handed to a subagent through the walk-through's fault loop).

**Goal:** The signed release runs on the son's Samsung Galaxy Tab A 10.1 (SM-T510, Android 11, One UI) in screen pinning, every queued device check is run and recorded, the faults they find are fixed, Culvery's data is kept out of Android's backup and device transfer, and a week of family use ends with a day-1 and day-7 comparison.

**Architecture:** One small code change (a `data_extraction_rules.xml` with nine excluded domains, the manifest attribute, a JVM test that reads both files, the privacy page's sentence) lands first, so every install carries it. The walk-through `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (committed with this plan) holds the ground rules, the adb commands, the save/restore and fault loop, and a transcript per session; Tasks 2–9 run it in seven short sessions and the family week. The close-out updates the follow-ups, then simplify, a final review and a local merge.

**Tech Stack:** Kotlin 2.2.20, JUnit4 + Truth, `javax.xml.parsers` (JDK 17) for the manifest test; Robolectric 4.16 and Roborazzi 1.46.1 for any fault fix; adb (platform-tools) from Git Bash on Windows; `tools/measure-release.sh`; the SDK's `retrace.bat`.

**Spec:** `docs/superpowers/specs/2026-10-12-culvery-4e-device-beta-design.md` (approved 2026-10-12; amended at plan review 2026-10-12 in §3, §4.1, §4.2, §4.5, §4.6, §5). **Walk-through:** `docs/superpowers/plans/2026-10-12-4e-walkthrough.md`.
**Previous plan (format, lessons):** `docs/superpowers/plans/2026-10-09-culvery-04d2-talkback-timeouts-words.md` and `docs/superpowers/plans/2026-10-09-4d2-walkthrough.md` (A.5: what worked for TalkBack). **Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (Task 10 updates it). **Release guide:** `docs/setup/release.md`.

**Plan series:** 1 · 2a · 2b-1 · 2b-2 · 3a · 4a · 4b · 4c · 4d-1 · 4d-2 (done) · **4e the device beta (this plan)** · the end-of-v1 design walk-through.

**Builds on:** `main` at 2cf83f3 (the spec), plus the commit of this plan, the walk-through and the spec's amendments. Work on a branch `4e-device-beta`; never push. File and line references were read at 2cf83f3.

| # | Task | Walk-through | Review |
|---|---|---|---|
| 1 | No backup or transfer: the rules (nine domains), the manifest attribute, `NoBackupTest`, the privacy sentence (§5) | — | sonnet |
| 2 | Session 1: install and set up | §1 | controller + user |
| 3 | Session 2a: font scale, dark theme, splash, zone, bars | §2a | controller + user |
| 4 | Session 2b: keyboard, status-bar touches, weather, PIN chooser | §2b | controller + user |
| 5 | Session 3: lock and restart; the `HOME_APP_HOW_TO` fix if One UI's menu differs | §3 | controller + user |
| 6 | Session 4: Google, a write, the forecast offline | §4 | controller + user |
| 7 | Session 5: TalkBack | §5 | controller + user |
| 8 | Session 6: performance, day 1; USB debugging off for the week | §6 | controller + user |
| 9 | The family week: the day 3–4 read and day 7 | §7 | controller + user |
| 10 | Close-out: names, logs, follow-ups, device owner as a post-v1 plan, simplify, final review, local merge | §8 | controller; opus final review |

**Task order and why:** the code first, so the first install carries it and Session 1 checks it; then the spec's sessions in order (Session 1 sets up the household every later session uses; Session 6 is day 1 of the family week, so it runs last); then the week and the close-out. Each session task has three steps: start, run its section to the end with that session's notes, commit the transcript. The fault loop, the save and restore rules, the pin check and the names check are written once, in the walk-through's Rules.

## Rulings against the code

Where the spec is silent, or doesn't fit the code or the device at 2cf83f3:

1. **Connect in the wizard is not pinned.** `shouldPin(setupComplete, kioskExited)` (`app/src/main/java/uk/co/siland/culvery/SetupWiring.kt:35`) pins only once setup is complete. Session 1 records the wizard's Connect unpinned and checks Culvery pins after Done; the chooser opened from a pinned Culvery is Session 4's Reconnect.
2. **Threads aren't printed by `tools/measure-release.sh`**; the walk-through's running snapshot adds `ps -T`, taken on the running process before any restart, so day 7 sees a week-old process if it lived that long.
3. **Logs over the week.** Logcat holds hours, `measure-release.sh` clears it, a restart loses the crash buffer, and Android's crash and ANR records (`dumpsys dropbox`) keep about three days. So nobody clears logs from Session 6 to day 7; a short read on day 3 or 4 takes the crash buffer and the dropbox records; day 7 reads them again before the snapshot and the script.
4. **The cold-start zone check (4b) needs the device zone to differ from the household's.** If both are UK time it is recorded "not reachable on this household"; the tablet's zone is not changed.
5. **TalkBack on One UI may be Samsung's build** (`com.samsung.android.accessibility.talkback`). Session 5 finds the installed package and service first and records whether its Developer settings and `Speaking fragment text=` lines match 4d-2's.
6. **Samsung's pinning settings are recorded, not changed.** Session 1 reads `lock_to_app_enabled` and `lock_to_app_exit_locked` (system settings) and whether the tablet has a screen lock; if Culvery never reaches `PINNED`, the controller stops and the user and the son decide.
7. **The privacy sentence** is the plan review's wording, which has no apostrophe, so it fits the page's existing straight-apostrophe style.
8. **Nine domains** (plan review S9): `root`, `file`, `database`, `sharedpref`, `external`, `device_root`, `device_file`, `device_database`, `device_sharedpref`, each with `path="."`, in both sections. The test pins exactly the nine.
9. **`adb reboot` only after the user's yes**, and the user unlocks the tablet afterwards.
10. **The serial lives in a file** (`$TMP/culvery-4e-serial`, chosen by model `SM_T510`), and device paths only appear inside one quoted `shell`/`exec-out` string (Git Bash rewrites arguments that start with `/`).
11. **E4 doesn't touch Android's dark mode.** Culvery's theme follows sun times (triage X7); Session 2a reaches Culvery's dark theme with its status-bar toggle and compares it with the dark baselines. The navigation bar (E2) is only recorded.
12. **Every changed setting is saved once** (one file per setting, `$TMP/culvery-4e-saved-<name>`, never overwritten), restored from it, read back and compared; the file is deleted only when they match. An unusual saved value or a second mismatch is a stop and ask.
13. **TalkBack is off whenever a real PIN is typed** (spec §3 as amended), and is added to the enabled accessibility services rather than written over them; its log level is changed only while TalkBack is off.
14. **USB debugging is off during the family week** (spec §3 as amended): off at the end of Session 6, on for the day 3–4 read and day 7; at the close-out the user is offered Revoke USB debugging authorisations and Developer options off.
15. **A slow chooser closing Settings** (Session 4) is recorded and brought to the user to rule on; no fix is drafted before the ruling.
16. **The subset font's saving is carried from the emulator** (Appendix B: Dalvik 17.2 → 2.8 MB), not re-measured on the tablet (spec §4.6 as amended).

Nothing in this plan needs a deprecated API. If a fault fix would need one, stop and ask.

## Global Constraints

**From the spec, verbatim — §2 Decisions 2–7:**

2. **Lock mode: screen pinning.** The tablet is the user's son's and can't be factory reset yet. It is already signed in to the family Google account that Culvery will use.
3. **Device owner** (the 4d ruling's wall setup) waits until everything else is done and tested, as its own small plan, probably after v1. 4e doesn't touch it.
4. **Shared use during the week:** the son keeps using the tablet; he leaves Culvery through Settings › Tablet lock › Use other apps (an Admin's PIN), and Culvery pins again when it next comes to the front.
5. **Home app:** Samsung's launcher stays the home app for the test phase. Culvery is made the home app only for the restart and power-cut checks (§4.3), then switched back.
6. **Planned code:** only the backup and transfer exclusion (§5). The clock set back during sleep, the wizard PIN pad hardening, the soak script and the code backlog stay in the follow-ups.
7. **Connection:** a USB cable to this PC, with Developer options and USB debugging on.

**Spec §3, the ground rules:** verbatim (as amended at plan review) in the walk-through's "Rules › The ground rules". Every task's requirements include them.

**From the house rules:**

- Package root `uk.co.siland.culvery`. `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17. No new dependency; never change a version.
- **Deprecated APIs:** use none without asking. A deprecation warning on a line you wrote: **stop and ask**.
- **Test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug` (Git Bash) or `.\gradlew.bat testDebugUnitTest verifyRoborazziDebug` (PowerShell). Never plain `test`, `check` or `build`. Every commit with code in it follows a green gate.
- **Tests that can fail:** every new test fails before its change, and its step says why ("Fails today: …"); a "Fails if" line names the mutation it catches.
- **Screenshots** (a fault fix that changes a baseline): `./gradlew <module>:compareRoborazziDebug --tests "<class pattern>"`, open each `*_compare.png` under `<module>/build/outputs/roborazzi/`, then `./gradlew <module>:recordRoborazziDebug --tests "<class pattern>"`. Write the expected changed names to `$TMP/expected-baselines.txt`, then:
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$TMP/changed.txt"
echo "changed but not listed:"; comm -23 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
echo "listed but unchanged:"; comm -13 "$TMP/changed.txt" <(sort -u "$TMP/expected-baselines.txt")
```
Both lists must be empty; anything in either is a stop and report.
- **UI copy** is plain, names what the user does, and uses curly quotes and apostrophes in code (`CopyScanTest` enforces it).
- **Comments say what the code can't**, one line by default. No comment names this plan's task numbers.
- **Write every file as UTF-8.**
- **Commit messages contain only the message** — no `Co-Authored-By`, `Signed-off-by` or any attribution trailer. Commit on `4e-device-beta`; never push. **Never** `git checkout -- .` or `git restore .`; to undo your own change, restore only the files you changed, after `git diff --name-only`.

## Review Focus

The five failure modes most likely to bite this household that no test can reach, each wired to its guard:

1. **A setting left changed on the son's tablet after a check fails part-way** (font scale 1.5, TalkBack on at Verbose, Wi-Fi off, Culvery left as home). Expected: each session ends with the son's settings as they were. Guard: ruling 12; the walk-through's "Saving a setting" (once only) and "Restoring a setting" (read back, compare, delete only on a match), run in each session's Restore section (§2a.6, §3.8 with its `set-home-activity` and by-hand fallbacks, §4.2, §5.5); a session isn't over while a `culvery-4e-saved-*` file it made is left (each session task's Step 2); the close-out's first checklist items.
2. **Household names leaking into a committed transcript.** Expected: only [person], [event], [calendar], [town], [account]. Guard: the user's full names list in Session 1 (§1.2); the names check with the email-shape grep at every session end (each session task's Step 2, before its Step 3 commit); the whole file and its branch history in Task 10 Step 1.
3. **A real PIN in a TalkBack log.** Expected: no key's speech is ever captured. Guard: ruling 13 (TalkBack off and the capture stopped for every real PIN, §5.2's off/on block; `logcat -c` before the capture restarts) and §5.5's digit check before the log is kept (any hit: deleted unread). Task 7 Step 2.
4. **An install that would wipe the household** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, a reflex `adb uninstall`, `install -d`). Expected: the household survives every install. Guard: the walk-through's Install rule, used by §1.3 and every fault-loop reinstall: stop and ask.
5. **Use other apps leaving Culvery unpinned with the son's apps reachable** at a session's end (after the TalkBack log-level trip, the home-app checks, a `force-stop`, the measure script). Expected: Culvery pinned in front, nobody signed in, Settings closed. Guard: the walk-through's pin check (all three conditions) at every session end, before the cable comes out (each session task's Step 2); §3.2 checks Use other apps itself.

The spec's own risks: the household deleted (Review Focus 4); test events left in Google (§4.1, §4.6, §4.7 delete what they add, with the user's yes; §4.8 confirms); a shared calendar's permission changed (§4.5 records the level and the person before, and reads both back after re-sharing); a slow chooser closing Settings (ruling 15); TalkBack wearing the user out (§5.4 stops after Home if the user is swiping; the lockout only under 20 minutes; two items carried); the tablet left open to adb during the week (ruling 14).

---

## File Structure

```
app/src/main/res/xml/data_extraction_rules.xml        (create, Task 1)
app/src/main/AndroidManifest.xml                       (modify, Task 1: android:dataExtractionRules)
app/src/test/java/uk/co/siland/culvery/NoBackupTest.kt (create, Task 1)
docs/privacy.html                                      (modify, Task 1: line 51)
docs/superpowers/plans/2026-10-12-4e-walkthrough.md    (committed with this plan; transcripts filled in Tasks 2–9; checklist Task 10)
core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupCopy.kt:120          (only if Session 3 finds another menu name, Task 5)
core/setup/src/test/java/uk/co/siland/culvery/core/setup/KioskPageTest.kt:54       (same condition)
core/setup/src/test/screenshots/settings_kiosk_dark.png, settings_kiosk_light.png  (same condition)
docs/superpowers/plans/2026-09-23-plan1-followups.md   (modify, Task 10)
(any file a fault fix needs, Tasks 2–9, through the walk-through's fault loop)
```

---

### Task 1: No backup or transfer (spec §5; rulings 7, 8)

**Review:** sonnet.

**Files:**
- Create: `app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `app/src/main/AndroidManifest.xml:7` (after `android:allowBackup="false"`)
- Create: `app/src/test/java/uk/co/siland/culvery/NoBackupTest.kt`
- Modify: `docs/privacy.html:51`

**Interfaces:**
- Consumes: nothing.
- Produces: the resource `@xml/data_extraction_rules`; Session 1 checks the Android 11 half on the tablet (§1.3's `flags=[` line, §1.6's `bmgr`).

`app/src/test/java/uk/co/siland/culvery/AppSourceTest.kt` is the pattern: a plain JUnit test (no Robolectric runner) reading source files relative to the module directory, where unit tests run. `NoBackupTest` does the same with the JDK's XML parser, so it checks the files a release is built from.

- [ ] **Step 1: Branch**

```bash
git switch -c 4e-device-beta
git status --short
```
Expected: `Switched to a new branch '4e-device-beta'`; no output from `git status` (or only the user's own files: leave them alone).

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/uk/co/siland/culvery/NoBackupTest.kt`:
```kotlin
package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** 4e design §5: the household's data is never copied off the tablet by Android's backup or device transfer. */
class NoBackupTest {
    // Unit tests run in the module's directory, as AppSourceTest relies on.
    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(path)).documentElement

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()

    private val application: Element
        get() = root("src/main/AndroidManifest.xml").children().single { it.tagName == "application" }

    @Test
    fun theManifestTurnsBackupOffAndNamesTheRules() {
        // allowBackup is what Android 11 and earlier honour for both backup and transfer; the rules cover Android 12+.
        assertThat(application.getAttributeNS(ANDROID, "allowBackup")).isEqualTo("false")
        assertThat(application.getAttributeNS(ANDROID, "dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
    }

    @Test
    fun theRulesExcludeEveryDomainFromCloudBackupAndDeviceTransfer() {
        val rules = root("src/main/res/xml/data_extraction_rules.xml")
        assertThat(rules.tagName).isEqualTo("data-extraction-rules")
        assertThat(rules.children().map { it.tagName }).containsExactly("cloud-backup", "device-transfer")
        for (section in rules.children()) {
            val entries = section.children()
            assertThat(entries.map { it.tagName }.toSet()).containsExactly("exclude")
            assertThat(entries.map { it.getAttribute("path") }.toSet()).containsExactly(".")
            assertThat(entries.map { it.getAttribute("domain") }).containsExactlyElementsIn(DOMAINS)
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val DOMAINS = listOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
```
Fails today: the manifest has no `android:dataExtractionRules` (the first test gets `""`), and the rules file doesn't exist (the second throws `FileNotFoundException`).
Fails if: either manifest attribute is removed or changed; a section, an `<exclude>`, a domain or a whole-domain path (`.`) is lost; an `<include>` is added; a domain is listed twice in place of another.

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "uk.co.siland.culvery.NoBackupTest"`
Expected: FAIL, 2 tests: `theManifestTurnsBackupOffAndNamesTheRules` (expected `@xml/data_extraction_rules`, but was `""`) and `theRulesExcludeEveryDomainFromCloudBackupAndDeviceTransfer` (`java.io.FileNotFoundException`).

- [ ] **Step 4: Add the rules and the attribute**

Create `app/src/main/res/xml/data_extraction_rules.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- 4e design §5: Android 12+ ignores allowBackup for device transfer, so every domain is excluded here too. -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
        <exclude domain="device_root" path="." />
        <exclude domain="device_file" path="." />
        <exclude domain="device_database" path="." />
        <exclude domain="device_sharedpref" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
        <exclude domain="device_root" path="." />
        <exclude domain="device_file" path="." />
        <exclude domain="device_database" path="." />
        <exclude domain="device_sharedpref" path="." />
    </device-transfer>
</data-extraction-rules>
```
In `app/src/main/AndroidManifest.xml`, add after `android:allowBackup="false"` (line 7), keeping it:
```xml
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
```

- [ ] **Step 5: Run the test to see it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "uk.co.siland.culvery.NoBackupTest"`
Expected: PASS, 2 tests.

- [ ] **Step 6: The privacy page**

In `docs/privacy.html` line 51, the line becomes:
```html
<p>None of this leaves the tablet except as described below. Culvery tells Android not to include its data in app backup or device transfer, so Android does not copy it to a cloud backup or a new tablet.</p>
```
Then: `grep -n "device transfer\|Some tablets" docs/privacy.html`. Expected: line 51 only, with the new sentence.

- [ ] **Step 7: The gate**

Run: `./gradlew testDebugUnitTest verifyRoborazziDebug`
Expected: `BUILD SUCCESSFUL`; `git status --porcelain -- "*.png"` prints nothing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/res/xml/data_extraction_rules.xml app/src/main/AndroidManifest.xml app/src/test/java/uk/co/siland/culvery/NoBackupTest.kt docs/privacy.html
git commit -m "Keep Culvery's data out of Android's backup and device transfer"
```

---

### Task 2: Session 1 — install and set up (walk-through §1; rulings 1, 6)

**Review:** controller + user.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§1.8)
- Modify: any file a fault fix needs (the walk-through's fault loop)

**Interfaces:**
- Consumes: Task 1's build.
- Produces: `$TMP/culvery-4e-serial`, `$TMP/culvery-4e-names.txt` (kept until Task 10), the household set up, the TalkBack package recorded for Task 7.

- [ ] **Step 1: Before the user arrives**

```bash
git branch --show-current
./gradlew testDebugUnitTest verifyRoborazziDebug
./gradlew :app:assembleRelease
ls "$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin/retrace.bat"
```
Expected: `4e-device-beta`; `BUILD SUCCESSFUL` twice; the `retrace.bat` path listed. If it's missing, ask the user to install it before the week: Android Studio › **Tools › SDK Manager › SDK Tools** › tick **Android SDK Command-line Tools (latest)** › **Apply**. "Release signing isn't set up": stop and ask.

- [ ] **Step 2: Run walk-through §1 to its end**

From the session start through §1.7 and the session end, writing §1.8's notes as it goes; faults through the Rules' fault loop.

- [ ] **Step 3: Commit the transcript**

```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record 4e session 1: install and set up on the SM-T510"
```

---

### Task 3: Session 2a — font scale, dark theme, splash, zone, bars (walk-through §2a; rulings 4, 11, 12)

**Review:** controller + user.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§2a.7)
- Modify: any file a fault fix needs (likely, if E1 overflows: the Home header's clock, with a font-scale test beside `app/src/test/java/uk/co/siland/culvery/shell/ui/ShellLayoutTest.kt`'s `atFontScale13…` tests at the size and density §1.8 recorded, and the `home_fs15` baseline)

**Interfaces:**
- Consumes: the household (Task 2).
- Produces: §2a.7.

- [ ] **Step 1: Session start**

The walk-through's session start; tell the user what this session changes (the font scale only) and that it goes back to the saved value at the end.

- [ ] **Step 2: Run §2a.0–§2a.6 to the end**

Including §2a.6's Restore (even if a check failed) and the session end; no `culvery-4e-saved-*` file of this session may be left. Notes in §2a.7.

- [ ] **Step 3: Commit the transcript**

```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record 4e session 2a: font scale, dark theme and start-up on the SM-T510"
```

---

### Task 4: Session 2b — keyboard, status-bar touches, weather, PIN chooser (walk-through §2b)

**Review:** controller + user.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§2b.5)
- Modify: any file a fault fix needs

**Interfaces:**
- Consumes: the household.
- Produces: §2b.5.

- [ ] **Step 1: Session start**

The walk-through's session start.

- [ ] **Step 2: Run §2b.1–§2b.4 to the end**

Including the session end. Notes in §2b.5.

- [ ] **Step 3: Commit the transcript**

```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record 4e session 2b: keyboard, touches and words on the SM-T510"
```

---

### Task 5: Session 3 — lock and restart; One UI's menu name (walk-through §3; rulings 9, 12)

**Review:** controller + user. Step 2 is a code change only if §3.3 found another menu path; it can be handed to a sonnet implementer with that step's text and the recorded path.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§3.9)
- Modify (only if §3.3 found another path): `core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupCopy.kt:120`, `core/setup/src/test/java/uk/co/siland/culvery/core/setup/KioskPageTest.kt:54`, `core/setup/src/test/screenshots/settings_kiosk_dark.png`, `settings_kiosk_light.png`

**Interfaces:**
- Consumes: the household.
- Produces: §3.9; possibly `HOME_APP_HOW_TO` (`internal const val String` in `uk.co.siland.culvery.core.setup`, used only by `pages/KioskPage.kt:48`) reworded.

- [ ] **Step 1: Session start, then run §3.0–§3.8 to the end**

The walk-through's session start; ask before each reboot. Including §3.8's Restore (with its fallbacks) and the session end. Notes in §3.9.

- [ ] **Step 2: If One UI's path isn't "Settings › Apps › Default apps" (E5)**

Skip if §3.3 recorded exactly "Settings › Apps › Default apps". Otherwise show the user the recorded path and the current line ("If that doesn’t work: tap Use other apps, then choose the home app in Android’s Settings › Apps › Default apps."), and ask for the new words built from the recorded path. No wording is written before the user approves it; use the approved line, character for character with curly apostrophes, in both places below.
1. The failing test: in `KioskPageTest.kt`, line 54's `howTo` becomes the approved line. Run `./gradlew :core:setup:testDebugUnitTest --tests "uk.co.siland.culvery.core.setup.KioskPageTest"`. Expected: FAIL in `theHowToLineShowsWhetherOrNotCulveryIsTheHomeApp` (no node with that text). Fails today: the page still names "Default apps" only. Fails if: the line goes back to the old path.
2. The fix: `SetupCopy.kt:120`'s `HOME_APP_HOW_TO` becomes the approved line. The same test command: PASS.
3. The baselines: `./gradlew :core:setup:compareRoborazziDebug --tests "*SettingsScreenshotTest*"`; open `core/setup/build/outputs/roborazzi/settings_kiosk_dark_compare.png` and `settings_kiosk_light_compare.png` (only the how-to line differs); `./gradlew :core:setup:recordRoborazziDebug --tests "*SettingsScreenshotTest*"`; `printf "settings_kiosk_dark\nsettings_kiosk_light\n" > "$TMP/expected-baselines.txt"`; the Global Constraints' baseline check (both lists empty); open both new images.
4. Other places that name the menu: `grep -rn "Default apps" --include=*.kt --include=*.md --include=*.html . | grep -v docs/superpowers`. Expected: only the two lines above (if `docs/setup/*.md` or `docs/*.html` name it, change them to the same path). The gate `./gradlew testDebugUnitTest verifyRoborazziDebug`: `BUILD SUCCESSFUL`.
5. Commit (add any docs file step 4 changed):
```bash
git add core/setup/src/main/java/uk/co/siland/culvery/core/setup/SetupCopy.kt core/setup/src/test/java/uk/co/siland/culvery/core/setup/KioskPageTest.kt core/setup/src/test/screenshots/settings_kiosk_dark.png core/setup/src/test/screenshots/settings_kiosk_light.png
git commit -m "Name One UI's default-apps menu in the Tablet lock how-to line"
```
6. The walk-through's Install block; the user reads the new line in Settings › Tablet lock; the pin check. Record the commit in §3.9.

- [ ] **Step 3: Commit the transcript**

```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record 4e session 3: lock and restart on the SM-T510"
```

---

### Task 6: Session 4 — Google, a write, the forecast offline (walk-through §4; ruling 15)

**Review:** controller + user.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§4.9)
- Modify: any file a fault fix needs

**Interfaces:**
- Consumes: the household.
- Produces: §4.9; a ruling request to the user if Settings closed behind the chooser.

- [ ] **Step 1: Session start**

The walk-through's session start. Tell the user: "This session makes a test series on your phone, turns Wi-Fi off for a minute, reconnects Google, and with your yes unshares one calendar for a few minutes and adds one test event. Everything is put back."

- [ ] **Step 2: Run §4.1–§4.8 to the end**

Ask before §4.1, §4.2, §4.5 and §4.7, each separately. If §4.3 shows Settings closing behind the chooser, record it and bring it to the user to rule on before any fix (ruling 15). Including the session end. Notes in §4.9.

- [ ] **Step 3: Commit the transcript**

```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record 4e session 4: Google on the SM-T510"
```

---

### Task 7: Session 5 — TalkBack (walk-through §5; rulings 5, 13)

**Review:** controller + user.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§5.6)
- Modify: any file a fault fix needs (a semantics test with real properties and `assertClickablesHaveRoleAndLabel` from `:core:ui`'s test fixtures, beside the owning module's tests)

**Interfaces:**
- Consumes: the TalkBack package recorded in §1.8.
- Produces: §5.6.

- [ ] **Step 1: Session start**

The walk-through's session start. Tell the user: "TalkBack will talk; you only listen. Whenever a PIN is needed I turn TalkBack off and stop the recording first, and you type it as usual."

- [ ] **Step 2: Run §5.0–§5.5 to the end**

§5.4 stops after item 2 to ask if the user is swiping; item 4 only if under 20 minutes have passed since §5.2. §5.5's Restore and digit check run even if the walk stopped early; then the session end. Notes in §5.6.

- [ ] **Step 3: Commit the transcript**

```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record 4e session 5: TalkBack on the SM-T510"
```

---

### Task 8: Session 6 — performance, day 1 (walk-through §6; rulings 2, 3, 14, 16)

**Review:** controller + user. Run it on the day the family week starts.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§6.4)
- Modify: any file a fault fix needs

**Interfaces:**
- Consumes: Sessions 1–5 done, every fix installed.
- Produces: day 1's numbers in §6.4, compared in Task 9.

- [ ] **Step 1: Session start**

The walk-through's session start. `git log -1 --format=%h` must be the build installed (`lastUpdateTime` after the last install); if not, the Install block and the pin check first.

- [ ] **Step 2: Run §6.1–§6.3 to the end**

The measure script with a 5-minute timeout, the running snapshot, the pin check, then USB debugging off (§6.3). Tell the user: "The family week starts now. Note anything odd with the time and what was on screen; please don’t clear anything on the tablet. I’ll ask for the cable again on day 3 or 4 for five minutes." Notes in §6.4.

- [ ] **Step 3: Commit the transcript**

```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record 4e session 6: performance on the SM-T510, day 1"
```

---

### Task 9: The family week — the day 3–4 read and day 7 (walk-through §7; ruling 3)

**Review:** controller + user.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§7.4)
- Modify: any file a fault fix needs

**Interfaces:**
- Consumes: day 1's numbers (§6.4).
- Produces: §7.4; the carried items Task 10 writes into the follow-ups.

- [ ] **Step 1: Day 3 or 4: run §7.2**

USB debugging on, the short read, the pin check, USB debugging off. Notes in §7.4. Commit:
```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record the 4e mid-week read"
```

- [ ] **Step 2: Day 7: run §7.3 to the end**

In its order (the logs before the snapshot, the snapshot before the script); each of the user's notes becomes a fault (the fault loop), a follow-up with what was seen, or a taste item. Notes in §7.4.

- [ ] **Step 3: Commit the transcript**

```bash
git add docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Record 4e day 7: a week of family use on the SM-T510"
```

---

### Task 10: Close-out (spec §8; walk-through §8)

**Review:** controller; the final review on opus.

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-4e-walkthrough.md` (§8 ticked)
- Modify: `docs/superpowers/plans/2026-09-23-plan1-followups.md` (the "For Plan 4e (the device)" lists at lines 17, 25, 33, 53, 59, 70, 87, 170, 217, 224; the 4c Tests and hardening item at line 125; the 4d-2 "From the TalkBack check" block at lines 260-269; the 4d triage's first paragraph at line 130; a new section before "## Feature ideas" at line 274)

**Interfaces:**
- Consumes: every transcript and its "Carried" list.
- Produces: the follow-ups with no "For Plan 4e" list left; the branch merged locally.

- [ ] **Step 1: Names, saved settings and logs**

Ask the user: "Are there names in the household I haven’t written down: people, calendars, the town, the account?" Add them to `$TMP/culvery-4e-names.txt`. Then:
```bash
w=docs/superpowers/plans/2026-10-12-4e-walkthrough.md
grep -n -i -F -f "$TMP/culvery-4e-names.txt" "$w"
grep -n -E '[[:alnum:]._%+-]+@[[:alnum:].-]+' "$w"
git log -p main..HEAD -- "$w" | grep -i -F -f "$TMP/culvery-4e-names.txt"
git log -p main..HEAD -- "$w" | grep -E '[[:alnum:]._%+-]+@[[:alnum:].-]+'
ls "$TMP"/culvery-4e-* 2>/dev/null
```
Expected: no output from the four greps (a hit in the history means a name was committed and later replaced: tell the user, who decides whether the branch's history is rewritten before the merge). `ls` lists only `culvery-4e-serial`, `culvery-4e-names.txt` and the `mapping` files: any `culvery-4e-saved-*` file is a setting not yet restored: stop and restore it. Ask: "May I copy the installed build’s mapping.txt next to your release key (`~/.culvery/`)? It’s needed to read a crash from this build." Copy it on a yes. Then `rm -f "$TMP"/culvery-4e-*`.

- [ ] **Step 2: The tablet left as found**

The pin check over USB. Then offer the user, in this order: confirm TalkBack's log level shows its Session 5 value (Settings › Accessibility › Screen reader › Settings › Advanced settings › Developer settings, through Use other apps); Developer options › **Revoke USB debugging authorisations**; Developer options off (the switch at its top). Each only with the user's yes; then back to Culvery (pinned).

- [ ] **Step 3: The follow-ups: close or carry every 4e item**

In `docs/superpowers/plans/2026-09-23-plan1-followups.md`, take each item under a "For Plan 4e (the device)" heading, the 4c Tests and hardening item about `dataExtractionRules` (line 125), and in the 4d-2 "From the TalkBack check" block (lines 260-269) the items Session 5 listened to again (focus falling to the status-bar clock, the twice-said first opens, "Stopped before the end"): delete it if its session closed it (the transcript says passed or fixed), or move it, with its reason, to the new section below.

| Follow-up (short) | Where |
|---|---|
| 2a: on-device pass of the calendar UI | §1, §2a, §2b |
| 2b-1: DM Sans weights / bold text on API 30 | §2a.1 |
| 2b-2: Samsung floating and split keyboards | closed: not wall use (plan review) |
| 2b-2: IME inset, scrim tap; add/edit sheet with the Samsung keyboard in lock-task; first Day row | §2b.1 |
| 2b-2: toast over the footer | carry (not run: no event added in §2b) unless §4.7 saw it |
| 2b-2: 30 s after midnight | carry ("not arranged; watch in family use") |
| 2b-2: "No explicit keyboard insets" manual grep | carry to Tests and hardening (not a device item) |
| 3a: chooser and consent in lock-task | §1.4, §4.3 |
| 3a, 4a: Play services crash loop | §6.1, §7.2, §7.3 |
| 4a: PIN chooser stages; wizard and Settings with the Samsung keyboard | §2b.3; §1.4, §2b.1 |
| 4b: weather at the density; fetch over a whole day; cold-start zone | §2b.3; §7; §2a.4 |
| 4c: Appendix B numbers; the font saving | §6; carried from the emulator (ruling 16): delete with that note |
| 4c: week-long run with no slowdown | §6.2 vs §7.3 (the soak script stays: decision 6) |
| 4c: Connect and Reconnect in lock-task | §1.4, §4.3 (device owner: the post-v1 plan) |
| 4c: pinning after a reboot; home app after a power cut; `onCreate` hand-over; E5 | §3.1; §3.5; §3.6; §3.3 |
| 4c: device-owner provisioning | the post-v1 plan below |
| 4c: unshared calendar; Delete this and following; the R8 build's writes | §4.5; §4.6; §4.7 |
| 4c: API 30 emulator pass | not in 4e (spec §9): carry |
| 4c Tests and hardening: `dataExtractionRules` | carry as "Android 12+ device check: the rules are in (Task 1) and Android 11 refuses backup (§1.6); device transfer on an Android 12+ tablet is untried" |
| 4d triage E1, E2, E3, E4, E8, E9 | §2a.1, §2a.5, §2a.3, §2a.2, §2a.1, §2a.1 |
| 4d triage E6, E10; E7 | §4.3, §4.4; §5 |
| 4d-1: Sign out and theme toggle touch; `lastFailure` wiring | §2b.2; §4.2 |
| 4d-1: unpinned window behind the home dialog | §3.4 (accepted: delete with "observed") |
| 4d-2: E7 stays; slow chooser closes Settings | §5; §4.3 (and the user's ruling) |
| 4d-2: `ShellUserActivity` after a Compose upgrade; clock set back; wizard PIN pad hardening | carry (no upgrade in 4e; decision 6) |
| 4d-2 TalkBack leftovers | §5.4; the PIN pad over an edited sheet and the toast with TalkBack on: carry (plan review) |

Delete each emptied "For Plan 4e (the device)" heading, and line 172's "E5 … is already under Plan 4c's 4e list above." Before "## Feature ideas (2026-10-02, after v1)" add:
```markdown
## From Plan 4e (deferred)

**Device owner (its own small plan, after v1; 4e decision 3)**
- Provisioning on a freshly reset tablet with no accounts (`docs/setup/release.md` §6): install the release, `dpm set-device-owner`, then add the family's Google account.
- Lock-task during Google's account chooser: Play services allowed in only while the chooser and consent screens show; Culvery alone afterwards.
- A reboot lands in Culvery with no pinning question, and Back + Overview can't leave it.
- The unpinned window behind Android's home-role dialog stays as it is with or without device owner (accepted 2026-10-06).

**Carried from the 4e sessions**
```
followed by one line per carried item from §1.8–§7.4, each with what was seen and why it wasn't fixed in 4e ("- None." if there are none), then:
```markdown
**Not in 4e (spec §9), still open**
- The clock set back during sleep (`elapsedRealtime`, `DefaultAccessControl.kt`); the wizard PIN pad hardening (`SetupWiring.kt`); the soak sampling script; an API 30 emulator pass; `ShellUserActivity` re-checked after a Compose upgrade.
```
In the 4d triage section's first paragraph (line 130), after "4d-2 is done (…)." add: "4e is done (`docs/superpowers/plans/2026-10-12-culvery-04e-device-beta.md`, walk-through `docs/superpowers/plans/2026-10-12-4e-walkthrough.md`): E1–E10 are closed or carried under \"From Plan 4e\"."
Then `grep -n "For Plan 4e" docs/superpowers/plans/2026-09-23-plan1-followups.md`. Expected: no output. Tick §8's done items in the walk-through and commit:
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md docs/superpowers/plans/2026-10-12-4e-walkthrough.md
git commit -m "Close the 4e device items in the follow-ups and list device owner as its own plan"
```

- [ ] **Step 4: Simplify, the final review, the gate**

Run the `simplify` skill over `git diff main...HEAD` (Task 1's files and any fault fixes); if it changes anything, the gate and `git commit -m "Simplify the 4e changes"`. Then one opus reviewer over the whole branch, the spec, this plan and the walk-through: every spec section run and recorded; `NoBackupTest` failing on each mutation in its "Fails if" line; every fault fix with a test and a recheck; the transcript naming no one (it lists capitalised words that aren't Culvery's own); the follow-ups map complete. One fix round: each accepted finding fixed, the gate, `git commit -m "Final review fixes"`. Then `./gradlew testDebugUnitTest verifyRoborazziDebug`: `BUILD SUCCESSFUL`. If the app changed since day 7, the Install block and the pin check. Tick §8's last items; commit the walk-through if it changed (`git commit -m "Tick the 4e close-out checklist"`).

- [ ] **Step 5: Merge locally**

Use superpowers:finishing-a-development-branch; the user has always chosen "merge locally": offer it and wait. Never push.

---

## Spec coverage (4e design → tasks)

| Design | Where |
|---|---|
| §1 purpose and done | Tasks 1–10; walk-through §8 |
| §2 decisions | Global Constraints (2–7 verbatim); 1 and 8: the task shape |
| §3 ground rules (as amended) | Walk-through Rules (verbatim, once); rulings 12–14; Review Focus |
| §4.1 Session 1 | Task 2; walk-through §1 (rulings 1, 6) |
| §4.2 Session 2 (2a, 2b) | Tasks 3, 4; walk-through §2a, §2b (rulings 4, 11) |
| §4.3 Session 3 | Task 5 (the `HOME_APP_HOW_TO` fix, Step 2); walk-through §3 |
| §4.4 Session 4 (with §4.1's write) | Task 6; walk-through §4 (ruling 15) |
| §4.5 Session 5 (trimmed) | Task 7; walk-through §5 (rulings 5, 13) |
| §4.6 Session 6 | Task 8; walk-through §6 (rulings 2, 16) |
| §5 no backup or transfer | Task 1 (rulings 7, 8); walk-through §1.3, §1.6 |
| §6 faults | The walk-through's fault loop, used in Tasks 2–9 |
| §7 the family week, day 7 | Task 9; walk-through §7 (ruling 3) |
| §8 close-out | Task 10; walk-through §8 |
| §9 not in 4e | Task 10 Step 3 |
