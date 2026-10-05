package uk.co.siland.culvery

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import kotlin.math.roundToInt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The launcher icon and its themed (monochrome) form, pinned as images. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherIconTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun drawable(id: Int): Drawable = checkNotNull(ContextCompat.getDrawable(context, id)).mutate()

    private fun snap(name: String, icon: Drawable, sizeDp: Int) {
        val px = (sizeDp * context.resources.displayMetrics.density).roundToInt()
        icon.toBitmap(px, px).captureRoboImage("src/test/screenshots/$name.png")
    }

    /** As a themed-icon launcher shows it: the monochrome layer tinted on a plain background. */
    private fun themed(background: Int, tint: Int) =
        AdaptiveIconDrawable(ColorDrawable(background), drawable(R.drawable.ic_launcher_monochrome).apply { setTint(tint) })

    @Test
    fun theAppUsesTheAdaptiveIconWithAThemedLayer() {
        assertThat(context.applicationInfo.icon).isEqualTo(R.mipmap.ic_launcher)
        for (id in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = drawable(id) as AdaptiveIconDrawable
            assertThat(icon.monochrome).isNotNull()
            assertThat((icon.background as ColorDrawable).color).isEqualTo(context.getColor(R.color.ic_launcher_background))
        }
    }

    /** The whole 108 dp canvas, unmasked: everything sits inside the 66 dp safe circle. */
    @Test
    fun layers108() = snap(
        "launcher_icon_layers_108",
        LayerDrawable(arrayOf(ColorDrawable(context.getColor(R.color.ic_launcher_background)), drawable(R.drawable.ic_launcher_foreground))),
        sizeDp = 108,
    )

    @Test
    fun icon108() = snap("launcher_icon_108", drawable(R.mipmap.ic_launcher), sizeDp = 108)

    @Test
    fun icon48() = snap("launcher_icon_48", drawable(R.mipmap.ic_launcher), sizeDp = 48)

    @Test
    fun themedLight48() = snap("launcher_icon_themed_light_48", themed(THEMED_LIGHT_BACKGROUND, THEMED_LIGHT_TINT), sizeDp = 48)

    @Test
    fun themedDark48() = snap("launcher_icon_themed_dark_48", themed(THEMED_DARK_BACKGROUND, THEMED_DARK_TINT), sizeDp = 48)

    private companion object {
        // Typical Material You pairs a launcher picks for themed icons in light and dark mode.
        const val THEMED_LIGHT_BACKGROUND = 0xFFD7E8DE.toInt()
        const val THEMED_LIGHT_TINT = 0xFF0F3324.toInt()
        const val THEMED_DARK_BACKGROUND = 0xFF253530.toInt()
        const val THEMED_DARK_TINT = 0xFFC2E9D5.toInt()
    }
}
