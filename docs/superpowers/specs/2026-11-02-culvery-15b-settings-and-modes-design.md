# Culvery Plan 15b: Settings and Modes

Scoped with the user on 2026-10-30 and 2026-11-02, from the end-of-v1 design review. The agreed changes are in `culvery-notes/culvery-design-review-2026-10-30/changes.md` (the source of truth, "Settings" items 1–3, 5 and 6) with mockups on the review canvas: "App settings, mockup", "Modes, mockup" and "A mode's page (Party mode), mockup". The handoff is `docs/design/2026-10-redesign/handoff.md`.

The review's changes are split into three plans. 15a (Home, Calendar, Door, Lights and plugs) is merged. This is 15b. 15c times Party mode's slow start, then runs it on the real speakers on the user's go.

## 1. Intent

Settings has grown one page per feature (11 pages), and what a mode does is spread over three places: Party mode's page, Settings › Lights (Holiday lights) and the old Plugs tab's columns, which 15a removed. 15b gathers the setup pages into one App settings page, and gathers everything about modes into one Modes page, where the family can also add modes of their own ("Film night"). Turning a mode on from Home becomes one tap (plus a PIN only where the mode asks for one).

### Goals
- Settings' list is eight pages, in this order: App settings, Calendars, Shopping, Music, Lights, Modes, Door, Home Assistant.
- App settings holds Home location, People, Display and Tablet lock on one page.
- Modes lists Holiday mode (built in), Party mode and any mode the family adds, each with its own page: Show on Home, Needs a PIN, music, and only the lights and plugs added to it.
- A mode on Home turns on or off with one tap, a PIN only if it needs one, and no sheet, except Holiday mode's turn-on, which still asks the day you're back.
- Settings closes after 90 s without a touch.
- The visual items the review listed (§6) are built.

### Non-goals
- Party mode's speed (15c). The setup wizard, Settings › Home buttons, the status tab, the rail redesign, the sound board.
- Any change to what Holiday mode does (its steps, heating, hoover, door chime) beyond where its settings live.
- Several modes on at once.
- Any change to Hue, Sonos or Home Assistant behaviour, or to the Lights tab (it keeps its Holiday lights button).

### Anti-goals (a failure even if it works)
- A child can switch a plug that needs a PIN, through a mode or otherwise.
- A mode that turns off leaves the house different from before it turned on, where Party mode today would have put it back.
- A family's Party mode settings or plug choices are lost in the move.
- A mode's name, a person's name or a room's name reaches the logs.
- Two modes end up on at once, or a mode's tile shows ON while a step it was asked to do didn't run and no line says so. (A mode with nothing to do may show ON; its line says so, §4.3.)

## 2. Decisions (the user's)

1. **One mode at a time** (asked 2026-11-02). Today's Holiday/Party rule applies to every mode: while one is on, tapping another's tile does nothing but say "Turn {mode} off first." This holds for Holiday mode too.
2. **A mode not on Home** (Show on Home off) is turned on and off from its own Settings page, with "Turn on now" / "Turn off now" under its name. (Asked 2026-11-02.)
3. **Up to 4 modes on Home** (asked 2026-11-02). Shown modes fill the tray in halves, two to a row, in the Modes list's order: the first two in the modes row, the third and fourth in the row kept for the sound board. The hoover's row stays as 15a built it. With 4 already shown, a fifth mode's Show on Home switch is off and disabled, with "Home has room for 4 modes."
4. **A new mode** (asked 2026-11-02): "Add a mode" asks for a name, then an icon from a fixed grid of about 12. Both can be changed later on the mode's page. Holiday mode's and Party mode's icons stay as they are (`beach_access`, `celebration`).
5. **The Lights tab keeps its Holiday lights button** (asked 2026-11-02). It still runs the presence automations on their own, outside Holiday mode. Their choice moves to Holiday mode's page (changes.md 5).
6. **A locked plug makes the mode need a PIN** (asked 2026-11-02). Adding a plug that needs a PIN turns the mode's Needs a PIN on and holds it there, disabled, with "A plug in this mode needs a PIN." Removing the last such plug lets it be switched again (it stays on until switched).
7. **No-PIN modes** (asked 2026-11-02, from the builder check): with Needs a PIN off, a tap may run exactly that mode's own music, lights and plugs with no person behind it; the wall vouches for that mode only, and the log says "no PIN". **Holiday mode always needs a PIN:** its switch shows on and disabled with "Holiday mode always needs a PIN.", as it turns the heating to Eco and runs the presence automations. This narrows changes.md's "every mode, Holiday included".
8. **Admins only set up modes** (asked 2026-11-02, from the builder check). Modes live in Settings, which opens with an Admin PIN, so only Admins add, change and delete modes and turn on hidden ones. Adults no longer choose what modes do with plugs (Plan 12 let them, from the old Plugs tab); they still turn shown modes on and off from Home.
9. **From changes.md:** Holiday mode is built in and can't be deleted; Party mode and added modes are stored modes and can. Every mode, Holiday included, has Show on Home and Needs a PIN (Holiday's held on, decision 7). Lights are picked by room, as Party mode does today; a room's choice is a scene, On at a brightness, or Off. Every light and plug not added is left alone. Other integrations moves to the end of the list as "Home Assistant". Settings' idle timeout is 90 s. Holiday lights' settings move to Holiday mode's page. The visual items are built (§6). Settings' clipped Close is to be checked on the shorter list.

