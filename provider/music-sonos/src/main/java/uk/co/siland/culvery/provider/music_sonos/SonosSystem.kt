package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.music.Favourite
import uk.co.siland.culvery.capability.music.FavouriteKind
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicRefusedException
import uk.co.siland.culvery.capability.music.MusicSystem
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.capability.music.NoLibraryException
import uk.co.siland.culvery.capability.music.NowPlaying
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.SpeakerGroup
import uk.co.siland.culvery.capability.music.SpeakerRoom
import uk.co.siland.culvery.core.plugin.WallClock

/** While the speakers stay unreachable, the automatic scan runs at most this often (6 §4.1). */
internal const val RESCAN_GAP_MS = 30_000L

/** A group, or its leader, isn't in the layout: regrouped in the Sonos app. I/O, so the layout is read again first. */
internal class GoneFromLayout : IOException("The group isn't in the layout")

/** A container, or a link to one, replaces the queue; anything else is set as the transport (6 §4.5). */
internal fun kindOf(item: DidlItem): FavouriteKind {
    val uri = item.uri.orEmpty()
    val container = item.metadataClass?.startsWith("object.container") == true ||
        uri.startsWith("x-rincon-cpcontainer:") || uri.startsWith(LIBRARY_PREFIX)
    return if (container) FavouriteKind.QUEUED else FavouriteKind.STREAM
}

/** A group's GetMediaInfo, read again only when its track or transport state changes (6 §4.2). */
private data class Media(val trackUri: String?, val state: PlayState, val info: MediaInfo)

private data class Playback(val state: PlayState, val nowPlaying: NowPlaying?, val artUrl: String?, val canSkip: Boolean, val volume: Int)

/**
 * One household's speakers through Sonos's local API (6 §3.2, §4): the layout from the saved speaker, each group's
 * playback from its leader, commands to the leader. When no speaker answers it reads the layout again and, failing that,
 * scans for the household (at most every 30 s) and saves where it found it; only then is there a [problem].
 *
 * The playback and layout checks may overlap (6 §4.2): every change to what the screen shows is made, and [groups]
 * published, under one lock.
 */
