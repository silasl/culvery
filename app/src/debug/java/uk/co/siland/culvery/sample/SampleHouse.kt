package uk.co.siland.culvery.sample

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.siland.culvery.capability.lights.AllOffResult
import uk.co.siland.culvery.capability.lights.Automation
import uk.co.siland.culvery.capability.lights.AutomationResult
import uk.co.siland.culvery.capability.lights.BridgeFound
import uk.co.siland.culvery.capability.lights.LightRoom
import uk.co.siland.culvery.capability.lights.LightScene
import uk.co.siland.culvery.capability.lights.LightsLink
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsProvider
import uk.co.siland.culvery.capability.lights.LightsSettings
import uk.co.siland.culvery.capability.lights.LightsSystem
import uk.co.siland.culvery.capability.lights.LightsUnreachableException
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicProvider
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.MusicSettings
import uk.co.siland.culvery.capability.music.MusicSystem
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.capability.music.NowPlaying
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.RoomGroup
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.plugin.ApplicationScope
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.attempt
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.ui.Icons

const val SAMPLE_MUSIC_ID = "sample.music"
const val SAMPLE_LIGHTS_ID = "sample.lights"

private const val SAMPLE_HOUSEHOLD = "sample-household"
private const val SAMPLE_BRIDGE = "sample-bridge"
private const val SAMPLE_KEY = "sample-key"

/** A reserved documentation address (RFC 5737): nothing answers there, and the samples never ask. */
private const val SAMPLE_ADDRESS = "192.0.2.1"

private const val TAG = "SampleHouse"
private const val FILE = "debug_sample_house"
private val KEPT_MUSIC_PROVIDER = stringPreferencesKey("music_provider")
private val KEPT_MUSIC_HOUSEHOLD = stringPreferencesKey("music_household")
private val KEPT_MUSIC_ADDRESS = stringPreferencesKey("music_address")
private val KEPT_LIGHTS_PROVIDER = stringPreferencesKey("lights_provider")
private val KEPT_LIGHTS_BRIDGE = stringPreferencesKey("lights_bridge")
private val KEPT_LIGHTS_ADDRESS = stringPreferencesKey("lights_address")
private val KEPT_LIGHTS_KEY = stringPreferencesKey("lights_key")

private val SPEAKER_NAMES = listOf("Kitchen", "Lounge", "Dining")
private val PARTY_MIX = Favourite("FV:2/3", "Party mix", null, FavouriteKind.QUEUED)
private val SUNDAY_MORNING = Favourite("FV:2/2", "Sunday morning", null, FavouriteKind.QUEUED)

private fun alone(room: SpeakerRoom) = SpeakerGroup(
    id = room.id,
    rooms = listOf(room),
    coordinatorId = room.id,
    state = PlayState.STOPPED,
    nowPlaying = null,
    artUrl = null,
    canSkip = true,
    volume = room.volume,
    startedAtMillis = null,
)

/**
 * Debug builds only: three speakers, Kitchen, Lounge and Dining, each alone and stopped, kept in memory (13 §8). Grouping,
 * playing and the volumes work as the capability expects of a Sonos household; nothing leaves the process.
 */
class SampleMusicSystem : MusicSystem {
    override val groups = MutableStateFlow(SPEAKER_NAMES.map { alone(SpeakerRoom("$it-id", it, 30)) })
    override val problem = MutableStateFlow<MusicProblem?>(null)

    @Volatile
    private var down = false

    fun setDown(down: Boolean) {
        this.down = down
        problem.value = if (down) MusicProblem.UNREACHABLE else null
    }

    /** What an unreachable household does to every call: it throws. */
    private fun guard() {
        if (down) throw MusicUnreachableException(MusicProblem.UNREACHABLE)
    }

    /** A hand change: Dining leaves whichever group it is in and stands alone, stopped. */
    fun handRegroup() = groups.update { layout -> lead(layout, DINING_ID, setOf(DINING_ID)) }

    /** A hand change: every group that is playing is paused. */
    fun handPause() = groups.update { layout -> layout.map { if (it.isPlaying) it.copy(state = PlayState.PAUSED) else it } }

    override suspend fun checkLayout() = guard()

    override suspend fun checkPlayback() = guard()

    override suspend fun playingNow(): List<SpeakerGroup> {
        guard()
        return groups.value.filter { it.isPlaying }
    }

