package uk.co.siland.culvery.provider.lights_hue

import java.io.IOException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import uk.co.siland.culvery.core.net.await
import uk.co.siland.culvery.core.net.homeHttpClient

/** A read or command gives up after this (7 §6). */
internal const val HUE_TIMEOUT_MS = 3_000L

/** Idle connections are kept this long only: well under any bridge's idle timeout, as no call is retried. */
internal const val IDLE_CONNECTIONS = 2
internal const val KEEP_ALIVE_MS = 5_000L

/** A whole bridge's rooms, lights and scenes run to tens of KB; past this isn't a bridge's answer. */
internal const val MAX_ANSWER_BYTES = 4L * 1024 * 1024

private const val KEY_HEADER = "hue-application-key"
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private val JSON_TYPE = "application/json".toMediaType()

/** An address that isn't a bridge on the home network: refused before anything is sent (7 §6). */
internal class NotABridge : IOException("Not a bridge on the home network")

/** The bridge that answered isn't the one asked for (7 §6). */
internal class WrongBridge : IOException("Another bridge answered")

/** The bridge no longer knows the key: a 403 or an unauthorised error (7 §6). */
internal class HueUnauthorised : IOException("The bridge no longer knows this key")

/** The bridge answered with an error status, [code]. */
internal class HueRefused(val code: Int) : Exception("The bridge refused ($code)")

/** What a paired call needs: where the bridge is, whose certificate to expect, and the application key. */
internal data class BridgeCall(val address: String, val bridgeId: String, val key: String) {
    override fun toString() = "BridgeCall"
}

/**
 * CLIP v2 to one Hue Bridge (7 §3.2), only where [accept] allows: a bridge on the home network, on port 443. Paired
 * calls trust only a certificate naming the bridge's id; commands go through [pacer]. Each call throws IOException when
 * the bridge doesn't answer (or isn't allowed, [NotABridge]; or names another id, [WrongBridge]), [HueUnauthorised] when
 * the key is no longer known, [HueRefused] for any other error status, and [HueJsonException] for an answer of the wrong
 * shape. Answers are read off the caller's thread.
 */
