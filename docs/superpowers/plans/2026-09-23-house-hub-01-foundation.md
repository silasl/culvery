# House Hub — Plan 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A runnable, kiosk-mode House Hub shell on the tablet: themed nav rail and Home screen driven by a capability registry, household people with roles and 4-digit PINs, and a PIN-gated Settings placeholder.

**Architecture:** Multi-module Gradle project with convention plugins so a new module's build file is a few lines. `:core:*` modules hold shared contracts and services; `:app` only wires. Capabilities (none yet — they arrive in Plan 2) register into a Hilt `Set<Capability>`; the shell renders whatever is registered. Each module that persists data owns its own Room database file, so capability modules never edit a shared schema.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (Material 3, restyled), Hilt (KSP), Room, SharedPreferences (lockout counter), Coroutines/Flow, JUnit4 + Robolectric + Truth + Turbine.

**Spec:** `docs/superpowers/specs/2026-09-23-house-hub-v1-design.md`

**Plan series** (each its own document, written when the previous one is done):
1. **Foundation** (this plan)
2. Calendar capability — `Connection` / `ProviderDescriptor` / `ConnectionHealth` contracts and connection storage, calendar contract, cache, outbox, contract test suite, fake provider, Today/Coming up cards, week view, event detail, quick-add, `.self`/`.own` permission checks, shell navigation from cards, module-dependency guard, Roborazzi screenshot tests
3. Calendar providers — Google OAuth spike (spec §12 risk), Google provider, ICS provider, `SecretStore`
4. Weather capability (incl. Home header slot and sunrise/sunset feed) + first-run setup wizard + Settings screens + connections health + release signing

**Deviations from spec (deliberate):**
- Tests use **JUnit4** (spec §11 updated): Robolectric and Roborazzi run on JUnit4 runners.
- The Google OAuth spike (spec §12) moves to the start of Plan 3. Nothing in Plans 1–2 depends on Google.
- `AccessControl.authorise(vararg anyOf): Authorised?` replaces spec §5's indicative `authorise(permission): Person?`, so one prompt can accept either `calendar.event.create` or `.create.self`. Plan 2 adds a target check for `.self`/`.own` as an additional parameter.
- Screen pinning (`startLockTask`) only runs in release builds; in debug it would show a system prompt on every start.
- Deferred from the plan review (recorded so they are not lost): Home header contributor slot (Plan 4); re-validating a session when a person's role changes (Plan 4, with the people editor); release signing and removing the debug Admin (Plan 4); DM Sans optical-size axis and status-bar wifi/battery icons (unscheduled polish).

## Global Constraints

- Package root `uk.co.siland.househub`; app name "House Hub".
- `minSdk 29`, `compileSdk 35`, `targetSdk 35`, JDK 17, landscape only.
- Stay on AGP 8.x, Gradle 8.13, Hilt 2.57.x. If a version fails to resolve, take the newest **patch** in the same minor line. Never move to a new major/minor (e.g. AGP 9, Hilt 2.59+) without asking the user.
- Design canvas 1280×800 dp; hand-off `docs/design/house_hub_handoff/README.md` is authoritative for colours, type, spacing, radii.
- No shadows; flat colours; no blur.
- No secrets, tokens or household data in source or build config.
- Do not use `androidx.security:security-crypto` / `EncryptedSharedPreferences` (deprecated). Do not use any other deprecated API without asking the user first. `@OptIn` to an *experimental* API is allowed where the plan says so.
- PINs are exactly 4 ASCII digits.
- Commit messages contain only the message — no `Co-Authored-By` or any attribution trailer.
- Module dependency rule: `:core:*` never depends on `:app`, `:capability:*` or `:provider:*`.
- Run tests with `./gradlew testDebugUnitTest` (Git Bash) or `.\gradlew.bat testDebugUnitTest` (PowerShell). Never plain `test`: release unit tests lack the Compose test activity.

## Review Focus

1. **Two people choosing the same PIN** — must be rejected, since the PIN is what identifies the person. Test in Task 6 (`duplicatePinIsRejected`).
2. **Session expiring between opening an action and confirming it** — `authorise` must prompt again after 60 s idle, and Settings must close. Tests in Task 7 (`sessionExpiresAfterSixtySecondsIdle`, `touchExtendsSessionButItStillExpires`) and Task 9 (`settingsCloseWhenSessionEnds`).
3. **A child resetting the lockout with their own PIN, or a restart clearing it** — only an authorised PIN resets; state survives a new process. Tests in Task 6 (`lockoutSurvivesNewStoreInstance`) and Task 7 (`notAllowedPinDoesNotResetLockout`, `correctPinIsRefusedDuringLockout`).
4. **Removing, demoting or clearing the PIN of the last Admin** — must be blocked or nobody can reach Settings. Tests in Task 4 (`lastAdminCannotBeDemoted`, `...Removed`, `...PinCleared`).
5. **Theme preview left on, or no sunrise data** — the preview must end when the schedule flips (not return tomorrow), and missing/inverted sun times fall back to 07:00/19:00. Tests in Task 9 (`themePreviewEndsWhenScheduleFlips`) and Task 9 `ThemeScheduleTest` (`invertedSunTimesFallBack`).

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, .gitignore
build-logic/convention/src/main/kotlin/
  AndroidConfig.kt, AndroidApplicationConventionPlugin.kt, AndroidLibraryConventionPlugin.kt,
  AndroidComposeConventionPlugin.kt, HiltConventionPlugin.kt, RoomConventionPlugin.kt
core/ui/          tokens, theme, type, icons, basic components
core/plugin/      Capability, HomeCard, HomeCardPlacer, SunTimes, WallClock, ApplicationScope
core/household/   Person (+ role, PIN hash), Credential, Role, HomeLocation, HouseholdRepository (household.db), last-Admin rule
core/access/      permissions, PinHasher, PinManager, LockoutStore, AccessControl, PinPromptController, PIN pad UI
app/
  src/main/java/uk/co/siland/househub/
    HouseHubApp.kt, MainActivity.kt, Kiosk.kt, di/AppModule.kt
    shell/ShellUiState.kt, ShellViewModel.kt, ThemeSchedule.kt, MinuteTicker.kt
    shell/ui/HouseHubShell.kt, NavRail.kt, StatusBar.kt, HomeScreen.kt, HomeGrid.kt, SettingsPlaceholder.kt
  src/debug/java/uk/co/siland/househub/DebugSeed.kt
  src/release/java/uk/co/siland/househub/DebugSeed.kt
  src/test/java/uk/co/siland/househub/shell/  MainDispatcherRule.kt, Fakes.kt, tests
