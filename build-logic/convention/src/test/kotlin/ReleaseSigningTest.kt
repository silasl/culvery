import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReleaseSigningTest {
    // Not a real password: the test only checks that a value is there.
    private val all = mapOf(
        ReleaseSigning.STORE_FILE to "C:/keys/culvery-release.jks",
        ReleaseSigning.STORE_PASSWORD to "set",
        ReleaseSigning.KEY_ALIAS to "culvery",
        ReleaseSigning.KEY_PASSWORD to "set",
    )

    @Test
    fun allFourSetIsComplete() {
        assertThat(ReleaseSigning.missing(all)).isEmpty()
    }

    @Test
    fun anAbsentOrBlankValueIsMissing() {
        val partial = all - ReleaseSigning.KEY_ALIAS + (ReleaseSigning.STORE_PASSWORD to " ")
        assertThat(ReleaseSigning.missing(partial)).containsExactly(ReleaseSigning.STORE_PASSWORD, ReleaseSigning.KEY_ALIAS).inOrder()
    }
}
