# Culvery 2b-1: Change events (design)

**Date:** 2026-09-24
**Status:** Draft for review
**Parent spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§5, §6, §7, §8, §9)
**Design:** `docs/design/house_hub_handoff/README.md` §7 ("Calendar sheets") and `screenshots/calendar-sheets/`
**Builds on:** Plan 2a (calendar read path), merged to `main`.

## 1. Scope

Plan 2 part b is split in two:
- **2b-1, change events (this document):** the write contract, the outbox, permissions, the redesigned PIN pad, the 2-minute status-bar session, toasts, the event detail sheet with **Delete** and **Assign**, the badges, and the deferred follow-ups.
- **2b-2, add and edit (next):** the quick-add/edit sheet, the date and time pickers, the **Edit** button, the + / "Add event" / column add hints, and keyboard handling.

Writes are exercised against the debug fake provider only. Google writes arrive in Plan 3 against the same contract.

## 2. Decisions (agreed with the user)

| # | Decision |
|---|---|
| D1 | **Session:** 2 minutes after the last authorised action, i.e. a PIN-gated action that succeeded (was 60 s after the last touch). Touching the screen does not extend it, so a child tapping around can't keep an adult's session alive (amended after the plan review, U1). Shown in the status bar as `account_circle · {name} · {role}` with a **Sign out** link. The Plan 1 rail chip is removed. Fresh-PIN permissions (`kiosk.exit`, `people.manage`) are unchanged. |
| D2 | **PIN pad:** adopt the hand-off §7 design: lock badge, "Who's this?", reason text, 76 dp keys, a Cancel key, a `danger` error line, digits that clear on a wrong PIN, and a 400 dp `surf` card. When a sheet is open it is placed over the sheet area, otherwise it is centred. |
| D3 | **Save flow:** try the provider directly for up to 10 s. Accepted → done. Rejected → the sheet stays open with the error and the input kept. Offline or timed out → queue in the outbox and show the syncing badge. A later rejection is rolled back with a toast. |
| D4 | **Split:** 2b-1 then 2b-2, each ending at a screenshot checkpoint. |
| D5 | **Master calendar:** choosing it is part of Plan 4's setup. In 2b-1 the debug seed marks the fake "Family" source as the master and makes it writable. |
| D6 | Remove the "Week ›" link from the Coming up card. Today's Week pill and the rail tab are enough. |

## 3. Architecture

### 3.1 Shell (`:core:plugin`, `:app`)
- **Overlay host:** `LocalOverlayHost` with `show(content: @Composable () -> Unit)` and `dismiss()`. The shell draws one overlay layer above the rail and content, with a scrim of `rgba(0,0,0,.55)` that dismisses on tap. The PIN pad and toasts draw above the overlay. Capabilities open sheets through this host and never touch `:app`.
- **Toaster:** `LocalToaster.show(message: String, icon: String = "info")`. A bottom-centre pill: background `ink`, text `bg`, 16 sp/600, padding 14×22, radius 26, `info` icon, 28 dp from the bottom. It auto-hides after 3.5 s. A new toast replaces the current one.
- **Status bar:** when a session is active, the status bar shows `account_circle` + "{name} · {role}" + "Sign out" (accent, 12 sp/700). Tapping Sign out locks. The rail session chip is removed.

### 3.2 Access (`:core:access`)
- **Session timeout:** `SESSION_TIMEOUT_MS = 120_000`.
- **New `authorise` parameters** (additive, with defaults so existing callers compile unchanged):

  ```kotlin
  suspend fun authorise(
      vararg anyOf: String,
      reason: PinReason = PinReason.Generic,
      allow: (Identified, granted: Set<String>) -> Boolean = { _, g -> g.isNotEmpty() },
  ): Authorised?
  ```

  - `reason` feeds the PIN pad's reason text: Generic, Save, Edit, Delete, Assign.
  - `allow` runs on the session shortcut and after each PIN. If it returns false, the result is the existing `NotAllowed` path. `NotAllowed` now carries a message so the calendar can supply the design's wording.
- **Refusal toast:** a refusal while a sheet is open shows as a **toast**, not only as the PIN pad error. The PIN pad dismisses, and the calendar shows the toast from the `NotAllowed` reason.
- **Refusal on the session shortcut:** when a signed-in person is refused with a toast, the session is not extended; it is **locked** after the toast, so the next tap brings up the PIN pad and someone else can take over (amended after the plan review, U2).

