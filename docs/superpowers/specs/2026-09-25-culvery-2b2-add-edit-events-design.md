# Culvery 2b-2: Add and edit events (design)

**Date:** 2026-09-25
**Status:** Approved
**Amended:** 2026-09-25, after the plan review, with the user's approval: an edit that changes Who follows the add rule (§6, with D2, D7, §3.3, §3.7, §4.2); a write the provider accepted counts as done even if storing it fails (§3.4, with D5, §3.3, §5).
**Parent spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§8, §9.3, §9.4)
**Previous plan:** `docs/superpowers/specs/2026-09-24-culvery-2b1-change-events-design.md` (2b-1, change events)
**Design:** `docs/design/house_hub_handoff/README.md` §7 ("Sheet 2 — Quick-add / edit", "Entry points") and `screenshots/calendar-sheets/10`–`16`, dark and light; `docs/design/brief-calendar-sheets.md`
**Builds on:** Plan 2b-1, merged to `main`.

## 1. Scope

2b-2 finishes Plan 2 part b. It adds:
- the add/edit sheet (hand-off Sheet 2), with the date and time pickers and keyboard handling;
- the entry points: **+** on the Today card, **Add event** in the Calendar header, a tap on empty space in a week column, and **Edit** in the detail sheet's footer;
- creating and updating events through `CalendarEditor`, with the same save flow as 2b-1;
- idempotent creates (follow-up S1): `calendar.db` v3 and a client key on `CalendarWriter.create`;
- the 2b-1 follow-ups marked "For Plan 2b-2".

Writes are still exercised against the debug fake provider only. Google writes arrive in Plan 3 against the same contract.

## 2. Decisions (agreed with the user)

