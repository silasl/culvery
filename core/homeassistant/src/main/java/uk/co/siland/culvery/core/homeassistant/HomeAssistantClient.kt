package uk.co.siland.culvery.core.homeassistant

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import uk.co.siland.culvery.core.net.AndroidNetwork
import uk.co.siland.culvery.core.net.Answer
import uk.co.siland.culvery.core.net.await
import uk.co.siland.culvery.core.net.homeHttpClient

/** A REST call gives up after this (11 §4.3). */
const val COMMAND_TIMEOUT_MS = 5_000L

/** The socket pings this often, so a NAS that vanishes is noticed. */
const val PING_MS = 30_000L

/** A whole `GET /api/states` runs to some hundreds of KB; past this isn't Home Assistant's answer. */
const val MAX_ANSWER_BYTES = 4_000_000L

private const val TAG = "HomeAssistantClient"
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val SUBSCRIPTION_ID = 1
private const val NORMAL_CLOSURE = 1000
private val JSON_TYPE = "application/json".toMediaType()

/** Why a call to Home Assistant failed (11 §6). */
enum class HaProblem { UNREACHABLE, OFFLINE, TOKEN_REFUSED, BLOCKED, NOT_HOME_ASSISTANT, REFUSED }

/** A failed call. The message is the problem alone and there is never a cause: either could quote the address or the token. */
class HomeAssistantException(val problem: HaProblem) : Exception("Home Assistant: ${problem.name}")

interface HomeAssistantApi {
    /** `GET /api/`: returns when it answers as Home Assistant and takes the token. */
    suspend fun check(link: HomeAssistantLink)

    suspend fun states(link: HomeAssistantLink): List<EntityState>

    /** `GET /api/states/<id>`; null on 404. */
    suspend fun state(link: HomeAssistantLink, entityId: String): EntityState?

    /** `POST /api/services/<domain>/<service>` with `entity_id` and [data] in the body. */
    suspend fun callService(
        link: HomeAssistantLink,
        domain: String,
        service: String,
        entityId: String,
        data: Map<String, String> = emptyMap(),
    )

    /**
     * One socket: auth, `subscribe_entities` for [entityIds], then the followed entities after each event, a missing one
     * null. Ends by throwing [HomeAssistantException] when the socket closes.
     */
    fun follow(link: HomeAssistantLink, entityIds: Set<String>): Flow<Map<String, EntityState?>>
}

/**
 * Home Assistant's REST and WebSocket APIs (11 §4.2, §4.3). Every failure is a [HomeAssistantException] and logs the call
 * and its outcome only (11 §6). [http] follows no redirect and retries nothing.
 */
