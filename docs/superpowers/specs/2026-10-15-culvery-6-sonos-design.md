# Culvery 6: Sonos

Brainstormed 2026-10-15. The second outside service after Ring, and the first that Culvery both reads and controls: live state from the speakers and commands back to them. Follow-ups file: `docs/superpowers/plans/2026-09-23-plan1-followups.md` ("Feature ideas", the Sonos line).

Built on Plan 5 (`plan-5-ring-doorbell`, not yet merged): it uses the alert layer and rail slot 30, and changes the door card by one argument (§3.3). Branch `plan-6-sonos`, from `plan-5-ring-doorbell`, rebased onto main once Plan 5 merges.

## 1. Purpose and outcome

The household plays music on Sonos, almost always from the home media library (a NAS folder added as the Sonos Music Library) and sometimes BBC Sounds. From the wall they want to:

- control each Sonos room on a Music tab;
- play, pause, skip and change the volume from Home, for the room that's playing (nearly always the same one);
- start the whole library shuffled with one tap;
- start one of the household's Sonos Favourites;
- hear the doorbell over the music: Sonos goes quieter while the door card shows.

Done means: all of the above works against the household's real speakers, from the emulator by address (§8.1) and from the tablet by scan (§8.3); the Sonos fixtures replaced by redacted captures; a final review, a local merge, and the build installed on the SM-T510.

## 2. Decisions (the user, 2026-10-15)

1. **Job:** a remote for each room, plus starting music (Shuffle everything, Favourites), plus the doorbell link.
2. **Home follows the room that's playing.** Settings › Music has a usual room. Home shows the playing room; with several playing, the usual room if it's one of them, else the one that started most recently; with none playing, the usual room. A room paused from Home stays on Home until it stops or another room plays, so Pause then Play resumes it (plan review, 2026-10-15).
3. **Doorbell:** every playing group drops to a share of its volume (Settings, default 20 %) while the door card shows, in quiet hours too, and goes back when the card closes. A group someone changed meanwhile is left as they set it.
4. **Groups:** shown as Sonos groups them, one row each, with a group volume and each room's own. Grouping and ungrouping stay in the Sonos app.
5. **Favourites:** in. Each Music row has a Favourites button listing the household's Sonos Favourites; tapping one plays it in that group.
6. **No limits:** everyone can use the controls, as with the Sonos app on a phone. Settings › Music is admin-only like the rest of Settings.
7. **Route:** Culvery talks to the speakers directly on the home Wi-Fi (approach A below). No Sonos sign-in, nothing secret stored.

### Approaches weighed

- **A. Sonos's local UPnP API (chosen).** SOAP to port 1400 on each speaker, speakers found by SSDP. No account, no cloud; the only route that can queue the local library. Sonos doesn't promise to keep it, though it has survived S2 and the 2024 app rewrite, and SoCo and Home Assistant rely on it.
- **B. Sonos Control API (cloud).** Developer registration, OAuth tokens on the wall, polling the cloud for state; can't queue the local library, so no Shuffle everything.
- **C. A hub** (Home Assistant). Steadier commands, but a whole system for one panel. The user notes a hub on the NAS is possible later if it helps other services (follow-ups file).

The `MusicSystem` seam (§3.1) keeps the provider replaceable: if the local API closes, a cloud or hub provider takes its place without touching the screens.

## 3. Modules

### 3.1 `:capability:music`

The capability: `id = "music"`, label "Music", icon `speaker`, rail order 30. `hasTab` is true once a `music.sonos` connection exists. It owns:

- `MusicSystem`, what a provider implements:
  - `groups: Flow<List<SpeakerGroup>>`;
  - `play(group)`, `pause(group)`, `next(group)`;
  - `setGroupVolume(group, percent)`, `setRoomVolume(room, percent)`;
  - `favourites(): List<Favourite>`, `playFavourite(group, favourite)`;
  - `shuffleEverything(group)`.
- `SpeakerGroup`: id, rooms (id, name, volume), coordinator room, play state (`PLAYING`, `PAUSED`, `STOPPED`, `TRANSITIONING`), what's playing (title, artist, album; or station and programme for radio), art URL, `canSkip`, group volume, and when it last started playing (for §2.2).
- `Favourite`: id, name, art URL, kind (`STREAM` or `QUEUED`).
- The Home-room rule (§2.2), the doorbell lowering (§4.6), the Home card, the Music tab, the Favourites sheet and Settings › Music.

### 3.2 `:provider:music-sonos`

- `SonosDiscovery`: SSDP `M-SEARCH` for `urn:schemas-upnp-org:device:ZonePlayer:1` under a `WifiManager.MulticastLock` (`CHANGE_WIFI_MULTICAST_STATE`), about 5 seconds.
- `SonosClient`: SOAP over OkHttp to `http://<speaker>:1400`: `AVTransport` (play, pause, next, queue, play mode, transport and position info), `RenderingControl` and `GroupRenderingControl` (volumes), `ZoneGroupTopology` (layout, household id), `ContentDirectory` (`FV:2` Favourites, `A:TRACKS` library).
- `SonosXml`: parses responses and DIDL-Lite metadata. Plain JVM, no Android.
- `ProviderDescriptor` `music.sonos`, "Sonos". The connection's config holds the household id and the last speaker address that answered. No secrets.

