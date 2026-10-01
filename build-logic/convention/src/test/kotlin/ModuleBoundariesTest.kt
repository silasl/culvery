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