## 3. Settings' list and App settings

- The list, top to bottom: **App settings, Calendars, Shopping, Music, Lights, Modes, Door, Home Assistant**. Page titles and the list's labels are these words. "Party mode" and "Other integrations" no longer appear in the list.
- **App settings** (mockup "App settings"): title "App settings", the sub-line "The home, the people who use the wall, the screen and the lock.", then four labelled sections:
  - **HOME LOCATION:** one row with the place and "Used for the clock's time zone and the weather. Tap to change." It opens today's Home location page as a sub-page.
  - **PEOPLE:** today's people rows, two to a row as the mockup draws, each opening today's person sheet; then the dashed "Add a person" row.
  - **DISPLAY:** "Brightness and night" (a summary line, opening today's brightness, night clock and camera wake settings) and "Seasonal looks" (a summary line, opening today's seasonal settings).
  - **TABLET LOCK:** a status row, "Tablet lock is on" / "Tablet lock is off" with today's explanation and action (W29).
- A sub-page has a back link to its parent ("‹ App settings"), as the mode's page has "‹ Modes". What each sub-page holds and does is unchanged.
- **Timeout:** Settings closes after **90 s** without a touch (today 2 minutes), and the PIN session that opened it ends with it. The "Settings closes in 20 s" warning and the 10-minute hold while a sheet has unsaved changes stay. Other overlays keep their timeouts. (My call: only Settings changes, as the review asked.)
- **Close:** after the list shrinks, check that Close no longer overlaps the last row (15a's fault 3) at the tablet's size and at font 1.3. If it still does, the list scrolls above a Close that keeps its own space.

## 4. Modes

### 4.1 The Modes page (mockup "Modes")
- Title "Modes", sub-line "One tap sets the house up. Each mode can sit on Home and can need a PIN."
- One row per mode, in this order: Holiday mode, then the stored modes in the order they were added (Party mode first). Each row: its icon, its name, and a summary line: "Built in" for Holiday, then "On Home" or "Not on Home", "PIN" or "No PIN", then what it does in short ("Sunday morning on 3 speakers · 2 rooms · 1 plug"; Holiday's: "Lights, heating, hoover, plugs, door chime"). A mode that's on says "On" first.
- The dashed "Add a mode" row last. It asks for a name (the tablet's keyboard; 1–24 characters; not the same as another mode's name, ignoring case), then an icon from the grid; then it opens the new mode's page. A new mode starts with Show on Home on if there's room (else off), Needs a PIN on, and nothing to do.
- The order of the tray follows this list. Reordering is not in this plan.

### 4.2 A mode's page (mockup "A mode's page")
- "‹ Modes", then the mode's icon and name (tap either to change, stored modes only), then:
- **Turn on now / Turn off now** (decision 2), an ordinary button, on every mode's page. It follows the same rules as the tile: one mode at a time, a fresh PIN if the mode needs one (Settings' own session doesn't count, as mode permissions are fresh-PIN today), Holiday's sheet for Holiday. (My call, from the builder check.)
- **Show on Home** and **Needs a PIN**, two switches side by side (decisions 3 and 6).
- **MUSIC** (stored modes): "Music" (the playlist or favourite) and "Speakers" (which speakers, default every speaker), as Party mode's page today. No music chosen means the mode leaves the speakers alone.
- **LIGHTS** with "· every other light is left alone": one row per added room, with a dropdown chip for what it does ("Scene: {scene}", "On · {n}%", "Off") and a × to remove it; "Add a light" lists the rooms not yet added. On · {n}% picks the brightness in steps of 10%, 10–100. (My call.)
- **PLUGS** with "· every other plug is left alone": one row per added plug with an On / Off chip and ×; "Add a plug" lists the plugs on the Lights tab not yet added. A plug that needs a PIN shows the padlock.
- **Holiday mode's page** has, instead of MUSIC and LIGHTS: **LIGHTS WHILE AWAY**, the presence automations as Settings › Lights offers them today (moved, not changed), and fixed lines for what Holiday mode does that isn't chosen here: the heating to Eco, a clean the day before you're back, the door chime. It has PLUGS like any mode. It has no music, no rename and no delete.
- **Delete mode** (stored modes), a quiet danger button at the foot, with a confirm card "Delete {mode}? Its music, lights and plugs choices go too." Refused while the mode is on: "Turn {mode} off first."
- Every change saves as it's made, as Settings pages do today.

### 4.3 Turning a mode on and off
- **From Home:** tap the tile; the PIN pad only if the mode needs a PIN; then it starts straight away. The tile shows it's working at once (the handoff's optimistic UI) and fills with the accent and ON when it's on. Turning off is the same: tap, PIN if needed, off. There's no sheet, except **Holiday mode's turn-on**, which keeps today's sheet asking the day you're back. Holiday's turn-off has no sheet. (My call: the review's exception is for asking the date, and turning off asks nothing.)
- **What a stored mode does**, on: what Party mode does today, from its own choices: plugs, the music on its speakers, then each added room to its choice. Off: puts back what it changed, by today's Party rules (a room changed by hand during the mode is left as it is; music regrouped by hand is left). A room counts as changed by hand when it's no longer as the mode left it: for "Scene", the bridge's active scene isn't the mode's (today's rule); for "On · {n}%", it's off or more than 5% from {n}; for "Off", it's on. This reads the bridge only. (My call, from the builder check.) The order of steps is Party's today; 15c may change it.
- **Changes while a mode is on** are saved but apply from its next turn-on; turn-off puts back what this run changed. The page says "Changes apply the next time {mode} turns on." while it's on. Needs a PIN applies as it stands when the tile is tapped, so a locked plug added mid-run makes turning off ask for a PIN. (My call, from the builder check.)
- **What didn't happen** shows as lines on the tile, as Party's tile does today ("Some lights didn't change: {rooms}.").
- **A mode with nothing to do** turns on and off like any other, and its tile's line says "Nothing to do yet. Choose music, lights or plugs in Settings › Modes." (My call: there's no sheet left to say it in.)
- **One at a time** (decision 1), checked before the PIN pad.
- **Needs a PIN off:** anyone can turn the mode on and off from its tile, Children included, and the run may do only that mode's own choices (decision 7). With it on, an Adult's or an Admin's fresh PIN, as Party and Holiday today.
- A restart mid-mode keeps what it must put back, as Party does today.

