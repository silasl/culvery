plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":capability:calendar"))
    implementation(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(project(":capability:calendar-testkit"))
    testImplementation(libs.okhttp.mockwebserver)
}
