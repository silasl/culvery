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
include(":core:setup")
include(":core:net")
include(":core:google")
include(":capability:calendar")
include(":capability:calendar-testkit")
include(":capability:weather")
include(":capability:door")
include(":capability:shopping")
include(":capability:lights")
include(":capability:music")
include(":provider:calendar-fake")
include(":provider:calendar-google")
include(":provider:door-ring")
include(":provider:lights-hue")
include(":provider:music-sonos")
include(":provider:weather-openmeteo")
