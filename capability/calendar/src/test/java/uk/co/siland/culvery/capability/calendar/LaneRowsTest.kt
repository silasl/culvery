package uk.co.siland.culvery.capability.calendar

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test
import uk.co.siland.culvery.core.household.Person

class LaneRowsTest {
    private fun span(title: String, firstDay: Int, lastDay: Int) = SpanUi(
        EventRef("c1", "s-family", title), title, Person.Family,
        LocalDate.of(2026, 10, firstDay), LocalDate.of(2026, 10, lastDay),
        startsBefore = false, endsAfter = false, label = "", spoken = "",
    )

    private fun titles(rows: List<List<SpanUi>>) = rows.map { row -> row.map { it.title } }

    @Test
    fun separateSpansShareARow() {
        val rows = laneRows(listOf(span("Course", 22, 23), span("Half term", 19, 20)))
        assertThat(titles(rows)).containsExactly(listOf("Half term", "Course"))
    }

    @Test
    fun threeOverlappingSpansTakeThreeRows() {
        val rows = laneRows(listOf(span("C", 21, 23), span("A", 19, 21), span("B", 20, 22)))
        assertThat(titles(rows)).containsExactly(listOf("A"), listOf("B"), listOf("C")).inOrder()
    }

    @Test
    fun aSpanInsideAnotherSharesNoRow() {
        val rows = laneRows(listOf(span("Inside", 20, 21), span("Outside", 19, 23), span("Later", 22, 23)))
        assertThat(titles(rows)).containsExactly(listOf("Outside"), listOf("Inside", "Later")).inOrder()
    }

    @Test
    fun packingIsStable() {
        val spans = listOf(
            span("Half term", 19, 23), span("Course", 19, 20), span("Away", 19, 20),
            span("Gran visiting", 21, 22), span("Camping", 24, 25),
        )
        val rows = laneRows(spans)
        assertThat(laneRows(spans.reversed())).isEqualTo(rows)
        assertThat(laneRows(spans.sortedBy { it.title })).isEqualTo(rows)
        // Earliest first day, then longest, then title.
        assertThat(titles(rows)).containsExactly(
            listOf("Half term", "Camping"),
            listOf("Away", "Gran visiting"),
            listOf("Course"),
        ).inOrder()
    }
}
