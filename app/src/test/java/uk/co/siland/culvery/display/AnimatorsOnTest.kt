package uk.co.siland.culvery.display

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowValueAnimator

@RunWith(AndroidJUnit4::class)
class AnimatorsOnTest {
    // Robolectric's setter is protected static.
    private fun durationScale(scale: Float) {
        ShadowValueAnimator::class.java.getDeclaredMethod("setDurationScale", Float::class.javaPrimitiveType)
            .apply { isAccessible = true }
            .invoke(null, scale)
    }

    @After
    fun restore() = durationScale(1f)

    @Test
    fun readsAndroidsSetting() {
        durationScale(0f)
        assertThat(AndroidAnimatorsOn().now()).isFalse()
        durationScale(1f)
        assertThat(AndroidAnimatorsOn().now()).isTrue()
    }
}
