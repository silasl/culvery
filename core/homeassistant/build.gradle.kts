plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // The fake Home Assistant and the cache builders, for every provider that reads it.
    testFixtures { enable = true }
}

dependencies {
    // Capability, SettingsPage and PermissionSource appear in this module's public API.
    api(project(":core:plugin"))
    // HttpUrl is part of the link's public type.
    api(project(":core:net"))
    implementation(project(":core:ui"))
    implementation(project(":core:access"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    // The Compose compiler runs on the fixtures too, so they need the runtime on their classpath.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
    testFixturesImplementation(libs.truth)
    testFixturesImplementation(libs.robolectric)
    testFixturesImplementation(libs.androidx.test.core)
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
