plugins {
    id("househub.android.application")
    id("househub.android.compose")
    id("househub.hilt")
}

android {
    namespace = "uk.co.siland.househub"
    defaultConfig {
        applicationId = "uk.co.siland.househub"
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
}
