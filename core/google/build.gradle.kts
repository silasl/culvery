plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    // FakeAuthorizer, FakeTokenSource, FakeGoogleAccounts and the log and screens helpers, for every module that signs in to Google.
    testFixtures { enable = true }
}

dependencies {
    api(project(":core:plugin"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.activity.compose)
    // The Compose compiler runs on the fixtures too, so they need the runtime on their classpath.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
    testFixturesImplementation(libs.truth)
    testFixturesImplementation(libs.robolectric)
    testFixturesImplementation(libs.androidx.test.core)
    testImplementation(testFixtures(project(":core:plugin")))
    testImplementation(libs.okhttp.mockwebserver)
}
