plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
}

android {
    // FakeAccess, for every capability whose tests answer a PIN pad with a role.
    testFixtures { enable = true }
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    implementation(project(":core:ui"))
    api(project(":core:plugin"))
    api(project(":core:household"))
    // The Compose compiler runs on the fixtures too, so they need the runtime on their classpath.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
}