    override suspend fun roomVolume(room: SpeakerRoom): Int {
        guard()
        return groups.value.flatMap { it.rooms }.firstOrNull { it.id == room.id }?.volume ?: room.volume
    }

    override suspend fun play(group: SpeakerGroup) = change(group) { it.copy(state = PlayState.PLAYING) }

    override suspend fun pause(group: SpeakerGroup) = change(group) { it.copy(state = PlayState.PAUSED) }

    override suspend fun next(group: SpeakerGroup) = guard()

    override suspend fun setGroupVolume(group: SpeakerGroup, percent: Int) =
        change(group) { g -> g.copy(volume = percent, rooms = g.rooms.map { it.copy(volume = percent) }) }

    override suspend fun setRoomVolume(room: SpeakerRoom, percent: Int) {
        guard()
        groups.update { layout -> layout.map { g -> g.copy(rooms = g.rooms.map { if (it.id == room.id) it.copy(volume = percent) else it }) } }
    }

    override suspend fun favourites(): List<Favourite> {
        guard()
        return listOf(PARTY_MIX)
    }

    override suspend fun playlists(): List<Favourite> {
        guard()
        return listOf(SUNDAY_MORNING)
    }

    override suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite) =
        change(group) { it.copy(state = PlayState.PLAYING, nowPlaying = NowPlaying.Track(favourite.name, null, null), startedAtMillis = System.currentTimeMillis()) }

    override suspend fun shuffleEverything(group: SpeakerGroup) =
        change(group) { it.copy(state = PlayState.PLAYING, nowPlaying = NowPlaying.Track("Everything", null, null), startedAtMillis = System.currentTimeMillis()) }

    override suspend fun art(url: String): ByteArray? {
        guard()
        return null
    }

    override suspend fun group(coordinatorId: String, roomIds: Set<String>): SpeakerGroup {
        guard()
        groups.update { lead(it, coordinatorId, roomIds) }
        return groups.value.first { it.coordinatorId == coordinatorId }
    }

    override suspend fun regroup(groups: List<RoomGroup>) {
        guard()
        val known = this.groups.value.flatMap { g -> g.rooms.map { it.id } }.toSet()
        this.groups.update { layout -> groups.filter { it.coordinatorId in known }.fold(layout) { l, g -> lead(l, g.coordinatorId, g.roomIds.toSet()) } }
    }

    override fun useAddress(address: String) = Unit

    private fun change(group: SpeakerGroup, edit: (SpeakerGroup) -> SpeakerGroup) {
        guard()
        groups.update { layout -> layout.map { if (it.id == group.id) edit(it) else it } }
    }

    /** [layout] with [coordinatorId] leading exactly [ids]: a room taken from a group leaves it, and one left behind stands alone, stopped. */
    private fun lead(layout: List<SpeakerGroup>, coordinatorId: String, ids: Set<String>): List<SpeakerGroup> {
        val rooms = layout.flatMap { it.rooms }.associateBy { it.id }
        if (coordinatorId !in rooms) throw MusicRefusedException()
        val members = (ids + coordinatorId).filter { it in rooms }.sortedBy { it != coordinatorId }
        val led = layout.firstOrNull { it.coordinatorId == coordinatorId }
        val others = layout.filter { it !== led }.mapNotNull { g ->
            val left = g.rooms.filter { it.id !in members }
            when {
                left.size == g.rooms.size -> g
                left.isEmpty() -> null
                // The leader went to lead another group: the rest carry on under their first room.
                g.coordinatorId in members -> g.copy(id = left.first().id, rooms = left, coordinatorId = left.first().id)
                else -> g.copy(rooms = left)
            }
        }
        val dropped = led?.rooms.orEmpty().filter { it.id !in members }.map(::alone)
        val group = (led ?: alone(rooms.getValue(coordinatorId))).copy(rooms = members.map { rooms.getValue(it) })
        return listOf(group) + dropped + others
    }

    private companion object {
        const val DINING_ID = "Dining-id"
    }
}

/** The sample speakers as a provider a debug build binds beside Sonos; it is never found, only connected by the receiver (13 §8). */
@Singleton
class SampleMusicProvider @Inject constructor() : MusicProvider {
    val system = SampleMusicSystem()

