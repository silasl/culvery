plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
}

dependencies {
    // SingleAction launches on a CoroutineScope.
    implementation(libs.kotlinx.coroutines.core)
}