README.md
```

---

### Task 1: Project scaffold with convention plugins

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `build-logic/settings.gradle.kts`, `build-logic/convention/build.gradle.kts`, `build-logic/convention/src/main/kotlin/*.kt` (6 files)
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/uk/co/siland/househub/HouseHubApp.kt`, `MainActivity.kt`

**Interfaces:**
- Produces: plugin ids `househub.android.application`, `househub.android.library`, `househub.android.compose`, `househub.hilt`, `househub.room`. Library namespace is derived from the module path: `:core:ui` → `uk.co.siland.househub.core.ui`, `:provider:calendar-google` → `uk.co.siland.househub.provider.calendar_google`.

- [ ] **Step 1: Write `.gitignore`**

```gitignore
.gradle/
.kotlin/
build/
local.properties
.idea/
*.iml
captures/
.DS_Store
```

- [ ] **Step 2: Write the version catalog `gradle/libs.versions.toml`**

`core-ktx` and `activity-compose` are held at the last releases that compile against SDK 35 (newer ones require compileSdk 36).

```toml
[versions]
agp = "8.13.0"
kotlin = "2.2.20"
ksp = "2.2.20-2.0.3"
hilt = "2.57.1"
composeBom = "2025.09.00"
activityCompose = "1.10.1"
coreKtx = "1.16.0"
lifecycle = "2.9.4"
room = "2.8.0"
coroutines = "1.10.2"
junit = "4.13.2"
truth = "1.4.4"
turbine = "1.2.1"
robolectric = "4.16"
androidxTestCore = "1.7.0"
androidxTestExtJunit = "1.3.0"

[libraries]
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
compose-gradlePlugin = { group = "org.jetbrains.kotlin", name = "compose-compiler-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }

androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }

androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }

room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

junit = { group = "junit", name = "junit", version.ref = "junit" }
truth = { group = "com.google.truth", name = "truth", version.ref = "truth" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit-ktx", version.ref = "androidxTestExtJunit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 3: Write root `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "HouseHub"
include(":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.configuration-cache=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 4: Write `build-logic`**

`build-logic/settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "build-logic"
include(":convention")
```

`build-logic/convention/build.gradle.kts`:
```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "househub.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "househub.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "househub.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "househub.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("room") {
            id = "househub.room"
            implementationClass = "RoomConventionPlugin"
        }
    }
}
```

`build-logic/convention/src/main/kotlin/AndroidConfig.kt`:
```kotlin
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun Project.configureAndroid(android: CommonExtension<*, *, *, *, *, *>) {
    android.apply {
        compileSdk = 35
        defaultConfig.minSdk = 29
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
    dependencies {
        add("testImplementation", libs.lib("junit"))
        add("testImplementation", libs.lib("truth"))
        add("testImplementation", libs.lib("turbine"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("testImplementation", libs.lib("robolectric"))
        add("testImplementation", libs.lib("androidx-test-core"))
        add("testImplementation", libs.lib("androidx-test-ext-junit"))
    }
}
```

`build-logic/convention/src/main/kotlin/AndroidLibraryConventionPlugin.kt`:
```kotlin
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<LibraryExtension> {
            namespace = "uk.co.siland.househub." +
                path.removePrefix(":").replace(':', '.').replace('-', '_')
            configureAndroid(this)
        }
    }
}
```

`build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt`:
```kotlin
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")
        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig.targetSdk = 35
        }
    }
}
```

`build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt`:
```kotlin
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        val android = extensions.getByName("android") as CommonExtension<*, *, *, *, *, *>
        android.buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.lib("androidx-compose-bom"))
            add("implementation", bom)
            add("testImplementation", bom)
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-compose-foundation"))
            add("implementation", libs.lib("androidx-compose-material3"))
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
            add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
        }
    }
}
```

`build-logic/convention/src/main/kotlin/HiltConventionPlugin.kt`:
```kotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")
        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
        }
    }
}
```

`build-logic/convention/src/main/kotlin/RoomConventionPlugin.kt`:
```kotlin
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        extensions.configure<KspExtension> {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.lib("room-runtime"))
            add("implementation", libs.lib("room-ktx"))
            add("ksp", libs.lib("room-compiler"))
        }
    }
}
```

- [ ] **Step 5: Write `app/build.gradle.kts`**

```kotlin
plugins {
    id("househub.android.application")
    id("househub.android.compose")
    id("househub.hilt")
}

android {
    namespace = "uk.co.siland.househub"
    defaultConfig {
        applicationId = "uk.co.siland.househub"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures.buildConfig = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
```

- [ ] **Step 6: Write manifest, resources and minimal Hilt entry points**

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".HouseHubApp"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.HouseHub">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:screenOrientation="sensorLandscape"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">House Hub</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.HouseHub" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">#FF0E1011</item>
    </style>
</resources>
```

`app/src/main/java/uk/co/siland/househub/HouseHubApp.kt`:
```kotlin
package uk.co.siland.househub

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HouseHubApp : Application()
```

`app/src/main/java/uk/co/siland/househub/MainActivity.kt`:
```kotlin
package uk.co.siland.househub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("House Hub") }
    }
}
```

- [ ] **Step 7: Generate the Gradle wrapper using the cached Gradle 8.13**

Run (Git Bash, from repo root):
```bash
GRADLE_BIN=$(ls -d ~/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin | head -1)
"$GRADLE_BIN/gradle" wrapper --gradle-version 8.13 --distribution-type bin
```
Expected: `BUILD SUCCESSFUL`; `gradlew`, `gradlew.bat`, `gradle/wrapper/*` created.

- [ ] **Step 8: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If resolution fails, follow the version rule in Global Constraints. If `:app:hiltAggregateDepsDebug` fails with `NoSuchMethodError ... javapoet.ClassName.canonicalName()`, set `hilt = "2.57.2"` and rerun; if that also fails, stop and ask.

- [ ] **Step 9: Install on the tablet and eyeball**

Run: `adb devices`, then `./gradlew :app:installDebug` and `adb shell am start -n uk.co.siland.househub/.MainActivity`
Expected: app opens in landscape showing "House Hub". If no device is connected, say so and continue — Step 8 is the gate.

- [ ] **Step 10: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat build-logic/ app/
git commit -m "Scaffold multi-module Android project with convention plugins"
```

---

### Task 2: `:core:ui` — tokens, theme, fonts, icons, basic components

**Files:**
- Modify: `settings.gradle.kts` (add `include(":core:ui")`)
- Create: `core/ui/build.gradle.kts`
- Create: `core/ui/src/main/res/font/dm_sans.ttf`, `core/ui/src/main/res/font/material_symbols_rounded.ttf`, `core/ui/licenses/OFL-DMSans.txt`, `core/ui/licenses/Apache-MaterialSymbols.txt`
- Create: `core/ui/src/main/java/uk/co/siland/househub/core/ui/Colors.kt`, `Type.kt`, `Theme.kt`, `HhIcon.kt`, `Components.kt`
- Test: `core/ui/src/test/java/uk/co/siland/househub/core/ui/ThemeTest.kt`

**Interfaces:**
- Produces:
  - `data class HhColors(bg, surf, surf2, surf3, line, ink, mute, accent, accentInk, accentSoft: Color)`; `val DarkColors`, `val LightColors`
  - `@Composable fun HouseHubTheme(dark: Boolean, content: @Composable () -> Unit)`
  - `object HouseHub { val colors: HhColors @Composable get }`
  - `object HhType` with, per the hand-off scale: `clock` (104/600, −4 tracking, tabular, line height 0.9), `date` (21/400), `screenTitle` (34/700), `headerValue` (34/600 tabular), `dateNumber` (24/700), `sectionTitle` (22/700), `cardTitle` (19/700), `rowTitle` (17/600), `body` (16/400), `secondary` (14/400), `label` (13/600), `status` (13/500), `labelSmall` (12/700), `buttonLabel` (15/700), `pinDigit` (30/600 tabular)
  - `@Composable fun HhIcon(name: String, size: Dp = 24.dp, filled: Boolean = false, tint: Color = HouseHub.colors.ink, modifier: Modifier = Modifier)` — `name` is a Material Symbols ligature, e.g. `"home"`
  - `@Composable fun HhCard(modifier: Modifier = Modifier, radius: Dp = 24.dp, color: Color = HouseHub.colors.surf, padding: PaddingValues = PaddingValues(22.dp), content: @Composable ColumnScope.() -> Unit)`
  - `@Composable fun HhPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false)`

- [ ] **Step 1: Add module and build file**

Append to `settings.gradle.kts`: `include(":core:ui")`

`core/ui/build.gradle.kts`:
```kotlin
plugins {
    id("househub.android.library")
    id("househub.android.compose")
}
```

- [ ] **Step 2: Download the fonts from the official Google repositories**

```bash
mkdir -p core/ui/src/main/res/font core/ui/licenses
curl -fL -o core/ui/src/main/res/font/dm_sans.ttf "https://github.com/google/fonts/raw/main/ofl/dmsans/DMSans%5Bopsz,wght%5D.ttf"
curl -fL -o core/ui/src/main/res/font/material_symbols_rounded.ttf "https://github.com/google/material-design-icons/raw/master/variablefont/MaterialSymbolsRounded%5BFILL,GRAD,opsz,wght%5D.ttf"
curl -fL -o core/ui/licenses/OFL-DMSans.txt "https://github.com/google/fonts/raw/main/ofl/dmsans/OFL.txt"
curl -fL -o core/ui/licenses/Apache-MaterialSymbols.txt "https://github.com/google/material-design-icons/raw/master/LICENSE"
ls -la core/ui/src/main/res/font
```
Expected: `dm_sans.ttf` ≈ 240 KB, `material_symbols_rounded.ttf` ≈ 15 MB. If a URL 404s, find the current path in that repository's `ofl/dmsans/` or `variablefont/` directory — no other source.

- [ ] **Step 3: Write the failing test**

`core/ui/src/test/java/uk/co/siland/househub/core/ui/ThemeTest.kt`:
```kotlin
package uk.co.siland.househub.core.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun darkThemeProvidesDarkTokens() {
        var seen: HhColors? = null
        compose.setContent { HouseHubTheme(dark = true) { seen = HouseHub.colors } }
        compose.waitForIdle()
        assertThat(seen).isEqualTo(DarkColors)
    }

    @Test
    fun lightThemeProvidesLightTokens() {
        var seen: HhColors? = null
        compose.setContent { HouseHubTheme(dark = false) { seen = HouseHub.colors } }
        compose.waitForIdle()
        assertThat(seen).isEqualTo(LightColors)
    }

    @Test
    fun bundledFontsAreRealFiles() {
        val res = ApplicationProvider.getApplicationContext<android.content.Context>().resources
        val dmSans = res.openRawResource(R.font.dm_sans).use { it.readBytes().size }
        val symbols = res.openRawResource(R.font.material_symbols_rounded).use { it.readBytes().size }
        assertThat(dmSans).isGreaterThan(100_000)
        assertThat(symbols).isGreaterThan(1_000_000)
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :core:ui:testDebugUnitTest`
Expected: FAIL — compilation errors (`HouseHubTheme`, `HhColors` unresolved).

- [ ] **Step 5: Write `Colors.kt`**

```kotlin
package uk.co.siland.househub.core.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class HhColors(
    val bg: Color,
    val surf: Color,
    val surf2: Color,
    val surf3: Color,
    val line: Color,
    val ink: Color,
    val mute: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
)

val DarkColors = HhColors(
    bg = Color(0xFF0E1011),
    surf = Color(0xFF1A1D1E),
    surf2 = Color(0xFF24282A),
    surf3 = Color(0xFF303537),
    line = Color(0xFF1F2324),
    ink = Color(0xFFF1F4F2),
    mute = Color(0xFF9AA3A0),
    accent = Color(0xFF4CB387),
    accentInk = Color(0xFF08170F),
    accentSoft = Color(0xFF173427),
)

val LightColors = HhColors(
    bg = Color(0xFFEDF0EE),
    surf = Color(0xFFFFFFFF),
    surf2 = Color(0xFFF1F4F2),
    surf3 = Color(0xFFDDE3E0),
    line = Color(0xFFDAE0DD),
    ink = Color(0xFF111514),
    mute = Color(0xFF5A6461),
    accent = Color(0xFF2E8A64),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFD3EDE1),
)
```

- [ ] **Step 6: Write `Type.kt`**

```kotlin
package uk.co.siland.househub.core.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

// One variable font file; Font(resId, weight) sets the wght axis from the weight.
val DmSans = FontFamily(
    Font(R.font.dm_sans, FontWeight.W400),
    Font(R.font.dm_sans, FontWeight.W500),
    Font(R.font.dm_sans, FontWeight.W600),
    Font(R.font.dm_sans, FontWeight.W700),
)

private const val TABULAR = "tnum"

private fun style(size: Int, weight: FontWeight, tabular: Boolean = false) = TextStyle(
    fontFamily = DmSans,
    fontWeight = weight,
    fontSize = size.sp,
    fontFeatureSettings = if (tabular) TABULAR else null,
)

object HhType {
    val clock = style(104, FontWeight.W600, tabular = true).copy(
        letterSpacing = (-4).sp,
        lineHeight = 93.6.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    val date = style(21, FontWeight.W400)
    val screenTitle = style(34, FontWeight.W700)
    val headerValue = style(34, FontWeight.W600, tabular = true)
    val dateNumber = style(24, FontWeight.W700)
    val sectionTitle = style(22, FontWeight.W700)
    val cardTitle = style(19, FontWeight.W700)
    val rowTitle = style(17, FontWeight.W600)
    val body = style(16, FontWeight.W400)
    val secondary = style(14, FontWeight.W400)
    val label = style(13, FontWeight.W600)
    val status = style(13, FontWeight.W500)
    val labelSmall = style(12, FontWeight.W700)
    val buttonLabel = style(15, FontWeight.W700)
    val pinDigit = style(30, FontWeight.W600, tabular = true)
}
```

- [ ] **Step 7: Write `Theme.kt`**

```kotlin
package uk.co.siland.househub.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LocalHhColors = staticCompositionLocalOf { DarkColors }

object HouseHub {
    val colors: HhColors
        @Composable get() = LocalHhColors.current
}

@Composable
fun HouseHubTheme(dark: Boolean, content: @Composable () -> Unit) {
    val target = if (dark) DarkColors else LightColors
    val colors = HhColors(
        bg = animated(target.bg),
        surf = animated(target.surf),
        surf2 = animated(target.surf2),
        surf3 = animated(target.surf3),
        line = animated(target.line),
        ink = animated(target.ink),
        mute = animated(target.mute),
        accent = animated(target.accent),
        accentInk = animated(target.accentInk),
        accentSoft = animated(target.accentSoft),
    )
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.accent,
        onPrimary = colors.accentInk,
        background = colors.bg,
        onBackground = colors.ink,
        surface = colors.surf,
        onSurface = colors.ink,
        onSurfaceVariant = colors.mute,
    )
    val typography = Typography().let { t ->
        t.copy(
            bodyLarge = t.bodyLarge.copy(fontFamily = DmSans),
            bodyMedium = t.bodyMedium.copy(fontFamily = DmSans),
            labelLarge = t.labelLarge.copy(fontFamily = DmSans),
            titleMedium = t.titleMedium.copy(fontFamily = DmSans),
        )
    }
    CompositionLocalProvider(LocalHhColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}

@Composable
private fun animated(target: Color): Color =
    animateColorAsState(target, animationSpec = tween(durationMillis = 400), label = "theme").value
```

- [ ] **Step 8: Write `HhIcon.kt`**

The `Font(..., variationSettings = ...)` overload is `@ExperimentalTextApi` (experimental, not deprecated); opt in locally.

```kotlin
package uk.co.siland.househub.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalTextApi::class)
private fun symbols(fill: Float) = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        FontWeight.W400,
        variationSettings = FontVariation.Settings(
            FontWeight.W400,
            FontStyle.Normal,
            FontVariation.Setting("FILL", fill),
        ),
    ),
)

private val SymbolsOutline = symbols(0f)
private val SymbolsFilled = symbols(1f)

/** Renders a Material Symbols Rounded glyph by ligature name, e.g. "lightbulb". */
@Composable
fun HhIcon(
    name: String,
    size: Dp = 24.dp,
    filled: Boolean = false,
    tint: Color = HouseHub.colors.ink,
    modifier: Modifier = Modifier,
) {
    val sp = with(LocalDensity.current) { size.toSp() }
    Text(
        text = name,
        style = TextStyle(
            fontFamily = if (filled) SymbolsFilled else SymbolsOutline,
            fontSize = sp,
            lineHeight = sp,
            color = tint,
        ),
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics { },
    )
}
```

- [ ] **Step 9: Write `Components.kt`**

```kotlin
package uk.co.siland.househub.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HhCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    color: Color = HouseHub.colors.surf,
    padding: PaddingValues = PaddingValues(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(padding),
        content = content,
    )
}

