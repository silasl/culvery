plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    // Startable and PermissionSource appear in this module's public API.
    api(project(":core:plugin"))
    // The seams in its public API.
    api(project(":core:house"))
    implementation(project(":core:ui"))
    implementation(project(":core:access"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
