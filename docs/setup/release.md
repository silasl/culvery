# Building and installing a release

A release build is signed with your own key, shrunk by R8, and logs only warnings and errors. It needs its own Google OAuth client, because Google matches the app by its signing key.

## 1. Make the key (once)

On Windows, from the repo root in PowerShell:

```powershell
.\tools\new-release-key.ps1
```

If Windows PowerShell says running scripts is disabled, run `powershell -ExecutionPolicy Bypass -File .\tools\new-release-key.ps1` instead (or `pwsh .\tools\new-release-key.ps1` with PowerShell 7).

(elsewhere: `bash tools/new-release-key.sh`; in Git Bash it runs only with `winpty`, as keytool would otherwise show the password as you type). It asks where to keep the key (outside the repo; the default is `~/.culvery/`), then runs `keytool`, which asks for the password itself. It prints the four lines to add to **your own** `~/.gradle/gradle.properties` (never the repo's):

```properties
culvery.release.storeFile=C:/Users/you/.culvery/culvery-release.jks
culvery.release.storePassword=<password>
culvery.release.keyAlias=culvery
culvery.release.keyPassword=<password>
```

Use an absolute path with forward slashes. Back up the `.jks` file and its password together, somewhere other than this computer. **Losing the key** means Culvery can't be updated on the tablet: it has to be uninstalled (its setup is lost) and Google needs a new OAuth client for the new key. The repo ignores `*.jks` and `*.keystore`, so a key copied here by mistake isn't committed.

Without all four lines a release build stops at once with "Release signing isn't set up: …". Debug builds and `./gradlew testDebugUnitTest verifyRoborazziDebug` never need them.

## 2. Give Google the key

Run `./gradlew :app:signingReport` and copy the `SHA1:` line under `Variant: release`. Then make the release Android OAuth client: `docs/setup/google-calendar.md`, section 4, last paragraph.

## 3. Build and install

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

A release can't be installed over a debug build (their signatures differ): run `adb uninstall uk.co.siland.culvery` first, which removes the debug build's setup and data. Installing one release over the next keeps everything.

## 4. Reading a release crash

R8 renames the app's classes in a release build, but the app's own exception classes keep their names, so a log line names the exception that caused it. A stack trace still shows renamed classes and methods. Each build writes `app/build/outputs/mapping/release/mapping.txt`: keep a copy beside every APK you install. To read a stack trace from that build, run the Android SDK's `retrace` with it:

```bash
"$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin/retrace" mapping.txt crash.txt
```

A release logs only warnings and errors, and they name no person, calendar, account, town, coordinates or time zone.
