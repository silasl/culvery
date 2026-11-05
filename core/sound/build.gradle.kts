plugins {
    id("culvery.android.library")
    id("culvery.hilt")
}

dependencies {
    // BuiltInSound.theme is a SeasonalTheme, in this module's public API.
    api(project(":core:ui"))
}
