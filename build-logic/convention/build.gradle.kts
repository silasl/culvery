import org.gradle.api.tasks.PathSensitivity

plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "culvery.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "culvery.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "culvery.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "culvery.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("room") {
            id = "culvery.room"
            implementationClass = "RoomConventionPlugin"
        }
    }
}

// LogHygieneTest reads the app's own sources.
tasks.test {
    inputs.files(fileTree(rootDir.parentFile) { include("*/src/main/**/*.kt", "*/*/src/main/**/*.kt", "*/src/release/**/*.kt") })
        .withPropertyName("culverySources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
