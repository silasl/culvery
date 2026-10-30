# Culvery Plan 15a: the end-of-v1 review's screen changes

Scoped with the user on 2026-10-30, from the end-of-v1 design review. The agreed changes are in `culvery-notes/culvery-design-review-2026-10-30/changes.md` (the source of truth) with mockups on the review canvas ("Home, mockup", "Lights and plugs, mockup"). The handoff is `docs/design/2026-10-redesign/handoff.md`.

The review's changes are split into three plans:
- **15a (this one):** Home, Calendar, Door, and Lights and plugs.
- **15b:** Settings and Modes (App settings, Modes in place of Party mode, modes started from Home with no sheet, the list order, the 90 s timeout, the visual items).
- **15c:** why Party mode is slow to turn on: timing first, then a run on the real speakers on the user's go.

## 1. Intent

Finish the screens the review passed with small changes, so that 15b can rebuild Settings on top of them. Nothing here changes what a mode does. It changes how the tray looks, what the Lights card offers, how far ahead the Calendar goes, how long door history is kept and how to clear it, and where plugs live.

### Goals
- The One-tap tray looks like the handoff: Holiday mode and Party mode side by side, Hoover full width under them, and a row left free for the sound board.
- The Lights card swaps Holiday lights for a Scene pill.
- The Calendar's week view goes 8 weeks ahead by default (this week plus 7), chosen on Settings › Calendars, and sync reads that far.
- The Door tab can clear its history, and Settings › Door sets how long history is kept.
- The Plugs tab joins the Lights tab as small tiles under the rooms.

### Non-goals
- Settings' structure, Modes, App settings and the timeout (15b). Party's speed (15c).
- Changing what turning a mode on or off does, or its sheets and PINs. The tiles still open today's sheets until 15b.
- The setup wizard, Settings › Home buttons, the status tab and the rail redesign (not in scope, on purpose).
- The sound board itself. Its row is only left free.
- Any change to Hue, Sonos or Home Assistant behaviour.

### Anti-goals (a failure even if it works)
- Clear history leaves any picture on the tablet: a snapshot or thumbnail file, or a decoded copy in memory that can still be drawn.
- Keeping door history longer makes the tab hard to read, or a stale old ring keeps the rail badge lit.
- The week view offers a week it has no synced events for, so it looks empty when it isn't.
- A child can switch a PIN plug, or clear the door history.
- The saved plug choices stop working for Party mode or Holiday mode once the columns leave the tab.

## 2. Decisions (the user's)