internal class HueClient(
    private val trust: HueTrust,
    private val pacer: HuePacer,
    private val accept: (String, Int) -> Boolean = ::isBridgeAuthority,
    timeoutMs: Long = HUE_TIMEOUT_MS,
    private val capture: (String, String) -> Unit = HueCapture::log,
) {
    // Over HTTP/2 a pool that keeps no idle connection can close one a new call has already taken.
    internal val pool = ConnectionPool(IDLE_CONNECTIONS, KEEP_ALIVE_MS, TimeUnit.MILLISECONDS)

    private val base = homeHttpClient(timeoutMs, pool).newBuilder().hostnameVerifier(trust.hostnames).build()

    private val named = ConcurrentHashMap<String, OkHttpClient>()
    private val streams = ConcurrentHashMap<String, OkHttpClient>()

    private val anyBridge: OkHttpClient by lazy {
        val (sockets, manager) = trust.forAnyBridge()
        base.newBuilder()
            .sslSocketFactory(sockets, manager)
            .addNetworkInterceptor { chain ->
                chain.request().tag(Leaf::class.java)?.cert =
                    chain.connection()?.handshake()?.peerCertificates?.firstOrNull() as? X509Certificate
                chain.proceed(chain.request())
            }
            .build()
    }

    private class Leaf {
        @Volatile var cert: X509Certificate? = null
    }

    private fun named(bridgeId: String): OkHttpClient = named.getOrPut(bridgeId) {
        val (sockets, manager) = trust.forBridge(bridgeId)
        base.newBuilder().sslSocketFactory(sockets, manager).build()
    }

    // The stream may be quiet for minutes: it keeps only the connect timeout.
    private fun stream(bridgeId: String): OkHttpClient = streams.getOrPut(bridgeId) {
        named(bridgeId).newBuilder().readTimeout(Duration.ZERO).callTimeout(Duration.ZERO).build()
    }

    /**
     * The bridge's id from `/api/config`, which needs no key, through the CA-only trust: the certificate's name must be
     * the id it answers, and [expectedId] when given (the mDNS record's), else [WrongBridge].
     */
    suspend fun config(address: String, expectedId: String? = null): String {
        val leaf = Leaf()
        val request = Request.Builder().url(url(address, "api", "config")).tag(Leaf::class.java, leaf).build()
        val bridgeId = send(anyBridge, request, "config", HueJson::config)
        val name = leaf.cert?.let(::commonName)
        if (!bridgeId.equals(name, ignoreCase = true)) throw WrongBridge()
        if (expectedId != null && !expectedId.equals(bridgeId, ignoreCase = true)) throw WrongBridge()
        return bridgeId
    }

    /** Asks for a key (7 §3.2), only from a certificate naming [bridgeId]; null when the bridge answered with an error. */
    suspend fun pair(address: String, bridgeId: String): String? {
        val request = Request.Builder().url(url(address, "api")).post(HueJson.pairBody().toRequestBody(JSON_TYPE)).build()
        return send(named(bridgeId), request, "pair", HueJson::pair)
    }

    suspend fun rooms(call: BridgeCall): List<HueRoom> = read(call, "room", HueJson::rooms)

    suspend fun groupedLights(call: BridgeCall): List<HueGroupedLight> = read(call, "grouped_light", HueJson::groupedLights)

    suspend fun lights(call: BridgeCall): List<HueLight> = read(call, "light", HueJson::lights)

    suspend fun scenes(call: BridgeCall): List<HueScene> = read(call, "scene", HueJson::scenes)

    suspend fun behaviors(call: BridgeCall): List<HueBehavior> = read(call, "behavior_instance", HueJson::behaviors)

    /** One automation; null when the bridge no longer lists it. */
    suspend fun behavior(call: BridgeCall, behaviorId: String): HueBehavior? = try {
        read(call, "behavior_instance", HueJson::behaviors, behaviorId).firstOrNull()
    } catch (e: HueRefused) {
        if (e.code == HTTP_NOT_FOUND) null else throw e
    }

    /** Each command answers true once sent, false when a newer one for the same target replaced it first. */
    suspend fun setOn(call: BridgeCall, groupedLightId: String, on: Boolean): Boolean =
        command(call, "grouped_light", groupedLightId, PaceKind.GROUP, HueJson.onBody(on))

    suspend fun setLightOn(call: BridgeCall, lightId: String, on: Boolean): Boolean =
        command(call, "light", lightId, PaceKind.LIGHT, HueJson.onBody(on))

    suspend fun setBrightness(call: BridgeCall, groupedLightId: String, percent: Int, turnOn: Boolean): Boolean =
        command(call, "grouped_light", groupedLightId, PaceKind.GROUP, HueJson.brightnessBody(percent, turnOn))

    suspend fun recall(call: BridgeCall, sceneId: String): Boolean =
        command(call, "scene", sceneId, PaceKind.GROUP, HueJson.recallBody())

    /** Mimic presence ([mimic]) by its trigger, any other automation by `enabled`; a newer one replaces only the same setting. */
    suspend fun setBehavior(call: BridgeCall, behaviorId: String, mimic: Boolean, on: Boolean): Boolean {
        val (setting, body) = if (mimic) "trigger" to HueJson.triggerBody(on) else "enabled" to HueJson.enabledBody(on)
        return command(call, "behavior_instance", behaviorId, PaceKind.LIGHT, body, "$behaviorId:$setting")
    }

    /** Drops every command still waiting its turn (7 §4.4). */
    fun dropWaiting() = pacer.dropWaiting()

    /** The bridge's event stream (7 §4.2): each payload's changes as they come, after [opened]; ends when the bridge closes it. */
    fun events(call: BridgeCall, opened: suspend () -> Unit = {}): Flow<List<HueEvent>> = channelFlow {
        val request = keyed(call, url(call.address, "eventstream", "clip", "v2")).header("Accept", "text/event-stream").build()
        val http = stream(call.bridgeId).newCall(request)
        val reader = launch(Dispatchers.IO) {
            try {
                http.execute().use {
                    if (!it.isSuccessful) check(it.code, HueJson.tree(it.peekBody(MAX_ANSWER_BYTES).string()))
                    opened()
                    val source = it.body?.source() ?: return@use
                    HueJson.sse(generateSequence { source.readUtf8Line() }).forEach { data ->
                        capture("event", data)
                        HueJson.events(data).takeIf { events -> events.isNotEmpty() }?.let { events -> send(events) }
                    }
                }
            } catch (e: IOException) {
                // Once the collector has left, the failure is only the cancelled call's.
                ensureActive()
                throw e.named()
            }
        }
        // A blocked read only ends when the call is cancelled.
        try {
            reader.join()
        } finally {
            http.cancel()
        }
    }

    private suspend fun <T> read(call: BridgeCall, type: String, parse: (JsonElement?) -> T, vararg id: String): T =
        send(named(call.bridgeId), keyed(call, url(call.address, "clip", "v2", "resource", type, *id)).build(), type, parse)

    /** [target] is what a newer command replaces: one setting of one resource. */
    private suspend fun command(call: BridgeCall, type: String, id: String, kind: PaceKind, body: String, target: String = id): Boolean {
        val request = keyed(call, url(call.address, "clip", "v2", "resource", type, id)).put(body.toRequestBody(JSON_TYPE)).build()
        val client = named(call.bridgeId)
        return pacer.send(target, kind) { send(client, request, "PUT $type") {} }
    }

    private fun keyed(call: BridgeCall, url: HttpUrl): Request.Builder = Request.Builder().url(url).header(KEY_HEADER, call.key)

    private suspend fun <T> send(client: OkHttpClient, request: Request, what: String, parse: (JsonElement?) -> T): T {
        val answer = try {
            client.newCall(request).await(MAX_ANSWER_BYTES)
        } catch (e: IOException) {
            throw e.named()
        }
        return withContext(Dispatchers.Default) {
            val text = answer.text
            capture(what, text)
            val root = HueJson.tree(text)
            check(answer.code, root)
            parse(root)
        }
    }

    private fun check(code: Int, root: JsonElement?) {
        if (code == HTTP_FORBIDDEN || HueJson.isUnauthorised(root)) throw HueUnauthorised()
        if (code !in 200..299 || HueJson.hasErrors(root)) throw HueRefused(code)
    }

    /** `https://host:port/segments…`, only for an address [accept] allows; anything else is [NotABridge]. */
    private fun url(address: String, vararg segments: String): HttpUrl {
        val (host, port) = bridgeAuthority(address)?.takeIf { (host, port) -> accept(host, port) } ?: throw NotABridge()
        return try {
            HttpUrl.Builder().scheme("https").host(host).port(port).apply { segments.forEach(::addPathSegment) }.build()
        } catch (e: IllegalArgumentException) {
            throw NotABridge()
        }
    }
}

/** A handshake refused because the certificate names another bridge is [WrongBridge]; anything else stays as it is. */
private fun IOException.named(): IOException =
    if (generateSequence<Throwable>(this) { it.cause }.any { it is WrongBridgeCertificate }) WrongBridge() else this
