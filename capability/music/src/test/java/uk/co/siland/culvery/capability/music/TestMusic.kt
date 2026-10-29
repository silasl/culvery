package uk.co.siland.culvery.capability.music

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import uk.co.siland.culvery.capability.music.ui.GroupActions
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.ui.Icons

internal val SONG = NowPlaying.Track("Song", "Artist", "Album")
internal val STATION = NowPlaying.Radio("Culvery FM", "The Breakfast Show")

/** A group of [rooms], the first leading; each room's id is its name with "-id". */
internal fun group(
    id: String,
    vararg rooms: String,
    state: PlayState = PlayState.STOPPED,
    nowPlaying: NowPlaying? = null,
    volume: Int = 30,
    startedAt: Long? = null,
    canSkip: Boolean = nowPlaying !is NowPlaying.Radio,
    artUrl: String? = null,
) = SpeakerGroup(id, rooms.map { SpeakerRoom("$it-id", it, volume) }, "${rooms.first()}-id", state, nowPlaying, artUrl, canSkip, volume, startedAt)

/** Controls that write each tap to [calls]: "play k", "volume k 45", "room Kitchen-id 20", "favourites k". */
internal fun recordingGroupActions(calls: MutableList<String>) = GroupActions(
    onPlay = { calls += "play ${it.id}" },
    onPause = { calls += "pause ${it.id}" },
    onNext = { calls += "next ${it.id}" },
    onGroupVolume = { g, v -> calls += "volume ${g.id} $v" },
    onRoomVolume = { _, r, v -> calls += "room ${r.id} $v" },
    onShuffle = { calls += "shuffle ${it.id}" },
    onFavourites = { calls += "favourites ${it.id}" },
)

internal fun musicSettings(dir: File, scope: CoroutineScope) =
    MusicSettings(settingsDataStore(scope) { File(dir, "music_settings.preferences_pb") }, scope)

/** A test's folder, a scope on Dispatchers.IO cancelled after the test, and [settings] kept in that folder. */
class MusicFixture : TestRule {
    private val folder = TemporaryFolder()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings by lazy { musicSettings(folder.root, scope) }

    override fun apply(base: Statement, description: Description): Statement = folder.apply(
        object : Statement() {
            override fun evaluate() = try {
                base.evaluate()
            } finally {
                scope.cancel()
            }
        },
        description,
    )
}



/** A music system a test drives: its groups and problem, the calls made of it in order, and calls that fail or wait. */
internal class TestMusicSystem(groups: List<SpeakerGroup> = emptyList()) : MusicSystem {
    override val groups = MutableStateFlow(groups)
    override val problem = MutableStateFlow<MusicProblem?>(null)

    /** Written from whatever thread a check runs on; read by the test. */
    val calls: MutableList<String> = CopyOnWriteArrayList()

    // The maps are written from groups lowered in parallel, so each is concurrent.

    /** Each room's volume as its speaker holds it, by room id; a room not here answers its [SpeakerRoom.volume]. */
    val roomVolumes: MutableMap<String, Int> = ConcurrentHashMap()

    /** What a call throws, by its name as [calls] writes it ("roomVolume Kitchen-id"), every time. */
    val failures: MutableMap<String, Exception> = ConcurrentHashMap()

    /** What a call throws the first time only. */
    val failOnce: MutableMap<String, Exception> = ConcurrentHashMap()

    /** Holds a call, by its name, until completed. */
    val gates: MutableMap<String, CompletableDeferred<Unit>> = ConcurrentHashMap()

    /** What playingNow() answers; null answers the playing ones of [groups]. */
    var playingNowAnswer: List<SpeakerGroup>? = null
    var favouriteList: List<Favourite> = emptyList()
    var playlistList: List<Favourite> = emptyList()

    /** Rooms a group() leaves out of the group it returns and out of the layout, as a speaker refusing the join would. */
    var groupLeavesOut: Set<String> = emptySet()

    private suspend fun record(call: String) {
        calls += call
        gates[call]?.await()
        failures[call]?.let { throw it }
        failOnce.remove(call)?.let { throw it }
    }

    override suspend fun checkLayout() = record("checkLayout")

    override suspend fun checkPlayback() = record("checkPlayback")

    override suspend fun playingNow(): List<SpeakerGroup> {
        record("playingNow")
        return playingNowAnswer ?: groups.value.filter { it.isPlaying }
    }

    override suspend fun roomVolume(room: SpeakerRoom): Int {
        record("roomVolume ${room.id}")
        return roomVolumes[room.id] ?: room.volume
    }

    override suspend fun setGroupVolume(group: SpeakerGroup, percent: Int) = record("setGroupVolume ${group.id} $percent")

    /** The speaker takes the volume before it answers, so a failure here is a command that took effect but whose answer was lost. */
    override suspend fun setRoomVolume(room: SpeakerRoom, percent: Int) {
        roomVolumes[room.id] = percent
        record("setRoomVolume ${room.id} $percent")
    }

