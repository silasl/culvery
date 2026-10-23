package uk.co.siland.culvery.provider.shopping_google_tasks

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uk.co.siland.culvery.core.google.GoogleAnswer
import uk.co.siland.culvery.core.google.GoogleJson

class TasksJsonTest {
    private fun tasks(file: String) = GoogleJson.decodeFromString(TasksPage.serializer(), resource(file))

    @Test
    fun theFirstPageHoldsMilkBreadAndABlankTitle() {
        val page = tasks("tasks_page1.json")
        assertThat(page.nextPageToken).isEqualTo("page2")
        assertThat(page.items.map { it.title }).containsExactly("Bread", "Milk", "").inOrder()
        assertThat(page.items.first { it.title == "Milk" }.position).isEqualTo("00000000000000000001")
    }

    @Test
    fun theSecondPageHasASubtaskAndItsLastPageEnds() {
        val page = tasks("tasks_page2.json")
        assertThat(page.nextPageToken).isNull()
        assertThat(page.items.first { it.title == "Butter" }.parent).isEqualTo("t1")
    }

    @Test
    fun historyHoldsCompletedTimes() {
        val done = tasks("tasks_history.json").items.filter { it.status == "completed" }
        assertThat(done.map { it.completed }).containsExactly("2026-10-17T12:00:00.000Z", "2026-10-18T09:00:00.000Z")
        assertThat(done.first().hidden).isTrue()
    }

    @Test
    fun listsDecodeAndAnAnswerWithoutItemsIsEmpty() {
        val lists = GoogleJson.decodeFromString(TaskListsPage.serializer(), resource("tasklists.json"))
        assertThat(lists.items.map { it.title }).containsExactly("Groceries", "shopping").inOrder()
        assertThat(GoogleJson.decodeFromString(TaskListsPage.serializer(), resource("tasklists_none.json")).items).isEmpty()
    }

    @Test
    fun anInsertedTaskAndTheErrorsDecode() {
        assertThat(GoogleJson.decodeFromString(TaskDto.serializer(), resource("task_inserted.json")).id).isEqualTo("t-new")
        assertThat(GoogleAnswer(404, resource("error_404.json")).reason).isEqualTo("notFound")
        assertThat(GoogleAnswer(403, resource("error_scope.json")).reasons)
            .containsExactly("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT").inOrder()
    }

    @Test
    fun anUnknownFieldIsIgnored() {
        val task = GoogleJson.decodeFromString(TaskDto.serializer(), """{"id":"t1","title":"Milk","somethingNew":{"a":1}}""")
        assertThat(task.id).isEqualTo("t1")
    }

    @Test
    fun aTaskNeverPrintsItsTitle() {
        assertThat(TaskDto("t1", "Milk", "needsAction").toString()).doesNotContain("Milk")
        assertThat(NewTask("Milk").toString()).doesNotContain("Milk")
    }

    @Test
    fun aNewTaskEncodesAsJustItsTitle() {
        assertThat(GoogleJson.encodeToString(NewTask.serializer(), NewTask("Milk"))).isEqualTo("""{"title":"Milk"}""")
    }
}