| # | Decision |
|---|---|
| D1 | **Save flow = 2b-1's.** Authorise on Save, then try the writer directly for up to 10 s with the sheet open. **Rejected** → the sheet stays open with the input kept, the hand-off's `dangerSoft` card "Couldn't save to {connection label}" shows above the footer, and Save becomes **Try again**. **Offline or timed out** → the change is queued, the sheet closes and the event shows as syncing. A later rejection from the queue is a toast, and the change rolls back. The hand-off's "close at once, reopen on a later rejection" is **not** built. Success toasts: "Event added" / "Changes saved". |
| D2 | **Who defaults.** If someone is signed in, Who starts on them. A signed-in Child sees the other chips disabled, not hidden. If nobody is signed in, Who starts on Family and every chip can be picked. The PIN on Save decides: a Child with only `create.self` saving an event that isn't for themselves gets the toast "{Name} can only add events for themselves.", and the sheet stays open with its input intact. An edit that changes Who follows the same rule (§6). Opening a sheet never asks for a PIN. |
| D3 | **Editing values that aren't chips.** An existing event's own start time shows on the **Pick time…** chip, selected (e.g. "19:30"). Its own length adds a 4th Length chip, selected (e.g. "1 h 30"). Nothing changes unless the user changes it. **Multi-day events** get Edit, but only Title and Who can be changed: Day, Time and Length become one read-only line, "Mon 22 – Wed 24 · change dates on your phone". New events can't span days. |
| D4 | **Architecture: a separate add/edit sheet.** `EventForm` is a plain Kotlin state holder (title, who, day, time, length; `canSave`; the summary line; builds the `EventDraft` in the household zone, all-day as whole dates; `unchanged`, so an unchanged edit just closes with no PIN). `EventEditorSheet` is stateless. `EventEditorHost` is shown through the existing overlay host. The detail sheet's Edit swaps the overlay to the editor. The editor's Delete authorises, then swaps to the detail sheet in its delete-confirm state. ✕ or the scrim closes entirely. The pickers draw inside the sheet's 600 dp box, with no second overlay. No ViewModels. |
| D5 | **Idempotent creates** (follow-up S1). `calendar.db` v3 adds a nullable `clientKey TEXT` to `outbox`, with a hand-written `MIGRATION_2_3` and a `MigrationTestHelper` test. `CalendarWriter.create` takes a `clientKey: String`, chosen on Save. Contract rule: a repeated create with the same key returns the existing event, never a duplicate. Providers that can choose ids (Google, the fake) use the key as the remote id, so a queued create's `EventRef`, built from the key, stays the same after it syncs. A new contract check proves idempotency. This also fixes the deferred "duplicate CREATE if `applyAcceptedWrite` throws". A write the provider accepted counts as done even if storing it on the tablet fails; the next sync stores it (§3.4). |
| D6 | **Pending creates behave like any event.** They can be opened, edited and deleted before they sync. Edits and deletes of a queued create queue behind it. In-order delivery plus idempotent creates make that correct; there is no collapsing of queued changes. |
| D7 | **Editor.** `create(draft)` authorises `create`, or `create.self` with `allow` = the draft is for the authorised person. `update(ref, draft)` uses `edit` / `edit.own` like delete, and re-reads `createdBy` **after** authorising (follow-up); an update that changes who also follows the add rule (§6). `createdBy` on a create is the authorised person. An update sends the title, times and who only; the existing contract keeps every other field (Google: PATCH). |
| D8 | **Sheet UI** per hand-off §7 Sheet 2 and screenshots 10–16 (§4.2). Header "New event" / "Edit event" + live summary ("Tomorrow · 14:00–15:00 · Mia") + ✕. Title 64 dp, "What's happening?", at most 100 characters; Save disabled until the trimmed title isn't empty. Who: Family and the household's people. Day: Today, Tomorrow, the next 5 days, Pick date… (a week-column tap preselects that day; an edit selects the event's date chip, or shows its date on Pick date…). Time: All day, Morning 09:00, Afternoon 14:00, Evening 18:00, Pick time… (a new event defaults to the next slot still to come today, All day late in the evening, Morning on a later day). Length: 30 min / 1 h / 2 h, default 1 h, hidden for All day. Footer: "Save event" / "Save changes", with Delete on the left in edit mode. One save at a time (Save disabled while busy); closing mid-save still completes it (application scope, as 2b-1). |
| D9 | **Keyboard.** New event → Title focused, keyboard up. Edit → no focus. With the keyboard up the sheet shrinks as in the hand-off: the header and Save stay pinned and the chips scroll. Toasts sit above the keyboard. The keyboard's Done closes the keyboard; it doesn't save. Kiosk mode and the Samsung keyboard are the riskiest part (§4.4, §8). |
| D10 | **Pickers.** Date: a 7×5 grid, today ringed, past days dimmed but selectable, arrows to move between pages, Cancel. Time: hour and minute steppers (1 h / 15 min), Cancel and **Set time**. Hand-off sizes. |
| D11 | **Closing with unsaved edits:** ✕ or the scrim closes without a "Discard changes?" prompt. |
| D12 | **Entry points.** **+** on the Today card header (44 dp accent circle) → a new event today. **Add event** pill (48 dp) in the Calendar header. A tap on empty space in a week column → a new event on that day; each column ends with a faint 24 dp `add` hint at 50% opacity with a tap target of at least 40 dp (chip taps still open the event). **Edit** in the detail sheet footer, right of Delete (flex, 60 dp, accent, `edit` icon, 18 sp/700), shown exactly where Delete shows (master calendar, not recurring). |
| D13 | **Housekeeping** from the follow-ups: move `SilentToaster` to test sources, and drop the `writers` and `toaster` defaults on `CalendarSync`'s internal constructor. |

## 3. Architecture

### 3.1 Form state (`EventForm`, `:capability:calendar`)
A plain Kotlin class with Compose `mutableStateOf` fields, so the sheet recomposes as it changes and unit tests drive it without a UI. No Android types.

```kotlin
class EventForm(
    val mode: Mode,               // New or Edit(ref, original)
    today: LocalDate,             // household-zone date when the sheet opened
    now: LocalTime,               // household-zone time when the sheet opened
    zone: ZoneId,
    signedIn: PersonId?,
    preselectedDay: LocalDate?,   // from a week-column tap
)
```

**Fields**
- `title: String` (at most 100 characters; typing past 100 is ignored).
- `who: PersonId` (`PersonId.FAMILY` for Family).
- `day: LocalDate`: a date, not "today + n", so a sheet left open over midnight still saves the day its chip showed when it was tapped.
- `time: TimeChoice`: `AllDay`, `Slot(Morning | Afternoon | Evening)` or `Custom(LocalTime)`.
- `length: Duration`: 30 min, 1 h, 2 h, or the edited event's own length (D3).
- `datesLocked: Boolean`: true when editing a multi-day event (D3).

**Defaults for a new event**
- Who: `signedIn` if set, else Family (D2).
- Day: `preselectedDay` if given (column tap), else today.
- Time: on today, the next slot still to come: before 09:00 → Morning, before 14:00 → Afternoon, before 18:00 → Evening, from 18:00 → All day. On a later day → Morning. Until the user touches a Time chip, changing Day re-applies this rule, so opening at 20:00 (All day) and tapping Tomorrow gives Morning.
- Length: 1 h.

**Defaults for an edit** (from the event as shown, with its queued changes laid over it)
- Title, Who: the event's. An untagged event (no person) shows Family; if Who is left alone, the update keeps it untagged.
- Day: its start date in the household zone.
- Time: All day for an all-day event; the matching slot if it starts at 09:00, 14:00 or 18:00; otherwise `Custom(start)` on the Pick time… chip.
- Length: the matching chip for 30, 60 or 120 minutes; otherwise its own length as a 4th chip, labelled "45 min", "1 h 30", "3 h" (whole hours "{h} h", under an hour "{m} min", otherwise "{h} h {mm}").
- `datesLocked` when the event's last day (by the existing midnight-end rule in `whenLabel`) is after its first day.

**Derived**
- `canSave`: the trimmed title is not empty.
- `summary`: the header line, e.g. "Today · 18:00–19:00 · Family", "Sat 26 Sep · All day · Family", "Tomorrow · 14:00–15:00 · Mia". It reuses `whenLabel` for the time part and adds " · {person name}". For a locked multi-day event it shows the event's own span.
- `unchanged`: in edit mode, the trimmed title, who, and (unless locked) day, time and length all equal the originals.
- `draft(createdBy: String?): EventDraft`:
  - All day → `start = AllDay(day)`, `end = AllDay(day + 1)`.
  - Timed → `start = ZonedDateTime.of(day, time, zone)` (java.time's rules for a clock change: a time inside a spring-forward gap moves forward, an ambiguous autumn time takes the earlier offset); `end = start + length` as elapsed time.
  - Locked multi-day → the event's own `start` and `end`, unchanged.
  - `forPerson`: the chosen person's id (`"family"` for Family), or null for an untagged event whose Who wasn't touched.
  - `end` is after `start` by construction, so no other validation exists (§6).

### 3.2 Sheet and host (`ui/EventEditorSheet.kt`, `ui/EventEditorHost.kt`)
- **`EventEditorSheet`** is stateless: it takes the `EventForm`, the people, the signed-in person and role, `busy`, the failure message (or null) and callbacks. It draws §4.2.
- **`EventEditorHost`** owns the form (`remember`ed per opening), runs Save and Delete, and swaps overlays. Like `EventDetailHost` it allows one action at a time: Save is disabled while `busy`.
- **Overlay swaps** all go through `LocalOverlayHost.show`, which replaces what is shown:
  - detail sheet **Edit** → `EventEditorHost(Edit(ref))`;
  - editor **Delete** → `editor.mayDelete(ref)`; on true → `EventDetailHost(ref, initialMode = ConfirmingDelete)`; on false the editor stays open;
  - ✕ or the scrim → `dismiss()`.
  `EventDetailHost` gains an `initialMode` parameter for this.
- **Openers:** `rememberEventOpener` gains a sibling, `rememberEventAdder(repo, editor, today): (day: LocalDate?) -> Unit`, used by the three add entry points.
- **Closing mid-save** still completes the save on the application scope (as 2b-1). If the sheet has gone by the time a rejection comes back, the editor shows it as a toast (§3.3).

### 3.3 Editor (`CalendarEditor`)
Two new operations beside `delete` and `assign`. The `require(kind == DELETE || kind == ASSIGN)` guard in `write` goes.

```kotlin
suspend fun create(draft: EventDraft): EditResult          // draft.createdBy is ignored; set from the PIN
suspend fun update(ref: EventRef, draft: EventDraft): EditResult
```

**`create`**
1. Resolves the master source, its connection and writer. No master, or no writer for it → `NotEditable` (the entry points are hidden in that case, §4.1).
2. `authorise(CREATE, CREATE_SELF, reason = Save, allow = { who, granted -> CREATE in granted || (CREATE_SELF in granted && draft.forPerson == who.person.id.value) }, refusal = Toast(::cannotAddForOthers))`.
3. Sets `createdBy` to the authorised person, and chooses `clientKey` (§3.4).
4. Writes as 2b-1 does: under the write lock, on the application scope, with a 10 s attempt. Accepted → the mirror gets the returned event → `Done` (still `Done` if storing it fails, §3.4). Rejected → `Rejected(message)`. Retry → a `CREATE` row with the key, → `Queued`.

**`update`**
1. Resolves the target. A pending create counts as a target (D6): its draft stands in for the mirror row.
2. `authorise(EDIT, EDIT_OWN, reason = Edit, allow = mayChange(granted, who, createdBy), refusal = Toast(::cannotChangeOthers))`. An edit that changes who also needs `assign`, or the create rule for the new person (§6); refused, it toasts `cannotAddForOthers`.
3. **After authorising**, under the write lock, re-reads the event and its queue and checks `mayChange` again with the fresh `createdBy`. If it now fails → the same toast, `Cancelled`. (Closes the follow-up "`CalendarEditor` judges `createdBy` from the snapshot taken before the PIN pad"; `delete` gets the same re-check.)
4. The draft sent keeps the event's current `createdBy`; only title, times and who change (D7).
5. Writes as `write` does today: queued behind any pending change for the same ref (including a pending create), otherwise tried directly.

**Reporting.** Done or Queued → "Event added" (create) / "Changes saved" (update). Rejected → no toast while the sheet is open: the sheet shows the failure card. If the caller has gone (the sheet was closed mid-save), the editor toasts `couldNotSave(label, reason)` as 2b-1 does. Cancelled and NotEditable → as 2b-1.

**Unchanged edits** never reach the editor: the host closes the sheet with no PIN and no toast.

### 3.4 Calendar contract
```kotlin
interface CalendarWriter {
    suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft, clientKey: String): RemoteEvent
    // update and delete unchanged
}
```
- **`clientKey`**: a random UUID written as 32 lowercase hex digits with no dashes. That is a valid Google event id (base32hex, 5–1024 characters). The editor chooses a new key on each Save tap; a queued create keeps its key for every retry.
- **Accepted means done:** a write the provider accepted counts as done even if storing it on the tablet fails; the next sync stores it. The editor returns `Done` and toasts success, so a Try again can't send a second create.
- **Rule:** a create with a key already used on that source returns the event that key created and never makes a second one. The writer uses the key as the event's remote id, so the returned `remoteId == clientKey`. Google (Plan 3): `events.insert` with `id = clientKey`; a 409 means it exists, so the writer fetches and returns it. The fake: keeps created events by key.
- Every v1 writer can choose its ids. A future writer that can't would need a key → id map in the engine; that is out of scope, and the contract check requires `remoteId == clientKey`.
- **Contract suite:** a new write check, gated on `Feature.WRITE`: create twice with one key → the same `remoteId`, equal to the key, and a full sync returns exactly one event with that title. The self-test gains a broken writer fixture that ignores the key (a new id per create), which the check must catch.

### 3.5 Storage (`calendar.db` v3)
- **`MIGRATION_2_3`**, hand-written in `Migrations.kt`: `ALTER TABLE outbox ADD COLUMN clientKey TEXT`. The v3 schema JSON is committed.
- **Migration test** in `CalendarMigrationTest`, with the driver-based `MigrationTestHelper` and `AndroidSQLiteDriver` (the Windows path workaround): a v2 database with connections, sources (with a master), events, cursors and outbox rows of each kind survives to v3 with `clientKey` null. The test also asserts the table list, since the driver-based helper may not catch a dropped table (follow-up).
- **`PendingChange`** gains `clientKey: String?`, set only for `CREATE`. `remoteId` stays null for a create. `PendingChange.ref` for a create is `EventRef(connectionId, sourceId, clientKey)`; for other kinds it is unchanged.
- An UPDATE or DELETE queued behind a create carries `remoteId = clientKey`, the ref the user saw.

### 3.6 Pending creates (D6)
- **Overlay:** `overlayPending` gives a queued create the ref `EventRef(conn, source, clientKey)` in place of `"pending-{id}"`. Queued updates and deletes with that ref apply over it in queue order, as for any event.
- **Detail:** `CalendarRepository.event(ref)` also returns a queued create that isn't in the mirror yet, with `syncing = true`, so the syncing pill, Edit and Delete all work before it syncs.
- **Drain:** a create is delivered with its key. Its ref blocks later changes for the same ref exactly as today, so an update or delete is never sent before its create. Once the create lands, the mirror holds the event under the same `remoteId`, and the queued update or delete targets it.
- **Duplicate create fixed:** if the provider accepts a create and `applyAcceptedWrite` then throws, the row stays; the retry returns the existing event.
- **Rejected create:** the drain drops it and every change queued behind it for the same ref, with one toast (§5).
- **Delete of a queued create:** a DELETE row queued behind the CREATE. The event hides at once. Both are sent in order, so the event may exist briefly on the provider before it is removed.

### 3.7 Permissions and wording
The permission table is unchanged from 2b-1 §3.6.

| Operation | Asks for | `allow` | Refusal toast |
|---|---|---|---|
| Create | `create`, `create.self` | `create`, or `create.self` and `forPerson` is the person | "{Name} can only add events for themselves." |
| Update | `edit`, `edit.own` | `mayChange` (as delete) | "{Name} can only change events they created." |
| Update that changes who | also `assign`, `create`, `create.self` | as Update, and `assign`, or the Create rule for the new person (§6) | "{Name} can only add events for themselves." |
| Delete, Assign | unchanged | unchanged | unchanged |

- New in `CalendarPermissions.kt`: `fun cannotAddForOthers(name: String) = "$name can only add events for themselves."`
- Failure wording reuses 2b-1's `couldNotSave(label, reason)` with the connection label in place of "Google" (U3).
- A refusal on the session shortcut signs the person out after the toast (2b-1 U2); the sheet stays open with its input.

## 4. Screens (hand-off §7 values)

### 4.1 Entry points (D12)
- **Today card:** a **+** left of the Week chip: 44 dp circle, `accent`, `add` 26 dp in `accentInk`. Opens a new event on today. The Week chip becomes 44 dp tall, radius 22, 14 sp/600.
- **Calendar header:** **Add event** at the right end of the header row, right of the legend (screenshot 02): 48 dp tall, radius 24, padding 0 20 0 14, `accent`, `add` 24 dp + 15 sp/700 in `accentInk`. Opens a new event on today.
- **Week columns:** a tap on a column's empty space (below its chips) opens a new event on that column's day. Each column ends with an `add` icon (24 dp, `mute`, 50% opacity) in a tap area at least 40 dp tall. Chip taps still open the event.
- **Detail sheet footer:** **Edit** right of Delete: flex 1, 60 dp, radius 30, `accent`, `edit` icon, 18 sp/700 in `accentInk`. It shows exactly where Delete shows (master calendar, not recurring, including untagged and syncing events). It opens the editor without a PIN.
- With no writable master calendar there is nowhere to add to, so the three add entry points are hidden. This isn't a permission rule; buttons are still never hidden for permission reasons.

### 4.2 Add/edit sheet (screenshots 10–14)
Right-side sheet, 600 dp, full height, `bg`, 1 dp `line` left border, scrim `rgba(0,0,0,.55)`. Padding 24×30×20, vertical gap 16.
- **Header:** "New event" / "Edit event" 30 sp/700; below it the live summary, 15 sp `mute`; the 48 dp close button (`surf2`, `close` 26 dp) on the right.
- **Title:** 64 dp, radius 18, `surf`, 22 sp/600, padding 0 20, a 2 dp border that is `accent` when focused. Placeholder "What's happening?". One line, at most 100 characters. Keyboard action Done.
- **Body** scrolls, gap 20. Section labels WHO / DAY / TIME / LENGTH: 13 sp/700 `mute`, 0.5 tracking, uppercase, 10 dp above the chips. Chips wrap with 8 dp gaps.
- **Chip:** 48 dp, padding 0 18, radius 24, 16 sp/600, icon–text gap 8. Unselected `surf`/`ink`; selected `accent`/`accentInk`. Secondary text in time chips ("09:00"): weight 500, 72% opacity. Disabled: 38% opacity, still tappable, and a tap explains by toast.
- **Who:** Family, then each household person in their order. Each chip shows a 12 dp colour dot; selected, its background is the person's colour, its ink `#0E1011`, and a `check` icon replaces the dot. For a signed-in Child, in a new event or an edit, every chip but their own is disabled; a tap toasts "{Name} can only add events for themselves." The disabled state follows the live session; the selection doesn't move after opening.
- **Day:** Today, Tomorrow, the next 5 days by weekday name ("Fri", "Sat"…), and **Pick date…** (`calendar_month`). A date outside those seven shows on Pick date… as "Mon 5 Oct", selected.
- **Time:** All day, Morning 09:00, Afternoon 14:00, Evening 18:00, and **Pick time…** (`schedule`), which shows a custom time ("16:15"), selected.
- **Length:** 30 min, 1 h, 2 h, plus the edited event's own length as a 4th chip when it matches none (D3). Hidden for All day.
- **Locked multi-day edit (D3):** Day, Time and Length are replaced by one `surf` line (radius 18, padding 14×16, `date_range` 22 dp `mute`, 16 sp/600): "Mon 22 – Wed 24 · change dates on your phone".
- **Failure card (screenshot 14):** above the footer. `dangerSoft`, radius 18, padding 14×16, `cloud_off` 24 dp `danger`. Title 16 sp/700: `couldNotSave(label, reason)`, e.g. "Couldn't save to Family". Body 14 sp: "Nothing was changed. Your details are still here — check the connection and try again." It clears on the next Save tap.
- **Footer:** **Save** (flex 1, 60 dp, radius 30, 18 sp/700, `check` icon): "Save event" / "Save changes"; after a failure "Try again" with `refresh`. Disabled (`surf2`, text `mute`, no-op) while `!canSave` or `busy`. In edit mode, **Delete** sits on the left, styled as in the detail sheet (60 dp, padding 0 26, radius 30, `surf2`, `danger` text 17 sp/700, `delete` icon).

### 4.3 Pickers (D10; screenshots 15, 16)
Drawn inside the sheet's box, over a scrim `rgba(0,0,0,.5)` on the sheet only; a tap on it cancels. Card `surf`, radius 30, padding 26, centred. Opening a picker hides the keyboard.
- **Date** (520 dp wide): "Pick a date" 24 sp/700 and the page's range, e.g. "Mon 21 Sep – Sun 25 Oct", 15 sp `mute`. Beside the title, ‹ and › (48 dp circles, `surf2`, `chevron_left` / `chevron_right`) move one page back or forward. A page is 5 weeks (7×5) from a Monday; the first page starts this week's Monday, and the picker opens on the page holding the selected day. Weekday header 13 sp/700 `mute`. Cells 56 dp tall, radius 16, `surf2`, 16 sp/600; the 1st of a month reads "1 Oct". Today has a 2 dp `accent` ring; the selected day is `accent`/`accentInk`; past days are at 30% but can be picked. Tapping a day selects it and closes the picker. Cancel: 52 dp, full width.
- **Time** (440 dp wide): "Pick a time". Hour and minute columns, each an 88×52 up button and down button (`surf2`, radius 18, `expand_less` / `expand_more` 32 dp) around a 72 sp/600 tabular value, separated by ":" at 64 sp. Hours step by 1 and wrap 23 ↔ 00 without changing the day; minutes step through 00, 15, 30, 45 and wrap. An off-quarter start (19:37) shows as it is, and the first press moves to the next or previous quarter. It opens on the selected start time, or on the default slot's time when All day is selected. Cancel and **Set time** (56 dp, radius 28, `accent`). A picked time equal to a slot selects that slot's chip.

### 4.4 Keyboard (D9)
- The activity uses `adjustResize`. The sheet pads by `WindowInsets.ime`, so with the keyboard up (about 320 dp) it is about 480 dp tall: the header, Title and footer stay in place, and the body scrolls (Who and the first row of Day stay visible, screenshot 10).
- New event → Title is focused on open and the keyboard shows. Edit → nothing is focused.
- Done closes the keyboard and clears focus. Save, ✕, the scrim and opening a picker also close it. Tapping a chip doesn't.
- **Toasts** sit 28 dp above the keyboard's top edge while it shows (the shell's toast host adds the IME inset), about the hand-off's 340 dp.
- **Risk:** the keyboard in kiosk (lock-task) mode, with edge-to-edge and no system-bar insets, could cover Save. The emulator walkthrough checks it in kiosk mode with the stock keyboard. The Samsung keyboard can only be checked on the SM-T510, so it joins the Plan 4 on-device pass.

### 4.5 Detail sheet changes
- Footer: Delete + Edit (§4.1).
- `EventDetailHost(initialMode)` for the editor's Delete.
- A queued create opens with the syncing pill (§3.6).

## 5. Errors and offline
- **Add offline** → the event appears at once with the syncing badge, and "Event added" toasts. It can be opened, edited and deleted before it syncs (D6).
- **Edit offline** → the new values show at once with the badge; "Changes saved".
- **Rejected while trying directly** → the sheet stays open with the failure card; Save becomes Try again (D1).
- **Rejected later from the queue** → the drain's toast ("Couldn't save to {label} — {reason}"), and the change rolls back: a create disappears, an edit reverts. When a create is rejected, the drain also drops the changes queued behind it for the same ref (they have nothing to apply to), counted in the same toast.
- **Timeout after the provider actually created the event** → the create is queued with its key; the retry returns the existing event, so there is no duplicate (D5).
- **Child saving for someone else** → the toast, the sheet stays open, the child is signed out (§3.7).
- **The event disappears while its editor is open** (deleted on a phone, then synced) → Save returns `NotEditable`; the sheet closes with no toast, as the detail sheet does.
- **Store failure** (disk full) before the provider accepted, or while queueing → `Rejected(TRY_AGAIN)`: the failure card reads "Couldn't save to {label} — try again". After the provider accepted → `Done` (§3.4).

## 6. Where the hand-off is ambiguous, and what was chosen
- **Which calendar new events go to:** the master calendar. There is no calendar picker.
- **Validation:** none beyond an empty title (Save disabled) and the 100-character cap. `end > start` holds by construction. Past days and times are allowed.
- **Save flow:** the hand-off closes at once and reopens on a later rejection; D1 tries directly first and never reopens.
- **Date picker:** the hand-off shows 5 weeks from this Monday with past days inactive and no arrows. D10 keeps the 7×5 grid and adds paging arrows; past days are dimmed but selectable.
- **"Late evening":** the hand-off's default is Evening from 14:00 on. D8 adds All day from 18:00, when Evening has already started.
- **Who in edit mode:** starts on the event's person. An edit that changes Who follows the add rule: a person without `create` or `assign` (a Child) may tag only themselves, so a signed-in Child's other chips are disabled in an edit too, and the editor refuses such a change with "{Name} can only add events for themselves." An edit that leaves Who alone is checked by `edit` / `edit.own` only (D7).
- **Keyboard stand-in:** the prototype draws its own keyboard; the app uses the system keyboard.

## 7. Testing
- **`EventForm` (unit):**
  - the slot default for each band (08:59, 09:00, 13:59, 14:00, 17:59, 18:00, 23:30), a later day → Morning, and Day changes re-applying it until Time is touched;
  - edit defaults: slot-matching and custom times, the 4th Length chip and its labels, an out-of-week date on Pick date…, an untagged event;
  - the multi-day lock (all-day spans, timed across midnight, and a timed event ending exactly at midnight, which is not multi-day);
  - the summary for Today, Tomorrow, a later day, All day, and the person's name;
  - drafts in the household zone: all-day as whole dates; a timed draft on 25 Oct 2026 (the UK autumn change) and 29 Mar 2026 (spring gap); "Tomorrow" chosen at 23:30 and saved after midnight keeps the chosen date;
  - `canSave` (blank, spaces, 100-character cap) and `unchanged` (including a title that differs only by surrounding spaces).
- **Editor:**
  - create and update, Done / Rejected / Queued for each;
  - the role matrix: Admin and Adult create for anyone; Child creates for self, is refused for Family and for others with the toast; Child updates own events only, and can't move one to someone else; the `createdBy` re-read after authorising, for update and delete;
  - `createdBy` on a create is the authorised person; an update keeps the event's;
  - success and failure toasts, and the toast when the sheet has gone before a rejection.
- **Idempotency:** a create accepted by the provider whose `applyAcceptedWrite` throws is retried and makes one event; an editor attempt that times out after the fake created the event makes one event after the drain.
- **Queue:** queued create → edit → delete, delivered in order; the event is openable at each step with the same ref; a rejected create drops what is queued behind it with one toast.
- **Store:** v2 → v3 migration test with the table list; `clientKey` round-trips; `PendingChange.ref` for a create.
- **Contract suite:** the idempotent-create check, and a self-test fixture that ignores the key and must fail it. The fake passes the full suite.
- **Compose (Robolectric):**
  - sheet states: new, edit, locked multi-day, failure card, Try again;
  - Save disabled until a title, and while busy;
  - a signed-in Child's disabled chips toast;
  - swaps: detail Edit → editor; editor Delete → detail in delete-confirm; ✕ and the scrim close;
  - pickers: paging, past-day selection, hour and minute wrapping, Cancel, Set time;
  - entry points: Today +, Add event, a column tap presets the day, a chip tap still opens the event.
- **Roborazzi**, dark and light: hand-off 10 (the sheet at 480 dp, as with the keyboard up), 11, 12, 13, 14, 15, 16; the locked multi-day edit; the Today card header with +; the Calendar header with Add event and the column hints; the detail footer with Edit.
- **Housekeeping (D13):** `SilentToaster` moves to test sources; `CalendarSync`'s internal constructor has no defaults.
- **Emulator walkthrough**, before the user's screenshot checkpoint, on `Culvery_Tablet_API_30` (Google Play image) if it is in place, otherwise API 35:
  - add an event with the PIN; edit it; delete it from the editor;
  - a Child signed in: disabled chips, and a save for Family refused;
  - keyboard: focus on open, Done, Save visible with the keyboard up, in kiosk mode;
  - add offline (fake unreachable), open it, edit it, delete it before it syncs, then go online: nothing remains.

## 8. Review focus
Inputs a person will hit, for the plan's reviewers:
- The keyboard in kiosk mode covering Save, or the toast behind the keyboard.
- Editing or deleting an event created offline before it syncs.
- A Child saving an event for Family, with and without a session.
- A timeout after Google actually created the event: no duplicate.
- Household-zone day boundaries and clock changes when building drafts: "Tomorrow" at 23:30, an event on 25 Oct 2026.

## 9. Out of scope
- Recurring events; reminders, location, notes and attendees.
- Creating multi-day events, or changing a multi-day event's dates.
- Choosing a calendar other than the master.
- A key → id map for writers that can't choose their event ids.
- The real Google provider and its writer (Plan 3).
