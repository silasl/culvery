plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:music"))
    implementation(project(":core:ui"))
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