### 3.3 Calendar contract
```kotlin
data class EventDraft(val title: String, val start: EventTime, val end: EventTime, val forPerson: String?, val createdBy: String?)
interface CalendarWriter {
    val providerId: String   // equals the matching CalendarProvider.descriptor.id
    suspend fun create(conn: Connection, source: CalendarSource, draft: EventDraft): RemoteEvent
    suspend fun update(conn: Connection, source: CalendarSource, remoteId: String, draft: EventDraft): RemoteEvent
    suspend fun delete(conn: Connection, source: CalendarSource, remoteId: String)
}
class WriteRejectedException(message: String, cause: Throwable? = null) : Exception(message, cause)
```
- **Writing:** implemented only by providers declaring `Feature.WRITE`, and bound with `@IntoSet Set<CalendarWriter>`. The engine matches a writer to its provider by `providerId == descriptor.id`.
- **Errors:**
  - `WriteRejectedException`: permanent rejection (Google 4xx other than auth).
  - `NeedsSignInException`, `UnreachableException`: as for reads.
  - The same 60 s timeout, IO dispatch and cancellation rules as `CalendarSync` apply. The editor's attempt timeout is 10 s (D3).
- **Tags:** `forPerson`/`createdBy` are household person ids, stored by the provider (Google: `extendedProperties.private`). Names are never written.
- **Contract suite:** gains write checks, gated on `Feature.WRITE`:
  - create → the next sync returns the event with equal title, times and tags
  - update → changed fields round-trip
  - delete → a removal on the next incremental sync, or absence on a full sync
  - a draft with an unknown source is rejected

  The self-test gains a broken writer fixture that drops tags.

### 3.4 Storage (`calendar.db` v2)
The schema moves from version 1 to version 2 with a `Migration(1, 2)` that adds:
- `source.isMaster INTEGER NOT NULL DEFAULT 0`. At most one master: setting a master clears the others in the same transaction.
- a new `outbox` table:

  ```
  id INTEGER PK AUTOINCREMENT
  connectionId, sourceId
  remoteId TEXT NULL        -- null for create
  kind TEXT                 -- CREATE | UPDATE | DELETE
  draftJson TEXT NULL
  attempts INTEGER
  nextAttemptMillis INTEGER
  createdMillis INTEGER
  ```

- a `MigrationTestHelper` test (v1 → v2) that preserves connections, sources, events and cursors.

The `event` table stays a **pure mirror of the provider**. Pending changes live only in `outbox`.

### 3.5 Editor and outbox
`CalendarEditor` has `delete(ref)`, `assign(ref, person)`, and (in 2b-2) `save(draft, existing?)`. Each operation:
1. Resolves the target event and the master source. Only master-calendar, non-recurring events are editable.
2. Calls `authorise` with the right permission set, reason and `allow` rule (§3.6).
3. Tries the writer with a 10 s timeout:
   - **Accepted** → upsert the returned `RemoteEvent` into the mirror, or remove it for a delete. Returns `Done`.
   - **`WriteRejectedException`** → returns `Rejected(message)`. Nothing changes.
   - **Unreachable, timeout or `NeedsSignIn`** → enqueue in the outbox. Returns `Queued`. `NeedsSignIn` also flags the connection.
4. Nudges the sync loop (`requestSync()`).

**Outbox drain:** the drain runs at the start of each sync pass, under the same single-writer guard. For each due entry:
- **Success** → remove the entry and apply the result to the mirror.
- **Rejected** → remove the entry and emit a `WriteFailed(message)` event. The UI shows it as a toast ("Couldn't save to Google Calendar — {reason}").
- **Unreachable** → `attempts++` and schedule the next attempt with backoff (30 s, 1 min, 2 min, 5 min max).

**Queued changes on screen:** the repository merges pending outbox entries over the mirror:
- a create or update shows the draft's values with `syncing = true`;
- a delete hides the event.

**The loop** gains `requestSync()`: a `MutableSharedFlow` merged with the connection-change trigger. A `Mutex` guarantees a single writer (deferred items C6 and R12).

### 3.6 Permissions (`CalendarPermissionSource`, spec §8)
| Permission | ADMIN | ADULT | CHILD |
|---|---|---|---|
| `calendar.event.create` (anyone) | ✓ | ✓ | |
| `calendar.event.create.self` | ✓ | ✓ | ✓ |
| `calendar.event.edit` (any master event) | ✓ | ✓ | |
| `calendar.event.edit.own` | ✓ | ✓ | ✓ |
| `calendar.event.assign` | ✓ | ✓ | |

- **Delete** asks for `edit` or `edit.own`. The `allow` rule requires `edit`, or `edit.own` with `createdBy == identified.person.id`.
- **Assign** asks for `assign`. The rule is the default (holds the permission).

**Refusal wording** (hand-off §7):
- "{Name} can only change events they created."
- "{Name} can only add events for themselves."
- "Ask an adult to assign this event."

### 3.7 Identity and keys
`EventRef(connectionId, sourceId, remoteId)` replaces `EventUi.key` everywhere. `EventUi` gains:
- `ref`
- `sourceName`
- `editable: Boolean`
- `readOnlyReason: ReadOnlyReason?` (OtherCalendar, Recurring)
- `untagged: Boolean`
- `syncing: Boolean`
- `createdBy: Person?`, or "Added from phone" when untagged, or "Calendar feed" when the source isn't writable

## 4. Screens (hand-off §7 values)

