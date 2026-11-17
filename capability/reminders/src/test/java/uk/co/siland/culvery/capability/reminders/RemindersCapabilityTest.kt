package uk.co.siland.culvery.capability.reminders

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import uk.co.siland.culvery.core.plugin.RailBadge

class RemindersCapabilityTest {
    private val waiting = MutableStateFlow<List<Due>>(emptyList())
    private val setup = MutableStateFlow(false)
    private val done = mutableListOf<DueKey>()
    private val capability = RemindersCapability(setup, waiting, flowOf(TabUi(emptyList(), emptyList(), emptyList(), null))) { done += it }

    private fun due(id: Long, snoozedUntil: Long? = null) = Due(DueKey.Mine(id), 1_000, snoozedUntil, null)

    @Test
    fun theOrderIsTwentyFive() {
        assertThat(listOf(capability.id, capability.label, capability.icon, capability.order))
            .containsExactly("reminders", "Reminders", "alarm", 25).inOrder()
        assertThat(REMINDERS_ID).isEqualTo("reminders")
    }

    @Test
    fun theTabAppearsWhenSetupIsComplete() = runTest {
        assertThat(capability.hasTab.first()).isFalse()
        setup.value = true
        assertThat(capability.hasTab.first()).isTrue()
    }

    @Test
    fun theBadgeCountsSnoozedOnesToo() = runTest {
        waiting.value = listOf(due(1), due(2, snoozedUntil = 2_000))
        assertThat(capability.badge.first()).isEqualTo(RailBadge.Count(2, "2 waiting"))
    }

    @Test
    fun noBadgeWithNothingWaiting() = runTest {
        assertThat(capability.badge.first()).isNull()
        assertThat(badgeOf(0)).isNull()
    }
}
