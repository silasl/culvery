# Culvery Plan 13: Party mode (design)

2026-10-29. Builds on Plan 11b (Holiday mode) and Plan 12 (smart plugs, which already supports a second mode).

## 1. Intent

One tap at the start of a party sets the house up for it: the party-light plugs on, a chosen Sonos playlist playing on every speaker, and chosen Hue scenes in chosen rooms. One tap at the end puts it all back. The user configures what Party does once; the tap itself has no choices.

**Goals**
- A Party mode beside Holiday, turned on and off from its own tile with an Adult's or Admin's PIN.
- Plugs: each plug's Party choice (Ignore / On / Off), using Plan 12's mode machinery and its Party column.
- Music: one chosen Sonos favourite or playlist, playing on all speakers grouped together.
- Lights: per room, "leave alone" or a chosen Hue scene.
- Ending puts plugs, music and lights back as they were, and says in one line per kind what couldn't be put back.

**Non-goals**
- No timer or automatic end (the user, 2026-10-29). Party ends only when someone taps it off.
- No volume setting: Party leaves the volume as it is (my call).
- No playlist picker at tap time (the user: "configurable" means set up beforehand).

**Anti-goals**
- Party must never leave the house half-set: if a step fails it says so on the tile, and the other steps still run.
- Party must never change a plug, light or speaker the family changed by hand during the party when it ends (same rule as Plan 12 decision 17).
- The children must not be able to start or end it (PIN, as Holiday).

## 2. Decisions

1. **The user's:** same shape as Holiday: choose which plugs Party switches; start a Sonos playlist on all speakers; optional Hue scenes; all configurable.
2. **The user's:** on ending, music stops and lights and plugs go back to how they were before Party started.
3. **The user's:** no automatic end.
4. **My call:** speaker groups go back to how they were before Party grouped them, after the music stops.
5. **My call:** a light room or speaker group changed by hand during the party is left as it is when Party ends.
6. **My call:** Party can't be turned on while Holiday mode is on, and the tile says why ("Turn Holiday mode off first."). Holiday can't be turned on while Party is on, with the matching line. Plan 12's two-mode plug rules still hold, in case the store ever has both.
7. **My call:** Party's tile sits beside Holiday's in the One-tap tray; it needs its own permission, `party.mode`, granted to Adults and Admins like `holiday.mode`.
8. **My call:** if Party has nothing configured (no plug on On or Off, no playlist, no scene), the tile still turns on, and the sheet says "Party mode has nothing to do yet. Choose plugs, music and lights in Settings › Party mode."
9. **My call:** Party keeps nothing running while it's on: no retries every 5 minutes for music or lights (a party is a few hours and someone's there to fix it). Plugs keep Plan 12's own retry rule unchanged.
10. **My call:** music isn't resumed after the party: whatever was playing before stays stopped (decision 2 says the music stops).
11. **My call:** "the music is still Party's" means the group Party made still has the same rooms and is playing. If the song changed but the group didn't, it's still Party's and is paused. If anyone regrouped or paused it, it's left.
12. **My call:** a light room counts as unchanged when the bridge's active scene is still Party's scene. If the bridge reports no active scene for it, the room is left as it is (safer than guessing).
13. **My call:** a saved speaker room or light room that no longer exists is skipped quietly; if no chosen speaker room is left, the music step fails with its line.
14. **My call:** lines are one per kind, as on Holiday's tile; the lights line names the rooms: "Some lights didn’t change: {rooms}." / "Some lights didn’t go back: {rooms}." Names show on the tile and are never logged.

## 3. What the family sees

