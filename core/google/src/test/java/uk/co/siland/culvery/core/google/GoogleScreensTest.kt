package uk.co.siland.culvery.core.google

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.LocalShellNavigator
import uk.co.siland.culvery.core.plugin.test.RecordingNavigator

// Robolectric for PendingIntent and the activity result registry.
@RunWith(AndroidJUnit4::class)
class GoogleScreensTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val answers = mutableListOf<Pair<Int, Intent?>>()
    private var notOpened = 0

    private fun intent(): PendingIntent =
        PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE)

    /** Shows the screens once the composition is up; [launch] stands in for the registry's launcher. */
    private fun show(launch: (dispatch: (Int, Intent?) -> Unit) -> Unit) {
        val registry = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    launch { code, data -> dispatchResult(requestCode, code, data) }
                }
            }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalShellNavigator provides RecordingNavigator(calls),
                LocalActivityResultRegistryOwner provides registry,
            ) {
                val screens = rememberGoogleScreens(
                    onAnswer = { code, data -> answers += code to data },
                    onNotOpened = { notOpened++ },
                )
                LaunchedEffect(Unit) { screens.show(intent()) }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aScreenThatFailsToOpenPinsAgainOnce() {
        show { error("no screen") }
        assertThat(calls).containsExactly("leave pinning", "return to pinning").inOrder()
        assertThat(notOpened).isEqualTo(1)
        assertThat(answers).isEmpty()
    }

    @Test
    fun aCancelPinsAgainOnce() {
        show { dispatch -> dispatch(Activity.RESULT_CANCELED, null) }
        compose.waitUntil(5_000) { answers.isNotEmpty() }
        assertThat(calls).containsExactly("leave pinning", "return to pinning").inOrder()
        assertThat(answers).containsExactly(Activity.RESULT_CANCELED to null)
        assertThat(notOpened).isEqualTo(0)
    }

    @Test
    fun anAnswerPinsAgainOnce() {
        show { dispatch -> dispatch(Activity.RESULT_OK, Intent("answer")) }
        compose.waitUntil(5_000) { answers.isNotEmpty() }
        assertThat(calls).containsExactly("leave pinning", "return to pinning").inOrder()
        assertThat(answers).hasSize(1)
        assertThat(answers.single().first).isEqualTo(Activity.RESULT_OK)
        assertThat(answers.single().second?.action).isEqualTo("answer")
    }
}
