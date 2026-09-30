plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
}

dependencies {
    // SingleAction takes a CoroutineScope in its public API.
    api(libs.kotlinx.coroutines.core)
}
