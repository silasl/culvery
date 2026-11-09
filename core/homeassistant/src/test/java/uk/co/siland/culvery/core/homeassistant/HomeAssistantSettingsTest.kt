package uk.co.siland.culvery.core.homeassistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val store = settingsDataStore(scope) { File(dir, "homeassistant_settings.preferences_pb") }
    private val settings = HomeAssistantSettings(store)
    private val url = homeAssistantUrl("192.168.1.20")!!
    private val secureUrl = homeAssistantUrl("https://ha.example.com")!!
    private val address = stringPreferencesKey("address")
    private val token = stringPreferencesKey("token")
    private val certificatePin = stringPreferencesKey("certificate_pin")

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    /** Fails if a saved link doesn't come back equal. */
    @Test
    fun aSavedLinkComesBack() = runBlocking {
        settings.save(HomeAssistantLink(url, "test-token", pin = null))
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(url, "test-token", pin = null))
    }

    /** Fails if a new token for the same address doesn't replace the old one. */
    @Test
    fun aNewTokenReplacesTheOld() = runBlocking {
        settings.save(HomeAssistantLink(url, "test-token", pin = null))
        settings.save(HomeAssistantLink(url, "new-token", pin = null))
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(url, "new-token", pin = null))
    }

    /** Fails if there is a link before one is saved. */
    @Test
    fun nothingSavedIsNull() = runBlocking {
        assertThat(settings.link.first()).isNull()
    }

    /** Fails if the link's text holds the address, the token or the pin. */
    @Test
    fun theLinkNamesNeitherAddressNorTokenNorPin() {
        val text = HomeAssistantLink(secureUrl, "test-token", "test-pin").toString()
        assertThat(text).doesNotContain("ha.example")
        assertThat(text).doesNotContain("test-token")
        assertThat(text).doesNotContain("test-pin")
    }

    /** Fails if a pin saved with the address and token isn't read back with them. */
    @Test
    fun thePinRoundTrips() = runBlocking {
        settings.save(HomeAssistantLink(secureUrl, "test-token", "test-pin"))
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(secureUrl, "test-token", "test-pin"))
        assertThat(settings.storedPin.first()).isEqualTo("test-pin")
    }

    /** Fails if a save without a pin leaves a pin behind that Forget cleared, or stores one. */
    @Test
    fun savingWithoutAPinAfterForgettingStoresNone() = runBlocking {
        settings.save(HomeAssistantLink(secureUrl, "test-token", "test-pin"))
        settings.forget()
        settings.save(HomeAssistantLink(url, "test-token", pin = null))
        assertThat(settings.storedPin.first()).isNull()
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(url, "test-token", pin = null))
    }

    /** Fails if a save without a pin goes through while a pin is stored, or changes anything when it is refused. */
    @Test
    fun aPinlessSaveIsRefusedWhileAPinIsStored() = runBlocking {
        val pinned = HomeAssistantLink(secureUrl, "test-token", "test-pin")
        settings.save(pinned)
        for (link in listOf(HomeAssistantLink(url, "other-token", pin = null), HomeAssistantLink(secureUrl, "other-token", pin = null))) {
            try {
                settings.save(link)
                error("The save went through")
            } catch (_: PinStoredException) {
            }
        }
        assertThat(settings.link.first()).isEqualTo(pinned)
        assertThat(settings.storedPin.first()).isEqualTo("test-pin")
    }

    /** Fails if an http address with a pin reads as a link. */
    @Test
    fun httpWithAPinReadsAsNotLinked() = runBlocking {
        store.edit {
            it[address] = url.toString()
            it[token] = "test-token"
            it[certificatePin] = "test-pin"
        }
        assertThat(settings.link.first()).isNull()
        assertThat(settings.storedPin.first()).isEqualTo("test-pin")
    }

    /** Fails if an http address saved with a pin is written. */
    @Test
    fun httpWithAPinCannotBeSaved() = runBlocking {
        runCatching { settings.save(HomeAssistantLink(url, "test-token", "test-pin")) }
            .also { assertThat(it.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java) }
        assertThat(settings.link.first()).isNull()
    }

    /** Fails if the stored pin is read through the link. */
    @Test
    fun storedPinReadsTheKeyWhateverTheRest() = runBlocking {
        store.edit { it[certificatePin] = "test-pin" }
        assertThat(settings.storedPin.first()).isEqualTo("test-pin")
        assertThat(settings.link.first()).isNull()
    }

    /** Fails if forgetting leaves the address, the token or the pin. */
    @Test
    fun forgetClearsAllThree() = runBlocking {
        settings.save(HomeAssistantLink(secureUrl, "test-token", "test-pin"))
        settings.forget()
        assertThat(settings.link.first()).isNull()
        assertThat(settings.storedPin.first()).isNull()
        val kept = store.data.first()
        assertThat(kept[address]).isNull()
        assertThat(kept[token]).isNull()
    }

    /** Regression guard: a record with only an address and a token, as Plan 19 wrote it, still links, with no pin. */
    @Test
    fun aLinkSavedBeforePlan20StillLinks() = runBlocking {
        store.edit {
            it[address] = url.toString()
            it[token] = "test-token"
        }
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(url, "test-token", pin = null))
    }
}
