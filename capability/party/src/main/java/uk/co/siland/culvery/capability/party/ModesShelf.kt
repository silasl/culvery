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

/** How many half-width spots Home's tray has: the redesign's row 1, two modes (18 §4.1). */
private const val HALF_SPOTS = 2

/** How many third-width spots Home's tray has: its last row, three sound pads (18 §4.1). */
private const val THIRD_SPOTS = 3

/** How long Home waits for every capability's tray buttons before it counts a silent one as having none. */
private const val BUTTONS_WAIT_MS = 1_000L

/**
 * Where the modes and the sound pads sit in Home's tray (15b decision 3, Ruling 12; 18 Ruling 2): Holiday mode first
 * while it is on Home, then the shown stored modes in the order they were added, in the half-width spots other buttons
 * leave, and as many pads as other buttons leave third-width spots. [capabilities] is a provider, as the modes' own
 * capabilities read this shelf. [room], [soundRoom] and [shown] are shared on [scope], so every reader gets one count and
 * the capabilities are asked once.
 */
@Singleton
class ModesShelf @Inject constructor(
    private val store: ModesStore,
    private val capabilities: Provider<Set<@JvmSuppressWildcards Capability>>,
    @ApplicationScope scope: CoroutineScope,
) : ModeShelf {
    /**
     * The tray buttons of every capability but the modes' own. It says nothing until each has given its buttons, so Home
     * never places a mode or a pad in a spot that a button then takes. One still silent after [BUTTONS_WAIT_MS] counts as
     * having none, and the buttons are corrected when it does answer.
     */
    private val others: Flow<List<TrayButton>> = flow {
        // The modes' own capabilities place their buttons by this shelf, so asking them would go round in a circle.
        val caps = capabilities.get().filter { it.id != HouseModes.HOLIDAY && it.id != HouseModes.PARTY }
        if (caps.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val answers = combine(
            caps.map { cap ->
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
        emitAll(
            combine(answers, waited) { lists, waited -> lists.takeIf { waited || it.none { list -> list == null } } }
                .filterNotNull()
                .map { lists -> lists.flatMap { it.orEmpty() } },
        )
    }.stateIn(scope, SharingStarted.Lazily, null).filterNotNull()

    private fun spotsLeft(spots: Int, takes: (TrayButton) -> Boolean): Flow<Int> =
        others.map { buttons -> (spots - buttons.count(takes)).coerceAtLeast(0) }.distinctUntilChanged()

    /** The spots left for modes: [HALF_SPOTS] less the half-width buttons that aren't modes. */
    val room: Flow<Int> = spotsLeft(HALF_SPOTS) { it.width == TrayWidth.HALF && !HouseModes.isModeTrayId(it.id) }

    /** The spots left for sound pads: [THIRD_SPOTS] less the third-width buttons, such as Taekwondo's. */
    val soundRoom: Flow<Int> = spotsLeft(THIRD_SPOTS) { it.width == TrayWidth.THIRD }

    /** The ids of the modes on Home, in tray order, as many as there is [room] for. */
    val shown: Flow<List<String>> = combine(store.prefs, room) { prefs, room ->
        val holiday = listOfNotNull(HouseModes.HOLIDAY.takeIf { prefs.holidayOnHome })
        (holiday + prefs.modes.filter { it.showOnHome }.map { it.id }).take(room)
    }.stateIn(scope, SharingStarted.Lazily, null).filterNotNull()

    override fun place(modeId: String): Flow<Int?> = shown.map { ids -> ids.indexOf(modeId).takeIf { it >= 0 } }.distinctUntilChanged()
}
