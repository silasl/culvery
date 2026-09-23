plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:calendar"))
    implementation(project(":core:plugin"))
    implementation(project(":core:ui"))
    testImplementation(project(":capability:calendar-testkit"))
}
