plugins {
    id("culvery.android.library")
    id("culvery.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":capability:lights"))
    implementation(project(":core:net"))
    implementation(project(":core:ui"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
}
