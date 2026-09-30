plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
    alias(libs.plugins.roborazzi)
}

android {
    // MigrationTestHelper reads the exported schema JSON as assets. Robolectric reads the variant's assets, not
    // test assets, so the schemas are debug assets: a few KB in the debug APK, nothing in release.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}

dependencies {
    api(project(":core:plugin"))
    // PersonId and Person appear in this module's public API (SourceMapping, EventUi).
    api(project(":core:household"))
    implementation(project(":core:ui"))
    implementation(project(":core:access"))
    testImplementation(libs.roborazzi.core)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.room.testing)
    // CalendarMigrationTest drives MigrationTestHelper with the framework SQLite driver directly, working
    // around a Windows path-separator bug in Room's default SupportSQLiteOpenHelper-backed driver (see
    // Migrations.kt).
    testImplementation(libs.androidx.sqlite.framework)
    // Settings' frame and Kiosk page, for the Calendars page's screenshot.
    testImplementation(project(":core:setup"))
}
