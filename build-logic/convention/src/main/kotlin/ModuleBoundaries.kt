import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

/**
 * Spec §4 dependency rules. A module's family is its name up to the first '-':
 * ":provider:calendar-google" and ":capability:calendar-testkit" are both family "calendar".
 */
object ModuleBoundaries {
    fun violation(from: String, to: String, configuration: String): String? {
        // AGP wires every Android library's androidTest variant with a ProjectDependency on itself
        // (e.g. debugAndroidTestCompileClasspath); that's not a real cross-module edge to police.
        val allowed = if (to == from) true else when (kind(from)) {
            "core" -> kind(to) == "core"
            "capability" -> kind(to) == "core" || (kind(to) == "capability" && family(to) == family(from))
            "provider" -> kind(to) == "core" ||
                to == ":capability:${family(from)}" ||
                (to == ":capability:${family(from)}-testkit" && isTestOnly(configuration))
            else -> true
        }
        return if (allowed) null else "Module boundary: $from must not depend on $to (in '$configuration'). See README › Modules."
    }

    private fun kind(path: String) = path.removePrefix(":").substringBefore(':')

    private fun family(path: String) = path.substringAfterLast(':').substringBefore('-')

    private fun isTestOnly(configuration: String) =
        configuration.startsWith("test") || configuration.startsWith("androidTest")
}

/** Fails configuration as soon as a forbidden project(...) dependency is declared. */
internal fun Project.enforceModuleBoundaries() {
    val from = path
    configurations.configureEach {
        val configuration = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            // ProjectDependency.path (Gradle 8.11+); dependencyProject is deprecated.
            val to = this.path
            ModuleBoundaries.violation(from, to, configuration)?.let { throw GradleException(it) }
        }
    }
}
