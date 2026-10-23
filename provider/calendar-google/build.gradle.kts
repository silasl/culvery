plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":capability:calendar"))
    implementation(project(":core:google"))
    implementation(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":capability:calendar-testkit"))
    testImplementation(testFixtures(project(":core:plugin")))
    testImplementation(testFixtures(project(":core:google")))
    testImplementation(libs.okhttp.mockwebserver)
    // The tests script Play services statuses and Google's screens.
    testImplementation(libs.play.services.auth)
    testImplementation(libs.androidx.activity.compose)
}
