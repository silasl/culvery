package uk.co.siland.culvery.capability.door

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.co.siland.culvery.capability.door.db.DoorDatabase
import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.Authorised
import uk.co.siland.culvery.core.access.CorePermissionSource
import uk.co.siland.culvery.core.access.Identified
import uk.co.siland.culvery.core.access.PermissionRegistry
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.Refusal
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.household.HouseholdRepository
import uk.co.siland.culvery.core.household.HouseholdZone
import uk.co.siland.culvery.core.household.db.HouseholdDatabase
import uk.co.siland.culvery.core.plugin.AlertHost
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.ui.SeasonalTheme
import uk.co.siland.culvery.core.ui.ShowingTheme

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
    DoorSettings(settingsDataStore(scope) { File(dir, "door_settings.preferences_pb") }, scope)

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
    var shown = 0
    var dismissed = 0
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

internal class FakeChime : Chime {
    val plays = mutableListOf<Pair<DoorSound, Float>>()
    val volumes: List<Float> get() = plays.map { it.second }

    /** What [play] reports: false for a chime that couldn't start. */
    var starts = true

    override fun play(sound: DoorSound, volume: Float): Boolean {
        plays += sound to volume
        return starts
    }
}

internal class FakeShowing(theme: SeasonalTheme? = null) : ShowingTheme {
    override val showing = MutableStateFlow(theme)
}

private val person = Person(PersonId("p"), "P", 0)

/**
 * A PIN pad answered by someone with [role] (null: cancelled); [asked] holds each call's permissions in order. It
 * answers whatever the Settings session holds, as a fresh-PIN permission does.
 */
internal class TestAccess(var role: Role?) : AccessControl {
    private val registry = PermissionRegistry(setOf(CorePermissionSource(), DoorPermissionSource()))
    val asked: MutableList<List<String>> = mutableListOf()

    override val session: StateFlow<Identified?> = MutableStateFlow(null)
    override val expiresAt: StateFlow<Long?> = MutableStateFlow(null)

    override suspend fun authorise(
        vararg anyOf: String,
        reason: PinReason,
        allow: (Identified, Set<String>) -> Boolean,
        refusal: Refusal,
        label: String?,
    ): Authorised? {
        asked += anyOf.toList()
        val role = role ?: return null
        val granted = anyOf.filter { registry.isGranted(role, it) }.toSet()
        return if (granted.isEmpty()) null else Authorised(person, role, granted)
    }

    override fun beginSetupSession(person: Identified) = Unit

    override fun endSetupSession() = Unit

    override fun touch(holdForEdits: Boolean) = Unit

    override fun checkExpiry() = Unit

    override fun lock() = Unit
}
