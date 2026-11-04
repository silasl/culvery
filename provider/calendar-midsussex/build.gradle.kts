plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:calendar"))
    implementation(project(":core:net"))
    implementation(project(":core:plugin"))
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    testImplementation(project(":capability:calendar-testkit"))
    testImplementation(libs.okhttp.mockwebserver)
}
