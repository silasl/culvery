package uk.co.siland.culvery.capability.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.capability.calendar.db.CalendarDatabase
import uk.co.siland.culvery.capability.calendar.db.RememberedTitleEntity
import uk.co.siland.culvery.core.household.PersonId
import uk.co.siland.culvery.core.plugin.Connection

@RunWith(AndroidJUnit4::class)
class TitleSuggestionsTest {
    private lateinit var db: CalendarDatabase
    private lateinit var store: CalendarStore
    private lateinit var suggestions: TitleSuggestions

    @Before
    fun setUp() {
        db = calendarDb()
        store = CalendarStore(db)
        suggestions = TitleSuggestions(store)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun connect(id: String, vararg sources: Pair<String, Boolean>) = store.addConnection(
        Connection(id, "calendar.test", id, emptyMap()),
        sources.map { CalendarSource(it.first, it.first.uppercase(), writable = false) },
        sources.associate { it.first to SourceMapping(PersonId.FAMILY, visible = it.second) },
    )

    private suspend fun remember(title: String, date: String, connection: String = "c1", source: String = "s1") =
        db.calendarDao().upsertRemembered(
            RememberedTitleEntity(
                connection, titleKey(title), title, source, "$connection-$source-$title", "family", false, 9 * 60, 60, null, null, null, date,
            ),
        )

    private suspend fun titles(typed: String, max: Int = 3) = suggestions.matching(typed, max).map { it.title }

    @Test
    fun nothingUnderTwoCharacters() = runTest {
        connect("c1", "s1" to true)
        remember("Guitar lesson", "2026-09-12")
        assertThat(titles("")).isEmpty()
        assertThat(titles("g")).isEmpty()
        assertThat(titles(" g ")).isEmpty()
        assertThat(titles("gu")).containsExactly("Guitar lesson")
    }

    @Test
    fun aWordInsideMatches() = runTest {
        connect("c1", "s1" to true)
        remember("Guitar lesson", "2026-09-12")
        assertThat(titles("less")).containsExactly("Guitar lesson")
        assertThat(titles("guitar les")).containsExactly("Guitar lesson")
        assertThat(titles("itar")).isEmpty()
    }

    @Test
    fun caseAndAccentsAreIgnored() = runTest {
        connect("c1", "s1" to true)
        remember("Café Lunch", "2026-09-12")
        assertThat(titles("CAFE")).containsExactly("Café Lunch")
        assertThat(titles("lunch")).containsExactly("Café Lunch")
        assertThat(titles("café l")).containsExactly("Café Lunch")
    }

    @Test
    fun titleStartsComeBeforeWordMatchesThenLatestFirst() = runTest {
        connect("c1", "s1" to true)
        remember("Piano lesson", "2026-09-20")
        remember("Lesson plan", "2026-09-01")
        remember("Lessons", "2026-09-10")
        remember("Guitar lesson", "2026-09-22")
        assertThat(titles("less")).containsExactly("Lessons", "Lesson plan", "Guitar lesson").inOrder()
        assertThat(titles("less", max = 4)).containsExactly("Lessons", "Lesson plan", "Guitar lesson", "Piano lesson").inOrder()
    }

    @Test
    fun atMostThree() = runTest {
        connect("c1", "s1" to true)
        listOf("Swim a", "Swim b", "Swim c", "Swim d").forEachIndexed { i, t -> remember(t, "2026-09-0${i + 1}") }
        assertThat(titles("swim")).containsExactly("Swim d", "Swim c", "Swim b").inOrder()
    }

    @Test
    fun theSameTitleOnTwoConnectionsShowsOnce() = runTest {
        connect("c1", "s1" to true)
        connect("c2", "s1" to true)
        remember("Swim", "2026-09-05", connection = "c1")
        remember("swim", "2026-09-19", connection = "c2")
        val found = suggestions.matching("swim")
        assertThat(found.map { it.title }).containsExactly("swim")
        assertThat(found.single().rememberedDate.toString()).isEqualTo("2026-09-19")
    }

    @Test
    fun aHiddenCalendarsTitlesAreNotSuggested() = runTest {
        connect("c1", "s1" to true, "s2" to false)
        remember("Swim", "2026-09-05", source = "s1")
        remember("Swimming gala", "2026-09-19", source = "s2")
        assertThat(titles("swim")).containsExactly("Swim")
    }

    @Test
    fun aSuggestionCarriesWhatWasRemembered() = runTest {
        connect("c1", "s1" to true)
        db.calendarDao().upsertRemembered(
            RememberedTitleEntity(
                "c1", "guitar lesson", "Guitar lesson", "s1", "e1", "mia", false, 10 * 60, 45, null,
                "RRULE:FREQ=WEEKLY;INTERVAL=1", "2026-09-05", "2026-09-12",
            ),
        )
        val s = suggestions.matching("guitar").single()
        assertThat(s.forPerson).isEqualTo("mia")
        assertThat(s.start.toString()).isEqualTo("10:00")
        assertThat(s.length?.toMinutes()).isEqualTo(45)
        assertThat(s.rule).isEqualTo("RRULE:FREQ=WEEKLY;INTERVAL=1")
        assertThat(s.seriesStart.toString()).isEqualTo("2026-09-05")
    }
}
