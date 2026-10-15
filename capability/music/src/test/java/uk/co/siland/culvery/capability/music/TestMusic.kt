package uk.co.siland.culvery.capability.music

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
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

internal fun musicSettings(dir: File, scope: CoroutineScope) =
    MusicSettings(musicSettingsStore(scope) { File(dir, "music_settings.preferences_pb") }, scope)

/** A music system a test drives: its groups and problem, the calls made of it in order, and calls that fail or wait. */
internal class TestMusicSystem(groups: List<SpeakerGroup> = emptyList()) : MusicSystem {
    override val groups = MutableStateFlow(groups)
    override val problem = MutableStateFlow<MusicProblem?>(null)

    /** Written from whatever thread a check runs on; read by the test. */
    val calls: MutableList<String> = CopyOnWriteArrayList()

    /** Each room's volume as its speaker holds it, by room id; a room not here answers its [SpeakerRoom.volume]. */
    val roomVolumes = mutableMapOf<String, Int>()

    /** What a call throws, by its name as [calls] writes it ("roomVolume Kitchen-id"), every time. */
    val failures = mutableMapOf<String, Exception>()

    /** What a call throws the first time only. */
    val failOnce = mutableMapOf<String, Exception>()

    /** Holds a call, by its name, until completed. */
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** What playingNow() answers; null answers the playing ones of [groups]. */
    var playingNowAnswer: List<SpeakerGroup>? = null
    var favouriteList: List<Favourite> = emptyList()

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

    override suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite) = record("playFavourite ${group.id} ${favourite.id}")

    override suspend fun shuffleEverything(group: SpeakerGroup) = record("shuffleEverything ${group.id}")

    override suspend fun art(url: String): ByteArray? {
        record("art $url")
        return null
    }
}

/** A provider whose answers a test sets; it opens a [TestMusicSystem] and keeps each. */
internal class TestMusicProvider(id: String = "music.sonos") : MusicProvider {
    override val descriptor = ProviderDescriptor(id, "Sonos", Icons.SPEAKER, setOf(Feature.READ, Feature.WRITE))
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
