# Culvery Plan 17: Bin collections

Brainstormed with the user on 2026-11-04. Visual choices stay provisional for the end-of-v1 design review. The house's address is personal data: it is in neither this document nor any committed file.

## 1. Intent

The family's bin collections show on Culvery's calendar: which bins go, on which day. Nobody has to look up the council's site or keep repeating events by hand. Mid Sussex District Council changed its rounds in November 2026 (food weekly, recycling and garden fortnightly, refuse every three weeks), so hand-made repeating events are already out of date.

Success: each collection day shows as one calendar entry naming the bins that go, with a stripe for each in that bin's colour. It is right because the council's own page says so, read daily. When the page can't be read, the wall says so where the calendar's problems already show, and the last dates read stay up.

### Goals
- Culvery reads the house's collections from the council's site itself, once a day (2.1).
- One entry per collection day, with one stripe per bin (2.3).
- Each bin type has a colour, chosen in Settings from a contrast-checked set (2.4).
- A failed read shows "Can't read the bin dates" in two places until a read works (2.5).

### Non-goals
- Reminders the night before. They belong to the Reminders idea on the follow-ups list (the user).
- A Home card or tile for bins. The Today card shows the entry like any other event.
- Anything sent to a phone or by email. The status tab, when built, is the place for more detail (the user).
- Any council but Mid Sussex, and any other source such as Home Assistant or an aggregator (2.1).
- Writing, editing or deleting bin entries on the wall.

### Anti-goals (a failure even if it works)
- The wrong bins, or the wrong day, shown with no sign anything is wrong.
- A broken read wiping the dates already known.
- The address, or anything from the council's page tied to it, in logs, committed files, tests, fixtures or screenshots.
- Culvery hammering the council's site: more than a handful of requests a day.
- A bin entry that reads as a person's event.

## 2. Decisions (the user's)

### 2.1 Culvery reads the council's site
Culvery reads the page itself, with nothing new on the NAS. The user looked at the alternatives: no Binminder service was found, Waste Collection Schedule has no Mid Sussex source, and UKBinCollectionData's Home Assistant add-on would add load to a NAS that is short of memory. UKBinCollectionData's `MidSussexDistrictCouncil.py` is the reference for the steps.

### 2.2 Setup takes the house name or number and the postcode
The `Track` value in the site's links is a dated session code (`Track=YYYY/MM/DD/…`) handed out on each visit, not a property id. The user has no tracker link. So the connection stores the house name or number and the postcode, and each read goes the whole way (1.1 in 4.1).

### 2.3 One entry per collection day, like a shared event
One all-day entry per day, for example "Bins: Food, Recycling". The chip is the neutral surface a shared event uses (`surf2`). Its edge is the shared event's `StripedBar`, with one stripe per bin in that bin's colour, where a shared event shows one per person. It goes everywhere events go: the Today card, the week view, the Calendar tab, the event sheet.

### 2.4 Bin colours
- The user's bins: Recycling blue, Waste black, Garden brown, Food green. These are the defaults.
- There is no chip colour to choose; the chip is neutral (2.3).
- Each bin type's colour is chosen in Settings from a short set of inks. Each ink has a day and a night version, checked for contrast on both backgrounds, as the birthday colours were. Black, at night, is a dark grey with a lighter edge, or another version that the contrast test passes.

### 2.5 When it can't read
"Can't read the bin dates" shows on the bins calendar's row in Settings › Calendars and on the Calendar tab's status line, until a read works. The user schedules a fix from there. This stands in until the status tab is built.

## 3. Calls I made (to check while reading)

### 3.1 Names on the wall
The council's long names map to the user's words:

| Council's name | On the wall |
|---|---|
| Domestic Food Waste Service | Food |
| Domestic Garden Waste Service | Garden |
| Domestic Recycling Waste Collection Service | Recycling |
| Domestic Refuse Waste Collection Service | Waste |

