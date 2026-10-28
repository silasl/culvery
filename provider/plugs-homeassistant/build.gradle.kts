plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:plugs"))
    implementation(project(":core:homeassistant"))
    testImplementation(testFixtures(project(":core:homeassistant")))
    testImplementation(testFixtures(project(":core:plugin")))
    testImplementation(project(":core:access"))
    testImplementation(libs.androidx.datastore.preferences)
}
