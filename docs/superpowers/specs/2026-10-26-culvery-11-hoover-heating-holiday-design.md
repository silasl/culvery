# Culvery 11: Home Assistant, the hoover, the heating and Holiday mode

Brainstormed 2026-10-26. Follow-ups file: `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("Feature ideas": Eufy robot vacuum, Thermostat (Nest), Holiday mode, A home hub on the NAS, the October 2026 redesign's speculative designs). Designs: `docs/design/2026-10-redesign/handoff.md` (the One-tap tray, note 13) and `notes.txt`.

Branch `plan-11-home-hub`, from `main` at a7733590, in the one `culvery` checkout (no worktree).

One spec, built as two plans:

- **11a:** the Home Assistant link, the hoover (Start from the One-tap tray) and the heating link (no screen of its own yet).
- **11b:** the house Holiday mode, in the tray, driving lights, heating, hoover, door chime and the tablet's screen.

11a merges before 11b is planned in detail. §8.2's Away check moved to the first real holiday (§2, decision 13).

## 1. Purpose and outcome

The house has a Eufy X10 Pro Omni robot vacuum and a Google Nest thermostat. The family want:

- **to start the hoover from the wall** with one tap ("just a Start button");
- **to come home to a warm house.** The last time they came back from a holiday the house was cold: Nest's Home/Away Assist (phones, the thermostat's motion sensor and the alarms) had put the thermostat in Eco while they were away, and it stayed there until they walked in;
- **a house Holiday mode** they switch on as they leave: lights to their holiday setting, heating to Eco, the door chime silent, the tablet dark; and on the way back, the heating back on its schedule from 00:01 on the day they return and a clean the day before.

Done means: 11a's Start works from the tray against the real X10 through the household's Home Assistant, and Culvery reads and sets the Nest's Eco preset; 11b's Holiday mode runs end to end on the tablet with a back date a day or two ahead; each plan has its final review, a local merge and the release installed on the SM-T510 with `install -r`.

## 2. Decisions (the user, 2026-10-26)

1. **Hoover:** a Start button only. No stop, no "send home", no rooms or scenes from the wall.
2. **Heating:** for Holiday mode only, for now. Day-to-day control (temperature, boost) may come later as an extra control; it goes on the Feature ideas list.
3. **The return:** at **00:01 on the back day**, Culvery takes the thermostat out of Eco so the normal schedule runs that day.
4. **Holiday mode is explicit.** The wall never works out a holiday from the calendar on its own: if nobody switched Holiday mode on, nothing happens. (Half term is a multi-day event in the same calendar and the family aren't away.)
5. **"When are you back?"** Switching Holiday mode on asks for the back date. It is filled in from a multi-day event in the calendar that is on now, if there is one; the person confirms or changes it. With nothing in the calendar they pick a date.
6. **Holiday mode ends only when someone taps it off** at home, in case the holiday runs over. If it runs over, the heating still comes back on the planned day; the family fix that from the Nest app, not the wall.
7. **Hoover in Holiday mode:** one clean the day before the back day.
8. **Route: Home Assistant on the NAS** (a Synology DS225+) for both devices. Culvery talks to Home Assistant on the home network with a token; Home Assistant holds the Eufy and Google sign-ins.
9. **Nothing existing moves** to Home Assistant: Hue and Sonos stay direct, Google Calendar and Tasks stay direct. Ring through Home Assistant goes on the Feature ideas list.
10. **One spec, two plans** (11a, 11b) as above. Arlo, Party mode, the sound pads and Settings › Home buttons are left for later.
11. The Holiday mode mockup from the brainstorm (the tray, the "When are you back?" sheet, the tray while away), including the guesses on it, was accepted as drawn: PIN both ways, the clean at 10:00, the door chime silent, the screen dark, lights left as they are when it is tapped off.
12. **Plan 7's lights-only Holiday mode becomes "Holiday lights"** (as the redesign's note 15 has it), everywhere it shows: the Lights tab's tray, Settings › Lights ("Holiday lights switch on"), TalkBack and messages. It keeps its own button and its `lights.holiday` permission for use on its own (an evening out); the house Holiday mode turns it on and off through `HouseLights` (§5.1). *(The user, after the builder check.)*
13. **Culvery turns Eco off once, at 00:01; Home/Away Assist stays on for now** (the user, 2026-10-27). The first time Holiday mode is used is the check: if Assist puts Eco back while the house is still Away, the family fix it from the Nest app, and the user then decides between turning Assist off and Culvery doing more. (Turning Assist off was weighed the same day: it costs Eco for a holiday nobody switched on and for days out.)

### What the devices allow (research, 2026-10-26)

- **Eufy:** no official API or developer programme. The X10 Pro Omni has no Matter (nothing in the Eufy app, firmware 3.4.85 robot / 1.2.27 station) and no local Tuya key; it is reachable only through Eufy's private cloud (MQTT) with the Eufy email and password. The community Home Assistant integration [`jeppesens/eufy-clean`](https://github.com/jeppesens/eufy-clean) ("Eufy Robovac MQTT", via HACS) names the X10 Pro Omni as tested. It is unofficial and can break when Eufy changes something.
- **Nest:** Google's Smart Device Management (SDM) API through Device Access, a one-off US$5 registration. It exposes the thermostat's mode, **Eco** (`MANUAL_ECO`/`OFF`), setpoints, temperature and humidity. **It cannot read or set Home/Away**, which is a structure setting. Turning Eco off returns the thermostat to its schedule. No UK hot-water control. Rate limits: per thermostat 5 commands a minute, 100 an hour. *Works with Nest* is shut down (deprecated; not used).
- **Home Assistant's Nest integration** uses SDM, so it has the same limits; in Home Assistant the thermostat is a `climate` entity whose **preset** is `none` or `eco`.

### Approaches weighed

- **A. Home Assistant on the NAS for both (chosen).** One link from Culvery (address and token), one style of call for both devices; Home Assistant keeps the Eufy cloud protocol up to date through the community integration and keeps the Google sign-in fresh. Later devices (Arlo, smart plugs for Party mode) can come the same way. Cost: another always-on service, and the 00:01 heating change needs the NAS as well as the tablet.
- **B. Culvery talks to Eufy's cloud and to SDM itself.** The Eufy password on the wall tablet, our own copy of an undocumented protocol to maintain, and Culvery's own Device Access OAuth. Rejected.
- **C. Nest direct over SDM, the hoover through Home Assistant.** Two routes for little gain once Home Assistant exists. Rejected.

## 3. The household's Home Assistant (set up 2026-10-26)

Done with the user during the brainstorm, so the build can test against it:

- Home Assistant Container (`ghcr.io/home-assistant/home-assistant:stable`) under Synology Container Manager, host networking, `TZ=Europe/London`, config in the `docker/homeassistant` shared folder. Plain `http://` on port 8123 at the NAS's address (`[ha address]` in committed files).
- HACS, and from it `Eufy Robovac MQTT`: Start and Return to dock both worked from Home Assistant, and the Eufy app on the phone stayed signed in.
- The Nest integration through its own Google Cloud project (not Culvery's), its consent screen **In production** (a Testing one would expire every 7 days), Device Access paid, and the Pub/Sub event feed. The thermostat ("Hall") shows; setting the preset to Eco and back to None worked and showed in the Google Home app.

Also done (2026-10-26): the Home Assistant user **Culvery**, not an administrator, limited to logins from the local network. The entities are `vacuum.eufy_clean_x10_pro_omni` and its battery `sensor.eufy_clean_x10_pro_omni_battery`. Still to do, during 11a: Culvery's token (§4.1).

## 4. 11a: the Home Assistant link, the hoover and the heating

### 4.1 Linking Culvery to Home Assistant

- **Settings › Other integrations** (admin-only, after Settings › Lights, order 480): the Home Assistant address (`http://[ha address]:8123`; `https://` also accepted), a **long-lived access token**, and **Check**. *(The user named the page, 2026-10-26: it will also hold later devices that come through Home Assistant, such as Arlo and smart plugs. "Home Assistant" appears on it as the source, the way "Hue Bridge" does on Settings › Lights.)*
- The token belongs to a **separate, non-admin Home Assistant user named Culvery**, made in Home Assistant (Settings › People › Users), so it can be revoked on its own. The page's help text tells the person how: sign in to Home Assistant as Culvery, Profile › Security › Long-lived access tokens › Create. *(My call.)* The person types or pastes it; Culvery never sees a Home Assistant password. **Saving a new address or token asks for a fresh Admin PIN** (as connecting Google does), since the token reaches everything in Home Assistant. *(The user, plan review 2026-10-26.)* The help text also says to give the NAS a fixed address on the router.
- **Check** calls `GET /api/` with the token; then the page lists the `vacuum.*` and `climate.*` entities from `GET /api/states` as two pickers, **Hoover** and **Thermostat**, each with "None". One of each is picked automatically when there is exactly one.
- The connection (`home.homeassistant`, "Home Assistant") holds the address and token; the chosen entity ids sit in the capabilities' own settings. Both stores are excluded from cloud backup and device transfer, as Hue's key is (the builder checks 4e's extraction rules cover them).
- **Plain HTTP on the home Wi-Fi.** The token crosses the home network unencrypted. The app's network security config already allows cleartext outside Google and Open-Meteo (Sonos needs it). Accepted for a home network, as with Sonos; HTTPS works if the household sets it up later. *(My call; the builder adds a line to the config file's comment.)*

### 4.2 Modules

Names are the builder's to adjust. The same capability/provider split as Lights and Music, within `ModuleBoundaries` (a provider depends only on core and its own capability; a capability only on core and its own family). No change to those rules. *(My call, after the builder check.)*

- **`:core:homeassistant`**: `HomeAssistantClient` (OkHttp, REST and the WebSocket API, the `Authorization: Bearer` header), the connection, and a plain-JVM JSON reader. Shared by both providers, since they share one connection and one socket (one client instance, a singleton).
- **`:capability:hoover`**: `HooverSystem` — `state: Flow<HooverState?>` (activity, battery percent, an error flag) and `start(): HooverResult`. The tray row (§4.4). No tab (`hasTab` false).
- **`:capability:heating`**: `HeatingSystem` — `state: Flow<HeatingState?>` (name, Eco on or off, current temperature, target, whether it's heating) and `setEco(on): HeatingResult`. No screen in 11a beyond Settings' status line; 11b's Holiday mode calls it.
- **`:provider:hoover-homeassistant`** and **`:provider:heating-homeassistant`**: each implements its capability's seam against the shared client:
  - Hoover: `vacuum.start` with the chosen entity. Activity from the entity's state (`docked`, `cleaning`, `returning`, `paused`, `idle`, `error`). **Battery from the integration's separate battery sensor**, found by name in `/api/states` as `sensor.<the vacuum's object id>_battery` (the integration's naming, seen on the household's instance); no such sensor, no percentage. No registry read, so no question of what a non-admin user may list. *(My call, plan review 2026-10-26.)* The vacuum entity's own `battery_level` attribute is **deprecated** in Home Assistant and not used.
  - Heating: `climate.set_preset_mode` with `eco` or `none`. Eco from the entity's `preset_mode`; temperatures and `hvac_action` from its attributes.
- **Rejected:** Culvery writing automations into Home Assistant (for example the 00:01 change). The house's logic stays in Culvery, where it's tested and visible on the wall. *(My call.)*

### 4.3 Staying up to date

- While Culvery is in front and Home shows: one WebSocket (`/api/websocket`, `auth` with the token, then `subscribe_entities` for the chosen entities, or `subscribe_events` for `state_changed` filtered to them). A full read on connect. *(Builder picks; my lean is `subscribe_entities`.)*
- Not in front: the socket closes. On reopen: backoff, and the full read covers the gap.
- Commands go over REST (`POST /api/services/<domain>/<service>`), 5 s timeout. The row shows the change when Home Assistant reports it, not before: the hoover can refuse (lifted, bin full).
- **Off Home** (Holiday mode's scheduled steps run with the screen dark, and the 30-second "did it start?" check in §6): a command is followed by REST reads of the entity (`GET /api/states/<id>`) every 5 s until it shows the change or 30 s pass. The socket isn't opened for it. *(My call, after the builder check.)*

### 4.4 The One-tap tray, 11a's part

The handoff's tray (note 13): a 2 px outline, "ONE-TAP" label, rows of tiles. The shared `HhTray` already exists in `:core:ui` (Lights uses it).

- **Placement:** Home's utility row. Today Shopping is widened into the tray's space ("Left out of Plan 10 on purpose"); with the tray, Shopping and the tray share the row, the tray 500 px wide as in the handoff. With no Shopping, the tray takes the row's left. The `HomeCardPlacer` allows one utility card today; the builder widens it (two utility cards in the row, or the tray as a second kind) and keeps 10's layout when there's no tray.
- **Contributions:** the tray collects buttons from capabilities (a small `TrayButton` contribution in `:core:plugin`, ordered by a number), so Holiday mode (11b), and later Party mode and the sound pads, add themselves without the shell knowing them. *(My call.)*
- **11a's only button, the Hoover row:** a `cleaning_services` icon, "Hoover" over its status, and a green "▶ Start" pill.
  - Status: "Docked · 86%", "Cleaning · 60%", "Going home · 40%", "Paused · 55%", "Stuck. Check the Eufy app." (error), "Can't reach Home Assistant" (§6).
  - **Start** is enabled while docked, idle or paused; while cleaning or going home it reads "Cleaning" and is inactive. *(My call: the user asked for Start only.)*
  - **Start asks for a fresh Adult or Admin PIN** every time (permission `hoover.start`, "Start the hoover", default roles Admin and Adult, fresh PIN, as Holiday mode). A Child's PIN is refused. *(The user, plan review 2026-10-26: so the tray isn't a toy.)*
- The tray shows only when it has a button: no hoover chosen, no tray, and Home stays as Plan 10 left it.

### 4.5 Heating in 11a

No button. Settings › Other integrations shows the thermostat's line: "Hall · 20.8 °C · heating to 21.2 °C" or "· Eco". 11a builds and tests `setEco`; the person can't trigger it from the wall until 11b.

### 4.6 TalkBack

- "Hoover, docked, battery 86 percent. Start cleaning, button."
- While cleaning: "Hoover, cleaning, battery 60 percent." with no button.

## 5. 11b: Holiday mode

### 5.1 Where it lives

A **`:capability:holiday`** owning the house Holiday mode: its record, the scheduler, the tray tile and the "When are you back?" sheet. `ModuleBoundaries` doesn't let one capability depend on another, so the house's parts meet in a new **`:core:house`** of small seams (*my call, after the builder check*):

- **`HouseMode`**: a `StateFlow` of whether Holiday mode is on, written by holiday, read by door (§5.5) and by `DisplayState` in `:app` (§5.6).
- **`HouseLights`**, implemented by `:capability:lights` over `LightsHouse.setHoliday(on, by)` (Plan 7 §4.5). `setHoliday` today `require`s `lights.holiday` in the caller's grant; the house implementation accepts the grant for `holiday.mode` instead (one fresh PIN covers the whole house), and the builder adjusts `LightsHouse` so both callers pass.
- **`HouseHeating`**, implemented by `:capability:heating` (`setEco`), and **`HouseHoover`**, by `:capability:hoover` (`start`).
- **`MultiDayEvents`**, implemented by `:capability:calendar`: all-day events of two days or more, overlapping a date range, from the calendars shown on the wall (title, first day, last day). Read-only.
- Each seam is optional (bound or not in Hilt): no Hue link, no lights step; and so on.

Each part is optional: no Hue link, no lights step; no thermostat chosen, no heating steps; and so on. The sheet lists only the steps that will happen.

### 5.2 The tile and the sheet

- **Tile:** row 1 of the tray, a mode tile as in the handoff (`beach_access` or similar, "Holiday mode"). **On:** filled in the accent with "ON", and "Back Sat 2 Nov" under the label. Until Party mode exists, the tile takes row 1 alone. *(My call.)*
- **Tap, while off:** a fresh Adult or Admin PIN (permission `holiday.mode`, "Turn Holiday mode on and off", default roles Admin and Adult, fresh PIN every time, as Plan 7's `lights.holiday`). Then the sheet:
  - **"When are you back?"**, the date in a large row with **Change date** (a date picker, from tomorrow up to 90 days ahead). *(My call on the range.)*
  - Filled in from the calendar when it can be: an all-day event of two days or more, in any calendar shown on the wall, that is on today or starts tomorrow. If several match, the one that started most recently. Its back date is the event's last day (the travel day home is usually inside the event; Google's exclusive end date is turned into the last day). The sheet says "From the calendar: Cornwall, ends Saturday." *(My calls, as in the accepted mockup; a person confirms either way, so the pick only has to be a good guess.)* No match: tomorrow is selected and the line reads "Pick the day you're back."
  - **"Holiday mode will:"** the steps it will take, in plain words with the dates filled in (§5.3).
  - **Turn on Holiday mode** and **Cancel**.
- **Tap, while on:** the same fresh PIN, then it turns off (§5.4). No sheet. *(The tray's rule: modes toggle.)*
- While turning on or off: "Turning on…" / "Turning off…", further taps ignored, as Plan 7's lights.

### 5.3 Turning on, and the scheduled steps

On **Turn on Holiday mode**, in order:

1. Save the record: on, the back date, and each step's state (not done).
2. **Lights:** Holiday lights on (`setHoliday(true)`): all lights off, then the chosen Hue automations (Mimic presence). Already on: left as it is.
3. **Heating:** Eco on. A backstop: Home/Away Assist usually does it when the house empties.
4. **Door chime:** silent (§5.5).
5. **Screen:** goes dark once the sheet closes (§5.6).

**A step that fails while turning on** (Hue unreachable, Home Assistant down) doesn't stop Holiday mode: it turns on, the other steps go, and the failed step is retried every 5 minutes until it succeeds or Holiday mode is turned off, with the §6 line on the tile meanwhile. Eco on matters most if the family leave with the NAS down. *(My call, after the builder check: the family are walking out of the door, so all-or-nothing would leave the house in no mode at all.)*

Then, while it stays on:

- **The day before the back date at 10:00:** the hoover starts, once.
- **The back date at 00:01:** heating Eco off, once.

**The scheduler** runs in Culvery's process: Culvery is pinned to the wall and always running, and nothing in the app uses `AlarmManager` or WorkManager today. A check runs every minute and on every start of the app: a step that is due and not yet done runs; a step whose window has passed is handled as follows. *(My call: no new Android permissions, and the catch-up on start covers a restart or a power cut.)*

- Heating: due from 00:01 on the back day until Holiday mode is turned off. A tablet that was off at 00:01 catches up when it starts.
- Hoover: due from 10:00 to 22:00 on the day before. Missed, it is skipped, not run on the back day or at night.
- A step that fails (Home Assistant unreachable, the hoover refuses) is retried every 5 minutes inside its window, and the tile shows the §6 line until it succeeds.
- Changes of clock (BST) are handled by working in local dates and times in `Europe/London`, not fixed intervals.

The back date can't be changed while away: the wall has no remote side. A holiday that runs over leaves the heating on from the planned day; the family stop it from the Nest app (§2.6).

### 5.4 Turning off

Fresh PIN, then:

1. **Lights:** `setHoliday(false)`: Holiday lights off, stopping the automations Culvery started; lights are left as they are. Lights keeps one record, so this also ends Holiday lights that someone had switched on by itself before leaving. *(My call: coming home ends the holiday's lights either way.)*
2. **Heating:** if Eco hasn't been turned off yet (they're back early), Eco off now.
3. **Door chime:** back to normal.
4. **Screen:** wakes and returns to Plan 8's normal rules.
5. Clear the record.

A failing part doesn't stop the rest; Holiday mode ends anyway and the tile's line says what didn't happen ("The heating is still on Eco. Turn it off in the Nest app.").

### 5.5 The door while away

The doorbell's chime is silent and the door card doesn't show; rings and motion are recorded in the Door tab with the rail badge, as at night (Plan 8 §4.4). The door capability reads `HouseMode` (§5.1) rather than Holiday mode writing Door's own quiet-hours settings. *(My call: nothing to put back afterwards.)*

### 5.6 The screen while away

Plan 8's `DisplayState` gets a Holiday input: while on, the screen goes **Asleep** (dark) day and night, camera wake-up is off, the night clock doesn't show, and a ring doesn't wake it (§5.5). A tap still wakes it, so someone at home can turn Holiday mode off; it sleeps again after the usual timeout. *(Accepted from the mockup.)*

### 5.7 The record

Holiday's own settings store: on, the back date, when it was turned on, and per step done or not (lights on, heating eco on, hoover run, heating eco off). On start the scheduler reads it, so a restart mid-holiday carries on. Holiday mode's on/off is Culvery's own record, as with Plan 7's lights: if someone takes the Nest off Eco from their phone, Culvery doesn't put it back.

### 5.8 TalkBack

- Tile: "Holiday mode, off. Turn on, button." / "Holiday mode, on, back Saturday 2 November. Turn off, button."
- Sheet: "When are you back? Saturday 2 November. Change date, button."

## 6. When things go wrong

The tray row or tile shows a one-line message; Settings › Other integrations shows the same.

| What happens | How Culvery notices | What it says |
|---|---|---|
| Home Assistant doesn't answer | Timeout (5 s) or refused | "Can't reach Home Assistant. Check the NAS is on." |
| The tablet is offline | Android reports no network | "The tablet is offline. Check the Wi-Fi." |
| The token was revoked | 401 | "Home Assistant no longer accepts Culvery's token. Add a new one in Settings › Other integrations." |
| A chosen entity is gone | Not in the states | "Home Assistant has no hoover called that any more. Choose it again in Settings › Other integrations." (and the same for the thermostat) |
| Eufy's cloud is down or the integration broke | The vacuum entity is `unavailable` | "Home Assistant can't reach the hoover. Check the Eufy app." |
| The Nest link expired or Google is down | The climate entity is `unavailable` | "Home Assistant can't reach the thermostat. Check the Nest integration." |
| The hoover refuses to start | It doesn't leave docked/idle within 30 s, or reports `error` | "The hoover didn't start. Check the Eufy app." |
| A Holiday mode step failed | §5.3, §5.4 | On the tile: "The heating didn't come off Eco. Turn it off in the Nest app." / "The hoover didn't run." |

**Privacy.** Logs carry the call and its outcome only: no address, no token, no entity ids or names (a thermostat or room can be named after a person), no back dates. `LogHygieneTest` covers the new modules. No calendar event titles in logs either; the sheet shows the title, the log doesn't.

## 7. Tests

- **JSON** (JVM): fixtures for `/api/`, `/api/states` (a vacuum in each state, the battery sensor, a climate entity with preset `eco` and `none`, `unavailable`), WebSocket auth ok/invalid, `subscribe_entities` events, a 401. From Home Assistant's documented shapes first, replaced by redacted captures from the household's instance (§8.2).
- **Client** (MockWebServer): each service call's path and body, the bearer header, timeouts, 401, the socket's auth and reconnect.
- **Tray:** the Hoover row in each state; Start enabled or not; no tray without a hoover; Shopping and the tray sharing the row, and Plan 10's layout when there's no tray.
- **Holiday logic** (with a test clock): the steps in order on turning on; the sheet's calendar pick (none, one, several, half term starting tomorrow vs a holiday on today); the hoover at 10:00 the day before and skipped when missed; Eco off at 00:01 and caught up after a restart; across a BST change; retries every 5 minutes inside the window; turning off early clears Eco; each part missing (no Hue, no thermostat, no hoover); a failing part doesn't stop the others.
- **Access:** Holiday mode asks for a fresh PIN both ways; a Child's PIN is refused; Start asks for a fresh Adult or Admin PIN and refuses a Child's; saving a new Home Assistant link asks for a fresh Admin PIN; Settings is admin-only.
- **Door and screen:** silent while on (recorded, no chime, no card); `DisplayState` Asleep day and night while on, a tap wakes it.
- **UI** (Robolectric; Roborazzi for the tray with the Hoover row, the Holiday tile off and on, the sheet with and without a calendar match, Settings › Other integrations): the TalkBack words.

## 8. Rollout

### 8.1 Emulator

On the `culvery-5` AVD (debug build) against the household's Home Assistant by address: linking with the token, the pickers, the tray's Hoover row and Start (the user says when it's fine to run the hoover), the thermostat line. For 11b: Holiday mode with a back date of tomorrow, then with the clock moved (a debug-only clock offset, never in release) to see 10:00 the day before and 00:01. No address or token goes into a committed file.

### 8.2 Checks against the real devices

1. **What Home Assistant sees when the house is Away.** Moved to the first real holiday (§2, decision 13): Home Assistant's history for the thermostat shows whether Assist put Eco back after Culvery cleared it at 00:01.
2. **Hoover battery sensor and states** from the real integration (the entity names, which states it reports while emptying the bin or washing the mop).
3. **Captures:** a debug-only switch logs the shape of each answer with names, ids and addresses replaced; fixtures come from these after the names check.

### 8.3 On the tablet

A release `install -r`; never uninstall. 11a: link, Start the hoover from the wall. 11b: turn Holiday mode on with a back date a day or two ahead and check the hoover the day before, Eco off at 00:01, the chime silent and the screen dark, then turn it off. The tablet's logs are read whenever it's plugged in while work is under way.

## 9. Not in this plan

- **Re-applying Eco after 00:01:** Culvery turns Eco off once (decision 13); anything more waits for the first real holiday.
- Day-to-day heating on the wall (temperature, boost, the schedule); UK hot water (SDM doesn't expose it).
- Stopping or sending the hoover home, rooms, scenes, maps, mop and suction settings.
- Arlo in Holiday mode (through Home Assistant's community Arlo integration, a later check), Party mode, the sound pads, Settings › Home buttons.
- Changing the back date while away; Holiday mode working out a holiday from the calendar by itself.
- Moving Hue, Sonos or Ring to Home Assistant (Ring through Home Assistant is on the Feature ideas list).
- Culvery installing or managing Home Assistant.
