package uk.co.siland.culvery.capability.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.Icons
import uk.co.siland.culvery.core.ui.WithFontScale
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel

@RunWith(AndroidJUnit4::class)
class EditorRowsTest {
    @get:Rule val compose = createComposeRule()

    private val clicks = mutableListOf<String>()

    private fun show(scale: Float = 1f, row: @Composable () -> Unit) {
        compose.setContent {
            CulveryTheme(dark = true) {
                WithFontScale(scale) { Box(Modifier.width(EditorDimens.columnWidth)) { row() } }
            }
        }
    }

    private fun description(tag: String): List<String> =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]

    private fun startsRow(
        date: String? = "Sat 26 Sep",
        time: String? = "10:00",
        spokenDate: String? = "Saturday 26 September",
        tag: RowTag? = null,
    ): @Composable () -> Unit = {
        EditorWhenRow(
            testTag = "editor_row_starts",
            icon = Icons.SCHEDULE,
            label = "Starts",
            date = date,
            time = time,
            spokenDate = spokenDate,
            active = false,
            set = date != null,
            typing = false,
            onDate = { clicks += "date" },
            onTime = { clicks += "time" },
            tag = tag,
        )
    }

    @Test
    fun eachTargetIsItsOwnButton() {
        show(row = startsRow())
        compose.onNodeWithTag("editor_row_starts_date").assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithTag("editor_row_starts_time").assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertThat(description("editor_row_starts_date")).contains("Starts, Saturday 26 September")
        assertThat(description("editor_row_starts_time")).contains("Starts at 10:00")
        compose.onNodeWithTag("editor_row_starts_time").performClick()
        compose.onNodeWithTag("editor_row_starts_date").performClick()
        assertThat(clicks).containsExactly("time", "date").inOrder()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anUnsetDateSaysChooseADayAndAllDayHasNoTime() {
        show(row = startsRow(date = null, time = null, spokenDate = null))
        assertThat(description("editor_row_starts_date")).contains("Starts, Choose a day")
        compose.onNodeWithTag("editor_row_starts_time").assertDoesNotExist()
        compose.onNode(hasText("Choose a day") and hasAnyAncestor(hasTestTag("editor_row_starts_date")), useUnmergedTree = true).assertExists()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun endsWaitsForAStartDay() {
        show {
            EditorWhenRow(
                testTag = "editor_row_ends",
                icon = Icons.EVENT,
                label = "Ends",
                date = null,
                time = null,
                spokenDate = null,
                active = false,
                set = false,
                typing = false,
                onDate = null,
                onTime = null,
            )
        }
        compose.onNode(hasText("Ends") and hasAnyAncestor(hasTestTag("editor_row_ends")), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("editor_row_ends_date").assertDoesNotExist()
        compose.onNodeWithTag("editor_row_ends_time").assertDoesNotExist()
        compose.onNodeWithTag("editor_row_ends").assertHasNoClickAction()
        assertThat(description("editor_row_ends")).contains("Ends, choose a start day first")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aLockedRowShowsTheSpansDatesWithALockAndNoButtons() {
        show {
            EditorWhenRow(
                testTag = "editor_row_starts",
                icon = Icons.SCHEDULE,
                label = "Starts",
                date = "Mon 28 Sep – Wed 30 Sep · change dates in Google Calendar on your phone",
                time = null,
                spokenDate = null,
                active = false,
                set = true,
                typing = false,
                onDate = null,
                onTime = null,
                locked = true,
            )
        }
        compose.onNodeWithTag("editor_row_starts_lock", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("editor_row_starts_date").assertDoesNotExist()
        compose.onNodeWithTag("editor_row_starts").assertHasNoClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, LOCKED_SPAN))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun atFontScale15TheStartsRowKeepsDateAndTimeOnOneLine() {
        show(scale = 1.5f, row = startsRow(date = "Wed 30 Sep", tag = RowTag("Clash", Icons.WARNING, Color.Gray, Color.Black)))
        listOf("Wed 30 Sep", "10:00").forEach { word ->
            val layout = compose.onNode(hasText(word), useUnmergedTree = true).textLayout()
            assertWithMessage("$word on one line").that(layout.lineCount).isEqualTo(1)
            assertWithMessage("$word not cut").that(layout.isLineEllipsized(0)).isFalse()
        }
        val row = compose.onNodeWithTag("editor_row_starts").getUnclippedBoundsInRoot()
        val date = compose.onNodeWithTag("editor_row_starts_date").getUnclippedBoundsInRoot()
        val time = compose.onNodeWithTag("editor_row_starts_time").getUnclippedBoundsInRoot()
        val tag = compose.onNodeWithTag("editor_row_starts_tag", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertThat(listOf(date.left, time.left, tag.left)).isInStrictOrder()
        assertWithMessage("the tag stays inside the row").that(tag.right).isAtMost(row.right)
        compose.assertClickablesHaveRoleAndLabel()
    }

    /** One tag on Starts: a clash hides Copied, and Copied comes back once the clash clears. */
    @Test
    fun aClashHidesCopiedUntilItClears() {
        val copied = RowTag("Copied", null, Color.Gray, Color.Black)
        val clash = RowTag("Clash", Icons.WARNING, Color.Gray, Color.Black)
        var clashing by mutableStateOf(false)
        show { startsRow(tag = startsTag(clash.takeIf { clashing }, copied))() }
        fun shown() = compose.onAllNodes(hasAnyAncestor(hasTestTag("editor_row_starts_tag")) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { n -> n.config[SemanticsProperties.Text].map { it.text } }
        assertThat(shown()).containsExactly("Copied")
        clashing = true
        compose.waitForIdle()
        assertThat(shown()).containsExactly("Clash")
        clashing = false
        compose.waitForIdle()
        assertThat(shown()).containsExactly("Copied")
        assertThat(startsTag(null, null)).isNull()
    }
}
