package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.FILM_ID
import uk.co.siland.culvery.capability.party.FILM_NIGHT
import uk.co.siland.culvery.capability.party.FakePlugs
import uk.co.siland.culvery.capability.party.LOCKED_HEATER
import uk.co.siland.culvery.capability.party.MemoryStore
import uk.co.siland.culvery.capability.party.ModesPrefs
import uk.co.siland.culvery.capability.party.ModesStore
import uk.co.siland.culvery.capability.party.PARTY_MODE
import uk.co.siland.culvery.capability.party.StoredMode
import uk.co.siland.culvery.core.plugin.COULD_NOT_SAVE
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.RecordingToaster
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

/** 15b §4.1: Settings › Modes, as the page draws it and as it saves. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModesPageTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = TestOverlay()
    private val scope = CoroutineScope(Job())
    private val memory = MemoryStore()
    private val settings = ModesStore(memory, scope)
    private val onMode = MutableStateFlow<String?>(null)
    private val holidayOn = MutableStateFlow<Boolean?>(false)
    private val room = MutableStateFlow(4)
    private val plugs = FakePlugs()
    private val toaster = RecordingToaster()

    @After fun stop() = scope.cancel()

    private fun page() = ModesPage(settings, onMode, holidayOn, room, plugs, toaster)

    private fun show(vararg modes: StoredMode) {
        runBlocking { settings.update { ModesPrefs(true, modes.toList()) } }
        val page = page()
        compose.setContent {
            CulveryTheme(dark = true) {
                CompositionLocalProvider(LocalOverlayHost provides overlay) {
                    Box(Modifier.size(960.dp, 1200.dp)) {
                        InSettingsPane { page.Content() }
                        overlay.content?.invoke()
                    }
                }
            }
        }
        compose.waitFor { exists("modes_add") }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun saved() = runBlocking { settings.prefs.first() }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top.value

    private fun hasSummary(id: String, summary: String) =
        compose.onAllNodes(hasTestTag("mode_row_$id") and hasText(summary, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun addAMode(name: String, icon: String) {
        compose.onNodeWithTag("modes_add").performClick()
        compose.onNodeWithTag("mode_name").performTextInput(name)
        compose.onNodeWithTag("mode_name_next").performClick()
        compose.onNodeWithTag("mode_icon_$icon").performClick()
        compose.onNodeWithTag("mode_add_save").performClick()
    }

    @Test
    fun itIsTheModesPage() {
        val page = page()

        assertThat(listOf(page.id, page.title, page.order)).containsExactly("modes", "Modes", 475).inOrder()
    }

    @Test
    fun rowsInOrder() {
        show(PARTY_MODE, FILM_NIGHT)

        val tags = listOf("mode_row_holiday", "mode_row_party", "mode_row_$FILM_ID", "modes_add")
        assertThat(tags.map(::top)).isInStrictOrder()
        compose.onNodeWithTag("mode_row_holiday").assertTextContains("Holiday mode")
        assertThat(hasSummary("holiday", "Built in · On Home · PIN · Lights, heating, hoover, plugs, door chime")).isTrue()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun anOnModeSaysOnFirst() {
        plugs.addedNow.value = listOf(LOCKED_HEATER, LOCKED_HEATER.copy(id = "lamp"))
        onMode.value = FILM_ID
        show(PARTY_MODE, FILM_NIGHT)

        compose.waitFor { hasSummary(FILM_ID, "On · On Home · No PIN · Party mix on every speaker · 1 room · 2 plugs") }
        assertThat(hasSummary("party", "On Home · PIN · 2 plugs")).isTrue()
        assertThat(hasSummary("party", "On · ")).isFalse()
    }

    @Test
    fun addAModeAsksANameThenAnIcon() {
        show(PARTY_MODE)

        compose.onNodeWithTag("modes_add").performClick()
        compose.onNodeWithTag("mode_add_sheet").assertExists()
        compose.onNodeWithTag("mode_name").performTextInput("Film night")
        compose.onNodeWithTag("mode_name_next").assertIsEnabled().performClick()
        compose.onNodeWithTag("mode_icon_movie").performClick()
        compose.onNodeWithTag("mode_add_save").performClick()

        compose.waitFor { exists("mode_header") }
        val added = saved().modes.last()
        assertThat(saved().modes).hasSize(2)
        assertThat(listOf(added.name, added.icon, added.showOnHome, added.needsPin)).containsExactly("Film night", "movie", true, true).inOrder()
        assertThat(added.id).matches("mode[a-z]{6}")
        compose.onNodeWithTag("mode_header").assert(hasAnyDescendant(hasText("Film night")))
        compose.onAllNodesWithTag("mode_add_sheet").assertCountEquals(0)
    }

    @Test
    fun aNewModeStartsOffHomeWhenHomeIsFull() {
        room.value = 2
        show(PARTY_MODE)

        addAMode("Film night", "movie")

        compose.waitFor { saved().modes.size == 2 }
        assertThat(saved().modes.last().showOnHome).isFalse()
    }

    @Test
    fun aNewModeIsOnHomeWhenThereIsRoom() {
        room.value = 3
        show(PARTY_MODE)

        addAMode("Film night", "movie")

        compose.waitFor { saved().modes.size == 2 }
        assertThat(saved().modes.last().showOnHome).isTrue()
    }

    @Test
    fun aRefusedNameKeepsTheSheet() {
        show(PARTY_MODE)
        compose.onNodeWithTag("modes_add").performClick()

        compose.onNodeWithTag("mode_name").performTextInput("party MODE")

        compose.onNodeWithTag("mode_name_error").assertTextEquals("There’s already a mode called Party mode.")
        compose.onNodeWithTag("mode_name_next").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("mode_add_sheet").assertExists()
        compose.onAllNodesWithTag("mode_add_save").assertCountEquals(0)
    }

    @Test
    fun theNameStopsAt24() {
        show(PARTY_MODE)
        compose.onNodeWithTag("modes_add").performClick()

        compose.onNodeWithTag("mode_name").performTextInput("a".repeat(30))

        val typed = compose.onNodeWithTag("mode_name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertThat(typed).isEqualTo("a".repeat(24))
        compose.onNodeWithTag("mode_name").performImeAction()
        compose.onNodeWithTag("mode_add_save").assertExists()
    }

    /** Icons carry no semantics, so the row's icon is read from the rows the page builds. */
    @Test
    fun anUnknownIconDrawsTheDefault() {
        val prefs = ModesPrefs(modes = listOf(PARTY_MODE, FILM_NIGHT.copy(icon = "not_an_icon"), FILM_NIGHT.copy(id = "modeb", icon = "pets")))

        val icons = modeRows(prefs, holidayOn = false, on = null, plugCounts = emptyMap()).map { it.icon }

        assertThat(icons).containsExactly("beach_access", "celebration", "celebration", "pets").inOrder()
    }

    @Test
    fun renameAndIconSave() {
        show(PARTY_MODE, FILM_NIGHT)
        compose.onNodeWithTag("mode_row_$FILM_ID").performClick()

        compose.onNodeWithTag("mode_header_name").performClick()
        compose.onNodeWithTag("mode_name").performTextReplacement("Party mode")
        compose.onNodeWithTag("mode_rename_save").assertIsNotEnabled()
        compose.onNodeWithTag("mode_name").performTextReplacement("Supper")
        compose.onNodeWithTag("mode_rename_save").performClick()
        compose.waitFor { saved().mode(FILM_ID)?.name == "Supper" }
        compose.onAllNodesWithTag("mode_rename_sheet").assertCountEquals(0)

        compose.onNodeWithTag("mode_header_icon").performClick()
        compose.onNodeWithTag("mode_icon_cake").performClick()
        compose.onNodeWithTag("mode_icon_save").performClick()
        compose.waitFor { saved().mode(FILM_ID)?.icon == "cake" }
        compose.onNodeWithTag("mode_header").assert(hasAnyDescendant(hasText("Supper")))
        assertThat(saved().modes.map { it.name }).containsExactly("Party mode", "Supper").inOrder()
    }

    @Test
    fun aModeMayChangeTheCaseOfItsOwnName() {
        show(FILM_NIGHT)
        compose.onNodeWithTag("mode_row_$FILM_ID").performClick()
        compose.onNodeWithTag("mode_header_name").performClick()

        compose.onNodeWithTag("mode_name").performTextReplacement("FILM NIGHT")

        compose.onNodeWithTag("mode_rename_save").assertIsEnabled()
    }

    @Test
    fun holidaysHeaderDoesntOpenAnything() {
        show(PARTY_MODE)
        compose.onNodeWithTag("mode_row_holiday").performClick()

        compose.onNodeWithTag("mode_header").assert(hasAnyDescendant(hasText("Holiday mode")))
        compose.onNodeWithTag("mode_header_icon").performClick()
        compose.onNodeWithTag("mode_header_name").performClick()

        assertThat(overlay.shown).isEqualTo(0)
        assertThat(overlay.content).isNull()
    }

    @Test
    fun backReturnsToTheList() {
        show(PARTY_MODE)
        compose.onNodeWithTag("mode_row_party").performClick()
        compose.onNodeWithTag("mode_header").assertExists()

        compose.onNodeWithTag("modes_back").performClick()

        compose.onAllNodesWithTag("mode_header").assertCountEquals(0)
        compose.onNodeWithTag("modes_add").assertExists()
    }

    @Test
    fun aSaveThatFailsKeepsTheSheetAndSaysSo() {
        show(PARTY_MODE)
        compose.onNodeWithTag("modes_add").performClick()
        compose.onNodeWithTag("mode_name").performTextInput("Film night")
        compose.onNodeWithTag("mode_name_next").performClick()
        memory.failOnWrite = 1

        compose.onNodeWithTag("mode_add_save").performClick()

        compose.waitFor { toaster.shown.isNotEmpty() }
        assertThat(toaster.shown).containsExactly(COULD_NOT_SAVE)
        assertThat(saved().modes).hasSize(1)
        compose.onNodeWithTag("mode_add_sheet").assertExists()
    }
}
