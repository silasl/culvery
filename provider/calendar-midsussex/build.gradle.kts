plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":capability:calendar"))
    implementation(project(":core:net"))
    implementation(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(project(":capability:calendar-testkit"))
    testImplementation(libs.okhttp.mockwebserver)
}
