package uk.co.siland.culvery.display

internal const val CAMERA_PROMPT_MISSING = "Culvery can’t ask for the camera on this tablet. Allow the camera in Android’s Settings › Apps › Culvery › Permissions."

/** Android's camera prompt, unpinned while it shows as Choose home app's dialog is (8 §4.3). */
internal class CameraPrompt(
    private val access: CameraAccess,
    private val openSystemScreen: (missing: String, launch: () -> Unit) -> Unit,
    private val returnToPinning: () -> Unit,
) {
    fun request(launch: () -> Unit) = openSystemScreen(CAMERA_PROMPT_MISSING, launch)

    /** Android can answer without showing its dialog, so Culvery may never pause and the next resume wouldn't pin it. */
    suspend fun answered(granted: Boolean, rationale: Boolean) {
        try {
            access.answered(granted, rationale)
        } finally {
            returnToPinning()
        }
    }
}
