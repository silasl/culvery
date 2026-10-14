package uk.co.siland.culvery.shell.ui

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** The rail's mark is the launcher icon's house and dove (5 §A.1): one drawing, so they can't drift apart. */
class CulveryMarkTest {
    // Unit tests run in the module's directory.
    private val launcher = File("src/main/res/drawable/ic_launcher_foreground.xml").readText(Charsets.UTF_8)

    private val paths = Regex("""android:pathData="([^"]+)"""").findAll(launcher).map { it.groupValues[1] }.toList()

    @Test
    fun theMarkDrawsTheLauncherIconsTwoPaths() {
        assertThat(paths).containsExactly(MARK_HOUSE, MARK_DOVE).inOrder()
    }
}
