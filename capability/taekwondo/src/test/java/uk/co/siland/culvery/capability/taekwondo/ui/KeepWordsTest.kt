package uk.co.siland.culvery.capability.taekwondo.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KeepWordsTest {
    @Test
    fun aJoinerSitsBetweenTheSyllablesOfAWord() {
        assertThat(keepWords("때리기와")).isEqualTo("때\u2060리\u2060기\u2060와")
    }

    @Test
    fun noJoinerCrossesASpace() {
        val kept = keepWords("옆뒤 때리기와 팔목")
        assertThat(kept).contains("뒤 때")
        assertThat(kept).contains("와 팔")
    }

    @Test
    fun latinTextIsUntouched() {
        assertThat(keepWords("Walking stance, grab the head")).isEqualTo("Walking stance, grab the head")
        assertThat(keepWords("A 구부려")).isEqualTo("A 구\u2060부\u2060려")
    }
}
