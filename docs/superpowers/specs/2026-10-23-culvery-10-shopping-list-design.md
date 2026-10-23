# Culvery 10: the Shopping list

Brainstormed 2026-10-23. Plan 10 builds the Shopping list from the October redesign: note 12 and the Shopping card in `docs/design/2026-10-redesign/handoff.md`. The list is kept in Google Tasks behind a provider, so Todoist can replace Google later. It adds a Shopping tab and a Home card, and suggestions and favourites that belong to Culvery rather than to the provider. Units are as in Plan 9 §3: dp = handoff px ÷ 1.5. Sample names only (Alex, Sam, Mia).

Branch `plan-10-shopping`, from main at 2514bab1.

## 1. Why

The user keeps the weekly shopping list in Todoist, which they call overkill. Only they use it. Once a week they type it into Waitrose by hand.

The list belongs on the wall: anyone walking past should be able to add to it. The user and their wife should also be able to add from their phones, from anywhere. Most items repeat week to week, so adding should usually be a tap or two, not typing.

**Success:** anyone in the house adds "milk" at the wall in two taps or fewer. The user and their wife see it on their phones, and add from there too. At the weekly shop, the user works through one list.

## 2. Goals, non-goals and anti-goals

**Goals**
- A Shopping tab and a Home card, both following the redesign.
- Adding and ticking on the wall with no PIN, by anyone.
- The same list on the user's and their wife's phones, through a third-party service.
- Suggestions as you type, and favourites to tap, learned from what the house adds.
- A provider seam, so moving from Google Tasks to Todoist means writing a provider, not reworking the capability or the screens.

**Non-goals**
- **Waitrose.** There is no public API, and the user's rules ("always buy offers", "organic meat unless there's an offer too good to miss") are judgement calls made while shopping. The list stops at helping the user build the order.
- Quantities, prices, aisles and categories.
- More than one list.
- Showing who added an item. Google Tasks doesn't record it, so the handoff's "Mia" label is dropped for now. A Todoist provider could bring it back.
- Todoist itself: it's a later provider.

**Anti-goals** (failures even if the feature technically works)
- Losing an item someone added: offline, at a sync, or in a race with a phone.
- The wall adding a second "Milk": a double tap, a retried add, or tapping a favourite already on the list. A duplicate a phone makes (or one that slips through a race with a phone) shows as it is in Google, two rows; the wall doesn't merge or tick it (my call, §4.6).
- Giving whoever is at the wall a way into anything beyond the shopping list.
- Item text in the logs (LogHygieneTest).

## 3. Rulings (the user, 2026-10-23)

1. **Google Tasks first, through a provider seam,** so it can move to Todoist easily. The user considered Todoist and chose Google for now.
2. **The house Google account holds the list.** It is the account Culvery already uses for the calendars. The user and their wife add that account to the Google Tasks app on their phones.
3. **Suggestions and favourites live in the capability, outside the provider,** so they are kept when the provider changes.
4. **Adding:** tap +, type, with suggestions as you type. Favourites are one-tap chips.
5. **Favourites show only items not already on the list,** and fill the space available. There is no cap of ten.
6. **Its own tab,** because the list can reach 20 or 30 items.
7. **The tab is layout B, revised:** an add card across the top, holding the add box with Add, then one chip row with suggestions first and favourites after. The list fills the rest in three columns. A ticked item shows an Undo strip at the foot.
8. **Home is layout A, as designed:** the Shopping card takes the design's top-right row. Until the One-tap tray exists, it is widened into the tray's space, with items in two columns. Lights and Music sit side by side below, at the design's 318 px (212 dp) height.
9. **Order:** the user isn't bothered. Keep Google's own order, so the wall and the phones agree (my call).
10. **No Waitrose integration.**
11. **Coming up goes whenever Shopping is on.** Coming up stays only for a household with no Shopping, Lights or Music, as Plan 9 left it.

The brainstorm's mockups are in `.superpowers/brainstorm/1424-1792769475/content/` (git-ignored): `shopping-tab-b2.html` is the tab, and `home-card.html` (option A) is Home.

