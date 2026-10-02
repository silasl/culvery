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
    implementation(project(":core:ui"))
    implementation(project(":core:plugin"))
    implementation(project(":core:household"))
    implementation(project(":core:access"))
    implementation(project(":core:setup"))
    implementation(project(":capability:calendar"))
    // Wired by Hilt alone: :app's own code never names it (4b design D6).
    implementation(project(":capability:weather"))
    implementation(project(":provider:calendar-google"))
    implementation(project(":provider:weather-openmeteo"))
    // Sample data only, in debug builds.
    debugImplementation(project(":provider:calendar-fake"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
