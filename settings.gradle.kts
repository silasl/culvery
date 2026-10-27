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
include(":core:house")
include(":core:setup")
include(":core:net")
include(":core:google")
include(":core:homeassistant")
include(":capability:calendar")
include(":capability:calendar-testkit")
include(":capability:weather")
include(":capability:door")
include(":capability:shopping")
include(":capability:lights")
include(":capability:hoover")
include(":capability:plugs")
include(":capability:heating")
include(":capability:holiday")
include(":capability:music")
include(":provider:calendar-fake")
include(":provider:calendar-google")
include(":provider:door-ring")
include(":provider:heating-homeassistant")
include(":provider:hoover-homeassistant")
include(":provider:lights-hue")
include(":provider:music-sonos")
include(":provider:plugs-homeassistant")
include(":provider:shopping-google-tasks")
include(":provider:weather-openmeteo")
