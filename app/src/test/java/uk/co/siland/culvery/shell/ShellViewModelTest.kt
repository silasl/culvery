package uk.co.siland.culvery.shell

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
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
import uk.co.siland.culvery.core.plugin.HomeLayout
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.plugin.SettingsPageIds
import uk.co.siland.culvery.core.plugin.ShellNavigator
import uk.co.siland.culvery.core.plugin.SunTimes
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.shouldPin

// Robolectric for android.util.Log: a failing capability flow is logged before it is retried.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ShellViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val noon = LocalDateTime.of(2026, 9, 23, 12, 0)
    private val alex = Person(PersonId("alex"), "Alex", 0xFF4CB387)
    private val admin = Authorised(alex, Role.ADMIN, setOf(CorePermissions.SETTINGS_MANAGE, CorePermissions.KIOSK_EXIT))
    private val access = FakeAccessControl()
    private val toasts = ShellToasts()
    private val london = ZoneId.of("Europe/London")
    private val clockScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    // On the test's scheduler, so a retry of the sun times waits on virtual time.
    private val sunScope = CoroutineScope(SupervisorJob() + main.dispatcher)
    private fun at(time: LocalDateTime): Long = time.atZone(london).toInstant().toEpochMilli()
    private val ticks = MutableStateFlow(at(noon))
    private val clock = HouseholdClock(flowOf(london), WallClock { ticks.value }, clockScope, ticks)

    @After
    fun tearDown() {
        clockScope.cancel()
        sunScope.cancel()
    }

    /** A tick that leaves the theme as it was doesn't re-emit [ShellViewModel.uiState], so read the current value. */
    private fun TestScope.darkNow(vm: ShellViewModel): Boolean {
        runCurrent()
        return vm.uiState.value.dark
    }

    private fun vm(caps: Set<Capability> = emptySet(), daylight: Daylight? = null) =
        ShellViewModel(caps, clock, access, SunToday(daylight, sunScope), toasts)

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

    /** A minute's tick that crosses no theme boundary emits nothing: the time isn't part of the state the shell draws from (4c §4.2, P2). */
    @Test
    fun aMinuteTickThatChangesNothingElseEmitsNoState() = runTest {
        val vm = vm()
        vm.uiState.test {
            expectMostRecentItem()
            ticks.value = at(noon.plusMinutes(1))
            runCurrent()
            expectNoEvents()
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

    /** Fails if tapping the open tab says nothing, or tapping another tab (which opens it) also counts as a tap on the open one. */
    @Test
    fun tappingTheOpenTabSignalsOnceAndOpeningAnotherDoesNot() = runTest {
        val vm = vm(setOf(FakeCapability("calendar", order = 10, shown = true), FakeCapability("lights", order = 20, shown = true)))
        vm.tabReselected.test {
            vm.selectTab("calendar")
            vm.selectTab("lights")
            expectNoEvents()
            vm.selectTab("lights")
            awaitItem()
            expectNoEvents()
        }
    }

    @Test
    fun homeCardsArePlacedFromAllCapabilities() = runTest {
        val today = HomeCard("today", HomeCardSize.TALL, 100) {}
        val music = HomeCard("music", HomeCardSize.REGULAR, 30) {}
        val vm = vm(
            setOf(
                FakeCapability("calendar", 10, true, listOf(today)),
                FakeCapability("weather", 90, false, listOf(music)),
            ),
        )
        vm.uiState.test {
            val layout = expectMostRecentItem().homeCards
            assertThat(layout.left?.id).isEqualTo("today")
            assertThat(layout.right.map { it.id }).containsExactly("music")
        }
    }

    @Test
    fun settingsOpensOnTheAskedPage() = runTest {
        access.result = admin
        val vm = vm()
        vm.uiState.test {
            vm.openSettings(SettingsPageIds.LOCATION)
            assertThat(expectMostRecentItem().settingsPage).isEqualTo("location")
        }
    }

    @Test
    fun aNamedPageStaysClosedWithoutAuthorisationThenOpensOnceAuthorised() = runTest {
        access.result = null
        val vm = vm()
        vm.uiState.test {
            vm.openSettings(SettingsPageIds.LOCATION)
            val closed = expectMostRecentItem()
            assertThat(closed.settingsOpen).isFalse()
            assertThat(closed.settingsPage).isNull()
            assertThat(access.requested.last()).containsExactly(CorePermissions.SETTINGS_MANAGE)
            access.result = admin
            vm.openSettings(SettingsPageIds.LOCATION)
            val open = expectMostRecentItem()
            assertThat(open.settingsOpen).isTrue()
            assertThat(open.settingsPage).isEqualTo("location")
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
        val vm = ShellViewModel(emptySet(), clock, noSessionAccess, SunToday(null, sunScope), toasts)
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
    fun usingOtherAppsSaysWhatHappensNext() = runTest {
        access.result = admin
        val vm = vm()
        assertThat(toasts.current.value).isNull()
        vm.exitKiosk()
        runCurrent()
        assertThat(toasts.current.value?.message).isEqualTo("You can use other apps now. Culvery locks again when it next opens.")
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
    fun exitKioskIsRememberedUntilCulveryIsInFrontAgain() = runTest {
        val vm = vm()
        access.result = admin
        vm.exitKiosk()
        runCurrent()
        assertThat(shouldPin(setupComplete = true, kioskExited = vm.kioskExited)).isFalse()
        vm.returnedToFront()
        assertThat(shouldPin(setupComplete = true, kioskExited = vm.kioskExited)).isTrue()
    }

    @Test
    fun changeHomeAppAsksForAFreshPinClosesSettingsAndSignsOut() = runTest {
        val vm = vm()
        access.result = admin
        vm.openSettings()
        runCurrent()
        vm.homeAppRequests.test {
            vm.changeHomeApp()
            assertThat(awaitItem()).isEqualTo(HomeAppRequest.CHANGE)
        }
        assertThat(access.requested.last()).containsExactly(CorePermissions.KIOSK_EXIT)
        assertThat(access.session.value).isNull()
        assertThat(vm.kioskExited).isTrue()
        vm.uiState.test { assertThat(expectMostRecentItem().settingsOpen).isFalse() }
    }

    @Test
    fun chooseHomeAppUsesTheOpenSession() = runTest {
        val vm = vm()
        access.result = admin
        vm.homeAppRequests.test {
            vm.chooseHomeApp()
            assertThat(awaitItem()).isEqualTo(HomeAppRequest.CHOOSE)
        }
        assertThat(access.requested.last()).containsExactly(CorePermissions.SETTINGS_MANAGE)
        assertThat(access.session.value).isNotNull()
        assertThat(vm.kioskExited).isFalse()
    }

    /** Android's screen leaves Culvery unpinned, another way out of the kiosk: a fresh Admin PIN first; a cancelled pad opens nothing. */
    @Test
    fun aSystemScreenAsksAFreshPinAndACancelledPadOpensNothing() = runTest {
        val vm = vm()
        val screens = listOf(Intent("detail"), Intent("list"))
        vm.systemScreenRequests.test {
            access.result = null
            vm.openSystemScreen(screens, "missing")
            runCurrent()
            expectNoEvents()
            assertThat(access.requested.last()).containsExactly(CorePermissions.CONNECTIONS_MANAGE)
            access.result = admin
            vm.openSystemScreen(screens, "missing")
            assertThat(awaitItem()).isEqualTo(SystemScreenRequest(screens, "missing"))
        }
        // Unlike Use other apps, Culvery pins again when it is back in front.
        assertThat(vm.kioskExited).isFalse()
    }

    @Test
    fun aRefusedPinAsksAndroidNothing() = runTest {
        val vm = vm()
        vm.homeAppRequests.test {
            vm.changeHomeApp()
            vm.chooseHomeApp()
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
            ticks.value = at(noon.withHour(20))
            expectMostRecentItem().let {
                assertThat(it.dark).isTrue()
                assertThat(it.previewing).isFalse()
            }
            ticks.value = at(noon.plusDays(1))
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
            assertThat(expectMostRecentItem().homeCards.left?.id).isEqualTo("today")
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

    /** 9 §4.3: Door history over Settings. Fails if Settings stays open over the tab it opened. */
    @Test
    fun openingATabClosesSettings() = runTest {
        access.result = admin
        val vm = vm(setOf(FakeCapability("door", order = 40, shown = true)))
        vm.uiState.test {
            vm.openSettings()
            assertThat(expectMostRecentItem().settingsOpen).isTrue()
            vm.openTab("door")
            val state = expectMostRecentItem()
            assertThat(state.settingsOpen).isFalse()
            assertThat(state.selectedTabId).isEqualTo("door")
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
            assertThat(expectMostRecentItem().homeCards).isEqualTo(HomeLayout.Empty)
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().homeCards.left?.id).isEqualTo("today")
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
            ticks.value = at(noon.with(LocalTime.of(19, 19)))
            assertThat(darkNow(vm)).isFalse()
            ticks.value = at(noon.with(sunset))
            assertThat(darkNow(vm)).isTrue()
            ticks.value = at(noon.plusDays(1).with(LocalTime.of(6, 49)))
            assertThat(darkNow(vm)).isTrue()
            ticks.value = at(noon.plusDays(1).with(sunrise))
            assertThat(darkNow(vm)).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun withoutDaylightTheThemeUsesSevenAndSeven() = runTest {
        val vm = vm()
        vm.uiState.test {
            ticks.value = at(noon.with(LocalTime.of(18, 59)))
            assertThat(darkNow(vm)).isFalse()
            ticks.value = at(noon.with(LocalTime.of(19, 0)))
            assertThat(darkNow(vm)).isTrue()
            ticks.value = at(noon.plusDays(1).with(LocalTime.of(6, 59)))
            assertThat(darkNow(vm)).isTrue()
            ticks.value = at(noon.plusDays(1).with(LocalTime.of(7, 0)))
            assertThat(darkNow(vm)).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun untilTheSunTimesAreKnownTheThemeUsesSevenAndSevenThenFollowsThem() = runTest {
        val daylight = FakeDaylight(null)
        val vm = vm(daylight = daylight)
        vm.uiState.test {
            ticks.value = at(noon.with(LocalTime.of(19, 10)))
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
            ticks.value = at(noon.with(LocalTime.of(19, 10)))
            expectMostRecentItem().let {
                assertThat(vm.now.value.toLocalTime()).isEqualTo(LocalTime.of(19, 10))
                assertThat(it.dark).isTrue()
            }
            advanceTimeBy(1_001)
            assertThat(expectMostRecentItem().dark).isFalse()
        }
        val logs = ShadowLog.getLogs().filter { it.tag == "ThemeSchedule" }
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

    /** 5 §3.3: any capability can mark its tab; the shell passes the mark to the rail as it changes. */
    @Test
    fun aTabCarriesItsCapabilitysBadge() = runTest {
        val door = FakeCapability("door", order = 50, shown = true)
        val vm = vm(setOf(door))
        vm.uiState.test {
            assertThat(expectMostRecentItem().tabs.single().badge).isNull()
            door.badgeFlow.value = RailBadge.Count(2, "2 unseen rings")
            assertThat(awaitItem().tabs.single().badge).isEqualTo(RailBadge.Count(2, "2 unseen rings"))
            door.badgeFlow.value = RailBadge.Dot("new motion")
            assertThat(awaitItem().tabs.single().badge).isEqualTo(RailBadge.Dot("new motion"))
        }
    }
}
