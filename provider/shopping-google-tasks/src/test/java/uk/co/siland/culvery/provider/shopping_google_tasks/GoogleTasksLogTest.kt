package uk.co.siland.culvery.provider.shopping_google_tasks

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.core.google.PlayServicesCheck
import uk.co.siland.culvery.core.google.test.FakeAuthorizer
import uk.co.siland.culvery.core.google.test.FakeGoogleAccounts
import uk.co.siland.culvery.core.google.test.FakeTokenSource
import uk.co.siland.culvery.core.google.test.assertLogsHoldNoPersonalData
import uk.co.siland.culvery.core.google.test.failureOf
import uk.co.siland.culvery.core.plugin.test.RecordingToaster

@RunWith(AndroidJUnit4::class)
class GoogleTasksLogTest {
    private val google = FakeTasksServer()
    private lateinit var provider: GoogleTasksProvider

    @Before
    fun setUp() {
        ShadowLog.clear()
        val api = TasksApi(google.start(), FakeTokenSource(), OkHttpClient())
        provider = GoogleTasksProvider(api, FakeAuthorizer(), FakeGoogleAccounts(listOf(ACCOUNT)), PlayServicesCheck { true }, RecordingToaster())
    }

    @After
    fun tearDown() = google.stop()

    @Test
    fun noLogLineHoldsItemText() = runTest {
        val path = "/tasks/v1/lists/L1/tasks"
        val refusal = """{"error":{"code":400,"message":"Invalid title: Bin bags","errors":[{"message":"Invalid title: Bin bags","reason":"invalid"}]}}"""
        google.on("POST", path, google.json(refusal, 400))
        google.on("GET", path, google.json("""{"items":[{"id":"t1","title":"Tea bags" BROKEN"""))
        failureOf { provider.add(CONNECTION, "Bin bags", "k1") }
        failureOf { provider.items(CONNECTION) }
        failureOf { provider.tick(CONNECTION, "t1") }

        assertLogsHoldNoPersonalData("Bin bags", "Tea bags", minLines = 3)
        val logs = ShadowLog.getLogs()
        assertThat(logs.map { it.tag }.toSet()).containsExactly("GoogleTasks")
        logs.forEach { log ->
            assertWithMessage(log.msg).that(listOf("tasks.insert", "tasks.list", "tasks.patch").any { it in log.msg }).isTrue()
        }
    }
}
