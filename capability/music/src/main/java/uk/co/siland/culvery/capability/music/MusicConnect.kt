package uk.co.siland.culvery.capability.music

import android.util.Log
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.homeNetworkHost

private const val TAG = "MusicConnect"

sealed interface ConnectOutcome {
    data object Connected : ConnectOutcome

    data class Failed(val line: String) : ConnectOutcome
}

/**
 * Settings › Music's ways to the speakers (6 §4.1): Find Sonos and a typed address, through the bound provider (Sonos,
 * 6 §3.4). What is found is saved as the connection, and the Music tab appears.
 */
@Singleton
class MusicConnect internal constructor(private val provider: MusicProvider?, private val save: suspend (String, MusicLink) -> Unit) {
    @Inject
    constructor(speakers: Speakers, settings: MusicSettings) : this(speakers.providers.firstOrNull(), settings::connect)

    suspend fun find(): ConnectOutcome = connect { it.find() } ?: ConnectOutcome.Failed(NOTHING_ANSWERED)

    suspend fun enterAddress(typed: String): ConnectOutcome {
        val host = homeNetworkHost(typed) ?: return ConnectOutcome.Failed(TYPE_FOUR_NUMBERS)
        return connect { it.check(host) } ?: ConnectOutcome.Failed(NO_SPEAKER_THERE)
    }

    /** Null when [look] found nothing. */
    private suspend fun connect(look: suspend (MusicProvider) -> MusicLink?): ConnectOutcome? {
        val p = provider ?: return null
        val link = try {
            look(p)
        } catch (e: MusicUnreachableException) {
            return ConnectOutcome.Failed(problemLine(e.problem))
        } ?: return null
        return try {
            save(p.descriptor.id, link)
            ConnectOutcome.Connected
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't save the speakers (${e::class.simpleName})")
            ConnectOutcome.Failed(COULD_NOT_SAVE)
        }
    }
}
