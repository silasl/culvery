package uk.co.siland.culvery.core.setup.pages

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.siland.culvery.core.plugin.SettingsPage
import uk.co.siland.culvery.core.setup.PEOPLE
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.PeoplePane
import uk.co.siland.culvery.core.setup.StepTitle

/** Settings › People (4a design §4.4): the Household step's list and sheet, and reordering (4c §7.1). */
@Singleton
class PeoplePage @Inject constructor(private val editor: PeopleEditor) : SettingsPage {
    override val id = "people"
    override val title = PEOPLE
    override val order = 100

    @Composable
    override fun Content() {
        StepTitle(PEOPLE)
        PeoplePane(editor, reorder = true)
    }
}