The match ignores case and spacing, and looks for the words "food", "garden", "recycling" and "refuse". A name with none of them shows as the council wrote it, with a neutral stripe (the `mute` ink). It does not count as a failed read.

### 3.2 Title and order
- The entry is titled "Bins: " followed by the names, in the order Food, Garden, Recycling, Waste, then any unknown names alphabetically.
- With one bin it is still "Bins: Food", so the chip always reads as bins.
- The stripes follow the same order. A small bin icon (Material Symbols `delete`, or the nearest in the icon set Culvery already ships) leads the title on chips that show an icon.

### 3.3 Who it's for
- The entries are tagged for Family, so Family's filter shows them and nobody's personal view does.
- The chip doesn't take Family's tint (2.3).

### 3.4 Keeping the last dates
- A failed read changes nothing in the stored entries. They stay until their day passes.
- If no read has worked for 7 days, the line stays the same and the entries still show. My call: wrong-but-flagged beats empty.
- A successful read replaces the whole set (`fullReplace`), so a cancelled collection disappears.

### 3.5 How often
- One read a day, plus Update now in Settings and on the Calendar tab, which reads at once.
- A failed read is retried at most every hour, up to three times that day.
- A read is three page loads (4.1), so a normal day is three requests and a bad day about twelve.
- The calendar engine syncs other calendars more often. The bins provider answers from what it read last, until a day has passed or someone taps Update now.

### 3.6 Setup flow
1. Settings › Calendars › Add a calendar lists "Mid Sussex bins" beside Google.
2. It asks for the house name or number and the postcode, then searches.
3. If the council lists more than one address, the user picks one from the list. The address line as the council prints it is stored, so the next reads pick the same one.
4. It then shows the next collection it found ("Next: Monday 9 November — Food") and an Add calendar button.
5. One calendar ("Bins") comes with the connection, tagged for Family.

### 3.7 Personal data
- The house name or number, postcode and chosen address line live in the connection's stored config only.
- The row in Settings shows "Mid Sussex bins". It never shows the address.
- Logs say only "Bins read: N collections" or the failure's kind ("Bins read failed: no collections section"). They never include the address, the `Track` value or a URL.
- Tests and fixtures use a made-up address and pages written by hand in the shape of 4.1. They are never a saved copy of the real page.

## 4. How it works

### 4.1 The council's page (seen once, 2026-11-04, with the user's go)
1. GET `https://waste.services.midsussex.gov.uk/` sets a session cookie. Its "View my collections for refuse, gardening, food and recycling" link carries `?Track=…&serviceID=A&seq=1`.
2. POST to that link with `seq=1` changed to `seq=2`, with form fields `address_name_number` and `address_postcode`.
   - The answer's `div#property_list` holds one link per matching address, `mop.php?Track=…&serviceID=A&seq=3&pIndex=N`.
   - Each link's text and `aria-label` is the address in capitals.
3. GET the chosen address's link.
   - `section` / `h2#upcoming-scheduled-collections` holds one `ul.displayinlineblock` per collection, with three `li`:
     1. an image (`img/<name>.png`, alt "Collection Item");
     2. the date as `dd/MM/yyyy` in a `p`;
     3. the bin's name in a `p`.
   - A `span#waste-hint` means "no scheduled services at this address".
- On the day it was seen, the page listed nine collections across four weeks. All were Mondays, and some Mondays had all four bins.

A read fails when any step's page lacks what it should have:
- no "View my collections" link;
- no `property_list`;
- the stored address line no longer listed;
- no collections section, or one with no rows;
- a row whose date doesn't parse.

Each failure is a `ConnectionHealth.Error` kind named for the step. A `waste-hint` page counts as a failed read too, because the house always has collections.

