import org.gradle.api.tasks.PathSensitivity

plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    alias(libs.plugins.roborazzi)
}

android {
    // The shared semantics helper every module's UI tests call (4d-2 §8.2).
    testFixtures { enable = true }
}

dependencies {
    // SingleAction takes a CoroutineScope in its public API.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testFixturesImplementation(platform(libs.androidx.compose.bom))
    testFixturesImplementation(libs.androidx.compose.ui.test.junit4)
}

// IconFontTest reads tools/fonts/icons.txt and every module's main sources.
tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootDir) { include("tools/fonts/icons.txt", "*/src/main/**/*.kt", "*/*/src/main/**/*.kt") })
        .withPropertyName("iconSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
