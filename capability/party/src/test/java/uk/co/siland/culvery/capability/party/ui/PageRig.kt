package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import uk.co.siland.culvery.capability.party.DINING
import uk.co.siland.culvery.capability.party.FakeLights
import uk.co.siland.culvery.capability.party.FakeMusic
import uk.co.siland.culvery.capability.party.FakePlugs
import uk.co.siland.culvery.capability.party.MemoryStore
import uk.co.siland.culvery.capability.party.ModesStore
import uk.co.siland.culvery.capability.party.KITCHEN
import uk.co.siland.culvery.capability.party.LOUNGE
import uk.co.siland.culvery.capability.party.StoredModeActions
import uk.co.siland.culvery.capability.party.StoredModesHouse
import uk.co.siland.culvery.capability.party.TestAccess
import uk.co.siland.culvery.capability.party.TestSwitch
import uk.co.siland.culvery.capability.party.Trace
import uk.co.siland.culvery.core.access.test.FakeAccess
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.house.MusicOptions
import uk.co.siland.culvery.core.house.MusicPick
import uk.co.siland.culvery.core.house.RoomChoice
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.plugin.Toaster
import uk.co.siland.culvery.core.plugin.WallClock

/** Everything a modes page runs on: a store, real actions and house over the fake seams, and the shelf's rule for who sits on Home. */
internal class PageRig(scope: CoroutineScope, role: Role? = Role.ADULT) {
    val trace = Trace()
    val memory = MemoryStore()
    val settings = ModesStore(memory, scope)
    val plugs = FakePlugs(trace)
    val music = FakeMusic(trace).apply { offered = MUSIC_OPTIONS }
    val lights = FakeLights(trace).apply { offered = LIGHT_OPTIONS }
    val house = StoredModesHouse(settings, plugs, music, lights, WallClock { 1_000_000L })
    val access: FakeAccess = TestAccess(role)
    val holidayOn = MutableStateFlow<Boolean?>(false)
    val room = MutableStateFlow(4)

    private val actions = StoredModeActions(access, house, settings, setOf(TestSwitch(HouseModes.HOLIDAY, "Holiday mode", holidayOn), house))

    /** As [uk.co.siland.culvery.capability.party.ModesShelf] places them: Holiday first while it is on Home, then the shown modes, as many as fit. */
    private val shown: Flow<List<String>> = combine(settings.prefs, room) { prefs, room ->
        (listOfNotNull(HouseModes.HOLIDAY.takeIf { prefs.holidayOnHome }) + prefs.modes.filter { it.showOnHome }.map { it.id }).take(room)
    }.distinctUntilChanged()

    private val controls = ModeControls(actions, house, { id -> shown.map { ids -> ids.indexOf(id).takeIf { it >= 0 } } }, music, lights)

    fun page(toaster: Toaster) = ModesPage(settings, controls, holidayOn, room, plugs, toaster)

    /** Reads the record, as the scheduler's first tick does; until then the page takes the mode for unread. */
    fun loaded() = runBlocking { house.tick() }
}

internal val SUNDAY = MusicPick("FV:2/2", "Sunday morning")
internal val MIX = MusicPick("FV:2/3", "Party mix")
internal val MUSIC_OPTIONS = MusicOptions(listOf(SUNDAY, MIX), listOf(KITCHEN, LOUNGE, DINING), null)

/** Kitchen and Lounge dim and have scenes; the Hall has neither. */
internal val LIGHT_OPTIONS = LightOptions(
    listOf(
        LightRoomOption("r1", "Kitchen", listOf(HouseScene("s1", "Bright"), HouseScene("s2", "Relax"))),
        LightRoomOption("r2", "Lounge", listOf(HouseScene("s3", "Relax"))),
        LightRoomOption("r3", "Hall", emptyList(), dimmable = false),
    ),
    null,
)
internal val BRIGHT_IN_KITCHEN = RoomChoice("r1", "Kitchen", LightChoice.Scene("s1", "Bright"))

/** Hosts a page as Settings does: in a vertical scroll, in a column 720 dp wide. */
@Composable
internal fun InSettingsPane(page: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 720.dp)) { page() }
    }
}