@Composable
fun HhPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val c = HouseHub.colors
    Text(
        text = text,
        style = HhType.buttonLabel,
        color = if (primary) c.accentInk else c.ink,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary) c.accent else c.surf2)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
    )
}
```

- [ ] **Step 10: Run tests to verify they pass**

Run: `./gradlew :core:ui:testDebugUnitTest`
Expected: PASS (3 tests).

- [ ] **Step 11: Commit**

```bash
git add settings.gradle.kts core/ui
git commit -m "Add core:ui design tokens, theme, DM Sans and Material Symbols"
```

---

### Task 3: `:core:plugin` — capability contract and Home card placement

**Files:**
- Modify: `settings.gradle.kts` (add `include(":core:plugin")`)
- Create: `core/plugin/build.gradle.kts`
- Create: `core/plugin/src/main/java/uk/co/siland/househub/core/plugin/Capability.kt`, `HomeCard.kt`, `HomeCardPlacer.kt`, `SunTimes.kt`, `Runtime.kt`
- Test: `core/plugin/src/test/java/uk/co/siland/househub/core/plugin/HomeCardPlacerTest.kt`

**Interfaces:**
- Produces:
  - `interface Capability : HomeCardContributor { val id: String; val label: String; val icon: String; val order: Int; val hasTab: Flow<Boolean>; @Composable fun TabContent() }`
  - `interface HomeCardContributor { fun cards(): Flow<List<HomeCard>> }`
  - `enum class HomeCardSize { TALL, WIDE, REGULAR }`
  - `class HomeCard(id: String, size: HomeCardSize, priority: Int, content: @Composable () -> Unit)`
  - `data class HomePlacement(card: HomeCard, col: Int, row: Int, colSpan: Int, rowSpan: Int)`
  - `object HomeCardPlacer { const val COLUMNS = 3; const val ROWS = 2; fun place(cards: List<HomeCard>): List<HomePlacement> }`
  - `data class SunTimes(sunrise: LocalTime, sunset: LocalTime)`
  - `fun interface WallClock { fun nowMillis(): Long }`
  - `@Qualifier annotation class ApplicationScope`

- [ ] **Step 1: Add module and build file**

Append to `settings.gradle.kts`: `include(":core:plugin")`

`core/plugin/build.gradle.kts`:
```kotlin
plugins {
    id("househub.android.library")
    id("househub.android.compose")
    id("househub.hilt")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
```

- [ ] **Step 2: Write the contracts (placer stubbed)**

`HomeCard.kt`:
```kotlin
package uk.co.siland.househub.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

enum class HomeCardSize { TALL, WIDE, REGULAR }

/** A card a capability contributes to Home. Higher [priority] is placed first; cards that don't fit are dropped. */
class HomeCard(
    val id: String,
    val size: HomeCardSize,
    val priority: Int,
    val content: @Composable () -> Unit,
)

interface HomeCardContributor {
    fun cards(): Flow<List<HomeCard>>
}
```

`Capability.kt`:
```kotlin
package uk.co.siland.househub.core.plugin

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

interface Capability : HomeCardContributor {
    val id: String
    val label: String
    /** Material Symbols ligature name for the nav rail. */
    val icon: String
    /** Rail position: Calendar 10, Lights 20, Music 30, Climate 40, Security 50. */
    val order: Int
    /** True when at least one connection provides this capability and it has a tab. */
    val hasTab: Flow<Boolean>

    @Composable
    fun TabContent()
}
```

`SunTimes.kt`:
```kotlin
package uk.co.siland.househub.core.plugin

import java.time.LocalTime

data class SunTimes(val sunrise: LocalTime, val sunset: LocalTime)
```

`Runtime.kt`:
```kotlin
package uk.co.siland.househub.core.plugin

import javax.inject.Qualifier

fun interface WallClock {
    fun nowMillis(): Long
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
```

`HomeCardPlacer.kt`:
```kotlin
package uk.co.siland.househub.core.plugin

data class HomePlacement(
    val card: HomeCard,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
)

object HomeCardPlacer {
    const val COLUMNS = 3
    const val ROWS = 2

    fun place(cards: List<HomeCard>): List<HomePlacement> = TODO()
}
```

- [ ] **Step 3: Write the failing tests**

`HomeCardPlacerTest.kt`:
```kotlin
package uk.co.siland.househub.core.plugin

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.househub.core.plugin.HomeCardSize.REGULAR
import uk.co.siland.househub.core.plugin.HomeCardSize.TALL
import uk.co.siland.househub.core.plugin.HomeCardSize.WIDE

class HomeCardPlacerTest {
    private fun card(id: String, size: HomeCardSize, priority: Int) = HomeCard(id, size, priority) {}

    private fun List<HomePlacement>.layout() =
        associate { it.card.id to listOf(it.col, it.row, it.colSpan, it.rowSpan) }

    @Test
    fun noCardsGivesEmptyLayout() {
        assertThat(HomeCardPlacer.place(emptyList())).isEmpty()
    }

    @Test
    fun v1LayoutTodayTallComingUpAndForecastWide() {
        val result = HomeCardPlacer.place(
            listOf(card("forecast", WIDE, 10), card("today", TALL, 100), card("comingUp", WIDE, 50)),
        )
        assertThat(result.layout()).containsExactly(
            "today", listOf(0, 0, 1, 2),
            "comingUp", listOf(1, 0, 2, 1),
            "forecast", listOf(1, 1, 2, 1),
        )
    }

    @Test
    fun higherPriorityWideCardPushesOthersDownAndLowestIsDropped() {
        val result = HomeCardPlacer.place(
            listOf(
                card("today", TALL, 100),
                card("comingUp", WIDE, 50),
                card("forecast", WIDE, 10),
                card("scenes", WIDE, 200),
            ),
        )
        assertThat(result.layout()).containsExactly(
            "today", listOf(0, 0, 1, 2),
            "scenes", listOf(1, 0, 2, 1),
            "comingUp", listOf(1, 1, 2, 1),
        )
    }

    @Test
    fun secondTallCardIsDropped() {
        val result = HomeCardPlacer.place(listOf(card("a", TALL, 20), card("b", TALL, 10)))
        assertThat(result.map { it.card.id }).containsExactly("a")
    }

    @Test
    fun regularCardsFillRightColumnsBeforeLeft() {
        val result = HomeCardPlacer.place(listOf(card("x", REGULAR, 10)))
        assertThat(result.layout()["x"]).isEqualTo(listOf(1, 0, 1, 1))
    }

    @Test
    fun regularCardTakesLeftColumnWhenRightIsFull() {
        val result = HomeCardPlacer.place(listOf(card("a", WIDE, 30), card("b", WIDE, 20), card("c", REGULAR, 10)))
        assertThat(result.layout()["c"]).isEqualTo(listOf(0, 0, 1, 1))
    }

    @Test
    fun equalPriorityIsOrderedById() {
        val result = HomeCardPlacer.place(listOf(card("b", WIDE, 10), card("a", WIDE, 10)))
        assertThat(result.layout()["a"]).isEqualTo(listOf(1, 0, 2, 1))
        assertThat(result.layout()["b"]).isEqualTo(listOf(1, 1, 2, 1))
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :core:plugin:testDebugUnitTest`
Expected: FAIL — `NotImplementedError`.

- [ ] **Step 5: Implement `HomeCardPlacer`**

Replace the object:
```kotlin
object HomeCardPlacer {
    const val COLUMNS = 3
    const val ROWS = 2

    // Right-hand cells first so REGULAR cards sit beside a TALL card before taking its column.
    private val regularOrder = listOf(0 to 1, 0 to 2, 1 to 1, 1 to 2, 0 to 0, 1 to 0)

    fun place(cards: List<HomeCard>): List<HomePlacement> {
        val used = Array(ROWS) { BooleanArray(COLUMNS) }
        val placed = mutableListOf<HomePlacement>()
        val ordered = cards.sortedWith(compareByDescending<HomeCard> { it.priority }.thenBy { it.id })
        for (card in ordered) {
            val placement = fit(card, used) ?: continue
            for (r in placement.row until placement.row + placement.rowSpan) {
                for (c in placement.col until placement.col + placement.colSpan) used[r][c] = true
            }
            placed += placement
        }
        return placed
    }

    private fun fit(card: HomeCard, used: Array<BooleanArray>): HomePlacement? =
        when (card.size) {
            HomeCardSize.TALL ->
                if (!used[0][0] && !used[1][0]) HomePlacement(card, 0, 0, 1, 2) else null
            HomeCardSize.WIDE ->
                (0 until ROWS).firstOrNull { r -> !used[r][1] && !used[r][2] }
                    ?.let { r -> HomePlacement(card, 1, r, 2, 1) }
            HomeCardSize.REGULAR ->
                regularOrder.firstOrNull { (r, c) -> !used[r][c] }
                    ?.let { (r, c) -> HomePlacement(card, c, r, 1, 1) }
        }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :core:plugin:testDebugUnitTest`
Expected: PASS (7 tests).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts core/plugin
git commit -m "Add core:plugin capability contract and Home card placement"
```

---

### Task 4: `:core:household` — people, roles, PIN storage, location

People and their role/PIN hash live on one row, so removing a person can never leave an orphaned credential, and the last-Admin rule is enforced in the same transaction as the change.

**Files:**
- Modify: `settings.gradle.kts` (add `include(":core:household")`)
- Create: `core/household/build.gradle.kts`
- Create: `core/household/src/main/java/uk/co/siland/househub/core/household/Model.kt`, `db/HouseholdDatabase.kt`, `HouseholdRepository.kt`, `di/HouseholdModule.kt`
- Test: `core/household/src/test/java/uk/co/siland/househub/core/household/HouseholdRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `@JvmInline value class PersonId(val value: String)` with `PersonId.FAMILY`, `PersonId.new()`
  - `data class Person(id: PersonId, name: String, color: Long)` with `isFamily`, and `Person.Family`
  - `const val FAMILY_COLOR: Long = 0xFFE0A85B`
  - `enum class Role { ADMIN, ADULT, CHILD }`
  - `data class Credential(personId: PersonId, role: Role, pinHash: String?, salt: String?)` with `hasPin`, `isActiveAdmin` — hash/salt are Base64
  - `class LastAdminException : Exception`
  - `data class HomeLocation(name: String, latitude: Double, longitude: Double, timeZoneId: String)`
  - `@Singleton class HouseholdRepository @Inject constructor(db: HouseholdDatabase)` with: `val people: Flow<List<Person>>`, `val peopleWithFamily: Flow<List<Person>>`, `val location: Flow<HomeLocation?>`, `suspend fun person(id: PersonId): Person?`, `suspend fun addPerson(name: String, color: Long, role: Role): Person`, `suspend fun updatePerson(person: Person)`, `suspend fun removePerson(id: PersonId)`, `suspend fun credential(id: PersonId): Credential?`, `suspend fun credentials(): List<Credential>`, `suspend fun setRole(id: PersonId, role: Role)`, `suspend fun setPinHash(id: PersonId, hash: String, salt: String)`, `suspend fun clearPin(id: PersonId)`, `suspend fun setLocation(location: HomeLocation)`

- [ ] **Step 1: Add module**

Append to `settings.gradle.kts`: `include(":core:household")`

`core/household/build.gradle.kts`:
```kotlin
plugins {
    id("househub.android.library")
    id("househub.hilt")
    id("househub.room")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // HouseholdDatabase is part of this module's public surface (DI and in-memory test databases).
    api(libs.room.runtime)
}
```

- [ ] **Step 2: Write the model**

`Model.kt`:
```kotlin
package uk.co.siland.househub.core.household

import java.util.UUID

@JvmInline
value class PersonId(val value: String) {
    companion object {
        val FAMILY = PersonId("family")
        fun new() = PersonId(UUID.randomUUID().toString())
    }
}

const val FAMILY_COLOR: Long = 0xFFE0A85B

data class Person(val id: PersonId, val name: String, val color: Long) {
    val isFamily: Boolean get() = id == PersonId.FAMILY

    companion object {
        val Family = Person(PersonId.FAMILY, "Family", FAMILY_COLOR)
    }
}

enum class Role { ADMIN, ADULT, CHILD }

/** Role and PIN material for one person. [pinHash] and [salt] are Base64. */
data class Credential(
    val personId: PersonId,
    val role: Role,
    val pinHash: String?,
    val salt: String?,
) {
    val hasPin: Boolean get() = pinHash != null
    val isActiveAdmin: Boolean get() = role == Role.ADMIN && pinHash != null
}

class LastAdminException : Exception("At least one Admin with a PIN must remain")

data class HomeLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
)
```

- [ ] **Step 3: Write the Room database**

`db/HouseholdDatabase.kt`:
```kotlin
package uk.co.siland.househub.core.household.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import uk.co.siland.househub.core.household.Role

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val sortOrder: Int,
    val role: Role,
    val pinHash: String?,
    val salt: String?,
)

@Entity(tableName = "location")
data class LocationEntity(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
)

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM person ORDER BY sortOrder")
    fun people(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person ORDER BY sortOrder")
    suspend fun all(): List<PersonEntity>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun person(id: String): PersonEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM person")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsertPerson(person: PersonEntity)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun deletePerson(id: String)

    @Query("SELECT * FROM location WHERE id = 0")
    fun location(): Flow<LocationEntity?>

    @Upsert
    suspend fun upsertLocation(location: LocationEntity)
}

@Database(entities = [PersonEntity::class, LocationEntity::class], version = 1)
abstract class HouseholdDatabase : RoomDatabase() {
    abstract fun householdDao(): HouseholdDao
}
```

- [ ] **Step 4: Write the repository stub and Hilt module**

`HouseholdRepository.kt`:
```kotlin
package uk.co.siland.househub.core.household

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import uk.co.siland.househub.core.household.db.HouseholdDatabase

@Singleton
class HouseholdRepository @Inject constructor(private val db: HouseholdDatabase) {
    val people: Flow<List<Person>> get() = TODO()
    val peopleWithFamily: Flow<List<Person>> get() = TODO()
    val location: Flow<HomeLocation?> get() = TODO()
    suspend fun person(id: PersonId): Person? = TODO()
    suspend fun addPerson(name: String, color: Long, role: Role): Person = TODO()
    suspend fun updatePerson(person: Person): Unit = TODO()
    suspend fun removePerson(id: PersonId): Unit = TODO()
    suspend fun credential(id: PersonId): Credential? = TODO()
    suspend fun credentials(): List<Credential> = TODO()
    suspend fun setRole(id: PersonId, role: Role): Unit = TODO()
    suspend fun setPinHash(id: PersonId, hash: String, salt: String): Unit = TODO()
    suspend fun clearPin(id: PersonId): Unit = TODO()
    suspend fun setLocation(location: HomeLocation): Unit = TODO()
}
```

`di/HouseholdModule.kt`:
```kotlin
package uk.co.siland.househub.core.household.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import uk.co.siland.househub.core.household.db.HouseholdDatabase

@Module
@InstallIn(SingletonComponent::class)
object HouseholdModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HouseholdDatabase =
        Room.databaseBuilder(context, HouseholdDatabase::class.java, "household.db").build()
}
```

- [ ] **Step 5: Write the failing tests**

`HouseholdRepositoryTest.kt`:
```kotlin
package uk.co.siland.househub.core.household

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.househub.core.household.db.HouseholdDatabase

@RunWith(AndroidJUnit4::class)
class HouseholdRepositoryTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var repo: HouseholdRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HouseholdRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun admin(name: String): Person =
        repo.addPerson(name, 0xFF4CB387, Role.ADMIN).also { repo.setPinHash(it.id, "hash-$name", "salt") }

    @Test
    fun addedPeopleAppearInInsertionOrder() = runTest {
        repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        assertThat(repo.people.first().map { it.name }).containsExactly("Alex", "Sam").inOrder()
    }

    @Test
    fun namesAreTrimmedAndBlankRejected() = runTest {
        assertThat(repo.addPerson("  Mia ", 0xFFE07BA8, Role.CHILD).name).isEqualTo("Mia")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.addPerson("   ", 0xFF000000, Role.CHILD) }
        }
    }

    @Test
    fun peopleWithFamilyPutsFamilyFirst() = runTest {
        repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        assertThat(repo.peopleWithFamily.first().map { it.name }).containsExactly("Family", "Alex").inOrder()
    }

    @Test
    fun personLooksUpFamilyAndRealPeople() = runTest {
        val alex = repo.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        assertThat(repo.person(PersonId.FAMILY)).isEqualTo(Person.Family)
        assertThat(repo.person(alex.id)).isEqualTo(alex)
        assertThat(repo.person(PersonId("missing"))).isNull()
    }

    @Test
    fun newPersonHasRoleAndNoPin() = runTest {
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        assertThat(repo.credential(mia.id)).isEqualTo(Credential(mia.id, Role.CHILD, null, null))
    }

    @Test
    fun updateChangesNameAndColourButKeepsCredential() = runTest {
        val alex = admin("Alex")
        repo.updatePerson(alex.copy(name = "Alexandra", color = 0xFF000000))
        assertThat(repo.person(alex.id)).isEqualTo(Person(alex.id, "Alexandra", 0xFF000000))
        assertThat(repo.credential(alex.id)?.pinHash).isEqualTo("hash-Alex")
    }

    @Test
    fun familyCannotBeUpdatedOrRemoved() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.updatePerson(Person.Family.copy(name = "Us")) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.removePerson(PersonId.FAMILY) }
        }
    }

    @Test
    fun removingAPersonRemovesTheirCredential() = runTest {
        admin("Alex")
        val mia = repo.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        repo.setPinHash(mia.id, "h", "s")
        repo.removePerson(mia.id)
        assertThat(repo.credentials().map { it.personId }).doesNotContain(mia.id)
    }

    @Test
    fun lastAdminCannotBeDemoted() = runTest {
        val alex = admin("Alex")
        assertThrows(LastAdminException::class.java) { runBlocking { repo.setRole(alex.id, Role.ADULT) } }
    }

    @Test
    fun lastAdminCannotBeRemoved() = runTest {
        val alex = admin("Alex")
        assertThrows(LastAdminException::class.java) { runBlocking { repo.removePerson(alex.id) } }
    }

    @Test
    fun lastAdminCannotHavePinCleared() = runTest {
        val alex = admin("Alex")
        assertThrows(LastAdminException::class.java) { runBlocking { repo.clearPin(alex.id) } }
    }

    @Test
    fun oneOfTwoAdminsCanBeDemotedOrRemoved() = runTest {
        admin("Alex")
        val sam = admin("Sam")
        repo.setRole(sam.id, Role.ADULT)
        assertThat(repo.credential(sam.id)?.role).isEqualTo(Role.ADULT)
        repo.removePerson(sam.id)
        assertThat(repo.person(sam.id)).isNull()
    }

    @Test
    fun adminWithoutPinDoesNotCountAsLastAdmin() = runTest {
        val alex = admin("Alex")
        repo.addPerson("Sam", 0xFF5B9BE0, Role.ADMIN)
        assertThrows(LastAdminException::class.java) { runBlocking { repo.removePerson(alex.id) } }
    }

    @Test
    fun pinHashNeedsAnExistingPerson() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo.setPinHash(PersonId("nobody"), "h", "s") }
        }
    }

    @Test
    fun locationRoundTrips() = runTest {
        assertThat(repo.location.first()).isNull()
        val home = HomeLocation("Balcombe", 51.06, -0.13, "Europe/London")
        repo.setLocation(home)
        assertThat(repo.location.first()).isEqualTo(home)
    }
}
```

- [ ] **Step 6: Run tests to verify they fail**

Run: `./gradlew :core:household:testDebugUnitTest`
Expected: FAIL with `NotImplementedError`.

- [ ] **Step 7: Implement `HouseholdRepository`**

```kotlin
package uk.co.siland.househub.core.household

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.househub.core.household.db.HouseholdDatabase
import uk.co.siland.househub.core.household.db.LocationEntity
import uk.co.siland.househub.core.household.db.PersonEntity

@Singleton
class HouseholdRepository @Inject constructor(private val db: HouseholdDatabase) {
    private val dao = db.householdDao()

    /** Real people in display order; never includes Family. */
    val people: Flow<List<Person>> = dao.people().map { rows -> rows.map { it.toPerson() } }

    val peopleWithFamily: Flow<List<Person>> = people.map { listOf(Person.Family) + it }

    val location: Flow<HomeLocation?> =
        dao.location().map { it?.let { l -> HomeLocation(l.name, l.latitude, l.longitude, l.timeZoneId) } }

    suspend fun person(id: PersonId): Person? =
        if (id == PersonId.FAMILY) Person.Family else dao.person(id.value)?.toPerson()

    suspend fun addPerson(name: String, color: Long, role: Role): Person {
        val clean = cleanName(name)
        val id = PersonId.new()
        dao.upsertPerson(PersonEntity(id.value, clean, color, dao.maxSortOrder() + 1, role, null, null))
        return Person(id, clean, color)
    }

