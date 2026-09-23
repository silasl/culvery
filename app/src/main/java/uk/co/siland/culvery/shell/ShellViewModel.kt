package uk.co.siland.culvery.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCardPlacer
import uk.co.siland.culvery.core.plugin.HomePlacement

@HiltViewModel
class ShellViewModel @Inject constructor(
    capabilities: Set<@JvmSuppressWildcards Capability>,
    ticker: MinuteTicker,
    private val access: AccessControl,
) : ViewModel() {
    private val ordered = capabilities.sortedBy { it.order }
    private val selected = MutableStateFlow(HOME_TAB_ID)
    private val settingsOpen = MutableStateFlow(false)
    private val previewing = MutableStateFlow(false)
    private val kioskExitEvents = Channel<Unit>(Channel.BUFFERED)
    val kioskExit: Flow<Unit> = kioskExitEvents.receiveAsFlow()

    private val now: StateFlow<LocalDateTime> =
        ticker.ticks().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    // Sunrise/sunset arrive with the weather capability in Plan 4; until then the 07:00/19:00 fallback applies.
    private val scheduledDark: StateFlow<Boolean> =
        now.map { ThemeSchedule.isDark(it.toLocalTime(), null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeSchedule.isDark(LocalTime.now(), null))

    private val tabs: Flow<List<TabItem>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                ordered.map { cap ->
                    cap.hasTab.onStart { emit(false) }.catch { emit(false) }
                        .map { shown -> if (shown) TabItem(cap.id, cap.label, cap.icon) else null }
                },
            ) {
                it.filterNotNull()
            }
        }

    private val placements: Flow<List<HomePlacement>> =
        if (ordered.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                ordered.map { it.cards().onStart { emit(emptyList()) }.catch { emit(emptyList()) } },
            ) { lists -> HomeCardPlacer.place(lists.toList().flatten()) }
        }

    val uiState: StateFlow<ShellUiState> =
        combine(
            combine(tabs, selected, access.session, placements, settingsOpen) { tabs, sel, session, cards, settings ->
                ShellUiState(
                    tabs = tabs,
                    selectedTabId = if (sel == HOME_TAB_ID || tabs.any { it.id == sel }) sel else HOME_TAB_ID,
                    session = session?.let { SessionChip(it.person.name, it.person.color) },
                    homeCards = cards,
                    settingsOpen = settings && session != null,
                )
            },
            now,
            scheduledDark,
            previewing,
        ) { state, time, scheduled, preview ->
            state.copy(now = time, dark = scheduled != preview, previewing = preview)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShellUiState(dark = scheduledDark.value))

    init {
        viewModelScope.launch { scheduledDark.drop(1).collect { previewing.value = false } }
        viewModelScope.launch { access.session.collect { if (it == null) settingsOpen.value = false } }
    }

    fun selectTab(id: String) {
        selected.value = id
    }

    fun openSettings() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.SETTINGS_MANAGE) != null) settingsOpen.value = true
        }
    }

    fun closeSettings() {
        settingsOpen.value = false
    }

    fun exitKiosk() {
        viewModelScope.launch {
            if (access.authorise(CorePermissions.KIOSK_EXIT) != null) {
                settingsOpen.value = false
                access.lock()
                kioskExitEvents.send(Unit)
            }
        }
    }

    fun lockSession() = access.lock()

    fun onUserActivity() = access.touch()

    fun toggleThemePreview() {
        previewing.value = !previewing.value
    }
}