### 4.4 Moving today's settings
On the first start of 15b, with no step for the family to take:
- Party mode becomes the first stored mode with today's music, speakers and room scenes (each becomes "Scene: {scene}"), Show on Home on, Needs a PIN on (as today), and its `celebration` icon. If Party mode is on during the move it stays on, and turning it off still puts everything back.
- Every plug's Party and Holiday choices carry over (On or Off; Ignore means the plug isn't added).
- Holiday lights' chosen automations carry over to Holiday mode's page. Holiday mode keeps Show on Home on and Needs a PIN on.
- Nothing about a mode that's on is lost if the tablet restarts during the move.

## 5. Ids, names and logs
- A stored mode has an id the app makes (never from its name), fitting the plug store's mode id rule. Party mode keeps the id `party`, so its plug choices and records still match.
- Mode names are the family's words: never logged, like room and plug names. Log lines name a mode only by "Holiday" or "a stored mode".

## 6. The visual items (changes.md 5, as listed in the follow-ups file)
- **W26:** Settings' left-hand title at 56 px (37 sp), and the list without page icons, as the mockups draw.
- **W29:** Tablet lock as a status row (§3).
- **W34:** Settings pages use the handoff's spacing: section labels 18/700 uppercase with 1.5 letter-spacing, rows of at least 84 px with 10 px between them, as the mockups draw.
- **People:** names at 19 sp (the handoff's), and the Set PIN pill not clipped at the sheet's scroll edge on an 800 dp screen.
- **Door:** "When the doorbell rings, lower music to" on one line at the tablet's size (shorten to "Lower music on a ring to").
- **Display's dates row:** a chevron as its tap cue, like the other rows that open something.
- **Shopping:** no "Not updated yet" before the first read (the row shows nothing until it knows), and Settings › Shopping no longer tells you to go to Settings › Shopping.
- **Calendars:** keeps "Family" for the household's calendars (9c-2's spec).

## 7. Copy (exact)
"App settings" · "The home, the people who use the wall, the screen and the lock." · "Modes" · "One tap sets the house up. Each mode can sit on Home and can need a PIN." · "Add a mode" · "Show on Home" · "Needs a PIN" · "Turn on now" / "Turn off now" · "Home has room for 4 modes." · "A plug in this mode needs a PIN." · "Holiday mode always needs a PIN." · "Changes apply the next time {mode} turns on." · "every other light is left alone" · "every other plug is left alone" · "Add a light" · "Add a plug" · "Delete mode" · "Delete {mode}? Its music, lights and plugs choices go too." · "Turn {mode} off first." · "Nothing to do yet. Choose music, lights or plugs in Settings › Modes." · "Home Assistant". Curly apostrophes (’) in copy.

## 8. Left to the builder
- How the stored modes are kept (one store of modes replacing `party_settings`, or Party's store widened), so long as §4.4 moves everything and a restart mid-mode still puts back.
- Where the Modes page and the stored modes' code live (`:capability:party` widened into a modes module is the expectation), and how Holiday's page reaches Holiday lights' choices in `:capability:lights`.
- The icon grid's exact 12 icons, added to the bundled icon font from the full font in the repo's history (tools/fonts/README.md); no download.
- The permission each stored mode asks for when it needs a PIN (Party's `party.mode` for all stored modes is fine).
- Tests: every decision above, the move from today's settings (with Party on and off), a Child refused for a PIN mode and allowed for a no-PIN mode, a locked plug forcing the PIN, one-at-a-time from the tile and from the page, the 4-on-Home limit, and the 90 s close.
