plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:heating"))
    implementation(project(":core:homeassistant"))
    testImplementation(testFixtures(project(":core:homeassistant")))
    testImplementation(testFixtures(project(":core:plugin")))
    testImplementation(libs.androidx.datastore.preferences)
}
