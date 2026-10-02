import org.gradle.api.tasks.PathSensitivity

plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
}

dependencies {
    // SingleAction takes a CoroutineScope in its public API.
    api(libs.kotlinx.coroutines.core)
}

// IconFontTest reads tools/fonts/icons.txt and every module's main sources.
tasks.withType<Test>().configureEach {
    inputs.files(fileTree(rootDir) { include("tools/fonts/icons.txt", "*/src/main/**/*.kt", "*/*/src/main/**/*.kt") })
        .withPropertyName("iconSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
