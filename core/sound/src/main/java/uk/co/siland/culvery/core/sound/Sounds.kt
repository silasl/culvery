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

/** The sounds in [root], then those in its theme subfolders (`halloween/`), each group by path. A repeated name keeps the first. */
fun readSoundsFolder(root: File?): List<Sound> {
    if (root == null || !root.isDirectory) return emptyList()
    val themeFolders = SeasonalTheme.entries.associateBy { it.name.lowercase() }
    val found = root.listFiles().orEmpty().flatMap { entry ->
        when {
            entry.isFile -> listOf(entry to null)
            entry.isDirectory && entry.name in themeFolders ->
                entry.listFiles().orEmpty().map { it to themeFolders.getValue(entry.name) }
            else -> emptyList()
        }
    }
    return found
        .filter { (file, _) -> file.isFile && !file.name.startsWith(".") && file.extension.lowercase() in SOUND_EXTENSIONS }
        .map { (file, theme) ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            Sound("file:$path", file.nameWithoutExtension, SoundSource.OnTablet(file), theme)
        }
        .sortedWith(compareBy<Sound> { it.theme != null }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.id })
        .distinctBy { it.name }
}

/** The name [readSoundsFolder] gives the file a `file:` [id] names, so a file that has gone can still be named; null for any other id. */
fun soundNameFromId(id: String): String? =
    if (id.startsWith("file:")) id.substringAfterLast('/').removePrefix("file:").substringBeforeLast('.') else null

/** What a pad's first default was before the clip moved to the tablet (18 §4.2): now the tablet sound named [SAD_TROMBONE_NAME], if there is one. */
const val SAD_TROMBONE_ID = "builtin:sad_trombone"
private const val SAD_TROMBONE_NAME = "Sad trombone"

/** The sound [id] names among these, or null for none: [SAD_TROMBONE_ID] finds the tablet's Sad trombone, and a gone file finds nothing. */
fun List<Sound>.soundFor(id: String?): Sound? = when (id) {
    null -> null
    SAD_TROMBONE_ID -> firstOrNull { it.onTablet && it.name.equals(SAD_TROMBONE_NAME, ignoreCase = true) }
    else -> firstOrNull { it.id == id }
}
