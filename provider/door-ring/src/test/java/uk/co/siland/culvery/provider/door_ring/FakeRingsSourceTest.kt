package uk.co.siland.culvery.provider.door_ring

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** The unit tests run the debug variant; this reads the release one, which must never read the adb shell's fakes (5 §7). */
class FakeRingsSourceTest {
    @Test
    fun releaseBuildsHaveNoFakeRings() {
        // Unit tests run in the module's directory.
        val release = File("src/release/java/uk/co/siland/culvery/provider/door_ring/FakeRings.kt").readText(Charsets.UTF_8)
        assertThat(release).contains("const val ENABLED = false")
    }
}
