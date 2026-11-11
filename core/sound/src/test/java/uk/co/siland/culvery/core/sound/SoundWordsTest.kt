package uk.co.siland.culvery.core.sound

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test
import uk.co.siland.culvery.core.ui.SeasonalTheme

class SoundWordsTest {
    @Test
    fun couldntPlayNamesTheFix() {
        val tablet = Sound("file:Test Honk.wav", "Test Honk", SoundSource.OnTablet(File("Test Honk.wav")), null)
        val builtIn = BuiltInSound.ORGAN.asSound()

        assertThat(couldntPlay(tablet)).isEqualTo("Couldn’t play Test Honk. Copy the file to the tablet’s sounds folder again.")
        assertThat(couldntPlay(builtIn)).isEqualTo("Couldn’t play ${builtIn.name}. Restart Culvery and try again.")
    }

    /** **Pin** (21 §4.7): a file from the christmas folder says so under its row. */
    @Test
    fun aChristmasFileSaysSo() {
        val file = Sound("file:christmas/Test Bells.ogg", "Test Bells", SoundSource.OnTablet(File("christmas/Test Bells.ogg")), SeasonalTheme.CHRISTMAS)
        assertThat(file.tabletLine).isEqualTo("On this tablet · Christmas")
    }
}