    override val descriptor = ProviderDescriptor(SAMPLE_MUSIC_ID, "Sample speakers (debug)", Icons.SPEAKER, setOf(Feature.READ, Feature.WRITE), userConnectable = false)

    override suspend fun find(): MusicLink? = null

    override suspend fun check(address: String): MusicLink? = null

    override fun open(link: MusicLink, onMoved: suspend (MusicLink) -> Unit): MusicSystem = system
}

private val KITCHEN_SCENES = listOf(LightScene("s1", "Bright"), LightScene("s2", "Relax"))
private val LOUNGE_SCENES = listOf(LightScene("s3", "Relax"))

/**
 * Debug builds only: two Hue rooms, Kitchen with Bright and Relax and Lounge with Relax, kept in memory (13 §8). A scene
 * recalled shows as the room's active scene; turning a room off or setting its brightness clears it, as the bridge does.
 */
class SampleLightsSystem : LightsSystem {
    override val rooms = MutableStateFlow(
        listOf(
            LightRoom("r1", "Kitchen", on = false, brightness = 60, scenes = KITCHEN_SCENES, activeSceneId = null, dimmable = true),
            LightRoom("r2", "Lounge", on = false, brightness = 60, scenes = LOUNGE_SCENES, activeSceneId = null, dimmable = true),
        ),
    )
    override val problem = MutableStateFlow<LightsProblem?>(null)

    @Volatile
    private var down = false

    fun setDown(down: Boolean) {
        this.down = down
        problem.value = if (down) LightsProblem.UNREACHABLE else null
    }

    private fun guard() {
        if (down) throw LightsUnreachableException(LightsProblem.UNREACHABLE)
    }

    /** A hand change: Kitchen's active scene goes, as it would when someone dims it on the wall. */
    fun handClearScene() = edit("r1") { it.copy(activeSceneId = null) }

    override suspend fun readAll() = guard()

    override fun stream(): Flow<Unit> = flow {
        guard()
        emit(Unit)
        awaitCancellation()
    }

    override suspend fun setOn(room: LightRoom, on: Boolean) {
        guard()
        edit(room.id) { it.copy(on = on, activeSceneId = if (on) it.activeSceneId else null) }
    }

    override suspend fun setBrightness(room: LightRoom, percent: Int) {
        guard()
        edit(room.id) { it.copy(on = true, brightness = percent, activeSceneId = null) }
    }

    override suspend fun recallScene(room: LightRoom, scene: LightScene) {
        guard()
        edit(room.id) { it.copy(on = true, brightness = 100, activeSceneId = scene.id) }
    }

    override suspend fun allOff(leaveOut: Set<String>): AllOffResult {
        guard()
        rooms.update { all -> all.map { if (it.id in leaveOut) it else it.copy(on = false, activeSceneId = null) } }
        return AllOffResult.Done
    }

    override suspend fun automations(): List<Automation> {
        guard()
        return emptyList()
    }

    override suspend fun setAutomation(automation: Automation, on: Boolean): AutomationResult {
        guard()
        return AutomationResult.CONFIRMED
    }

    override fun useAddress(address: String) = Unit

    private fun edit(roomId: String, change: (LightRoom) -> LightRoom) =
        rooms.update { all -> all.map { if (it.id == roomId) change(it) else it } }
}

/** The sample lights as a provider a debug build binds beside Hue; it is never found or paired, only connected by the receiver (13 §8). */
@Singleton
class SampleLightsProvider @Inject constructor() : LightsProvider {
    val system = SampleLightsSystem()

    override val descriptor = ProviderDescriptor(SAMPLE_LIGHTS_ID, "Sample lights (debug)", Icons.LIGHTBULB, setOf(Feature.READ, Feature.WRITE), userConnectable = false)

    override suspend fun find(): BridgeFound? = null

    override suspend fun check(address: String): BridgeFound? = null

    override suspend fun pair(bridge: BridgeFound): LightsLink? = null

    override fun open(link: LightsLink, onMoved: suspend (LightsLink) -> Unit): LightsSystem = system
}

/**
 * What `DebugSampleHouseReceiver` does (13 §8): connects the samples and brings the saved connections back, makes a hand
 * change on them, and takes them down and up. The saved connections wait in `debug_sample_house`, under `files/datastore/`.
 */
