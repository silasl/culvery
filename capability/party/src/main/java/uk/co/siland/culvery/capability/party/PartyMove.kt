package uk.co.siland.culvery.capability.party

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import uk.co.siland.culvery.core.house.HouseModes

/**
 * Party mode's choices from 13 moved into the modes store as its first stored mode (15b §4.4, Ruling 5). It runs while
 * the file has no `modes` key, so a fresh install gets Party mode with nothing to do. It never throws: a bad old entry is
 * skipped, so the move can't fail again at every start.
 */
internal object PartyMove : DataMigration<Preferences> {
    private const val NAME = "Party mode"
    private val PICK = stringPreferencesKey("music.pick")
    private val PICK_NAME = stringPreferencesKey("music.pick_name")
    private val ROOMS = stringSetPreferencesKey("music.rooms")
    private val SCENES = stringSetPreferencesKey("scenes")

    override suspend fun shouldMigrate(currentData: Preferences) = ModeKeys.MODES !in currentData

    override suspend fun migrate(currentData: Preferences): Preferences {
        val next = currentData.toMutablePreferences()
        val party = StoredMode(
            HouseModes.PARTY, NAME, PARTY_ICON, showOnHome = true, needsPin = true,
            music = next.read(PICK)?.let { id -> next.read(PICK_NAME)?.let { MusicChoice(id, it) } },
            speakers = next.read(ROOMS)?.mapNotNull(ModeKeys::parseRoom),
            lights = next.read(SCENES).orEmpty().mapNotNull(ModeKeys::parseRoomChoice),
        )
        for (key in listOf(PICK, PICK_NAME, ROOMS, SCENES)) next.remove(key)
        next.writeModes(listOf(party))
        if (next.read(ModeKeys.ON) == true) next[ModeKeys.RECORD_MODE] = HouseModes.PARTY
        return next.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    /** A value of another type than [key]'s reads as none. */
    private inline fun <reified T> Preferences.read(key: Preferences.Key<T>): T? = asMap()[key] as? T
}
