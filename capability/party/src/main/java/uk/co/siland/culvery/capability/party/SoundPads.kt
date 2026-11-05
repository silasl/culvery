package uk.co.siland.culvery.capability.party

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.retryWithBackoff
import uk.co.siland.culvery.core.plugin.settingsDataStore
import uk.co.siland.culvery.core.sound.BuiltInSound
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.SoundLibrary
import uk.co.siland.culvery.core.sound.id
import uk.co.siland.culvery.core.ui.ShowingTheme

private const val FILE = "sound_pads"
private const val TAG = "SoundPadsStore"
private const val NONE = "none"

const val PAD_COUNT = 3

private val KEYS = (1..PAD_COUNT).map { stringPreferencesKey("pad_$it") }
private val DEFAULTS = listOf(BuiltInSound.SAD_TROMBONE.id, BuiltInSound.ORGAN.id, null)

/** Which sound each pad plays, by sound id, in a small DataStore file of its own (18 §4.2). Writes throw `IOException`. */
@Singleton
class SoundPadsStore(private val store: DataStore<Preferences>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(settingsDataStore { context.preferencesDataStoreFile(FILE) })

    /** Pads 1 to [PAD_COUNT] in order; null is None. */
    val pads: Flow<List<String?>> = store.data
        .retryWithBackoff { Log.w(TAG, "Couldn't read the sound pads (${it::class.simpleName}); retrying") }
        .map { prefs ->
            KEYS.zip(DEFAULTS) { key, default ->
                when (val id = prefs[key]) {
                    null -> default
                    NONE -> null
                    else -> id
                }
            }
        }
        .distinctUntilChanged()

    /** Sets [pad], 1 to [PAD_COUNT], to the sound [id], or to None with null. */
    suspend fun setPad(pad: Int, id: String?) {
        require(pad in 1..PAD_COUNT) { "No pad $pad" }
        store.edit { it[KEYS[pad - 1]] = id ?: NONE }
    }
}

/** A pad on Home: its number and what it plays. */
data class PadUi(val pad: Int, val sound: Sound)

/**
 * The pads Home shows (18 §4.2, §4.3, Ruling 2): pads 1 to [room] of [stored], pad 1 Happy Birthday on a [birthday], then
 * without those set to None or to a sound [library] no longer has.
 */
fun shownPads(stored: List<String?>, room: Int, library: (String?) -> Sound?, birthday: Boolean): List<PadUi> =
    stored.take(room).mapIndexedNotNull { i, id ->
        val pad = i + 1
        library(if (pad == 1 && birthday) BuiltInSound.HAPPY_BIRTHDAY.id else id)?.let { PadUi(pad, it) }
    }

/** The pads on Home as the stored choices, the tray's room, the sounds and the wall's birthday change. */
@Singleton
class SoundPads internal constructor(stored: Flow<List<String?>>, room: Flow<Int>, sounds: Flow<List<Sound>>, birthday: Flow<Boolean>) {
    @Inject
    constructor(store: SoundPadsStore, shelf: ModesShelf, library: SoundLibrary, showing: ShowingTheme) :
        this(store.pads, shelf.soundRoom, library.sounds, showing.birthdayShowing)

    val shown: Flow<List<PadUi>> = combine(stored, room, sounds, birthday) { ids, spots, all, isBirthday ->
        shownPads(ids, spots, { id -> all.firstOrNull { it.id == id } }, isBirthday)
    }.distinctUntilChanged()
}
