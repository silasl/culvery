plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

dependencies {
    // SetupStep, SettingsPage, AccessControl and the household's types appear in this module's public API.
    api(project(":core:plugin"))
    api(project(":core:access"))
    api(project(":core:household"))
    implementation(project(":core:ui"))
    // SetupState's constructor takes a DataStore, which `:app`'s debug tests build.
    api(libs.androidx.datastore.preferences)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
