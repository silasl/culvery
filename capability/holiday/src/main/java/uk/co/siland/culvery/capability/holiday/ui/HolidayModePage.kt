package uk.co.siland.culvery.capability.holiday.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.capability.holiday.HolidayActions
import uk.co.siland.culvery.capability.holiday.HolidayHouse
import uk.co.siland.culvery.capability.holiday.HolidayWords
import uk.co.siland.culvery.core.house.HouseModes
import uk.co.siland.culvery.core.plugin.BuiltInModePage
import uk.co.siland.culvery.core.plugin.HolidayLightsSection
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.turnNowLabel
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.HouseModeTurnButton
import uk.co.siland.culvery.core.ui.HouseModeTurnLine
import uk.co.siland.culvery.core.ui.SettingsTokens
import uk.co.siland.culvery.core.ui.rememberSingleAction

private const val TAG = "HolidayModePage"

/** Holiday mode's own parts of its page in Settings › Modes (15b §4.2): the turn button, Lights while away, then Also while away. */
@Singleton
class HolidayModePage @Inject constructor(
    private val actions: HolidayActions,
    private val house: HolidayHouse,
    private val lights: Set<@JvmSuppressWildcards HolidayLightsSection>,
) : BuiltInModePage {
    override val modeId = HouseModes.HOLIDAY

    @Composable
    override fun TurnButton() {
        val record by house.record.collectAsState()
        val running by house.running.collectAsState()
        val overlay = LocalOverlayHost.current
        val action = rememberSingleAction(house) { e -> Log.w(TAG, "Holiday mode turn button action failed: ${e::class.simpleName}") }
        HouseModeTurnButton(
            turnNowLabel(record?.on == true),
            { action.run { actions.toggle { by -> overlay.show { HolidaySheetHost(by, actions, house, overlay::dismiss) } } } },
            read = record != null, running = running != null,
        )
    }

    @Composable
    override fun TurnLine() {
        val houseLine by house.line.collectAsState()
        val refusal by actions.refusal.line.collectAsState(initial = actions.refusal.current())
        HouseModeTurnLine(refusal ?: houseLine)
    }

    @Composable
    override fun Sections() {
        val away by house.awayLines.collectAsState()
        Column(verticalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap)) {
            if (lights.isNotEmpty()) {
                HhSectionLabel(HolidayWords.LIGHTS_WHILE_AWAY)
                lights.forEach { it.Content(Modifier) }
            }
            HhSectionLabel(HolidayWords.ALSO_WHILE_AWAY)
            away.forEachIndexed { i, line ->
                Text(line, style = HhType.body, color = Culvery.colors.mute, modifier = Modifier.testTag("holiday_fixed_$i"))
            }
        }
    }
}
