plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
    alias(libs.plugins.roborazzi)
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    // Capability, RailBadge, AlertHost, ProviderDescriptor and Startable appear in this module's public API.
    api(project(":core:plugin"))
    implementation(project(":core:house"))
    implementation(project(":core:household"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