### 3.3 `:core:plugin`

- `AlertQuieter` (a Hilt multibinding set): `suspend fun quiet()` and `suspend fun restore()`.
- `AlertHost.show(content, loud = false)` and `suspend fun AlertHost.quieted()`.
  - When a loud alert shows, the shell runs every quieter's `quiet()` at once.
  - `quieted()` returns when they have all finished, or after 500 ms, whichever comes first.
  - When the alert is dismissed, the shell runs every `restore()`.
  - A loud alert that replaces a loud one runs nothing again.
- Door: `DoorAlerts` passes `loud = true` and awaits `quieted()` before the chime. No other Door change; Door never sees Music.

### 3.4 No sample speakers

Dropped at the plan review (2026-10-15): tests and screenshots use plain values and a test `MusicSystem`, and walk-throughs use the household's real speakers by address (§8.1).

### 3.5 Backup

The connection config sits in the stores 4e's data extraction rules already exclude from cloud backup and device transfer. The rooms list is held in memory only. No new rule.

## 4. How it works

### 4.1 Finding the speakers

Settings › Music › **Find Sonos** runs the SSDP search. Any speaker that answers gives the whole household through `ZoneGroupTopology`; Culvery saves the household id and that speaker's address as the `music.sonos` connection and the Music tab appears. Nothing found: **Enter a speaker's address** (shown in the Sonos app under About My System), checked by asking it for the topology.

When the saved address stops answering, Culvery scans once by itself, keeps the first speaker of the same household, and saves its address. Only then does it report a problem (§6).

### 4.2 Staying up to date

Culvery checks the speakers on a timer rather than subscribing to UPnP events, which would need an HTTP server on the tablet.

- While Culvery is in front and Home or the Music tab shows: each group's coordinator every **2 seconds** (transport info, position info, group volume).
- Group layout (rooms, groups, room volumes): grouping changes are noticed within a check, as each check also asks every speaker which group it is in (ZoneGroupTopology `GetZoneGroupAttributes`) and reads the layout at once when one differs from the screen; the full layout is still read every **30 seconds**, and when the Music tab opens (tablet walk-through, 2026-10-16).
- After a command: that group at once.
- Not in front, or neither screen showing: no checks.
- Art: from the speaker's `/getaa` URL (or the stream's own art URL), cached in memory only.

### 4.3 Commands

Each goes to the group's coordinator. The screen changes at once and the next check corrects it if the speaker disagrees. Next is greyed out when `canSkip` is false (radio streams).

### 4.4 Shuffle everything

In the chosen group:

1. `RemoveAllTracksFromQueue`;
2. `AddURIToQueue` with the library container (`A:TRACKS`);
3. `SetAVTransportURI` to the group's queue (`x-rincon-queue:<coordinator>#0`);
4. `SetPlayMode SHUFFLE_NOREPEAT`;
5. `Play`.

The queue comes before shuffle because Sonos refuses a play mode while the current source is a stream, such as a paused BBC station (plan review, 2026-10-15).

It replaces that group's queue. Sonos holds at most 40,000 tracks in a queue; a larger library gets the first 40,000, shuffled. An empty library browse stops before step 1 (§6).

### 4.5 Favourites

Read from `FV:2` each time the sheet opens. A `STREAM` favourite (a radio station) is set as the group's transport URI with its metadata and played. A `QUEUED` one (a playlist, an album) replaces the queue, as in §4.4 steps 1, 2, 3 and 5, without changing the play mode.

### 4.6 Lowering for the doorbell

`MusicQuieter` is Music's `AlertQuieter`.

- **quiet():** for each group playing at that moment, and each room in it, read the room's volume `v`, remember `(v, set)` with `set = round(v × share)` (share from Settings, 0–50 %, default 20 %), then set it. Rooms are remembered before the command goes, so a command that times out but took effect is still restored. Groups are lowered in parallel. A group that fails is skipped. If groups are already lowered, it does nothing.
- **restore():** room by room, read its volume. If it is within 1 of `set`, put it back to `v`; otherwise leave it. A failed restore is tried once more. Then forget them all. Restoring room by room keeps a group's balance, even from 0 %.
- Kept in memory only. A restart while lowered leaves those groups low.

## 5. Screens

Layout, sizes and copy are starting values for the user's end-of-v1 design walk-through; the Home card in particular may move or change size then. It keeps to the existing `HomeCard` contract so moving it is cheap.

### 5.1 The Home card

A regular-size `HomeCard`, placed after Today and the forecast; the existing placer drops it when it doesn't fit.

- The room's name, the art, title and artist (radio: station, and programme when there is one).
- **Play/Pause**, **Next** (greyed for radio), and the group's volume slider.
- **Shuffle everything**, only while that group is stopped or paused, so a tap mid-song can't replace the queue.
- Nothing playing anywhere: the usual room, "Nothing playing", Play (resumes its queue) and Shuffle everything.
- No usual room chosen yet and nothing playing: the first room Sonos lists.
- Sonos unreachable: the §6 line, no controls.

