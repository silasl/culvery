package uk.co.siland.culvery.shell

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.siland.culvery.FrontTracker
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.access.mayManageConnections
import uk.co.siland.culvery.core.household.label
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.Daylight
import uk.co.siland.culvery.core.plugin.HeaderItem
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomeLayout
import uk.co.siland.culvery.core.plugin.HouseholdClock
import uk.co.siland.culvery.core.plugin.ShellNavigator
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.retryWithBackoff

/** What Settings › Tablet lock or the Done step asked of Android (4c §5.1). */
enum class HomeAppRequest { CHOOSE, CHANGE }

/** One of Android's screens asked for through [ShellNavigator.openSystemScreen], after the PIN. */
data class SystemScreenRequest(val intents: List<Intent>, val missing: String)

@HiltViewModel
class ShellViewModel @Inject constructor(
    capabilities: Set<@JvmSuppressWildcards Capability>,
    clock: HouseholdClock,
    private val access: AccessControl,
    sunToday: SunToday,
    private val toaster: Toaster,
) : ViewModel(), ShellNavigator {
    private val ordered = capabilities.sortedBy { it.order }
    private val selected = MutableStateFlow(HOME_TAB_ID)

    /** One per tap on the tab that is already open; the open tab counts it as opening again. */
    private val reselections = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val tabReselected: Flow<Unit> = reselections
    /** Settings open, on a page or (null page) the first; null while closed. */
    private class OpenSettings(val page: String?)

    private val settings = MutableStateFlow<OpenSettings?>(null)
    private val previewing = MutableStateFlow(false)
    private val kioskExitEvents = Channel<Unit>(Channel.BUFFERED)
    val kioskExit: Flow<Unit> = kioskExitEvents.receiveAsFlow()
    private val homeAppEvents = Channel<HomeAppRequest>(Channel.BUFFERED)

    /** Each request to Android about the home app; MainActivity unpins first, as nothing opens over a pinned app. */
    val homeAppRequests: Flow<HomeAppRequest> = homeAppEvents.receiveAsFlow()
    private val systemScreenEvents = Channel<SystemScreenRequest>(Channel.BUFFERED)

    /** Each system screen allowed; MainActivity unpins and opens it. */
    val systemScreenRequests: Flow<SystemScreenRequest> = systemScreenEvents.receiveAsFlow()

    /**
     * Use other apps or Change home app, and Culvery not back in front since (4c K2). A field, not saved state: a
     * configuration change keeps it, a process death forgets it, so the kiosk pins again when next in front (ruling 6).
     */
    var kioskExited: Boolean = false
        private set

    /** The household's wall time, for the status bar and Home's clock; not part of [uiState], so a tick redraws only them (4c §4.2). */
    val now: StateFlow<LocalDateTime> =
        clock.minutes.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    // Without Daylight, or until today's times are known, ThemeSchedule's 07:00 / 19:00 applies (4b design §3.8).
    private val scheduledDark: StateFlow<Boolean> =
        combine(now, sunToday.times) { time, sun -> ThemeSchedule.isDark(time.toLocalTime(), sun) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSchedule.isDark(LocalTime.now(), null))

    private val tabs: Flow<List<TabItem>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                ordered.map { cap ->
                    combine(
                        // onStart after the retry: a retry must not hide a tab that was showing.
                        cap.hasTab.retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read whether it has a tab (${it::class.simpleName}); retrying") }
                            .onStart { emit(false) },
                        cap.badge.retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its badge (${it::class.simpleName}); retrying") }
                            .onStart { emit(null) },
                    ) { shown, badge -> if (shown) TabItem(cap.id, cap.label, cap.icon, badge) else null }
                },
            ) {
                it.filterNotNull()
            }
        }

    private class PlacedCards(val layout: HomeLayout, val loaded: Boolean)

    private val placements: Flow<PlacedCards> =
        if (ordered.isEmpty()) {
            flowOf(PlacedCards(HomeLayout.Empty, loaded = true))
        } else {
            combine(
                ordered.map { cap ->
                    cap.cards()
                        .map<List<HomeCard>, List<HomeCard>?> { it }
                        .retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its Home cards (${it::class.simpleName}); retrying") }
                        // Null until this capability answers.
                        .onStart { emit(null) }
                },
            ) { lists -> PlacedCards(HomeCardPlacer.place(lists.filterNotNull().flatten()), loaded = lists.none { it == null }) }
        }

    private val headerItems: Flow<List<HeaderItem>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                ordered.map { cap ->
                    cap.headerItems()
                        .retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its header items (${it::class.simpleName}); retrying") }
                        .onStart { emit(emptyList()) }
                },
            ) { lists -> lists.toList().flatten().sortedWith(compareBy({ it.order }, { it.id })) }
        }

    val uiState: StateFlow<ShellUiState> =
        combine(
            combine(tabs, selected, access.session, placements, settings) { tabs, sel, session, cards, settings ->
                ShellUiState(
                    tabs = tabs,
                    selectedTabId = if (sel == HOME_TAB_ID || tabs.any { it.id == sel }) sel else HOME_TAB_ID,
                    session = session?.let { SessionUi(it.person.name, it.role.label) },
                    homeCards = cards.layout,
                    cardsLoaded = cards.loaded,
                    settingsOpen = settings != null && session != null,
                    settingsPage = settings?.page,
                )
            },
            scheduledDark,
            previewing,
            headerItems,
        ) { state, scheduled, preview, header ->
            state.copy(dark = scheduled != preview, previewing = preview, headerItems = header)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState(dark = scheduledDark.value))

    init {
        viewModelScope.launch { scheduledDark.drop(1).collect { previewing.value = false } }
        viewModelScope.launch { access.session.collect { if (it == null) settings.value = null } }
    }

    fun selectTab(id: String) {
        if (id == selected.value) reselections.tryEmit(Unit)
        selected.value = id
    }

    /** Closes Settings first, so a tab opened from over it (the door card's Door history, 9 §4.3) is what shows. */
    override fun openTab(id: String) {
        settings.value = null
        selectTab(id)
    }

    override fun openSettings(page: String?) {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.SETTINGS_MANAGE) != null) settings.value = OpenSettings(page)
        }
    }

    fun closeSettings() {
        settings.value = null
    }

    override fun exitKiosk() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.KIOSK_EXIT) != null) {
                leaveKiosk()
                kioskExitEvents.send(Unit)
                // As the home app Culvery stays in front, so nothing else shows that it worked.
                toaster.show(USING_OTHER_APPS)
            }
        }
    }

    override fun chooseHomeApp() {
        viewModelScope.launch {
            access.authorise(CorePermissions.SETTINGS_MANAGE) ?: return@launch
            homeAppEvents.send(HomeAppRequest.CHOOSE)
        }
    }

    /** Asks for kiosk.exit's fresh PIN: its pad says "Use other apps", which is what this does. */
    override fun changeHomeApp() {
        viewModelScope.launch {
            access.authorise(CorePermissions.KIOSK_EXIT) ?: return@launch
            leaveKiosk()
            homeAppEvents.send(HomeAppRequest.CHANGE)
        }
    }

    /** Leaving Culvery unpinned is a way out of the kiosk: a fresh Admin PIN, as Change home app asks; nothing opens without it. */
    override fun openSystemScreen(intents: List<Intent>, missing: String) {
        viewModelScope.launch {
            if (!access.mayManageConnections()) return@launch
            systemScreenEvents.send(SystemScreenRequest(intents, missing))
        }
    }

    /** The view model has no window: MainActivity's navigator leaves pinning (4c §5.3). */
    override fun leavePinning() = Unit

    override fun returnToPinning() = Unit

    /** Culvery is in front again after being stopped; with D3 that includes a Home press from another app. */
    fun returnedToFront() {
        kioskExited = false
    }

    /** Survives the activity, so a relaunch after Culvery was stopped counts as coming back to the front (K2). */
    internal val front = FrontTracker()

    private fun leaveKiosk() {
        kioskExited = true
        settings.value = null
        access.lock()
    }

    fun signOut() = access.lock()

    fun toggleThemePreview() {
        previewing.value = !previewing.value
    }
}

private const val TAG = "ShellViewModel"

internal const val USING_OTHER_APPS = "You can use other apps now. Culvery locks again when it next opens."
