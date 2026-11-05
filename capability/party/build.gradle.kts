plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    testImplementation(testFixtures(project(":core:access")))
    // Startable and PermissionSource appear in this module's public API.
    api(project(":core:plugin"))
    // The seams in its public API.
    api(project(":core:house"))
    // Sound, in PadUi.
    api(project(":core:sound"))
    implementation(project(":core:ui"))
    implementation(project(":core:access"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