### 4.2 The provider
- A new module, `:provider:calendar-midsussex`, implements `CalendarProvider`. It is read-only, with no `Feature.WRITE`.
- `sources` returns one source, "Bins". `sync` returns one `RemoteEvent` per collection day: all-day, end exclusive the next day, not recurring, `forPerson` "family", with the title from 3.2.
- Which bins a day has must reach the chip, so their stripes can be drawn. How they travel is left to the builder (6). The title alone isn't enough to recolour from, because unknown names exist (3.1).
- Network failures map to `OfflineException` or `UnreachableException` as for Google. Page-shape failures map to the "Can't read" health (2.5).
  - Today `ConnectionHealth.Error` shows as "Stopped updating — disconnect and connect again." The bins failure needs its own case with its own words, in Settings › Calendars (`ReviewCalendars.kt`) and in the Calendar tab's problem line (`CalendarUi.problemLine`).
  - Between daily reads, the provider answers from its last read. A failure it stored must keep being reported on those answers until a real read works, so the engine doesn't clear the line on a cached answer.
- The page lists about four weeks, so bins show only that far ahead on the calendar. That's expected.
- `CalendarProviderContractTest` covers it, as it covers the fake and Google providers.

### 4.3 The chip
- A bins entry draws as a shared event does (2.3): neutral surface, `StripedBar` edge.
- The stripe colours come from the bin types (2.4) in the order of 3.2, rather than from people.
- The event sheet shows the same bar and title, with no edit controls, like any read-only calendar's event. It adds a line "From Mid Sussex District Council".

### 4.4 Settings
- The bins calendar's page lists each bin type seen so far, with its colour swatch. A tap opens the ink set (2.4).
- The four known types are always listed, so colours can be set before the first read.
- An unknown name seen in a read is added to the list with the neutral ink until a colour is chosen.

## 5. Constraints and house rules
- Kotlin, Compose, Hilt, Room, as the rest of Culvery. Use the HTTP client the other providers use; if a small HTML parser is needed, add one with a licence that allows shipping (jsoup, MIT) and say so in the plan.
- No deprecated APIs without asking the user.
- The address never in logs, committed files, tests, fixtures or screenshots (3.7). `LogHygieneTest` gets a sample line.
- Requests use a plain browser User-Agent, as the reference does, and a 60 s timeout per read under the engine's own.
- New inks are contrast-tested in `ContrastTest` for day and night, like the person and birthday colours.
- Files stay UTF-8 with their existing line endings. Comments say only what the code can't.
- The user's real address is asked for at the walk-through on the tablet, not before. The emulator walk-through uses the fake pages, served locally or from the debug sample.

## 6. Left to the builder
- How a day's bin types travel from the provider to the chip: a new field on `RemoteEvent` and the stored event, a structured part of the event, or another way. Pick the one with the smallest change to the calendar engine.
- The exact ink values for the four bins and the rest of the set. Keep five to eight inks, each with day and night versions.
- How the daily cadence sits with the engine's sync loop (3.5).
- Whether the debug build gets a sample bins calendar, like the sample Google calendar, for the emulator walk-through.

## 7. Walk-through checks
1. **Emulator, made-up address:**
   - Add Mid Sussex bins, pick an address from a two-address list, see the next collection, and add it.
   - The Today card, the week view and the Calendar tab show one chip per day with the right stripes, by day and by night.
2. **A day with all four bins:** one chip, four stripes in the order Food, Garden, Recycling, Waste.
3. **Colours:** change Waste's colour in Settings and see the chips follow. The black ink is clearly visible at night.
4. **A page with an unknown bin name:** the name shows as written, with a neutral stripe, and it is listed in Settings.
5. **A page missing its collections section:**
   - "Can't read the bin dates" shows on the row and on the Calendar tab's status line.
   - The last dates stay up.
   - A good page clears the line.
6. **Update now** reads at once. Otherwise there's no more than one read a day; count the requests in logcat.
7. **Logs:** nothing names the address or the `Track` value.
8. **The tablet, with the user's address:**
   - The first read matches the council's site.
   - The user checks the chips from across the room.
