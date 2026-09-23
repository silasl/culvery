plugins {
    id("househub.android.library")
    id("househub.hilt")
    id("househub.room")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // HouseholdDatabase is part of this module's public surface (DI and in-memory test databases).
    api(libs.room.runtime)
}
