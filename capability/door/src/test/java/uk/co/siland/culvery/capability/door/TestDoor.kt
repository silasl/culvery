package uk.co.siland.culvery.capability.door

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.core.access.test.FakeAccess
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.sound.PadStart
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.SoundLibrary
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.ShowingTheme
import uk.co.siland.culvery.core.ui.test.awaiting

internal fun doorDb(): DoorDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DoorDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** The household's zone with no home set: the device's own, as on a fresh install. */
internal fun householdZone(): HouseholdZone = HouseholdZone(
    HouseholdRepository(
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HouseholdDatabase::class.java).allowMainThreadQueries().build(),
    ),
)

internal fun doorSettings(dir: File, scope: CoroutineScope) =
    DoorSettings(doorDataStore(scope) { File(dir, "door_settings.preferences_pb") }, scope)

/** Wednesday 14 October 2026, 14:02 in London (13:02 UTC). */
internal const val NOW = 1_791_982_920_000L

internal fun ring(
    key: String = "k1",
    at: Long = NOW,
    device: String = "Front Door",
    snapshot: Snapshot? = null,
    unrecognised: Boolean = false,
    whenMillis: Long = NOW,
) = DoorNotice(key, at, DoorKind.RING, device, unrecognised, snapshot, whenMillis)

internal fun motion(key: String = "m1", at: Long = NOW, device: String = "Front Door") = DoorNotice(key, at, DoorKind.MOTION, device)

/** A snapshot that writes a few bytes, as a picture would. */
internal val aPicture = Snapshot { it.writeBytes(byteArrayOf(1, 2, 3)) }

/** A grey picture [width] × [height], for the scaling and the screenshots. */
internal fun picture(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }

internal fun File.names(): List<String> = listFiles()?.map { it.name }?.sorted().orEmpty()

/** An alert host a test drives: whether Culvery is in front, and what was shown and dismissed. */
internal class FakeAlertHost(inFront: Boolean = true) : AlertHost {
    override val inFront = MutableStateFlow(inFront)
    override val lowered = MutableStateFlow(false)
    override val showing = MutableStateFlow(false)
    @Volatile var shown = 0
    @Volatile var dismissed = 0
    var content: (@Composable () -> Unit)? = null

    /** Whether the last show was loud. */
    var loud: Boolean? = null

    /** Holds quieted() until completed; null returns at once. */
    var quietedGate: CompletableDeferred<Unit>? = null
    var quietedCalls = 0

    override fun show(loud: Boolean, content: @Composable () -> Unit) {
        shown++
        this.loud = loud
        this.content = content
    }

    override suspend fun quieted() {
        quietedCalls++
        quietedGate?.await()
    }

    override fun dismiss() {
        dismissed++
        content = null
    }
}

/** Records each play as the sound's id and the volume. */
internal class FakeChime : Chime {
    val plays = mutableListOf<Pair<String, Float>>()
    val volumes: List<Float> get() = plays.map { it.second }
    var hushes = 0
    var onHush: () -> Unit = {}

    override val previewing = MutableStateFlow<String?>(null)
    val previews = mutableListOf<Pair<String, Float>>()
    var stopPreviews = 0

    /** What [play] reports: false for a chime that couldn't start. */
    var starts = true

    /** True once [play] has started a sound; a preview is then refused as the doorbell's. */
    var doorbellPlaying = false

    /** What [preview] says when it doesn't start, and the results to give the next previews, in order. */
    var refusal = PadStart.FAILED
    val outcomes = ArrayDeque<PadStart>()

    override suspend fun play(sound: Sound, volume: Float): Boolean {
        plays += sound.id to volume
        if (starts) doorbellPlaying = true
        return starts
    }

    override fun hushPads() {
        hushes++
        onHush()
    }

    override suspend fun preview(sound: Sound, volume: Float): PadStart {
        previews += sound.id to volume
        if (doorbellPlaying) return PadStart.DOORBELL
        val result = outcomes.removeFirstOrNull() ?: if (starts) PadStart.STARTED else refusal
        if (result == PadStart.STARTED) previewing.value = sound.id
        return result
    }

    override fun stopPreview() {
        stopPreviews++
        previewing.value = null
    }
}

/** The real library over [folder] as the tablet's sounds folder, read as it is made; null is a tablet with no sounds folder. */
internal fun testSounds(folder: File? = null) = SoundLibrary({ folder }, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined).also {
    runBlocking { awaiting("the sounds folder's first read") { it.firstReadDone.first { done -> done } } }
}

/** Puts a made-up file at [path] in [folder]. */
internal fun tabletSound(folder: File, path: String) = folder.resolve(path).apply {
    parentFile!!.mkdirs()
    writeBytes(byteArrayOf(0))
}

internal class FakeShowing(theme: SeasonalTheme? = null) : ShowingTheme {
    override val showing = MutableStateFlow(theme)
    override val birthdayShowing = MutableStateFlow(false)
}

/** A PIN pad answered by someone with [role] (null: cancelled) over the Door permissions. */
internal fun TestAccess(role: Role?) = FakeAccess(role, DoorPermissionSource())

/**
 * An IO dispatcher that holds the [n]th dispatch after [holdDispatch] until [release], so a test can stop a store call at a known
 * point: [held] opens once it is waiting.
 */
internal class GatedIo : CoroutineDispatcher() {
    private val seen = AtomicInteger()
    private val gate = CountDownLatch(1)
    @Volatile private var holdAt = 0
    val held = CountDownLatch(1)

    fun holdDispatch(n: Int) {
        seen.set(0)
        holdAt = n
    }

    fun release() = gate.countDown()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val hold = holdAt != 0 && seen.incrementAndGet() == holdAt
        Dispatchers.IO.dispatch(context) {
            if (hold) {
                held.countDown()
                gate.await()
            }
            block.run()
        }
    }
}
