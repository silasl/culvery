package uk.co.siland.culvery.capability.door

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.AlertHost

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
    DoorSettings(doorSettingsStore(scope) { File(dir, "door_settings.preferences_pb") }, scope)

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
    var shown = 0
    var dismissed = 0
    var content: (@Composable () -> Unit)? = null

    override fun show(loud: Boolean, content: @Composable () -> Unit) {
        shown++
        this.content = content
    }

    override suspend fun quieted() = Unit

    override fun dismiss() {
        dismissed++
        content = null
    }
}

internal class FakeChime : Chime {
    val volumes = mutableListOf<Float>()

    /** What [play] reports: false for a chime that couldn't start. */
    var starts = true

    override fun play(volume: Float): Boolean {
        volumes += volume
        return starts
    }
}
