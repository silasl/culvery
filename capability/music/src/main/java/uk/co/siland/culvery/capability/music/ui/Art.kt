package uk.co.siland.culvery.capability.music.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.music.Speakers
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhIcon
import uk.co.siland.culvery.core.ui.Icons

/** Where the screens get art; none by default, so a preview or a test draws the placeholder. */
fun interface ArtSource {
    suspend fun load(url: String): ImageBitmap?
}

val LocalArtSource = staticCompositionLocalOf { ArtSource { null } }

/** About twice the card's 72 dp art at the tablet's density. */
internal const val ART_PX = 256
private const val ART_KEPT = 24

/** Art in memory only (6 §4.2): the last few pictures, decoded off the main thread at about the size shown. */
@Singleton
class ArtCache internal constructor(private val bytes: suspend (String) -> ByteArray?) : ArtSource {
    @Inject
    constructor(speakers: Speakers) : this({ url -> speakers.system.value?.art(url) })

    private val cache = LruCache<String, ImageBitmap>(ART_KEPT)

    override suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val data = bytes(url) ?: return null
        val image = withContext(Dispatchers.Default) { decodeArt(data, ART_PX) } ?: return null
        cache.put(url, image)
        return image
    }
}

/** Bounds first, then sampled down by halves while still at least [px]; null for anything that isn't a picture or won't fit in memory. */
internal fun decodeArt(data: ByteArray, px: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val longer = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longer / (sample * 2) >= px) sample *= 2
    return try {
        BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    } catch (e: OutOfMemoryError) {
        null
    }
}

/** A group's or a Favourite's art, or a music note on `surf2` while there's none. Decorative: the words say what plays. */
@Composable
internal fun ArtImage(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val c = Culvery.colors
    val source = LocalArtSource.current
    val image by produceState<ImageBitmap?>(null, url, source) { value = url?.let { source.load(it) } }
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size).clip(RoundedCornerShape(MusicDimens.artRadius)).background(c.surf2)) {
        val shown = image
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            HhIcon(Icons.MUSIC_NOTE, size = size / 2, tint = c.mute)
        }
    }
}
