plugins {
    id("culvery.android.application")
    id("culvery.android.compose")
    id("culvery.hilt")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "uk.co.siland.culvery"
    defaultConfig {
        applicationId = "uk.co.siland.culvery"
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures.buildConfig = true
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:plugin"))
    implementation(project(":core:household"))
    implementation(project(":core:access"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