class HomeAssistantClient internal constructor(
    internal val http: OkHttpClient,
    private val online: () -> Boolean,
    private val capture: (HaCall, String) -> Unit,
) : HomeAssistantApi {
    @Inject
    constructor(@ApplicationContext context: Context) :
        this(homeHttpClient(COMMAND_TIMEOUT_MS), AndroidNetwork(context)::online, HaCapture::log)

    private val socketHttp = http.newBuilder().pingInterval(Duration.ofMillis(PING_MS)).build()

    override suspend fun check(link: HomeAssistantLink) {
        val answer = send(HaCall.CHECK, request(link, "api", ""))
        val running = answer.code != HTTP_NOT_FOUND && withContext(Dispatchers.Default) { HaJson.apiRunning(answer.text) }
        if (!running) refuse(HaCall.CHECK, answer, HaProblem.NOT_HOME_ASSISTANT)
    }

    override suspend fun states(link: HomeAssistantLink): List<EntityState> =
        read(HaCall.STATES, send(HaCall.STATES, request(link, "api", "states")), HaJson::states)

    override suspend fun state(link: HomeAssistantLink, entityId: String): EntityState? {
        val answer = send(HaCall.STATE, request(link, "api", "states", entityId), missingIsNull = true)
        return if (answer.code == HTTP_NOT_FOUND) null else read(HaCall.STATE, answer, HaJson::state)
    }

    override suspend fun callService(link: HomeAssistantLink, domain: String, service: String, entityId: String, data: Map<String, String>) {
        val body = buildJsonObject {
            put("entity_id", entityId)
            data.forEach { (name, value) -> put(name, value) }
        }
        send(HaCall.SERVICE, request(link, "api", "services", domain, service).post(body.toString().toRequestBody(JSON_TYPE)))
    }

    override fun follow(link: HomeAssistantLink, entityIds: Set<String>): Flow<Map<String, EntityState?>> = flow {
        val inbox = Channel<Frame>(Channel.UNLIMITED)
        val socket = socketHttp.newWebSocket(request(link, "api", "websocket").build(), listener(inbox))
        val current = mutableMapOf<String, EntityState>()
        try {
            for (frame in inbox) {
                if (frame is Frame.Ended) socketEnded(frame)
                val text = (frame as Frame.Text).text
                capture(HaCall.SOCKET, text)
                val entities = when (val message = socketMessage(text)) {
                    SocketMessage.AuthRequired -> {
                        socket.send(authMessage(link).toString())
                        null
                    }
                    SocketMessage.AuthOk -> {
                        socket.send(subscribeMessage(entityIds).toString())
                        null
                    }
                    SocketMessage.AuthInvalid -> socketFailure(HaProblem.TOKEN_REFUSED)
                    is SocketMessage.Result -> if (message.success) null else socketFailure(HaProblem.REFUSED)
                    is SocketMessage.Entities -> message
                    SocketMessage.Other -> null
                }
                if (entities != null) {
                    current.putAll(entities.added)
                    entities.changed.forEach { (id, change) -> current[id]?.let { current[id] = it.apply(change) } }
                    current.keys.removeAll(entities.removed)
                    emit(entityIds.associateWith { current[it] })
                }
            }
        } finally {
            socket.close(NORMAL_CLOSURE, null)
        }
    }

    private sealed interface Frame {
        class Text(val text: String) : Frame

        /** How the socket ended: an HTTP or close [code], or the [error] that broke it. Logged only if the collector is still there. */
        class Ended(val problem: HaProblem, val code: Int?, val error: Throwable?) : Frame
    }

    /** Hands the socket's messages and its end to the collector, which owns all the state. */
    private fun listener(inbox: Channel<Frame>) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            inbox.trySend(Frame.Text(text))
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            inbox.trySend(Frame.Ended(unreachableOrOffline(), code, null))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val problem = when (response?.code) {
                HTTP_UNAUTHORIZED -> HaProblem.TOKEN_REFUSED
                HTTP_FORBIDDEN -> HaProblem.BLOCKED
                else -> unreachableOrOffline()
            }
            inbox.trySend(Frame.Ended(problem, response?.code, t))
        }
    }

    private fun socketEnded(frame: Frame.Ended): Nothing {
        val what = HaCall.SOCKET
        val error = frame.error
        if (frame.code != null) {
            Log.w(TAG, "${what.label} ended (${frame.code})")
        } else if (error != null) {
            Log.w(TAG, "${what.label} failed (${error::class.simpleName})")
        }
        throw HomeAssistantException(frame.problem)
    }

    private fun authMessage(link: HomeAssistantLink): JsonObject = buildJsonObject {
        put("type", "auth")
        put("access_token", link.token)
    }

    private fun subscribeMessage(entityIds: Set<String>): JsonObject = buildJsonObject {
        put("id", SUBSCRIPTION_ID)
        put("type", "subscribe_entities")
        put("entity_ids", buildJsonArray { entityIds.forEach { add(JsonPrimitive(it)) } })
    }

    private fun socketMessage(text: String): SocketMessage = try {
        HaJson.socket(text)
    } catch (e: HaJsonException) {
        val what = HaCall.SOCKET
        Log.w(TAG, "${what.label} failed (${e::class.simpleName})")
        throw HomeAssistantException(HaProblem.UNREACHABLE)
    }

    private fun socketFailure(problem: HaProblem): Nothing {
        val what = HaCall.SOCKET
        Log.w(TAG, "${what.label} refused by Home Assistant")
        throw HomeAssistantException(problem)
    }

    private fun unreachableOrOffline() = if (online()) HaProblem.UNREACHABLE else HaProblem.OFFLINE

    /** The URL under the link's base, with the token; an empty last segment is a trailing slash. */
    private fun request(link: HomeAssistantLink, vararg segments: String): Request.Builder {
        // The base's own path is "/", one empty segment, which the first added segment replaces.
        val url = link.baseUrl.newBuilder().apply { segments.forEach(::addPathSegment) }.build()
        return Request.Builder().url(url).header("Authorization", "Bearer ${link.token}")
    }

    private suspend fun send(what: HaCall, builder: Request.Builder, missingIsNull: Boolean = false): Answer {
        val answer = try {
            http.newCall(builder.build()).await(MAX_ANSWER_BYTES)
        } catch (e: IOException) {
            Log.w(TAG, "${what.label} failed (${e::class.simpleName})")
            throw HomeAssistantException(unreachableOrOffline())
        }
        capture(what, answer.text)
        when {
            answer.code in 200..299 -> Unit
            answer.code == HTTP_UNAUTHORIZED -> refuse(what, answer, HaProblem.TOKEN_REFUSED)
            answer.code == HTTP_FORBIDDEN -> refuse(what, answer, HaProblem.BLOCKED)
            answer.code == HTTP_NOT_FOUND && (missingIsNull || what == HaCall.CHECK) -> Unit
            what == HaCall.SERVICE -> refuse(what, answer, HaProblem.REFUSED)
            else -> refuse(what, answer, HaProblem.UNREACHABLE)
        }
        return answer
    }

    private suspend fun <T> read(what: HaCall, answer: Answer, parse: (String) -> T): T = withContext(Dispatchers.Default) {
        try {
            parse(answer.text)
        } catch (e: HaJsonException) {
            Log.w(TAG, "${what.label} failed (${e::class.simpleName})")
            throw HomeAssistantException(HaProblem.UNREACHABLE)
        }
    }

    private fun refuse(what: HaCall, answer: Answer, problem: HaProblem): Nothing {
        Log.w(TAG, "${what.label} failed (${answer.code})")
        throw HomeAssistantException(problem)
    }
}
