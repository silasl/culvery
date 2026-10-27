plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    testImplementation(testFixtures(project(":core:plugin")))
    api(project(":core:plugin"))
    implementation(project(":core:house"))
    implementation(libs.androidx.datastore.preferences)
}
