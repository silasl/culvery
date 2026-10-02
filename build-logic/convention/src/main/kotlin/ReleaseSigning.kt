/** Release signing from the user's own `~/.gradle/gradle.properties` (4c design D4, §3.1). No value is ever logged. */
object ReleaseSigning {
    const val STORE_FILE = "culvery.release.storeFile"
    const val STORE_PASSWORD = "culvery.release.storePassword"
    const val KEY_ALIAS = "culvery.release.keyAlias"
    const val KEY_PASSWORD = "culvery.release.keyPassword"
    val KEYS = listOf(STORE_FILE, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)

    const val MESSAGE = "Release signing isn't set up: add culvery.release.storeFile, storePassword, keyAlias and keyPassword " +
        "to ~/.gradle/gradle.properties (see docs/setup/release.md)."

    /** The properties with no value, in [KEYS] order; a blank value counts as none. */
    fun missing(values: Map<String, String?>): List<String> = KEYS.filter { values[it].isNullOrBlank() }
}
