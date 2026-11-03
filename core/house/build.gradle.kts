plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    // Authorised appears in HouseLights' signature.
    api(project(":core:access"))
    // Person appears in TodaysBirthdays' signature.
    api(project(":core:household"))
    api(libs.kotlinx.coroutines.core)
}
