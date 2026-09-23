plugins {
    id("culvery.android.library")
    id("culvery.hilt")
    id("culvery.room")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    // HouseholdDatabase is part of this module's public surface (DI and in-memory test databases).
    api(libs.room.runtime)
}