## 4. The design

### 4.1 Modules
- **`capability/shopping`** holds the capability, the tab, the Home card, the Settings page, suggestions and favourites, and the offline queue. Like `capability/door`, it declares a `ShoppingProvider` interface and binds `Capability` into the set. A provider binds `@IntoSet ShoppingProvider`, and the capability uses the first one. With no provider bound there is no tab.
- **`provider/shopping-google-tasks`** holds the Google Tasks client.
- **The Google sign-in is shared with the calendar.** Today the account, `Authorizer` and `TokenSource` live in `provider/calendar-google`, with the scopes in `CALENDAR_SCOPES`. The Tasks provider needs the same account and a token with the Tasks scope. How to share this is the builder's choice, for example a small `core/google` module that both providers use, holding the account and the authorizer. The calendar must keep working unchanged for a household that never turns Shopping on.

### 4.2 The provider seam
`ShoppingProvider` speaks in Culvery's terms, not Google's. Roughly:
- `items(): List<ShoppingItem>`: the open items, in the provider's order. Each has a provider id, its text, and when it was last changed.
- `add(text, clientKey): ShoppingItem`. The `clientKey` is Culvery's own id, so a retried add can be recognised.
- `tick(id)` and `untick(id)`, where untick is for Undo.
- `history(since): List<String>`: texts of items ticked since a time, for learning suggestions.
- `health`: a `ConnectionHealth`, as the other providers report it.

**Nothing Google-specific crosses the seam:** no task-list ids, ETags or Google field names. A Todoist provider must be able to implement it from Todoist's v1 API (one shared project, tasks closed and reopened).

### 4.3 Google Tasks
- **Scope:** `https://www.googleapis.com/auth/tasks`, asked for the house account through the same `AuthorizationClient` route as the calendar (play-services-auth; never the deprecated GoogleSignIn). It is a second permission request, so the user sees Google's "this app isn't verified" screen once more. The OAuth app is published unverified on purpose (see the culvery-workflow memory).
- **The list:** a task list called "Shopping". Culvery uses an existing list with that name, or creates it. Store the list's id in the connection config. If the list is deleted on a phone, Culvery says so on the Settings page and offers to make a new one; it doesn't recreate it silently.
- **Reads:** `tasks.list` on the list, with paging. Open items are `status = needsAction`. History comes from `showCompleted` and `showHidden` with `completedMin`.
- **Writes:** `tasks.insert` adds; `tasks.patch` sets `status` to completed or needsAction. Culvery never deletes a task.
- **Who added it:** not available from Google Tasks.
- **HTTP and JSON:** follow `provider/calendar-google`: OkHttp, and kotlinx.serialization DTOs in their own file.
- **Logs:** call names and outcomes only, as `GoogleCall` does today. Never item text.

### 4.4 Keeping in step with phones
Google doesn't push changes to the tablet, so Culvery asks.
- **When:** while the Shopping tab or the Home card is on screen and Culvery is in front, check every 60 s, and straight away when either opens. Follow the `MusicChecks` / `LightsFollow` pattern: `watch()` in a `DisposableEffect`, running only while `AlertHost.inFront` and someone is watching.
- **Wall changes first:** after an add or a tick on the wall, the list shows the change at once, and the next read mustn't undo it while it's still queued (§4.5).
- **Sleep:** Plan 8's sleep layer leaves Home composed, so checks would keep running while the wall sleeps. Pausing them while asleep is the builder's call. Either way, a check is cheap.

### 4.5 Offline and failures
- **Adds and ticks queue on the tablet** in the capability's Room database. They show at once, and drain to the provider in order, retrying with a backoff as the calendar's outbox does (`Writes.kt`). Nothing typed is lost to a dropped connection or a restart.
- **A retried add must not create a duplicate.** Before inserting, the drain checks whether the list already has an open item with the same normalised text; if so, it treats the add as done.
- **When the provider can't be reached,** the tab says so in one line with what to do. For example: "Can't reach Google Tasks. Items you add are kept and sent when it's back." The Settings page shows health as the other connections do.
- **When the Tasks permission is missing or withdrawn,** the tab and the card show how to fix it ("Allow Google Tasks in Settings › Shopping"). Adding still queues.
- **Undo** reverses a tick: untick if the tick has already reached the provider, otherwise drop it from the queue.