    suspend fun updatePerson(person: Person) {
        require(!person.isFamily) { "Family cannot be edited" }
        val existing = requireNotNull(dao.person(person.id.value)) { "Unknown person ${person.id.value}" }
        dao.upsertPerson(existing.copy(name = cleanName(person.name), color = person.color))
    }

    suspend fun removePerson(id: PersonId) {
        require(id != PersonId.FAMILY) { "Family cannot be removed" }
        db.withTransaction {
            val current = dao.person(id.value) ?: return@withTransaction
            guardLastAdmin(current, next = null)
            dao.deletePerson(id.value)
        }
    }

    suspend fun credential(id: PersonId): Credential? = dao.person(id.value)?.toCredential()

    suspend fun credentials(): List<Credential> = dao.all().map { it.toCredential() }

    suspend fun setRole(id: PersonId, role: Role) = change(id) { it.copy(role = role) }

    suspend fun setPinHash(id: PersonId, hash: String, salt: String) = change(id) { it.copy(pinHash = hash, salt = salt) }

    suspend fun clearPin(id: PersonId) = change(id) { it.copy(pinHash = null, salt = null) }

    suspend fun setLocation(location: HomeLocation) {
        dao.upsertLocation(
            LocationEntity(
                name = location.name,
                latitude = location.latitude,
                longitude = location.longitude,
                timeZoneId = location.timeZoneId,
            ),
        )
    }

    private suspend fun change(id: PersonId, edit: (PersonEntity) -> PersonEntity) = db.withTransaction {
        val current = requireNotNull(dao.person(id.value)) { "Unknown person ${id.value}" }
        val next = edit(current)
        guardLastAdmin(current, next)
        dao.upsertPerson(next)
    }

    private suspend fun guardLastAdmin(current: PersonEntity, next: PersonEntity?) {
        if (!current.isActiveAdmin() || next?.isActiveAdmin() == true) return
        if (dao.all().count { it.isActiveAdmin() } <= 1) throw LastAdminException()
    }

    private fun cleanName(name: String): String =
        name.trim().also { require(it.isNotEmpty()) { "Name must not be blank" } }

    private fun PersonEntity.isActiveAdmin() = role == Role.ADMIN && pinHash != null
    private fun PersonEntity.toPerson() = Person(PersonId(id), name, color)
    private fun PersonEntity.toCredential() = Credential(PersonId(id), role, pinHash, salt)
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :core:household:testDebugUnitTest`
Expected: PASS (15 tests). Commit the generated `core/household/schemas/` directory (it is the migration baseline).

- [ ] **Step 9: Commit**

```bash
git add settings.gradle.kts core/household
git commit -m "Add core:household with people, roles, PIN storage and last-admin rule"
```

---

### Task 5: `:core:access` domain — permissions and PIN hashing

**Files:**
- Modify: `settings.gradle.kts` (add `include(":core:access")`)
- Create: `core/access/build.gradle.kts`
- Create: `core/access/src/main/java/uk/co/siland/househub/core/access/Permissions.kt`, `PermissionRegistry.kt`, `PinHasher.kt`
- Test: `core/access/src/test/java/uk/co/siland/househub/core/access/PermissionRegistryTest.kt`, `PinHasherTest.kt`

**Interfaces:**
- Consumes: `Role` (Task 4).
- Produces:
  - `data class PermissionDef(id: String, label: String, defaultRoles: Set<Role>, freshPin: Boolean = false)` — `freshPin` permissions always show the PIN pad, even mid-session
  - `interface PermissionSource { val permissions: List<PermissionDef> }`
  - `object CorePermissions { SETTINGS_MANAGE = "settings.manage"; PEOPLE_MANAGE = "people.manage"; KIOSK_EXIT = "kiosk.exit" }`
  - `class CorePermissionSource @Inject constructor() : PermissionSource`
  - `@Singleton class PermissionRegistry @Inject constructor(sources: Set<PermissionSource>) { fun require(id: String): PermissionDef; fun isGranted(role: Role, id: String): Boolean; fun all(): Collection<PermissionDef> }`
  - `class PinHasher @Inject constructor() { fun isWellFormed(pin: String): Boolean; fun validate(pin: String); fun newSalt(): String; fun hash(pin: String, salt: String): String; fun matches(pin: String, salt: String, hash: String): Boolean }` with `PIN_LENGTH = 4`

- [ ] **Step 1: Add module**

Append to `settings.gradle.kts`: `include(":core:access")`

`core/access/build.gradle.kts`:
```kotlin
plugins {
    id("househub.android.library")
    id("househub.android.compose")
    id("househub.hilt")
}

dependencies {
    implementation(project(":core:ui"))
    api(project(":core:plugin"))
    api(project(":core:household"))
}
```

- [ ] **Step 2: Write the types (logic stubbed)**

`Permissions.kt`:
```kotlin
package uk.co.siland.househub.core.access

import javax.inject.Inject
import uk.co.siland.househub.core.household.Role

data class PermissionDef(
    val id: String,
    val label: String,
    val defaultRoles: Set<Role>,
    val freshPin: Boolean = false,
)

/** Each capability contributes one of these via `@IntoSet`. */
interface PermissionSource {
    val permissions: List<PermissionDef>
}

object CorePermissions {
    const val SETTINGS_MANAGE = "settings.manage"
    const val PEOPLE_MANAGE = "people.manage"
    const val KIOSK_EXIT = "kiosk.exit"
}

class CorePermissionSource @Inject constructor() : PermissionSource {
    override val permissions = listOf(
        PermissionDef(CorePermissions.SETTINGS_MANAGE, "Change settings", setOf(Role.ADMIN)),
        PermissionDef(CorePermissions.PEOPLE_MANAGE, "Manage people", setOf(Role.ADMIN), freshPin = true),
        PermissionDef(CorePermissions.KIOSK_EXIT, "Exit kiosk mode", setOf(Role.ADMIN), freshPin = true),
    )
}
```

`PermissionRegistry.kt`:
```kotlin
package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.househub.core.household.Role

@Singleton
class PermissionRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards PermissionSource>,
) {
    fun require(id: String): PermissionDef = TODO()
    fun isGranted(role: Role, id: String): Boolean = TODO()
    fun all(): Collection<PermissionDef> = TODO()
}
```

`PinHasher.kt`:
```kotlin
package uk.co.siland.househub.core.access

import javax.inject.Inject

class PinHasher @Inject constructor() {
    fun isWellFormed(pin: String): Boolean = TODO()
    fun validate(pin: String): Unit = TODO()
    fun newSalt(): String = TODO()
    fun hash(pin: String, salt: String): String = TODO()
    fun matches(pin: String, salt: String, hash: String): Boolean = TODO()

    companion object {
        const val PIN_LENGTH = 4
    }
}
```

- [ ] **Step 3: Write the failing tests**

`PermissionRegistryTest.kt`:
```kotlin
package uk.co.siland.househub.core.access

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.co.siland.househub.core.household.Role

class PermissionRegistryTest {
    private val calendar = object : PermissionSource {
        override val permissions = listOf(
            PermissionDef("calendar.event.create", "Add events", setOf(Role.ADMIN, Role.ADULT)),
            PermissionDef("calendar.event.create.self", "Add your own events", Role.entries.toSet()),
        )
    }
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), calendar))

    @Test
    fun corePermissionsAreAdminOnly() {
        for (id in listOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.PEOPLE_MANAGE, CorePermissions.KIOSK_EXIT)) {
            assertThat(registry.isGranted(Role.ADMIN, id)).isTrue()
            assertThat(registry.isGranted(Role.ADULT, id)).isFalse()
            assertThat(registry.isGranted(Role.CHILD, id)).isFalse()
        }
    }

    @Test
    fun kioskExitAndPeopleNeedAFreshPin() {
        assertThat(registry.require(CorePermissions.KIOSK_EXIT).freshPin).isTrue()
        assertThat(registry.require(CorePermissions.PEOPLE_MANAGE).freshPin).isTrue()
        assertThat(registry.require(CorePermissions.SETTINGS_MANAGE).freshPin).isFalse()
    }

    @Test
    fun capabilityPermissionsUseTheirDefaultRoles() {
        assertThat(registry.isGranted(Role.CHILD, "calendar.event.create")).isFalse()
        assertThat(registry.isGranted(Role.CHILD, "calendar.event.create.self")).isTrue()
    }

    @Test
    fun unknownPermissionFailsLoudly() {
        assertThrows(IllegalArgumentException::class.java) { registry.isGranted(Role.ADMIN, "nope") }
    }

    @Test
    fun duplicatePermissionIdsAreRejected() {
        val dup = object : PermissionSource {
            override val permissions = listOf(PermissionDef(CorePermissions.KIOSK_EXIT, "x", emptySet()))
        }
        assertThrows(IllegalArgumentException::class.java) { PermissionRegistry(setOf(CorePermissionSource(), dup)) }
    }

    @Test
    fun allListsEveryPermission() {
        assertThat(registry.all()).hasSize(5)
    }
}
```

`PinHasherTest.kt`:
```kotlin
package uk.co.siland.househub.core.access

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Assert.assertThrows
import org.junit.Test

class PinHasherTest {
    private val hasher = PinHasher()

    @Test
    fun acceptsExactlyFourAsciiDigits() {
        assertThat(hasher.isWellFormed("0000")).isTrue()
        assertThat(hasher.isWellFormed("2468")).isTrue()
    }

    @Test
    fun rejectsOtherLengthsLettersAndNonAsciiDigits() {
        listOf("", "123", "12345", "12a4", "12 4", "١٢٣٤").forEach {
            assertThat(hasher.isWellFormed(it)).isFalse()
            assertThrows(IllegalArgumentException::class.java) { hasher.validate(it) }
        }
    }

