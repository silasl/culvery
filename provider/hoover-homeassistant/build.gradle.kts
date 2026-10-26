plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:hoover"))
    implementation(project(":core:homeassistant"))
    implementation(project(":core:plugin"))
    testImplementation(testFixtures(project(":core:homeassistant")))
    testImplementation(testFixtures(project(":core:plugin")))
    testImplementation(libs.androidx.datastore.preferences)
}
