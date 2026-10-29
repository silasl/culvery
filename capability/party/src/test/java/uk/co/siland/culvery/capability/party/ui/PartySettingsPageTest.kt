package uk.co.siland.culvery.capability.party.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import uk.co.siland.culvery.capability.party.DINING
import uk.co.siland.culvery.capability.party.FakeLights
import uk.co.siland.culvery.capability.party.FakeMusic
import uk.co.siland.culvery.capability.party.KITCHEN
import uk.co.siland.culvery.capability.party.LOUNGE
import uk.co.siland.culvery.capability.party.MemoryStore
import uk.co.siland.culvery.capability.party.MusicChoice
import uk.co.siland.culvery.capability.party.PartyPrefs
import uk.co.siland.culvery.capability.party.PartySettings
import uk.co.siland.culvery.core.house.HouseRoom
import uk.co.siland.culvery.core.house.HouseScene
import uk.co.siland.culvery.core.house.LightOptions
import uk.co.siland.culvery.core.house.LightRoomOption
import uk.co.siland.culvery.core.house.MusicOptions
import uk.co.siland.culvery.core.house.MusicPick
import uk.co.siland.culvery.core.house.SceneChoice
import uk.co.siland.culvery.core.plugin.LocalOverlayHost
import uk.co.siland.culvery.core.plugin.test.TestOverlay
import uk.co.siland.culvery.core.ui.CulveryTheme
import uk.co.siland.culvery.core.ui.test.assertClickablesHaveRoleAndLabel
import uk.co.siland.culvery.core.ui.test.waitFor

internal val SUNDAY = MusicPick("FV:2/2", "Sunday morning")
internal val MIX = MusicPick("FV:2/3", "Party mix")
internal val MUSIC_OPTIONS = MusicOptions(listOf(SUNDAY, MIX), listOf(KITCHEN, LOUNGE, DINING), null)
internal val LIGHT_OPTIONS = LightOptions(
    listOf(
        LightRoomOption("r1", "Kitchen", listOf(HouseScene("s1", "Bright"), HouseScene("s2", "Relax"))),
        LightRoomOption("r2", "Lounge", listOf(HouseScene("s3", "Relax"))),
    ),
    null,
)
internal val BRIGHT_IN_KITCHEN = SceneChoice("r1", "Kitchen", "s1", "Bright")

/** Hosts a page as Settings does: in a vertical scroll, in a column 720 dp wide. */
@Composable
internal fun InSettingsPane(page: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 40.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.widthIn(max = 720.dp)) { page() }
    }
}

