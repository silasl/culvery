package uk.co.siland.culvery.display

import uk.co.siland.culvery.core.access.AccessControl
import uk.co.siland.culvery.core.access.CorePermissions

internal const val CAMERA_PROMPT_MISSING = "Culvery can’t ask for the camera on this tablet. Allow the camera in Android’s Settings › Apps › Culvery › Permissions."

/** Asks a fresh Admin PIN, labelled "Allow the camera", before the tablet is unpinned. */
internal fun cameraUnpinGate(access: AccessControl): suspend () -> Boolean =
    { access.authorise(CorePermissions.CONNECTIONS_MANAGE, label = ALLOW_THE_CAMERA) != null }

/** Android's camera prompt, unpinned while it shows as Choose home app's dialog is (8 §4.3). [mayUnpin] asks the PIN first; a refusal leaves Culvery pinned. */
internal class CameraPrompt(
    private val access: CameraAccess,
    private val mayUnpin: suspend () -> Boolean,
    private val openSystemScreen: (missing: String, launch: () -> Unit) -> Unit,
    private val returnToPinning: () -> Unit,
) {
    suspend fun request(launch: () -> Unit) {
        if (mayUnpin()) openSystemScreen(CAMERA_PROMPT_MISSING, launch)
    }

    /** Android can answer without showing its dialog, so Culvery may never pause and the next resume wouldn't pin it. */
    suspend fun answered(granted: Boolean, rationale: Boolean) {
        try {
            access.answered(granted, rationale)
        } finally {
            returnToPinning()
        }
    }
}
