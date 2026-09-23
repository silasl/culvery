plugins {
    id("househub.android.library")
    id("househub.android.compose")
    id("househub.hilt")
}

dependencies {
    implementation(project(":core:ui"))
    api(project(":core:plugin"))
    api(project(":core:household"))
}
