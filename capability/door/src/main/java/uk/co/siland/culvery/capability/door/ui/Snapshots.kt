package uk.co.siland.culvery.capability.door.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.siland.culvery.capability.door.MAX_SNAPSHOT_PX

private const val TAG = "Snapshots"

/**
 * [file] read sampled down by powers of two to about [maxPx] on its longer side, the bounds read first; null when it
 * can't be read (pruned meanwhile) or memory runs out.
 */
internal fun decodeSnapshot(file: File, maxPx: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (e: OutOfMemoryError) {
    Log.w(TAG, "A door snapshot was too large to show (${e::class.simpleName})")
    null
}

/** A ring's picture, cropped to [modifier]'s size, read off the main thread; the words beside it say what it is. */
@Composable
fun SnapshotImage(file: File, modifier: Modifier = Modifier, maxPx: Int = MAX_SNAPSHOT_PX) {
    val image by produceState<ImageBitmap?>(null, file, maxPx) {
        value = withContext(Dispatchers.IO) { decodeSnapshot(file, maxPx)?.asImageBitmap() }
    }
    image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier) }
}
