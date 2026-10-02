package uk.co.siland.culvery.core.setup.steps

import androidx.compose.runtime.Composable
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.co.siland.culvery.core.plugin.SetupStep
import uk.co.siland.culvery.core.setup.PeopleEditor
import uk.co.siland.culvery.core.setup.PeoplePane
import uk.co.siland.culvery.core.setup.StepTitle
import uk.co.siland.culvery.core.setup.WHO_ELSE

/** 4a design §4.2: everyone else. Always passable: "Skip for now" while only you live here. */
@Singleton
class HouseholdStep @Inject constructor(private val editor: PeopleEditor) : SetupStep {
    override val id = "household"
    override val order = 300
    override val skippable = true
    override val done: Flow<Boolean> = editor.members.map { it.size > 1 }

    @Composable
    override fun Content(onNext: () -> Unit) {
        StepTitle(WHO_ELSE)
        PeoplePane(editor, reorder = false)
    }
}
