package uk.co.siland.culvery.core.sound

import java.io.File
import uk.co.siland.culvery.core.ui.SeasonalTheme

sealed interface SoundSource {
    data class BuiltIn(val sound: BuiltInSound) : SoundSource
    data class OnTablet(val file: File) : SoundSource
}

/** [toString] leaves out the name and the file, so a log line never names a tablet sound. */
data class Sound(val id: String, val name: String, val source: SoundSource, val theme: SeasonalTheme?) {
    override fun toString() = "Sound(${if (source is SoundSource.OnTablet) "tablet" else id}, theme=$theme)"
}

val Sound.onTablet: Boolean get() = source is SoundSource.OnTablet

internal val SOUND_EXTENSIONS = setOf("wav", "mp3", "ogg")

const val UNNAMED_SOUND = "Unnamed sound"
const val SOUND_NAME_MAX = 40

/** [raw] with line breaks and tabs as spaces, other control and formatting characters dropped, and spaces collapsed; not cut. */
private val RUN_OF_SPACES = Regex(" +")

private fun tidySoundName(raw: String): String {
    val kept = StringBuilder()
    raw.codePoints().forEach { cp ->
        val type = Character.getType(cp).toByte()
        when {
            cp in 0x09..0x0D -> kept.append(' ')
            type == Character.CONTROL || type == Character.FORMAT -> Unit
            Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> kept.append(' ')
            else -> kept.appendCodePoint(cp)
        }
    }
    return kept.toString().replace(RUN_OF_SPACES, " ").trim().ifEmpty { UNNAMED_SOUND }
}

private fun cutSoundName(name: String): String =
    if (name.codePointCount(0, name.length) <= SOUND_NAME_MAX) name
    else name.substring(0, name.offsetByCodePoints(0, SOUND_NAME_MAX - 1)).trimEnd() + "…"

/** A file's name as Settings shows it: line breaks and tabs as spaces, other control and formatting characters gone, spaces collapsed, at most [SOUND_NAME_MAX] characters. */
fun cleanSoundName(raw: String): String = cutSoundName(tidySoundName(raw))

/**
 * The sounds in [root], then those in its theme subfolders (`halloween/`, whatever its case), each group by path. Within the main
 * folder and within each theme folder, a name repeated ignoring case keeps the first.
 */
fun readSoundsFolder(root: File?): List<Sound> {
    if (root == null || !root.isDirectory) return emptyList()
    val themeFolders = SeasonalTheme.entries.associateBy { it.name.lowercase() }
    val found = root.listFiles().orEmpty().flatMap { entry ->
        val theme = themeFolders[entry.name.lowercase()]
        when {
            entry.isFile -> listOf(entry to null)
            entry.isDirectory && theme != null -> entry.listFiles().orEmpty().map { it to theme }
            else -> emptyList()
        }
    }
    return found
        .filter { (file, _) -> file.isFile && !file.name.startsWith(".") && file.extension.lowercase() in SOUND_EXTENSIONS }
        .map { (file, theme) ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            val tidy = tidySoundName(file.nameWithoutExtension)
            Sound("file:$path", cutSoundName(tidy), SoundSource.OnTablet(file), theme) to tidy.lowercase()
        }
        .sortedWith(compareBy<Pair<Sound, String>> { it.first.theme != null }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.id }.thenBy { it.first.id })
        .distinctBy { (sound, key) -> sound.theme to key }
        .map { it.first }
}

/** The name [readSoundsFolder] gives the file a `file:` [id] names, so a file that has gone can still be named; null for any other id. */
fun soundNameFromId(id: String): String? =
    if (id.startsWith("file:")) cleanSoundName(id.substringAfterLast('/').removePrefix("file:").substringBeforeLast('.')) else null

/** What a pad's first default was before the clip moved to the tablet (18 §4.2): now the tablet sound named [SAD_TROMBONE_NAME], if there is one. */
const val SAD_TROMBONE_ID = "builtin:sad_trombone"
private const val SAD_TROMBONE_NAME = "Sad trombone"

/** The sound [id] names among these, or null for none: [SAD_TROMBONE_ID] finds the tablet's Sad trombone, and a gone file finds nothing. */
fun List<Sound>.soundFor(id: String?): Sound? = when (id) {
    null -> null
    SAD_TROMBONE_ID -> firstOrNull { it.onTablet && it.name.equals(SAD_TROMBONE_NAME, ignoreCase = true) }
    else -> firstOrNull { it.id == id }
}
