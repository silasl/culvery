package uk.co.siland.culvery.provider.music_sonos

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.ResponseBody

internal class Answer(val code: Int, val bytes: ByteArray) {
    val text: String get() = bytes.toString(Charsets.UTF_8)
}

/** A body longer than the caller allows. An IOException, so the caller treats it as a failed call. */
internal class BodyTooLarge : IOException("The answer is longer than allowed")

/** Reading the answer failed in a way that isn't I/O. No cause: its text could quote the body. */
internal class ReadFailed : IOException("Reading the answer failed")

/**
 * Enqueues the call and suspends until its whole body is in, reading it on OkHttp's thread (as the weather's): cancelling
 * the coroutine cancels the call. A body over [maxBytes] is [BodyTooLarge], read no further than one byte past the limit.
 */
internal suspend fun Call.await(maxBytes: Long): Answer = suspendCancellableCoroutine { cont ->
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
