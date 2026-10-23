package uk.co.siland.culvery.provider.shopping_google_tasks

import kotlinx.serialization.Serializable

@Serializable
internal data class TaskListsPage(val items: List<TaskListDto> = emptyList(), val nextPageToken: String? = null)

@Serializable
internal data class TaskListDto(val id: String, val title: String? = null)

@Serializable
internal data class TasksPage(val items: List<TaskDto> = emptyList(), val nextPageToken: String? = null)

@Serializable
internal data class TaskDto(
    val id: String,
    val title: String? = null,
    val status: String? = null,
    val updated: String? = null,
    val completed: String? = null,
    val parent: String? = null,
    val position: String? = null,
    val deleted: Boolean? = null,
    val hidden: Boolean? = null,
) {
    // Item text never reaches a log or an exception message.
    override fun toString(): String = "TaskDto(id=$id, status=$status)"
}

@Serializable
internal data class NewTask(val title: String) {
    override fun toString(): String = "NewTask()"
}

@Serializable
internal data class NewTaskList(val title: String)
