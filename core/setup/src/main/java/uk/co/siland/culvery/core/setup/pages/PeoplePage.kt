package uk.co.siland.culvery.core.setup.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import uk.co.siland.culvery.core.setup.PEOPLE
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.PeoplePane
import uk.co.siland.culvery.core.ui.HhSectionLabel
import uk.co.siland.culvery.core.ui.SettingsTokens

/** App settings › PEOPLE (4a design §4.4, 15b §3): the Household step's list two to a row, its sheet, and reordering (4c §7.1). */
@Composable
fun PeopleSection(editor: PeopleEditor) {
    Column(verticalArrangement = Arrangement.spacedBy(SettingsTokens.rowGap)) {
        HhSectionLabel(PEOPLE)
        PeoplePane(editor, reorder = true)
    }
}
