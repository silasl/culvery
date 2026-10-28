package uk.co.siland.culvery.capability.holiday.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.holiday.BackDatePick
import uk.co.siland.culvery.capability.holiday.HolidayStep
import uk.co.siland.culvery.core.house.PlugChange
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhTrayColumn
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.test.snap

/** The Holiday mode tile in the One-tap tray at 333 dp, and the "When are you back?" sheet (11 §5.2), dark and light. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land-hdpi")
class HolidayScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 28)
    private val back = LocalDate.of(2026, 10, 31)
    private val steps = HolidayStep.entries

    private fun tile(name: String, dark: Boolean, on: Boolean, line: String? = null) =
        compose.snap(name, dark, Modifier.padding(16.dp)) {
            HhTrayColumn(
                "One-tap",
                listOf("holiday" to { HolidayTile(on, back.takeIf { on }, line, null) {} }, "hoover" to { HooverSizedRow() }),
                Modifier.width(333.dp),
            )
        }

    /** Stands in for the Hoover row below the tile, which lives in another module. */
    @Composable
    private fun HooverSizedRow() {
        val c = Culvery.colors
        Column(
            Modifier.fillMaxWidth().background(c.surf2, RoundedCornerShape(17.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text("Hoover", style = HhType.buttonLabel, color = c.ink)
            Text("Docked · 86%", style = HhType.secondary, color = c.mute)
        }
    }

    private fun sheet(
        name: String,
        dark: Boolean,
        back: LocalDate,
        pick: BackDatePick?,
        choosing: Boolean = false,
        plugs: List<PlugChange> = emptyList(),
    ) =
        compose.snap(name, dark, Modifier.size(1_116.dp, 800.dp)) {
            Box(Modifier.size(1_116.dp, 800.dp), contentAlignment = Alignment.CenterEnd) {
                HolidaySheet(back, today, pick, steps, plugs, choosing, turning = false, onChangeDate = {}, onPickDate = {}, onTurnOn = {}, onCancel = {})
            }
        }

    private val cornwall = BackDatePick("Cornwall", LocalDate.of(2026, 11, 1))

    @Test fun tileOffDark() = tile("holiday_tile_off_dark", true, on = false)

    @Test fun tileOffLight() = tile("holiday_tile_off_light", false, on = false)

    @Test fun tileOnDark() = tile("holiday_tile_on_dark", true, on = true)

    @Test fun tileOnLight() = tile("holiday_tile_on_light", false, on = true)

    @Test
    fun tileLineDark() = tile("holiday_tile_line_dark", true, on = true, line = LINE)

    @Test
    fun tileLineLight() = tile("holiday_tile_line_light", false, on = true, line = LINE)

    @Test
    fun tileThreeLinesDark() = tile("holiday_tile_three_lines_dark", true, on = false, line = THREE_LINES)

    @Test
    fun tileThreeLinesLight() = tile("holiday_tile_three_lines_light", false, on = false, line = THREE_LINES)

    @Test fun sheetFromCalendarDark() = sheet("holiday_sheet_from_calendar_dark", true, cornwall.back, cornwall)

    @Test fun sheetFromCalendarLight() = sheet("holiday_sheet_from_calendar_light", false, cornwall.back, cornwall)

    @Test fun sheetPickDark() = sheet("holiday_sheet_pick_dark", true, today.plusDays(1), null)

    @Test fun sheetPickLight() = sheet("holiday_sheet_pick_light", false, today.plusDays(1), null)

    @Test fun sheetChangeDateDark() = sheet("holiday_sheet_change_date_dark", true, cornwall.back, cornwall, choosing = true)

    @Test fun sheetChangeDateLight() = sheet("holiday_sheet_change_date_light", false, cornwall.back, cornwall, choosing = true)

    @Test fun sheetPlugsDark() = sheet("holiday_sheet_plugs_dark", true, cornwall.back, cornwall, plugs = PLUGS)

    @Test fun sheetPlugsLight() = sheet("holiday_sheet_plugs_light", false, cornwall.back, cornwall, plugs = PLUGS)

    private companion object {
        val PLUGS = listOf(PlugChange("Lamp", false), PlugChange("Fan", false), PlugChange("Desk", true))
        const val LINE = "The heating didn’t come off Eco. Turn it off in the Nest app."
        val THREE_LINES = listOf(
            "The heating is still on Eco. Turn it off in the Nest app.",
            "Lamp didn’t switch back on. Switch it on the Plugs tab.",
            "Holiday lights are still on. Turn them off in the Lights tab.",
        ).joinToString("\n")
    }
}
