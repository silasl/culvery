package uk.co.siland.culvery.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Optional
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardSize
import uk.co.siland.culvery.core.plugin.ShellNavigator
import uk.co.siland.culvery.core.plugin.SunTimes

// Robolectric for android.util.Log: a failing capability flow is logged before it is retried.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShellViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val noon = LocalDateTime.of(2026, 9, 23, 12, 0)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val admin = Authorised(alex, Role.ADMIN, setOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.KIOSK_EXIT))
    private val access = FakeAccessControl()
    private val ticks = MutableStateFlow(noon)

    /** A tick that leaves the theme as it was doesn't re-emit [ShellViewModel.uiState], so read the current value. */
    private fun TestScope.darkNow(vm: ShellViewModel): Boolean {
        runCurrent()
        return vm.uiState.value.dark
    }

    private fun vm(caps: Set<Capability> = emptySet(), daylight: Daylight? = null) =
        ShellViewModel(caps, { ticks }, access, Optional.ofNullable(daylight))

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
        val vm = vm()
        vm.uiState.test {
            val s = expectMostRecentItem()
            assertThat(s.tabs).isEmpty()
            assertThat(s.selectedTabId).isEqualTo(HOME_TAB_ID)
            assertThat(vm.now.value).isEqualTo(noon)
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
    fun settingsNeverOpenWithoutSession() = runTest {
        val noSessionAccess = FakeAccessControl(result = admin, startsSession = false)
        val vm = ShellViewModel(emptySet(), { ticks }, noSessionAccess, Optional.empty())
        vm.uiState.test {
            vm.openSettings()
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
    fun exitKioskClosesSettingsAndLocksSession() = runTest {
        val vm = vm()
        access.result = admin
        vm.uiState.test {
            vm.openSettings()
            assertThat(expectMostRecentItem().settingsOpen).isTrue()
            vm.exitKiosk()
            expectMostRecentItem().let {
                assertThat(it.settingsOpen).isFalse()
                assertThat(it.session).isNull()
            }
        }
        assertThat(access.session.value).isNull()
    }

    @Test
    fun cancelledExitKioskLeavesSettingsOpen() = runTest {
        val vm = vm()
        access.result = admin
        vm.uiState.test {
            vm.openSettings()
            assertThat(expectMostRecentItem().settingsOpen).isTrue()
            access.result = null
            vm.exitKiosk()
            expectNoEvents()
        }
    }

    @Test
    fun sessionShowsNameAndRoleForTheStatusBar() = runTest {
        val vm = vm()
        vm.uiState.test {
            access.session.value = Identified(alex, Role.ADMIN)
            assertThat(expectMostRecentItem().session).isEqualTo(SessionUi("Alex", "Admin"))
            vm.signOut()
            assertThat(expectMostRecentItem().session).isNull()
        }
    }

    @Test
    fun roleLabelsReadAsWords() {
        assertThat(Role.entries.map(::roleLabel)).containsExactly("Admin", "Adult", "Child").inOrder()
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
    fun capabilityThatNeverEmitsDoesNotBlockShell() = runTest {
        val stuck = NeverEmittingCapability("stuck", order = 5)
        val normal = FakeCapability("calendar", order = 10, shown = true)
        val vm = vm(setOf(stuck, normal))
        access.session.value = Identified(alex, Role.ADMIN)
        vm.uiState.test {
            val state = expectMostRecentItem()
            assertThat(state.tabs.map { it.id }).containsExactly("calendar")
            assertThat(state.session).isEqualTo(SessionUi("Alex", "Admin"))
        }
    }

    @Test
    fun capabilityFlowThatThrowsIsIgnored() = runTest {
        val throwing = ThrowingCardsCapability("bad", order = 5)
        val today = HomeCard("today", HomeCardSize.TALL, 100) {}
        val normal = FakeCapability("calendar", order = 10, shown = true, listOf(today))
        val vm = vm(setOf(throwing, normal))
        vm.uiState.test {
            assertThat(expectMostRecentItem().homeCards.map { it.card.id }).containsExactly("today")
        }
    }

    @Test
    fun initialStateUsesScheduledTheme() = runTest {
        val vm = vm()
        assertThat(vm.uiState.value.dark).isFalse()
    }

    @Test
    fun openTabSelectsThatTab() = runTest {
        val vm = vm(setOf(FakeCapability("calendar", order = 10, shown = true)))
        val navigator: ShellNavigator = vm
        vm.uiState.test {
            navigator.openTab("calendar")
            assertThat(expectMostRecentItem().selectedTabId).isEqualTo("calendar")
        }
    }

    @Test
    fun openTabForAHiddenTabStaysHome() = runTest {
        val vm = vm(setOf(FakeCapability("calendar", order = 10, shown = false)))
        vm.uiState.test {
            vm.openTab("calendar")
            assertThat(expectMostRecentItem().selectedTabId).isEqualTo(HOME_TAB_ID)
        }
    }

    @Test
    fun aTabWhoseFlowFailsComesBackAfterTheRetry() = runTest {
        val vm = vm(setOf(FlakyTabCapability("calendar", order = 10)))
        vm.uiState.test {
            assertThat(expectMostRecentItem().tabs).isEmpty()
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().tabs.map { it.id }).containsExactly("calendar")
        }
    }

    @Test
    fun cardsWhoseFlowFailsComeBackAfterTheRetry() = runTest {
        val today = HomeCard("today", HomeCardSize.TALL, 100) {}
        val vm = vm(setOf(FlakyCardsCapability("calendar", order = 10, listOf(today))))
        vm.uiState.test {
            assertThat(expectMostRecentItem().homeCards).isEmpty()
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().homeCards.map { it.card.id }).containsExactly("today")
        }
    }

    @Test
    fun theShellNeverBeginsASetupSession() = runTest {
        access.result = admin
        val vm = vm()
        vm.openSettings()
        vm.closeSettings()
        vm.exitKiosk()
        vm.signOut()
        assertThat(access.setupSessionsBegun).isEqualTo(0)
    }

    private val sunrise = LocalTime.of(6, 50)
    private val sunset = LocalTime.of(19, 20)

    @Test
    fun headerItemsComeFromEveryCapabilityInTheirOrder() = runTest {
        val vm = vm(
            setOf(
                FakeCapability("climate", order = 40, shown = false, headerList = listOf(HeaderItem("climate", 20) {})),
                FakeCapability("weather", order = 60, shown = false, headerList = listOf(HeaderItem("weather", 10) {})),
            ),
        )
        vm.uiState.test {
            assertThat(expectMostRecentItem().headerItems.map { it.id }).containsExactly("weather", "climate").inOrder()
        }
    }

    @Test
    fun headerItemsWhoseFlowFailsComeBackAfterTheRetry() = runTest {
        val vm = vm(setOf(FlakyHeaderCapability("weather", order = 60, listOf(HeaderItem("weather", 10) {}))))
        vm.uiState.test {
            assertThat(expectMostRecentItem().headerItems).isEmpty()
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().headerItems.map { it.id }).containsExactly("weather")
        }
    }

    @Test
    fun theThemeTurnsDarkAtTodaysSunsetAndLightAtSunrise() = runTest {
        val vm = vm(daylight = FakeDaylight(SunTimes(sunrise, sunset)))
        vm.uiState.test {
            ticks.value = noon.with(LocalTime.of(19, 19))
            assertThat(darkNow(vm)).isFalse()
            ticks.value = noon.with(sunset)
            assertThat(darkNow(vm)).isTrue()
            ticks.value = noon.plusDays(1).with(LocalTime.of(6, 49))
            assertThat(darkNow(vm)).isTrue()
            ticks.value = noon.plusDays(1).with(sunrise)
            assertThat(darkNow(vm)).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withoutDaylightTheThemeUsesSevenAndSeven() = runTest {
        val vm = vm()
        vm.uiState.test {
            ticks.value = noon.with(LocalTime.of(18, 59))
            assertThat(darkNow(vm)).isFalse()
            ticks.value = noon.with(LocalTime.of(19, 0))
            assertThat(darkNow(vm)).isTrue()
            ticks.value = noon.plusDays(1).with(LocalTime.of(6, 59))
            assertThat(darkNow(vm)).isTrue()
            ticks.value = noon.plusDays(1).with(LocalTime.of(7, 0))
            assertThat(darkNow(vm)).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun untilTheSunTimesAreKnownTheThemeUsesSevenAndSevenThenFollowsThem() = runTest {
        val daylight = FakeDaylight(null)
        val vm = vm(daylight = daylight)
        vm.uiState.test {
            ticks.value = noon.with(LocalTime.of(19, 10))
            assertThat(expectMostRecentItem().dark).isTrue()
            daylight.sun.value = SunTimes(sunrise, sunset)
            assertThat(expectMostRecentItem().dark).isFalse()
        }
    }

    @Test
    fun sunTimesThatFailToLoadLeaveSevenAndSevenUntilTheRetryThenFollowTheSun() = runTest {
        ShadowLog.clear()
        val vm = vm(daylight = FlakyDaylight(SunTimes(sunrise, sunset)))
        vm.uiState.test {
            ticks.value = noon.with(LocalTime.of(19, 10))
            expectMostRecentItem().let {
                assertThat(vm.now.value.toLocalTime()).isEqualTo(LocalTime.of(19, 10))
                assertThat(it.dark).isTrue()
            }
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().dark).isFalse()
        }
        val logs = ShadowLog.getLogs().filter { it.tag == "ShellViewModel" }
        assertThat(logs.map { it.msg }).containsExactly("Couldn't read today's sun times (IllegalStateException); retrying")
        assertThat(logs.single().throwable).isNull()
    }

    @Test
    fun cardsAreLoadedOnceEveryCapabilityHasAnswered() = runTest {
        vm(setOf(FakeCapability("calendar", order = 10, shown = true))).uiState.test {
            assertThat(expectMostRecentItem().cardsLoaded).isTrue()
        }
        vm(setOf(FakeCapability("calendar", order = 10, shown = true), NeverEmittingCapability("lights", order = 20))).uiState.test {
            assertThat(expectMostRecentItem().cardsLoaded).isFalse()
        }
    }
}
