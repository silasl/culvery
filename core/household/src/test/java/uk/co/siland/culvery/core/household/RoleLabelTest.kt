package uk.co.siland.culvery.core.household

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoleLabelTest {
    @Test
    fun theRolesWords() {
        assertThat(Role.entries.map { it.label }).containsExactly("Admin", "Adult", "Child").inOrder()
    }
}