- **Tile (One-tap tray):** "Party mode" with "Off" or "On". While on, one line per failed kind under it (as Holiday's tile, 12 §5.5). Tapping it asks for the PIN, then opens a sheet listing what will happen (plugs from `HousePlugs.preview`, "Play {playlist} on every speaker", "{scene} in {room}" per room) with "Turn Party mode on" / "Turn Party mode off".
- **Turning off:** the sheet lists what goes back; after it, the tile shows any can't-put-back lines until they're cleared by the next turn-on, or for 10 minutes, as Holiday does.
- **Settings › Party mode** (a new section, PIN-guarded like Settings): Music: a picker of the household's Sonos favourites and playlists ("None" by default), and which speakers (all, by default; a checkbox per room). Lights: per Hue room, "Leave alone" or one of that room's scenes. A line pointing to the Plugs tab for each plug's Party choice.
- **Plugs tab:** a second mode column, "Party mode", added by registering the mode (Plan 12 §5.1: "a second contribution shows a second column with no other change").

## 4. Behaviour

### 4.1 Turning on (in this order, each step independent)
1. Record Party as on (survives a restart), with the snapshot below.
2. **Plugs:** `HousePlugs.begin(PARTY, by)` then `enter(PARTY)`, exactly as Holiday.
3. **Music:** read the speaker groups; remember each group's rooms and coordinator; group the chosen rooms into one group (new: Sonos local UPnP, joining each room to one coordinator); play the chosen favourite or playlist on it.
4. **Lights:** for each room with a scene: remember the room's on/off and brightness (and its active scene id, when the bridge reports one); recall the scene.

### 4.2 Turning off
1. **Music:** if the party group is still the group Party made and still playing Party's choice, pause it and put the groups back as remembered. If someone regrouped or changed what's playing, leave the music alone.
2. **Lights:** for each room Party set: if its active scene is still Party's scene, put it back (the remembered scene if there was one, else on/off and brightness). If someone changed it, leave it.
3. **Plugs:** `HousePlugs.leave(PARTY)`.
4. Record Party as off; show one line per kind that failed (heating isn't involved): music, then plugs, then lights.

### 4.3 Restarts
The record and snapshot are in a DataStore, so a restart mid-party still knows what to put back. A turn-on cut short by a restart is put back on the first read that finds Party off (Holiday's rule, 12 §5.2).

## 5. Lines (copy)
- "The music didn’t start. Check the Sonos app." / "The music didn’t stop. Stop it in the Sonos app."
- "Some lights didn’t change: {rooms}." / "Some lights didn’t go back: {rooms}."
- Plugs: Plan 12's existing lines.
- "Turn Holiday mode off first." / "Turn Party mode off first."

## 6. Constraints
- Everything from Plan 12's Global Constraints: no new library, tests fail first, Roborazzi for new screens, no ids, names or addresses in logs or committed files.
- Hue and Sonos stay read-only in tests on real devices without the user's go; a real party run is the user's.
- No personal data (rooms, playlist names) in logs.

## 6a. Structure (my calls)
- **Seams in `:core:house`, as Holiday:** `HouseModes.PARTY = "party"`, `HousePermissions.PARTY_MODE = "party.mode"`, a `HouseMode` registration for Party (which adds the Plugs tab column), and two new seams, `HouseMusic` (start the party music and remember the groups; stop and put the groups back) and `HouseLights` gaining Party's scene-and-put-back pair. `:capability:party` depends only on `:core:house`, like `:capability:holiday`.
- **`MusicSystem` grows grouping** in this plan (join rooms to one coordinator, and put a remembered set of groups back), implemented in `:provider:music-sonos` and in every fake music system. The "still Party's" check reads the groups afresh, not from the cached `groups` flow.
- **Holiday changes in this plan:** Holiday's turn-on is refused while Party is on (decision 6), checked in each mode's actions before the PIN sheet opens, from a shared "which modes are on" read in `:core:house`.

## 7. Left to the builder
- How grouping is done over Sonos UPnP (`SetAVTransportURI` with `x-rincon:` to the coordinator, `BecomeCoordinatorOfStandaloneGroup` to undo), and how "still the group Party made" is judged.
- Whether to restore colour per light (Hue v2 per-light reads) or room on/off and brightness only; the second is enough.
- Where the Party module lives (a `:capability:party` mirroring `:capability:holiday` is the expectation).
- Settings layout, within the house style.

## 8. Testing
- Unit: each step's on and off, the hand-change rules (music regrouped, scene changed), Holiday/Party exclusion, nothing configured, a restart mid-party, each failure line.
- Roborazzi: the tile off/on/with lines; the sheet; Settings › Party mode; the Plugs tab with two columns.
- Emulator walk-through with fakes for music and lights; a real party run only on the user's go.
