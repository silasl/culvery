plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":capability:door"))
    implementation(project(":core:plugin"))
    implementation(project(":core:ui"))
    implementation(libs.androidx.core.ktx)
}
