package uk.co.siland.culvery.core.google

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import uk.co.siland.culvery.core.plugin.LocalShellNavigator

private const val TAG = "GoogleScreens"

/**
 * Play services' account chooser and consent screens over the kiosk (4c §5.3). [show] leaves pinning, opens them, and
 * returns to pinning exactly once however they end: `onAnswer` hears every result, a cancel included, and
 * `onNotOpened` hears a launch that threw.
 */
class GoogleScreens internal constructor(private val open: (PendingIntent) -> Unit) {
    fun show(intent: PendingIntent) = open(intent)
}

@Composable
fun rememberGoogleScreens(onAnswer: (resultCode: Int, data: Intent?) -> Unit, onNotOpened: () -> Unit): GoogleScreens {
    val navigator = LocalShellNavigator.current
    val answered by rememberUpdatedState(onAnswer)
    val notOpened by rememberUpdatedState(onNotOpened)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Whatever the answer, Google's screens are over (or never opened): restore the kiosk.
        navigator.returnToPinning()
        answered(result.resultCode, result.data)
    }
    return remember(navigator, launcher) {
        GoogleScreens { intent ->
            // A pinned app can't open another app's screens; Culvery pins again when it is back in front.
            navigator.leavePinning()
            try {
                launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Google's screens couldn't open (${e::class.simpleName})")
                navigator.returnToPinning()
                notOpened()
            }
        }
    }
}
