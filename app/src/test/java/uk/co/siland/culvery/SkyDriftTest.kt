package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.display.Screen

class SkyDriftTest {
    @Test
    fun theSkyDriftsOnlyAwakeUnheldWithAnimations() {
        val combinations = Screen.entries.flatMap { screen ->
            listOf(false, true).flatMap { held -> listOf(false, true).map { on -> Triple(screen, held, on) } }
        }
        assertThat(combinations).hasSize(12)
        val drifting = combinations.filter { (screen, held, on) -> skyDrifts(screen, held, on) }
        assertThat(drifting).containsExactly(Triple(Screen.AWAKE, false, true))
    }
}