### 4.6 Duplicates
Text is normalised as the calendar's `titleKey()` does it (lower case, accents dropped, spaces trimmed), plus collapsed inner spaces. Adding text that matches an open item doesn't add a second one. The add box clears, and the existing item flashes briefly so the person sees it's there (my call). The list on screen is Google's rows as they are, not grouped by text. If a phone has made a duplicate, both rows show and each ticks on its own (my call: simpler, and a phone can tidy it).

### 4.7 Suggestions and favourites
These are kept in the capability's Room database, independent of the provider (ruling 3).
- **What's learned:** every item text that is added (on the wall or seen arriving from a phone) and every tick, kept as a normalised key, the best-cased display text, an add count and the last date. Ticks seen in the provider's history count too, so a phone's ticked items teach it. Entries unused for 12 months are pruned, as remembered titles are.
- **Suggestions while typing:** from 1 character. Prefix matches come first, then word-start matches. Within each group, the most-added come first. Items already on the list never show. The row shows as many as fit before the favourites (my calls; the calendar starts at 2 characters and shows 3, but shopping words are short).
- **Favourites:** the most-added items that aren't on the list, filling the chip row's space. The row is one line on the tab; whether it may wrap to two is a builder check against the screen. One tap adds the item.
- **First use:** the store starts empty. It is seeded once from the provider's history when Shopping is first connected, so favourites aren't blank on day one. Until anything has been learned, the row says "Favourites appear as you add things."
- **Moving providers:** the store is keyed on text, not provider ids, so it carries over to Todoist unchanged.

### 4.8 The Shopping tab
Layout B, revised, from the mockup:
- **Header:** "Shopping" in the page-title style, and "{n} items · from Google Tasks, {time ago}" (muted), following the Calendar tab's "Updated from Google Calendar…" line.
- **Add card** (`s1`):
  - The add box (the shared text field, 1 line, capped at 100 characters) and a primary **Add** button. The keyboard's Done also adds. Empty text can't be added.
  - The chip row: suggestions while typing (accent-soft), then favourites (quiet `s2`).
  - After an add, the box clears and keeps focus, ready for the next item.
