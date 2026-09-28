# Culvery 3a: Google Calendar and crash-proofing (design)

**Date:** 2026-09-28
**Status:** Approved
**Parent spec:** `docs/superpowers/specs/2026-09-23-culvery-v1-design.md` (§5, §6, §7, §9.6, §9.7, §12)
**Previous plan:** `docs/superpowers/specs/2026-09-25-culvery-2b2-add-edit-events-design.md` (2b-2, add and edit events)
**Follow-ups:** `docs/superpowers/plans/2026-09-23-plan1-followups.md` (every "For Plan 3" item; §8 says where each goes)
**Spike:** branch `spike/google-auth`, `SpikeGoogleAuthActivity.kt` (Play services `AuthorizationClient`, calendar scopes)
**Builds on:** Plan 2b-2, merged to `main`.

## 1. Scope

Plan 3 is split. 3a (this spec) adds:
- `:provider:calendar-google`: Google Calendar API v3, read and write, signing in with Android's standard account flow;
- connecting Google from Settings and from the Connect-a-calendar card, with automatic source setup (no mapping UI);
- a daily source refresh that follows the calendars ticked in Google;
- reconnecting from the "Google needs reconnecting" chip;
- the crash-proofing work (R3 and the store-failure follow-ups);
- the Google-specific follow-ups (touched-field edits, lost-reply creates, aged creates, deleted events);
- `calendar.db` v4;
- docs: `docs/setup/google-calendar.md` (publishing status, §3.13) and README "Adding a calendar provider" (the contract changes in §3.10).

The direct ICS provider is deferred, not a "3b" (D1). Google becomes the first provider in release builds; the fake stays debug-only.

## 2. Decisions (agreed with the user)

| # | Decision |
|---|---|
| D1 | **Split.** Plan 3 → 3a = Google + crash-proofing. The direct ICS provider is **deferred**: external feeds (school terms, clubs, published Outlook or iCloud calendars) are subscribed inside Google Calendar ("Add calendar › From URL") and arrive as read-only sources of the Google connection. Trade-offs accepted: Google refreshes subscribed feeds every ~8–24 h, which nobody can change; subscriptions are one-way and read-only; published Outlook/iCloud links are unauthenticated; Microsoft 365 admins may block publishing; a household without Google needs a future ICS, Outlook or CalDAV provider, which the provider architecture keeps possible. |
| D2 | **Sign-in** is Android's standard flow through Play services `AuthorizationClient` (Identity API), as in the spike: the system account chooser ("Choose an account" / "Add another account") then Google's consent, for `calendar.readonly` + `calendar.events`. The connection's config stores only the account email. No tokens or secrets are stored on the tablet: each call gets a short-lived access token from Play services, refreshed silently. When Play services needs the user (a resolution), the call throws `NeedsSignInException` and the connection's health becomes `NeedsSignIn`. |
| D3 | **Where.** "Connect Google Calendar" appears in the Settings placeholder and, when no connection exists, on the Connect-a-calendar card. Both ask for an Admin PIN (`settings.manage`, §3.3). One Google connection in 3a. |
| D4 | **Automatic setup, no mapping UI.** Every calendar in the account's calendarList becomes a source. Visible = `selected` and not `hidden` in Google. Writable = `accessRole` `owner` or `writer`. The account's primary calendar becomes the master and maps to Family. Every other calendar maps by name to a household person (its summary names exactly one person, e.g. "Mia" or "Mia's swimming"), else to Family. Label "Google". The first full sync is requested at once. Editing mappings, visibility and the master is Plan 4. |
| D5 | **Debug builds.** Connecting Google removes the sample (fake) connection with its events, sync state and outbox, so sample and real events never mix. The store gains `removeConnection`, and removing a connection or a source removes its events, sync state and outbox rows (the Plan 4 "calendar sources" clean-up, pulled forward). |
| D6 | **Reconnect.** The "Google needs reconnecting" chip runs the Android flow for the same account, behind the Admin PIN, instead of opening Settings. On success the health becomes Ok, a sync is requested, and that connection's queued changes are made due now (follow-up m4). |
| D7 | **Daily source refresh**, and on app start: re-read calendarList; add newly ticked calendars (visible, mapped by name or Family); hide unticked ones; remove deleted ones with their events and sync state; keep existing person mappings. If the master disappears or becomes read-only, clear it (the add buttons hide, as today) and toast "Google Calendar: can't find the master calendar, so new events can't be added". |
| D8 | **HTTP stack:** OkHttp + kotlinx.serialization (with its Gradle plugin); OkHttp `MockWebServer` with a `Dispatcher`-based fake Google server for tests. Every call is cancellable: cancelling the coroutine cancels the OkHttp call, so the editor's 10 s and the drain's and sync's 60 s timeouts hold (follow-up R9, with its contract check). Versions pinned in the catalog; no deprecated APIs. |
| D9 | **Google API behaviour.** `calendarList`; `events.list` with `singleEvents=true` over the existing window (yesterday to 14 days ahead), `syncToken` for incremental syncs, 410 → full replace, `nextPageToken` paging. Create = `events.insert` with `id = clientKey`; 409 → fetch and return the existing event. Update = PATCH of title, times and tags only. Delete: 404 and 410 are success. Person tags in `extendedProperties.private` (`culvery.person`, `culvery.createdBy`, parent §6) and `colorId` = the Google event colour nearest the person's colour. Cancelled instances → `removedIds`. `recurringEventId` → `recurring = true`. |
| D10 | **Error mapping.** 401 → refresh the token once, then `NeedsSignIn`. 429, 403 `rateLimitExceeded` / `userRateLimitExceeded`, 5xx, `IOException` and timeouts → `Unreachable`. Other write refusals → `WriteRejectedException` with fixed, friendly wording (follow-up R8), never Google's raw text. |
| D11 | **Wording.** The service name, "Google Calendar", in failure, repeating-event and delete-confirmation copy. The short connection label ("Google") stays on the syncing pill and the reconnect chip (follow-up U3). |
| D12 | **Repeats row** (follow-up DL1): "Every day", "Every week", "Every 2 weeks", "Every month", "Every year" (generally "Every {n} {unit}s") from the series' RRULE when it is simple (§3.5), else "Yes". |
| D13 | **Crash-proofing** (R3 and the folded follow-ups): a logging `CoroutineExceptionHandler` on `@ApplicationScope`; per source, catch `Throwable` minus `CancellationException` → health Error, logged with its cause; store calls outside the per-source try; `ShellViewModel`'s capability flows and `connectionIds()` use `retryWhen` with backoff instead of a terminal `catch`; the drain backs off after a failed drain (m2); a drain write the provider accepted but the tablet couldn't store is retried later, not left due (C2); the drain/editor orphan race is closed; Try again after a failed queue write reuses the key; the add/edit sheet closes with a toast when it can't load; the editor's Delete failure uses delete wording; `NeedsSignIn`, `Unreachable` and `Error` are logged with their cause; a counting-DAO test for SQLite's 999-variable limit. |
| D14 | **Google-specific follow-ups.** C3: an edit PATCHes only the fields the user touched (the form tracks them; they are applied to the event as re-read under the write lock; queued offline edits too). m3: a queued ASSIGN for an event outside the window fetches it from Google. C9: an aged (48 h) create is looked up by its key before it is dropped. C10: contract rule "a create never recreates a deleted event", with its check. |
| D15 | **Testing.** Fake-Google-server tests for every mapping and error; the contract suite against the Google provider (through the fake server); crash-proofing tests; an emulator walkthrough with the user's real Google account on the API 35 Play image; the user's screenshot checkpoint (§7). |

