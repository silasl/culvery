package uk.co.siland.culvery.capability.calendar.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorFlowTest {
    @Test
    fun stayDropsAPendingMoveOn() = runTest {
        val flow = EditorFlow(listOf(EditorStep.Who, EditorStep.Day, EditorStep.Time, EditorStep.Check), EditorStep.Day)
        val moves = launch { flow.runMoves() }
        flow.moveOnSoon()
        advanceTimeBy(MOVE_ON_MS / 2)
        flow.stay()
        advanceTimeBy(MOVE_ON_MS)
        assertThat(flow.active).isEqualTo(EditorStep.Day)
        flow.moveOnSoon()
        advanceTimeBy(MOVE_ON_MS + 1)
        assertThat(flow.active).isEqualTo(EditorStep.Time)
        moves.cancel()
    }
}
