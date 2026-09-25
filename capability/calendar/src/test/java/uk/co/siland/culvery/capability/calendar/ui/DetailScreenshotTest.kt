package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.calendar.EventDetailUi
import uk.co.siland.culvery.core.access.PinReason
import uk.co.siland.culvery.core.access.ui.PinPadSheet
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.ShellTokens

/** The sheet as the shell shows it: against the right edge, over the scrim, on the 1280×800 canvas. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun snap(
        name: String,
        dark: Boolean,
        detail: EventDetailUi,
        mode: DetailMode = DetailMode.Idle,
        over: @Composable () -> Unit = {},
    ) {
        compose.setContent {
            CulveryTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Culvery.colors.bg)) {
                    Box(Modifier.fillMaxSize().background(ShellTokens.sheetScrim))
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        EventDetailSheet(detail, SampleUi.household, mode, busy = false, {}, {}, {}, {}, {}, {}, {})
                    }
                    over()
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun editableDark() = snap("detail_editable_dark", true, SampleUi.detailEditable)
    @Test fun editableLight() = snap("detail_editable_light", false, SampleUi.detailEditable)
    @Test fun readOnlyFeedDark() = snap("detail_readonly_feed_dark", true, SampleUi.detailReadOnlyFeed)
    @Test fun readOnlyFeedLight() = snap("detail_readonly_feed_light", false, SampleUi.detailReadOnlyFeed)
    @Test fun recurringDark() = snap("detail_recurring_dark", true, SampleUi.detailRecurring)
    @Test fun recurringLight() = snap("detail_recurring_light", false, SampleUi.detailRecurring)
    @Test fun untaggedDark() = snap("detail_untagged_dark", true, SampleUi.detailUntagged, DetailMode.ChoosingPerson)
    @Test fun untaggedLight() = snap("detail_untagged_light", false, SampleUi.detailUntagged, DetailMode.ChoosingPerson)
    @Test fun syncingDark() = snap("detail_syncing_dark", true, SampleUi.detailSyncing)
    @Test fun syncingLight() = snap("detail_syncing_light", false, SampleUi.detailSyncing)
    @Test fun deleteConfirmDark() = snap("detail_delete_confirm_dark", true, SampleUi.detailEditable, DetailMode.ConfirmingDelete)
    @Test fun deleteConfirmLight() = snap("detail_delete_confirm_light", false, SampleUi.detailEditable, DetailMode.ConfirmingDelete)

    @Test
    fun pinOverTheSheetDark() = snap("detail_pin_dark", true, SampleUi.detailEditable) {
        PinPadSheet("Change any event", PinReason.Delete, error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {}, overSheet = true)
    }

    @Test
    fun pinOverTheSheetLight() = snap("detail_pin_light", false, SampleUi.detailEditable) {
        PinPadSheet("Change any event", PinReason.Delete, error = null, lockedUntilMillis = null, onSubmit = {}, onCancel = {}, overSheet = true)
    }
}
