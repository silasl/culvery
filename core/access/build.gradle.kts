plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
}

dependencies {
    testImplementation(testFixtures(project(":core:ui")))
    testImplementation(testFixtures(project(":core:plugin")))
    implementation(project(":core:ui"))
    api(project(":core:plugin"))
    api(project(":core:household"))
}
