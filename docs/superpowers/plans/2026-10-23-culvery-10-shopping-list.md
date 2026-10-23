# Culvery — Plan 10: The Shopping list Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Tasks 1–10 are subagent tasks (sonnet implementers, a reviewer per task; **opus** reviews where the table says so). Task 11 is the controller. Task 12 is the controller on the `culvery-5` AVD with the user. Tasks 13–14 need the user and the son's SM-T510.

**Goal:** Anyone at the wall adds to and ticks off one shopping list, kept in Google Tasks so the user and their wife see and change it on their phones, with suggestions and favourites learned from what the house adds.

**Architecture:**
- **`:core:google`** (new) holds the Google sign-in both Google providers use: `Authorizer`, `TokenSource` and `PlayServicesCheck`, now taking the scopes per call, and `GoogleAccounts`, the calendar's connected accounts. The calendar asks for exactly the scopes it asks for today.
- **`:capability:shopping`** (new) declares `ShoppingProvider` and owns everything that outlives a provider: `shopping.db` (the connection, the last read, the queue, what's been learned), the queue's drain, the 60-second checks, suggestions and favourites, the tab, the Home card and Settings › Shopping.
- **`:provider:shopping-google-tasks`** (new) is the Tasks REST client behind the seam, and the connect flow that asks for the Tasks scope and finds or makes the "Shopping" list. **`:provider:shopping-fake`** (new, debug only) is an in-memory list for the emulator.
- **Home** gains a fourth card size, `UTILITY`: when one shows, it takes the right column's top and Lights and Music sit side by side below it at 212 dp.

**Tech Stack:** Kotlin 2.2.20, Jetpack Compose (BOM 2025.09.00, Material 3), Hilt 2.57.1, Room, DataStore, kotlinx.serialization, OkHttp 4.12, play-services-auth 21.4.0 (`Identity.getAuthorizationClient`), Coroutines 1.10.2; JUnit4 + Robolectric 4.16 + Truth + Turbine, MockWebServer, Roborazzi 1.46.1. **No new library.**

**Spec:** `docs/superpowers/specs/2026-10-23-culvery-10-shopping-list-design.md` (all of it). The drawings are the handoff `docs/design/2026-10-redesign/handoff.md`, "Home (1a–1d)" › "Right column" and "Shopping card (note 12)", and the brainstorm mockups `.superpowers/brainstorm/1424-1792769475/content/shopping-tab-b2.html` (the tab) and `home-card.html` (option A). The mockups are at their own scale; take sizes from the handoff and this plan's Units, and only arrangement from the mockups. Where the spec and the handoff disagree, the spec wins.

**Previous plans (format, shared parts):** `docs/superpowers/plans/2026-10-20-culvery-09c2-redesign-wizard-settings.md` (the house format, the image and gate routine), `docs/superpowers/plans/2026-10-14-culvery-05-ring-doorbell.md` (a new capability and provider pair: `:capability:door` and `:provider:door-ring`).

**Builds on:** branch `plan-10-shopping` in the one checkout `C:\Users\si\code\siland\culvery`, cut from `main` at 2514bab1. No worktrees. Never push.

| # | Task | Review |
|---|---|---|
| 1 | `:core:google`: the shared Google sign-in, scopes per call, the connected accounts | **opus** (the calendar's sign-in must not change; tokens) |
| 2 | `:capability:shopping`: the module, the seam, `shopping.db`, the store, the shared text key | **opus** (files and data) |
| 3 | Suggestions and favourites | sonnet |
| 4 | The queue, the drain, the checks, add, tick and Undo | **opus** (offline, races, nothing lost or doubled) |
| 5 | `:provider:shopping-google-tasks`: the Tasks client and connecting | **opus** (auth, the log, Google's answers) |
| 6 | Home: the utility row, Lights and Music side by side | sonnet |
| 7 | The Shopping tab | sonnet |
| 8 | The Home card | sonnet |
| 9 | Settings › Shopping, turning it on and off, the capability wired in, the calendar's disconnect warning | **opus** (Admin PIN, accounts, the kiosk) |
| 10 | `:provider:shopping-fake` for debug builds | sonnet |
| 11 | Simplify the whole branch | controller |
| 12 | The emulator walk-through on `culvery-5` | controller + user |
| 13 | On the tablet | controller + user, **needs the SM-T510** |
| 14 | Final review, merge, install | controller + opus reviewer, **needs the SM-T510** |

**Task order and why:**
1. **The sign-in first** (1): it is a move under the calendar, and must be green on its own before anything uses it.
2. **The capability's data and logic** (2–4) before any provider or screen, so they are tested against the fake provider alone.
3. **The Google provider** (5) against the seam from 2.
4. **Home's reshape** (6) before the card (8) that fills it.
5. **Settings and wiring** (9) last of the code, because it turns the tab and card on.

## Rulings against the code

The spec's §5 leaves these to the builder, and a few more fall out of the code. Each is this plan's call.

1. **The shared sign-in is a `:core:google` module** (spec §4.1). `ModuleBoundaries` lets a provider depend only on core modules and its own capability, so the Tasks provider can't use `:provider:calendar-google`; a core module is the only shared home. It holds `Authorizer`, `Authorization`, `PlayServicesAuthorizer`, `TokenSource`, `PlayServicesTokenSource`, `PlayServicesCheck`, `GooglePlayServicesCheck`, `authorizationFailure`, `isUserCancel`, `PlayServicesStatusException`, `UPDATE_PLAY_SERVICES`, and their Hilt bindings. Every call takes its scopes: the calendar passes `CALENDAR_SCOPES` (unchanged, still in `:provider:calendar-google`) and the Tasks provider `TASKS_SCOPES`, so a household that never turns Shopping on is never asked for Tasks.
2. **`NeedsSignInException`, `UnreachableException` and `OfflineException` move to `:core:plugin`** (`Connections.kt`), because `:core:google` throws them and can't see `:capability:calendar`. `CalendarContract.kt` keeps their names as `typealias`es, so no calendar file changes its imports.
3. **The connected accounts cross by a core interface.** `GoogleAccounts` (`:core:google`) lists the accounts of the calendar's Google connections; `:provider:calendar-google` implements it from `CalendarStore`. The Tasks provider reads it to offer accounts and to notice a disconnected one.
4. **The disconnect warning crosses by `AccountUse`** (`:core:plugin`), multibound: Shopping binds one; the calendar's disconnect question appends its line when any says the account is used (spec §4.10).
5. **The queue is Shopping's own small table, not the calendar's outbox** (spec §5). The outbox is shaped around sources, series and a 48-hour drop; Shopping needs three kinds (add, tick, untick), strict order, and **no maximum age**: an add is never dropped (anti-goal 1). The backoff values are the outbox's: 30 s, 1 min, 2 min, 5 min, then every 5 min.
6. **Reads and the drain share one lock, and a check drains before it reads.** A read then always follows every write it could race (spec §4.4 "the next read mustn't undo it"), so what's shown is the last read plus whatever is still queued. No timestamps are compared.
7. **The text key moves to `:core:plugin` as `textKey()`** (spec §4.6). The calendar's `titleKey()` already trims, collapses inner spaces, drops accents and lowers case; it becomes a call to `textKey`, so the two stay one rule.
8. **The provider seam takes the `Connection`, as `CalendarProvider` does**, and its health is worked out by the capability from what the provider throws, as the calendar engine does. `history` returns `ShoppingItem`s, not bare strings, so an item seen open and later seen ticked is counted once (rule 11). Neither changes what crosses the seam: no Google ids, ETags or field names. The list's id lives in `Connection.config`, which is opaque to the capability.
9. **The Undo strip is the house toast** with an "Undo" action (`Toaster`, `ToastAction`, `TOAST_WITH_ACTION_MS` = 8 s, at least the 6 s rule). It already shows at the foot on any screen, replaces the one before ("one strip for the latest"), and is what the calendar's Undo uses. A ticked row stays muted with its line through while its tick is the latest and less than 8 s old, then leaves the list; a newer tick sends the earlier one off at once.
10. **Duplicates on the wall** (spec §4.6): an add whose `textKey` matches any open row shown (read or queued) adds nothing, clears the box, and flashes that row in `accentSoft` for `FLASH_MS = 1_200`. Adds go through one mutex, so a double tap, Done then Add, or a favourite tapped twice makes one queued add.
11. **Learning counts each item once.** `learned(key, text, count, lastMillis)` and `seen(id, atMillis)`: an item id not yet in `seen` (open or from history) adds 1 and is marked seen; a wall add adds 1 when queued, and the id its drain returns is marked seen. A tick only updates `lastMillis`. Both tables are pruned at 12 months unused, at most once a day. The display text is the latest text whose first letter is upper case, else the latest.
12. **Suggestions start at 1 character** (spec §4.7): prefix matches, then word-start matches, each by count, then last used, then key. Items on the list never show. A suggestion chip adds that item, as a favourite does (spec ruling 4 makes both one tap).
13. **Favourites have no threshold**: every learned item not on the list, by count, then last used, then key, minus any shown as a suggestion. How many show is how many fit.
14. **The chip row is one line, never wrapping** (spec §5). Ruling 7 draws one row, and the list below needs the height. `FitRow` places whole chips left to right and drops the first that would not fit and everything after it. Suggestions and favourites are split by a 1 dp `surf3` rule, 6 dp each side, shown only when both groups have a chip.
15. **The tab's list overflow** (spec §5): three columns, filled down then across, `ceil(n / 3)` rows per column; the whole list scrolls, with `HhBottomFade` at its foot while more is below. No pill: the tab is where "more" would go.
16. **The Home card's overflow:** two columns, `rows = floor(item area height / 31 dp)`, filled down then across. When the items don't fit, the last slot is dropped and "+{n} more · Open Shopping" counts what's hidden.
17. **Home's placer** (spec §4.9): `HomeCardSize.UTILITY`; `HomeLayout(left, right, utility: HomeCard? = null)`. With a utility card: it takes the top of the right column; the top two REGULAR cards share one 212 dp row below it, side by side, either one taking the full width alone; WIDE never shows; with no REGULAR card the utility card fills the column. Without one, everything is as today. Shopping's card is UTILITY at priority 40.
18. **Checks don't pause while the wall sleeps** (spec §4.4). A check is one `tasks.list` (plus a short history read) a minute, about 1,500 a day against Google's 50,000; the Display state lives in `:app` and pausing would need a new seam for little gain; and on waking the list is already fresh. They do stop when Culvery isn't in front and when nothing is watching, as Music's do.
19. **Adds are limited to 3 a second** (spec §4.11). A fourth within the same second is refused quietly: the box keeps its text, so nothing is lost.
20. **The rail icon is `shopping_cart`** (spec §5), added to the icon font, because it says "shopping" where `checklist` says "to-do". The Shopping tab is `order = 20`.
21. **The Home card's +** opens the tab with the add box focused and the keyboard up, through a capability-internal `ShoppingFocus` request the tab consumes once. No shell change.
22. **On Home, the whole row ticks** (spec §4.9 says the circle). Rows are the handoff's 31 dp; the row, not the 20 dp circle, is the tap target, so a finger can hit it. The 31 dp height is below the house 48 dp and goes on the end-of-v1 list.
23. **The add box keeps focus after Done.** `HhTextField` gains `clearsFocusOnDone: Boolean = true`; the add box passes `false` (spec §4.8 "keeps focus, ready for the next item").
24. **Debug builds bind a sample provider** (`:provider:shopping-fake`, `userConnectable = false`), as the calendar's debug sample, so the emulator walk-through needs no Google account. With more than one provider bound, Settings offers one switch per provider, at most one on; the capability uses the provider the stored connection names.
25. **Google's order** (spec ruling 9): top-level tasks by `position` (compared as strings), each followed by its subtasks by `position`. A task with a blank title is skipped. A `tasks.insert` without `previous` puts the item first, so a queued add shows at the top, newest first, where Google will put it.
26. **What a failure does to the queue:**
    - unreachable, offline, needs permission, account gone, list gone: the head stays, with its attempts and next time (the queue waits; nothing is dropped);
    - a tick or untick whose item has gone (`ItemGoneException`): dropped, because the item isn't on the list either way;
    - an add Google refuses (`ShoppingRejectedException`): it stays and retries on the 5-minute step, never dropped. It shows in the list as queued.
27. **"Stop using"** (spec §4.10) asks first, behind an Admin's fresh PIN. When adds or ticks are still queued, the question says how many will not be sent. It removes the connection, the last read and the queue, and keeps `learned` and `seen`.
28. **The shell variable for prediction lists is `$LISTS`** (`C:/t9`), as in 9c-2.

## Notes for the end-of-v1 design review

These are this plan's calls; Task 12 lists them again at the walk-through:
- Home's Shopping rows are the handoff's 31 dp, under the house's 48 dp touch height; the row is the target (ruling 22).
- The Undo strip is the house toast, bottom-centre, not a strip across the tab's foot (ruling 9).
- The chip row never wraps; chips that don't fit are left off (ruling 14).
- Home's card shows "{n} items" with no "added from phones" footer, because the added-by label is cut.
- Lights and Music at 343 × 212 dp, side by side, against their 701 × 285.5 dp look without Shopping.

## Units (converted once, px ÷ 1.5)

| Where | Handoff px | dp / sp |
|---|---|---|
| Home grid | gaps 22; Lights and Music row 318 | gaps 15 (`ShellDimens.gridGap`); row 212 (`ShellDimens.cardRow`) |
| Home right column | at 1280 × 800 | 701 wide, 586 high; utility card 701 × 359; row cards 343 × 212 each |
| Shopping card | radius 36; padding 28/30 | radius 24; padding 19 top and foot, 20 sides |
| Card header | "Shopping" 30/700; "7 items" 21 muted; + 60 round quiet | 20 sp W700; 14 sp `mute`, 8 after the title; + 40 drawn in a 48 tap, `HhRoundButton` quiet on `Surf` |
| Card items | rows 46; circle 30, ring 3 `s3`; name 24 | rows 31; circle 20, ring 2 `surf3`; name 16 sp, 11 after the circle; columns 12 apart; 12 below the header |
| Card footer | 20 muted | 13 sp `mute`, 8 above |
| Tab add card | `s1` | `surf`, radius 24, padding 19 × 20; the field row and the chips 12 apart; Add 12 after the field |
| Tab chips | — | `HhChoiceChip` (48 dp), 9 apart; suggestions `accentSoft`/`accent`, favourites `surf2`/`ink`; the rule 1 × 28, `surf3` |
| Tab list | `s1`; three columns | `surf`, radius 24, padding 19 × 20; rows 48; circle 20, ring 2; name 18 sp, 12 after the circle; columns 15 apart; ticked: `mute` with a line through, circle filled `accent` |
| Gaps on the tab | — | header to add card 15; add card to list 15 |

## Global Constraints

**From the spec, verbatim:**
- §2 "Adding and ticking on the wall with no PIN, by anyone." "A provider seam, so moving from Google Tasks to Todoist means writing a provider, not reworking the capability or the screens."
- §2 anti-goals: "Losing an item someone added: offline, at a sync, or in a race with a phone." "The wall adding a second "Milk": a double tap, a retried add, or tapping a favourite already on the list." "Giving whoever is at the wall a way into anything beyond the shopping list." "Item text in the logs (LogHygieneTest)."
- §4.2 "**Nothing Google-specific crosses the seam:** no task-list ids, ETags or Google field names."
- §4.3 "Culvery never deletes a task." "Logs: call names and outcomes only, as `GoogleCall` does today. Never item text."
- §4.3 scope `https://www.googleapis.com/auth/tasks`, "through the same `AuthorizationClient` route as the calendar (play-services-auth; never the deprecated GoogleSignIn)".
- §4.4 "check every 60 s, and straight away when either opens."
- §4.8 "capped at 100 characters"; "Plan 9's rule: at least 6 s".
- §4.11 "Connecting and disconnecting need an Admin's fresh PIN."

**Copy** (verbatim from the spec; curly apostrophes and "›", "·" in code):
- **Tab:** "Shopping"; "`{n} items · from Google Tasks, {time ago}`" ("1 item" for one); "Add"; "Nothing on the list. Add something above, or tap a favourite."; "Favourites appear as you add things."; "`Ticked {item}`" with "Undo".
- **Problems** (tab, card and Settings): "Can’t reach Google Tasks. Items you add are kept and sent when it’s back." (offline too); "Allow Google Tasks in Settings › Shopping"; "Shopping’s Google account was disconnected. Connect it again in Settings › Calendars."
- **Card:** "Shopping"; "`{n} items`"; "Nothing on the list"; "`+{n} more · Open Shopping`".
- **Settings › Shopping:** "Use Google Tasks for the shopping list"; "Stop using Google Tasks"; "Connect Google Calendar first. Shopping uses the same account."
- **This plan's copy** (spec gaps; Task 9 and 4 own them):
  - before any read: "`{n} items · not read from Google Tasks yet`";
  - the list deleted on a phone: "The Shopping list isn’t in Google Tasks any more. Make a new one in Settings › Shopping." (tab and card), and on Settings "The Shopping list isn’t in Google Tasks any more." with the button "Make a new list";
  - the account choice: "Which Google account holds the list?";
  - the permission kept: "Google Tasks stays allowed for {account} until someone removes it in their Google account settings.";
  - Settings' fix button: "Allow Google Tasks";
  - stop's question: "Stop using Google Tasks? The list stays in Google Tasks, and suggestions stay here." plus, when queued, " {n} unsent changes will be dropped." ("1 unsent change");
  - the calendar's disconnect, when Shopping uses the account: " Shopping uses this account too, and stops until it’s connected again." appended to `disconnectQuestion`;
  - connecting fails: `couldNotConnect`'s words with "Google Tasks" ("Couldn’t connect to Google Tasks — try again.", "Couldn’t reach Google Tasks — check the tablet’s Wi-Fi, then try again.");
  - the scope refused: "Google Tasks wasn’t allowed. Try again and tick Google Tasks." (toast);
  - the card's + spoken "Add to the shopping list";
  - after stopping: "Stopped using Google Tasks." (toast).
- Product names in copy are the provider's `descriptor.displayName`, so a Todoist provider changes them without a screen changing.

**Privacy:**
- Item text appears on screen and in toasts only. It is never logged, never in an exception message, never in a URL (it goes in request bodies only), and never in a test name or tag beyond the sample items.
- The account email shows only on Settings › Shopping, as Settings › Calendars shows it.
- Tokens are never stored; `Authorization.Granted` stays a plain class whose `toString` can't print one.

**From the house rules** (as 9c-2; binding):
- **Platform:** the package root is `uk.co.siland.culvery`; `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only; **no new library; never change a version**; no deprecated API (the one opt-in stays `ExperimentalMaterial3Api` in `HhPercentSlider`). The APIs this plan names were checked: `Identity.getAuthorizationClient`, `AuthorizationRequest.Builder.setRequestedScopes`/`setAccount`, `AuthorizationClient.authorize`/`getAuthorizationResultFromIntent`/`clearToken`, `LocalSoftwareKeyboardController`, `FocusRequester`, `rememberLauncherForActivityResult` with `StartIntentSenderForResult`; none is deprecated. Never `GoogleSignIn`.
- **New modules:** `:core:google` (`culvery.android.library`, `culvery.hilt`), `:capability:shopping` (library, compose, hilt, room, roborazzi; as `capability/door/build.gradle.kts`), `:provider:shopping-google-tasks` (library, compose, hilt, kotlin serialization; as `provider/calendar-google`), `:provider:shopping-fake` (library, hilt). Each is added to `settings.gradle.kts` and `app/build.gradle.kts` (the fake as `debugImplementation`), and to the README's module list.
- **The test gate:** `./gradlew testDebugUnitTest verifyRoborazziDebug`; never plain `test`, `check` or `build`, and never a Gradle `install…` task; no subagent touches a device; **never set TMP, TEMP, TMPDIR or `java.io.tmpdir`**; `$LISTS` is `C:/t9`, a plain shell variable, never exported.
- **Tests that can fail:** every new test fails before its change; one that passes before it is labelled **pin**. Timer tests run on virtual time; UI timers under `compose.mainClock`, one frame before the boundary and at it plus one. Colour checks read `pixelAt(node, dx, dy)` inside a fill and clear of content, under `@GraphicsMode(NATIVE)`. Tokens aren't asserted against the literals that set them.
- **Every UI task ends the same way** (9c-2):
  1. Run `compareRoborazziDebug` on the module.
  2. Open each `*_compare.png` and each new image against the drawing the task names.
  3. Record with `./gradlew <module>:recordRoborazziDebug`.
  4. The baseline check. Write both `$LISTS/expected-baselines.txt` and `$LISTS/expected-prefixes.txt` afresh (an empty file for the one not used), then:
```bash
git status --porcelain -- "*.png" | sed 's#.*/##; s#\.png$##' | sort -u > "$LISTS/changed.txt"
echo "changed but not listed:"; comm -23 "$LISTS/changed.txt" <(sort -u "$LISTS/expected-baselines.txt")
echo "listed but unchanged:"; comm -13 "$LISTS/changed.txt" <(sort -u "$LISTS/expected-baselines.txt")
echo "changed but not predicted:"; grep -v -f <(sed 's/^/^/' "$LISTS/expected-prefixes.txt") "$LISTS/changed.txt"
echo "predicted but unchanged:"; while read -r p; do grep -q "^$p" "$LISTS/changed.txt" || echo "$p"; done < "$LISTS/expected-prefixes.txt"
```
     Both lists of the pair that applies must be empty, or stop.
  5. Run the gate. 6. Commit.

  Baselines live in `<module>/src/test/screenshots/`. Every new screen is recorded in `_dark` and `_light`.
- **Copy and accessibility:** copy names what the user does; `CopyScanTest` passes; every screen test ends with `compose.assertClickablesHaveRoleAndLabel()`; TalkBack names follow the copy. A row's tick is `Role.Checkbox`, spoken with the item's name and "ticked"/"not ticked".
- **Code:** icons come from `Icons`, and a new one goes in `tools/fonts/icons.txt` with the subset rebuilt from the full font at `C:/t9/material-symbols-full.ttf` (`python tools/fonts/subset.py --source "$LISTS/material-symbols-full.ttf"`); layout numbers are named in the module's dimens object; comments say what the code can't, in one line, spec refs as `(10 §4.6)`, never a task or ruling number.
- **Sample data only:** names Alex, Sam, Mia; items Milk, Bread, Eggs, Butter, Bananas, Bin bags, Tea bags, Apples; the account `family@example.com`; any address `192.168.1.x`. Files are UTF-8.
- **Git:** a commit is a plain sentence with no prefix and no trailer; never push; **never** `git checkout -- .`, `git restore .` or `git stash`.

## Review Focus

1. **A double tap, Done then Add, or a favourite tapped twice.**
   - **Expected:** one queued add, one "Milk" row; the second tap flashes the row and clears the box.
   - **Tests** (Task 4): `ShoppingActionsTest.twoAddsOfTheSameTextAtOnceQueueOne`, `addingTextAlreadyOnTheListFlashesIt`; (Task 7) `ShoppingTabTest.doneThenAddAddsOnce`.
2. **An insert Google made, but whose answer was lost** (a timeout after the write).
   - **Expected:** the retry finds the open "Milk" by its key and counts the add done; Google has one Milk; the id is marked seen, so learning counts it once.
   - **Tests** (Task 4): `ShoppingSyncTest.aRetriedAddThatAlreadyLandedAddsNothing`, `aRetriedAddIsLearnedOnce`.
3. **Offline: add, tick it, restart, then the network comes back.**
   - **Expected:** the row shows at once and greys on tick; after the restart the queue is still there in order; on reconnect the add then the tick are sent; Google ends with Milk completed; nothing doubled.
   - **Tests** (Task 4): `ShoppingSyncTest.anOfflineAddAndTickSurviveARestartAndDrainInOrder`, `undoBeforeTheTickIsSentDropsIt`, `undoAfterTheTickIsSentUnticks`.
4. **What phones put on the list:** a blank title, a 1,000-character title, a subtask, a duplicate "Milk", a task completed and uncompleted on a phone.
   - **Expected:** the blank is skipped; the long one ends in "…" on one line; the subtask follows its parent; both Milks show and tick on their own; a task reopened on a phone is back at the next check (it is open again in `items`).
   - **Tests** (Task 5): `GoogleTasksProviderTest.itemsAreTheOpenTasksInGoogleOrder`; (Task 4) `ShoppingViewTest.aPhonesDuplicateShowsAsTwoRows`; (Task 7) `ShoppingTabTest.aLongNameIsOneLineEndingInAnEllipsis`.
5. **The list deleted on a phone while adds are queued.**
   - **Expected:** the tab and card say so; the queue waits; Settings' "Make a new list" makes one and the queue drains into it.
   - **Tests** (Task 4): `ShoppingSyncTest.aDeletedListWaitsWithTheQueueKept`; (Task 9) `ShoppingSettingsTest.makeANewListThenTheQueueDrains`.

---

## File Structure

```
core/plugin/.../Connections.kt (the three exceptions, AccountUse), TextKey.kt (textKey)           (Tasks 1, 2)
core/google/ (new): Authorizer.kt, TokenSource.kt, PlayServices.kt, GoogleAccounts.kt, di/GoogleModule.kt;
  src/testFixtures: FakeAuthorizer.kt, FakeTokenSource.kt, FakeGoogleAccounts.kt                    (Task 1)
provider/calendar-google/: TokenSource.kt (CALENDAR_SCOPES only), GoogleHttp.kt, GoogleConnectFlow.kt,
  GoogleCalendarAccounts.kt (new), di/GoogleCalendarModule.kt; tests onto core/google's fakes         (Task 1)
capability/calendar/: CalendarContract.kt (typealiases), RememberedTitles.kt (titleKey → textKey)   (Tasks 1, 2)
capability/shopping/ (new): ShoppingContract.kt, ShoppingStore.kt, db/ShoppingDatabase.kt, di/ShoppingModule.kt (Task 2);
  ShoppingLearning.kt (Task 3); ShoppingSync.kt, ShoppingDrainLoop.kt, ShoppingChecks.kt, ShoppingActions.kt,
  ShoppingView.kt, ShoppingWords.kt (Task 4); ui/ShoppingTab.kt, ui/FitRow.kt, ui/ShoppingDimens.kt,
  ShoppingFocus.kt (Task 7); ui/ShoppingCard.kt (Task 8); ShoppingCapability.kt, ShoppingConnections.kt,
  ui/ShoppingSettingsPage.kt (Task 9); src/testFixtures: FakeShoppingProvider.kt (Task 2)
provider/shopping-google-tasks/ (new): GoogleTasksProvider.kt, TasksApi.kt, TasksJson.kt, TasksConnectFlow.kt,
  di/GoogleTasksModule.kt; src/test/resources/tasks/*.json; docs/setup/google-calendar.md               (Task 5)
core/plugin/.../HomeCard.kt, HomeCardPlacer.kt; app/.../shell/ui/HomeGrid.kt, ShellDimens.kt;
  capability/lights/.../ui/LightsCard.kt, capability/music/.../ui/MusicCard.kt (if they must give)     (Task 6)
core/ui/.../Controls.kt (HhTextField clearsFocusOnDone), Icons.kt, tools/fonts/icons.txt, the font  (Task 7)
capability/calendar/.../CalendarReview.kt, di/ (Set<AccountUse>), ui/ReviewCalendars.kt              (Task 9)
provider/shopping-fake/ (new): SampleShoppingProvider.kt, di/SampleShoppingModule.kt                  (Task 10)
settings.gradle.kts, app/build.gradle.kts, README.md                                                  (Tasks 1, 2, 5, 10)
docs/superpowers/plans/2026-09-23-plan1-followups.md                                                  (Tasks 12, 13)
```

---

### Task 1: `:core:google`: the shared Google sign-in (spec §4.1, §4.3; rulings 1–3)

**Review:** opus. The calendar's sign-in moves under it; a household without Shopping must see no change.

**Files:**
- Create `core/google/build.gradle.kts` (library, hilt; `api(project(":core:plugin"))`, `implementation(libs.play.services.auth)`, `implementation(libs.kotlinx.coroutines.play.services)`; `java-test-fixtures` as `:core:plugin` does), and register it in `settings.gradle.kts` and `app/build.gradle.kts`.
- Move from `provider/calendar-google/.../TokenSource.kt` and `PlayServices.kt` into `core/google/src/main/java/uk/co/siland/culvery/core/google/`: `Authorizer.kt` (`Authorization`, `Authorizer`, `PlayServicesAuthorizer`, `authorizationFailure`, `isUserCancel`, `PlayServicesStatusException`), `TokenSource.kt` (`TokenSource`, `PlayServicesTokenSource`), `PlayServices.kt` (`PlayServicesCheck`, `GooglePlayServicesCheck`, `UPDATE_PLAY_SERVICES`). Create `GoogleAccounts.kt` and `di/GoogleModule.kt` (the three `@Binds` that leave `GoogleCalendarModule`).
- Modify `core/plugin/.../Connections.kt`: the three exceptions move here; `AccountUse`.
- Modify `capability/calendar/.../CalendarContract.kt`: the three classes become `typealias`es.
- Modify `provider/calendar-google`: `TokenSource.kt` keeps only `CALENDAR_SCOPES`; `GoogleHttp.kt` (`send` asks `tokens.token(account, CALENDAR_SCOPES)`); `GoogleConnectFlow.kt` (`authorize(account, CALENDAR_SCOPES)`); create `GoogleCalendarAccounts.kt`; `di/GoogleCalendarModule.kt` binds `GoogleAccounts`; `build.gradle.kts` adds `implementation(project(":core:google"))` and `testImplementation(testFixtures(project(":core:google")))`.
- Move `provider/calendar-google/src/test/.../FakeAuthorizer.kt` and `FakeTokenSource.kt` to `core/google/src/testFixtures/java/uk/co/siland/culvery/core/google/test/`, made public; create `FakeGoogleAccounts.kt` there.
- Test: `core/google/src/test/.../TokenSourceTest.kt` (moved from calendar-google), `provider/calendar-google/src/test/.../GoogleCalendarAccountsTest.kt`, `GoogleApiTest.kt`, `GoogleConnectFlowTest.kt`. Every existing calendar-google and calendar test stays green (**pins**).

**Interfaces:**
- Produces (`uk.co.siland.culvery.core.plugin`):
  - `class NeedsSignInException`, `open class UnreachableException`, `class OfflineException : UnreachableException`, constructors and KDoc unchanged.
  - `fun interface AccountUse { suspend fun usersOf(account: String): List<String> }` — the names of features that use [account], e.g. `["Shopping"]`.
- Produces (`uk.co.siland.culvery.core.google`):
  - `interface Authorizer { suspend fun authorize(account: String?, scopes: List<String>): Authorization; fun authorizationFrom(data: Intent?): Authorization; suspend fun clearToken(token: String) }`.
  - `interface TokenSource { suspend fun token(account: String, scopes: List<String>): String; suspend fun invalidate(token: String) }`.
  - `interface GoogleAccounts { val connected: Flow<List<String>> }` — the account emails of the calendar's Google connections, in the order they were connected, distinct.
  - Test fixtures: `FakeAuthorizer` records each `authorize` call's `(account, scopes)` in `asked: List<Pair<String?, List<String>>>`; `FakeTokenSource` records `(account, scopes)` in `asked`; `FakeGoogleAccounts(initial: List<String>)` with `MutableStateFlow` `connected`.
- Produces (`provider/calendar-google`): `class GoogleCalendarAccounts @Inject constructor(store: CalendarStore) : GoogleAccounts`, reading `store.connections()` for `providerId == GOOGLE_PROVIDER_ID` and `config[CONFIG_ACCOUNT]`.

- [ ] **Step 1: The failing tests.**
  - `TokenSourceTest.aTokenIsAskedForTheGivenScopes`: `PlayServicesTokenSource(fake).token("family@example.com", listOf("s1"))` makes `fake.asked == listOf("family@example.com" to listOf("s1"))`. The moved tests (`NeedsUser` → `NeedsSignInException`, and the rest) keep their assertions.
  - `GoogleApiTest.theCalendarAsksOnlyForTheCalendarScopes`: after one `send`, every `FakeTokenSource.asked` entry's scopes equal `CALENDAR_SCOPES`.
  - `GoogleConnectFlowTest.connectingAsksOnlyForTheCalendarScopes`: every `FakeAuthorizer.asked` entry's scopes equal `CALENDAR_SCOPES`, on both the first ask and the silent re-ask for the email.
  - `GoogleCalendarAccountsTest.listsTheGoogleConnectionsAccounts`: with Google connections for `family@example.com` and `sam@example.com` and one sample connection, `connected.first() == listOf("family@example.com", "sam@example.com")`; removing the first emits `listOf("sam@example.com")`.
  - `CalendarContractTest.theCalendarsExceptionsAreTheSharedOnes` (**pin** once compiled): `NeedsSignInException("x") is uk.co.siland.culvery.core.plugin.NeedsSignInException`.

  Run `./gradlew :core:google:testDebugUnitTest :provider:calendar-google:testDebugUnitTest :capability:calendar:testDebugUnitTest`. Expected: FAIL (doesn't compile).
- [ ] **Step 2: Implement** per the Interfaces. `PlayServicesAuthorizer.authorize` builds its request with `setRequestedScopes(scopes.map(::Scope))`; nothing else in it changes. `GoogleModule` is `@InstallIn(SingletonComponent::class)` with the `TokenSource`, `Authorizer` and `PlayServicesCheck` binds.
- [ ] **Step 3: The gate.** Run `./gradlew testDebugUnitTest verifyRoborazziDebug`. Expected: PASS, no screenshot changes (`git status --porcelain -- "*.png"` is empty).
- [ ] **Step 4: Commit.**
```bash
git add settings.gradle.kts app/build.gradle.kts README.md core/google core/plugin capability/calendar provider/calendar-google
git commit -m "Move the Google sign-in to a core module that asks for scopes per call, so the calendar and the shopping list can share the account"
```

---

### Task 2: `:capability:shopping`: the module, the seam, `shopping.db`, the store (spec §4.1, §4.2, §4.5, §4.6; rulings 5, 7, 8, 26)

**Review:** opus (files and data).

**Files:**
- Create `capability/shopping/build.gradle.kts` as `capability/door`'s, plus `implementation(project(":core:access"))` and `java-test-fixtures`. The connection's config is stored as `org.json`, as `CalendarStore` does. Register in `settings.gradle.kts`, `app/build.gradle.kts` and README.
- Create, in `capability/shopping/src/main/java/uk/co/siland/culvery/capability/shopping/`: `ShoppingContract.kt`, `db/ShoppingDatabase.kt` (entities, `ShoppingDao`, version 1, schema exported to `capability/shopping/schemas/`), `ShoppingStore.kt`, `di/ShoppingModule.kt` (`@Multibinds Set<ShoppingProvider>`, the database as `"shopping.db"`; no `Capability` yet).
- Create `core/plugin/.../TextKey.kt`; `capability/calendar/.../RememberedTitles.kt`'s `titleKey` becomes `= textKey(title)`.
- Create `capability/shopping/src/testFixtures/.../test/FakeShoppingProvider.kt`.
- Test: `core/plugin/src/test/.../TextKeyTest.kt`, `capability/shopping/src/test/.../ShoppingStoreTest.kt`. The calendar's `RememberedTitlesTest` stays green (**pin**).

**Interfaces:**
- Produces (`core/plugin`): `fun textKey(text: String): String` — trimmed, inner whitespace collapsed to one space, NFD with marks removed, lower case (`Locale.ROOT`).
- Produces (`ShoppingContract.kt`):
  - `data class ShoppingItem(val id: String, val text: String, val changedAt: Instant)` — `toString` leaves out `text`.
  - `sealed interface ProviderAccounts { data class Ready(val accounts: List<String>) : ProviderAccounts; data class Blocked(val line: String) : ProviderAccounts }`.
  - ```kotlin
    interface ShoppingProvider {
        val descriptor: ProviderDescriptor
        /** Which accounts it can be turned on for, or why it can't yet (10 §4.10). Empty Ready means no account to pick. */
        val accounts: Flow<ProviderAccounts>
        /** The fix shown when [AccountGoneException] is thrown. */
        val accountGoneLine: String
        @Composable
        fun ConnectScreen(account: String?, existing: Connection?, onConnected: (Connection) -> Unit, onCancel: () -> Unit)
        suspend fun remakeList(conn: Connection): Connection
        suspend fun items(conn: Connection): List<ShoppingItem>
        suspend fun add(conn: Connection, text: String, clientKey: String): ShoppingItem
        suspend fun tick(conn: Connection, id: String)
        suspend fun untick(conn: Connection, id: String)
        suspend fun history(conn: Connection, since: Instant): List<ShoppingItem>
    }
    ```
    Its KDoc states the contract: main-safe and cancellable; throws only `NeedsSignInException`, `UnreachableException` (and `OfflineException`), `ListGoneException`, `AccountGoneException`, `ItemGoneException` (tick/untick only), `ShoppingRejectedException`; `items` is the open items in the provider's order; `history` is items ticked since [since]; nothing deletes.
  - Exceptions: `class ListGoneException`, `class AccountGoneException`, `class ItemGoneException`, `class ShoppingRejectedException`, each `(message: String? = null, cause: Throwable? = null) : Exception`. Messages never hold item text.
  - `const val SHOPPING_CONFIG_ACCOUNT = "account"`; `const val SHOPPING_ID = "shopping"`.
  - `enum class QueuedKind { ADD, TICK, UNTICK }`; `enum class ShoppingHealth { OK, CANT_REACH, NEEDS_PERMISSION, LIST_GONE, ACCOUNT_GONE }`.
- Produces (`db/`): entities `ConnectionEntity(slot: Int = 0 PK, id, providerId, label, configJson, health: String, lastReadMillis: Long?, lastHistoryMillis: Long?, seeded: Boolean)`, `ItemEntity(id PK, text, position: Int)`, `QueuedEntity(seq: Long PK autoGenerate, kind: String, clientKey, itemId: String?, text, madeAtMillis, attempts: Int, nextAtMillis: Long)`, `LearnedEntity(key PK, text, count: Int, lastMillis)`, `SeenEntity(id PK, atMillis)`.
- Produces (`ShoppingStore`, `@Singleton`, the only writer of `shopping.db`):
  - `val connection: Flow<StoredShopping?>`; `data class StoredShopping(val connection: Connection, val health: ShoppingHealth, val lastReadMillis: Long?, val lastHistoryMillis: Long?, val seeded: Boolean)`.
  - `suspend fun connect(connection: Connection)` (replaces any, health OK, `seeded = false`); `suspend fun replaceConnection(connection: Connection)` (same id, keeps the rest); `suspend fun stop()` (deletes the connection, items and queue in one transaction; keeps `learned` and `seen`).
  - `val items: Flow<List<ShoppingItem>>` (by `position`); `suspend fun saveRead(items: List<ShoppingItem>, health: ShoppingHealth, atMillis: Long)` (replaces all items in one transaction); `suspend fun setHealth(health: ShoppingHealth)`; `suspend fun setHistoryRead(atMillis: Long)`; `suspend fun markSeeded()`.
  - `val queue: Flow<List<QueuedEntity>>` (by `seq`); `suspend fun queueNow(): List<QueuedEntity>`; `suspend fun enqueue(kind: QueuedKind, clientKey: String, itemId: String?, text: String, atMillis: Long): Long`; `suspend fun sent(seq: Long, itemId: String?)` (deletes the row; with an id from an ADD, sets `itemId` on every later row with the same `clientKey`); `suspend fun failed(seq: Long, nextAtMillis: Long)` (attempts + 1); `suspend fun drop(seq: Long)`.
  - `FakeShoppingProvider(descriptor = …)`: an in-memory open list and history; `items`, `add` (id `"g1"`, `"g2"`… inserted first), `tick`/`untick` move items between them; scriptable `failNext: Exception?` and `failAll: Exception?`; `calls: List<String>` such as `"add"`, `"tick g1"`; `ConnectScreen` calls `onConnected` at once with `Connection("c1", descriptor.id, descriptor.displayName, config)`, where `config` holds `SHOPPING_CONFIG_ACCOUNT` only when `account` isn't null.

- [ ] **Step 1: The failing tests.**
  - `TextKeyTest`: `textKey("  Crème   Fraîche ") == "creme fraiche"`; `textKey("MILK") == textKey("milk")`; `textKey("\tEggs\n") == "eggs"`; `textKey("   ") == ""`.
  - `ShoppingStoreTest` (Robolectric, an in-memory Room database):
    - `aConnectionIsKeptAndReplaced`: `connect(c1)` then `connect(c2)` leaves only `c2`; `replaceConnection(c2 with a new config)` keeps `lastReadMillis`.
    - `aReadReplacesTheItemsInOrder`: `saveRead([Milk g1, Bread g2])` then `saveRead([Eggs g3, Milk g1])` gives `items == [Eggs, Milk]`.
    - `theQueueKeepsItsOrderAndAnAddsIdReachesLaterRows`: enqueue ADD(k1, "Milk"), TICK(k1, null), ADD(k2, "Bread"); `sent(seq1, "g9")` leaves `[TICK k1 itemId g9, ADD k2]`.
    - `aFailureCountsAndWaits`: `failed(seq, 90_000)` gives `attempts == 1`, `nextAtMillis == 90_000`.
    - `stopKeepsWhatWasLearned`: with a connection, items, queue, a learned row and a seen row, `stop()` leaves only the learned and seen rows.
    - `theQueueSurvivesANewDatabaseInstance`: on a file database under the test's `TemporaryFolder`, enqueue, close, reopen: the row is there with its text.
    - `anItemsToStringHasNoText`: `ShoppingItem("g1", "Milk", t).toString()` doesn't contain "Milk".

  Run `./gradlew :core:plugin:testDebugUnitTest --tests '*TextKey*' :capability:calendar:testDebugUnitTest --tests '*RememberedTitles*' :capability:shopping:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: The gate**, then commit (the exported schema JSON included).
```bash
git add settings.gradle.kts app/build.gradle.kts README.md core/plugin capability/calendar capability/shopping
git commit -m "Add the shopping capability's provider seam and its database: the connection, the last read, the queue and what's learned"
```

---

### Task 3: Suggestions and favourites (spec §4.7; rulings 11–13)

**Review:** sonnet.

**Files:**
- Create `capability/shopping/.../ShoppingLearning.kt`.
- Modify `ShoppingStore.kt`, `db/ShoppingDatabase.kt` (the DAO's learned and seen queries).
- Test: `ShoppingLearningTest.kt`, `SuggestTest.kt`.

**Interfaces:**
- Consumes: `ShoppingStore`, `textKey`, `WallClock`.
- Produces:
  - `data class Learned(val key: String, val text: String, val count: Int, val lastMillis: Long)`.
  - `internal fun suggest(typed: String, learned: List<Learned>, onList: Set<String>): List<Learned>` and `internal fun favourites(learned: List<Learned>, onList: Set<String>, suggested: Set<String>): List<Learned>` (pure; rulings 12–13; `onList` and `suggested` are keys).
  - `internal fun betterText(old: String, new: String): String` (ruling 11).
  - `class ShoppingLearning @Inject constructor(store: ShoppingStore, clock: WallClock)`:
    - `val learned: Flow<List<Learned>>`;
    - `suspend fun added(text: String)` (count + 1, last now);
    - `suspend fun ticked(text: String)` (last now; a text never learned is learned with count 1);
    - `suspend fun seen(items: List<ShoppingItem>)` (each id not in `seen`: count + 1 for its key, mark seen);
    - `suspend fun markSeen(id: String)`;
    - `suspend fun pruneIfDue()` (at most once a day; removes learned and seen rows unused for 12 months: `LEARNED_KEEP_MONTHS = 12`).

- [ ] **Step 1: The failing tests.**
  - `SuggestTest` (pure):
    - `oneLetterSuggests`: learned Butter (5), Bread (9), Bin bags (2), Eggs (7); `suggest("b", …)` keys are `["bread", "butter", "bin bags"]`.
    - `prefixMatchesComeBeforeWordStarts`: learned Tea bags (9), Bananas (1); `suggest("ba", …)` is `["bananas", "tea bags"]`.
    - `itemsOnTheListNeverShow`: with `onList = {"bread"}`, `suggest("b", …)` has no `"bread"`; `favourites` has none either.
    - `tiesGoToTheLatestThenTheKey`: two count-3 items: the later `lastMillis` first; equal both: by key.
    - `nothingTypedSuggestsNothing`: `suggest("  ", …)` is empty.
    - `favouritesLeaveOutSuggestions`: `favourites(…, suggested = {"butter"})` has no `"butter"`, and is by count.
    - `accentsAndCaseMatch`: learned "Crème fraîche"; `suggest("CRE", …)` finds it.
  - `ShoppingLearningTest` (Room in memory, a fake clock):
    - `anAddCountsAndTheBestCasingWins`: `added("milk")`, `added("Milk")`, `added("milk")` gives one row: count 3, text "Milk". `betterText("Milk", "MILK") == "MILK"`; `betterText("Milk", "milk") == "Milk"`.
    - `anItemSeenTwiceCountsOnce`: `seen([g1 Milk])` twice gives count 1; `seen([g2 Milk])` gives 2.
    - `aWallAddMarkedSeenIsNotCountedAgain`: `added("Milk")`, `markSeen("g1")`, `seen([g1 Milk])` gives count 1.
    - `aTickOnlyUpdatesTheDate`: `ticked("Milk")` on a count-2 row keeps 2 and moves `lastMillis`.
    - `aYearUnusedIsForgotten`: a row last used 12 months and a day ago goes on `pruneIfDue()`; one 11 months old stays; a second call within 24 h does nothing (the DAO is not called: count rows deleted).

  Run `./gradlew :capability:shopping:testDebugUnitTest --tests '*Suggest*' --tests '*Learning*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per rulings 11–13.
- [ ] **Step 3: The gate**, then commit.
```bash
git add capability/shopping
git commit -m "Learn shopping items from what the house adds and ticks, and suggest them as you type and as favourites"
```

---

### Task 4: The queue, the drain, the checks, add, tick and Undo (spec §4.4–§4.6, §4.11; rulings 5, 6, 9, 10, 18, 19, 26; Review Focus 1–3, 5)

**Review:** opus. Offline, races, restarts: the anti-goals live here.

**Files:**
- Create in `capability/shopping/.../`: `ShoppingSync.kt` (`ShoppingLock`, `ShoppingSync`, `healthOf`), `ShoppingDrainLoop.kt`, `ShoppingChecks.kt`, `ShoppingActions.kt`, `ShoppingView.kt`, `ShoppingWords.kt`.
- Modify `di/ShoppingModule.kt`: `@IntoSet Startable` for `ShoppingDrainLoop` and `ShoppingChecks`.
- Test: `ShoppingSyncTest.kt`, `ShoppingActionsTest.kt`, `ShoppingViewTest.kt`, `ShoppingChecksTest.kt`, `ShoppingLogTest.kt`.

**Interfaces:**
- Consumes: Tasks 2–3; `AlertHost.inFront`, `Toaster`, `ToastAction`, `TOAST_WITH_ACTION_MS`, `WallClock`, `@ApplicationScope`, `attempt`, `plural` (`core/ui`), `newClientKey()`-style keys (write `newShoppingKey()` here: a random UUID without dashes, since the calendar's can't be imported).
- Produces:
  - `const val SHOPPING_CHECK_MS = 60_000L`; `val SHOPPING_BACKOFF_MS = listOf(30_000L, 60_000L, 120_000L, 300_000L)`; `const val ADD_MAX_CHARS = 100`; `const val ADDS_PER_SECOND = 3`; `const val FLASH_MS = 1_200L`; `const val HISTORY_OVERLAP_MS = 10 * 60_000L`; `const val SEED_MONTHS = 12L`.
  - `internal fun healthOf(e: Exception): ShoppingHealth`: `NeedsSignIn` → `NEEDS_PERMISSION`; `Unreachable` (and `Offline`) → `CANT_REACH`; `ListGone` → `LIST_GONE`; `AccountGone` → `ACCOUNT_GONE`.
  - `@Singleton class ShoppingLock` (`suspend fun <T> withLock(block: suspend () -> T): T`).
  - `@Singleton class ShoppingSync @Inject constructor(store, providers: Set<@JvmSuppressWildcards ShoppingProvider>, learning, lock, clock)`:
    - `suspend fun drain(): Long?` — sends the queue in order under the lock until it is empty (null) or the head waits (its `nextAtMillis`). Before the first ADD of a pass it reads `items` once; an ADD whose key matches an open item is `sent` with that item's id and `markSeen`; otherwise `provider.add`, then `sent(seq, id)` and `markSeen(id)`. A TICK or UNTICK without an `itemId` waits for its ADD. Failures per ruling 26, with `failed(seq, now + backoff(attempts + 1))` and `setHealth(healthOf(e))`.
    - `suspend fun check()` — under the lock: drain; `items`; `learning.seen(items)`; `history(since = (lastHistoryMillis ?: now) − HISTORY_OVERLAP_MS)` (on the first check after connecting, `since = now − SEED_MONTHS`, then `markSeeded()`), each ticked item through `learning.seen` and `ticked`; `saveRead(items, OK, now)`; `pruneIfDue()`. A failure sets the health and keeps the last read.
  - `@Singleton class ShoppingDrainLoop : Startable` — drains on start, on every `wake()` and when the head's time comes. `fun wake()`.
  - `@Singleton class ShoppingChecks : Startable` — as `MusicChecks`: `fun watch(checkNow: Boolean = true): () -> Unit`; checks every `SHOPPING_CHECK_MS` while a connection is stored, `inFront` and watchers > 0, at once on each `watch(checkNow = true)`. `internal val watching: Int`.
  - `data class ShownItem(val key: String, val text: String, val ticked: Boolean, val queued: Boolean)` — `key` is the item id, or `"q:" + clientKey` while its add is queued; `toString` leaves out `text`.
  - `data class ShoppingState(val items: List<ShownItem>, val open: Int, val health: ShoppingHealth, val lastReadMillis: Long?, val service: String, val problem: String?, val flashing: String?)` — `open` counts rows not ticked; `service` is the provider's `displayName`; `problem` is the line for the health (`ShoppingWords`), null when OK.
  - `internal fun shown(read: List<ShoppingItem>, queue: List<QueuedEntity>, undoable: Ticked?): List<ShownItem>` (pure): queued ADDs not yet sent, newest first; then the read in order; a row with a queued TICK is hidden unless it is `undoable`'s, which shows ticked; a queued UNTICK whose item isn't in the read shows at the top; `undoable`'s row, when the read no longer has it, shows ticked at its old index.
  - `internal data class Ticked(val key: String, val itemId: String?, val clientKey: String, val text: String, val index: Int, val atMillis: Long)`.
  - `@Singleton class ShoppingView @Inject constructor(store, actions, providers, clock, scope)`: `val state: StateFlow<ShoppingState?>` (null until the store answers or with no connection).
  - `@Singleton class ShoppingActions @Inject constructor(store, learning, loop: ShoppingDrainLoop, toaster, clock, scope)`:
    - `suspend fun add(text: String): AddResult` — `sealed interface AddResult { data object Added; data class AlreadyThere(val key: String); data object Empty; data object TooFast }`; under a mutex: trims and caps at `ADD_MAX_CHARS`; empty → `Empty`; a key already open on the shown list (read or queued) → `AlreadyThere` and `flashing = key` for `FLASH_MS`; more than `ADDS_PER_SECOND` in the last second → `TooFast`; else enqueue ADD, `learning.added`, `loop.wake()`.
    - `fun tick(row: ShownItem)` — enqueue TICK (with the row's item id, or its client key when queued), `learning.ticked`, `undoable = Ticked(…)`, toast "`Ticked {text}`" with `ToastAction("Undo") { undo(it) }`, `loop.wake()`.
    - `fun undo(ticked: Ticked)` — if its TICK is still queued, `drop` it; else enqueue UNTICK. Only the latest tick is undoable; after `TOAST_WITH_ACTION_MS`, `undoable` clears.
    - `internal val undoable: StateFlow<Ticked?>`, `internal val flashing: StateFlow<String?>`.
  - `ShoppingWords.kt`: `itemsLine(open: Int, service: String, lastReadMillis: Long?, nowMillis: Long): String` ("22 items · from Google Tasks, just now"; "1 item · …"; "0 items · not read from Google Tasks yet"), using `core/ui`'s ago words (move the "just now / n minutes ago / n hours ago / n days ago" part of the calendar's `updatedLabel` into `core/ui` as `agoWords(thenMillis, nowMillis)` and make `updatedLabel` call it); `problemLine(health, provider): String?` for the copy in Global Constraints (`ACCOUNT_GONE` uses `provider.accountGoneLine`); `ticked(text)`; `UNDO = "Undo"`.

- [ ] **Step 1: The failing tests.** All on a `StandardTestDispatcher`, an in-memory database, `FakeShoppingProvider` and a fake clock.
  - **`ShoppingSyncTest`:**
    - `aDrainSendsInOrder`: ADD Milk, ADD Bread, TICK (Milk's key) gives provider calls `["items", "add", "add", "tick g1"]` and an empty queue.
    - `aRetriedAddThatAlreadyLandedAddsNothing` (Review Focus 2): the provider holds open "milk " (g7); a queued ADD "Milk" drains with no `"add"` call and `sent` with `g7`; the provider still has one Milk.
    - `aRetriedAddIsLearnedOnce` (Review Focus 2): the add is learned (count 1) when queued; after it drains into `g7` and a check reads `g7`, the count is still 1.
    - `anOfflineAddAndTickSurviveARestartAndDrainInOrder` (Review Focus 3): with `failAll = OfflineException()`, add Milk and tick it; the queue has ADD then TICK, the head's `attempts == 1` and `nextAtMillis == now + 30_000`; a new `ShoppingSync` over a reopened file database, `failAll = null`, `drain()` gives calls `["items", "add", "tick g1"]`.
    - `theBackoffStepsAndStaysAtFiveMinutes`: five failures in a row wait 30 s, 60 s, 120 s, 300 s, 300 s.
    - `anAddIsNeverDropped`: `failAll = ShoppingRejectedException()`; after 50 failed drains over 3 virtual days the ADD is still queued.
    - `aTickWhoseItemHasGoneIsDropped`: `failNext = ItemGoneException()` on a TICK: the row goes and the next row is sent.
    - `aDeletedListWaitsWithTheQueueKept` (Review Focus 5): `failAll = ListGoneException()`: health `LIST_GONE`, both queued rows kept, the last read kept.
    - `needsPermissionAndAccountGoneKeepTheQueue`: `NeedsSignInException` → `NEEDS_PERMISSION`; `AccountGoneException` → `ACCOUNT_GONE`; the queue is untouched; a later success sets `OK`.
    - `aCheckDrainsBeforeItReads`: with an ADD queued, `check()` calls `add` before `items` (the first `items` is the drain's), and the saved read holds the new item.
    - `theFirstCheckSeedsFromAYearOfHistory`: the first `history` call's `since` is now − 12 months; the next is the last history read − 10 min; history items are learned.
    - `aTickQueuedBeforeItsAddWaits`: TICK with no item id and its ADD failing: no `tick` call is made.
  - **`ShoppingActionsTest`:**
    - `twoAddsOfTheSameTextAtOnceQueueOne` (Review Focus 1): `add("Milk")` and `add("milk")` launched together give one queued ADD and results `{Added, AlreadyThere}`.
    - `addingTextAlreadyOnTheListFlashesIt` (Review Focus 1): with Milk (g1) read, `add(" MILK ")` returns `AlreadyThere("g1")`; `flashing == "g1"`, and null at `FLASH_MS + 1`.
    - `emptyTextAddsNothing`: `add("   ")` is `Empty`; nothing queued.
    - `textIsCappedAtOneHundred`: a 150-character add queues the first 100.
    - `aFourthAddInASecondIsRefused`: four different adds within 999 ms: the fourth is `TooFast`; one at 1,001 ms is `Added`.
    - `aTickShowsTheUndoToast`: the recording toaster has "Ticked Milk" with action label "Undo".
    - `undoBeforeTheTickIsSentDropsIt` (Review Focus 3): offline, tick then `undo`: the queue has no TICK and no UNTICK.
    - `undoAfterTheTickIsSentUnticks` (Review Focus 3): tick, drain, `undo`: an UNTICK is queued, and after the drain the provider has Milk open.
    - `onlyTheLatestTickIsUndoable`: tick Milk then Bread: `undoable` is Bread's; at `TOAST_WITH_ACTION_MS + 1` it is null.
  - **`ShoppingViewTest`** (the pure `shown`):
    - `queuedAddsShowFirstNewestFirst`: read [Bread g2], queued ADD Milk then ADD Eggs: keys `["q:k2", "q:k1", "g2"]`, the first two `queued`.
    - `aQueuedTickHidesItsRowUnlessUndoable`: read [Milk g1, Bread g2], TICK g1: [Bread]; with `undoable` Milk: [Milk ticked, Bread].
    - `anUndoableRowStaysAtItsPlaceAfterTheRead`: read [Bread g2], `undoable` Milk at index 0: [Milk ticked, Bread].
    - `aPhonesDuplicateShowsAsTwoRows` (Review Focus 4): read [Milk g1, Milk g5]: two rows, keys g1 and g5.
    - `theCountLeavesOutTickedRows`: `open == 1` for [Milk ticked, Bread].
  - **`ShoppingChecksTest`** (as `MusicChecksTest`): `watchingChecksAtOnceThenEveryMinute` (checks at 0, 60 s, 120 s; none at 59,999 ms after the first); `nothingChecksOutOfFrontOrUnwatched`; `aSecondWatchWithCheckNowChecksAtOnce`; `stoppingTwiceCountsOnce`.
  - **`ShoppingWordsTest`:** `itemsLine(22, "Google Tasks", now − 30_000, now) == "22 items · from Google Tasks, just now"`; `itemsLine(1, …, now − 300_000, now) == "1 item · from Google Tasks, 5 minutes ago"`; `itemsLine(0, …, null, now) == "0 items · not read from Google Tasks yet"`; each health's `problemLine` is the Global Constraints copy. `UpdatedLabelTest` in the calendar stays green (**pin**).
  - **`ShoppingLogTest.noLogLineHoldsItemText`** (privacy): under `ShadowLog`, run a drain where the provider throws each exception type in turn while adding "Bin bags" and ticking "Tea bags": no logged message contains "Bin bags", "bin bags" or "Tea bags", and at least one line was logged.

  Run `./gradlew :capability:shopping:testDebugUnitTest :capability:calendar:testDebugUnitTest --tests '*UpdatedLabel*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and rulings 5, 6, 9, 10, 18, 19, 26. Log lines name the step and the exception's class only (`"Shopping drain: the add waits (${e::class.simpleName})"`); LogHygieneTest scans them.
- [ ] **Step 3: The gate**, then commit.
```bash
git add capability/shopping capability/calendar core/ui
git commit -m "Queue shopping adds and ticks on the tablet, send them in order when Google answers, check the list every minute while it shows, and undo the latest tick"
```

---

### Task 5: `:provider:shopping-google-tasks`: the Tasks client and connecting (spec §4.3, §4.10; rulings 1, 3, 8, 25; Review Focus 4)

**Review:** opus. Auth, Google's answers, and the log.

**Files:**
- Create `provider/shopping-google-tasks/build.gradle.kts` (as `provider/calendar-google`'s, with `:capability:shopping`, `:core:google`, `:core:net`, `:core:plugin`, `:core:ui`; tests with `testFixtures(project(":core:google"))`, `testFixtures(project(":capability:shopping"))`, MockWebServer). Register it in `settings.gradle.kts`, `app/build.gradle.kts`, README.
- Create in `provider/shopping-google-tasks/src/main/java/uk/co/siland/culvery/provider/shopping_google_tasks/`: `TasksJson.kt` (DTOs), `TasksApi.kt`, `TasksConnectFlow.kt`, `GoogleTasksProvider.kt`, `di/GoogleTasksModule.kt` (`@IntoSet ShoppingProvider`; the API as `GoogleCalendarModule` provides `GoogleApi`, its client private).
- Create `src/test/resources/tasks/`: `tasks_page1.json`, `tasks_page2.json`, `tasks_history.json`, `tasklists.json`, `tasklists_none.json`, `task_inserted.json`, `error_404.json`, `error_scope.json` — Google's shapes, sample items only (Milk, Bread, Eggs, Butter, Bananas), the account `family@example.com`.
- Modify `docs/setup/google-calendar.md`: a "Shopping (Google Tasks)" section: enable the Google Tasks API in the same Cloud project, and add the `…/auth/tasks` scope to the OAuth consent screen. The app stays unverified (the culvery-workflow memory).
- Test: `TasksJsonTest.kt`, `TasksApiTest.kt`, `GoogleTasksProviderTest.kt`, `TasksConnectFlowTest.kt`, `GoogleTasksLogTest.kt`.

**Interfaces:**
- Consumes: `Authorizer`, `TokenSource`, `GoogleAccounts`, `PlayServicesCheck` (Task 1); the seam (Task 2); `core/net`'s `Call.await(maxBytes)`; `Toaster`; `LocalShellNavigator`.
- Produces:
  - `const val TASKS_PROVIDER_ID = "shopping.google-tasks"` (stored: never change); `internal const val TASKS_DISPLAY_NAME = "Google Tasks"`; `val TASKS_SCOPES = listOf("https://www.googleapis.com/auth/tasks")`; `const val TASKS_BASE_URL = "https://tasks.googleapis.com/tasks/v1/"`; `internal const val SHOPPING_LIST_TITLE = "Shopping"`; `internal const val CONFIG_LIST = "list"`; `internal const val TASKS_MAX_BYTES = 2L * 1024 * 1024`.
  - DTOs (`TasksJson`, `ignoreUnknownKeys`, `explicitNulls = false`): `TaskListsPage(items: List<TaskListDto> = emptyList(), nextPageToken: String?)`, `TaskListDto(id, title: String?)`, `TasksPage(items: List<TaskDto> = emptyList(), nextPageToken: String?)`, `TaskDto(id, title: String?, status: String?, updated: String?, completed: String?, parent: String?, position: String?, deleted: Boolean?, hidden: Boolean?)`, `NewTask(title: String)`, `TaskStatus(status: String)`, `NewTaskList(title: String)`. `TaskDto.toString` leaves out `title`.
  - `@JvmInline internal value class TasksCall(val label: String)` with `TASKLISTS_LIST = "tasklists.list"`, `TASKLISTS_INSERT = "tasklists.insert"`, `TASKS_LIST = "tasks.list"`, `TASKS_HISTORY = "tasks.list completed"`, `TASKS_INSERT = "tasks.insert"`, `TASKS_PATCH = "tasks.patch"`.
  - `class TasksApi(baseUrl: HttpUrl, tokens: TokenSource, client: Lazy<OkHttpClient>)` — `GoogleApi`'s rules for this API: a token per call with `TASKS_SCOPES`; a 401 invalidates and retries once, a second 401 or a 403 `insufficientPermissions`/`ACCESS_TOKEN_SCOPE_INSUFFICIENT` throws `NeedsSignInException`; 429, a rate-limit 403, 5xx and `IOException` throw `UnreachableException` (an `UnknownHostException` throws `OfflineException`); other answers return. Item text travels only in request bodies. `internal suspend fun send(account: String, method: String, url: HttpUrl, body: JsonElement? = null): TasksAnswer`.
  - `@Singleton class GoogleTasksProvider @Inject constructor(api: TasksApi, authorizer: Authorizer, accounts: GoogleAccounts, playServices: PlayServicesCheck, toaster: Toaster) : ShoppingProvider`:
    - `descriptor = ProviderDescriptor(TASKS_PROVIDER_ID, "Google Tasks", Icons.SHOPPING_CART, setOf(READ, WRITE), connectHint = "Allow Google Tasks for the family’s Google account.")`;
    - `accounts`: `GoogleAccounts.connected` mapped to `Ready(list)`, or `Blocked("Connect Google Calendar first. Shopping uses the same account.")` when empty;
    - `accountGoneLine = "Shopping’s Google account was disconnected. Connect it again in Settings › Calendars."`;
    - every call first checks the connection's account is in `connected.first()`, else `AccountGoneException`;
    - `items`: `GET lists/{list}/tasks?showCompleted=false&showHidden=false&maxResults=100&fields=items(id,title,status,updated,parent,position),nextPageToken`, every page, ordered per ruling 25; a 404 → `ListGoneException`;
    - `history(since)`: `showCompleted=true&showHidden=true&completedMin={RFC 3339 UTC}&maxResults=100`, every page, only `status == "completed"`;
    - `add`: `POST lists/{list}/tasks` with `{"title": text}` and no `previous` or `parent`; 404 → `ListGoneException`; another 4xx → `ShoppingRejectedException`;
    - `tick` / `untick`: `PATCH lists/{list}/tasks/{id}` with `{"status":"completed"}` / `{"status":"needsAction"}`; 404 or 410 → `ItemGoneException`;
    - `remakeList`: find or make the list (as connecting), returning the connection with the new `CONFIG_LIST`;
    - `ConnectScreen`: as `GoogleCalendarProvider.ConnectScreen` (leave pinning, launch the screens, return to pinning, a cancel says nothing), driving `TasksConnectFlow`.
  - `internal class TasksConnectFlow(authorizer, api, toaster, playServices)`: `start(account: String, existing: Connection?): ConnectStep` and `afterScreens(account, existing, data): ConnectStep` (its own `ConnectStep`, as the calendar's). Granted without `TASKS_SCOPES` → toast "Google Tasks wasn’t allowed. Try again and tick Google Tasks." and stop. Then `GET users/@me/lists` (every page): the first list whose `textKey(title) == textKey("Shopping")`, else `POST users/@me/lists` `{"title":"Shopping"}`. Done: `Connection(existing?.id ?: UUID, TASKS_PROVIDER_ID, "Google Tasks", mapOf(SHOPPING_CONFIG_ACCOUNT to account, CONFIG_LIST to id))`. Other failures toast `couldNotConnect`'s words for "Google Tasks" (defined here; the calendar's can't be imported).
  - Log lines: `"${call.label}: Google Tasks answered ${answer.code} (${answer.reason})"` and `"${call.label}: … (${e::class.simpleName})"` only; Google's message is never logged (it can quote a title).

- [ ] **Step 1: The failing tests.**
  - `TasksJsonTest`: each resource file decodes; `tasks_page1.json` gives Milk, Bread and a blank-titled task; an unknown field is ignored; `TaskDto("t1", "Milk", …).toString()` has no "Milk"; `NewTask("Milk")` encodes as `{"title":"Milk"}`.
  - `GoogleTasksProviderTest` (MockWebServer, `FakeTokenSource`, `FakeGoogleAccounts(listOf("family@example.com"))`):
    - `itemsAreTheOpenTasksInGoogleOrder` (Review Focus 4): two pages (the second asked with `pageToken`); the request's query has `showCompleted=false`, `showHidden=false`, `maxResults=100`; tasks at positions "00000000000000000002", "00000000000000000001", a subtask of the second, and a blank title come back as [second, its subtask, first], the blank skipped; each `changedAt` is the task's `updated`.
    - `historyIsWhatWasTickedSince`: the query has `showCompleted=true`, `showHidden=true`, `completedMin=2026-10-16T10:00:00Z`; only completed tasks return.
    - `anAddPostsOnlyTheTitle`: method POST, path `/tasks/v1/lists/L1/tasks`, body `{"title":"Milk"}`, no `previous` in the query; returns the inserted task's id.
    - `aTickAndAnUntickPatchTheStatus`: PATCH `/tasks/v1/lists/L1/tasks/t1` with `{"status":"completed"}`, then `{"status":"needsAction"}`; no DELETE request is ever made (assert over every recorded request in the class).
    - `aMissingListIsListGone`: 404 on `tasks.list` and on `tasks.insert` → `ListGoneException`.
    - `aMissingItemIsItemGone`: 404 on PATCH → `ItemGoneException`.
    - `googlesRefusalsMapAsTheCalendarsDo`: 401 twice → `NeedsSignInException` (and `invalidate` called once); 403 `insufficientPermissions` → `NeedsSignInException`; 429, 503 and a 403 `rateLimitExceeded` → `UnreachableException`; a dropped connection → `UnreachableException`; 400 on insert → `ShoppingRejectedException`.
    - `anAccountNoLongerConnectedIsAccountGone`: with `connected` empty, `items` throws `AccountGoneException` and makes no request.
    - `everyTokenIsForTheTasksScope`: every `FakeTokenSource.asked` scopes equal `TASKS_SCOPES`.
    - `noAccountsBlocksWithTheCalendarLine`: `accounts.first()` is `Blocked("Connect Google Calendar first. Shopping uses the same account.")`; with two, `Ready(["family@example.com", "sam@example.com"])`.
  - `TasksConnectFlowTest` (`FakeAuthorizer`, `RecordingToaster`):
    - `aGrantFindsTheShoppingList`: `tasklists.json` holds "Groceries" and "shopping": the connection's `CONFIG_LIST` is the "shopping" list's id; no POST.
    - `noShoppingListMakesOne`: `tasklists_none.json`: one POST `users/@me/lists` with `{"title":"Shopping"}`; the new id is stored.
    - `withoutTheTasksScopeNothingIsStored`: a grant of only calendar scopes stops with the "wasn’t allowed" toast.
    - `screensAreAskedForFirst`: `NeedsUser` gives `ShowScreens`; a cancel after them stops with no toast.
    - `theAccountAskedForIsTheOneChosen`: `FakeAuthorizer.asked == listOf("sam@example.com" to TASKS_SCOPES)`.
  - `GoogleTasksLogTest.noLogLineHoldsItemText`: under `ShadowLog`, a 400 whose Google message is "Invalid title: Bin bags", a body that doesn't parse holding "Tea bags", and a 404 on a tick: no message contains "Bin bags" or "Tea bags"; each logged line names a `TasksCall` label.

  Run `./gradlew :provider:shopping-google-tasks:testDebugUnitTest`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces.
- [ ] **Step 3: The gate**, then commit.
```bash
git add settings.gradle.kts app/build.gradle.kts README.md provider/shopping-google-tasks docs/setup/google-calendar.md
git commit -m "Keep the shopping list in Google Tasks: read, add and tick on a list called Shopping, found or made when it is turned on"
```

---

### Task 6: Home: the utility row, Lights and Music side by side (spec §4.9 and its rulings 8 and 11; ruling 17)

**Review:** sonnet.

**Files:**
- Modify `core/plugin/.../HomeCard.kt` (`UTILITY`, the KDoc), `HomeCardPlacer.kt`, `app/.../shell/ui/HomeGrid.kt`, `app/.../shell/ui/ShellDimens.kt` (`cardRow = 212.dp`).
- Modify, only if Step 4 shows a spill: `capability/lights/.../ui/LightsCard.kt`, `capability/music/.../ui/MusicCard.kt` and their dimens.
- Test: `core/plugin/.../HomeCardPlacerTest.kt`, `app/.../shell/ui/HomeGridTest.kt`, `app/.../ShellScreenshotTest.kt`, `capability/lights/.../ui/LightsCardTest.kt`, `capability/music/.../ui/MusicCardTest.kt` and their screenshot tests.

**Interfaces:**
- Produces: `enum class HomeCardSize { TALL, WIDE, REGULAR, UTILITY }`; `data class HomeLayout(val left: HomeCard?, val right: List<HomeCard>, val utility: HomeCard? = null)`; `HomeCardPlacer.place` per ruling 17; `ShellDimens.cardRow`.

- [ ] **Step 1: The failing tests.**
  - `HomeCardPlacerTest` (`RIGHT_CARDS` stays 2; the existing tests are **pins** of "without Shopping, as today"):
    - `shoppingTakesTheTopAndLightsAndMusicShareARow`: today (TALL 100), comingUp (WIDE 50), shopping (UTILITY 40), lights (35), music (30) give `left = today`, `utility = shopping`, `right = [lights, music]`.
    - `comingUpNeverShowsWithShopping`: today, comingUp, shopping give `utility = shopping`, `right = []`.
    - `aSecondUtilityCardIsDropped`: two UTILITY cards: the higher priority stays.
  - `HomeGridTest` (1280 × 800, as the file's other tests):
    - `shoppingSitsAboveARowOfLightsAndMusic`: shopping's top equals today's top; its width 701 ±0.5 and height 359 ±1; lights and music 212 ±0.5 high, each (701 − 15) / 2 wide ±0.5, 15 apart; lights' left equals shopping's left, music's right its right; music's bottom equals today's bottom ±0.5; the gap under shopping is 15.
    - `oneOfLightsOrMusicTakesTheRowsWidth`: shopping and music only: music is 701 × 212.
    - `shoppingAloneFillsTheColumn`: shopping is 701 × 586 ±1.
    - The existing `twoRightCardsShareTheColumn` and `oneRightCardTakesTheColumn` are **pins**.
  - `LightsCardTest.theCardFitsTheSideBySideRow` and `MusicCardTest.theCardFitsTheSideBySideRow`: at `DpSize(343.dp, 212.dp)` in both themes and at font scale 1.0, every clickable node and every text node lies inside the card's bounds, no two clickables overlap, and each text's layout has no clipped line (`hasVisualOverflow` false; the room name may end in "…").
- [ ] **Step 2: Implement** the placer and the grid (with a utility card: a Column of the utility card at `weight(1f)` and, when `right` isn't empty, a Row of `height(cardRow)` whose cards each take `weight(1f)`).
- [ ] **Step 3: Run the card fit tests.** If either card spills, give it room without dropping a control: the Music art shrinks to fit the height (`BoxWithConstraints`), and the Lights card's pills share the width by weight. If a control still can't fit, stop and ask.
- [ ] **Step 4: Images.**
  - **Must not change:** every existing `home_` image (no Shopping in them), `lights_card_*`, `music_card_*`.
  - **New:** `home_shopping_dark`, `home_shopping_light` (today, Shopping as a tagged placeholder box, Lights and Music), `home_shopping_no_lights_dark` (Shopping and Music), `home_shopping_alone_dark`; `lights_card_row_dark`, `lights_card_row_light`, `music_card_row_dark`, `music_card_row_light` at 343 × 212.
  - **Check against** the handoff's 1a right column and the Lights and Music card notes.
```bash
git add core/plugin app capability/lights capability/music
git commit -m "Give Home a utility row above Lights and Music, side by side, for the shopping card"
```

---

### Task 7: The Shopping tab (spec §4.8; rulings 9, 10, 14, 15, 20, 21, 23; Review Focus 1, 4)

**Review:** sonnet.

**Files:**
- Modify `core/ui/.../Controls.kt` (`HhTextField(clearsFocusOnDone: Boolean = true)`), `Icons.kt` (`SHOPPING_CART = "shopping_cart"`), `tools/fonts/icons.txt`, the font (rebuilt).
- Create in `capability/shopping/.../`: `ShoppingFocus.kt`, `ui/ShoppingDimens.kt`, `ui/FitRow.kt`, `ui/ShoppingTab.kt` (`ShoppingTab` stateless; `ShoppingTabHost(view, actions, learning, checks, focus)`).
- Test: `core/ui` `ControlsTest.kt`, `IconFontTest.kt`; `capability/shopping/src/test/.../ui/FitRowTest.kt`, `ShoppingTabTest.kt`, `ShoppingTabScreenshotTest.kt`.

**Interfaces:**
- Consumes: `ShoppingState`, `ShownItem`, `AddResult`, `ShoppingActions`, `ShoppingChecks.watch`, `ShoppingLearning.learned`, `suggest`, `favourites` (Tasks 3–4); `HhPageHeader`, `HhTextField`, `HhButton`, `HhChoiceChip(role = Role.Button)`, `HhBottomFade`, `HhMessageLine`.
- Produces:
  - `@Singleton class ShoppingFocus @Inject constructor()`: `fun askForAdd()`; `internal fun takeAddAsk(): Boolean` (true once per ask).
  - `@Composable internal fun FitRow(gap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit)` — ruling 14.
  - `@Composable internal fun ShoppingTab(state: ShoppingState, typed: String, onTyped: (String) -> Unit, onAdd: () -> Unit, suggestions: List<Learned>, favourites: List<Learned>, onChip: (Learned) -> Unit, onTick: (ShownItem) -> Unit, focusAdd: Boolean, nowMillis: Long)`.
  - Tags: `shopping_title`, `shopping_line`, `shopping_status`, `shopping_field`, `shopping_add`, `shopping_suggest_{key}`, `shopping_favourite_{key}`, `shopping_favourites_hint`, `shopping_list`, `shopping_item_{key}`, `shopping_empty`.
  - `ShoppingDimens`: the Units table's tab rows.
- Behaviour, beyond the Units: the header is `HhPageHeader("Shopping", itemsLine(…))`; under the add card, while `problem != null`, the problem as an `HhMessageLine` (`shopping_status`); Add and Done both call `onAdd`; `Empty` keeps the box as it is, `TooFast` too; `Added` and `AlreadyThere` clear it, and the box keeps focus; the field is capped at 100 in `onTyped`; a row is one toggleable (`Role.Checkbox`, the item's name) over the circle and name; the flashing row's background is `accentSoft`; a ticked row is `mute` with `TextDecoration.LineThrough` and a filled `accent` circle; names are one line, ending in "…"; with no items, "Nothing on the list. Add something above, or tap a favourite." (`shopping_empty`); with nothing learned and nothing typed, the chip row shows "Favourites appear as you add things." (`shopping_favourites_hint`); the host watches with `checks.watch(checkNow = true)` in a `DisposableEffect`; when `focus.takeAddAsk()` is true it requests focus on the field and shows the keyboard (`LocalSoftwareKeyboardController.current?.show()`).

- [ ] **Step 1: The failing tests.** Each screen test ends with `assertClickablesHaveRoleAndLabel()`.
  - `ControlsTest.aFieldCanKeepFocusAfterDone`: with `clearsFocusOnDone = false`, after `performImeAction()` the field `assertIsFocused()` and `onDone` was called once; the default still clears focus (**pin**).
  - `IconFontTest` covers `shopping_cart`.
  - `FitRowTest.onlyWholeChipsShow`: five 100 dp boxes, gap 9, in 330 dp: three are placed, the fourth and fifth aren't (not displayed); in 1000 dp all five.
  - `ShoppingTabTest` (1280 × 800, a state built by hand):
    - `theHeaderSaysHowManyAndFromWhere`: `shopping_title` "Shopping" is a heading; `shopping_line` is "22 items · from Google Tasks, just now".
    - `theListFillsDownThenAcross`: 7 items: column 1 holds 1–3, column 2 4–6, column 3 the 7th (by bounds: items 1, 4 and 7 share a top; 2 is below 1).
    - `thirtyItemsScrollWithAFade`: 30 items: `shopping_item` of the 30th is not displayed until `performScrollToNode`, and the fade shows at the list's foot before and not after.
    - `tappingTheNameTicks`: a tap on the text "Milk" calls `onTick` with Milk's row; the row is `Role.Checkbox`, named "Milk", "not ticked".
    - `aTickedRowIsMutedAndStruck`: the ticked Milk's text has `LineThrough` and `ToggleableState.On`.
    - `doneThenAddAddsOnce` (Review Focus 1) — with the real `ShoppingActions` over a fake provider: type "Milk", IME Done, then tap Add: one queued ADD; the field is empty and focused.
    - `suggestionsComeFirstThenFavourites`: typed "b" with learned Butter, Bread, Bin bags, Eggs, Tea bags: `shopping_suggest_bread` is left of `shopping_suggest_butter`, both left of `shopping_favourite_eggs`; no favourite for a suggested key; a tap on `shopping_suggest_butter` calls `onChip(Butter)`.
    - `nothingLearnedSaysHowFavouritesCome`: `shopping_favourites_hint` "Favourites appear as you add things.".
    - `anEmptyListSaysWhatToDo`: `shopping_empty` "Nothing on the list. Add something above, or tap a favourite.".
    - `aProblemShowsUnderTheAddCard`: `CANT_REACH` shows "Can’t reach Google Tasks. Items you add are kept and sent when it’s back." in `shopping_status`, between the add card and the list; Add still works.
    - `aLongNameIsOneLineEndingInAnEllipsis` (Review Focus 4): a 1,000-character name's row is 48 dp ±1 and its text has an ellipsis.
    - `theAddBoxTakesAHundredCharacters`: typing 120 characters leaves 100.
    - `homesPlusFocusesTheBox`: after `focus.askForAdd()`, the host's field is focused; a second composition doesn't refocus.
    - `theTabWatchesWhileShown`: `checks.watching` is 1 while shown, 0 after.
  - `ShoppingTabScreenshotTest` (each `_dark` and `_light`): `shopping_tab_empty`, `shopping_tab_few` (Milk, Bread, Eggs), `shopping_tab_30`, `shopping_tab_typing` ("bu" with suggestions and favourites), `shopping_tab_ticked` (Bananas ticked), `shopping_tab_cant_reach`, `shopping_tab_permission`.

  Run `./gradlew :core:ui:testDebugUnitTest --tests '*ControlsTest*' --tests '*IconFont*' :capability:shopping:testDebugUnitTest --tests '*FitRow*' --tests '*ShoppingTab*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces and Units.
- [ ] **Step 3: Images.**
  - **Predicted:** none in `core/ui` (the field's default is unchanged, the icon is new). **New:** the 14 above.
  - **Check against** `shopping-tab-b2.html` (arrangement) and the Units.
```bash
git add core/ui tools/fonts/icons.txt capability/shopping
git commit -m "Add the Shopping tab: an add box with suggestions and favourites over the list in three columns, tick by tapping a row"
```

---

### Task 8: The Home card (spec §4.9; rulings 16, 21, 22)

**Review:** sonnet.

**Files:**
- Create `capability/shopping/.../ui/ShoppingCard.kt` (`ShoppingCard` stateless; `ShoppingCardHost(view, actions, checks, focus)`); extend `ui/ShoppingDimens.kt`.
- Test: `ui/ShoppingCardTest.kt`, `ui/ShoppingCardScreenshotTest.kt`.

**Interfaces:**
- Consumes: Tasks 4 and 7 (`ShoppingFocus.askForAdd`); `LocalShellNavigator.openTab(SHOPPING_ID)`; `HhCard`, `HhRoundButton`.
- Produces: `@Composable internal fun ShoppingCard(state: ShoppingState, onTick: (ShownItem) -> Unit, onOpen: () -> Unit, onAdd: () -> Unit)`; `internal fun cardSlots(count: Int, rows: Int): Pair<Int, Int>` (items shown, more) — `rows * 2` when all fit, else `rows * 2 − 1` and the rest; tags `shopping_card`, `shopping_card_title`, `shopping_card_count`, `shopping_card_add`, `shopping_card_item_{key}`, `shopping_card_more`, `shopping_card_empty`, `shopping_card_status`.
- Behaviour: the header (title and count) is one button that opens the tab; + calls `focus.askForAdd()` then `openTab`; a row (`Role.Checkbox`) ticks through `actions.tick`, and the Undo toast shows on Home; the footer "+{n} more · Open Shopping" opens the tab; empty shows "Nothing on the list" and the +; a problem replaces the item area's last line with its line in `mute`, under the items (`shopping_card_status`).

- [ ] **Step 1: The failing tests.**
  - `cardSlotsFillTwoColumns`: `cardSlots(10, 8) == 10 to 0`; `cardSlots(16, 8) == 16 to 0`; `cardSlots(17, 8) == 15 to 2`.
  - `ShoppingCardTest` (the card at 701 × 359, the Units; then at 701 × 586):
    - `theHeaderSaysHowMany`: "Shopping" and "3 items"; the + is 48 dp to the touch and spoken "Add to the shopping list".
    - `itemsAreTwoColumnsDownThenAcross`: 10 items at 701 × 359: item 6 is in the second column (its left is right of item 1's right); rows are 31 dp ±0.5.
    - `overflowSaysHowManyMore`: 20 items at 701 × 359 show 15 rows and "+5 more · Open Shopping"; tapping it calls `openTab("shopping")` on a `RecordingNavigator`.
    - `theTallCardShowsMore`: 20 items at 701 × 586 show all 20 and no footer.
    - `aRowTicks`: a tap on Milk's row calls `onTick(Milk)`; the row is `Role.Checkbox`.
    - `plusOpensTheTabAskingForTheBox`: after a tap on `shopping_card_add`, `focus.takeAddAsk()` is true and the navigator recorded `openTab shopping`.
    - `emptySaysSo`: "Nothing on the list", and the + still shows.
    - `aProblemShowsOnTheCard`: `NEEDS_PERMISSION` shows "Allow Google Tasks in Settings › Shopping".
    - `theCardWatchesWhileShown`: `checks.watching` is 1 while shown.
  - `ShoppingCardScreenshotTest` (each `_dark` and `_light`): `shopping_card_wide` (8 items, 701 × 359), `shopping_card_empty`, `shopping_card_overflow` (20 items), `shopping_card_tall` (701 × 586).

  Run `./gradlew :capability:shopping:testDebugUnitTest --tests '*ShoppingCard*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Units.
- [ ] **Step 3: Images.** New: the 8 above. Check against the handoff's "Shopping card (note 12)", without the added-by label and with the plan's footer.
```bash
git add capability/shopping
git commit -m "Add the Shopping card for Home: the list in two columns, tick a row, plus opens the tab ready to type"
```

---

### Task 9: Settings › Shopping, turning it on and off, the capability wired in (spec §4.1, §4.10, §4.11; rulings 3, 4, 20, 24, 27; Review Focus 5)

**Review:** opus. Admin PIN, which account, the kiosk while Google's screens show, and a way out of nothing beyond the list.

**Files:**
- Create in `capability/shopping/.../`: `ShoppingCapability.kt`, `ShoppingConnections.kt`, `ShoppingAccountUse.kt`, `ui/ShoppingSettingsPage.kt` (`ShoppingPage`, `ShoppingSettings` stateless, `ShoppingSettingsHost`).
- Modify `di/ShoppingModule.kt`: `@IntoSet Capability`, `@IntoSet AccountUse`.
- Modify `capability/calendar`: its module adds `@Multibinds Set<AccountUse>`; `CalendarReview.kt` (`otherUsers(row): List<String>`; `disconnectQuestion(service, queued, otherUsers)`); `ui/ReviewCalendars.kt` (`Confirming` carries them).
- Modify `app/.../shell/ui/ShellScreenshotTest.kt` only if the rail's test capabilities list changes (it shouldn't).
- Test: `ShoppingCapabilityTest.kt`, `ShoppingConnectionsTest.kt`, `ui/ShoppingSettingsTest.kt`, `ui/ShoppingSettingsScreenshotTest.kt`; `capability/calendar` `CalendarReviewTest.kt`, `ReviewCalendarsTest.kt`.

**Interfaces:**
- Consumes: everything above; `AccessControl.authorise(CorePermissions.CONNECTIONS_MANAGE)`; `HhSwitchRow`, `HhRadioRow`, `HhListRow`, `HhButton`, `HhConfirmCard`, `HhPageTitle`, `HhStatusLine`; `SettingsPage`.
- Produces:
  - `@Singleton class ShoppingCapability : Capability`: `id = SHOPPING_ID`, `label = "Shopping"`, `icon = Icons.SHOPPING_CART`, `order = 20`; `hasTab` = a stored connection whose provider is bound; `cards()` = `[HomeCard("shopping.list", UTILITY, 40) { ShoppingCardHost(…) }]` while `hasTab`; `TabContent()` = `ShoppingTabHost`; `settingsPages()` = `[ShoppingPage]` when any provider is bound; no badge.
  - `@Singleton class ShoppingConnections @Inject constructor(store, providers, sync, loop: ShoppingDrainLoop, access, toaster, @ApplicationScope scope)`:
    - `val providers: List<ShoppingProvider>` (userConnectable first, then by name);
    - `suspend fun mayConnect(): Boolean` (an Admin's fresh PIN);
    - `fun connected(connection: Connection)` (on the app scope: `store.connect`, then `sync.check()`, which seeds);
    - `suspend fun remakeList(): Boolean` (PIN; `provider.remakeList`, `replaceConnection`, health OK, `loop.wake()`);
    - `suspend fun queued(): Int`;
    - `suspend fun stop(): Boolean` (PIN; `store.stop()`; toast "Stopped using Google Tasks.").
  - `class ShoppingAccountUse @Inject constructor(store) : AccountUse` — `["Shopping"]` when the stored connection's `SHOPPING_CONFIG_ACCOUNT` equals the account (ignoring case), else empty.
  - Settings states, as `ShoppingSettings(state: SettingsState, …)`: `Off(providers with ProviderAccounts)`, `ChoosingAccount(provider, accounts, chosen)`, `Connecting(provider, account)`, `On(provider, account, health, lastReadMillis)`, `ConfirmingStop(queued)`.
  - Tags: `shopping_use_{providerId}`, `shopping_blocked`, `shopping_account_{index}`, `shopping_account_line`, `shopping_health`, `shopping_allow`, `shopping_make_list`, `shopping_kept_line`, `shopping_stop`, `shopping_confirm_stop`, `shopping_keep`.
- Behaviour:
  - **Off:** for each provider, `HhSwitchRow("Use Google Tasks for the shopping list", line = the only account or null)`; `Blocked` shows its line ("Connect Google Calendar first. Shopping uses the same account.") under a disabled switch.
  - **Turning on:** a fresh Admin PIN first; with two or more accounts, "Which Google account holds the list?" and an `HhRadioRow` per account, then "Use Google Tasks for the shopping list" again to go on; then the provider's `ConnectScreen` for that account. Done → `connected`. Cancel → Off, nothing stored.
  - **On:** the switch on with the account as its line; the health line as Settings › Calendars draws it (`HhStatusLine`, "Updated {ago}" when OK, else the problem); `NEEDS_PERMISSION` adds "Allow Google Tasks" (primary; PIN, then `ConnectScreen` with the stored connection, which keeps its id); `LIST_GONE` shows "The Shopping list isn’t in Google Tasks any more." and "Make a new list" (primary); `ACCOUNT_GONE` shows the provider's `accountGoneLine`; "Stop using Google Tasks" (quiet) under all of them; "Google Tasks stays allowed for {account} until someone removes it in their Google account settings." (`mute`) at the foot.
  - **Stopping:** `HhConfirmCard("Stop using Google Tasks? The list stays in Google Tasks, and suggestions stay here." + the unsent line, confirm "Stop using Google Tasks")`, then the PIN.
  - **The calendar:** `disconnectQuestion` appends " Shopping uses this account too, and stops until it’s connected again." when `otherUsers` holds "Shopping" (any names: " {names} uses this account too…" with "and").

- [ ] **Step 1: The failing tests.** Screen tests end with `assertClickablesHaveRoleAndLabel()`.
  - `ShoppingCapabilityTest`:
    - `noConnectionNoTabAndNoCard`; `aConnectionShowsTheTabAndTheUtilityCard` (`cards().first()` is one UTILITY card, priority 40); `noProviderBoundNoSettingsPage`; `theTabSitsAfterCalendar` (`order == 20`, icon `shopping_cart`).
    - `aStoredConnectionWhoseProviderIsGoneHasNoTab`: providerId "shopping.todoist" with only the fake bound: `hasTab` false.
  - `ShoppingConnectionsTest`:
    - `connectingAsksForAnAdminPin`: a refused PIN stores nothing.
    - `connectingSeedsFavourites`: after `connected`, the fake's history items (Milk ×3, Bread) are learned, Milk with count 3.
    - `stopKeepsWhatWasLearned`: with a queue of 2 and learned rows, `queued() == 2`; `stop()` leaves no connection or queue and keeps the learned rows.
    - `stoppingAsksForAnAdminPin`.
    - `theAccountUseNamesShopping`: connected for `family@example.com`: `usersOf("FAMILY@example.com") == ["Shopping"]`, `usersOf("sam@example.com")` empty.
  - `ShoppingSettingsTest` (fake providers and accounts; `RecordingNavigator`):
    - `withNoGoogleAccountItSaysConnectTheCalendarFirst`: `shopping_blocked` "Connect Google Calendar first. Shopping uses the same account."; the switch is disabled.
    - `oneAccountIsUsedWithoutAsking`: the switch's line is "family@example.com"; on (after the PIN) goes straight to `ConnectScreen("family@example.com")`.
    - `twoAccountsAskWhich`: "Which Google account holds the list?" with two radio rows; choosing Sam's connects for "sam@example.com".
    - `onShowsTheAccountTheHealthAndHowToStop`: "Updated 2 minutes ago", `shopping_stop` "Stop using Google Tasks", and `shopping_kept_line` "Google Tasks stays allowed for family@example.com until someone removes it in their Google account settings.".
    - `missingPermissionOffersAllow`: `NEEDS_PERMISSION` shows `shopping_allow` "Allow Google Tasks", primary; it reconnects with the same connection id.
    - `makeANewListThenTheQueueDrains` (Review Focus 5): `LIST_GONE` with two queued adds: "Make a new list" (after the PIN) calls `remakeList`, the health is OK, and after the drain the fake's new list holds both items.
    - `aDisconnectedAccountSaysWhereToFixIt`: `ACCOUNT_GONE` shows "Shopping’s Google account was disconnected. Connect it again in Settings › Calendars.".
    - `stopAsksFirstAndSaysWhatIsLost`: with 1 queued, the card reads "Stop using Google Tasks? The list stays in Google Tasks, and suggestions stay here. 1 unsent change will be dropped."; Keep closes it with nothing changed.
    - `nothingOnThePageOpensAnythingElse`: every clickable on each state is one of the tags above (no navigation outside Settings › Shopping except Google's screens).
  - `CalendarReviewTest.disconnectingAnAccountShoppingUsesSaysSo`: with an `AccountUse` returning `["Shopping"]`, the question ends " Shopping uses this account too, and stops until it’s connected again."; with none, it is unchanged (**pin** of today's words).
  - `ShoppingSettingsScreenshotTest` (each `_dark` and `_light`): `settings_shopping_blocked`, `settings_shopping_off`, `settings_shopping_choose`, `settings_shopping_on`, `settings_shopping_permission`, `settings_shopping_list_gone`, `settings_shopping_account_gone`, `settings_shopping_stop`.

  Run `./gradlew :capability:shopping:testDebugUnitTest --tests '*ShoppingCapability*' --tests '*ShoppingConnections*' --tests '*ShoppingSettings*' :capability:calendar:testDebugUnitTest --tests '*CalendarReview*' --tests '*ReviewCalendars*'`. Expected: FAIL.
- [ ] **Step 2: Implement** per the Interfaces. The Settings page is `order = 420`, `id = "shopping"`, `title = "Shopping"`.
- [ ] **Step 3: Images.**
  - **New:** the 16 above. **Must not change:** `review_`, `settings_calendars_` (the question shows only in a confirm card that no screenshot holds; if one does, it is unchanged without Shopping).
  - **Check against** Settings › Calendars' row pattern (5c).
```bash
git add capability/shopping capability/calendar
git commit -m "Turn the shopping list on in Settings › Shopping behind an Admin PIN, show its health and fixes, and warn before disconnecting the account it uses"
```

---

### Task 10: `:provider:shopping-fake` for debug builds (ruling 24)

**Review:** sonnet.

**Files:**
- Create `provider/shopping-fake/build.gradle.kts` (library, compose, hilt; `:capability:shopping`, `:core:plugin`, `:core:ui`); `SampleShoppingProvider.kt`, `di/SampleShoppingModule.kt` (`@IntoSet ShoppingProvider`). Register in `settings.gradle.kts`, `app/build.gradle.kts` as `debugImplementation`, README.
- Test: `SampleShoppingProviderTest.kt`.

**Interfaces:**
- Produces: `class SampleShoppingProvider @Inject constructor(clock: WallClock) : ShoppingProvider` — `descriptor = ProviderDescriptor("shopping.sample", "the sample list", Icons.SHOPPING_CART, setOf(READ, WRITE), userConnectable = false)`; `accounts = flowOf(Ready(emptyList()))`; starts with Milk, Bread and Eggs open and a history of Butter ×3, Bananas ×2, Bin bags, Tea bags; `ConnectScreen` connects at once; a debug-only `fun addFromAPhone(text: String)` puts an item first, for the walk-through. The Settings switch reads "Use the sample list for the shopping list".

- [ ] **Step 1: The failing tests.** `SampleShoppingProviderTest`: it passes the seam's behaviour as `FakeShoppingProvider` is used in Task 4 (add first, tick and untick move items, history since); `addFromAPhone("Eggs")` shows on the next `items`; nothing is deleted.
- [ ] **Step 2: Implement.**
- [ ] **Step 3: The gate**, then commit.
```bash
git add settings.gradle.kts app/build.gradle.kts README.md provider/shopping-fake
git commit -m "Add a sample shopping list to debug builds"
```

---

### Task 11: Simplify the whole branch

**Review:** the controller.

- [ ] **Step 1:** Run the `simplify` skill over `git diff 2514bab1...HEAD`. Look hardest at: the two Google HTTP layers (`GoogleApi`, `TasksApi`) for a shared piece that belongs in `:core:google` without changing the calendar's log words; the two connect flows; `FakeShoppingProvider` against `SampleShoppingProvider`; and any copy built twice.
- [ ] **Step 2:** For each change, the affected module's tests, then the gate.
- [ ] **Step 3: Commit** (when anything changed).
```bash
git commit -am "Simplify the shopping list's code"
```

---

### Task 12: The emulator walk-through on `culvery-5`

**Review:** the controller, with the user at the emulator.

**Rules** (as 9c-2's Task 8): only the `culvery-5` AVD (`-no-snapshot-save -camera-front emulated`; check `adb -s "$S" emu avd name`); `./gradlew :app:assembleDebug` then `adb -s "$S" install -r app/build/outputs/apk/debug/app-debug.apk`; never uninstall, never a Gradle install task with a device connected; the user types every Admin PIN (the debug sample PINs are entered by Claude, per the walk-through memory); put back every setting changed; collect faults and ask before fixing.

- [ ] **Step 1: The build on the AVD.**
- [ ] **Step 2: Walk the checklist** (one line each).

  **Not in this build, on purpose:** who added an item (Google Tasks doesn't record it); Todoist; Waitrose; the One-tap tray (Shopping is widened into its space); quantities, prices, aisles and categories; more than one list. **Design cuts to say again:** the five "Notes for the end-of-v1 design review".

  1. Before turning it on: Home is as before (Today, Lights over Music, or Coming up); no Shopping tab.
  2. Settings › Shopping: turn on the sample list (PIN); the tab appears after Calendar with the cart icon; Coming up leaves Home; the Shopping card takes the top right, Lights and Music sit side by side below it.
  3. The tab: Milk, Bread, Eggs in three columns; favourites show Butter, Bananas, Bin bags, Tea bags.
  4. Add: type "b": suggestions first, then favourites; tap Butter; type "Milk" and press Done: Milk flashes, nothing added; Add then Done fast on "Apples": one Apples; the box keeps focus.
  5. Tick Bread: muted and struck, the "Ticked Bread" toast with Undo; Undo brings it back; tick two in a row: one toast, the first gone.
  6. Home: the card's rows tick with Undo; + opens the tab with the keyboard up; "+{n} more · Open Shopping" once the list is long.
  7. A phone's add (`addFromAPhone` through the debug menu or a test hook): it shows within a minute while the tab or card is on screen.
  8. Wi-Fi off with the Google provider (if the AVD holds a Google account): add Milk, the line under the add card says can't reach; Wi-Fi on: it sends. Otherwise covered on the tablet.
  9. Stop using: the question, then the PIN; the tab and card go; Home is as in 1; turn it on again: favourites are still there.
  10. Settings › Calendars › Disconnect (with Shopping on Google): the question says Shopping uses the account (Keep; don't disconnect).
- [ ] **Step 3: The fault loop.** For each fault, with the user's go: a failing test, the fix, the gate, a commit naming it, `install -r`, a re-check.
- [ ] **Step 4: Put back** every setting, then `adb -s "$S" emu kill`.
- [ ] **Step 5: Record** under "Plan 10 (The Shopping list): emulator walk-through".
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 10's emulator walk-through"
```

---

### Task 13: On the tablet

**Review:** the controller with the user. **Needs the SM-T510 on the cable**, and the Tasks API enabled in the Cloud project (Task 5's setup section) — the user does that first.

4e's tablet rules apply: save and restore every setting changed; install signed releases with `adb -s <tablet> install -r`; never uninstall; run the names check before any commit.

- [ ] **Step 1: Install the release.** Sign it per `docs/setup/release.md`, keep `mapping.txt` in `culvery-notes/mappings/`, then `adb -s <tablet> install -r app/build/outputs/apk/release/app-release.apk`.
- [ ] **Step 2: Turn it on** (the user): Settings › Shopping, the Admin PIN, Google's Tasks permission ("isn't verified" once more, expected); the "Shopping" list is found or made.
- [ ] **Step 3: At the wall and on the phones** (the user), check:
  - add on the wall, see it on a phone within a minute;
  - add on a phone, see it on the wall within a minute with the tab open, and with only Home showing;
  - tick on each side, and Undo on the wall;
  - Wi-Fi off on the tablet: add one, see the line; Wi-Fi on: it reaches the phone;
  - favourites after the first seed look like the household's list;
  - Lights and Music side by side read at arm's length.
- [ ] **Step 4: The log scan.** `adb -s <tablet> logcat -d -s GoogleTasks ShoppingSync ShoppingChecks > "$LISTS/plan10-log.txt"`, then grep it for every item added in Step 3: no match.
- [ ] **Step 5: Record** under "Plan 10 (The Shopping list): on the tablet": what worked and what didn't. No names, no items.
```bash
git add docs/superpowers/plans/2026-09-23-plan1-followups.md
git commit -m "Record Plan 10 on the tablet"
```

---

### Task 14: Final review, merge, install

- [ ] **Step 1: The review.** Opus reviews the whole branch against the spec and this plan, the anti-goals first. Then one fix wave, each fix with its failing test first and the gate.
- [ ] **Step 2: Merge**, on the user's say: `git merge --ff-only plan-10-shopping` on `main`; the gate on main; never push. Move "Notes for the end-of-v1 design review" to the follow-ups.
- [ ] **Step 3: Install** the release from the merged `main` on the tablet with `install -r`.

---

## Spec coverage (10 design → tasks)

| Spec | Task |
|---|---|
| §3 rulings 1–2: Google Tasks through a seam, the house account | 1, 2, 5 |
| §3 ruling 3, §4.7: suggestions and favourites in the capability | 3, 7 |
| §3 rulings 4–7, §4.8: the tab, layout B | 7 |
| §3 ruling 8, §4.9: Home, layout A, the card | 6, 8 |
| §3 ruling 9: Google's order | 5 |
| §3 ruling 11: Coming up goes with Shopping | 6 |
| §4.1 modules; the shared sign-in | 1, 2, 5 |
| §4.2 the seam; nothing Google-specific crosses | 2, 5 |
| §4.3 scope, the list, reads, writes, logs | 1, 5 |
| §4.4 every 60 s while shown; wall changes first; sleep | 4 (ruling 18) |
| §4.5 the queue, no duplicate on retry, problems, Undo | 4, 7, 8 |
| §4.6 duplicates | 2 (`textKey`), 4 |
| §4.10 Settings, which account, disconnect, stop | 9 |
| §4.11 no PIN to add or tick; PIN to connect; the cap and the rate | 4, 7, 9 |
| §6 unit, screenshot and tablet tests | 1–10, 13 |