    override suspend fun play(group: SpeakerGroup) = record("play ${group.id}")

    override suspend fun pause(group: SpeakerGroup) = record("pause ${group.id}")

    override suspend fun next(group: SpeakerGroup) = record("next ${group.id}")

    override suspend fun favourites(): List<Favourite> {
        record("favourites")
        return favouriteList
    }

    override suspend fun playlists(): List<Favourite> {
        record("playlists")
        return playlistList
    }

    /** Records "group {coordinatorId} {roomIds sorted, comma-joined}", then the coordinator leads exactly those rooms. */
    override suspend fun group(coordinatorId: String, roomIds: Set<String>): SpeakerGroup {
        record("group $coordinatorId ${roomIds.sorted().joinToString(",")}")
        groups.value = lead(groups.value, coordinatorId, (roomIds - groupLeavesOut) + coordinatorId)
        return groups.value.first { it.coordinatorId == coordinatorId }
    }

    /** Records "regroup {coordinatorId}:{ids joined by +},..." in the order given, then each coordinator leads its rooms; rooms not in the layout are skipped. */
    override suspend fun regroup(groups: List<RoomGroup>) {
        record("regroup " + groups.joinToString(",") { "${it.coordinatorId}:${it.roomIds.joinToString("+")}" })
        val known = this.groups.value.flatMap { g -> g.rooms.map { it.id } }.toSet()
        this.groups.value = groups.filter { it.coordinatorId in known }.fold(this.groups.value) { layout, g ->
            lead(layout, g.coordinatorId, g.roomIds.filter { it in known }.toSet() + g.coordinatorId)
        }
    }

    /** [layout] with [coordinatorId] leading exactly [ids]: the rooms taken from a group leave it, and a room left behind stands alone, stopped. */
    private fun lead(layout: List<SpeakerGroup>, coordinatorId: String, ids: Set<String>): List<SpeakerGroup> {
        val rooms = layout.flatMap { it.rooms }.associateBy { it.id }
        val led = layout.firstOrNull { it.coordinatorId == coordinatorId }
        val stay = layout.flatMap { g ->
            val left = g.rooms.filter { it.id !in ids }
            val alone = { room: SpeakerRoom -> g.copy(id = room.id, rooms = listOf(room), coordinatorId = room.id, state = PlayState.STOPPED, nowPlaying = null) }
            when {
                left.isEmpty() -> emptyList()
                g.coordinatorId !in ids -> listOf(g.copy(rooms = left))
                g.coordinatorId == coordinatorId -> left.map(alone)
                else -> listOf(alone(left.first()).copy(rooms = left))
            }
        }
        val leads = (led ?: (layout.firstOrNull { g -> g.rooms.any { it.id == coordinatorId } } ?: throw MusicRefusedException()).copy(id = coordinatorId, state = PlayState.STOPPED, nowPlaying = null))
            .copy(rooms = ids.sorted().sortedBy { it != coordinatorId }.mapNotNull { rooms[it] }, coordinatorId = coordinatorId)
        return listOf(leads) + stay
    }

    override suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite) = record("playFavourite ${group.id} ${favourite.id}")

    override suspend fun shuffleEverything(group: SpeakerGroup) = record("shuffleEverything ${group.id}")

    /** Each address the system was told to use, in order. */
    val addresses = MutableStateFlow<List<String>>(emptyList())

    override fun useAddress(address: String) {
        addresses.value += address
    }

    override suspend fun art(url: String): ByteArray? {
        record("art $url")
        return null
    }
}

/** A provider whose answers a test sets; it opens a [TestMusicSystem] and keeps each. */
internal class TestMusicProvider(id: String = "music.sonos", connectable: Boolean = true) : MusicProvider {
    override val descriptor = ProviderDescriptor(id, "Sonos", Icons.SPEAKER, setOf(Feature.READ, Feature.WRITE), userConnectable = connectable)
    var found: MusicLink? = null

    /** What check(address) answers, by address. */
    val speakers = mutableMapOf<String, MusicLink>()

    /** Thrown by find() and check(): offline, say. */
    var failure: Exception? = null
    val asked = mutableListOf<String>()
    val opened = mutableListOf<MusicLink>()
    val systems = mutableListOf<TestMusicSystem>()
    var onMoved: (suspend (MusicLink) -> Unit)? = null

    override suspend fun find(): MusicLink? {
        asked += "find"
        failure?.let { throw it }
        return found
    }

    override suspend fun check(address: String): MusicLink? {
        asked += "check $address"
        failure?.let { throw it }
        return speakers[address]
    }

    override fun open(link: MusicLink, onMoved: suspend (MusicLink) -> Unit): MusicSystem {
        opened += link
        this.onMoved = onMoved
        return TestMusicSystem().also { systems += it }
    }
}

