# Setting up Google Calendar access

Culvery signs in to Google on the tablet itself. No client secret or token is ever put in the code. Google only lets the app sign in if a Google Cloud project has an **Android OAuth client** registered for the app's package name and signing certificate. Anyone building Culvery needs to do this once for their own Cloud project.

Google renames these console screens from time to time. If a label below doesn't match, look for the nearest equivalent.

## 1. Create the project
1. Open <https://console.cloud.google.com/> and sign in with the Google account that will own the project.
2. Create a new project, e.g. `culvery`.

## 2. Enable the Calendar API
1. Go to **APIs & Services › Library**.
2. Search for **Google Calendar API** and click **Enable**.

## 3. Configure the consent screen (Google Auth Platform)
1. Go to **Google Auth Platform** (or **APIs & Services › OAuth consent screen**) and click **Get started**.
2. **App information:** name `Culvery`, and your email as the support and developer contact.
3. **Audience:** **External**, which is needed for personal Gmail accounts. Set the publishing status to **In production**. The app stays unverified, which is fine for a household: the first sign-in shows a "Google hasn't verified this app" screen (choose **Advanced**, then **Go to Culvery (unsafe)**), up to 100 users can sign in, and access doesn't expire. In **Testing**, Google ends access every 7 days, so the tablet would show "Google needs reconnecting" weekly.
4. **Test users:** add every Google account that will sign in on the tablet, e.g. the household account.
5. **Data access › Add or remove scopes**, and add both of these:
   - `https://www.googleapis.com/auth/calendar.readonly`
   - `https://www.googleapis.com/auth/calendar.events`

   These are "sensitive" scopes. That's expected: while the app is unverified, Google shows an "unverified app" warning at sign-in, which you can safely continue past for your own app.

## 4. Create the Android OAuth client
1. Go to **Google Auth Platform › Clients** (or **APIs & Services › Credentials**), then **Create client**, and pick application type **Android**.
2. Enter:
   - **Package name:** `uk.co.siland.culvery`
   - **SHA-1 certificate fingerprint:** the fingerprint of the key that signs your build. For debug builds, get it from the repo root:
     ```bash
     ./gradlew :app:signingReport
     ```
     Copy the `SHA1:` line under `Variant: debug`.
3. Click **Create**. Nothing needs to be downloaded or copied into the project. Google matches the app by package name and fingerprint.

A release build is signed with a different key, so it needs a second Android client with that key's SHA-1. That comes with release signing in Plan 4.

## 5. Connect on the tablet
The emulator (or tablet) needs Google Play services: an image with **Google Play**. Add the family's Google account under **Settings › Accounts** first, so the chooser offers it. Then, in Culvery, **Settings › Connect Google Calendar** (Admin PIN): pick the account, allow calendar access (both boxes), and the household's calendars appear. Every calendar in the account is added; the account's own calendar becomes the one the tablet adds events to, and a calendar named after one person (e.g. "Mia's swimming") shows in their colour. A person whose name is also a common word ("May", "Will") can catch a calendar like "May half term"; Plan 4's Settings will let you change a mapping. If access lapses, the Calendar tab shows "Google needs reconnecting": tap it and approve again. There is no disconnect yet: if the wrong account was connected, clear Culvery's data (**Settings › Apps › Culvery › Storage**) and connect again.

Release builds need a second Android client with the release key's SHA-1 (Plan 4). In release kiosk mode the account chooser may not appear; if so, exit kiosk (Settings › Exit kiosk), connect, and return.

**Emulator note.** Play services 26.34 on Android 15 (API 35) emulator images can crash-loop with `NetworkCapability 37 out of range` after a network change, which blocks sign-in ("Google needs reconnecting" or "Can't reach"). To test offline, turn Wi-Fi and mobile data off (`adb shell svc wifi disable`, `adb shell svc data disable`) rather than airplane mode. If the crash loop starts, `adb shell pm uninstall-system-updates com.google.android.gms` restores the image's own Play services; the account may then ask you to sign in again.
