package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.reminders.CardUi
import uk.co.siland.culvery.capability.reminders.DueKey
import uk.co.siland.culvery.capability.reminders.StripeUi
import uk.co.siland.culvery.core.ui.test.snap

/** The card at its own width on `bg`, with room round it for its shadow; people are the sample household's. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReminderCardScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val mia = StripeUi(colour = 0xFFE07BA8)
    private val alex = StripeUi(colour = 0xFF4CB387)
    private val sam = StripeUi(colour = 0xFF5B9BE0)
    private val one = CardUi(DueKey.Mine(1), "PE kit", listOf(mia), "Mia", "07:30")
    private val two = CardUi(DueKey.Mine(2), "Parents’ evening", listOf(alex, sam), "Alex and Sam", "Mon 19:00")
    private val bins = CardUi(
        DueKey.Mine(3), "Bins out tonight", listOf(StripeUi(inkId = "green"), StripeUi(inkId = "black")), "Bins", "18:00", detail = "Food, Recycling",
    )

    private fun snap(name: String, dark: Boolean, card: CardUi) =
        compose.snap(name, dark, Modifier.padding(32.dp)) { ReminderCard(card, onDone = {}, onLater = {}) }

    @Test fun oneLight() = snap("reminder_card_one_light", false, one)
    @Test fun oneDark() = snap("reminder_card_one_dark", true, one)
    @Test fun twoLight() = snap("reminder_card_two_light", false, two)
    @Test fun twoDark() = snap("reminder_card_two_dark", true, two)
    @Test fun binsLight() = snap("reminder_card_bins_light", false, bins)
    @Test fun binsDark() = snap("reminder_card_bins_dark", true, bins)
    @Test fun oneOfTwoLight() = snap("reminder_card_1_of_2_light", false, one.copy(of = 2))
    @Test fun oneOfTwoDark() = snap("reminder_card_1_of_2_dark", true, one.copy(of = 2))
}