/** 13 §3: Settings › Party mode, as the screen draws it and as the host saves it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PartySettingsPageTest {
    @get:Rule val compose = createComposeRule()
    private val overlay = TestOverlay()
    private val scope = CoroutineScope(Job())
    private val settings = PartySettings(MemoryStore(), scope)
    private val music = FakeMusic().apply { offered = MUSIC_OPTIONS }
    private val lights = FakeLights().apply { offered = LIGHT_OPTIONS }
    private val picked = mutableListOf<MusicPick?>()
    private val speakers = mutableListOf<Pair<HouseRoom, Boolean>>()
    private val scenes = mutableListOf<Pair<String, HouseScene?>>()

    @After fun stop() = scope.cancel()

    private val ui = PartySettingsUi(PartyPrefs(), MUSIC_OPTIONS, LIGHT_OPTIONS)

    private fun page(content: @Composable () -> Unit) = compose.setContent {
        CulveryTheme(dark = true) {
            CompositionLocalProvider(LocalOverlayHost provides overlay) {
                Box(Modifier.size(960.dp, 800.dp)) {
                    InSettingsPane(content)
                    overlay.content?.invoke()
                }
            }
        }
    }

    private fun screen(ui: PartySettingsUi) = page {
        PartySettingsScreen(
            ui, onMusic = { picked += it }, onSpeaker = { room, on -> speakers += room to on },
            onScene = { room, scene -> scenes += room.id to scene },
        )
    }

    private fun host(prefs: PartyPrefs? = null, withMusic: Boolean = true, withLights: Boolean = true, waitFor: String = "party_music") {
        if (prefs != null) runBlocking { settings.update { prefs } }
        page { PartySettingsHost(settings, music.takeIf { withMusic }, lights.takeIf { withLights }) }
        compose.waitFor { count(waitFor) > 0 }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun saved() = runBlocking { settings.prefs.first() }

    private fun savedAfter(change: () -> Unit, until: (PartyPrefs) -> Boolean): PartyPrefs {
        change()
        compose.waitFor { until(saved()) }
        return saved()
    }

    @Test
    fun musicDefaultsToNone() {
        screen(ui)
        compose.onNodeWithTag("party_music", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("party_music").assertTextEquals("Music", "None")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun pickingMusicSavesIt() {
        screen(ui)
        compose.onNodeWithTag("party_music").performClick()
        compose.onNodeWithTag("party_music_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Music for Party mode"))
        compose.onNodeWithTag("party_music_0").assertTextEquals("None")
        compose.onNodeWithTag("party_music_1").assertTextEquals("Sunday morning")
        compose.onNodeWithTag("party_music_2").assertTextEquals("Party mix")
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("party_music_2").performClick()
        assertThat(picked).containsExactly(MIX)
        compose.onAllNodesWithTag("party_music_sheet").assertCountEquals(0)
    }

    @Test
    fun theHostSavesThePick() {
        host()
        compose.onNodeWithTag("party_music").performClick()
        val after = savedAfter({ compose.onNodeWithTag("party_music_2").performClick() }) { it.music != null }
        assertThat(after.music).isEqualTo(MusicChoice("FV:2/3", "Party mix"))
    }

    @Test
    fun noneClearsTheMusicAndKeepsTheRooms() {
        host(PartyPrefs(music = MusicChoice("FV:2/3", "Party mix"), rooms = listOf(KITCHEN, LOUNGE)))
        compose.onNodeWithTag("party_music").performClick()
        val after = savedAfter({ compose.onNodeWithTag("party_music_0").performClick() }) { it.music == null }
        assertThat(after.rooms).containsExactly(KITCHEN, LOUNGE).inOrder()
    }

    @Test
    fun everySpeakerByDefault() {
        screen(ui)
        for ((i, name) in listOf("Kitchen", "Lounge", "Dining").withIndex()) {
            compose.onNodeWithTag("party_speaker_$i").assertTextEquals(name).assertIsOn().assertIsEnabled()
        }
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun switchingASpeakerSendsItsRoomAndState() {
        screen(ui)
        compose.onNodeWithTag("party_speaker_2").performClick()
        assertThat(speakers).containsExactly(DINING to false)
    }

    @Test
    fun theLastSpeakerCantBeSwitchedOff() {
        screen(ui.copy(prefs = PartyPrefs(rooms = listOf(DINING))))
        compose.onNodeWithTag("party_speaker_0").assertIsOff().assertIsEnabled()
        compose.onNodeWithTag("party_speaker_2").assertIsOn().assertIsNotEnabled()
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun switchingARoomOffSavesTheRest() {
        host()
        val off = savedAfter({ compose.onNodeWithTag("party_speaker_2").performClick() }) { it.rooms != null }
        assertThat(off.rooms).containsExactly(KITCHEN, LOUNGE).inOrder()
        compose.onNodeWithTag("party_speaker_2").assertIsOff()
        val on = savedAfter({ compose.onNodeWithTag("party_speaker_2").performClick() }) { it.rooms == null }
        assertThat(on.rooms).isNull()
    }

    @Test
    fun theRoomsSaveWithNoMusicPicked() {
        host()
        assertThat(saved().music).isNull()
        val after = savedAfter({ compose.onNodeWithTag("party_speaker_0").performClick() }) { it.rooms != null }
        assertThat(after.rooms).containsExactly(LOUNGE, DINING).inOrder()
    }

    @Test
    fun aLightRoomDefaultsToLeaveAlone() {
        screen(ui)
        compose.onNodeWithTag("party_light_0").assertTextEquals("Kitchen", "Leave alone")
        compose.onNodeWithTag("party_light_1").assertTextEquals("Lounge", "Leave alone")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theSceneSheetListsTheRoomsScenes() {
        screen(ui)
        compose.onNodeWithTag("party_light_0").performClick()
        compose.onNodeWithTag("party_scene_sheet").assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Kitchen"))
        compose.onNodeWithTag("party_scene_0").assertTextEquals("Leave alone")
        compose.onNodeWithTag("party_scene_1").assertTextEquals("Bright")
        compose.onNodeWithTag("party_scene_2").assertTextEquals("Relax")
        compose.assertClickablesHaveRoleAndLabel()
        compose.onNodeWithTag("party_scene_2").performClick()
        assertThat(scenes).containsExactly("r1" to HouseScene("s2", "Relax"))
        compose.onAllNodesWithTag("party_scene_sheet").assertCountEquals(0)
    }

    @Test
    fun pickingASceneSavesIt() {
        host()
        compose.onNodeWithTag("party_light_0").performClick()
        val picked = savedAfter({ compose.onNodeWithTag("party_scene_1").performClick() }) { it.scenes.isNotEmpty() }
        assertThat(picked.scenes).containsExactly(BRIGHT_IN_KITCHEN)
        compose.onNodeWithTag("party_light_0").assertTextEquals("Kitchen", "Bright")
        compose.onNodeWithTag("party_light_0").performClick()
        val cleared = savedAfter({ compose.onNodeWithTag("party_scene_0").performClick() }) { it.scenes.isEmpty() }
        assertThat(cleared.scenes).isEmpty()
    }

    @Test
    fun aSavedChoiceNoLongerOfferedStillShows() {
        screen(
            ui.copy(
                prefs = PartyPrefs(music = MusicChoice("FV:9/9", "Old mix"), scenes = listOf(SceneChoice("r1", "Kitchen", "gone", "Candlelight"))),
            ),
        )
        compose.onNodeWithTag("party_music").assertTextEquals("Music", "Old mix")
        compose.onNodeWithTag("party_light_0").assertTextEquals("Kitchen", "Candlelight")
    }

    @Test
    fun unlinkedSystemsSaySetUp() {
        screen(ui.copy(music = null, lights = null))
        compose.onNodeWithTag("party_music_line").assertTextEquals("Set up Sonos in Settings › Music to choose the music.")
        compose.onNodeWithTag("party_lights_line").assertTextEquals("Set up Hue in Settings › Lights to choose scenes.")
        compose.onAllNodesWithTag("party_music").assertCountEquals(0)
        compose.onAllNodesWithTag("party_light_0").assertCountEquals(0)
        compose.onAllNodesWithTag("party_speaker_0").assertCountEquals(0)
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun aMissingSeamSaysSetUpToo() {
        host(withMusic = false, withLights = false, waitFor = "party_plugs_line")
        compose.onNodeWithTag("party_music_line").assertTextEquals("Set up Sonos in Settings › Music to choose the music.")
        compose.onNodeWithTag("party_lights_line").assertTextEquals("Set up Hue in Settings › Lights to choose scenes.")
    }

    @Test
    fun aSeamThatIsntReadySaysSetUp() {
        music.readyNow.value = false
        lights.readyNow.value = false
        host(waitFor = "party_plugs_line")
        compose.onNodeWithTag("party_music_line").assertTextEquals("Set up Sonos in Settings › Music to choose the music.")
        compose.onNodeWithTag("party_lights_line").assertTextEquals("Set up Hue in Settings › Lights to choose scenes.")
    }

    @Test
    fun aReadFailureShowsTheLine() {
        val line = "Culvery can’t reach Sonos. Check the speakers are on and the tablet is on the home Wi-Fi."
        screen(ui.copy(music = MusicOptions(emptyList(), emptyList(), line), lights = LightOptions(emptyList(), "The Hue bridge didn’t answer.")))
        compose.onNodeWithTag("party_music_line").assertTextEquals(line)
        compose.onNodeWithTag("party_lights_line").assertTextEquals("The Hue bridge didn’t answer.")
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun thePlugsLinePointsToThePlugsTab() {
        screen(ui)
        compose.onNodeWithTag("party_plugs_line").assertTextEquals("Choose what each plug does in Party mode on the Plugs tab.")
    }

    @Test
    fun theRowsSpeakTheirValue() {
        screen(ui.copy(prefs = PartyPrefs(music = MusicChoice("FV:2/3", "Party mix"), scenes = listOf(BRIGHT_IN_KITCHEN))))
        compose.onNodeWithTag("party_music").assertContentDescriptionEquals("Music, Party mix")
        compose.onNodeWithTag("party_light_0").assertContentDescriptionEquals("Kitchen, Bright")
        compose.onNodeWithTag("party_speaker_2").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assert(hasText("Dining"))
        compose.assertClickablesHaveRoleAndLabel()
    }

    @Test
    fun theToStringNamesNothing() {
        assertThat(ui.copy(prefs = PartyPrefs(music = MusicChoice("FV:2/3", "Party mix"))).toString()).doesNotContain("Party mix")
    }
}
