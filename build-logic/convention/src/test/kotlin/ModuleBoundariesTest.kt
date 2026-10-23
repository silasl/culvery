import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModuleBoundariesTest {
    private fun allowed(from: String, to: String, configuration: String = "implementation") =
        assertThat(ModuleBoundaries.violation(from, to, configuration)).isNull()

    private fun banned(from: String, to: String, configuration: String = "implementation") =
        assertThat(ModuleBoundaries.violation(from, to, configuration)).isNotNull()

    @Test
    fun coreMayDependOnCore() = allowed(":core:access", ":core:household", "api")

    @Test
    fun coreMayNotDependOnAppCapabilityOrProvider() {
        banned(":core:ui", ":app")
        banned(":core:plugin", ":capability:calendar")
        banned(":core:plugin", ":provider:calendar-fake", "testImplementation")
    }

    @Test
    fun capabilityMayDependOnCore() = allowed(":capability:calendar", ":core:household", "api")

    @Test
    fun capabilityMayNotDependOnAProviderEvenInTests() {
        banned(":capability:calendar", ":provider:calendar-fake")
        banned(":capability:calendar", ":provider:calendar-fake", "testImplementation")
    }

    @Test
    fun capabilityMayNotDependOnAnotherCapability() = banned(":capability:calendar", ":capability:weather")

    @Test
    fun testkitMayDependOnItsOwnCapability() = allowed(":capability:calendar-testkit", ":capability:calendar", "api")

    @Test
    fun providerMayDependOnCoreAndItsOwnCapability() {
        allowed(":provider:calendar-fake", ":core:ui")
        allowed(":provider:calendar-fake", ":capability:calendar")
    }

    @Test
    fun providerMayUseItsTestkitOnlyInTests() {
        allowed(":provider:calendar-fake", ":capability:calendar-testkit", "testImplementation")
        allowed(":provider:calendar-fake", ":capability:calendar-testkit", "testDebugImplementation")
        banned(":provider:calendar-fake", ":capability:calendar-testkit", "implementation")
    }

    @Test
    fun providerMayNotDependOnOtherCapabilitiesOrProviders() {
        banned(":provider:calendar-fake", ":capability:weather")
        banned(":provider:weather-openmeteo", ":capability:calendar")
        banned(":provider:calendar-google", ":provider:calendar-fake")
        banned(":provider:calendar-fake", ":app")
    }

    @Test
    fun theWeatherModulesFollowTheRules() {
        allowed(":capability:weather", ":core:household", "api")
        allowed(":capability:weather", ":core:ui")
        allowed(":provider:weather-openmeteo", ":capability:weather")
        allowed(":app", ":capability:weather")
        banned(":capability:weather", ":provider:weather-openmeteo")
        banned(":capability:weather", ":provider:weather-openmeteo", "testImplementation")
        banned(":capability:weather", ":capability:calendar")
        banned(":provider:calendar-google", ":capability:weather")
        banned(":core:plugin", ":capability:weather")
    }

    @Test
    fun theDoorModulesFollowTheRules() {
        allowed(":capability:door", ":core:plugin", "api")
        allowed(":capability:door", ":core:ui")
        allowed(":provider:door-ring", ":capability:door")
        allowed(":app", ":provider:door-ring")
        banned(":capability:door", ":provider:door-ring")
        banned(":capability:door", ":provider:door-ring", "testImplementation")
        banned(":capability:door", ":capability:calendar")
        banned(":provider:door-ring", ":capability:weather")
        banned(":provider:calendar-google", ":capability:door")
    }

    @Test
    fun theMusicModulesFollowTheRules() {
        allowed(":capability:music", ":core:plugin", "api")
        allowed(":capability:music", ":core:ui")
        allowed(":provider:music-sonos", ":capability:music")
        allowed(":app", ":provider:music-sonos")
        banned(":capability:music", ":provider:music-sonos")
        banned(":capability:music", ":provider:music-sonos", "testImplementation")
        banned(":capability:music", ":capability:door")
        banned(":capability:door", ":capability:music")
        banned(":provider:music-sonos", ":capability:door")
        banned(":provider:music-sonos", ":capability:calendar")
    }

    /** Pin: the Lights modules follow the same rules as Music's. */
    @Test
    fun theLightsModulesFollowTheRules() {
        allowed(":capability:lights", ":core:plugin", "api")
        allowed(":capability:lights", ":core:access")
        allowed(":provider:lights-hue", ":capability:lights")
        allowed(":app", ":provider:lights-hue")
        banned(":capability:lights", ":provider:lights-hue")
        banned(":capability:lights", ":capability:music")
        banned(":capability:music", ":capability:lights")
        banned(":provider:lights-hue", ":capability:music")
        banned(":provider:lights-hue", ":provider:music-sonos")
    }

    /** Pin: the Shopping pair follows the Lights rules, and the Google sign-in is core, so the Tasks provider may use it. */
    @Test
    fun theShoppingPairKeepsToItsFamily() {
        allowed(":provider:shopping-google-tasks", ":capability:shopping")
        allowed(":provider:shopping-google-tasks", ":core:google")
        banned(":provider:shopping-google-tasks", ":provider:calendar-google")
        banned(":provider:shopping-google-tasks", ":capability:calendar")
        banned(":capability:shopping", ":capability:calendar")
    }

    @Test
    fun theNetworkModuleIsCore() {
        allowed(":provider:music-sonos", ":core:net")
        allowed(":provider:lights-hue", ":core:net")
        banned(":core:net", ":capability:music")
    }

    @Test
    fun appMayDependOnAnything() = allowed(":app", ":provider:calendar-fake", "debugImplementation")

    @Test
    fun aModuleMayDependOnItself() {
        // AGP wires every Android library's androidTest variant with a ProjectDependency on itself.
        allowed(":provider:calendar-fake", ":provider:calendar-fake", "debugAndroidTestCompileClasspath")
        allowed(":core:ui", ":core:ui", "debugAndroidTestCompileClasspath")
        allowed(":capability:calendar", ":capability:calendar", "debugAndroidTestCompileClasspath")
    }

    @Test
    fun messageNamesBothModulesAndTheConfiguration() {
        assertThat(ModuleBoundaries.violation(":core:ui", ":app", "implementation"))
            .isEqualTo("Module boundary: :core:ui must not depend on :app (in 'implementation'). See README › Modules.")
    }
}
