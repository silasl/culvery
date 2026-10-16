# Culvery 7: Philips Hue

Brainstormed 2026-10-16. The third outside service after Ring and Sonos, and like Sonos it both reads and controls: live state from the Hue Bridge and commands back to it. Follow-ups file: `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("Feature ideas", the Philips Hue and Holiday mode lines).

Branch `plan-7-hue`, from `main` at 290f4cc, in the worktree `culvery-hue`.

## 1. Purpose and outcome

The household's lights are Philips Hue, on a Hue Bridge. From the wall they want to:

- turn every light off with one tap, on leaving the house or going to bed;
- turn the main room (the lounge or snug, whatever Hue calls it) on and off from Home;
- control each room on a Lights tab: on and off, brightness, and the room's Hue scenes;
- put the lights into **Holiday mode** while the family is away, so the house looks lived in.

Holiday mode here is the lights' part only. The whole-app Holiday mode in the feature ideas (tablet asleep, Arlo, thermostat) comes later and will switch this one on as one of its parts.

Done means: all of the above works against the household's real bridge, from the emulator by address (§8.1) and from the tablet by search (§8.3); the protocol checks in §8.2 answered; a final review, a local merge, and the build installed on the SM-T510.

## 2. Decisions (the user, 2026-10-16)

1. **Job:** All lights off, Holiday mode, and the main room on Home; every room on a Lights tab.
2. **The Home card is small:** three buttons: the main room (toggle), **All lights off**, **Holiday mode**. The main room is chosen in Settings › Lights.
3. **The main-room button** toggles the room. On brings back how it was last lit (Hue's own behaviour). A › on the button opens that room on the Lights tab.
4. **Each room on the Lights tab:** an on/off switch, a brightness slider and the room's Hue scenes as chips. No colour picker, no bulb-by-bulb control.
5. **All lights off** covers every room; Settings › Lights can leave rooms out (a garden, a sleeping child's room). By default nothing is left out.
6. **Holiday mode on:** All lights off, then the Hue automations chosen in Settings › Lights (for example Mimic presence) are switched on. **Off:** the automations Culvery switched on are switched off and the lights are left as they are. While on, the Holiday mode button shows "On"; while it is being turned on or off, "Turning on…" or "Turning off…", and further taps are ignored (plan review, 2026-10-16).
7. **If an automation doesn't start:** Holiday mode still turns the lights off and says it couldn't start the automation, so use the Hue app for that (§6). Since the bridge check (§8.2 check 1, confirmed), this covers only a start the read-back can't confirm: Mimic presence does start from the wall.
8. **Who:** the lights are free for everyone, as with a light switch. Holiday mode, both ways, needs a **fresh Adult or Admin PIN**: asked every time, even within a signed-in session (plan review, 2026-10-16). Settings › Lights is admin-only like the rest of Settings.
9. **Route:** Culvery talks to the Hue Bridge directly on the home network (approach A below). No Hue account. Pairing is one press of the bridge's button.

### Approaches weighed

- **A. The bridge's local API, CLIP v2 (chosen).** HTTPS to the bridge on the home network, paired once with the link button. No account, no cloud, works when Hue's cloud is down.
- **B. Hue's Remote API (cloud).** A Hue account sign-in and tokens on the wall, for control from outside the house, which the wall never needs.
- **C. A hub** (Home Assistant on the NAS). Possible later if it helps several services (follow-ups file); not needed for one bridge.

The `LightsSystem` seam (§3.1) keeps the provider replaceable, as `MusicSystem` does for Sonos.

## 3. Modules

The same shape as Music. Names are the builder's to adjust.

### 3.1 `:capability:lights`

The capability: `id = "lights"`, label "Lights", a light-bulb icon (add it to the font subset and `Icons` if it isn't there), rail order **40**, between Music (30) and Door (50). `hasTab` is true once a `lights.hue` connection exists. It owns:

- `LightsSystem`, what a provider implements:
  - `rooms: Flow<List<LightRoom>>`;
  - `setOn(room, on)`, `setBrightness(room, percent)`, `recallScene(room, scene)`;
  - `automations(): List<Automation>`, `setAutomation(automation, on): AutomationResult` (confirmed by the read-back, or not).
- `LightRoom`: id, name, on (any light in it on), brightness (0–100), scenes (id, name), the active scene if Hue reports one, and whether it has any lights it can dim.
- `Automation`: id, name, and whether it is on (Mimic presence by its `pm_state`, others by `enabled`, §4.5).
- **All lights off** and **Holiday mode** as functions of the capability, not of the screens, so the later whole-app Holiday mode can call them.
- The Home card, the Lights tab and Settings › Lights.
- A permission `lights.holiday`, "Turn Holiday mode on and off", default roles Admin and Adult, **fresh PIN** (plan review, 2026-10-16).

### 3.2 `:provider:lights-hue`

- `HueDiscovery`: an mDNS search for `_hue._tcp` with Android's `NsdManager` (under a multicast lock if needed), about 5 seconds. Each service found is resolved with `registerServiceInfoCallback` on Android 14 (API 34) and later, and below it with `resolveService` (deprecated in API 34, the only call there; the tablet runs Android 11), one at a time from a queue, as it refuses a second while one runs (plan review, 2026-10-16). No use of Hue's cloud discovery (`discovery.meethue.com`). *(My call: keeps everything on the home network.)*
- `HueClient`: CLIP v2 over OkHttp, reading answers through a shared `:core:net` module (the helper and network check Sonos has, moved there; plan review, 2026-10-16), to `https://<bridge>/clip/v2/resource/…` with the `hue-application-key` header. Resources used: `room`, `grouped_light`, `scene`, `behavior_instance`, `bridge`.
- TLS: the bridge's certificate is checked against Signify's Hue root CA, bundled in the app, and its common name against the bridge id. No other route, and no trust-all route. **Confirmed on the household's bridge (2026-10-16; BSB002, software 1978418030, API 1.78):** it presents one leaf certificate issued by `C=NL, O=Philips Hue, CN=root-bridge`, whose common name is the bridge id (16 lower-case hex characters). The pin-at-pairing fallback is dropped.
- The bridge id before pairing comes from an unauthenticated source: the mDNS record's `bridgeid`, or `GET /api/config` for a typed address. *(My call.)*
- Pairing: `POST https://<bridge>/api` with a `devicetype` of `culvery#tablet` and `generateclientkey: true` after the link button is pressed. The answer's `username` is the application key.
- `HueJson`: parses responses. Plain JVM, no Android.
- `ProviderDescriptor` `lights.hue`, "Philips Hue". The connection's config holds the bridge id, the last address that answered, and the application key.