- **The list** (`s1`): three columns, filled down each column then across. Each row has a circle and the name; tap the circle or the name to tick. It scrolls once it's past the card, with the house's fade-and-pill treatment for overflow left to the builder.
- **Ticking:** the row goes muted with a line through it, and an Undo strip shows at the foot with "Ticked {item}" and **Undo**, for the house toast time (Plan 9's rule: at least 6 s). Ticking several items in a row keeps one strip for the latest; earlier ones are done.
- **Empty list:** "Nothing on the list. Add something above, or tap a favourite."
- **Rail:** the Shopping tab sits right after Calendar (`order = 20`), with a shopping-cart or checklist icon (the builder's pick from the existing icon set). No badge.
- **No PIN** for adding, ticking or Undo (handoff note 12).

### 4.9 The Home card
Layout A, from the mockup and the handoff's Shopping card:
- **Card:** `s1`, radius 36 → 24 dp, padding 28/30 → 19/20 dp.
- **Header:** "Shopping" (30/700 → 20 sp) and "{n} items" (21 → 14 sp, muted). On the right, a 60 px (40 dp) round quiet **+**, which opens the Shopping tab with the add box focused and the keyboard up (my call; no add sheet on Home).
- **Items:** 46 px (31 dp) rows, each with a 30 px (20 dp) circle with a 3 px s3 ring, and the name at 24 → 16 sp. Two columns while the card is wide. Tapping the circle ticks the item, with the same Undo strip, shown on Home.
- **Footer:** "+{n} more · Open Shopping" (20 → 13 sp, muted) when items overflow. Tapping the footer or the header opens the tab.
- **Empty:** "Nothing on the list" (muted) and the +.
- **Placement:** today `HomeCardPlacer` takes at most two REGULAR cards in a right-hand column (`RIGHT_CARDS = 2`), so a third card would push Lights or Music off Home. Plan 10 changes the right column to the design's arrangement: a utility row on top (Shopping, later Shopping and the tray), then one row holding Lights and Music side by side at 212 dp. When Lights or Music is missing, the other takes the row's full width. When both are missing, the design's 1b layout applies, and until the slideshow exists the Shopping card fills the right column. Coming up (the calendar's WIDE card) never shows while Shopping is on (ruling 11). How the card kinds and placer change is the builder's design. The existing Home screenshot tests must be updated, and the Lights and Music cards checked at their new width.
- **Shopping not set up:** Home is unchanged from today, with Lights over Music.

### 4.10 Turning it on
- **Settings › Shopping** has one page:
  - "Use Google Tasks for the shopping list", with the account shown.
  - Turning it on asks for the Tasks permission, behind `CONNECTIONS_MANAGE` (Admin, fresh PIN), as connecting the calendar does. Then it finds or creates the "Shopping" list.
  - The list's health shows here.
  - "Stop using Google Tasks" removes the connection and hides the tab and card. It keeps the learned suggestions.
- **The setup wizard:** no new step. Shopping is turned on from Settings once the calendar's Google account is connected.
- **No Google account connected:** the Settings page says "Connect Google Calendar first. Shopping uses the same account."
- **Which account (my calls):** a household can connect more than one Google calendar account. With one connected, Shopping uses it. With more than one, turning Shopping on asks which account holds the list, and the choice is stored in Shopping's own connection config. Shopping keeps its own connection record pointing at that account, rather than sharing the calendar's.
- **When that calendar connection is removed (my call):** Shopping stops reading and writing. The queue is kept, and the tab, card and Settings page say "Shopping's Google account was disconnected. Connect it again in Settings › Calendars." Reconnecting the same account resumes Shopping without asking again. Disconnecting the calendar warns first that Shopping uses that account.
- **"Stop using Google Tasks" (my call):** it removes Shopping's connection and stops asking for Tasks tokens. It doesn't try to withdraw the Tasks permission from the account, so the calendar is untouched. The Tasks permission stays granted in Google until someone removes it in their Google account settings, and the page says so.

### 4.11 A child in the house
- Anyone can add or tick without a PIN (handoff note 12). That's intended: the worst case is silly items on the list, which a phone can tidy.
- The tablet's token is the house account's and now includes Tasks. Nothing on the wall shows other task lists, and nothing deletes tasks.
- Connecting and disconnecting need an Admin's fresh PIN.
- The add box is capped at 100 characters, and a burst of adds is rate-limited: a few a second at most is plenty. The exact limit is the builder's call.

## 5. Left to the builder
- Sharing the Google account and authorizer between the calendar and Tasks providers (§4.1).
- The queue's shape, and whether to reuse the calendar's outbox code or write a smaller one alongside.
- The Home placer's new shape (§4.9).
- Pausing checks while the wall sleeps (§4.4).
- The list's overflow treatment on the tab, and whether the chip row wraps to two lines.
- The rail icon.

## 6. Testing
- **Unit:** suggestion matching and favourites ranking; normalising and duplicates; the queue (offline add, retry, a duplicate after a retry, Undo before and after it drains); the Google DTOs against recorded sample responses with sample items only (Milk, Bread, Eggs); log hygiene for every new log line.
- **Screenshots (Roborazzi):** the tab (empty, a few items, 30 items, typing with suggestions, a tick with Undo, can't reach Google, permission missing); the Home card (wide, empty, overflowing) and the new Home grid with and without Lights and Music; Settings › Shopping in each state; light and dark.
- **On the tablet:** add on the wall and see it on a phone within a minute; add on a phone and see it on the wall within a minute; tick on each side; add with Wi-Fi off, then turn it back on; check the logs for item text.
