package uk.co.siland.culvery.capability.music.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** 6 §4.2: art decoded at about the size shown, kept in memory, fetched once. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtCacheTest {
    private fun png(width: Int, height: Int): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }.compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    /** 1 200 px sampled by 4 is 300: smaller, never below 256. */
    @Test
    fun aLargePictureIsDecodedSmallerButNotBelowTheSizeShown() {
        val image = decodeArt(png(1_200, 1_200), ART_PX)!!
        assertThat(image.width).isAtLeast(ART_PX)
        assertThat(image.width).isLessThan(ART_PX * 2)
    }

    @Test
    fun somethingThatIsNotAPictureIsNoArt() {
        assertThat(decodeArt("<html/>".toByteArray(), ART_PX)).isNull()
    }

    @Test
    fun artIsFetchedOnceAndKept() = runTest {
        var fetches = 0
        val cache = ArtCache {
            fetches++
            png(64, 64)
        }
        assertThat(cache.load("http://speaker/getaa?s=1")).isNotNull()
        assertThat(cache.load("http://speaker/getaa?s=1")).isNotNull()
        assertThat(fetches).isEqualTo(1)
    }

    /** Fails if two screens asking for the same picture at once fetch it twice. */
    @Test
    fun twoLoadsAtOnceFetchOnce() = runTest {
        var fetches = 0
        val cache = ArtCache {
            fetches++
            delay(100)
            png(64, 64)
        }
        val both = List(2) { async { cache.load("http://speaker/getaa?s=1") } }.awaitAll()
        assertThat(both.all { it != null }).isTrue()
        assertThat(fetches).isEqualTo(1)
    }

    @Test
    fun noBytesIsNoArtAndIsAskedAgain() = runTest {
        var fetches = 0
        val cache = ArtCache {
            fetches++
            null
        }
        assertThat(cache.load("u")).isNull()
        cache.load("u")
        assertThat(fetches).isEqualTo(2)
    }
}
