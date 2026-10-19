package uk.co.siland.culvery.display.camera

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/** Nothing the camera sees can leave the presence code, and nothing outside its package can reach a frame (8 §4.3). */
class CameraSealTest {
    // Unit tests run in the module's directory.
    private val main = File("src/main/java/uk/co/siland/culvery")
    private val camera = File(main, "display/camera")

    private fun cameraFiles() = camera.walk().filter { it.isFile && it.extension == "kt" }.toList()

    /** Fails if any presence file gains a way to write, send, encode or hand on an image. */
    @Test
    fun thePresenceCodeHasNoFileNetworkOrImagePath() {
        val files = cameraFiles() + File(main, "display/DisplayState.kt")
        assertThat(files.map { it.name }).containsAtLeast("FrameDiff.kt", "Presence.kt", "DisplayState.kt")
        val found = files.flatMap { file -> BANNED.filter { it in file.readText() }.map { "${file.name}: $it" } }
        assertWithMessage(found.joinToString("\n")).that(found).isEmpty()
    }

    /** Pin. Fails if a file outside the camera package names a frame, the frame source or a Camera2 reader. */
    @Test
    fun theCameraTypesStayInTheirPackage() {
        val outside = File("src/main").walk()
            .filter { it.isFile && it.extension == "kt" && !it.canonicalPath.startsWith(camera.canonicalPath + File.separator) }
            .toList()
        assertThat(outside.map { it.name }).contains("DisplayState.kt")
        val found = outside.flatMap { file -> SEALED.filter { it in file.readText() }.map { "${file.path}: $it" } }
        assertWithMessage(found.joinToString("\n")).that(found).isEmpty()
    }

    /** Fails if the camera package logs anything but the one warning naming the exception's class. */
    @Test
    fun theCameraPackageLogsOnlyTheOneWarning() {
        val calls = cameraFiles().flatMap { file -> LOG_CALL.findAll(file.readText()).map { it.value.trim() } }
        assertThat(calls).containsExactly("Log.w(TAG, \"The camera stopped (\${e::class.simpleName})\")")
    }

    /** Fails if a top-level declaration in the camera package is visible outside `:app`. */
    @Test
    fun everyCameraTypeIsInternal() {
        val declarations = cameraFiles().flatMap { file ->
            file.readLines().mapNotNull { DECLARATION.matchEntire(it) }.map { file.name to it }
        }
        assertThat(declarations.map { it.second.groupValues[3] })
            .containsAtLeast("Luma", "FrameDiff", "FrameSource", "Presence", "NoFrontCamera", "CameraStopped", "DIFF_WIDTH")
        val visible = declarations
            .filterNot { (_, match) -> match.groupValues[1].split(' ').any { it == "internal" || it == "private" } }
            .map { (name, match) -> "$name: ${match.value}" }
        assertWithMessage(visible.joinToString("\n")).that(visible).isEmpty()
    }

    private companion object {
        val BANNED = listOf(
            "java.io", "java.nio.file", "java.net", "android.net", "okhttp", "writeBytes", "writeText", "contentResolver",
            "ParcelFileDescriptor", "Bitmap", "compress(", "YuvImage", "ImageFormat.JPEG", "MediaStore", "ImageWriter",
            "MediaRecorder", "openFileOutput", "cacheDir", "filesDir", "getExternal", "DataStore", "SharedPreferences",
            "Socket", "Intent(", "sendBroadcast",
        )
        val SEALED = listOf("Luma", "FrameSource", "ImageReader", "CameraManager")
        val LOG_CALL = Regex("""\bLog\.\w+\(.*""")

        /** A column-0 declaration: its modifiers, its keyword and its name. */
        val DECLARATION = Regex(
            """^(?:@\w+(?:\([^)]*\))?\s+)*((?:[a-z]+\s+)*?)(fun interface|class|interface|object|fun|val|var)\s+(?:\w+\.)?`?(\w+).*""",
        )
    }
}
