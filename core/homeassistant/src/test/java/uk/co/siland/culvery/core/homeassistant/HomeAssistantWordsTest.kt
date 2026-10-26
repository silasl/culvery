package uk.co.siland.culvery.core.homeassistant

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 11 §6: each problem's line, verbatim. */
class HomeAssistantWordsTest {
    @Test
    fun eachProblemHasItsLine() {
        val unreachable = "Can’t reach Home Assistant. Check the NAS is on."
        assertThat(problemLine(HaProblem.UNREACHABLE)).isEqualTo(unreachable)
        assertThat(problemLine(HaProblem.NOT_HOME_ASSISTANT)).isEqualTo(unreachable)
        assertThat(problemLine(HaProblem.REFUSED)).isEqualTo(unreachable)
        assertThat(problemLine(HaProblem.OFFLINE)).isEqualTo("The tablet is offline. Check the Wi-Fi.")
        assertThat(problemLine(HaProblem.TOKEN_REFUSED))
            .isEqualTo("Home Assistant no longer accepts Culvery’s token. Add a new one in Settings › Other integrations.")
        assertThat(problemLine(HaProblem.BLOCKED))
            .isEqualTo("Home Assistant has blocked the tablet. Unblock its address in Home Assistant.")
    }

    @Test
    fun notLinkedSaysWhereToLinkIt() {
        assertThat(NOT_LINKED).isEqualTo("Home Assistant isn’t linked. Link it in Settings › Other integrations.")
    }

    /** Fails if the order changes: the link's problem first, then a gone entity, then an unreachable one. */
    @Test
    fun anEntitysTroubleComesInOrder() {
        val id = "vacuum.hoover"
        val unavailable = EntityState(id, "unavailable", emptyMap(), emptyMap())
        fun trouble(entities: Map<String, EntityState?>, problem: HaProblem? = null) = entityTrouble(id, entities, problem, "gone", "unreachable")

        assertThat(trouble(mapOf(id to null), HaProblem.OFFLINE)).isEqualTo(problemLine(HaProblem.OFFLINE))
        assertThat(trouble(mapOf(id to null))).isEqualTo("gone")
        assertThat(trouble(mapOf(id to unavailable))).isEqualTo("unreachable")
        assertThat(trouble(mapOf(id to unavailable.copy(state = "docked")))).isNull()
        assertThat(trouble(emptyMap())).isNull()
    }
}
