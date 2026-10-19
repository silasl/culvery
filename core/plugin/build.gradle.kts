plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
}

android {
    // FakeUserActivity, for every module whose timers read UserActivity.
    testFixtures { enable = true }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:household"))
    implementation(libs.androidx.datastore.preferences)
    // The Compose compiler runs on the fixtures too, so they need the runtime on their classpath.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
}