### 4.1 Opening the sheet
Tapping a Today row (rows become buttons) or a week chip opens the **event detail sheet** through the overlay host.

### 4.2 Event detail sheet
The sheet is 600 dp wide on the right, with padding 28×30×26 and a 48 dp close button.
- **Header:** 6 dp colour bar + title at 34 sp/700, −0.5 tracking. While a change is pending, a **"Syncing to Google…"** pill sits above the title (30 dp, radius 15, `surf2`, `cloud_upload`).
- **Info card** (`surf`, radius 22, rows at least 58 dp, `line` dividers; icon 22 dp muted, label 15 sp muted in a 104 dp column, value 17 sp/600):
  - When: "Today · 19:30–21:00" or "Sat 26 Sep · All day"
  - For: a colour dot and the name
  - Created by
  - Calendar
  - Repeats: recurring events only
- **States:**
  1. **Editable:** a footer with **Delete** (60 dp, `surf2`, `danger` text, `delete` icon). The Edit button arrives in 2b-2.
  2. **Read-only, other calendar:** a 🔒 card, "From {source} (read-only)" / "This is a subscribed calendar, so it can't be changed here." No footer.
  3. **Read-only, recurring:** an `event_repeat` card, "Repeating event" / "Edit repeating events in Google Calendar on your phone." No footer.
  4. **Untagged** (master, added from a phone): an `accentSoft` card with `smartphone`, "Added from a phone" / "Showing as Family until someone assigns it." Its **Assign to…** button expands 48 dp person chips. Picking one → PIN (reason Assign) → tagged, syncing. Delete remains available.
  5. **Delete confirmation:** replaces the footer. It is a `dangerSoft` panel: "Delete this event?" and "'{title}' will be removed from Google Calendar for everyone." It has two buttons: **Keep event** on the left (`surf`) and **Delete event** on the right (`danger`, `delete_forever`). After the delete, the sheet closes with the toast "Event deleted". The PIN guard runs before the confirmation appears, so deleting takes guard + Delete + confirm.

### 4.3 Badges
In week chips (15 dp, muted, right of the time) and Today rows (20 dp):
- `cloud_upload`: syncing
- `lock`: other calendar
- `repeat`: recurring

When more than one applies, show `cloud_upload` first, then `lock` or `repeat`.

### 4.4 Other changes
- **PIN pad:** D2.
- **Toasts:** §3.1 and §3.6.
- **Status bar:** D1.
- **Coming up:** remove the "Week ›" link (D6).

## 5. Errors and offline
- **Delete while offline** → the event hides at once and the delete is queued. If a later rejection comes back, the event reappears with a toast.
- **Assign while offline** → the new person shows at once with the syncing badge.
- **Writer missing for a WRITE provider** (a misconfiguration) → the event is treated as read-only and the problem is logged.
- **Sign-in expired during a write** → the change is queued, the connection is flagged `NeedsSignIn`, and the reconnect chip appears. The queue resumes after reconnect.

## 6. Testing
- **Access:**
  - the session lasts 2 minutes after the last authorised action; touches alone don't keep it alive
  - a refusal on the session shortcut toasts, then locks
  - `reason` reaches the PIN pad
  - `allow` is applied on both the session shortcut and after a PIN
  - `NotAllowed` carries the message
- **Contract suite:** write checks, gated by feature, plus a self-test fixture that drops tags.
- **Fake provider:** an in-memory write store, with switches for reject-next-write and unreachable-next-write. It passes the full suite, including the write checks.
- **Store:** v1 → v2 migration test, the master flag, and outbox CRUD.
- **Editor:** each branch (Done, Rejected, Queued) for delete and assign; queued retries with backoff; a drain rejection emits `WriteFailed`; the `allow` rules for child, adult and admin.
- **Repository:** the outbox overlay (hide on delete, syncing on assign), `editable` and `readOnlyReason` for each source and recurrence case, `EventRef`.
- **UI:** detail sheet states, delete confirmation order, badges, the PIN pad redesign, the toast, and the status-bar sign-in.
- **Roborazzi:** screenshots of the sheet states, dark and light where the design has both.
- **Deferred 2a tests:**
  - an unknown health code maps to Error
  - the midnight-end label rule
  - real cancellation propagates through sync and the loop
  - an id change mid-sync
- **Emulator walkthrough on an API 30 Google Play AVD** (see §7), including a DM Sans weight and truncation check.

## 7. Environment
The Android 11 "Google APIs" image has Play services from 2020. For the 2b-1 walkthrough, replace `Culvery_Tablet_API_30`'s image with **Android 11 "Google Play"**. That is a user step in Android Studio's SDK Manager. Until then, the walkthrough runs on `Culvery_Tablet_API_35`.

## 8. Out of scope (2b-2 or later)
- 2b-2: the quick-add/edit sheet, the Edit button, the pickers, the + buttons, "Add event", the column add hints and keyboard handling.
- Plan 3: Google writes.
- Plan 4: master calendar selection in setup.
