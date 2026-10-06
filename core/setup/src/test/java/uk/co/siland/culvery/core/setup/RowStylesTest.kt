package uk.co.siland.culvery.core.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.household.Member
import uk.co.siland.culvery.core.household.Person
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.household.Role
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.PersonPalette

/** People's and towns' rows are the shared row, at the sizes Settings › Calendars uses too (4d-1 §4.2). */
@RunWith(AndroidJUnit4::class)
class RowStylesTest {
    @get:Rule val compose = createComposeRule()

    private val alex = Member(Person(PersonId("alex"), "Alex", PersonPalette.colors[0]), Role.ADMIN, hasPin = true)
    private val london = PlaceMatch("London", "England", "United Kingdom", 51.5074, -0.1278, "Europe/London")

    private fun style(text: String): TextStyle = compose.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style

    private fun assertRowTitle(text: String) {
        assertWithMessage("$text size").that(style(text).fontSize).isEqualTo(17.sp)
        assertWithMessage("$text weight").that(style(text).fontWeight).isEqualTo(FontWeight.W600)
    }

    @Test
    fun aPersonsRowUsesTheSharedRowStyles() {
        compose.setContent { CulveryTheme(dark = true) { PeopleList(listOf(alex), onEdit = {}, onAdd = null, onMove = null) } }
        assertRowTitle("Alex")
        assertWithMessage("subtitle size").that(style("Admin · PIN set").fontSize).isEqualTo(14.sp)
    }

    @Test
    fun aTownsRowUsesTheSharedRowStyle() {
        compose.setContent {
            CulveryTheme(dark = true) {
                LocationContent("Lon", {}, TownResults.Found(listOf(london)), current = null, showCurrent = false, busy = false, onChoose = {})
            }
        }
        assertRowTitle("London, England, United Kingdom")
    }
}
