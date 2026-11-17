package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WhoPickerTest {
    @get:Rule val compose = createComposeRule()

    private val family = "family"

    @Test
    fun familyReplacesThePicksAndAPersonReplacesFamily() {
        assertThat(toggledWho(listOf("sam", "mia"), tapped = family, family = family)).containsExactly(family)
        assertThat(toggledWho(listOf(family), tapped = "sam", family = family)).containsExactly("sam")
        assertThat(toggledWho(listOf("sam"), tapped = "mia", family = family)).containsExactly("sam", "mia").inOrder()
        assertThat(toggledWho(listOf("sam", "mia"), tapped = "sam", family = family)).containsExactly("mia")
    }

    @Test
    fun theLastPersonStaysUnlessMayClear() {
        assertThat(toggledWho(listOf("mia"), tapped = "mia", family = family)).containsExactly("mia")
        assertThat(toggledWho(listOf("mia"), tapped = "mia", family = family, mayClear = true)).isEmpty()
    }

    @Test
    fun aChildsTilesAreLockedOnceAnyoneIsChosen() {
        assertThat(lockedForWho("sam", chosen = emptyList(), only = null)).isFalse()
        assertThat(lockedForWho("sam", chosen = emptyList(), only = "mia")).isTrue()
        assertThat(lockedForWho("mia", chosen = emptyList(), only = "mia")).isFalse()
        assertThat(lockedForWho("mia", chosen = listOf("mia"), only = "mia")).isTrue()
        assertThat(lockedForWho(family, chosen = listOf("mia"), only = "mia")).isTrue()
    }

    @Test
    fun aLockedUnchosenTileCallsOnRefused() {
        val picked = mutableListOf<String?>()
        var refused = 0
        compose.setContent {
            CulveryTheme(dark = true) {
                HhWhoPicker(
                    options = SampleWho.options, chosen = setOf("mia"), everyone = false,
                    everyoneLine = "Shows as a Family event", everyoneColour = SampleWho.FAMILY,
                    locked = { true }, enabled = true,
                    onPick = { picked += it }, onRefused = { refused++ },
                )
            }
        }
        compose.onNodeWithTag("who_Sam").performClick()
        compose.onNodeWithTag("who_everyone").performClick()
        compose.onNodeWithTag("who_Mia").performClick()
        compose.runOnIdle {
            assertThat(refused).isEqualTo(2)
            assertThat(picked).isEmpty()
        }
    }

    @Test
    fun anUnlockedTileOrEveryonePicksItsKeyAndABusyPickerTakesNoTap() {
        val picked = mutableListOf<String?>()
        var enabled by mutableStateOf(true)
        compose.setContent {
            CulveryTheme(dark = true) {
                HhWhoPicker(
                    options = SampleWho.options, chosen = emptySet(), everyone = false,
                    everyoneLine = "Shows as a Family event", everyoneColour = SampleWho.FAMILY,
                    locked = { false }, enabled = enabled,
                    onPick = { picked += it }, onRefused = {},
                )
            }
        }
        compose.onNodeWithTag("who_Sam").performClick()
        compose.onNodeWithTag("who_everyone").performClick()
        compose.runOnIdle { assertThat(picked).containsExactly("sam", null).inOrder() }
        enabled = false
        compose.onNodeWithTag("who_Mia").performClick()
        compose.runOnIdle { assertThat(picked).hasSize(2) }
    }

    @Test
    fun aWhoOptionPrintsOnlyItsTypeName() {
        assertThat(SampleWho.options.first().toString()).isEqualTo("WhoOption")
    }
}

/** The sample household as the picker takes it. */
internal object SampleWho {
    const val FAMILY = 0xFF9C7CE3
    val options = listOf(
        WhoOption("alex", "Alex", "Admin", 0xFF4CB387),
        WhoOption("sam", "Sam", "Adult", 0xFF5B9BE0),
        WhoOption("mia", "Mia", "Child", 0xFFE07BA8),
    )
}
