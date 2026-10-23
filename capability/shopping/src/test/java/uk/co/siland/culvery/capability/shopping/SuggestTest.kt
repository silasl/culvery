package uk.co.siland.culvery.capability.shopping

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.plugin.textKey

class SuggestTest {
    private fun l(text: String, count: Int, last: Long = 0) = Learned(textKey(text), text, count, last)

    @Test
    fun oneLetterSuggests() {
        val learned = listOf(l("Butter", 5), l("Bread", 9), l("Bin bags", 2), l("Eggs", 7))
        assertThat(suggest("b", learned, emptySet()).map { it.key }).containsExactly("bread", "butter", "bin bags").inOrder()
    }

    @Test
    fun prefixMatchesComeBeforeWordStarts() {
        val learned = listOf(l("Tea bags", 9), l("Bananas", 1))
        assertThat(suggest("ba", learned, emptySet()).map { it.key }).containsExactly("bananas", "tea bags").inOrder()
    }

    @Test
    fun itemsOnTheListNeverShow() {
        val learned = listOf(l("Butter", 5), l("Bread", 9))
        assertThat(suggest("b", learned, setOf("bread")).map { it.key }).doesNotContain("bread")
        assertThat(favourites(learned, setOf("bread"), emptySet()).map { it.key }).doesNotContain("bread")
    }

    @Test
    fun tiesGoToTheLatestThenTheKey() {
        val learned = listOf(l("Cheese", 3, 10), l("Cherries", 3, 20), l("Chives", 3, 20))
        assertThat(suggest("ch", learned, emptySet()).map { it.key }).containsExactly("cherries", "chives", "cheese").inOrder()
    }

    @Test
    fun nothingTypedSuggestsNothing() {
        assertThat(suggest("  ", listOf(l("Milk", 1)), emptySet())).isEmpty()
    }

    @Test
    fun favouritesLeaveOutSuggestions() {
        val learned = listOf(l("Butter", 5), l("Bread", 9), l("Eggs", 7))
        assertThat(favourites(learned, emptySet(), setOf("butter")).map { it.key }).containsExactly("bread", "eggs").inOrder()
    }

    @Test
    fun accentsAndCaseMatch() {
        assertThat(suggest("CRE", listOf(l("Crème fraîche", 1)), emptySet()).map { it.text }).containsExactly("Crème fraîche")
    }
}