    @Test
    fun matchesOnlyTheSamePinAndSalt() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("2468", salt)
        assertThat(hasher.matches("2468", salt, hash)).isTrue()
        assertThat(hasher.matches("2469", salt, hash)).isFalse()
        assertThat(hasher.matches("2468", hasher.newSalt(), hash)).isFalse()
    }

    @Test
    fun saltsAreRandomSixteenBytes() {
        val a = hasher.newSalt()
        assertThat(Base64.getDecoder().decode(a)).hasLength(16)
        assertThat(a).isNotEqualTo(hasher.newSalt())
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: FAIL with `NotImplementedError`.

- [ ] **Step 5: Implement `PermissionRegistry` and `PinHasher`**

`PermissionRegistry.kt` body:
```kotlin
@Singleton
class PermissionRegistry @Inject constructor(
    sources: Set<@JvmSuppressWildcards PermissionSource>,
) {
    private val byId: Map<String, PermissionDef> = buildMap {
        sources.flatMap { it.permissions }.forEach { def ->
            require(put(def.id, def) == null) { "Duplicate permission id ${def.id}" }
        }
    }

    fun require(id: String): PermissionDef =
        byId[id] ?: throw IllegalArgumentException("Unknown permission $id")

    fun isGranted(role: Role, id: String): Boolean = role in require(id).defaultRoles

    fun all(): Collection<PermissionDef> = byId.values
}
```

`PinHasher.kt`:
```kotlin
package uk.co.siland.househub.core.access

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

class PinHasher @Inject constructor() {
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun isWellFormed(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun validate(pin: String) = require(isWellFormed(pin)) { "PIN must be $PIN_LENGTH digits" }

    fun newSalt(): String = encoder.encodeToString(ByteArray(16).also(random::nextBytes))

    fun hash(pin: String, salt: String): String = encoder.encodeToString(derive(pin, decoder.decode(salt)))

    fun matches(pin: String, salt: String, hash: String): Boolean =
        MessageDigest.isEqual(derive(pin, decoder.decode(salt)), decoder.decode(hash))

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        const val PIN_LENGTH = 4
        // Kid-proofing, not strong auth: identify() checks every person, so keep this cheap on a 2 GB tablet.
        private const val ITERATIONS = 10_000
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: PASS (10 tests).

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts core/access
git commit -m "Add core:access permission registry and 4-digit PIN hashing"
```

---

### Task 6: `:core:access` — PIN management and lockout store

**Files:**
- Create: `core/access/src/main/java/uk/co/siland/househub/core/access/PinManager.kt`, `LockoutStore.kt`
- Test: `core/access/src/test/java/uk/co/siland/househub/core/access/PinManagerTest.kt`, `LockoutStoreTest.kt`

**Interfaces:**
- Consumes: `HouseholdRepository`, `Person`, `PersonId`, `Role`, `Credential` (Task 4); `PinHasher` (Task 5).
- Produces:
  - `data class Identified(person: Person, role: Role)`
  - `class PinInUseException : Exception`
  - `@Singleton class PinManager @Inject constructor(household: HouseholdRepository, hasher: PinHasher) { suspend fun setPin(id: PersonId, pin: String); suspend fun identify(pin: String): Identified? }`
  - `@Singleton class LockoutStore @Inject constructor(@ApplicationContext context: Context) { fun lockedUntil(nowMillis: Long): Long?; fun recordFailure(nowMillis: Long); fun reset() }` with `FREE_ATTEMPTS = 5`, `BASE_LOCK_MS = 30_000L`, `MAX_DOUBLINGS = 5`

- [ ] **Step 1: Write stubs**

`PinManager.kt`:
```kotlin
package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.PersonId
import uk.co.siland.househub.core.household.Role

data class Identified(val person: Person, val role: Role)

class PinInUseException : Exception("That PIN is already used by someone else")

@Singleton
class PinManager @Inject constructor(
    private val household: HouseholdRepository,
    private val hasher: PinHasher,
) {
    suspend fun setPin(id: PersonId, pin: String): Unit = TODO()
    suspend fun identify(pin: String): Identified? = TODO()
}
```

`LockoutStore.kt`:
```kotlin
package uk.co.siland.househub.core.access

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockoutStore @Inject constructor(@ApplicationContext context: Context) {
    fun lockedUntil(nowMillis: Long): Long? = TODO()
    fun recordFailure(nowMillis: Long): Unit = TODO()
    fun reset(): Unit = TODO()

    companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_DOUBLINGS = 5
    }
}
```

- [ ] **Step 2: Write the failing tests**

`PinManagerTest.kt`:
```kotlin
package uk.co.siland.househub.core.access

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.PersonId
import uk.co.siland.househub.core.household.Role
import uk.co.siland.househub.core.household.db.HouseholdDatabase

@RunWith(AndroidJUnit4::class)
class PinManagerTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java)
            .allowMainThreadQueries().build()
        household = HouseholdRepository(db)
        pins = PinManager(household, PinHasher())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun identifyReturnsPersonAndRole() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        val mia = household.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        pins.setPin(alex.id, "1234")
        pins.setPin(mia.id, "9876")
        assertThat(pins.identify("9876")).isEqualTo(Identified(mia, Role.CHILD))
        assertThat(pins.identify("1234")).isEqualTo(Identified(alex, Role.ADMIN))
    }

    @Test
    fun identifyReturnsNullForWrongOrMalformedPin() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        pins.setPin(alex.id, "1234")
        assertThat(pins.identify("1235")).isNull()
        assertThat(pins.identify("12")).isNull()
    }

    @Test
    fun duplicatePinIsRejected() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        val sam = household.addPerson("Sam", 0xFF5B9BE0, Role.ADULT)
        pins.setPin(alex.id, "1234")
        assertThrows(PinInUseException::class.java) { runBlocking { pins.setPin(sam.id, "1234") } }
    }

    @Test
    fun personCanKeepTheirOwnPin() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        pins.setPin(alex.id, "1234")
        pins.setPin(alex.id, "1234")
        assertThat(pins.identify("1234")?.person).isEqualTo(alex)
    }

    @Test
    fun malformedPinIsRejected() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { pins.setPin(alex.id, "12345") } }
    }

    @Test
    fun unknownPersonIsRejected() = runTest {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { pins.setPin(PersonId("nobody"), "1234") } }
    }

    @Test
    fun removedPersonsPinNoLongerIdentifies() = runTest {
        val alex = household.addPerson("Alex", 0xFF4CB387, Role.ADMIN)
        val mia = household.addPerson("Mia", 0xFFE07BA8, Role.CHILD)
        pins.setPin(alex.id, "1234")
        pins.setPin(mia.id, "9876")
        household.removePerson(mia.id)
        assertThat(pins.identify("9876")).isNull()
    }
}
```

`LockoutStoreTest.kt`:
```kotlin
package uk.co.siland.househub.core.access

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LockoutStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = LockoutStore(context)
    private val t0 = 1_000_000L

    @Test
    fun fourFailuresDoNotLock() {
        repeat(4) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isNull()
    }

    @Test
    fun fifthFailureLocksForThirtySecondsThenDoubles() {
        repeat(5) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + 30_000)
        store.recordFailure(t0 + 31_000)
        assertThat(store.lockedUntil(t0 + 31_000)).isEqualTo(t0 + 31_000 + 60_000)
    }

    @Test
    fun lockExpires() {
        repeat(5) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0 + 30_000)).isNull()
    }

    @Test
    fun resetClearsFailures() {
        repeat(5) { store.recordFailure(t0) }
        store.reset()
        assertThat(store.lockedUntil(t0)).isNull()
        repeat(4) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isNull()
    }

    @Test
    fun lockoutSurvivesNewStoreInstance() {
        repeat(5) { store.recordFailure(t0) }
        assertThat(LockoutStore(context).lockedUntil(t0 + 1_000)).isEqualTo(t0 + 30_000)
    }

    @Test
    fun lockDurationIsCappedAtSixteenMinutes() {
        repeat(100) { store.recordFailure(t0) }
        assertThat(store.lockedUntil(t0)).isEqualTo(t0 + 30_000L * 32)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: FAIL with `NotImplementedError` in the new tests.

- [ ] **Step 4: Implement `PinManager`**

```kotlin
package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.siland.househub.core.household.Credential
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.PersonId
import uk.co.siland.househub.core.household.Role

data class Identified(val person: Person, val role: Role)

class PinInUseException : Exception("That PIN is already used by someone else")

@Singleton
class PinManager @Inject constructor(
    private val household: HouseholdRepository,
    private val hasher: PinHasher,
) {
    suspend fun setPin(id: PersonId, pin: String) {
        hasher.validate(pin)
        val (hash, salt) = withContext(Dispatchers.Default) {
            val others = household.credentials().filter { it.personId != id }
            if (others.any { it.matches(pin) }) throw PinInUseException()
            val salt = hasher.newSalt()
            hasher.hash(pin, salt) to salt
        }
        household.setPinHash(id, hash, salt)
    }

    suspend fun identify(pin: String): Identified? {
        if (!hasher.isWellFormed(pin)) return null
        val match = withContext(Dispatchers.Default) {
            household.credentials().firstOrNull { it.matches(pin) }
        } ?: return null
        val person = household.person(match.personId) ?: return null
        return Identified(person, match.role)
    }

    private fun Credential.matches(pin: String): Boolean {
        val h = pinHash ?: return false
        val s = salt ?: return false
        return hasher.matches(pin, s, h)
    }
}
```

- [ ] **Step 5: Implement `LockoutStore`**

```kotlin
package uk.co.siland.househub.core.access

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Consecutive-failure counter. Kept in SharedPreferences so it survives the app being killed. */
@Singleton
class LockoutStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("lockout", Context.MODE_PRIVATE)

    fun lockedUntil(nowMillis: Long): Long? =
        prefs.getLong(KEY_UNTIL, 0L).takeIf { it > nowMillis }

    fun recordFailure(nowMillis: Long) {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val until = if (failures >= FREE_ATTEMPTS) {
            nowMillis + (BASE_LOCK_MS shl minOf(failures - FREE_ATTEMPTS, MAX_DOUBLINGS))
        } else {
            prefs.getLong(KEY_UNTIL, 0L)
        }
        prefs.edit().putInt(KEY_FAILURES, failures).putLong(KEY_UNTIL, until).commit()
    }

    fun reset() {
        prefs.edit().clear().commit()
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCK_MS = 30_000L
        const val MAX_DOUBLINGS = 5
        private const val KEY_FAILURES = "failures"
        private const val KEY_UNTIL = "lockedUntil"
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: PASS (all Task 5 and Task 6 tests).

- [ ] **Step 7: Commit**

```bash
git add core/access
git commit -m "Add PIN manager and persistent lockout store"
```

---

### Task 7: `:core:access` — `AccessControl`, session and PIN prompt

**Files:**
- Create: `core/access/src/main/java/uk/co/siland/househub/core/access/AccessControl.kt`, `PinPromptController.kt`, `DefaultAccessControl.kt`, `di/AccessModule.kt`
- Test: `core/access/src/test/java/uk/co/siland/househub/core/access/DefaultAccessControlTest.kt`

**Interfaces:**
- Consumes: `PermissionRegistry`, `PermissionSource`, `CorePermissionSource` (Task 5); `PinManager`, `Identified`, `LockoutStore` (Task 6); `WallClock`, `ApplicationScope` (Task 3); `Person`, `Role` (Task 4).
- Produces:
  - `data class Authorised(person: Person, role: Role, granted: Set<String>)`
  - `interface AccessControl { val session: StateFlow<Identified?>; suspend fun authorise(vararg anyOf: String): Authorised?; fun touch(); fun lock() }` — returns `null` if cancelled; `granted` is the non-empty subset of `anyOf` the person holds
  - `const val SESSION_TIMEOUT_MS = 60_000L`
  - `sealed interface PinError { data object WrongPin; data class NotAllowed(name: String) }`
  - `class PinRequest(label: String, error: PinError?, lockedUntilMillis: Long?)` (identity equality)
  - `@Singleton class PinPromptController @Inject constructor() { val request: StateFlow<PinRequest?>; fun submit(pin: String); fun cancel() }` plus module-internal `open(...)`, `ask(...)`, `dismiss()`

- [ ] **Step 1: Write the interfaces and prompt controller**

`AccessControl.kt`:
```kotlin
package uk.co.siland.househub.core.access

import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.Role

data class Authorised(val person: Person, val role: Role, val granted: Set<String>)

const val SESSION_TIMEOUT_MS = 60_000L

interface AccessControl {
    /** The person currently identified, or null once the session has timed out or been locked. */
    val session: StateFlow<Identified?>

    /**
     * Succeeds if the identified person (or whoever enters a PIN) holds at least one of [anyOf].
     * Shows the PIN pad when needed, and always for fresh-PIN permissions. Returns null if cancelled.
     */
    suspend fun authorise(vararg anyOf: String): Authorised?

    /** Call on each touch-down; extends an active session. */
    fun touch()

    fun lock()
}
```

`PinPromptController.kt`:
```kotlin
package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PinError {
    data object WrongPin : PinError
    data class NotAllowed(val name: String) : PinError
}

/** One showing of the PIN pad. Identity equality: every retry is a new request. */
class PinRequest internal constructor(
    val label: String,
    val error: PinError?,
    val lockedUntilMillis: Long?,
) {
    internal val answer = CompletableDeferred<String?>()
}

@Singleton
class PinPromptController @Inject constructor() {
    private val _request = MutableStateFlow<PinRequest?>(null)
    val request: StateFlow<PinRequest?> = _request.asStateFlow()

    fun submit(pin: String) {
        _request.value?.answer?.complete(pin)
    }

    fun cancel() {
        _request.value?.answer?.complete(null)
    }

    internal fun open(label: String, error: PinError?, lockedUntilMillis: Long?): PinRequest =
        PinRequest(label, error, lockedUntilMillis).also { _request.value = it }

    internal suspend fun ask(label: String, error: PinError?, lockedUntilMillis: Long?): String? =
        open(label, error, lockedUntilMillis).answer.await()

    internal fun dismiss() {
        _request.value = null
    }
}
```

`DefaultAccessControl.kt` (stub):
```kotlin
package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.househub.core.plugin.ApplicationScope
import uk.co.siland.househub.core.plugin.WallClock

@Singleton
class DefaultAccessControl @Inject constructor(
    private val registry: PermissionRegistry,
    private val pins: PinManager,
    private val lockout: LockoutStore,
    private val prompt: PinPromptController,
    private val clock: WallClock,
    @ApplicationScope private val scope: CoroutineScope,
) : AccessControl {
    override val session: StateFlow<Identified?> get() = TODO()
    override suspend fun authorise(vararg anyOf: String): Authorised? = TODO()
    override fun touch(): Unit = TODO()
    override fun lock(): Unit = TODO()
}
```

`di/AccessModule.kt`:
```kotlin
package uk.co.siland.househub.core.access.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import uk.co.siland.househub.core.access.AccessControl
import uk.co.siland.househub.core.access.CorePermissionSource
import uk.co.siland.househub.core.access.DefaultAccessControl
import uk.co.siland.househub.core.access.PermissionSource

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessModule {
    @Binds
    abstract fun accessControl(impl: DefaultAccessControl): AccessControl

    @Multibinds
    abstract fun permissionSources(): Set<PermissionSource>

    @Binds
    @IntoSet
    abstract fun corePermissions(impl: CorePermissionSource): PermissionSource
}
```

- [ ] **Step 2: Write the failing tests**

`DefaultAccessControlTest.kt`:
```kotlin
package uk.co.siland.househub.core.access

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.Role
import uk.co.siland.househub.core.household.db.HouseholdDatabase
import uk.co.siland.househub.core.plugin.WallClock

@RunWith(AndroidJUnit4::class)
class DefaultAccessControlTest {
    private lateinit var db: HouseholdDatabase
    private lateinit var household: HouseholdRepository
    private lateinit var pins: PinManager
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prompt = PinPromptController()
    private val seen = mutableListOf<PinRequest>()

    private val everyone = object : PermissionSource {
        override val permissions = listOf(PermissionDef("test.any", "Do a thing", Role.entries.toSet()))
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HouseholdDatabase::class.java).allowMainThreadQueries().build()
        household = HouseholdRepository(db)
        pins = PinManager(household, PinHasher())
    }

    @After
    fun tearDown() = db.close()

    private fun TestScope.access() = DefaultAccessControl(
        registry = PermissionRegistry(setOf(CorePermissionSource(), everyone)),
        pins = pins,
        lockout = LockoutStore(context),
        prompt = prompt,
        clock = WallClock { testScheduler.currentTime },
        scope = backgroundScope,
    )

    private suspend fun person(name: String, role: Role, pin: String): Person =
        household.addPerson(name, 0xFF4CB387, role).also { pins.setPin(it.id, pin) }

    /** Answers successive PIN pad requests in order; null = tap Cancel. */
    private fun TestScope.answerPins(vararg answers: String?) {
        val queue = ArrayDeque(answers.toList())
        backgroundScope.launch {
            prompt.request.filterNotNull().collect { req ->
                seen += req
                if (queue.isEmpty()) return@collect
                val pin = queue.removeFirst()
                if (pin == null) prompt.cancel() else prompt.submit(pin)
            }
        }
    }

    /** Starts [permission] and returns the first PIN pad request it shows, then cancels it. */
    private suspend fun TestScope.firstPromptFor(access: DefaultAccessControl, permission: String): PinRequest {
        val job = launch { access.authorise(permission) }
        val request = prompt.request.filterNotNull().first()
        job.cancel()
        return request
    }

    @Test
    fun correctPinAuthorisesAndStartsSession() = runTest {
        val alex = person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        val result = access.authorise(CorePermissions.SETTINGS_MANAGE)
        assertThat(result?.person).isEqualTo(alex)
        assertThat(result?.granted).containsExactly(CorePermissions.SETTINGS_MANAGE)
        assertThat(access.session.value?.person).isEqualTo(alex)
        assertThat(prompt.request.value).isNull()
    }

    @Test
    fun activeSessionSkipsThePrompt() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        val again = withTimeout(1_000) { access.authorise("test.any") }
        assertThat(again).isNotNull()
        assertThat(seen).hasSize(1)
    }

    @Test
    fun freshPinPermissionPromptsEvenDuringSession() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        val request = firstPromptFor(access, CorePermissions.KIOSK_EXIT)
        assertThat(request.label).isEqualTo("Exit kiosk mode")
    }

    @Test
    fun sessionExpiresAfterSixtySecondsIdle() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(59_000); runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(2_000); runCurrent()
        assertThat(access.session.value).isNull()
        assertThat(firstPromptFor(access, CorePermissions.SETTINGS_MANAGE).error).isNull()
    }

    @Test
    fun touchExtendsSessionButItStillExpires() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        advanceTimeBy(50_000); runCurrent()
        access.touch()
        advanceTimeBy(50_000); runCurrent()
        assertThat(access.session.value).isNotNull()
        advanceTimeBy(11_000); runCurrent()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun lockEndsSessionImmediately() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        access.lock()
        assertThat(access.session.value).isNull()
    }

    @Test
    fun wrongPinShowsErrorThenAcceptsCorrectPin() = runTest {
        val alex = person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("0000", "1234")
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)?.person).isEqualTo(alex)
        assertThat(seen.map { it.error }).containsExactly(null, PinError.WrongPin).inOrder()
    }

    @Test
    fun personWithoutPermissionIsToldAndNotSignedIn() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876", null)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(seen.last().error).isEqualTo(PinError.NotAllowed("Mia"))
        assertThat(access.session.value).isNull()
    }

    @Test
    fun sessionPersonWithoutPermissionIsPromptedAgain() = runTest {
        person("Mia", Role.CHILD, "9876")
        val alex = person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("9876", "1234")
        access.authorise("test.any")
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)?.person).isEqualTo(alex)
    }

    @Test
    fun grantedContainsOnlyHeldPermissions() = runTest {
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("9876")
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE, "test.any")?.granted).containsExactly("test.any")
    }

    @Test
    fun cancelReturnsNullAndHidesPad() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins(null)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(prompt.request.value).isNull()
    }

    @Test
    fun correctPinIsRefusedDuringLockout() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "0000", "1234", null)
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNull()
        assertThat(seen[5].lockedUntilMillis).isEqualTo(30_000L)
        assertThat(access.session.value).isNull()

        advanceTimeBy(30_001)
        answerPins("1234")
        assertThat(access.authorise(CorePermissions.SETTINGS_MANAGE)).isNotNull()
    }

    @Test
    fun notAllowedPinDoesNotResetLockout() = runTest {
        person("Alex", Role.ADMIN, "1234")
        person("Mia", Role.CHILD, "9876")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "9876", "0000", null)
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        assertThat(seen.last().lockedUntilMillis).isNotNull()
    }

    @Test
    fun authorisedPinResetsLockoutCounter() = runTest {
        person("Alex", Role.ADMIN, "1234")
        val access = access()
        answerPins("0000", "0000", "0000", "0000", "1234")
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        access.lock()
        answerPins("0000", null)
        access.authorise(CorePermissions.SETTINGS_MANAGE)
        assertThat(seen.last().lockedUntilMillis).isNull()
    }

    @Test
    fun unknownPermissionThrows() = runTest {
        val access = access()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { access.authorise("nope") } }
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:access:testDebugUnitTest --tests "*DefaultAccessControlTest*"`
Expected: FAIL with `NotImplementedError`.

- [ ] **Step 4: Implement `DefaultAccessControl`**

```kotlin
package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.househub.core.household.Role
import uk.co.siland.househub.core.plugin.ApplicationScope
import uk.co.siland.househub.core.plugin.WallClock

@Singleton
class DefaultAccessControl @Inject constructor(
    private val registry: PermissionRegistry,
    private val pins: PinManager,
    private val lockout: LockoutStore,
    private val prompt: PinPromptController,
    private val clock: WallClock,
    @ApplicationScope private val scope: CoroutineScope,
) : AccessControl {
    private val _session = MutableStateFlow<Identified?>(null)
    override val session: StateFlow<Identified?> = _session.asStateFlow()

    private var expiry: Job? = null
    // Serialises callers so repeated taps can't stack PIN pads, and a queued caller sees the session the first one started.
    private val authoriseLock = Mutex()

    override suspend fun authorise(vararg anyOf: String): Authorised? {
        require(anyOf.isNotEmpty()) { "authorise needs at least one permission" }
        val defs = anyOf.map(registry::require)
        return authoriseLock.withLock {
            val current = _session.value
            val sessionGrants = current?.let { grantedFor(it.role, anyOf) }.orEmpty()
            if (current != null && sessionGrants.isNotEmpty() && defs.none { it.freshPin }) {
                touch()
                return@withLock Authorised(current.person, current.role, sessionGrants)
            }
            try {
                promptUntilResolved(defs.first().label, anyOf)
            } finally {
                prompt.dismiss()
            }
        }
    }

    private suspend fun promptUntilResolved(label: String, anyOf: Array<out String>): Authorised? {
        var error: PinError? = null
        while (true) {
            val pin = prompt.ask(label, error, lockout.lockedUntil(clock.nowMillis())) ?: return null
            if (lockout.lockedUntil(clock.nowMillis()) != null) continue

            val identified = pins.identify(pin)
            if (identified == null) {
                lockout.recordFailure(clock.nowMillis())
                error = PinError.WrongPin
                continue
            }
            val granted = grantedFor(identified.role, anyOf)
            if (granted.isEmpty()) {
                // A real but unauthorised PIN neither resets nor counts, so a child's own PIN can't clear the counter.
                error = PinError.NotAllowed(identified.person.name)
                continue
            }
            lockout.reset()
            _session.value = identified
            restartExpiry()
            return Authorised(identified.person, identified.role, granted)
        }
    }

    override fun touch() {
        if (_session.value != null) restartExpiry()
    }

    override fun lock() {
        expiry?.cancel()
        _session.value = null
    }

    private fun grantedFor(role: Role, anyOf: Array<out String>): Set<String> =
        anyOf.filter { registry.isGranted(role, it) }.toSet()

    private fun restartExpiry() {
        expiry?.cancel()
        expiry = scope.launch {
            delay(SESSION_TIMEOUT_MS)
            _session.value = null
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: PASS (all access tests).

- [ ] **Step 6: Commit**

```bash
git add core/access
git commit -m "Add AccessControl with PIN identification, 60s session and lockout"
```

---

### Task 8: `:core:access` — PIN pad UI

**Files:**
- Create: `core/access/src/main/java/uk/co/siland/househub/core/access/ui/PinPad.kt`
- Test: `core/access/src/test/java/uk/co/siland/househub/core/access/ui/PinPadTest.kt`

**Interfaces:**
- Consumes: `PinPromptController`, `PinRequest`, `PinError` (Task 7); `PinHasher.PIN_LENGTH` (Task 5); `HouseHub`, `HhType`, `HhIcon` (Task 2).
- Produces:
  - `@Composable fun PinPadHost(controller: PinPromptController)` — place once at the root
  - `@Composable fun PinPadSheet(label: String, error: PinError?, lockedUntilMillis: Long?, onSubmit: (String) -> Unit, onCancel: () -> Unit)` — stateless; submits automatically on the 4th digit
  - Test tags: `pin_key_0`…`pin_key_9`, `pin_backspace`, `pin_cancel`, `pin_scrim`

- [ ] **Step 1: Write the failing test**

`PinPadTest.kt`:
```kotlin
package uk.co.siland.househub.core.access.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.househub.core.access.PinError
import uk.co.siland.househub.core.access.PinPromptController
import uk.co.siland.househub.core.ui.HouseHubTheme

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PinPadTest {
    @get:Rule val compose = createComposeRule()
    private val controller = PinPromptController()

    private fun show() = compose.setContent { HouseHubTheme(dark = true) { PinPadHost(controller) } }

    private fun tap(vararg keys: String) = keys.forEach { compose.onNodeWithTag("pin_key_$it").performClick() }

    @Test
    fun fourthDigitSubmitsAutomatically() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3", "4")
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isEqualTo("1234")
    }

    @Test
    fun threeDigitsDoNotSubmit() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.waitForIdle()
        assertThat(request.answer.isCompleted).isFalse()
    }

    @Test
    fun backspaceRemovesLastDigit() {
        val request = controller.open("Change settings", null, null)
        show()
        tap("1", "2", "3")
        compose.onNodeWithTag("pin_backspace").performClick()
        tap("4", "5")
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isEqualTo("1245")
    }

    @Test
    fun cancelAnswersNull() {
        val request = controller.open("Change settings", null, null)
        show()
        compose.onNodeWithTag("pin_cancel").performClick()
        compose.waitForIdle()
        assertThat(request.answer.getCompleted()).isNull()
    }

    @Test
    fun showsWhoIsNotAllowed() {
        controller.open("Change settings", PinError.NotAllowed("Mia"), null)
        show()
        compose.onNodeWithText("Mia can't do that").assertExists()
    }

    @Test
    fun keysAreDisabledWhileLocked() {
        // The countdown loops on delay(); stop the test clock racing through it.
        compose.mainClock.autoAdvance = false
        controller.open("Change settings", PinError.WrongPin, System.currentTimeMillis() + 60_000)
        show()
        compose.onNodeWithTag("pin_key_1").assertIsNotEnabled()
        compose.onNodeWithText("Too many tries", substring = true).assertExists()
    }

    @Test
    fun hiddenWhenNoRequest() {
        show()
        compose.onNodeWithTag("pin_scrim").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:access:testDebugUnitTest --tests "*PinPadTest*"`
Expected: FAIL — `PinPadHost` unresolved.

- [ ] **Step 3: Implement `PinPad.kt`**

```kotlin
package uk.co.siland.househub.core.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.co.siland.househub.core.access.PinError
import uk.co.siland.househub.core.access.PinHasher
import uk.co.siland.househub.core.access.PinPromptController
import uk.co.siland.househub.core.ui.HhIcon
import uk.co.siland.househub.core.ui.HhType
import uk.co.siland.househub.core.ui.HouseHub

@Composable
fun PinPadHost(controller: PinPromptController) {
    val request by controller.request.collectAsState()
    request?.let { r ->
        // Keyed on the request so each retry starts with an empty field.
        key(r) {
            PinPadSheet(
                label = r.label,
                error = r.error,
                lockedUntilMillis = r.lockedUntilMillis,
                onSubmit = controller::submit,
                onCancel = controller::cancel,
            )
        }
    }
}

@Composable
fun PinPadSheet(
    label: String,
    error: PinError?,
    lockedUntilMillis: Long?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val c = HouseHub.colors
    var digits by remember { mutableStateOf("") }
    // Counts down from the initial value rather than re-reading the wall clock, so tests with a
    // virtual frame clock stay deterministic.
    val secondsLeft by produceState(secondsUntil(lockedUntilMillis), lockedUntilMillis) {
        while (value > 0) {
            delay(1_000)
            value -= 1
        }
    }
    val locked = secondsLeft > 0
    val canType = !locked && digits.length < PinHasher.PIN_LENGTH
    val message = when {
        locked -> "Too many tries — wait ${secondsLeft}s"
        error is PinError.WrongPin -> "Wrong PIN"
        error is PinError.NotAllowed -> "${error.name} can't do that"
        else -> ""
    }
    val type: (String) -> Unit = { d ->
        digits += d
        if (digits.length == PinHasher.PIN_LENGTH) onSubmit(digits)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("pin_scrim")
            .background(Color(0x8C000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.bg)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(28.dp),
        ) {
            Text("Enter your PIN", style = HhType.screenTitle, color = c.ink)
            Text(label, style = HhType.secondary, color = c.mute)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(PinHasher.PIN_LENGTH) { i ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (i < digits.length) c.ink else c.surf3),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(message, style = HhType.label, color = c.ink, modifier = Modifier.height(20.dp))
            Spacer(Modifier.height(12.dp))
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { d -> DigitKey(d, enabled = canType) { type(d) } }
                }
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(Modifier.size(80.dp))
                DigitKey("0", enabled = canType) { type("0") }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .testTag("pin_backspace")
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable(enabled = digits.isNotEmpty() && !locked) { digits = digits.dropLast(1) },
                ) {
                    HhIcon("backspace", size = 30.dp, tint = c.mute)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Cancel",
                style = HhType.buttonLabel,
                color = c.mute,
                modifier = Modifier
                    .testTag("pin_cancel")
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 22.dp, vertical = 13.dp),
            )
        }
    }
}

