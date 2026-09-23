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
3. **Audience:** **External**, which is needed for personal Gmail accounts. Leave publishing status as **Testing** for now; the spike checks whether that's good enough.
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

## 5. Emulator note
The emulator must use a **Google APIs** system image, which includes Google Play services. Add the Google account on the emulator under **Settings › Accounts** before testing sign-in.
