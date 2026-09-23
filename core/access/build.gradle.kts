plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
}

dependencies {
    implementation(project(":core:ui"))
    api(project(":core:plugin"))
    api(project(":core:household"))
}
