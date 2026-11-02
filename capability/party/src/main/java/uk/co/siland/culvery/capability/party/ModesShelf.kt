package uk.co.siland.culvery.capability.party

import android.util.Log
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.ModeShelf
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.TrayButton
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.plugin.retryWithBackoff

private const val TAG = "ModesShelf"

/** How many half-width spots Home's tray has: two rows of two (15b decision 3). */
private const val HALF_SPOTS = 4

/** How long Home waits for every capability's tray buttons before it counts a silent one as having none. */
private const val BUTTONS_WAIT_MS = 1_000L

/**
 * Where the modes sit in Home's tray (15b decision 3, Ruling 12): Holiday mode first while it is on Home, then the
 * shown stored modes in the order they were added, in the half-width spots other buttons leave. [capabilities] is a
 * provider, as the modes' own capability reads this shelf. [room] and [shown] are shared on [scope], so every reader
 * gets one count and the capabilities are asked once.
 */
@Singleton
class ModesShelf @Inject constructor(
    private val store: ModesStore,
    private val capabilities: Provider<Set<@JvmSuppressWildcards Capability>>,
    @ApplicationScope scope: CoroutineScope,
) : ModeShelf {
    /**
     * The spots left for modes: four less the half-width buttons that aren't modes. It says nothing until every other
     * capability has given its buttons, so Home never places a mode in a spot that a button then takes. One still silent
     * after [BUTTONS_WAIT_MS] counts as having none, and the room is corrected when it does answer.
     */
    val room: Flow<Int> = flow {
        // The modes' own capabilities place their buttons by this shelf, so asking them would go round in a circle.
        val others = capabilities.get().filter { it.id != HouseModes.HOLIDAY && it.id != HouseModes.PARTY }
        val halves = if (others.isEmpty()) {
            flowOf(0)
        } else {
            val answers = combine(
                others.map { cap ->
                    cap.trayButtons()
                        .retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its tray buttons (${it::class.simpleName}); retrying") }
                        .map<List<TrayButton>, List<TrayButton>?> { it }
                        .onStart { emit(null) }
                },
            ) { lists -> lists }
            val waited = flow {
                emit(false)
                delay(BUTTONS_WAIT_MS)
                emit(true)
            }
            combine(answers, waited) { lists, waited -> lists.takeIf { waited || it.none { list -> list == null } } }
                .filterNotNull()
                .map { lists -> lists.sumOf { list -> list.orEmpty().count { it.width == TrayWidth.HALF && !it.id.isModeButton() } } }
        }
        emitAll(halves.map { (HALF_SPOTS - it).coerceAtLeast(0) })
    }.stateIn(scope, SharingStarted.Lazily, null).filterNotNull()

    /** The ids of the modes on Home, in tray order, as many as there is [room] for. */
    val shown: Flow<List<String>> = combine(store.prefs, room) { prefs, room ->
        val holiday = listOfNotNull(HouseModes.HOLIDAY.takeIf { prefs.holidayOnHome })
        (holiday + prefs.modes.filter { it.showOnHome }.map { it.id }).take(room)
    }.stateIn(scope, SharingStarted.Lazily, null).filterNotNull()

    override fun place(modeId: String): Flow<Int?> = shown.map { ids -> ids.indexOf(modeId).takeIf { it >= 0 } }.distinctUntilChanged()
}

private fun String.isModeButton() = startsWith("mode.") || this == "holiday.mode"