1. **Tray:** as the handoff draws it. Holiday mode and Party mode side by side, half width each. Hoover full width below. The bottom row left free for the sound board. Mode tiles show no "Off" line; a mode that's on fills `accent` and says ON. (changes.md, Home 1.)
2. **Lights card:** Holiday lights comes off. A Scene pill takes its place, showing the main room's current scene; each tap moves to the room's next scene. Holiday lights stays in the Lights tab's tray. (Home 2.)
3. **Calendar:** Settings › Calendars gets a choice of **4, 8 or 12 weeks**, counting this week. The default is 8. Sync reads as far as the chosen setting. (Asked 2026-10-30.)
4. **Door history:** Settings › Door gets **"Keep door history for": 2 days, 7 days or 30 days**, default 2 days (today's 48 hours). Pictures go with their events. (Asked 2026-10-30: today's 48-hour prune meant a 30-day clear would never fire.)
5. **Clear history:** a 72 px "Clear history" button in `danger` text on the quiet fill with a delete icon, right of the Door header. It opens a confirm card, "Clear all {n} door events? Pictures saved on this tablet are deleted too. Ring keeps its own copies.", then asks for an Admin or Adult PIN. It deletes every log row and every cached snapshot and thumbnail file. (Door 1.)
6. **Lights and plugs:** the Plugs tab joins the Lights tab; the Plugs rail tab goes. The page title is **"Lights and plugs"**; the rail label stays **"Lights"**. (Asked 2026-10-30.) The header line reads "{on} of {n} rooms on · {on} of {n} plugs on".
7. **Plug tiles:** small tiles under the rooms, four across: the name, a state line, and a Switch on / Switch off pill. PIN plugs keep the padlock. Unreachable and checking plugs show their line and no pill.
8. **Edit and the mode columns leave the tab in this plan.** The saved choices keep working for Party mode and Holiday mode but can't be changed until 15b's Modes pages. 15a goes on the tablet before 15b. (Asked 2026-10-30.)
9. **Room cards shrink to fit what they hold**, which also fixes the tall empty cards. (Claude's call in the mockup, not objected to.)

## 3. Calls I made (for the user to check while reading)

- **One mode tile alone** (for example Party mode before it's set up) takes the whole first row, not half of it.
- **The tray keeps three rows' height** (modes, Hoover, sound board) whatever is set up, so the tiles don't change size when one goes. A missing button leaves its row empty. The sound-board row is plain empty space on the tablet, with no outline or words.
- **The mode tiles keep their other lines:** "Turning on…", "Turning off…", Holiday's back date, and what didn't happen. Only "Off" goes. Party mode gets the same ON pill Holiday has.
- **Scene pill:** reads "Scene" with a chip holding the scene's name. With no current scene the chip says "None". A tap recalls the next scene in the room's list, wrapping round; from "None" it recalls the first. On an off room it turns the room on with that scene, as a scene chip does on the Lights tab. With no scenes, the pill isn't shown and All lights off takes the row. The mockup's chevron goes, because a tap doesn't open a list. It speaks as "Scene, Relax. Double tap for the next scene."
- **The Lights card's problem line and status lines** stay as they are.
- **The tab shows when there are lights or plugs.** With both, it is "Lights and plugs" in the title. With lights only, it's "Lights" as today. With plugs only (Hue not linked), the title is "Plugs" and the rail label is "Plugs" with the power icon. The header line shows only the parts there are.
- **The page scrolls as one.** Rooms first, by the handoff's column rules, then a "PLUGS · Home Assistant" section label, then the plug tiles in rows of four. When the page runs past the screen, the existing fade and pill show, saying "{n} more rooms" while rooms are hidden and "More below" when only plugs are.
- **A plug that's on** fills `accentSoft` (as in the mockup). A tap on a PIN plug asks for the PIN as today. While a switch is out, the pill shows today's switching state.
- **Plugs › Settings** (which plugs to show, which need a PIN) stays where it is.
- **Clear history** shows only when there is at least one event. When it's done: a toast "Door history cleared", and the rail badge goes out. The PIN asked is a fresh one, like the plug PINs.
- **Kept days are the household's calendar days**, not hours: "2 days" keeps today and yesterday, "7 days" today and the 6 days before, and so on, each cut at local midnight. The default therefore shows exactly what the tab shows today. It keeps a little less than today's 48 hours, never more.
- **Longer history in the Door tab:** the tab shows every kept day, newest first, with "TODAY", "YESTERDAY", then "MON 27 OCT" style labels.
- **The rail badge still counts unseen rings from today and yesterday only**, so an old ring can't keep it lit. Opening the Door tab still marks every event seen, as it does today. 15a doesn't build the handoff's proposed "Mark all seen" button or latest-picture panel.
- **Shortening the keep setting** (30 days to 2 days) prunes straight away, files included.
- **Weeks setting:** three radio rows on Settings › Calendars, labelled "4 weeks", "8 weeks" and "12 weeks", under "Weeks ahead in the Calendar". The week view's › stops at the last chosen week. If the setting shrinks while the view is further on, it steps back to the last allowed week.
- **Settings rows** follow today's Settings patterns and sit behind each page's existing PIN.

## 4. How it works

### 4.1 Home tray
- Each `TrayButton` says how wide it is (half or full). The tray lays half-width buttons out in pairs and full-width ones on their own row, in `order`, then leaves the sound-board row. A half-width button with no partner takes the full row.
- `HouseModeTile` loses its "Off" second line. Holiday's `OnPill` moves somewhere both tiles can use it (core house UI or core ui; the builder decides).
- Sizes are from the handoff's "One-tap tray": 2 px `s3` outline, radius 36, padding 16, a 36 px filled icon in `accent`, a 22/700 label, "ON" at 16/700 with letter-spacing 1, Hoover 22/700 over 18 muted with a 54 px Start pill, 12 px between rows. The handoff's px are divided by 1.5 for dp.

### 4.2 Lights card
- The card's `HolidayButton` and its `holidayOn` and `run` inputs go. `HouseActions` loses `onHoliday`.
- A `ScenePill` takes its place: a 68 px quiet pill whose 46 px inner chip shows the scene. It calls `LightsActions.recallScene(main, next)`.
- Its next scene is a pure function of the room's scenes and `activeSceneId`, unit-tested.

### 4.3 Calendar weeks ahead
- `MAX_WEEKS_AHEAD` becomes a stored setting: weeks after this one, 3, 7 or 11 (4, 8 or 12 weeks in all). Default 7. It lives with the calendar's other settings. A value that can't be read falls back to 7.
- `lastShownDay`, the week view's stepping and `CalendarRepository`'s read window follow the setting.
- **Sync:** the synced window (`SYNC_FUTURE_DAYS`, 28 days today) and the read (`SYNC_AHEAD_DAYS` past it) must reach at least the last shown day for the chosen setting. When the setting grows, the next pass reads the wider range in full. The builder must check what the stored cursor holds per window, so a wider window can't be treated as already synced. A sync is triggered when the setting changes.
- The repository's "synced as far as" check keeps working: a week past what's synced isn't offered.

### 4.4 Door history
- **Keep setting:** stored in `DoorSettings` as 2, 7 or 30 days. Default 2. `DoorStore.prune` deletes events before the start of the oldest kept day (today minus N−1 days, at local midnight in the household's zone) in place of `KEEP_MS`, keeping today's rule that files with no event go too. A change prunes at once.
- **Days shown:** `doorDays` returns every kept day, grouped by the household's dates, newest first. The badge's `since` stays `startOfYesterday`.
- **Clear:** `DoorStore.clear()` under the store's lock (so a record in progress can't leave a picture behind) deletes every row, then every file in the snapshot folder, and drops any decoded snapshot or thumbnail Culvery holds in memory. A new permission, Door's "Clear door history" for Admin and Adult with a fresh PIN, guards it.
- **Flow:** tap Clear history, then the `HhConfirmCard` with the copy in §2.5 and "Clear history" or "Keep". Then the PIN, then the delete, then the toast. Cancelling either step changes nothing.

### 4.5 Lights and plugs
- Plugs and Lights are separate capabilities that don't depend on each other. The plugs part reaches the Lights tab through a seam in `core:house`, next to `HouseLights`: plugs supply their section, and Lights draws it under the rooms. The builder decides the seam's shape. The Plugs capability stops having a tab.
- The section keeps `PlugsTabHost`'s watching: plugs are watched while the tab shows.
- `PlugsTab`, `PlugRow`'s mode cells, `EDIT_IDLE_MS` and the `MANAGE` permission's use on the tab go. `PlugActions.setChoice` and the stored choices stay for 15b. The `MANAGE` permission stays defined, for 15b.
- Room cards size to their content inside the handoff's grid. The empty stretch goes.
- Plug tile sizes are from the mockup: about 400 × 150 px, radius 28, the name at 26/700, the state at 20 muted, a 52 px pill. The section label is 18/700 uppercase with letter-spacing 1.5 and the power icon.

## 5. Tests

- Unit: the tray's row layout (pairs, a lone half, full rows, the free row); next scene (wrap, none, empty list); weeks-ahead parsing and default; `lastShownDay` and the sync window for 4, 8 and 12 weeks; door keep parsing and default; prune by setting; `clear()` removes every row and file, and a record that started before it can't leave a file; `doorDays` over 30 days; prune at the midnight cut-off for 2, 7 and 30 days; the badge still counts only today and yesterday; the new permission's roles; the plug section's header counts.
- Compose: the tray at 1, 2 and 3 buttons; the mode tile on, off and running; the Lights card with and without scenes; Clear history's confirm, cancel and PIN refusal; plug tiles in each state; the tab's three titles.
- Screenshots (Roborazzi), dark and light: Home with the new tray and Lights card, Lights and plugs with 2 rooms and 4 plugs, and with 9 rooms and plugs (scrolling), the Door tab with Clear history and with older days, Settings › Calendars and Settings › Door with the new rows.

## 6. Done means

An approved spec, then a plan, a three-reviewer plan review, a subagent-driven build, an emulator walk-through on the `culvery-5` AVD (taken offline after every restart, before Culvery opens; Halloween's dates put back to 24–31 October), and a release `install -r` on the tablet. No real light, plug or speaker is switched without the user's go. Once merged, the to-do list loses the items this plan closed and gains only what it leaves open.
