package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.reminders.CardUi
import uk.co.siland.culvery.capability.reminders.DueKey
import uk.co.siland.culvery.capability.reminders.StripeUi
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertAccessible
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReminderCardTest {
    @get:Rule val compose = createComposeRule()

    private val first = DueKey.Mine(1)
    private val second = DueKey.Mine(2)
    private val peKit = CardUi(first, "PE kit", listOf(StripeUi(colour = 0xFFE07BA8)), "Mia", "07:30")

    private fun show(card: CardUi) =
        compose.setContent { CulveryTheme(dark = true) { ReminderCard(card, onDone = {}, onLater = {}) } }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun theCardSaysTheWordsThenWho() {
        show(peKit)
        compose.onNodeWithTag("reminder_card_words").assert(hasContentDescription("PE kit, Mia"))
        compose.assertAccessible()
    }

    @Test
    fun theCountIsReadAfterWho() {
        show(peKit.copy(of = 2))
        compose.onNodeWithTag("reminder_card_words").assert(hasContentDescription("PE kit, Mia, 1 of 2"))
        compose.onNodeWithText("1 of 2").assertExists()
    }

    /** Fails if a button isn't Large, Done isn't twice Later's width and after it, or a button doesn't say its word. */
    @Test
    fun laterIsQuietAndDonePrimary() {
        show(peKit)
        val later = bounds("reminder_later")
        val done = bounds("reminder_done")
        assertThat((later.bottom - later.top).value).isWithin(0.5f).of(60f)
        assertThat((done.bottom - done.top).value).isWithin(0.5f).of(60f)
        assertThat((done.right - done.left).value).isWithin(2f).of(2 * (later.right - later.left).value)
        assertThat(later.left).isLessThan(done.left)
        compose.onNodeWithTag("reminder_later").assertTextEquals("Later")
        compose.onNodeWithTag("reminder_done").assertTextEquals("Done")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun detailShowsUnderTheWords() {
        show(peKit.copy(words = "Bins out tonight", who = "Bins", detail = "Food, Recycling"))
        compose.onNodeWithText("Food, Recycling").assertExists()
        compose.onNodeWithTag("reminder_card_words").assert(hasContentDescription("Bins out tonight, Bins, Food, Recycling"))
        assertThat(bounds("reminder_detail").top).isAtLeast(bounds("reminder_words").bottom)
    }

    /** Fails if a second tap, before the next frame, answers the card that replaced the first. */
    @Test
    fun doneAndLaterCarryTheKeyTheyWereDrawnWith() {
        val answered = mutableListOf<Pair<String, DueKey>>()
        var card by mutableStateOf<CardUi?>(peKit)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CulveryTheme(dark = true) {
                ReminderCardLayer(
                    engineKey = card?.key, card = card,
                    onDone = { answered += "done" to it; card = peKit.copy(key = second) },
                    onLater = { answered += "later" to it },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        // A touch moves the clock on; the semantics action doesn't, so both taps land on the first frame's card.
        compose.onNodeWithTag("reminder_done").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("reminder_done").performSemanticsAction(SemanticsActions.OnClick)
        assertThat(answered).containsExactly("done" to first, "done" to first)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("reminder_later").performClick()
        assertThat(answered.last()).isEqualTo("later" to second)
    }

    /** The layer is never a scrim with no buttons. */
    @Test
    fun aCardWithNothingToDrawStillOffersDone() {
        val answered = mutableListOf<Pair<String, DueKey>>()
        compose.setContent {
            CulveryTheme(dark = true) {
                ReminderCardLayer(engineKey = second, card = null, onDone = { answered += "done" to it }, onLater = { answered += "later" to it })
            }
        }
        compose.onNodeWithText("Reminder").assertExists()
        compose.onNodeWithTag("reminder_later").performClick()
        compose.onNodeWithTag("reminder_done").performClick()
        assertThat(answered).containsExactly("later" to second, "done" to second).inOrder()
        compose.assertAccessible()
    }

    @Test
    fun aCardForAnotherKeyIsNotDrawnForTheEnginesKey() {
        val answered = mutableListOf<DueKey>()
        compose.setContent {
            CulveryTheme(dark = true) { ReminderCardLayer(engineKey = second, card = peKit, onDone = { answered += it }, onLater = {}) }
        }
        compose.onNodeWithText("PE kit").assertDoesNotExist()
        compose.onNodeWithTag("reminder_done").performClick()
        assertThat(answered).containsExactly(second)
    }
}
