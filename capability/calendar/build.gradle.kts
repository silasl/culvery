plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
    id("culvery.hilt")
    id("culvery.room")
}

dependencies {
    api(project(":core:plugin"))
    // PersonId and Person appear in this module's public API (SourceMapping, EventUi).
    api(project(":core:household"))
}
