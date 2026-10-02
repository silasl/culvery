package uk.co.siland.culvery.provider.weather_openmeteo

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody

internal class Answer(val code: Int, val body: String)

/** A body longer than the caller allows. An IOException, so the caller treats it as a failed call. */
internal class BodyTooLarge : IOException("The answer is longer than allowed")

/** Reading the answer failed in a way that isn't I/O. No cause: its text could quote the body. */
internal class ReadFailed : IOException("Reading the answer failed")

/**
 * Enqueues the call and suspends until its whole body is in, reading it on OkHttp's thread (3a's `Call.await`):
 * cancelling the coroutine cancels the call, which ends a stalled read at once. With [maxBytes], a longer body is
 * [BodyTooLarge], read no further than one byte past the limit. Resumes with the body, or an IOException
 * ([BodyTooLarge], [ReadFailed], or OkHttp's own).
 */
internal suspend fun Call.await(maxBytes: Long? = null): Answer = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val answer = try {
                    response.use { Answer(it.code, it.body?.let { body -> read(body, maxBytes) }.orEmpty()) }
                } catch (e: IOException) {
                    // After a cancel this is the closed socket and the continuation is already cancelled.
                    cont.resumeWithException(e)
                    return
                } catch (e: Throwable) {
                    // The caller's contract is IOException or the body (§7.2): anything else is a failed read.
                    cont.resumeWithException(ReadFailed())
                    return
                }
                cont.resume(answer)
            }
        },
    )
}

private fun read(body: ResponseBody, maxBytes: Long?): String {
    if (maxBytes == null) return body.string()
    if (body.contentLength() > maxBytes) throw BodyTooLarge()
    val source = body.source()
    if (source.request(maxBytes + 1)) throw BodyTooLarge()
    return source.buffer.readUtf8()
}