@Composable
private fun DigitKey(digit: String, enabled: Boolean, onClick: () -> Unit) {
    val c = HouseHub.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag("pin_key_$digit")
            .size(80.dp)
            .clip(CircleShape)
            .background(c.surf2)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Text(digit, style = HhType.pinDigit, color = c.ink)
    }
}

private fun secondsUntil(until: Long?): Int =
    until?.let { ((it - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0) } ?: 0
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:access:testDebugUnitTest`
Expected: PASS (all access tests including 7 PIN pad tests).

- [ ] **Step 5: Commit**

```bash
git add core/access
git commit -m "Add 4-digit PIN pad sheet and host"
```

---

### Task 9: Shell logic — theme schedule and `ShellViewModel`

**Files:**
- Modify: `app/build.gradle.kts` (add project dependencies)
- Create: `app/src/main/java/uk/co/siland/househub/shell/ThemeSchedule.kt`, `MinuteTicker.kt`, `ShellUiState.kt`, `ShellViewModel.kt`, `app/src/main/java/uk/co/siland/househub/di/AppModule.kt`
- Test: `app/src/test/java/uk/co/siland/househub/shell/MainDispatcherRule.kt`, `Fakes.kt`, `ThemeScheduleTest.kt`, `ShellViewModelTest.kt`

**Interfaces:**
- Consumes: `Capability`, `HomeCard`, `HomeCardPlacer`, `HomePlacement`, `SunTimes`, `WallClock`, `ApplicationScope` (Task 3); `AccessControl`, `Authorised`, `Identified`, `CorePermissions` (Tasks 5–7); `Person`, `PersonId`, `Role` (Task 4).
- Produces:
  - `object ThemeSchedule { fun isDark(now: LocalTime, sun: SunTimes?): Boolean }`
  - `fun interface MinuteTicker { fun ticks(): Flow<LocalDateTime> }`, `val SystemMinuteTicker`
  - `const val HOME_TAB_ID = "home"`; `data class TabItem(id, label, icon)`; `data class SessionChip(name: String, color: Long)`; `data class ShellUiState(tabs, selectedTabId, session, now: LocalDateTime, dark, previewing, homeCards, settingsOpen)`
  - `class ShellViewModel { val uiState: StateFlow<ShellUiState>; val kioskExit: Flow<Unit>; fun selectTab(id); fun openSettings(); fun closeSettings(); fun exitKiosk(); fun lockSession(); fun onUserActivity(); fun toggleThemePreview() }`

- [ ] **Step 1: Add dependencies to `app/build.gradle.kts`**

Replace the `dependencies` block:
```kotlin
dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:plugin"))
    implementation(project(":core:household"))
    implementation(project(":core:access"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
```

- [ ] **Step 2: Write types and stubs**

`shell/ThemeSchedule.kt`:
```kotlin
package uk.co.siland.househub.shell

import java.time.LocalTime
import uk.co.siland.househub.core.plugin.SunTimes

object ThemeSchedule {
    val DEFAULT_DAY_START: LocalTime = LocalTime.of(7, 0)
    val DEFAULT_DAY_END: LocalTime = LocalTime.of(19, 0)

    fun isDark(now: LocalTime, sun: SunTimes?): Boolean = TODO()
}
```

`shell/MinuteTicker.kt`:
```kotlin
package uk.co.siland.househub.shell

import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun interface MinuteTicker {
    fun ticks(): Flow<LocalDateTime>
}

val SystemMinuteTicker = MinuteTicker {
    flow {
        while (true) {
            val now = LocalDateTime.now()
            emit(now)
            delay(60_000L - (now.second * 1_000L + now.nano / 1_000_000L))
        }
    }
}
```

`shell/ShellUiState.kt`:
```kotlin
package uk.co.siland.househub.shell

import java.time.LocalDateTime
import uk.co.siland.househub.core.plugin.HomePlacement

const val HOME_TAB_ID = "home"

data class TabItem(val id: String, val label: String, val icon: String)

data class SessionChip(val name: String, val color: Long)

data class ShellUiState(
    val tabs: List<TabItem> = emptyList(),
    val selectedTabId: String = HOME_TAB_ID,
    val session: SessionChip? = null,
    val now: LocalDateTime = LocalDateTime.now(),
    val dark: Boolean = true,
    val previewing: Boolean = false,
    val homeCards: List<HomePlacement> = emptyList(),
    val settingsOpen: Boolean = false,
)
```

`shell/ShellViewModel.kt` (stub):
```kotlin
package uk.co.siland.househub.shell

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.househub.core.access.AccessControl
import uk.co.siland.househub.core.plugin.Capability

@HiltViewModel
class ShellViewModel @Inject constructor(
    capabilities: Set<@JvmSuppressWildcards Capability>,
    ticker: MinuteTicker,
    private val access: AccessControl,
) : ViewModel() {
    val uiState: StateFlow<ShellUiState> get() = TODO()
    val kioskExit: Flow<Unit> get() = TODO()
    fun selectTab(id: String): Unit = TODO()
    fun openSettings(): Unit = TODO()
    fun closeSettings(): Unit = TODO()
    fun exitKiosk(): Unit = TODO()
    fun lockSession(): Unit = TODO()
    fun onUserActivity(): Unit = TODO()
    fun toggleThemePreview(): Unit = TODO()
}
```

`di/AppModule.kt`:
```kotlin
package uk.co.siland.househub.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.co.siland.househub.core.plugin.ApplicationScope
import uk.co.siland.househub.core.plugin.Capability
import uk.co.siland.househub.core.plugin.WallClock
import uk.co.siland.househub.shell.MinuteTicker
import uk.co.siland.househub.shell.SystemMinuteTicker

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Multibinds
    abstract fun capabilities(): Set<Capability>

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }

        @Provides
        fun minuteTicker(): MinuteTicker = SystemMinuteTicker
    }
}
```

- [ ] **Step 3: Write test helpers and failing tests**

`app/src/test/java/uk/co/siland/househub/shell/MainDispatcherRule.kt`:
```kotlin
package uk.co.siland.househub.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
```

`Fakes.kt`:
```kotlin
package uk.co.siland.househub.shell

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import uk.co.siland.househub.core.access.AccessControl
import uk.co.siland.househub.core.access.Authorised
import uk.co.siland.househub.core.access.Identified
import uk.co.siland.househub.core.plugin.Capability
import uk.co.siland.househub.core.plugin.HomeCard

