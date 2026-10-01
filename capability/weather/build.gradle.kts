plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
    alias(libs.plugins.roborazzi)
}

dependencies {
    // Capability, HeaderItem, Daylight, SunTimes and ProviderDescriptor appear in this module's public API.
    api(project(":core:plugin"))
    // HomeLocation and HouseholdZone appear in this module's public API (weatherView, WeatherRepository).
    api(project(":core:household"))
    implementation(project(":core:ui"))
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
}
