plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
}

android {
    // FakeAuthorizer, FakeTokenSource and FakeGoogleAccounts, for every module that signs in to Google.
    testFixtures { enable = true }
}

dependencies {
    api(project(":core:plugin"))
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.activity.compose)
    // The Compose compiler runs on the fixtures too, so they need the runtime on their classpath.
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui)
    testImplementation(testFixtures(project(":core:plugin")))
}
