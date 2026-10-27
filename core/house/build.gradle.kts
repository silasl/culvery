plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    // Authorised appears in HouseLights' signature.
    api(project(":core:access"))
    api(libs.kotlinx.coroutines.core)
}
