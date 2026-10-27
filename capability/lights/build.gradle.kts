plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    // Capability, ProviderDescriptor and PermissionSource appear in this module's public API.
    api(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(project(":core:access"))
    implementation(project(":core:house"))
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