class FakeCapability(
    override val id: String,
    override val order: Int,
    shown: Boolean,
    private val cardList: List<HomeCard> = emptyList(),
) : Capability {
    override val label = id.replaceFirstChar { it.uppercase() }
    override val icon = "star"
    val shownFlow = MutableStateFlow(shown)
    override val hasTab: Flow<Boolean> = shownFlow
    override fun cards(): Flow<List<HomeCard>> = flowOf(cardList)
    @Composable override fun TabContent() {}
}

/** Returns [result] for every authorise call and, like the real one, starts a session on success. */
class FakeAccessControl(var result: Authorised? = null) : AccessControl {
    override val session = MutableStateFlow<Identified?>(null)
    val requested = mutableListOf<List<String>>()
    var touches = 0
    override suspend fun authorise(vararg anyOf: String): Authorised? {
        requested += anyOf.toList()
        result?.let { session.value = Identified(it.person, it.role) }
        return result
    }
    override fun touch() { touches++ }
    override fun lock() { session.value = null }
}
```

`ThemeScheduleTest.kt`:
```kotlin
package uk.co.siland.househub.shell

import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import org.junit.Test
import uk.co.siland.househub.core.plugin.SunTimes

class ThemeScheduleTest {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test
    fun fallbackIsLightFromSevenUntilNineteen() {
        assertThat(ThemeSchedule.isDark(t(6, 59), null)).isTrue()
        assertThat(ThemeSchedule.isDark(t(7, 0), null)).isFalse()
        assertThat(ThemeSchedule.isDark(t(18, 59), null)).isFalse()
        assertThat(ThemeSchedule.isDark(t(19, 0), null)).isTrue()
    }

    @Test
    fun usesSunriseAndSunsetWhenKnown() {
        val sun = SunTimes(t(6, 12), t(19, 48))
        assertThat(ThemeSchedule.isDark(t(6, 11), sun)).isTrue()
        assertThat(ThemeSchedule.isDark(t(6, 12), sun)).isFalse()
        assertThat(ThemeSchedule.isDark(t(19, 30), sun)).isFalse()
        assertThat(ThemeSchedule.isDark(t(19, 48), sun)).isTrue()
    }

    @Test
    fun invertedSunTimesFallBack() {
        val nonsense = SunTimes(t(20, 0), t(4, 0))
        assertThat(ThemeSchedule.isDark(t(12, 0), nonsense)).isFalse()
        assertThat(ThemeSchedule.isDark(t(22, 0), nonsense)).isTrue()
    }
}
```

`ShellViewModelTest.kt`:
```kotlin
package uk.co.siland.househub.shell

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import uk.co.siland.househub.core.access.Authorised
import uk.co.siland.househub.core.access.CorePermissions
import uk.co.siland.househub.core.access.Identified
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.PersonId
import uk.co.siland.househub.core.household.Role
import uk.co.siland.househub.core.plugin.Capability
import uk.co.siland.househub.core.plugin.HomeCard
import uk.co.siland.househub.core.plugin.HomeCardSize

class ShellViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val noon = LocalDateTime.of(2026, 9, 23, 12, 0)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val admin = Authorised(alex, Role.ADMIN, setOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.KIOSK_EXIT))
    private val access = FakeAccessControl()
    private val ticks = MutableStateFlow(noon)

    private fun vm(caps: Set<Capability> = emptySet()) = ShellViewModel(caps, { ticks }, access)

    @Test
    fun tabsShowOnlyCapabilitiesWithTabsInOrder() = runTest {
        val vm = vm(
            setOf(
                FakeCapability("weather", order = 90, shown = false),
                FakeCapability("lights", order = 20, shown = true),
                FakeCapability("calendar", order = 10, shown = true),
            ),
        )
        vm.uiState.test {
            assertThat(expectMostRecentItem().tabs.map { it.id }).containsExactly("calendar", "lights").inOrder()
        }
    }

    @Test
    fun noCapabilitiesGivesHomeOnlyAndTracksTheClock() = runTest {
        vm().uiState.test {
            val s = expectMostRecentItem()
            assertThat(s.tabs).isEmpty()
            assertThat(s.selectedTabId).isEqualTo(HOME_TAB_ID)
            assertThat(s.now).isEqualTo(noon)
            assertThat(s.dark).isFalse()
        }
    }

    @Test
    fun selectedTabFallsBackToHomeWhenItDisappears() = runTest {
        val calendar = FakeCapability("calendar", order = 10, shown = true)
        val vm = vm(setOf(calendar))
        vm.uiState.test {
            vm.selectTab("calendar")
            assertThat(expectMostRecentItem().selectedTabId).isEqualTo("calendar")
            calendar.shownFlow.value = false
            assertThat(expectMostRecentItem().selectedTabId).isEqualTo(HOME_TAB_ID)
        }
    }

    @Test
    fun homeCardsArePlacedFromAllCapabilities() = runTest {
        val today = HomeCard("today", HomeCardSize.TALL, 100) {}
        val forecast = HomeCard("forecast", HomeCardSize.WIDE, 10) {}
        val vm = vm(
            setOf(
                FakeCapability("calendar", 10, true, listOf(today)),
                FakeCapability("weather", 90, false, listOf(forecast)),
            ),
        )
        vm.uiState.test {
            assertThat(expectMostRecentItem().homeCards.map { it.card.id }).containsExactly("today", "forecast")
        }
    }

    @Test
    fun settingsOpenOnlyWhenAuthorised() = runTest {
        val vm = vm()
        vm.uiState.test {
            assertThat(expectMostRecentItem().settingsOpen).isFalse()
            vm.openSettings()
            expectNoEvents()
            assertThat(access.requested.last()).containsExactly(CorePermissions.SETTINGS_MANAGE)

            access.result = admin
            vm.openSettings()
            assertThat(expectMostRecentItem().settingsOpen).isTrue()

            vm.closeSettings()
            assertThat(expectMostRecentItem().settingsOpen).isFalse()
        }
    }

    @Test
    fun settingsCloseWhenSessionEnds() = runTest {
        val vm = vm()
        access.result = admin
        vm.uiState.test {
            vm.openSettings()
            assertThat(expectMostRecentItem().settingsOpen).isTrue()
            access.session.value = null
            assertThat(expectMostRecentItem().settingsOpen).isFalse()
        }
    }

    @Test
    fun exitKioskEmitsOnlyWhenAuthorised() = runTest {
        val vm = vm()
        vm.kioskExit.test {
            vm.exitKiosk()
            expectNoEvents()
            access.result = admin
            vm.exitKiosk()
            awaitItem()
        }
    }

    @Test
    fun sessionShowsAsChip() = runTest {
        val vm = vm()
        vm.uiState.test {
            access.session.value = Identified(alex, Role.ADMIN)
            assertThat(expectMostRecentItem().session).isEqualTo(SessionChip("Alex", 0xFF4CB387))
            vm.lockSession()
            assertThat(expectMostRecentItem().session).isNull()
        }
    }

    @Test
    fun themePreviewEndsWhenScheduleFlips() = runTest {
        val vm = vm()
        vm.uiState.test {
            assertThat(expectMostRecentItem().dark).isFalse()
            vm.toggleThemePreview()
            expectMostRecentItem().let {
                assertThat(it.dark).isTrue()
                assertThat(it.previewing).isTrue()
            }
            ticks.value = noon.withHour(20)
            expectMostRecentItem().let {
                assertThat(it.dark).isTrue()
                assertThat(it.previewing).isFalse()
            }
            ticks.value = noon.plusDays(1)
            assertThat(expectMostRecentItem().dark).isFalse()
        }
    }

    @Test
    fun userActivityTouchesSession() {
        vm().onUserActivity()
        assertThat(access.touches).isEqualTo(1)
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL with `NotImplementedError`.

- [ ] **Step 5: Implement `ThemeSchedule.isDark`**

```kotlin
    fun isDark(now: LocalTime, sun: SunTimes?): Boolean {
        val usable = sun?.takeIf { it.sunrise < it.sunset }
        val start = usable?.sunrise ?: DEFAULT_DAY_START
        val end = usable?.sunset ?: DEFAULT_DAY_END
        return now < start || now >= end
    }
```

- [ ] **Step 6: Implement `ShellViewModel`**

```kotlin
package uk.co.siland.househub.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.siland.househub.core.access.AccessControl
import uk.co.siland.househub.core.access.CorePermissions
import uk.co.siland.househub.core.plugin.Capability
import uk.co.siland.househub.core.plugin.HomeCardPlacer
import uk.co.siland.househub.core.plugin.HomePlacement

@HiltViewModel
class ShellViewModel @Inject constructor(
    capabilities: Set<@JvmSuppressWildcards Capability>,
    ticker: MinuteTicker,
    private val access: AccessControl,
) : ViewModel() {
    private val ordered = capabilities.sortedBy { it.order }
    private val selected = MutableStateFlow(HOME_TAB_ID)
    private val settingsOpen = MutableStateFlow(false)
    private val previewing = MutableStateFlow(false)
    private val kioskExitEvents = Channel<Unit>(Channel.BUFFERED)
    val kioskExit: Flow<Unit> = kioskExitEvents.receiveAsFlow()

    private val now: StateFlow<LocalDateTime> =
        ticker.ticks().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    // Sunrise/sunset arrive with the weather capability in Plan 4; until then the 07:00/19:00 fallback applies.
    private val scheduledDark: StateFlow<Boolean> =
        now.map { ThemeSchedule.isDark(it.toLocalTime(), null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSchedule.isDark(LocalTime.now(), null))

    private val tabs: Flow<List<TabItem>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(ordered.map { cap -> cap.hasTab.map { shown -> if (shown) TabItem(cap.id, cap.label, cap.icon) else null } }) {
                it.filterNotNull()
            }
        }

    private val placements: Flow<List<HomePlacement>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(ordered.map { it.cards() }) { lists -> HomeCardPlacer.place(lists.toList().flatten()) }
        }

    val uiState: StateFlow<ShellUiState> =
        combine(
            combine(tabs, selected, access.session, placements, settingsOpen) { tabs, sel, session, cards, settings ->
                ShellUiState(
                    tabs = tabs,
                    selectedTabId = if (sel == HOME_TAB_ID || tabs.any { it.id == sel }) sel else HOME_TAB_ID,
                    session = session?.let { SessionChip(it.person.name, it.person.color) },
                    homeCards = cards,
                    settingsOpen = settings,
                )
            },
            now,
            scheduledDark,
            previewing,
        ) { state, time, scheduled, preview ->
            state.copy(now = time, dark = scheduled != preview, previewing = preview)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState())

    init {
        viewModelScope.launch { scheduledDark.drop(1).collect { previewing.value = false } }
        viewModelScope.launch { access.session.collect { if (it == null) settingsOpen.value = false } }
    }

    fun selectTab(id: String) {
        selected.value = id
    }

    fun openSettings() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.SETTINGS_MANAGE) != null) settingsOpen.value = true
        }
    }

    fun closeSettings() {
        settingsOpen.value = false
    }

    fun exitKiosk() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.KIOSK_EXIT) != null) kioskExitEvents.send(Unit)
        }
    }

    fun lockSession() = access.lock()

    fun onUserActivity() = access.touch()

    fun toggleThemePreview() {
        previewing.value = !previewing.value
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (3 theme tests + 10 view-model tests).

- [ ] **Step 8: Commit**

```bash
git add app
git commit -m "Add shell view model, theme schedule and app DI"
```

---

### Task 10: Shell UI, kiosk mode and debug seed

**Files:**
- Create: `app/src/main/java/uk/co/siland/househub/shell/ui/HouseHubShell.kt`, `NavRail.kt`, `StatusBar.kt`, `HomeScreen.kt`, `HomeGrid.kt`, `SettingsPlaceholder.kt`
- Create: `app/src/main/java/uk/co/siland/househub/Kiosk.kt`
- Create: `app/src/debug/java/uk/co/siland/househub/DebugSeed.kt`, `app/src/release/java/uk/co/siland/househub/DebugSeed.kt`
- Modify: `app/src/main/java/uk/co/siland/househub/MainActivity.kt`, `HouseHubApp.kt`

**Interfaces:**
- Consumes: `ShellUiState`, `ShellViewModel`, `TabItem`, `SessionChip`, `HOME_TAB_ID` (Task 9); `PinPadHost`, `PinPromptController`, `PinManager` (Tasks 6–8); `HouseholdRepository`, `Role` (Task 4); `HhCard`, `HhIcon`, `HhType`, `HouseHub`, `HouseHubTheme`, `HhPillButton` (Task 2); `HomePlacement`, `HomeCardPlacer` (Task 3).
- Produces:
  - `@Composable fun HouseHubShell(state: ShellUiState, onSelectTab: (String) -> Unit, onOpenSettings: () -> Unit, onLockSession: () -> Unit, onToggleThemePreview: () -> Unit, tabContent: @Composable (String) -> Unit)` — stateless
  - `@Composable fun SettingsPlaceholder(onExitKiosk: () -> Unit, onClose: () -> Unit)`
  - `suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager)` (debug: creates Admin/1234; release: no-op)
  - `fun ComponentActivity.hideSystemBars()`, `showSystemBars()`, `pinToScreen()`, `unpinFromScreen()`

- [ ] **Step 1: Write `HomeGrid.kt` and `HomeScreen.kt`**

`shell/ui/HomeGrid.kt`:
```kotlin
package uk.co.siland.househub.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import uk.co.siland.househub.core.plugin.HomeCardPlacer
import uk.co.siland.househub.core.plugin.HomePlacement

