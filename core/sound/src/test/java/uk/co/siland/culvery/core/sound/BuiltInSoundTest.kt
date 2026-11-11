package uk.co.siland.culvery.core.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.SeasonalTheme

@RunWith(AndroidJUnit4::class)
class BuiltInSoundTest {
    @Test
    fun idsAreBuiltinKeys() {
        assertThat(BuiltInSound.entries.map { it.id }).containsExactly(
            "builtin:classic", "builtin:organ", "builtin:happy_birthday", "builtin:christmas_music_box",
        ).inOrder()
    }

    @Test
    fun theOrganAndTheMusicBoxHaveTheirThemes() {
        assertThat(BuiltInSound.ORGAN.theme).isEqualTo(SeasonalTheme.HALLOWEEN)
        assertThat(BuiltInSound.CHRISTMAS_MUSIC_BOX.theme).isEqualTo(SeasonalTheme.CHRISTMAS)
        assertThat(BuiltInSound.CLASSIC.theme).isNull()
        assertThat(BuiltInSound.HAPPY_BIRTHDAY.theme).isNull()
    }

    @Test
    fun everySoundOpens() {
        val resources = ApplicationProvider.getApplicationContext<Context>().resources
        for (sound in BuiltInSound.entries) {
            resources.openRawResourceFd(sound.res).use { assertThat(it.length).isGreaterThan(0L) }
        }
    }
}
