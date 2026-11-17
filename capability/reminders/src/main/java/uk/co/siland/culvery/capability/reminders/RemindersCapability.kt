package uk.co.siland.culvery.capability.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import uk.co.siland.culvery.capability.reminders.ui.RemindersTab
import uk.co.siland.culvery.capability.reminders.ui.openBins
import uk.co.siland.culvery.capability.reminders.ui.openReminder
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.HomeCard
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.OverlayHost
import uk.co.siland.culvery.core.plugin.RailBadge
import uk.co.siland.culvery.core.setup.SetupState
import uk.co.siland.culvery.core.ui.Icons

const val REMINDERS_ID = "reminders"

internal fun badgeOf(waiting: Int): RailBadge? = if (waiting > 0) RailBadge.Count(waiting, "$waiting waiting") else null

/** Reminders (22 §4.1): rail slot 25, a tab once setup is complete, badged with what waits, snoozed ones included. */
@Singleton
class RemindersCapability internal constructor(
    setupComplete: Flow<Boolean>,
    waiting: Flow<List<Due>>,
    private val tab: Flow<TabUi>,
    private val openForm: suspend (OverlayHost, Long?) -> Unit = { _, _ -> },
    private val openBins: (OverlayHost, BinsRowUi) -> Unit = { _, _ -> },
    private val done: (DueKey) -> Unit,
) : Capability {
    @Inject
    constructor(setup: SetupState, engine: RemindersEngine, view: RemindersView, actions: RemindersActions, household: HouseholdRepository) : this(
        setup.setupComplete, engine.waiting, view.tab, { overlay, id -> overlay.openReminder(id, actions, household.members) },
        { overlay, row -> overlay.openBins(row, actions) }, engine::done,
    )

    override val id = REMINDERS_ID
    override val label = "Reminders"
    override val icon = Icons.ALARM
    override val order = 25
    override val hasTab: Flow<Boolean> = setupComplete
    override val badge: Flow<RailBadge?> = waiting.map { badgeOf(it.size) }.distinctUntilChanged()

    override fun cards(): Flow<List<HomeCard>> = flowOf(emptyList())

    @Composable
    override fun TabContent() {
        val ui by tab.collectAsStateWithLifecycle(null)
        val overlay = LocalOverlayHost.current
        val scope = rememberCoroutineScope()
        var loading by remember { mutableStateOf(false) }
        val open = { id: Long? ->
            if (!loading) {
                loading = true
                scope.launch {
                    try {
                        openForm(overlay, id)
                    } finally {
                        loading = false
                    }
                }
            }
        }
        ui?.let { tabUi -> RemindersTab(tabUi, onAdd = { open(null) }, onOpen = { open(it) }, onDone = done, onBins = { tabUi.bins?.let { openBins(overlay, it) } }) }
    }
}