## 3. Architecture

### 3.1 Module `:provider:calendar-google`
Set up as README "Adding a calendar provider" step 1, plus:
- `play-services-auth` (`Identity.getAuthorizationClient`), `kotlinx-coroutines-play-services` (`Task.await()`, which is cancellable), `okhttp`, `kotlinx-serialization-json` and the `org.jetbrains.kotlin.plugin.serialization` plugin; test: `mockwebserver`. All pinned in `gradle/libs.versions.toml`. `GoogleSignIn`, `GoogleSignInAccount` and other deprecated sign-in APIs are not used.
- `GoogleCalendarProvider` implements `CalendarProvider` and `CalendarWriter`, bound `@IntoSet` for both. Descriptor: id `calendar.google`, display name "Google Calendar", icon `calendar_month`, `READ` + `WRITE`.
- `GoogleApi`: the HTTP calls, the JSON models and the error mapping (§3.7). Its base URL and `TokenSource` are constructor parameters, so tests point it at the fake server with a fake token source.
- `TokenSource` (`suspend fun token(account: String): String`, `suspend fun invalidate(token: String)`): the production one is `PlayServicesTokenSource` (§3.2).
- `:app` takes the module as `implementation` in every build type; the fake stays `debugImplementation`.

**Cancellable HTTP (R9).** Every request runs through one helper that enqueues the OkHttp `Call` inside `suspendCancellableCoroutine` and cancels the call from `invokeOnCancellation` (or OkHttp's own coroutine `executeAsync`, if the pinned version ships it). Nothing calls the blocking `execute()`. OkHttp's own timeouts (connect 15 s, read 30 s) sit inside the engine's, so the engine's timeout decides.

**JSON.** One `Json { ignoreUnknownKeys = true; explicitNulls = false }`. PATCH bodies are built with `buildJsonObject`, so a field is sent only when it is touched, and an explicit `null` is sent only to clear a field (§3.6).

### 3.2 Sign-in and tokens (D2)
- **Request:** `AuthorizationRequest` with the two scopes; for an existing connection, `setAccount(Account(email, "com.google"))`, so the chooser is skipped and the token is for that account.
- **Silent calls:** `PlayServicesTokenSource.token(email)` calls `authorize(request).await()` on the application context. A result with a token → the token. A result that `hasResolution()` → `NeedsSignInException`. An `ApiException` with a network status → `UnreachableException`; any other `ApiException` → `NeedsSignInException`. Play services caches and refreshes tokens, so every API call asks for a token; nothing is stored by Culvery.
- **401:** `invalidate(token)` clears it from Play services' cache (`AuthorizationClient.clearToken`; the plan pins a `play-services-auth` version that has it), then the call is retried once with a fresh token. A second 401 → `NeedsSignInException`.
- **Interactive flow** (connect and reconnect) lives in the provider's `ConnectScreen`: it launches the resolution's `PendingIntent` with `rememberLauncherForActivityResult(StartIntentSenderForResult())` and reads the result with `getAuthorizationResultFromIntent`. If `authorize` returns a token without a resolution (already granted), no system UI appears.
- **Which account:** after the flow, the provider calls `GET calendars/primary`; the primary calendar's id is the account email. That becomes `config["account"]`. On reconnect, a different email than the stored one is refused: the toast "That's a different Google account. Reconnect with {email}." and nothing changes.

### 3.3 Connecting (D3, D4, D6)
- **Who may:** `authorise(CorePermissions.SETTINGS_MANAGE)`, which only an Admin has. It isn't a fresh-PIN permission, so an Admin already signed in isn't asked again, as with opening Settings today.
- **Connectable providers:** `ProviderDescriptor` gains `userConnectable: Boolean = true`; the fake sets it false. "Connect {displayName}" is offered for each connectable provider with no connection yet, so in 3a just "Connect Google Calendar", and it goes once Google is connected.
- **`CalendarConnectHost`** (`:capability:calendar`, shown through `LocalOverlayHost`): authorises, then draws the provider's `ConnectScreen` (a centred `surf` card, "Connecting to Google Calendar…", with Cancel, over the scrim, while the system screens run). `onConnected` → `CalendarSetup.connectWithDefaults(connection)` for a new connection, or `CalendarSetup.reconnect(connection)`; `onCancel` → dismiss, no toast.
- **`connectWithDefaults`** (D4): reads `provider.sources()` through the IO + 60 s timeout wrapper (the Plan 4 follow-up for `connect`, pulled forward; `setMaster` gets the same wrapper), builds the mapping with `defaultMapping` (§3.4) from the household's people, adds the connection and sources in one transaction, makes the primary the master (`setMaster` clears any other), requests a sync, and toasts "Google Calendar connected". A failure → "Couldn't connect to Google Calendar — try again", and nothing is stored.
- **`reconnect`** (D6): keeps the connection id and config; sets health Ok; `store.makeDue(connectionId, now)` sets `nextAttemptMillis = now` on that connection's outbox rows without touching `attempts`; requests a sync; toasts "Google Calendar reconnected".
- **Where:** the Settings placeholder gains a Calendars row per connection ("Google Calendar · {email}", its health in words, and **Reconnect** when it needs signing in) and the Connect button (§4.1). The Connect-a-calendar card's button becomes the Connect button (§4.2). The reconnect chip opens `CalendarConnectHost` for its connection (§4.3).

### 3.4 Sources, default mapping and the daily refresh (D4, D7)
- **`CalendarSource`** gains `shown: Boolean = true` (ticked and not hidden in the service) and `primary: Boolean = false` (at most one per connection).
- **`defaultMapping(source, people)`**, a pure function in `:capability:calendar`: the primary → Family. Otherwise, the people whose name appears in the summary as a whole word, ignoring case, with a trailing "'s" allowed ("Mia's swimming"); exactly one → that person; none or several → Family. Visible = `shown`, except that the primary is always visible, so events added on the tablet can be seen.
- **`SourceRefresher`** (`:capability:calendar`, used by `CalendarSync` and `CalendarSetup`) refreshes a connection when it hasn't been refreshed in this process yet (app start), when `sourcesCheckedMillis` is more than 24 h old, or when a sync flagged it (§3.5, a calendar gone). It runs at the start of that connection's part of a pass, reading `sources()` through the IO + timeout wrapper. A failure is logged and retried at the next pass; the sync still runs on the sources already stored. In one store transaction (`store.refreshSources`):
  - new source → added with `defaultMapping`;
  - existing source → name and writability updated; visibility follows `shown` (the primary stays visible); the person mapping is kept;
  - source no longer listed → removed with its events, sync state and outbox rows;
  - the master removed, or now read-only → master cleared, and the store reports it so the refresher toasts D7's message once;
  - `sourcesCheckedMillis` = now.
- In 3a visibility simply follows Google's ticks on every refresh. Whether a tablet-side choice overrides them is Plan 4's question, with its visibility UI.

### 3.5 Reading (D9, D12)
- **`sources`:** `GET users/me/calendarList`, paged. Id = calendar id; name = `summaryOverride` if set, else `summary`; `writable` = `accessRole` in (`owner`, `writer`); `shown` = `selected == true && hidden != true`; `primary` = `primary == true`.
- **Full sync** (no cursor): `GET calendars/{id}/events?singleEvents=true&timeMin=…&timeMax=…&maxResults=250`, following `nextPageToken`. `timeMin` and `timeMax` are the window's instants in the household zone. Returns `fullReplace = true` and the last page's `nextSyncToken` as the cursor.
- **Incremental sync:** the same call with `syncToken` in place of `timeMin`/`timeMax` (Google refuses both together). `fullReplace = false`. Upserts may lie outside the window (README contract).
- **410 Gone** on an incremental sync → the provider runs a full sync itself and returns it with `fullReplace = true`.
- **404 or 403 (not a rate limit)** on `events.list` → `SourceGoneException`, a new subclass of `UnreachableException` (§3.10). The engine records the source as Unreachable and flags the connection for a source refresh on the next pass, which removes it if it has gone.
- **Mapping one event:**
  - `status == "cancelled"` → its id goes in `removedIds`.
  - `eventType == "workingLocation"` → skipped (in a full sync) or removed (in an incremental one): it isn't an event people plan around.
  - `start.dateTime` → `EventTime.Timed`; `start.date` → `EventTime.AllDay` (Google's end date is already exclusive). Same for `end`.
  - `summary` missing or blank → "(No title)", as Google shows it.
  - `recurringEventId` present → `recurring = true`.
  - `extendedProperties.private["culvery.person"]` → `forPerson`; `["culvery.createdBy"]` → `createdBy`.
- **Recurrence rule (D12).** `RemoteEvent` gains `recurrenceRule: String?`: the series' `RRULE:` line. Instances don't carry it, so the provider fetches each series once with `events.get(recurringEventId)` and caches the rule in memory per connection; a full sync clears the cache. A failed fetch leaves the rule null (the row reads "Yes") and doesn't fail the sync.
- **"Simple" rule and its label** (`repeatsLabel(rule, start)` in `:capability:calendar`): only `FREQ`, `INTERVAL`, `UNTIL`, `COUNT` and `WKST`, plus a `BYDAY` of one day equal to the event's weekday (weekly) or a `BYMONTHDAY` equal to its day of month (monthly), which only restate the start and which Google writes for its own "Weekly on Tuesday". Labels: interval 1 → "Every day" / "Every week" / "Every month" / "Every year"; otherwise "Every {n} days" / "weeks" / "months" / "years". Anything else, or no rule → "Yes". The end of a series (`UNTIL`, `COUNT`) isn't shown.

### 3.6 Writing (D9, D14)
- **Create:** `POST calendars/{id}/events` with `id = clientKey`, `summary`, `start`/`end`, `extendedProperties.private` {`culvery.person`, `culvery.createdBy`} (a null tag is left out) and `colorId` (§ below). Timed times are sent as UTC `dateTime` with no `timeZone`, so Google shows them in the calendar's own zone; all-day as `date`.
- **409 on create** → `GET` the event by the key. Found and not cancelled → return it (the lost-reply retry, 2b-2 §3.4). Cancelled, or 404/410 → `WriteRejectedException(EVENT_GONE)`: **a create never recreates a deleted event** (C10, §3.10).
- **Update (C3):** `CalendarWriter.update` gains `fields: Set<EventField>` (`TITLE`, `TIMES`, `FOR_PERSON`). The PATCH carries only those: `summary`; `start` and `end` (switching between timed and all-day sends the other key as `null`, e.g. `{"date": "2026-10-01", "dateTime": null}`); `extendedProperties.private["culvery.person"]` and `colorId`. Google merges the keys of `extendedProperties.private` on PATCH, so `culvery.createdBy` and anything else there is kept; the fake-server test pins that assumption and the walkthrough checks it on a real account. `createdBy` is never patched. The PATCH response is the returned event.
- **Where fields come from:** `EventForm` records which of Title, Who and Day/Time/Length the user changed (`touched`). `CalendarEditor.update` builds the draft under the write lock from the event as re-read there, with the touched fields from the sheet laid over it, so the mirror and the queue show the right event. The outbox stores the fields with the change (§3.11). The drain sends a queued UPDATE with its stored fields, so a phone change to an untouched field, made while an offline tablet edit waits, is kept.
- **ASSIGN** is sent as an update with `fields = {FOR_PERSON}`. When its event isn't in the mirror (it left the window, m3), the drain calls `writer.find` first: found → send it; not found → dropped with `EVENT_GONE`. A PATCH isn't sent blind, because a PATCH can bring a cancelled event back.
- **`colorId`:** the nearest of Google's 11 fixed event colours (`colors.get` ids 1–11, a table in the provider) to the person's colour by RGB distance. `EventDraft` gains `forPersonColor: Long?`, set by the editor from the household; null for Family or untagged, which sends no `colorId` on a create and clears it (`null`) on a Who change.
- **Delete:** `DELETE calendars/{id}/events/{eventId}`; 404 and 410 → success.
- **`find`:** `GET calendars/{id}/events/{eventId}` → the event, or null for 404, 410 or `status == "cancelled"`.
- **Aged create (C9):** at 48 h, before dropping a create, the drain calls `writer.find(clientKey)`. Found → the create is completed (the mirror gets the event, the row goes), and the changes queued behind it are sent in this pass whatever their age, since they describe an event that exists. Not found → dropped with its followers, as today. `find` unreachable → the row stays and the check repeats next pass.

### 3.7 Error mapping (D10)
| Response | Reads (`sources`, `sync`, `find`) | Writes |
|---|---|---|
| 401 | refresh once (§3.2), then `NeedsSignInException` | same |
| 429; 403 `rateLimitExceeded` / `userRateLimitExceeded`; 5xx; `IOException`; timeout | `UnreachableException` | `UnreachableException` |
| 410 on `events.list` | full resync (§3.5) | — |
| 404, other 403 on `events.list` | `SourceGoneException` | — |
| 409 on insert | — | fetch (§3.6) |
| 404, 410 on update | — | `WriteRejectedException(EVENT_GONE)` |
| 404, 410 on delete | — | success |
| other 403 on a write | — | `WriteRejectedException("this calendar can't be changed from the tablet")` |
| any other 4xx | `UnreachableException` (logged with Google's message) | `WriteRejectedException("the change was refused")` |
| a body that doesn't parse | `UnreachableException` (logged) | `UnreachableException` (logged) |

Google's own message text is logged, never shown. The toast reads, for example, "Couldn't save to Google Calendar — the change was refused".

**`NeedsSignIn` from a write.** `WriteOutcome.Retry` gains `needsSignIn`; when set, the editor or the drain also sets the connection's health to `NeedsSignIn`, so the reconnect chip shows at once rather than at the next sync. The change is queued and backs off as any retry; the reconnect makes it due (D6). A change still unsent after 48 h is dropped as today.

### 3.8 Store (D5, D7)
- `removeConnection(id)`: one transaction deleting the connection and its sources, events, sync state and outbox rows.
- `refreshSources(connectionId, sources, mappingForNew)`: §3.4; returns whether the master was cleared.
- `clearMaster()`, used by `refreshSources` when the master goes; `setMaster` already clears any other master when `connectWithDefaults` sets the primary.
- `makeDue(connectionId, nowMillis)`: D6.
- `applySync` and `applyAccepted` return early, writing nothing, when the source row is gone, so a removal racing an in-flight sync or write leaves no orphan rows or stale cursor.
- The Plan 4 follow-up's foreign keys are not added: the store is the only writer of `calendar.db` and deletes the dependent rows itself in the same transaction, and the early returns cover the race. That follow-up closes.

### 3.9 Debug builds (D5)
`seedDebugData` keeps seeding the sample connection only when there are no connections. It also collects `store.connections()` on the application scope: whenever a connection other than `debug-sample` exists, it removes `debug-sample`. So connecting Google removes the sample at once, and the sample never comes back on a later start. The debug offline switch still works on the fake until then.

### 3.10 Contract changes (`CalendarContract.kt`, README)
- `CalendarSource(id, name, writable, shown = true, primary = false)`.
- `RemoteEvent` gains `recurrenceRule: String? = null`.
- `EventDraft` gains `forPersonColor: Long? = null`.
- `enum class EventField { TITLE, TIMES, FOR_PERSON }`; `CalendarWriter.update(conn, source, remoteId, draft, fields: Set<EventField>)` changes only those fields; the draft's other fields are ignored.
- `CalendarWriter.find(conn, source, remoteId): RemoteEvent?`, null when the event doesn't exist or was deleted.
- `UnreachableException` becomes `open`; `class SourceGoneException : UnreachableException` means "this source isn't there any more"; the engine responds with a source refresh.
- `ProviderDescriptor.userConnectable` (`:core:plugin`, §3.3).
- **New contract rules and checks** (in `CalendarProviderContractTest`; each gets a broken self-test fixture that must fail it, which also covers the follow-up "more contract-suite self-test fixtures"):
  - a create never recreates a deleted event: create with key K, delete, create with K again → `WriteRejectedException`;
  - an update changes only its `fields`: after an update with `{TITLE}`, the times set by an earlier update with `{TIMES}` are kept, and `createdBy` is kept after any update;
  - `find` returns a created event and null after it is deleted;
  - a write returns promptly when its caller is cancelled (R9): a new optional hook `gateWrites()` holds the service's reply; the check cancels the caller and requires the call to finish within 1 s. On the cooperative fake it proves little; on Google through the fake server it proves the OkHttp call is cancelled;
  - `sources` reports at most one `primary`.
- The fake is brought in line: `update` applies only `fields`, `find` exists, a repeated key after a delete is refused, `userConnectable = false`.
- README "Adding a calendar provider" is updated to match, including `SourceGoneException` and `find`.

### 3.11 Storage (`calendar.db` v4)
- **`MIGRATION_3_4`**, hand-written:
  - `ALTER TABLE event ADD COLUMN recurrenceRule TEXT`;
  - `ALTER TABLE outbox ADD COLUMN fields TEXT` (a comma-separated `EventField` list; null on an older row means every field for an UPDATE, which is what it sent, and `FOR_PERSON` for an ASSIGN);
  - `ALTER TABLE connection ADD COLUMN sourcesCheckedMillis INTEGER`.
- The v4 schema JSON is committed. `CalendarMigrationTest` gets a v3 → v4 test with rows of every kind and the table list.
- `PendingChange` gains `fields: Set<EventField>?` (UPDATE and ASSIGN only). `EventDraft`'s `forPersonColor` goes in the outbox's draft JSON; an older row without it reads null.

### 3.12 Crash-proofing (D13)
- **Application scope:** `AppModule.applicationScope()` adds a `CoroutineExceptionHandler` that logs with `Log.e` and keeps the process alive; with the existing `SupervisorJob`, one failed child no longer brings down the others.
- **Per source:** `CalendarSync.syncSource` catches `Throwable` except `CancellationException` → `ConnectionHealth.Error`. `NeedsSignIn`, `Unreachable` and `Error` are each logged with their cause and the connection and source names (never tokens).
- **Store calls outside the provider's try:** the cursor read and `applySync` sit outside it, so a store failure isn't reported as the provider's health; it fails the pass, which `runPass` logs, and the next pass retries.
- **Flows:** `ShellViewModel`'s `hasTab` and `cards()` flows and `CalendarSyncLoop`'s `connectionIds` use `retryWhen` with backoff (1 s doubling to 60 s, each failure logged) instead of a terminal `catch`, so a store hiccup no longer leaves the tab or cards gone until restart. The UI still starts from the `onStart` defaults.
- **Drain backoff (m2):** when a drain throws, `CalendarSync` counts consecutive failed drains; the loop's next wait is at least `backoffMillis(failures)` (30 s, 1 min, 2 min, 5 min), whatever `untilNextRetry` says. A drain that completes resets the count.
- **Accepted but not stored (C2):** when the provider accepts a queued write and `applyAcceptedWrite` throws, the drain reschedules the row with `retryLater` (a retry is safe: creates are idempotent by key, updates and deletes by nature). If rescheduling fails too, the drain fails and m2 applies. `CalendarSyncTest.aCreateWhoseMirrorWriteFailsIsRetriedAndMakesOneEvent` changes to expect the row rescheduled, not due, and still one event after the retry.
- **Orphan race:** the editor's write lock moves into a shared `@Singleton CalendarWriteLock`. The drain takes it around dropping a create and the changes behind it, so an edit being queued behind that create either lands before the drop (and goes with it) or sees no create and gets `NotEditable`.
- **Try again reuses the key:** `EventEditorHost` keeps the create's client key after a `Rejected(TRY_AGAIN)`, and the next Save uses it; after any other result the next Save chooses a new key. `CalendarEditor.create` takes the key from the host (2b-2 had the editor choose one per Save tap).
- **Sheet load failure:** `EventEditorHost`'s `produceState` catches a store failure (not cancellation): the sheet closes and toasts "Couldn't open the event — try again".
- **Editor Delete failure:** a store failure in the editor's Delete shows the delete toast the detail sheet uses (`couldNotSave(service, TRY_AGAIN)`), not the Save failure card.
- **Housekeeping:** `EventDetailHost`'s `onEdit = {}` default is dropped, so a new caller can't get a dead Edit button.

### 3.13 Google Cloud publishing status
The 2026-10-01 seven-day check on the spike decides whether the Cloud project stays **Testing** or moves to **In production** (unverified: a warning screen at first sign-in, a 100-user cap, and no weekly expiry of grants). That is a user action in the Cloud console, not code. 3a handles a lapse through D6 either way. `docs/setup/google-calendar.md` §3 is updated with the outcome: which status to choose and why, what the warning screen looks like, and that a Testing project needs reconnecting every 7 days.

## 4. Screens

### 4.1 Settings placeholder
Below the existing text, a **Calendars** block (title 22 sp/700 `ink`):
- per connection, one `surf` row (radius 18, padding 16×20): the provider icon, "{displayName} · {account}" 18 sp/600, and under it the health in words, 15 sp `mute`: "Synced {x} ago", "Can't reach Google Calendar", "Needs reconnecting" (`danger`) or "Something went wrong". With `NeedsSignIn`, a **Reconnect** pill (48 dp, `accent`) on the right runs D6. The fake's row shows too in debug until it is removed;
- a **Connect Google Calendar** pill (48 dp, `accent`, `add` icon) for each connectable provider without a connection (§3.3).
No disconnect, mapping or master controls (Plan 4).

### 4.2 Connect-a-calendar card
Unchanged layout. Body: "Connect your family's calendar to see it here." The primary button becomes **Connect Google Calendar**, which runs §3.3; if no provider is connectable it stays **Open settings**.

### 4.3 Reconnect chip
The label is unchanged ("Google needs reconnecting"). A tap runs D6 for the first connection that needs signing in, instead of opening Settings.

### 4.4 Connecting card
`CalendarConnectHost`: a centred `surf` card, radius 30, padding 26, 440 dp wide, over the scrim: `calendar_month` 32 dp `accent`, "Connecting to Google Calendar…" 22 sp/700, "Choose the family's Google account and allow access." 15 sp `mute`, and **Cancel** (52 dp, full width). The system account chooser and consent screens appear over it.

### 4.5 Copy changes (D11, D12)
- `EventUi` gains `serviceName` (the provider's display name) beside `connectionLabel`.
- Repeating-event note: "Edit repeating events in Google Calendar on your phone."
- Delete confirmation: "“{title}” will be removed from Google Calendar for everyone."
- Failure card and toasts: `couldNotSave(serviceName, reason)`, e.g. "Couldn't save to Google Calendar — try again".
- Syncing pill, reconnect chip and the week subtitle ("synced with Google") keep the label.
- Repeats row: `repeatsLabel` (§3.5).

## 5. Errors and offline
- **Offline** → sync health Unreachable, cached events shown; saves queue as in 2b-2.
- **Google access lapses** (revoked, password change, a Testing-status grant expiring) → the next call gets a resolution or a second 401 → `NeedsSignIn`; the chip shows. Saves meanwhile are queued (§3.7). Reconnect → health Ok, the queue is due now and drains, a sync runs.
- **Lost reply on a create** → queued with its key; the retry's 409 returns the existing event; one event.
- **A phone edit while an offline tablet edit waits** → only the touched fields are sent; the phone's other changes stay.
- **An event deleted on a phone while a tablet create for it was queued** (the create's reply was lost, then the event deleted) → the retry's 409 finds it cancelled → rejected with `EVENT_GONE`, dropped with its followers, one toast.
- **Master calendar gone or read-only** → D7's toast once; add buttons hide; existing events still show.
- **A calendar unticked in Google** → hidden at the next refresh (up to 24 h, or the next app start).
- **A provider throws an `Error`** (e.g. `StackOverflowError`) → that source's health is Error, logged; other sources and connections sync; no crash.
- **Store failure** during a pass → the pass fails, logged; the next pass retries; the drain backs off.
- **Google's own messages** are logged only (§3.7).

## 6. Where the decisions are ambiguous, and what was chosen
- **Admin PIN:** `settings.manage` is Admin-only but not a fresh-PIN permission, so a signed-in Admin isn't asked again; this matches opening Settings.
- **The primary calendar** is always visible and maps to Family, even if unticked or named after a person: it's where the tablet's events go.
- **A summary naming two people** ("Mia & Sam football") maps to Family.
- **Visibility after a refresh** follows Google's ticks each time (§3.4); person mappings are kept.
- **"Simple" RRULE** also allows the one-day `BYDAY` / `BYMONTHDAY` that Google writes for every weekly and monthly event; otherwise almost no real series would get a label. Intervals other than 1 and 2 weeks read "Every {n} {unit}s".
- **Reconnect in Settings:** the Settings row also offers Reconnect, since an Admin looking there for the problem should be able to fix it; it runs the same flow as the chip.
- **Access lapsing for over 48 h** drops queued changes by the existing age rule (with the toast); 3a doesn't extend it.

## 7. Testing
- **Fake Google server** (`MockWebServer` + a `Dispatcher` holding calendars and events in memory, with sync tokens, pages, cancelled instances and extended properties): tests for every row of §3.5–§3.7:
  - calendarList: paging, `summaryOverride`, `accessRole`, `selected`/`hidden`, `primary`;
  - events.list: full sync with `timeMin`/`timeMax` in the household zone, paging, `nextSyncToken`, incremental with the token only, 410 → full replace, cancelled → removed, `recurringEventId`, `workingLocation` skipped, missing summary, timed and all-day, tags read back;
  - RRULE fetch once per series, cache cleared on full sync, a failed fetch → null;
  - insert body (id = key, tags, `colorId`, UTC `dateTime`), 409 → existing event, 409 on a cancelled event → rejected;
  - PATCH bodies for each field set, including timed ↔ all-day and a Who change to Family (`colorId: null`); that `culvery.createdBy` survives a `culvery.person` patch;
  - delete 204, 404, 410; `find` 200, 404, cancelled;
  - errors: 401 → one refresh then retry; 401 twice → NeedsSignIn; 429, 403 rate limits, 500, 503, a dropped connection, a slow reply → Unreachable; other 403, 400 → rejected with the fixed wording; 404 on list → SourceGone; unparseable body;
  - cancellation: a held reply, the caller cancelled → the call returns within 1 s and the server sees it cancelled.
- **Token source:** resolution → NeedsSignIn; network `ApiException` → Unreachable; `invalidate` then retry.
- **Contract suite** runs against `GoogleCalendarProvider` through the fake server, and against the fake; both pass every check, and every new check has a self-test fixture that fails it.
- **Mapping and refresh:** `defaultMapping` (primary, one name, a possessive, two names, a name inside another word such as "Samantha" for "Sam", case); the refresh (add, hide, unhide, remove with events and outbox, keep mappings, master removed, master read-only → cleared and one toast); `repeatsLabel` for each label and "Yes" cases.
- **Store:** v3 → v4 migration with the table list; `removeConnection` and source removal leave no events, cursors or outbox rows; `applySync`/`applyAccepted` after a removal write nothing; `makeDue`.
- **Editor and drain:** touched fields (sheet → editor → queue → writer), including an offline edit overlaid on a later phone change; ASSIGN outside the window found and not found; an aged create found (followers sent) and not found (dropped, one toast); `NeedsSignIn` from a write sets health; reconnect makes the queue due.
- **Crash-proofing:** a provider throwing `Error` → Error health, others sync, the loop keeps running; a store failure in a pass → logged, next pass runs; the drain backoff sequence and reset; C2 (the updated test); the orphan race with the shared lock; Try again reuses the key after `TRY_AGAIN`; the sheet's load failure closes with the toast; the editor's Delete failure toast; `retryWhen` restores a tab after a failing flow recovers; an uncaught exception in an application-scope child is logged and siblings keep running; the counting-DAO test that no statement binds more than 999 variables.
- **Connect flow (Robolectric):** `CalendarConnectHost` with a fake provider: authorise, connect → setup, cancel, failure toast; the card's and Settings' buttons; the chip opening reconnect; debug: a Google connection removes the sample.
- **Roborazzi**, dark and light: the Settings placeholder with a Google row (Ok and NeedsSignIn), the connect card with Connect Google Calendar, the Connecting card.
- **Emulator walkthrough** on the API 35 Google Play image with the user's real Google account (debug client, `docs/setup/google-calendar.md`), before the user's screenshot checkpoint:
  - sign in from the card: account chooser, consent, the sample connection gone, the real calendars and events showing, people's calendars mapped by name;
  - add, edit, delete and assign on the tablet, each checked in Google Calendar on the phone (title, time, colour); an edit's untouched fields kept after a phone change;
  - a repeating event's Repeats row;
  - offline (airplane mode): add and edit, then back online: delivered once;
  - revoke access at myaccount.google.com › Security › Third-party access: the chip appears; reconnect with the PIN: queued changes go through;
  - tick a new calendar in Google, restart the app: it appears, mapped.

## 8. Plan 3 follow-ups: what 3a does
| Follow-up | In 3a? |
|---|---|
| Widen R3 (handler, `retryWhen`, catch `Throwable` per source) | Yes, §3.12 |
| Log NeedsSignIn, Unreachable, Error with cause | Yes, §3.12 |
| Store calls outside the per-source try | Yes, §3.12 |
| Counting-DAO test for the 999-variable limit | Yes, §3.12 |
| More contract-suite self-test fixtures | Yes, one per new check, §3.10 |
| ICS empty feed with `fullReplace` and zero-duration events | No: moves with the ICS provider (deferred, D1) |
| Extend the module guard to JVM-only modules | No: 3a adds no JVM-only module; it goes with the first plan that does |
| R8 friendly write-error wording | Yes, §3.7 |
| R9 cancellable HTTP + contract check | Yes, §3.1, §3.10 |
| DL1 Repeats from RRULE | Yes, §3.5 |
| U3 service name in copy | Yes, §4.5 |
| m2 drain backoff | Yes, §3.12 |
| m3 ASSIGN outside the window | Yes, §3.6 |
| m4 reconnect makes the queue due | Yes, §3.3 |
| C2 accepted but unstored | Yes, §3.12 |
| C3 touched-field PATCH | Yes, §3.6 |
| C9 aged create | Yes, §3.6 |
| C10 create never recreates a deleted event | Yes, §3.6, §3.10 |
| Try again reuses the key | Yes, §3.12 |
| Google `events.insert` with the key, 409 → fetch | Yes, §3.6 |
| Sheet `produceState` catch | Yes, §3.12 |
| Drain/editor orphan race | Yes, §3.12 |
| Editor Delete failure wording | Yes, §3.12 |
| Drop `EventDetailHost`'s `onEdit = {}` default | Yes, §3.12 |
| Pulled forward from Plan 4: `connect`/`setMaster` through IO + timeout; source refresh and pruning; clean-up of removed sources | Yes, §3.3, §3.4, §3.8 (foreign keys replaced by explicit deletes) |

## 9. Review focus
Inputs a person will hit, for the plan's reviewers:
- Google access lapsing mid-save: the change is queued, the chip shows `NeedsSignIn`, and the reconnect drains it.
- A 409 on a create after a lost reply: no duplicate, and never a deleted event brought back.
- A phone edit made while an offline tablet edit waits: the touched-field PATCH keeps it.
- A calendar deleted in Google while it is the master: cleared, one toast, add buttons hide, nothing crashes.
- A provider throwing an `Error`: no crash, the other sources and connections keep syncing.
- Release kiosk (lock-task) mode may block Play services' account chooser from appearing; debug builds don't lock, so this is untested until the Plan 4 on-device pass. Until then, connect or reconnect with kiosk exited if it is blocked.

## 10. Out of scope
- ICS, Outlook and CalDAV providers (deferred, D1).
- A second Google account; disconnecting a connection.
- Settings UI for mappings, visibility and the master; the setup wizard (Plan 4).
- The release OAuth client (release SHA-1), with release signing (Plan 4).
- `SecretStore`: Google needs none (D2). It arrives with the first provider that has a secret; the comment on `Connection.config` is updated to say so.
- Deferred follow-ups: the ICS fixtures (with ICS), the JVM-only module guard (with the first JVM-only module), and every item already marked "For Plan 4", the accessibility pass or "Adding events, later", which stay where they are.
