plugins {
    id("culvery.android.library")
    id("culvery.android.compose")
}

dependencies {
    api(project(":capability:calendar"))
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // Deliberately broken providers: ContractSuiteSelfTest runs them through JUnitCore and expects failures.
    exclude("**/fixtures/**")
}
