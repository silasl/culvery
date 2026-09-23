package uk.co.siland.househub.shell

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import uk.co.siland.househub.core.access.Authorised
import uk.co.siland.househub.core.access.CorePermissions
import uk.co.siland.househub.core.access.Identified
import uk.co.siland.househub.core.household.Person
import uk.co.siland.househub.core.household.PersonId
import uk.co.siland.househub.core.household.Role
import uk.co.siland.househub.core.plugin.Capability
import uk.co.siland.househub.core.plugin.HomeCard
import uk.co.siland.househub.core.plugin.HomeCardSize

class ShellViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val noon = LocalDateTime.of(2026, 9, 23, 12, 0)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val admin = Authorised(alex, Role.ADMIN, setOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.KIOSK_EXIT))
    private val access = FakeAccessControl()
    private val ticks = MutableStateFlow(noon)

    private fun vm(caps: Set<Capability> = emptySet()) = ShellViewModel(caps, { ticks }, access)

    @Test
    fun tabsShowOnlyCapabilitiesWithTabsInOrder() = runTest {
        val vm = vm(
            setOf(
                FakeCapability("weather", order = 90, shown = false),
                FakeCapability("lights", order = 20, shown = true),
                FakeCapability("calendar", order = 10, shown = true),
            ),
        )
        vm.uiState.test {
            assertThat(expectMostRecentItem().tabs.map { it.id }).containsExactly("calendar", "lights").inOrder()
        }
    }

    @Test
    fun noCapabilitiesGivesHomeOnlyAndTracksTheClock() = runTest {
        vm().uiState.test {
            val s = expectMostRecentItem()
            assertThat(s.tabs).isEmpty()
            assertThat(s.selectedTabId).isEqualTo(HOME_TAB_ID)
            assertThat(s.now).isEqualTo(noon)
            assertThat(s.dark).isFalse()
        }
    }

    @Test
    fun selectedTabFallsBackToHomeWhenItDisappears() = runTest {
        val calendar = FakeCapability("calendar", order = 10, shown = true)
        val vm = vm(setOf(calendar))
        vm.uiState.test {
            vm.selectTab("calendar")
            assertThat(expectMostRecentItem().selectedTabId).isEqualTo("calendar")
            calendar.shownFlow.value = false
            assertThat(expectMostRecentItem().selectedTabId).isEqualTo(HOME_TAB_ID)
        }
    }

    @Test
    fun homeCardsArePlacedFromAllCapabilities() = runTest {
        val today = HomeCard("today", HomeCardSize.TALL, 100) {}
        val forecast = HomeCard("forecast", HomeCardSize.WIDE, 10) {}
        val vm = vm(
            setOf(
                FakeCapability("calendar", 10, true, listOf(today)),
                FakeCapability("weather", 90, false, listOf(forecast)),
            ),
        )
        vm.uiState.test {
            assertThat(expectMostRecentItem().homeCards.map { it.card.id }).containsExactly("today", "forecast")
        }
    }

    @Test
    fun settingsOpenOnlyWhenAuthorised() = runTest {
        val vm = vm()
        vm.uiState.test {
            assertThat(expectMostRecentItem().settingsOpen).isFalse()
            vm.openSettings()
            expectNoEvents()
            assertThat(access.requested.last()).containsExactly(CorePermissions.SETTINGS_MANAGE)

            access.result = admin
            vm.openSettings()
            assertThat(expectMostRecentItem().settingsOpen).isTrue()

            vm.closeSettings()
            assertThat(expectMostRecentItem().settingsOpen).isFalse()
        }
    }

    @Test
    fun settingsCloseWhenSessionEnds() = runTest {
        val vm = vm()
        access.result = admin
        vm.uiState.test {
            vm.openSettings()
            assertThat(expectMostRecentItem().settingsOpen).isTrue()
            access.session.value = null
            assertThat(expectMostRecentItem().settingsOpen).isFalse()
        }
    }

    @Test
    fun exitKioskEmitsOnlyWhenAuthorised() = runTest {
        val vm = vm()
        vm.kioskExit.test {
            vm.exitKiosk()
            expectNoEvents()
            access.result = admin
            vm.exitKiosk()
            awaitItem()
        }
    }

    @Test
    fun sessionShowsAsChip() = runTest {
        val vm = vm()
        vm.uiState.test {
            access.session.value = Identified(alex, Role.ADMIN)
            assertThat(expectMostRecentItem().session).isEqualTo(SessionChip("Alex", 0xFF4CB387))
            vm.lockSession()
            assertThat(expectMostRecentItem().session).isNull()
        }
    }

    @Test
    fun themePreviewEndsWhenScheduleFlips() = runTest {
        val vm = vm()
        vm.uiState.test {
            assertThat(expectMostRecentItem().dark).isFalse()
            vm.toggleThemePreview()
            expectMostRecentItem().let {
                assertThat(it.dark).isTrue()
                assertThat(it.previewing).isTrue()
            }
            ticks.value = noon.withHour(20)
            expectMostRecentItem().let {
                assertThat(it.dark).isTrue()
                assertThat(it.previewing).isFalse()
            }
            ticks.value = noon.plusDays(1)
            assertThat(expectMostRecentItem().dark).isFalse()
        }
    }

    @Test
    fun userActivityTouchesSession() {
        vm().onUserActivity()
        assertThat(access.touches).isEqualTo(1)
    }
}
