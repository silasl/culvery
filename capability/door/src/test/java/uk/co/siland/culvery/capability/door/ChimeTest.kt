package uk.co.siland.culvery.capability.door

import android.media.AudioAttributes
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChimeTest {
    @Test
    fun theChimeIsANotificationEvent() = assertThat(CHIME_ATTRIBUTES.usage).isEqualTo(AudioAttributes.USAGE_NOTIFICATION_EVENT)
}
