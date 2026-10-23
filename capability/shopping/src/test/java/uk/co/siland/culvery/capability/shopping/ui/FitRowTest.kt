package uk.co.siland.culvery.capability.shopping.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FitRowTest {
    @get:Rule val compose = createComposeRule()

    /** Fails if a chip that would run past the edge is drawn cut off, or if one after the first dropped is placed. */
    @Test
    fun onlyWholeChipsShow() {
        var width by mutableStateOf(330.dp)
        compose.setContent {
            FitRow(gap = 9.dp, modifier = Modifier.width(width)) {
                repeat(5) { Box(Modifier.size(100.dp, 48.dp).background(Color.Gray).testTag("box$it")) }
            }
        }
        (0..2).forEach { compose.onNodeWithTag("box$it").assertIsDisplayed() }
        (3..4).forEach { compose.onNodeWithTag("box$it").assertIsNotDisplayed() }
        width = 1000.dp
        compose.waitForIdle()
        (0..4).forEach { compose.onNodeWithTag("box$it").assertIsDisplayed() }
    }

    /** Fails if the row drops only the chip that is too wide and lets a narrower one after it through. */
    @Test
    fun aDroppedChipDropsEverythingAfterIt() {
        compose.setContent {
            FitRow(gap = 9.dp, modifier = Modifier.width(330.dp)) {
                Box(Modifier.size(100.dp, 48.dp).testTag("a"))
                Box(Modifier.size(300.dp, 48.dp).testTag("wide"))
                Box(Modifier.size(20.dp, 48.dp).testTag("small"))
            }
        }
        compose.onNodeWithTag("a").assertIsDisplayed()
        compose.onNodeWithTag("wide").assertIsNotDisplayed()
        compose.onNodeWithTag("small").assertIsNotDisplayed()
    }
}