### 3.3 Settings stored

In Lights' own settings store: the main room id, the ids of rooms All lights off leaves out, the ids of the Holiday mode automations, whether Holiday mode is on, and the ids of the automations Culvery switched on for it (§4.5). Room and automation **ids**, not names.

### 3.4 Backup

The application key is the one secret: anyone holding it on the home network can control the lights. It and the settings sit in stores 4e's data extraction rules already exclude from cloud backup and device transfer; the builder checks the new store is covered and adds it if not. The rooms list is held in memory only.

### 3.5 No sample lights

As with Sonos: tests and screenshots use plain values and a test `LightsSystem`; walk-throughs use the household's real bridge by address (§8.1).

## 4. How it works

### 4.1 Finding and pairing the bridge

Settings › Lights › **Find Hue Bridge** runs the mDNS search. Nothing found: **Enter the bridge's address** (shown in the Hue app under Settings › My Hue system › the bridge), checked by asking it for `/api/config`, which answers without pairing.

With a bridge found, Culvery says "Press the round button on top of the Hue Bridge" and tries to pair every 2 seconds for 30 seconds (the bridge accepts pairing for 30 seconds after the press). Paired: the connection is saved and the Lights tab appears. Not paired in time: "The button wasn't pressed in time. Try again."

When the saved address stops answering, Culvery searches once by itself, keeps the bridge with the same id, and saves its new address. Only then does it report a problem (§6).

### 4.2 Staying up to date

- While Culvery is in front and Home or the Lights tab shows: Culvery holds the bridge's event stream open (`/eventstream/clip/v2`, server-sent events) and updates rooms from it. A full read of rooms, lights' grouped state and scenes happens when the stream opens and every **60 seconds**. *(My call: the bridge is built for the event stream; it saves polling every room.)*
- Changes made in the Hue app or at a wall switch show within about a second.
- Not in front, or neither screen showing: the stream is closed.
- The stream drops: reopen it with backoff; the full read covers the gap.

### 4.3 Commands

- A room on or off: its `grouped_light` `on`. On with no other setting brings back how it was last lit.
- Brightness: its `grouped_light` `dimming.brightness`. While the slider is dragged, at most one command every 300 ms, and one on release; the pacing below may send fewer, always the latest value. Moving the slider of a room that's off turns it on at that brightness, as in the Hue app. *(My calls.)*
- A scene: the scene's `recall` with `action: active`.

The screen changes at once and the stream or the next read corrects it if the bridge disagrees. Hue asks apps to send group commands at about one a second and light commands at about ten a second; Culvery paces to that, and a command waiting its turn is replaced by a newer one for the same room or light (plan review, 2026-10-16).

### 4.4 All lights off

