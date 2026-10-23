plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
    alias(libs.plugins.roborazzi)
}

android {
    // SampleShoppingProvider, for this module's tests and for the Tasks provider's and the debug app's.
    testFixtures { enable = true }
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    // Capability, ProviderDescriptor, Connection and the sign-in exceptions appear in this module's public API.
    api(project(":core:plugin"))
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:household"))
    implementation(project(":core:ui"))
    implementation(project(":core:access"))
    implementation(libs.androidx.datastore.preferences)
    // The Compose compiler runs on the fixtures too, so they need the runtime on their classpath; ConnectScreen is a composable.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
    testFixturesImplementation(project(":core:ui"))
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
