plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":capability:shopping"))
    implementation(project(":core:google"))
    implementation(project(":core:net"))
    implementation(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(testFixtures(project(":core:plugin")))
    testImplementation(testFixtures(project(":core:google")))
    testImplementation(libs.okhttp.mockwebserver)
    // The tests script Play services statuses and Google's screens.
    testImplementation(libs.play.services.auth)
    testImplementation(libs.androidx.activity.compose)
}
