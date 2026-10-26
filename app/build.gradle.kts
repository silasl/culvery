plugins {
    id("culvery.android.application")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

// Release signing from the user's own ~/.gradle/gradle.properties (4c design D4); debug builds and the tests never need it.
val releaseSigning = ReleaseSigning.KEYS.associateWith { providers.gradleProperty(it) }

android {
    namespace = "uk.co.siland.culvery"
    defaultConfig {
        applicationId = "uk.co.siland.culvery"
        versionCode = 2
        versionName = "1.0.0-beta1"
    }
    buildFeatures.buildConfig = true
    signingConfigs {
        // AGP's signing config takes plain values, so these are read while configuring, and only when all four are set.
        if (ReleaseSigning.missing(releaseSigning.mapValues { it.value.orNull }).isEmpty()) {
            create("release") {
                storeFile = file(releaseSigning.getValue(ReleaseSigning.STORE_FILE).get())
                storePassword = releaseSigning.getValue(ReleaseSigning.STORE_PASSWORD).get()
                keyAlias = releaseSigning.getValue(ReleaseSigning.KEY_ALIAS).get()
                keyPassword = releaseSigning.getValue(ReleaseSigning.KEY_PASSWORD).get()
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// A release task stops at once without the properties; it holds the providers, read only when it runs.
val checkReleaseSigning by tasks.registering {
    val properties = releaseSigning
    doLast {
        if (ReleaseSigning.missing(properties.mapValues { it.value.orNull }).isNotEmpty()) throw GradleException(ReleaseSigning.MESSAGE)
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseSigning) }

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    implementation(project(":core:ui"))
    implementation(project(":core:plugin"))
    implementation(project(":core:household"))
    implementation(project(":core:access"))
    implementation(project(":core:setup"))
    // Wired by Hilt alone: the Google sign-in the calendar and the shopping list share (10 §4.1).
    implementation(project(":core:google"))
    // Wired by Hilt alone: the Home Assistant link the hoover and the heating share (11 §4.1).
    implementation(project(":core:homeassistant"))
    implementation(project(":capability:calendar"))
    // Wired by Hilt alone: :app's own code never names it (4b design D6).
    implementation(project(":capability:weather"))
    // Wired by Hilt alone, as the weather (5 §3).
    implementation(project(":capability:door"))
    implementation(project(":provider:door-ring"))
    // Wired by Hilt alone, as the door (6 §3).
    implementation(project(":capability:music"))
    implementation(project(":provider:music-sonos"))
    // Wired by Hilt alone, as the door and music (7 §3).
    implementation(project(":capability:lights"))
    implementation(project(":provider:lights-hue"))
    // Wired by Hilt alone, as the lights (10 §4.1).
    implementation(project(":capability:shopping"))
    // Wired by Hilt alone, as the lights (11 §4.2).
    implementation(project(":capability:hoover"))
    implementation(project(":capability:heating"))
    implementation(project(":provider:hoover-homeassistant"))
    implementation(project(":provider:heating-homeassistant"))
    implementation(project(":provider:calendar-google"))
    implementation(project(":provider:shopping-google-tasks"))
    implementation(project(":provider:weather-openmeteo"))
    // Sample data only, in debug builds.
    debugImplementation(project(":provider:calendar-fake"))
    debugImplementation(testFixtures(project(":capability:shopping")))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    // The Home screenshot builds the real Shopping card over an in-memory database.
    testImplementation(libs.room.runtime)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
