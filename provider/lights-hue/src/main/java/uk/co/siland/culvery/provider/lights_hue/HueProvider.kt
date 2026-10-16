package uk.co.siland.culvery.provider.lights_hue

import android.content.Context
import android.net.nsd.NsdManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.lights.BridgeFound
import uk.co.siland.culvery.capability.lights.LightsLink
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsProvider
import uk.co.siland.culvery.capability.lights.LightsSystem
import uk.co.siland.culvery.capability.lights.LightsUnreachableException
import uk.co.siland.culvery.core.net.AndroidNetwork
import uk.co.siland.culvery.core.net.multicastLock
import uk.co.siland.culvery.core.plugin.Feature
import uk.co.siland.culvery.core.plugin.ProviderDescriptor
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.ui.Icons

const val HUE_PROVIDER_ID = "lights.hue"

/**
 * The Hue Bridge on the home Wi-Fi (7 §2.9, §3.2): no Hue account. A bridge is only given out, and only asked for a key,
 * once its certificate and its `/api/config` name the same id.
 */
@Singleton
class HueProvider internal constructor(
    private val client: HueClient,
    private val search: suspend (wanted: String?) -> List<BridgeFound>,
    private val online: () -> Boolean,
    private val clock: WallClock,
) : LightsProvider {
    @Inject
    constructor(@ApplicationContext context: Context, clock: WallClock) : this(
        HueClient(HueTrust(), HuePacer(now = SystemClock::elapsedRealtime)),
        mdnsSearch(context),
        AndroidNetwork(context)::online,
        clock,
    )

    override val descriptor = ProviderDescriptor(HUE_PROVIDER_ID, "Philips Hue", Icons.LIGHTBULB, setOf(Feature.READ, Feature.WRITE))

    override suspend fun find(): BridgeFound? {
        whenOnline()
        return search(null).firstNotNullOfOrNull { verified(it.address, it.bridgeId) }
    }

    override suspend fun check(address: String): BridgeFound? {
        whenOnline()
        return verified(address, expectedId = null)
    }

    /**
     * [bridge] is one [find] or [check] verified; the key is asked only of a certificate naming its id. Null when the
     * button wasn't pressed, or the bridge there isn't [bridge]; throws when nothing answers.
     */
    override suspend fun pair(bridge: BridgeFound): LightsLink? {
        whenOnline()
        return try {
            client.pair(bridge.address, bridge.bridgeId)?.let { LightsLink(bridge.bridgeId, bridge.address, it) }
        } catch (e: WrongBridge) {
            null
        } catch (e: HueJsonException) {
            null
        } catch (e: HueRefused) {
            null
        } catch (e: IOException) {
            throw LightsUnreachableException(LightsProblem.UNREACHABLE)
        }
    }

    override fun open(link: LightsLink, onMoved: suspend (LightsLink) -> Unit): LightsSystem =
        HueSystem(client, search, online, link, onMoved, clock)

    private fun whenOnline() {
        if (!online()) throw LightsUnreachableException(LightsProblem.OFFLINE)
    }

    /** The bridge at [address] by the id its certificate and config agree on, and [expectedId] when given; else null. */
    private suspend fun verified(address: String, expectedId: String?): BridgeFound? = try {
        BridgeFound(client.config(address, expectedId), address)
    } catch (e: IOException) {
        null
    } catch (e: HueRefused) {
        null
    }
}

/** NsdManager is looked up at the first search, not when Hilt builds the provider; without it nothing is found. */
private fun mdnsSearch(context: Context): suspend (String?) -> List<BridgeFound> {
    val discovery by lazy {
        context.getSystemService(NsdManager::class.java)?.let { HueDiscovery(it, multicastLock(context, LOCK_TAG)) }
    }
    return { wanted -> discovery?.search(wanted).orEmpty() }
}
