plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    // Capability, AlertQuieter, ProviderDescriptor and Startable appear in this module's public API.
    api(project(":core:plugin"))
    // HouseMusic appears in MusicHouseSeam's public API.
    api(project(":core:house"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
