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
    testImplementation(testFixtures(project(":core:access")))
    // Capability, RailBadge, AlertHost and Startable appear in this module's public API.
    api(project(":core:plugin"))
    implementation(project(":core:house"))
    implementation(project(":core:household"))
    implementation(project(":core:access"))
    implementation(project(":core:ui"))
    implementation(project(":core:sound"))
    implementation(project(":core:setup"))
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