### 5.2 The Music tab

One row per group, playing groups first, then the rest in Sonos's order. Each row:

- the rooms' names joined ("Kitchen + Lounge"), the art, title and artist;
- Play/Pause, Next, the group volume;
- grouped rows: a **Rooms** chip that shows each room's own slider in the row;
- **Favourites** and **Shuffle everything**, always shown here.

A status line at the top shows only when something is wrong (§6). Open to everyone, no PIN. No rail badge.

### 5.3 The Favourites sheet

A sheet on `OverlayHost`: the Sonos Favourites by name, with art. Tapping one plays it in the row's group and closes the sheet. Empty: "No Sonos Favourites yet. Add them in the Sonos app."

### 5.4 Settings › Music

Admin-only.

- **Sonos:** the status (§6), **Find Sonos**, **Enter a speaker's address**.
- **Usual room:** a picker from the rooms found.
- **When the doorbell rings, lower music to:** a slider, 0–50 % of its volume, default 20 %. 0 % mutes.

### 5.5 TalkBack

- Card and rows: "Kitchen, playing Song by Artist"; "Kitchen, paused"; "Kitchen, nothing playing".
- Buttons: "Pause Kitchen", "Play Kitchen", "Next track in Kitchen", "Shuffle everything in Kitchen".
- Sliders: "Kitchen volume, 30 percent".

## 6. When things go wrong

The Music tab's status line, the Home card's line and Settings › Music show the same message.

| What happens | How Culvery notices | What it says |
|---|---|---|
| No speaker answers, after one automatic rescan | Checks or commands time out (3 s) | "Culvery can't reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi." |
| The tablet is offline | Android reports no network | "The tablet is offline. Check the Wi-Fi." |
| Sonos refuses a command | SOAP fault | On that row: "Sonos didn't do that. Try again." The group is checked again |
| A Favourite can't play from the wall | SOAP fault on play | "Sonos couldn't play this here." The sheet stays open |
| No music library | `A:TRACKS` browse returns nothing | "Sonos has no music library. Add it in the Sonos app." |
| Lowering fails for a group | Command fails or times out | That group is left as it is; the chime still plays after the 500 ms cap |
| The saved household is gone (system reset) | Topology gives a different household id | "Culvery found a different Sonos system. Find Sonos again in Settings › Music." |

**Privacy.** Only speakers on the home network are contacted; nothing goes to the internet except art for a stream that names its own URL. Logs carry the command and its outcome only: no room names (a room can be named after a person), track titles, favourites or addresses. The rooms list is kept in memory, not stored.

## 7. Tests

- **Sonos XML** (JVM): fixtures for the topology (one room, a group, the household id), transport and position info (a library track, a BBC radio stream with station and programme, stopped), volumes, Favourites (a station, a playlist), the library browse, and a SOAP fault. From documented examples first; replaced by redacted captures (§8.2).
- **Commands** (JVM, MockWebServer as a speaker): each command's SOAP body and action header; Shuffle everything's five steps in order; a stream vs a queued Favourite; the rescan after timeouts; a different household id.
- **Logic:** the Home-room rule (usual room playing, another playing, two playing, none playing, no usual room); lowering (only playing groups, the share, restore only if unchanged, a second ring while lowered, a failing group); checks only while in front and showing.
- **UI** (Robolectric; Roborazzi for the Home card's states, a single and a grouped Music row, the Favourites sheet, Settings › Music): Next greyed for radio; Shuffle everything hidden on Home while playing; the TalkBack words.
- **Alert layer:** a loud alert runs the quieters and `quieted()` returns at 500 ms when one hangs; dismiss runs restore; a loud alert over a loud one doesn't quiet again. Door's existing tests pass, plus one that the chime waits for `quieted()`.

## 8. Rollout

### 8.1 Emulator

On the `culvery-5` AVD (debug build), with **Enter a speaker's address** pointed at a real speaker. The emulator's ordinary connections reach the home network through the PC, though its SSDP scan won't, so commands, Shuffle everything, Favourites and the doorbell lowering (with a fake ring) get tested on the real system.

### 8.2 Captures

During §8.1, a debug-only switch logs the shape of each Sonos response, with names, titles and addresses replaced. The fixtures come from these after the names check, using placeholders ([room], [person]).

### 8.3 On the tablet

A release `install -r`, with Plan 5's build or after it; never uninstall. Find Sonos on the tablet (the scan's real test), choose the usual room, ring the bell with music playing. From then on, the tablet's logs are read whenever it's plugged in while work is under way.

## 9. Not in this plan

- Grouping or ungrouping rooms from the wall.
- Browsing the library or BBC Sounds; search.
- UPnP event subscriptions instead of checks.
- A volume cap or quiet-hours limit on Sonos.
- The Sonos cloud Control API.
- A home hub on the NAS bridging Sonos, Hue, Arlo, the thermostat and smart plugs (follow-ups file).
- Party mode (follow-ups file): it would drive Sonos through `MusicSystem`.
