package uk.co.siland.culvery.provider.music_sonos

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicProvider
import uk.co.siland.culvery.capability.music.MusicSystem
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.Icons

const val SONOS_PROVIDER_ID = "music.sonos"

/** Sonos through its local API on the home Wi-Fi (6 §2.7, §3.2): no account, nothing secret. */
@Singleton
class SonosProvider internal constructor(
    private val client: SonosClient,
    private val scan: suspend () -> List<String>,
    private val online: () -> Boolean,
    private val clock: WallClock,
    private val port: Int = SONOS_PORT,
) : MusicProvider {
    @Inject
    constructor(@ApplicationContext context: Context, clock: WallClock) :
        this(SonosClient(sonosHttp()), SonosDiscovery(multicastLock(context))::scan, AndroidNetwork(context)::online, clock)

    override val descriptor = ProviderDescriptor(SONOS_PROVIDER_ID, "Sonos", Icons.SPEAKER, setOf(Feature.READ, Feature.WRITE))

    override suspend fun find(): MusicLink? {
        if (!online()) throw MusicUnreachableException(MusicProblem.OFFLINE)
        return client.householdsAt(scan()).firstOrNull()?.let { (address, household) -> MusicLink(household, address) }
    }

    override suspend fun check(address: String): MusicLink? {
        if (!online()) throw MusicUnreachableException(MusicProblem.OFFLINE)
        val speaker = "$address:$port"
        return client.householdAt(speaker)?.let { MusicLink(it, speaker) }
    }

    override fun open(link: MusicLink, onMoved: suspend (MusicLink) -> Unit): MusicSystem = SonosSystem(client, scan, online, link, onMoved, clock)
}
