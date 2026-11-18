package uk.co.siland.culvery.capability.reminders.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.reminders.BinsRowUi
import uk.co.siland.culvery.capability.reminders.DueKey
import uk.co.siland.culvery.capability.reminders.RowUi
import uk.co.siland.culvery.capability.reminders.StripeUi
import uk.co.siland.culvery.capability.reminders.TabUi
import uk.co.siland.culvery.core.ui.test.snap

/** The tab in the content area beside the rail, as the shell gives it; people are the sample household's. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemindersTabScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val mia = StripeUi(colour = 0xFFE07BA8)
    private val alex = StripeUi(colour = 0xFF4CB387)
    private val sam = StripeUi(colour = 0xFF5B9BE0)
    private val bins = BinsRowUi(on = true, time = LocalTime.of(18, 0), cantRead = false)

    private val filled = TabUi(
        waiting = listOf(
            RowUi(DueKey.Mine(1), 1, listOf(mia), "07:30", "PE kit", "Mia", null),
            RowUi(DueKey.Mine(2), 2, listOf(alex, sam), "07:45", "Parents’ evening forms", "Alex and Sam", "Back at 18:30"),
        ),
        today = listOf(RowUi(null, 3, listOf(mia), "16:30", "Swimming bag", "Mia", "Every Friday")),
        comingUp = listOf(
            RowUi(null, 4, listOf(sam), "Thu 09:00", "Ring the dentist", "Sam", "Once"),
            RowUi(null, 5, listOf(alex), "Sat 10:00", "Water the plants", "Alex", "Every week, on Sat, Sun"),
        ),
        bins = bins,
    )
    private val empty = TabUi(emptyList(), emptyList(), emptyList(), null)
    private val cantRead = TabUi(emptyList(), emptyList(), emptyList(), bins.copy(cantRead = true))

    private fun snap(name: String, dark: Boolean, ui: TabUi) =
        compose.snap(name, dark, Modifier.size(1116.dp, 690.dp)) {
            RemindersTab(ui, onAdd = {}, onOpen = {}, onDone = {}, onBins = {}, modifier = Modifier.fillMaxSize())
        }

    @Test fun filledLight() = snap("reminders_tab_filled_light", false, filled)
    @Test fun filledDark() = snap("reminders_tab_filled_dark", true, filled)
    @Test fun emptyLight() = snap("reminders_tab_empty_light", false, empty)
    @Test fun emptyDark() = snap("reminders_tab_empty_dark", true, empty)
    @Test fun cantReadLight() = snap("reminders_tab_bins_cant_read_light", false, cantRead)
    @Test fun cantReadDark() = snap("reminders_tab_bins_cant_read_dark", true, cantRead)
}
