plugins {
    id("culvery.android.library")
    id("culvery.hilt")
    id("culvery.room")
}

android {
    // MigrationTestHelper reads the exported schemas as assets; Robolectric reads the variant's, so debug's.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // HouseholdDatabase is part of this module's public surface (DI and in-memory test databases).
    api(libs.room.runtime)
    testImplementation(libs.room.testing)
    // The framework driver: Room's default one mismatches Robolectric's backslashed paths on Windows (CalendarMigrationTest).
    testImplementation(libs.androidx.sqlite.framework)
}
