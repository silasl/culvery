pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Culvery"
include(":app")
include(":core:ui")
include(":core:plugin")
include(":core:household")
include(":core:access")
include(":capability:calendar")
include(":capability:calendar-testkit")
include(":provider:calendar-fake")