private val COLUMN_WEIGHTS = floatArrayOf(1.15f, 1f, 1f)

/** The hand-off's 3-column (1.15fr 1fr 1fr) × 2-row Home grid with 14 dp gaps. */
@Composable
fun HomeGrid(placements: List<HomePlacement>, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        content = { placements.forEach { p -> Box { p.card.content() } } },
    ) { measurables, constraints ->
        val gap = 14.dp.roundToPx()
        val available = constraints.maxWidth - gap * (HomeCardPlacer.COLUMNS - 1)
        val colWidths = COLUMN_WEIGHTS.map { (available * it / COLUMN_WEIGHTS.sum()).toInt() }.toMutableList()
        colWidths[colWidths.lastIndex] += available - colWidths.sum()
        val colX = colWidths.runningFold(0) { x, w -> x + w + gap }
        val rowHeight = (constraints.maxHeight - gap * (HomeCardPlacer.ROWS - 1)) / HomeCardPlacer.ROWS

        val placeables = measurables.mapIndexed { i, m ->
            val p = placements[i]
            val w = (p.col until p.col + p.colSpan).sumOf { colWidths[it] } + gap * (p.colSpan - 1)
            val h = rowHeight * p.rowSpan + gap * (p.rowSpan - 1)
            m.measure(Constraints.fixed(w, h))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, placeable ->
                val p = placements[i]
                placeable.place(colX[p.col], p.row * (rowHeight + gap))
            }
        }
    }
}
```

`shell/ui/HomeScreen.kt`:
```kotlin
package uk.co.siland.househub.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uk.co.siland.househub.core.plugin.HomePlacement
import uk.co.siland.househub.core.ui.HhType
import uk.co.siland.househub.core.ui.HouseHub

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)

@Composable
fun HomeScreen(now: LocalDateTime, placements: List<HomePlacement>) {
    val c = HouseHub.colors
    Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth()) {
            Text(now.format(CLOCK), style = HhType.clock, color = c.ink)
            Spacer(Modifier.height(12.dp))
            Text(now.format(DATE), style = HhType.date, color = c.mute)
        }
        HomeGrid(placements, Modifier.fillMaxWidth().weight(1f))
    }
}
```

- [ ] **Step 2: Write `StatusBar.kt` and `NavRail.kt`**

`shell/ui/StatusBar.kt`:
```kotlin
package uk.co.siland.househub.shell.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import uk.co.siland.househub.core.ui.HhIcon
import uk.co.siland.househub.core.ui.HhType
import uk.co.siland.househub.core.ui.HouseHub

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun StatusBar(now: LocalDateTime, dark: Boolean, previewing: Boolean, onToggleThemePreview: () -> Unit) {
    val c = HouseHub.colors
    val label = when {
        !previewing -> "Auto"
        dark -> "Night"
        else -> "Day"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 20.dp),
    ) {
        Text(now.format(TIME), style = HhType.status, color = c.mute)
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("status_theme").clickable(onClick = onToggleThemePreview).padding(4.dp),
        ) {
            HhIcon(if (dark) "dark_mode" else "light_mode", size = 16.dp, tint = c.mute)
            Text(label, style = HhType.status.copy(fontSize = 12.sp), color = c.mute)
        }
    }
}
```

`shell/ui/NavRail.kt`:
```kotlin
package uk.co.siland.househub.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.siland.househub.core.ui.HhIcon
import uk.co.siland.househub.core.ui.HhType
import uk.co.siland.househub.core.ui.HouseHub
import uk.co.siland.househub.shell.HOME_TAB_ID
import uk.co.siland.househub.shell.SessionChip
import uk.co.siland.househub.shell.TabItem

private val HomeTab = TabItem(HOME_TAB_ID, "Home", "home")

@Composable
fun NavRail(
    tabs: List<TabItem>,
    selectedId: String,
    session: SessionChip?,
    onSelect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLockSession: () -> Unit,
) {
    val c = HouseHub.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(108.dp)
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(c.line, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            .padding(top = 16.dp, bottom = 20.dp),
    ) {
        session?.let { SessionChipView(it, onLockSession) }
        (listOf(HomeTab) + tabs).forEach { tab ->
            RailItem(tab, selected = tab.id == selectedId) { onSelect(tab.id) }
        }
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            modifier = Modifier
                .testTag("rail_settings")
                .size(80.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.surf2)
                .clickable(onClick = onOpenSettings),
        ) {
            HhIcon("settings", size = 30.dp, tint = c.ink)
            Text("Settings", style = HhType.labelSmall, color = c.ink)
        }
    }
}

@Composable
private fun RailItem(tab: TabItem, selected: Boolean, onClick: () -> Unit) {
    val c = HouseHub.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("rail_${tab.id}")
            .width(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 64.dp, height = 38.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(if (selected) c.accentSoft else Color.Transparent),
        ) {
            HhIcon(tab.icon, size = 26.dp, filled = selected, tint = if (selected) c.ink else c.mute)
        }
        Spacer(Modifier.height(6.dp))
        Text(tab.label, style = HhType.label, color = if (selected) c.ink else c.mute)
    }
}

@Composable
private fun SessionChipView(chip: SessionChip, onLock: () -> Unit) {
    val c = HouseHub.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .testTag("rail_session")
            .padding(bottom = 8.dp)
            .widthIn(max = 92.dp)
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surf2)
            .clickable(onClick = onLock)
            .padding(horizontal = 10.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(chip.color)))
        Text(
            chip.name,
            style = HhType.label,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        HhIcon("lock", size = 14.dp, tint = c.mute)
    }
}
```

- [ ] **Step 3: Write `HouseHubShell.kt` and `SettingsPlaceholder.kt`**

`shell/ui/HouseHubShell.kt`:
```kotlin
package uk.co.siland.househub.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.siland.househub.core.ui.HouseHub
import uk.co.siland.househub.shell.HOME_TAB_ID
import uk.co.siland.househub.shell.ShellUiState

@Composable
fun HouseHubShell(
    state: ShellUiState,
    onSelectTab: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLockSession: () -> Unit,
    onToggleThemePreview: () -> Unit,
    tabContent: @Composable (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HouseHub.colors.bg)) {
        StatusBar(state.now, state.dark, state.previewing, onToggleThemePreview)
        Row(Modifier.weight(1f)) {
            NavRail(state.tabs, state.selectedTabId, state.session, onSelectTab, onOpenSettings, onLockSession)
            Box(Modifier.weight(1f).padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 22.dp)) {
                if (state.selectedTabId == HOME_TAB_ID) {
                    HomeScreen(state.now, state.homeCards)
                } else {
                    tabContent(state.selectedTabId)
                }
            }
        }
    }
}
```

`shell/ui/SettingsPlaceholder.kt`:
```kotlin
package uk.co.siland.househub.shell.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.siland.househub.core.ui.HhPillButton
import uk.co.siland.househub.core.ui.HhType
import uk.co.siland.househub.core.ui.HouseHub

/** Replaced by real Settings screens in Plan 4. */
@Composable
fun SettingsPlaceholder(onExitKiosk: () -> Unit, onClose: () -> Unit) {
    val c = HouseHub.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings")
            .background(c.bg)
            // Swallow taps on empty space so they don't reach the rail underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(48.dp),
    ) {
        Text("Settings", style = HhType.screenTitle, color = c.ink)
        Text("Household, people and connections arrive in a later update.", style = HhType.body, color = c.mute)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HhPillButton("Exit kiosk", onExitKiosk)
            HhPillButton("Close", onClose, primary = true)
        }
    }
}
```

- [ ] **Step 4: Write kiosk helpers and debug seed**

`Kiosk.kt`:
```kotlin
package uk.co.siland.househub

import android.app.ActivityManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun ComponentActivity.hideSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun ComponentActivity.showSystemBars() {
    WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
}

/** Must be called while resumed. Debug builds skip pinning: it shows a system prompt on every start. */
fun ComponentActivity.pinToScreen() {
    val am = getSystemService(ActivityManager::class.java)
    if (!BuildConfig.DEBUG && am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask()
}

fun ComponentActivity.unpinFromScreen() {
    val am = getSystemService(ActivityManager::class.java)
    if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) stopLockTask()
}
```

`app/src/debug/java/uk/co/siland/househub/DebugSeed.kt`:
```kotlin
package uk.co.siland.househub

import kotlinx.coroutines.flow.first
import uk.co.siland.househub.core.access.PinManager
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.household.Role

/** Debug builds only: an Admin with PIN 1234 so Settings is reachable before the setup wizard exists. */
suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager) {
    if (household.people.first().isNotEmpty()) return
    val admin = household.addPerson("Admin", 0xFF4CB387, Role.ADMIN)
    pins.setPin(admin.id, "1234")
}
```

`app/src/release/java/uk/co/siland/househub/DebugSeed.kt`:
```kotlin
package uk.co.siland.househub

import uk.co.siland.househub.core.access.PinManager
import uk.co.siland.househub.core.household.HouseholdRepository

@Suppress("UNUSED_PARAMETER")
suspend fun seedDebugData(household: HouseholdRepository, pins: PinManager) = Unit
```

- [ ] **Step 5: Wire `HouseHubApp` and `MainActivity`**

`HouseHubApp.kt`:
```kotlin
package uk.co.siland.househub

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uk.co.siland.househub.core.access.PinManager
import uk.co.siland.househub.core.household.HouseholdRepository
import uk.co.siland.househub.core.plugin.ApplicationScope

@HiltAndroidApp
class HouseHubApp : Application() {
    @Inject lateinit var household: HouseholdRepository
    @Inject lateinit var pins: PinManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        appScope.launch { seedDebugData(household, pins) }
    }
}
```

`MainActivity.kt`:
```kotlin
package uk.co.siland.househub

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import uk.co.siland.househub.core.access.PinPromptController
import uk.co.siland.househub.core.access.ui.PinPadHost
import uk.co.siland.househub.core.plugin.Capability
import uk.co.siland.househub.core.ui.HouseHubTheme
import uk.co.siland.househub.shell.ShellViewModel
import uk.co.siland.househub.shell.ui.HouseHubShell
import uk.co.siland.househub.shell.ui.SettingsPlaceholder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    @Inject lateinit var pinPrompt: PinPromptController
    @Inject lateinit var capabilities: Set<@JvmSuppressWildcards Capability>

    // Set by Settings › Exit kiosk; cleared when the process restarts.
    private var kioskExited = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this) { }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                shell.kioskExit.collect {
                    kioskExited = true
                    unpinFromScreen()
                    showSystemBars()
                }
            }
        }
        setContent {
            val state by shell.uiState.collectAsStateWithLifecycle()
            HouseHubTheme(dark = state.dark) {
                HouseHubShell(
                    state = state,
                    onSelectTab = shell::selectTab,
                    onOpenSettings = shell::openSettings,
                    onLockSession = shell::lockSession,
                    onToggleThemePreview = shell::toggleThemePreview,
                    tabContent = { id -> capabilities.firstOrNull { it.id == id }?.TabContent() },
                )
                if (state.settingsOpen) {
                    SettingsPlaceholder(onExitKiosk = shell::exitKiosk, onClose = shell::closeSettings)
                }
                PinPadHost(pinPrompt)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!kioskExited) {
            hideSystemBars()
            pinToScreen()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !kioskExited) hideSystemBars()
    }

    // Every touch-down anywhere (shell, Settings, PIN pad) keeps the PIN session alive.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) shell.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }
}
```

- [ ] **Step 6: Build and run the whole test suite**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 7: Verify on the tablet**

Run: `./gradlew :app:installDebug && adb shell am start -n uk.co.siland.househub/.MainActivity`

Check each, and report any that fail:
1. Full screen, no system bars; screen stays on. Swipe the edge: bars appear briefly, then hide again. Back does nothing.
2. Status bar shows time and "Auto"; tapping it flips the theme with a smooth ~400 ms fade and the label becomes "Day"/"Night"; tapping again returns to "Auto".
3. Rail shows Home (selected: filled icon in green pill) and Settings at the bottom.
4. Home shows the big clock and date ("Wednesday 23 September" style) with an empty grid below.
5. Tap Settings → PIN pad with 4 dots. `0000` → "Wrong PIN" (submits on the 4th digit). `1234` → Settings opens; rail shows an "Admin" chip.
6. In Settings, tap empty space: nothing happens (no rail tab switch). Tap Exit kiosk → PIN pad appears even though the session is active; Cancel.
7. Leave the tablet untouched for 60 s with Settings open → Settings closes and the chip disappears.
8. Tap the chip → it disappears immediately.
9. Side by side with `docs/design/house_hub_handoff/screenshots/01-home-dark.png`: compare rail width and spacing, clock size/weight and gap to the date, colours. Note any mismatch in your report.

If no device is connected, say so; Step 6 is the gate.

- [ ] **Step 8: Commit**

```bash
git add app
git commit -m "Add shell UI, kiosk mode and debug admin seed"
```

---

### Task 11: README

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write `README.md`**

````markdown
# House Hub

A wall-mounted Android tablet app for running a family home. Built as a set of modules so new services (calendars, lights, cameras…) plug in without touching the rest of the app, and nothing about a particular household is baked in.

- Design reference: `docs/design/house_hub_handoff/`
- Spec: `docs/superpowers/specs/2026-09-23-house-hub-v1-design.md`
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

1. Create `capability/<name>/build.gradle.kts` with `id("househub.android.library")`, `id("househub.android.compose")`, `id("househub.hilt")`, and depend on `:core:plugin` (plus `:core:access` if it has actions).
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

**Forgotten PIN:** an Admin can reset anyone's PIN in Settings. If every Admin has forgotten theirs, clear the app's data (Android Settings › Apps › House Hub › Storage › Clear data). That wipes all configuration and starts setup again.

## Licences

DM Sans: SIL Open Font License (`core/ui/licenses/OFL-DMSans.txt`). Material Symbols: Apache 2.0 (`core/ui/licenses/Apache-MaterialSymbols.txt`).
````

- [ ] **Step 2: Final check**

Run: `./gradlew assembleDebug testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "Add README"
```
