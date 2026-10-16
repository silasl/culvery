package uk.co.siland.culvery.core.net

import java.io.IOException
import java.time.Duration
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * A client for a device on the home network: every timeout [timeoutMs], no redirect (one could lead off the home
 * network) and no retry (a command could be sent twice). [pool] is OkHttp's default when null.
 */
fun homeHttpClient(timeoutMs: Long, pool: ConnectionPool? = null): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofMillis(timeoutMs))
    .readTimeout(Duration.ofMillis(timeoutMs))
    .writeTimeout(Duration.ofMillis(timeoutMs))
    .callTimeout(Duration.ofMillis(timeoutMs))
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .apply { if (pool != null) connectionPool(pool) }
    .build()

/** A finished HTTP answer: its status [code] and whole body. */
class Answer(val code: Int, val bytes: ByteArray) {
    val text: String get() = bytes.toString(Charsets.UTF_8)
}

/** A body longer than the caller allows. An IOException, so the caller treats it as a failed call. */
class BodyTooLarge : IOException("The answer is longer than allowed")

/** Reading the answer failed in a way that isn't I/O. No cause: its text could quote the body. */
class ReadFailed : IOException("Reading the answer failed")

/**
 * Enqueues the call and suspends until its whole body is in, reading it on OkHttp's thread: cancelling the coroutine
 * cancels the call. A body over [maxBytes] is [BodyTooLarge], read no further than one byte past the limit.
 */
suspend fun Call.await(maxBytes: Long): Answer = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = try {
                    response.use { Answer(it.code, it.body?.let { body -> read(body, maxBytes) } ?: ByteArray(0)) }
                } catch (e: IOException) {
                    cont.resumeWithException(e)
                    return
                } catch (e: Exception) {
                    cont.resumeWithException(ReadFailed())
                    return
                }
                cont.resume(answer)
            }
        },
    )
}

private fun read(body: ResponseBody, maxBytes: Long): ByteArray {
    if (body.contentLength() > maxBytes) throw BodyTooLarge()
    val source = body.source()
    if (source.request(maxBytes + 1)) throw BodyTooLarge()
    return source.buffer.readByteArray()
}
