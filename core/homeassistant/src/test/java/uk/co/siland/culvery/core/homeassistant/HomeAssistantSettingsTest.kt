package uk.co.siland.culvery.core.homeassistant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.plugin.settingsDataStore

/** 11 §4.1: the link is kept in its own file, and nothing names it. */
@RunWith(AndroidJUnit4::class)
class HomeAssistantSettingsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir = File(ApplicationProvider.getApplicationContext<Context>().filesDir, "ha-settings-test").apply { mkdirs() }
    private val settings = HomeAssistantSettings(settingsDataStore(scope) { File(dir, "homeassistant_settings.preferences_pb") })
    private val url = homeAssistantUrl("192.168.1.20")!!

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    /** Fails if a saved link doesn't come back equal. */
    @Test
    fun aSavedLinkComesBack() = runBlocking {
        settings.save(HomeAssistantLink(url, "test-token"))
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(url, "test-token"))
    }

    /** Fails if a new token for the same address doesn't replace the old one. */
    @Test
    fun aNewTokenReplacesTheOld() = runBlocking {
        settings.save(HomeAssistantLink(url, "test-token"))
        settings.save(HomeAssistantLink(url, "new-token"))
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(url, "new-token"))
    }

    /** Fails if there is a link before one is saved. */
    @Test
    fun nothingSavedIsNull() = runBlocking {
        assertThat(settings.link.first()).isNull()
    }

    /** Fails if the link's text holds the address or the token. */
    @Test
    fun theLinkNamesNeitherAddressNorToken() {
        val text = HomeAssistantLink(url, "test-token").toString()
        assertThat(text).doesNotContain("192.168")
        assertThat(text).doesNotContain("test-token")
    }
}
