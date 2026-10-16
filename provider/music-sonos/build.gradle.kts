plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:music"))
    implementation(project(":core:ui"))
    implementation(project(":core:net"))
    testImplementation(libs.okhttp.mockwebserver)
}
