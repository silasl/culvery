# Culvery

A wall-mounted Android tablet app for running a family home. Built as a set of modules so new services (calendars, lights, cameras…) plug in without touching the rest of the app, and nothing about a particular household is baked in.

- Design reference: `docs/design/house_hub_handoff/`
- Spec: `docs/superpowers/specs/2026-09-23-culvery-v1-design.md`
- Plans: `docs/superpowers/plans/`

## Build and run

Requirements: JDK 17, Android SDK platform 35.

```bash
./gradlew assembleDebug testDebugUnitTest
./gradlew :app:installDebug
```

Use `testDebugUnitTest`, not `test` — release unit tests don't include the Compose test activity.

Debug builds create an **Admin** with PIN **1234** on first launch so Settings is reachable. Release builds do not.

## Modules

| Module | Purpose |
|---|---|
| `:app` | Activity, kiosk mode, nav rail, Home grid, wiring only |
| `:core:ui` | Design tokens, DM Sans, Material Symbols, shared components |
| `:core:plugin` | `Capability` and `HomeCard` contracts, Home card placement |
| `:core:household` | People (with role and PIN hash), Family, home location — `household.db` |
| `:core:access` | Permissions, PIN hashing, lockout, 60 s session, PIN pad |

Rules: `:core:*` never depends on `:app`, `:capability:*` or `:provider:*`. Capabilities never depend on providers. Each module that stores data owns its own database file.

## Adding a capability

1. Create `capability/<name>/build.gradle.kts` with `id("culvery.android.library")`, `id("culvery.android.compose")`, `id("culvery.hilt")`, and depend on `:core:plugin` (plus `:core:access` if it has actions).
2. Implement `Capability` (tab, icon, `order`, `hasTab`, Home `cards()`).
3. Bind it: `@Binds @IntoSet abstract fun bind(impl: MyCapability): Capability` in a Hilt module.
4. If it has actions, implement `PermissionSource` and bind it `@IntoSet` too; call `AccessControl.authorise("<name>.<action>")` before acting.
5. `include(":capability:<name>")` in `settings.gradle.kts` and add it to `:app` dependencies.

No other module changes.

## Kiosk mode

Release builds pin the app to the screen (Android "screen pinning"). Leave properly via **Settings › Exit kiosk** (Admin PIN, always asked).

Screen pinning can also be undone by holding **Back + Overview**. To stop a child doing that, on the tablet: set a screen lock (PIN), then turn on **Settings › Security › Other security settings › Pin windows › Ask for PIN before unpinning**. Unpinning then drops to the lock screen.

A stronger device-owner lock is possible later; it is not built yet.

## PINs

- Everyone can have their own 4-digit PIN. PINs must be unique in the household because the PIN identifies the person. The pad submits on the 4th digit.
- Viewing never needs a PIN. Changing things does.
- A session lasts 60 seconds after the last touch. Tap your name in the rail to lock early. Settings closes when the session ends.
- Exiting kiosk and managing people always ask for a PIN, even mid-session.
- 5 wrong PINs lock the pad for 30 seconds, doubling each time up to 16 minutes. Only a PIN that is allowed to do the thing clears the count.
- This is kid-proofing, not strong security.

**Forgotten PIN:** an Admin can reset anyone's PIN in Settings. If every Admin has forgotten theirs, clear the app's data (Android Settings › Apps › Culvery › Storage › Clear data). That wipes all configuration and starts setup again.

## Licences

DM Sans: SIL Open Font License (`core/ui/licenses/OFL-DMSans.txt`). Material Symbols: Apache 2.0 (`core/ui/licenses/Apache-MaterialSymbols.txt`).
