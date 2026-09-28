package uk.co.siland.culvery

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import uk.co.siland.culvery.di.AppModule

// Robolectric for android.util.Log, which the handler writes to.
@RunWith(AndroidJUnit4::class)
class ApplicationScopeTest {
    @Test
    fun anUncaughtFailureInAnApplicationJobIsLoggedAndItsSiblingsRun() = runBlocking {
        val scope = AppModule.applicationScope()
        try {
            scope.launch { error("a sync job failed") }.join()
            val sibling = CompletableDeferred<Unit>()
            scope.launch { sibling.complete(Unit) }
            withTimeout(5_000) { sibling.await() }
            assertThat(ShadowLog.getLogsForTag("Culvery").map { it.throwable?.message }).contains("a sync job failed")
        } finally {
            scope.cancel()
        }
    }
}