@Singleton
class SampleHouse(
    private val music: SampleMusicProvider,
    private val lights: SampleLightsProvider,
    private val musicSettings: MusicSettings,
    private val lightsSettings: LightsSettings,
    private val kept: DataStore<Preferences>,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        music: SampleMusicProvider,
        lights: SampleLightsProvider,
        musicSettings: MusicSettings,
        lightsSettings: LightsSettings,
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) : this(music, lights, musicSettings, lightsSettings, settingsDataStore { context.preferencesDataStoreFile(FILE) }, scope)

    private val lock = Mutex()

    /** [then] runs when the change has finished, however it ended; one that throws is logged, so a walk-through carries on. */
    fun useSoon(sample: Boolean, then: () -> Unit) {
        scope.launch {
            try {
                attempt({ use(sample) }) { e -> Log.w(TAG, "The sample house didn't change (${e::class.simpleName})") }
            } finally {
                then()
            }
        }
    }

    /**
     * True: keeps the music and lights connections a household saved, then connects the samples; a second true keeps what
     * was kept. False: reconnects the kept ones, and does nothing when none were kept (a connection can't be cleared).
     */
    suspend fun use(sample: Boolean) = lock.withLock {
        if (sample) {
            keepSaved()
            musicSettings.connect(SAMPLE_MUSIC_ID, MusicLink(SAMPLE_HOUSEHOLD, SAMPLE_ADDRESS))
            lightsSettings.connect(SAMPLE_LIGHTS_ID, LightsLink(SAMPLE_BRIDGE, SAMPLE_ADDRESS, SAMPLE_KEY))
        } else {
            bringBackSaved()
        }
    }

    private suspend fun keepSaved() {
        val musicPrefs = musicSettings.prefs.first()
        val lightsPrefs = lightsSettings.prefs.first()
        val musicProvider = musicPrefs.providerId
        val musicLink = musicPrefs.link
        val lightsProvider = lightsPrefs.providerId
        val lightsLink = lightsPrefs.link
        kept.edit { saved ->
            if (musicProvider != null && musicProvider != SAMPLE_MUSIC_ID && musicLink != null) {
                saved[KEPT_MUSIC_PROVIDER] = musicProvider
                saved[KEPT_MUSIC_HOUSEHOLD] = musicLink.householdId
                saved[KEPT_MUSIC_ADDRESS] = musicLink.address
            }
            if (lightsProvider != null && lightsProvider != SAMPLE_LIGHTS_ID && lightsLink != null) {
                saved[KEPT_LIGHTS_PROVIDER] = lightsProvider
                saved[KEPT_LIGHTS_BRIDGE] = lightsLink.bridgeId
                saved[KEPT_LIGHTS_ADDRESS] = lightsLink.address
                // The household's Hue key, kept under files/datastore/ as LightsSettings keeps it, which backup and transfer leave alone.
                saved[KEPT_LIGHTS_KEY] = lightsLink.key
            }
        }
    }

    private suspend fun bringBackSaved() {
        val saved = kept.data.first()
        val musicProvider = saved[KEPT_MUSIC_PROVIDER]
        val household = saved[KEPT_MUSIC_HOUSEHOLD]
        val musicAddress = saved[KEPT_MUSIC_ADDRESS]
        if (musicProvider != null && household != null && musicAddress != null) {
            musicSettings.connect(musicProvider, MusicLink(household, musicAddress))
        }
        val lightsProvider = saved[KEPT_LIGHTS_PROVIDER]
        val bridge = saved[KEPT_LIGHTS_BRIDGE]
        val lightsAddress = saved[KEPT_LIGHTS_ADDRESS]
        val key = saved[KEPT_LIGHTS_KEY]
        if (lightsProvider != null && bridge != null && lightsAddress != null && key != null) {
            lightsSettings.connect(lightsProvider, LightsLink(bridge, lightsAddress, key))
        }
    }

    /** [hand] is `regroup`, `pause` or `scene`; anything else does nothing. */
    fun hand(hand: String) {
        when (hand) {
            "regroup" -> music.system.handRegroup()
            "pause" -> music.system.handPause()
            "scene" -> lights.system.handClearScene()
        }
    }

    fun down(down: Boolean) {
        music.system.setDown(down)
        lights.system.setDown(down)
    }
}
