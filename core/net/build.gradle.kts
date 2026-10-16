plugins {
    id("culvery.android.library")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
