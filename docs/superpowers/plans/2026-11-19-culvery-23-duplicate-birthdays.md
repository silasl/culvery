# Culvery Plan 23: Birthdays that show twice — the plan

Spec: `docs/superpowers/specs/2026-11-19-culvery-23-duplicate-birthdays-design.md`. Branch `plan-23-duplicate-birthdays`. One task; the controller builds it, since it is three lines.

## Task 1: leave Google's birthday kind out

Files:
- `provider/calendar-google/src/main/java/uk/co/siland/culvery/provider/calendar_google/GoogleEvents.kt`
- `provider/calendar-google/src/test/java/uk/co/siland/culvery/provider/calendar_google/GoogleReadTest.kt`

Steps:
- [ ] **Step 1, the failing test.** In `GoogleReadTest`, after `aWorkingLocationIsSkippedInAFullSyncAndRemovedInAnIncrementalOne`, add `aGoogleBirthdayIsSkippedInAFullSyncAndRemovedInAnIncrementalOne`: put an all-day event with `eventType` `birthday`; a full sync has no upserts; put it again; the incremental sync's `removedIds` is exactly its id. Use a made-up title ("Sam's birthday"), never a real name or date.
- [ ] **Step 2.** Run `./gradlew :provider:calendar-google:testDebugUnitTest --tests "*GoogleReadTest*"` and see the new test fail on the full sync's upserts.
- [ ] **Step 3, the guard test.** Add `aBirthdayTheFamilyMadeIsStillRead`: one all-day event titled "Sam birthday" with no `eventType`, one with `eventType` `default`; the full sync returns both. It passes before and after.
- [ ] **Step 4, the change.** In `GoogleEvents.kt`, replace the `WORKING_LOCATION` constant with the set of kinds the wall leaves out (`workingLocation`, `birthday`), use it in `isGone`, and say in its KDoc why a Google birthday is left out (the family keeps its own events; Google's can't be tagged).
- [ ] **Step 5.** Run the module's tests, then the gate: `./gradlew testDebugUnitTest verifyRoborazziDebug`.
- [ ] **Step 6.** Commit: "Leave Google's own birthday events off the wall".

## After the task
- Final review of the branch's diff by one read-only reviewer.
- Emulator walk-through (spec section 6): the debug build on `culvery-5`; the Calendar tab and a sample birthday read as before.
- Release build, mapping saved to `culvery-notes/mappings/`, `adb install -r` on the tablet with the user there to unlock it; check it pinned.
- Mark item 42 on the follow-ups list; update the memory. The merge waits for the user's say.

## Rulings
1. (The user, 2026-11-19) Leave Google's birthday kind out of the wall. A birthday that exists only as that kind doesn't show.
2. (Mine) No simplify pass: the change is under the size the user's rule sets for one.
3. (Mine) One reviewer for the plan and one for the diff, instead of three, for a one-task plan.
4. (Mine, from the plan review) Only birthdays are left out. Google's birthday kind also holds anniversaries and other dates, told apart by `birthdayProperties.type`; those stay on the wall. Costs one more field asked of Google. If the user wants the whole kind gone, it is a one-line change.
5. (Mine, from the plan review) The test's event is a date of a yearly series, as Google sends a birthday, and it also checks the look-up and that the series is never fetched. Steps 1, 3 and 4 above were built that way.
6. (Mine) Two low findings go to the follow-ups list rather than being fixed: a reminder added from a Google birthday can outlive it, and its title can stay as a suggestion for up to a year.