internal class SonosSystem(
    private val client: SonosClient,
    private val scan: suspend () -> List<String>,
    private val online: () -> Boolean,
    link: MusicLink,
    private val onMoved: suspend (MusicLink) -> Unit,
    private val clock: WallClock,
) : MusicSystem {
    private val _groups = MutableStateFlow<List<SpeakerGroup>>(emptyList())
    override val groups: StateFlow<List<SpeakerGroup>> = _groups.asStateFlow()

    private val _problem = MutableStateFlow<MusicProblem?>(null)
    override val problem: StateFlow<MusicProblem?> = _problem.asStateFlow()

    @Volatile private var link = link
    @Volatile private var favouriteItems: Map<String, DidlItem> = emptyMap()
    private var lastScanMillis: Long? = null
    private val recovering = Mutex()

    private val lock = Any()

    // Written only in change(); read anywhere.
    @Volatile private var layout: List<ZoneGroupInfo> = emptyList()

    // Guarded by lock.
    private val playback = HashMap<String, Playback>()
    private val roomVolumes = HashMap<String, Int>()
    private val started = HashMap<String, Long>()
    private val media = HashMap<String, Media>()

    override suspend fun checkLayout() {
        refusing { reaching { readLayout(link.address) } }
    }

    /** A leader that refuses, or answers in a shape that isn't Sonos's, leaves its group as it was; the others are read. */
    override suspend fun checkPlayback() {
        if (layout.isEmpty()) checkLayout()
        reaching { coroutineScope { layout.map { g -> async { skippingRefusal { readGroup(g) } } }.awaitAll() } }
    }

    override suspend fun playingNow(): List<SpeakerGroup> {
        if (layout.isEmpty()) checkLayout()
        val states = coroutineScope {
            layout.map { g -> async { g.id to quietly { client.transportInfo(g.leaderAddress()) } } }.awaitAll()
        }
        change {
            states.forEach { (id, state) ->
                if (state != null) record(id, playback[id]?.copy(state = state) ?: Playback(state, null, null, false, 0))
            }
        }
        val playing = states.filter { it.second?.isPlaying == true }.map { it.first }.toSet()
        return _groups.value.filter { it.id in playing }
    }

    override suspend fun roomVolume(room: SpeakerRoom): Int = toRooms { client.volume(memberAddress(room.id)) }

    override suspend fun play(group: SpeakerGroup) = command(group.id, { it.copy(state = PlayState.PLAYING) }) { client.play(it.leaderAddress()) }

    override suspend fun pause(group: SpeakerGroup) = command(group.id, { it.copy(state = PlayState.PAUSED) }) { client.pause(it.leaderAddress()) }

    override suspend fun next(group: SpeakerGroup) = command(group.id, { it }, resend = false) { client.next(it.leaderAddress()) }

    override suspend fun setGroupVolume(group: SpeakerGroup, percent: Int) {
        val volume = percent.coerceIn(0, 100)
        command(group.id, { it.copy(volume = volume) }) { client.setGroupVolume(it.leaderAddress(), volume) }
    }

    override suspend fun setRoomVolume(room: SpeakerRoom, percent: Int) {
        val volume = percent.coerceIn(0, 100)
        // Through toRooms, so a different household (whose layout is empty) is said rather than refused.
        val groupId = layout.firstOrNull { g -> g.members.any { it.uuid == room.id } }?.id ?: return toRooms { throw MusicRefusedException() }
        change { roomVolumes[room.id] = volume }
        command(groupId, { it }) { g -> client.setVolume(g.members.firstOrNull { it.uuid == room.id }?.address ?: throw GoneFromLayout(), volume) }
    }

    /** 6 §4.4: the library checked first, then its five steps; the queue is the source before the play mode, which a stream refuses. */
    override suspend fun shuffleEverything(group: SpeakerGroup) = command(group.id, { it }) { g ->
        val leader = g.leader()
        if (client.browse(leader.address, LIBRARY, 0, 1).total == 0) throw NoLibraryException()
        client.removeAllTracks(leader.address)
        client.addToQueue(leader.address, "$LIBRARY_PREFIX${leader.uuid}#$LIBRARY", "")
        client.setTransportUri(leader.address, queueUri(leader.uuid), "")
        client.setPlayMode(leader.address, SHUFFLE_NOREPEAT)
        client.play(leader.address)
    }

    override suspend fun favourites(): List<Favourite> {
        val items = toRooms { client.browse(link.address, FAVOURITES, 0, FAVOURITES_PAGE).items }
        favouriteItems = items.associateBy { it.id }
        return items.map { Favourite(it.id, it.title, artUrl(it.artUri, link.address), kindOf(it)) }
    }

    /** 6 §4.5: a station is set and played; a playlist or album replaces the queue (§4.4 steps 1, 2, 3 and 5), leaving the play mode. */
    override suspend fun playFavourite(group: SpeakerGroup, favourite: Favourite) {
        val item = favouriteItems[favourite.id] ?: favourites().let { favouriteItems[favourite.id] } ?: throw MusicRefusedException()
        val uri = item.uri ?: throw MusicRefusedException()
        val metadata = item.metadata.orEmpty()
        command(group.id, { it }) { g ->
            val leader = g.leader()
            if (favourite.kind == FavouriteKind.QUEUED) {
                client.removeAllTracks(leader.address)
                client.addToQueue(leader.address, uri, metadata)
                client.setTransportUri(leader.address, queueUri(leader.uuid), "")
            } else {
                client.setTransportUri(leader.address, uri, metadata)
            }
            client.play(leader.address)
        }
    }

    /** Only a speaker's own /getaa art on the home network, or a stream's https art (6 §4.2, §6). */
    override suspend fun art(url: String): ByteArray? = if (artAllowed(url)) client.bytes(url) else null

    override fun useAddress(address: String) {
        link = link.copy(address = address)
    }

    private fun artAllowed(url: String): Boolean =
        url.startsWith("https://") || layout.any { g -> g.members.any { url.startsWith("http://${it.address}/getaa") } }

    /**
     * Shows [shown] at once (6 §4.3), sends the command to the group's leader, and reads that group again whatever happened,
     * so the screen shows what the speaker did.
     */
    private suspend fun command(
        groupId: String,
        shown: (Playback) -> Playback,
        resend: Boolean = true,
        send: suspend (ZoneGroupInfo) -> Unit,
    ) {
        sameHousehold()
        change { playback[groupId]?.let { record(groupId, shown(it)) } }
        try {
            toRooms(resend) { send(infoOf(groupId)) }
        } finally {
            withContext(NonCancellable) { quietly { readGroup(infoOf(groupId)) } }
        }
    }

    /**
     * Every read of, or command to, the household's rooms. While a different household answers, nothing is read from or
     * sent to it until the saved one answers again; the layout checks don't come here, as they are how it answers again.
     */
    private suspend fun <T> toRooms(resend: Boolean = true, block: suspend () -> T): T {
        sameHousehold()
        return refusing { reaching(resend = resend, block = block) }
    }

    private fun sameHousehold() {
        if (_problem.value == MusicProblem.OTHER_HOUSEHOLD) throw MusicUnreachableException(MusicProblem.OTHER_HOUSEHOLD)
    }

    /**
     * Runs [block]; when no speaker answers, finds the household again and runs it once more. Offline, or still nothing,
     * is reported only then (6 §4.1, §6). A group still missing after the layout is read again is refused. Unless
     * [resend], [block] runs again only when it failed before reaching a speaker: a Next whose answer was lost may have skipped.
     */
    private suspend fun <T> reaching(again: Boolean = true, resend: Boolean = true, block: suspend () -> T): T = try {
        block().also { reached() }
    } catch (e: IOException) {
        if (!again && e is GoneFromLayout) throw MusicRefusedException()
        if (!online()) fail(MusicProblem.OFFLINE)
        // It may have reached the speaker, so it fails this command only; the next check says whether the speakers answer.
        if (!resend && !neverReached(e)) throw MusicUnreachableException(MusicProblem.UNREACHABLE)
        if (!again || !recover()) fail(if (_problem.value == MusicProblem.OTHER_HOUSEHOLD) MusicProblem.OTHER_HOUSEHOLD else MusicProblem.UNREACHABLE)
        reaching(again = false, block = block)
    }

    private fun neverReached(e: IOException) =
        e is ConnectException || e is NoRouteToHostException || e is NotASpeaker || e is GoneFromLayout

    /** The layout from the saved speaker again; failing that, a scan, keeping the first speaker of this household. */
    private suspend fun recover(): Boolean = recovering.withLock {
        quietly { readLayout(link.address) }?.let { return@withLock it }
        val now = clock.nowMillis()
        if (lastScanMillis?.let { now - it in 0 until RESCAN_GAP_MS } == true) return@withLock false
        lastScanMillis = now
        val address = findHousehold() ?: return@withLock false
        link = MusicLink(link.householdId, address)
        onMoved(link)
        quietly { readLayout(address) } == true
    }

    /** Every scanned address asked at once; the first of this household to answer ends the rest (6 §4.1, §6). */
    private suspend fun findHousehold(): String? {
        val addresses = scan()
        var other = false
        val found = coroutineScope {
            val answers = Channel<Pair<String, String?>>(Channel.UNLIMITED)
            addresses.forEach { address -> launch { answers.send(address to client.householdAt(address)) } }
            repeat(addresses.size) {
                val (address, household) = answers.receive()
                if (household == link.householdId) {
                    coroutineContext.cancelChildren()
                    return@coroutineScope address
                }
                if (household != null) other = true
            }
            null
        }
        // The old rooms' addresses may now be the other household's (6 §6).
        if (found == null && other) otherHousehold()
        return found
    }

    /** False when [address] answers for another household, whose rooms are never shown or sent to (6 §6). */
    private suspend fun readLayout(address: String): Boolean {
        if (client.householdId(address) != link.householdId) {
            otherHousehold()
            return false
        }
        if (_problem.value == MusicProblem.OTHER_HOUSEHOLD) _problem.value = null
        val groups = client.zoneGroups(address)
        val volumes = coroutineScope {
            groups.flatMap { it.members }.map { m -> async { m.uuid to quietly { client.volume(m.address) } } }.awaitAll()
        }
        val ids = groups.map { it.id }.toSet()
        change {
            layout = groups
            volumes.forEach { (id, volume) -> if (volume != null) roomVolumes[id] = volume }
            playback.keys.retainAll(ids)
            started.keys.retainAll(ids)
            media.keys.retainAll(ids)
        }
        return true
    }

    /** 6 §3.1: what plays, from the queue (title and artist, Next) or a stream (the station and its programme). */
    private suspend fun readGroup(group: ZoneGroupInfo) = coroutineScope {
        val address = group.leaderAddress()
        val state = async { client.transportInfo(address) }
        val track = async { client.positionInfo(address) }
        val volume = async { client.groupVolume(address) }
        val t = track.await()
        val s = state.await()
        val known = synchronized(lock) { media[group.id] }?.takeIf { it.trackUri == t.uri && it.state == s }
        val m = known?.info ?: client.mediaInfo(address).also { synchronized(lock) { media[group.id] = Media(t.uri, s, it) } }
        val fromQueue = m.uri?.startsWith(QUEUE_PREFIX) == true
        val nowPlaying = if (fromQueue) {
            t.title?.let { NowPlaying.Track(it, t.artist, t.album) }
        } else {
            (m.title ?: t.title?.takeUnless { it == t.uri })?.let { NowPlaying.Radio(it, t.programme) }
        }
        val read = Playback(s, nowPlaying, artUrl(t.artUri ?: m.artUri, address), fromQueue, volume.await())
        change { record(group.id, read) }
    }

    /** Applies [block] to the state and publishes [groups] from it, as one step. */
    private inline fun change(block: () -> Unit) = synchronized(lock) {
        block()
        publish()
    }

    // Under lock.
    private fun record(groupId: String, now: Playback) {
        val before = playback.put(groupId, now)
        if (now.state.isPlaying && before?.state?.isPlaying != true) started[groupId] = clock.nowMillis()
    }

    // Under lock.
    private fun publish() {
        _groups.value = layout.map { g ->
            val p = playback[g.id]
            SpeakerGroup(
                id = g.id,
                rooms = g.members.map { SpeakerRoom(it.uuid, it.name, roomVolumes[it.uuid] ?: 0) },
                coordinatorId = g.coordinator,
                state = p?.state ?: PlayState.STOPPED,
                nowPlaying = p?.nowPlaying,
                artUrl = p?.artUrl,
                canSkip = p?.canSkip ?: false,
                volume = p?.volume ?: 0,
                startedAtMillis = started[g.id],
            )
        }
    }

    private fun otherHousehold() {
        _problem.value = MusicProblem.OTHER_HOUSEHOLD
        change {
            layout = emptyList()
            playback.clear()
            media.clear()
        }
    }

    private fun reached() {
        if (_problem.value == MusicProblem.UNREACHABLE || _problem.value == MusicProblem.OFFLINE) _problem.value = null
    }

    private fun fail(problem: MusicProblem): Nothing {
        _problem.value = problem
        throw MusicUnreachableException(problem)
    }

    private fun infoOf(groupId: String): ZoneGroupInfo = layout.firstOrNull { it.id == groupId } ?: throw GoneFromLayout()

    private fun ZoneGroupInfo.leader(): ZoneMember = members.firstOrNull { it.uuid == coordinator } ?: throw GoneFromLayout()

    private fun ZoneGroupInfo.leaderAddress(): String = leader().address

    private fun memberAddress(roomId: String): String =
        layout.firstNotNullOfOrNull { g -> g.members.firstOrNull { it.uuid == roomId } }?.address ?: throw GoneFromLayout()

    /** A relative art path is the speaker's; a stream's own https URL stays; anything else is no art (6 §4.2, §6). */
    private fun artUrl(uri: String?, address: String): String? = when {
        uri.isNullOrBlank() -> null
        uri.startsWith("/") -> "http://$address$uri"
        uri.startsWith("https://") -> uri
        else -> null
    }

    private suspend fun <T> refusing(block: suspend () -> T): T = try {
        block()
    } catch (e: SonosFault) {
        throw MusicRefusedException(e.code)
    } catch (e: SonosXmlException) {
        throw MusicRefusedException()
    }

    private suspend fun skippingRefusal(read: suspend () -> Unit) = try {
        read()
    } catch (e: SonosFault) {
        // Left as it was.
    } catch (e: SonosXmlException) {
        // Left as it was.
    }
}
