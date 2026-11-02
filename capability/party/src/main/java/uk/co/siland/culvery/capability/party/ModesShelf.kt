package uk.co.siland.culvery.capability.party

import android.util.Log
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.ModeShelf
import uk.co.siland.culvery.core.plugin.Capability
import uk.co.siland.culvery.core.plugin.TrayWidth
import uk.co.siland.culvery.core.plugin.retryWithBackoff

private const val TAG = "ModesShelf"

/** How many half-width spots Home's tray has: two rows of two (15b decision 3). */
private const val HALF_SPOTS = 4

/**
 * Where the modes sit in Home's tray (15b decision 3, Ruling 12): Holiday mode first while it is on Home, then the
 * shown stored modes in the order they were added, in the half-width spots other buttons leave. [capabilities] is a
 * provider, as the modes' own capability reads this shelf.
 */
@Singleton
class ModesShelf @Inject constructor(
    private val store: ModesStore,
    private val capabilities: Provider<Set<@JvmSuppressWildcards Capability>>,
) : ModeShelf {
    /**
     * The spots left for modes: four less the half-width buttons that aren't modes. It says nothing until every other
     * capability has given its buttons, so Home never places a mode in a spot that a button then takes.
     */
    val room: Flow<Int> = flow {
        // The modes' own capabilities place their buttons by this shelf, so asking them would go round in a circle.
        val others = capabilities.get().filter { it.id != HouseModes.HOLIDAY && it.id != HouseModes.PARTY }
        val halves = if (others.isEmpty()) {
            flowOf(0)
        } else {
            combine(
                others.map { cap ->
                    cap.trayButtons()
                        .retryWithBackoff { Log.w(TAG, "${cap.id}: couldn't read its tray buttons (${it::class.simpleName}); retrying") }
                },
            ) { lists -> lists.sumOf { list -> list.count { it.width == TrayWidth.HALF && !it.id.isModeButton() } } }
        }
        emitAll(halves.map { (HALF_SPOTS - it).coerceAtLeast(0) })
    }.distinctUntilChanged()

    /** The ids of the modes on Home, in tray order, as many as there is [room] for. */
    val shown: Flow<List<String>> = combine(store.prefs, room) { prefs, room ->
        val holiday = listOfNotNull(HouseModes.HOLIDAY.takeIf { prefs.holidayOnHome })
        (holiday + prefs.modes.filter { it.showOnHome }.map { it.id }).take(room)
    }.distinctUntilChanged()

    override fun place(modeId: String): Flow<Int?> = shown.map { ids -> ids.indexOf(modeId).takeIf { it >= 0 } }.distinctUntilChanged()
}

private fun String.isModeButton() = startsWith("mode.") || this == "holiday.mode"
