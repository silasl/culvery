package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import uk.co.siland.culvery.capability.party.ModesWords
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightChoice
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.ui.ButtonSize
import uk.co.siland.culvery.core.ui.ButtonSurface
import uk.co.siland.culvery.core.ui.ButtonTone
import uk.co.siland.culvery.core.ui.Culvery
import uk.co.siland.culvery.core.ui.HhButton
import uk.co.siland.culvery.core.ui.HhCloseButton
import uk.co.siland.culvery.core.ui.HhRadioGroup
import uk.co.siland.culvery.core.ui.HhRadioRow
import uk.co.siland.culvery.core.ui.HhRoundButton
import uk.co.siland.culvery.core.ui.HhSheet
import uk.co.siland.culvery.core.ui.HhType
import uk.co.siland.culvery.core.ui.HouseModeDimens
import uk.co.siland.culvery.core.ui.Icons

/** The brightness a room can be set to, in steps of ten (15b §4.2). */
internal val PERCENTS = (10..100 step 10).toList()

/** What a chip says of [choice]; a room that can't dim is only ever "On". */
internal fun lightChip(choice: LightChoice, dimmable: Boolean = true): String = when (choice) {
    is LightChoice.Scene -> "Scene: ${choice.sceneName}"
    is LightChoice.On -> if (dimmable) "On · ${choice.percent}%" else ModesWords.LIGHT_ON
    LightChoice.Off -> ModesWords.LIGHT_OFF
}

/**
 * What a mode does to [room]: one of its scenes, On (at a percent, if the room dims) or Off, as radio rows tagged
 * `light_scene_{i}`, `light_on` and `light_off`. [current] is shown as chosen; nothing is saved until "Save light".
 */
@Composable
internal fun LightChoiceSheet(room: LightRoomOption, current: LightChoice?, onSave: (LightChoice) -> Unit, onClose: () -> Unit) {
    var picked by remember { mutableStateOf(current) }
    val on = picked as? LightChoice.On
    HhSheet(
        padding = PaddingValues(horizontal = HouseModeDimens.sheetPaddingH, vertical = HouseModeDimens.sheetPaddingV),
        modifier = Modifier.testTag("light_sheet").semantics { paneTitle = room.name },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(room.name, style = HhType.sectionTitle, color = Culvery.colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
            HhCloseButton(onClose)
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                HhRadioGroup {
                    room.scenes.forEachIndexed { i, scene ->
                        HhRadioRow(scene.name, selected = picked.isScene(scene), tag = "light_scene_$i") { picked = LightChoice.Scene(scene.id, scene.name) }
                    }
                    HhRadioRow(ModesWords.LIGHT_ON, selected = on != null, tag = "light_on") { picked = on ?: LightChoice.On(100) }
                    if (on != null && room.dimmable) Stepper(room.name, on.percent) { picked = LightChoice.On(it) }
                    HhRadioRow(ModesWords.LIGHT_OFF, selected = picked == LightChoice.Off, tag = "light_off") { picked = LightChoice.Off }
                }
            }
        }
        HhButton(
            ModesWords.SAVE_LIGHT, { picked?.let(onSave) }, Modifier.fillMaxWidth().testTag("light_save"),
            tone = ButtonTone.Primary, size = ButtonSize.Large, enabled = picked != null, on = ButtonSurface.Bg,
        )
    }
}

private fun LightChoice?.isScene(scene: HouseScene) = this is LightChoice.Scene && sceneId == scene.id

/** The percent between Lower and Raise buttons, ten a step; each stops at its end of [PERCENTS]. */
@Composable
private fun Stepper(room: String, percent: Int, onPercent: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PartyDimens.rowGap, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
        HhRoundButton(
            Icons.REMOVE, ModesWords.lower(room), { onPercent((percent - PERCENTS.first()).coerceAtLeast(PERCENTS.first())) },
            Modifier.testTag("light_minus"), enabled = percent > PERCENTS.first(), on = ButtonSurface.Bg,
        )
        Text("$percent%", style = HhType.sectionTitle, color = Culvery.colors.ink, modifier = Modifier.testTag("light_percent"))
        HhRoundButton(
            Icons.ADD, ModesWords.raise(room), { onPercent((percent + PERCENTS.first()).coerceAtMost(PERCENTS.last())) },
            Modifier.testTag("light_plus"), enabled = percent < PERCENTS.last(), on = ButtonSurface.Bg,
        )
    }
}
