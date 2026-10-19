package uk.co.siland.culvery.display

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import uk.co.siland.culvery.core.plugin.ApplicationScope

/** Whether Culvery may use the front camera (8 §4.3). */
enum class CameraState { NO_CAMERA, NOT_ASKED, REFUSED, ALLOWED }

/** Android stops showing its prompt after a refusal with no rationale to give, so asked-and-no-rationale is a refusal for good. */
fun cameraState(hasFrontCamera: Boolean, granted: Boolean, asked: Boolean, rationale: Boolean): CameraState = when {
    !hasFrontCamera -> CameraState.NO_CAMERA
    granted -> CameraState.ALLOWED
    asked && !rationale -> CameraState.REFUSED
    else -> CameraState.NOT_ASKED
}

/** The camera is looked at only when it is allowed and the switch is on (on until touched). */
fun DisplayPrefs.wakesByCamera(state: CameraState): Boolean = state == CameraState.ALLOWED && (wakeByCamera ?: WAKE_BY_CAMERA_DEFAULT)

@Singleton
class CameraAccess internal constructor(
    private val hasFrontCamera: () -> Boolean,
    private val permissionGranted: () -> Boolean,
    private val settings: DisplaySettings,
    scope: CoroutineScope,
) {
    @Inject
    constructor(@ApplicationContext context: Context, settings: DisplaySettings, @ApplicationScope scope: CoroutineScope) : this(
        { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) },
        { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED },
        settings,
        scope,
    )

    private val frontCamera by lazy(hasFrontCamera)

    // Null until first read, in the scope rather than on the main thread at start-up: both checks ask the system.
    private val permission = MutableStateFlow<Pair<Boolean, Boolean>?>(null)
    private val asks = Channel<Unit>(Channel.CONFLATED)

    /** [CameraState.NOT_ASKED] for the moment before the first reading. */
    val state: StateFlow<CameraState> = combine(settings.current, permission) { prefs, answer ->
        val (isGranted, rationale) = answer ?: (permissionGranted() to false)
        cameraState(frontCamera, isGranted, prefs.cameraAsked, rationale)
    }.stateIn(scope, SharingStarted.Eagerly, CameraState.NOT_ASKED)

    /** The activity shows Android's camera prompt for each item. */
    val requests: Flow<Unit> = asks.receiveAsFlow()

    /** Reads the permission again, as on resume, when it may have been changed in Android's settings. */
    fun refresh(rationale: Boolean) {
        permission.value = permissionGranted() to rationale
    }

    fun ask() {
        asks.trySend(Unit)
    }

    /** Android's prompt was answered; the ask is saved first, so that a refusal reads as one. */
    suspend fun answered(granted: Boolean, rationale: Boolean) {
        settings.setCameraAsked()
        permission.value = granted to rationale
    }
}