- Nothing left out: the bridge's whole-home `grouped_light` (the one under the `bridge_home` resource), off.
- Rooms left out (ignoring any no longer on the bridge; if none is left, as nothing left out): each included room's `grouped_light`, off, plus each light that is in no room (only in a zone, or nowhere), off, paced as in §4.3, so leaving the garden out leaves only the garden on. The screen shows them all off at once. *(My call: "every light except the rooms left out".)*
- A room whose command fails: the others still go; the §6 line names how many didn't.

### 4.5 Holiday mode

Revised from the bridge check and the plan review (2026-10-16).

- **What an automation is.** Mimic presence (Hue's script named `PM`) stays `enabled: true` and `status: running` whether or not it is on; its real state is `state.pm_state`, `started` or `stopped`. It starts with `PUT behavior_instance/<id>` `{"trigger":{"start":true}}` and stops with `{"trigger":{"stop":true}}`. Any other automation (a `Schedule`, say) is switched by `enabled` true or false and read back by `enabled`; its `status` stays `running` either way. The bridge answers 200 to a trigger it doesn't understand and does nothing, so **the read-back is the only proof**.
- **On** (fresh Adult or Admin PIN): All lights off (§4.4). As soon as that has gone, Holiday mode is saved as on. Then each chosen automation that is **off** is switched on and read back; each one confirmed is saved as one Culvery started. One already on is left alone and not recorded.
- **Off** (the same PIN rule): exactly the automations Culvery started are switched off and read back, whatever the ticks say now; lights are left as they are; saved as off at the end.
- **Started?** Read back about every second for about 5 seconds. Not confirmed: Holiday mode stays on with the §6 message naming that automation (by the name last read from the bridge; "an automation" if none is known).
- **One at a time.** While Holiday mode or All lights off is running, further taps on either are ignored and the Holiday mode button says "Turning on…" or "Turning off…". Whether a tap turns it on or off is decided from Culvery's saved record when the run starts.
- **The bridge can't be reached** before All lights off has gone: nothing changes, Holiday mode stays as it was, and the §6 "can't reach" line shows. Reached but some rooms didn't turn off, or an automation didn't start: Holiday mode is on, with the §6 line. The capability's function returns which of these happened, for the later whole-app Holiday mode, and takes the caller's PIN check as an argument. *(My call.)*
- **No automations chosen:** Holiday mode is only All lights off and the "On" marker; Settings › Lights says so under the automations list.
- Holiday mode's on/off is Culvery's own record. If someone stops the automation in the Hue app, Culvery still shows Holiday mode on until someone turns it off on the wall. *(My call: simple, and honest about whose switch it is.)*

## 5. Screens

Layout, sizes and copy are starting values for the user's end-of-v1 design walk-through (the mockup from the brainstorm is in `.superpowers/brainstorm/`, not committed). It keeps to the existing `HomeCard` contract so moving it is cheap.

### 5.1 The Home card

A `REGULAR`-size `HomeCard`. When every card wants to show (Today, Coming up, the forecast, Music and Lights), the forecast gives way to Lights: Lights' priority sits between the forecast's and Coming up's (plan review, 2026-10-16).

- **The main room**, a large button: its name and "On · 70%" or "Off". A tap toggles it; a › on its right opens the Lights tab scrolled to that room.
- **All lights off.**
- **Holiday mode**, with "On" or "Off" under it ("Turning on…" or "Turning off…" while it runs), lit in the accent while on. A tap asks for a fresh PIN (§2.8), then turns it on or off.
- No main room chosen yet: the first room Hue lists.
- Bridge unreachable: the §6 line, no controls.

### 5.2 The Lights tab

At the top: **All lights off** and **Holiday mode** (as on Home). Then one row per Hue room, in Hue's order:

- the room's name and an on/off switch;
- a brightness slider (hidden for a room with no dimmable lights);
- the room's scenes as chips, the active one marked. No scenes: no chip row.

A status line at the top shows only when something is wrong (§6). Open to everyone, no PIN except Holiday mode. No rail badge.

### 5.3 Settings › Lights

Admin-only. Placed after Settings › Music (order 470).

- **Hue Bridge:** the status (§6), **Find Hue Bridge**, **Enter the bridge's address**.
- **Main room:** a picker from the rooms found.
- **All lights off leaves out:** a tick list of rooms, none ticked by default. Left-out rooms no longer on the bridge are dropped, with a line saying how many.
- **Holiday mode switches on:** a tick list of the bridge's automations by name, none ticked by default. Empty: "No Hue automations yet. Set one up in the Hue app, such as Mimic presence."

### 5.4 TalkBack

- Room button and rows: "Lounge, on, 70 percent"; "Lounge, off".
- Buttons: "Turn the Lounge on", "Turn the Lounge off", "Turn all lights off", "Turn Holiday mode on", "Turn Holiday mode off".
- Slider: "Lounge brightness, 70 percent". Scene chips: "Relax scene in the Lounge", selected when active.

## 6. When things go wrong

The Lights tab's status line, the Home card's line and Settings › Lights show the same message.

| What happens | How Culvery notices | What it says |
|---|---|---|
| The bridge doesn't answer, after one automatic search | Reads or commands time out (3 s) | "Culvery can't reach the Hue Bridge. Check it's plugged in and the tablet is on the home Wi-Fi." |
| The tablet is offline | Android reports no network | "The tablet is offline. Check the Wi-Fi." |
| The pairing was removed in the Hue app | 403 or an unauthorised error | "The Hue Bridge no longer knows Culvery. Pair it again in Settings › Lights." |
| A different bridge answers at the address | The bridge id differs | "Culvery found a different Hue Bridge. Find Hue Bridge again in Settings › Lights." |
| The bridge refuses a command | An error in the answer | On that row: "Hue didn't do that. Try again." |
| Some rooms didn't turn off | Commands fail during §4.4 | "2 rooms didn't turn off. Try again." |
| An automation won't start | §4.5 read-back | "Lights are off, but Culvery couldn't start [automation]. Start it in the Hue app." |
| A chosen automation or room is gone | Its id isn't on the bridge | Settings › Lights drops it from the list (for left-out rooms, saying how many); Holiday mode and All lights off carry on with the rest |

**Privacy.** Only the bridge on the home network is contacted. Logs carry the command and its outcome only: no room, scene or automation names (a room can be named after a person), no addresses, and never the application key. `LogHygieneTest` covers the new modules.

## 7. Tests

- **Hue JSON** (JVM): fixtures for rooms, grouped lights, scenes (active and not), the whole-home group, behavior instances (enabled, disabled, started, not started), the bridge resource, a pairing success, "link button not pressed", and an unauthorised error. From Hue's documented examples first; replaced by redacted captures (§8.2).
- **Commands** (JVM, MockWebServer as a bridge): each command's path and body; All lights off with and without left-out rooms; Holiday mode's order (lights off before automations) and its read-back; pacing; the search after timeouts; a different bridge id; TLS refusing a certificate that isn't from the Hue CA or names a different bridge.
- **Logic:** the main room when none is chosen; Holiday mode on and off with none, one or two automations, one that won't start, one that's gone; the stream open only while in front and showing.
- **Access:** Holiday mode asks for a PIN both ways; a Child's PIN is refused; the lights need none.
- **UI** (Robolectric; Roborazzi for the Home card on, off and Holiday mode on, a Lights row with and without scenes, Settings › Lights, the pairing prompt): the TalkBack words.

## 8. Rollout

### 8.1 Emulator

On the `culvery-5` AVD (debug build), with **Enter the bridge's address** pointed at the real bridge: pairing, the rooms, commands, All lights off, Holiday mode. The emulator's mDNS search won't reach the home network.

The household's network (2026-10-16): the bridge sits on the broadband router's subnet, the Wi-Fi (and so the tablet) on the mesh's, a different one. By address the bridge answers across them; mDNS probably doesn't cross. So both walk-throughs pair by the typed address first and record whether the search finds the bridge. No address goes into a committed file (`[bridge address]`).

### 8.2 Protocol checks and captures

On the real bridge, during §8.1:

1. **Automations (confirmed 2026-10-16 from the PC):** Mimic presence starts and stops by its trigger and shows it in `pm_state`; it stayed started for 10 seconds and stopped on the stop trigger; the Hue app's own start changes only `pm_state`. A `Schedule` switches by `enabled` and reads back by it (§4.5). Re-checked from the wall in §8.1.
2. **The whole-home group** turns every light off in one command.
3. **TLS (confirmed 2026-10-16):** the bridge's certificate chains to the Hue CA and names the bridge id (§3.2).

A debug-only switch logs the shape of each Hue response, with names and addresses replaced. The fixtures come from these after the names check, using placeholders ([room], [person]).

### 8.3 On the tablet

A release `install -r`; never uninstall. Pair by the typed address first (§8.1's network note), then try Find Hue Bridge and record whether the search finds the bridge across the subnets; pair, choose the main room, use All lights off and Holiday mode. From then on, the tablet's logs are read whenever it's plugged in while work is under way.

## 9. Not in this plan

- Colour and white-warmth pickers; bulb-by-bulb control; Hue zones (rooms only).
- Making or editing scenes or automations from the wall.
- Culvery running its own presence pattern (ruling 7's option B, for later).
- The whole-app Holiday mode (tablet asleep, Arlo, thermostat).
- Lights reacting to Culvery (a flash when the doorbell rings, on at sunset).
- Hue's cloud Remote API; Hue motion sensors, switches and Hue Secure.
- A home hub on the NAS (follow-ups file).
- Party mode (follow-ups file): it would drive Hue through `LightsSystem`.
