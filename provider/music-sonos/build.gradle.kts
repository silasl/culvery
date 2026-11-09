plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:music"))
    implementation(project(":core:ui"))
    implementation(project(":core:net"))
    implementation(project(":core:plugin"))
    testImplementation(libs.okhttp.mockwebserver)
}
