package uk.co.siland.culvery.provider.lights_hue

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import uk.co.siland.culvery.capability.lights.BridgeFound

/** How long a search listens (7 §3.2). */
internal const val SEARCH_MS = 5_000L

internal const val LOCK_TAG = "Culvery:HueSearch"

private const val TAG = "HueDiscovery"
private const val SERVICE_TYPE = "_hue._tcp"
private const val BRIDGE_ID_KEY = "bridgeid"

/** One resolved `_hue._tcp` service: its `bridgeid` record and its IPv4 address, either null when it had none. */
internal data class MdnsAnswer(val bridgeId: String?, val host: String?)

/** Each bridge once, in the order heard, by its record's id; only answers whose address [accept] allows on port 443 (7 §6). */
internal fun bridgesFrom(answers: List<MdnsAnswer>, accept: (String, Int) -> Boolean = ::isBridgeAuthority): List<BridgeFound> = answers
    .mapNotNull { answer ->
        val id = answer.bridgeId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val host = answer.host?.takeIf { accept(it, HUE_PORT) } ?: return@mapNotNull null
        BridgeFound(id, host)
    }
    .distinctBy { it.bridgeId }

/**
 * Resolves services one at a time, as Android allows: each waits until the one before has answered, and one refused as
 * busy goes to the back of the queue. A service is resolved once however often it is heard.
 */
internal class ResolveQueue(
    private val resolve: (service: NsdServiceInfo, onDone: (MdnsAnswer?) -> Unit, onBusy: () -> Unit) -> Unit,
) {
    private val lock = Any()
    private val waiting = ArrayDeque<NsdServiceInfo>()
    private val heard = HashSet<String>()
    private val answers = mutableListOf<MdnsAnswer>()
    private var resolving = false
    private var closed = false

    fun add(service: NsdServiceInfo) {
        synchronized(lock) {
            if (closed || !heard.add(service.serviceName.orEmpty())) return
            waiting.addLast(service)
        }
        next()
    }

    fun answers(): List<MdnsAnswer> = synchronized(lock) { answers.toList() }

    /** Starts nothing more; an answer still to come is ignored. */
    fun close() = synchronized(lock) {
        closed = true
        waiting.clear()
    }

    private fun next() {
        val service = synchronized(lock) {
            if (resolving || closed) return
            waiting.removeFirstOrNull()?.also { resolving = true } ?: return
        }
        val settled = AtomicBoolean(false)
        resolve(
            service,
            { answer ->
                if (settled.compareAndSet(false, true)) {
                    synchronized(lock) {
                        if (!closed && answer != null) answers += answer
                        resolving = false
                    }
                    next()
                }
            },
            {
                if (settled.compareAndSet(false, true)) {
                    synchronized(lock) {
                        if (!closed) waiting.addLast(service)
                        resolving = false
                    }
                    next()
                }
            },
        )
    }
}

/**
 * Finds Hue Bridges on the home Wi-Fi by mDNS (7 §3.2), never through Hue's cloud: `_hue._tcp` for [SEARCH_MS] under
 * the multicast [lock], each service resolved for its `bridgeid` record and IPv4 address. Finds nothing rather than
 * failing; the caller says nothing answered.
 */
internal class HueDiscovery(
    private val nsd: NsdManager,
    private val lock: () -> AutoCloseable,
    private val sdk: Int = Build.VERSION.SDK_INT,
) {
    suspend fun search(): List<BridgeFound> = lock().use {
        val callbacks = ConcurrentHashMap.newKeySet<NsdManager.ServiceInfoCallback>()
        val queue = if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ResolveQueue { service, onDone, onBusy -> resolveByCallback(service, callbacks, onDone, onBusy) }
        } else {
            ResolveQueue(::resolveOnce)
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(service: NsdServiceInfo) = queue.add(service)

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "The bridge search didn't start")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceLost(service: NsdServiceInfo) = Unit
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            delay(SEARCH_MS)
        } finally {
            queue.close()
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                // The search never started.
            }
            if (sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) callbacks.toList().forEach { unregister(it, callbacks) }
        }
        bridgesFrom(queue.answers())
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun resolveByCallback(
        service: NsdServiceInfo,
        callbacks: MutableSet<NsdManager.ServiceInfoCallback>,
        onDone: (MdnsAnswer?) -> Unit,
        onBusy: () -> Unit,
    ) {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                callbacks.remove(this)
                failed(errorCode, onDone, onBusy)
            }

            override fun onServiceUpdated(info: NsdServiceInfo) {
                val host = info.hostAddresses.firstOrNull { it is Inet4Address } ?: return
                if (unregister(this, callbacks)) onDone(answer(info, host))
            }

            override fun onServiceLost() {
                if (unregister(this, callbacks)) onDone(null)
            }

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        callbacks += callback
        nsd.registerServiceInfoCallback(service, Runnable::run, callback)
    }

    /** True when [callback] was still registered. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun unregister(callback: NsdManager.ServiceInfoCallback, callbacks: MutableSet<NsdManager.ServiceInfoCallback>): Boolean {
        if (!callbacks.remove(callback)) return false
        try {
            nsd.unregisterServiceInfoCallback(callback)
        } catch (e: IllegalArgumentException) {
            // Android dropped it first.
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun resolveOnce(service: NsdServiceInfo, onDone: (MdnsAnswer?) -> Unit, onBusy: () -> Unit) {
        // The only resolver below API 34.
        nsd.resolveService(
            service,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = failed(errorCode, onDone, onBusy)

                // Below API 34 the resolved address is only here.
                override fun onServiceResolved(info: NsdServiceInfo) = onDone(answer(info, info.host))
            },
        )
    }

    private fun failed(errorCode: Int, onDone: (MdnsAnswer?) -> Unit, onBusy: () -> Unit) {
        if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) return onBusy()
        Log.w(TAG, "A bridge didn't resolve")
        onDone(null)
    }

    private fun answer(info: NsdServiceInfo, host: InetAddress?) = MdnsAnswer(
        info.attributes[BRIDGE_ID_KEY]?.toString(Charsets.US_ASCII),
        (host as? Inet4Address)?.hostAddress,
    )
}
