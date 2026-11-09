package uk.co.siland.culvery.core.homeassistant

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.siland.culvery.core.homeassistant.test.FakeHomeAssistantApi
import uk.co.siland.culvery.core.homeassistant.test.entityState
import uk.co.siland.culvery.core.homeassistant.test.testHomeAssistant
import uk.co.siland.culvery.core.household.Role

/**
 * 11 §4.1: Check asks for an Admin PIN before a different link is saved, and a refused address or token is never saved.
 * Plan 20 N1: an https address asks the PIN, showing the certificate's fingerprint, before the token is sent; Forget asks it too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class HomeAssistantLinkingTest {
    private val api = FakeHomeAssistantApi()
    private val settings = HomeAssistantSettings(MemoryStore())
    private val access = TestAccess(Role.ADMIN)
    private val hoover = hoover()
    private val thermostat = thermostat()
    private val lit = MutableStateFlow(true)
    private lateinit var ha: HomeAssistant

    private suspend fun TestScope.linking(saved: HomeAssistantLink? = null, vararg devices: FakeDevice = arrayOf(hoover, thermostat)): HomeAssistantLinking {
        if (saved != null) settings.save(saved)
        ha = testHomeAssistant(api, settings.link, lit, backgroundScope)
        runCurrent()
        return HomeAssistantLinking(ha, settings, access, devices.toSet()) { homeAssistantUrl(it) }
    }

    private fun oneOfEach() {
        api.states["vacuum.hoover"] = entityState("vacuum.hoover", "docked", mapOf("friendly_name" to "Robot"))
        api.states["climate.hall"] = entityState("climate.hall", "heat", mapOf("friendly_name" to "Hall"))
        api.states["sensor.hoover_battery"] = entityState("sensor.hoover_battery", "86")
    }

    /** Fails if the PIN isn't asked once for the connections permission, or the link isn't saved, or the only ones aren't chosen. */
    @Test
    fun aGoodCheckAsksForAnAdminPinThenSavesAndPicksTheOnlyOnes() = runTest {
        oneOfEach()
        val linking = linking()
        assertThat(linking.check("192.168.1.20", "new-token")).isEqualTo(CheckOutcome.Linked)
        assertThat(access.asked).containsExactly(listOf("connections.manage"))
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(checkNotNull(homeAssistantUrl("192.168.1.20")), "new-token", pin = null))
        assertThat(hoover.picks).containsExactly("vacuum.hoover")
        assertThat(thermostat.picks).containsExactly("climate.hall")
    }

    /** Fails if a kind with two entities is chosen for the household. */
    @Test
    fun twoThermostatsStayUnchosen() = runTest {
        oneOfEach()
        api.states["climate.den"] = entityState("climate.den", "off")
        val linking = linking()
        linking.check("192.168.1.20", "new-token")
        assertThat(hoover.picks).containsExactly("vacuum.hoover")
        assertThat(thermostat.picks).isEmpty()
    }

    /** Fails if a kind that already has a choice is chosen again. */
    @Test
    fun aKindAlreadyChosenIsLeftAlone() = runTest {
        oneOfEach()
        val kept = hoover("vacuum.other")
        val linking = linking(null, kept, thermostat)
        linking.check("192.168.1.20", "new-token")
        assertThat(kept.picks).isEmpty()
        assertThat(thermostat.picks).containsExactly("climate.hall")
    }

    /** Fails if a refused Admin PIN saves anything. */
    @Test
    fun aRefusedPinSavesNothing() = runTest {
        oneOfEach()
        access.role = Role.ADULT
        val linking = linking(SAVED)
        assertThat(linking.check("192.168.1.21", "new-token")).isEqualTo(CheckOutcome.PinRefused)
        assertThat(settings.link.first()).isEqualTo(SAVED)
        assertThat(hoover.picks).isEmpty()
    }

    /** Fails if a cancelled PIN pad saves anything. */
    @Test
    fun aCancelledPinSavesNothing() = runTest {
        access.role = null
        val linking = linking()
        assertThat(linking.check("192.168.1.20", "new-token")).isEqualTo(CheckOutcome.PinRefused)
        assertThat(settings.link.first()).isNull()
    }

    /** Fails if a refused address reaches Home Assistant or asks for the PIN. */
    @Test
    fun aRefusedAddressSendsNothing() = runTest {
        val linking = linking()
        assertThat(linking.check("http://ha.example.com", "new-token")).isEqualTo(CheckOutcome.Failed(ADDRESS_REFUSED))
        assertThat(api.calls).isEmpty()
        assertThat(access.asked).isEmpty()
    }

    /** Fails if a token with a character a header can't carry is sent, or isn't told to paste it again. */
    @Test
    fun aTokenThatCantBeSentSaysSoAndSendsNothing() = runTest {
        val linking = linking()
        val line = "That token has characters it can’t contain. Copy it again from Home Assistant and paste it here."
        listOf("abc\u200Bdef", "abc\ndef", "abc def", "abc\u0007def", "abc\u00E9def").forEach { token ->
            assertThat(linking.check("192.168.1.20", token)).isEqualTo(CheckOutcome.Failed(line))
        }
        assertThat(api.calls).isEmpty()
        assertThat(access.asked).isEmpty()
    }

    /** Fails if a refused token isn't told to make a new one, or is saved, or asks for the PIN. */
    @Test
    fun aRefusedTokenSaysMakeANewOne() = runTest {
        api.failNext = HaProblem.TOKEN_REFUSED
        val linking = linking()
        assertThat(linking.check("192.168.1.20", "new-token"))
            .isEqualTo(CheckOutcome.Failed("Home Assistant didn’t accept that token. Create a new one and paste it here."))
        assertThat(settings.link.first()).isNull()
        assertThat(access.asked).isEmpty()
    }

    /** Fails if an address that isn't Home Assistant gets any other line. */
    @Test
    fun notHomeAssistantSaysCheckTheAddress() = runTest {
        api.failNext = HaProblem.NOT_HOME_ASSISTANT
        val linking = linking()
        assertThat(linking.check("192.168.1.20", "new-token"))
            .isEqualTo(CheckOutcome.Failed("Nothing at that address answered as Home Assistant. Check the address."))
        assertThat(access.asked).isEmpty()
    }

    /** Fails if a link problem has a line other than its §6 one, or saves or asks for the PIN. */
    @Test
    fun unreachableOfflineAndBlockedGiveTheirLines() = runTest {
        val linking = linking()
        for (problem in listOf(HaProblem.UNREACHABLE, HaProblem.OFFLINE, HaProblem.BLOCKED)) {
            api.failNext = problem
            assertThat(linking.check("192.168.1.20", "new-token")).isEqualTo(CheckOutcome.Failed(problemLine(problem)))
        }
        assertThat(access.asked).isEmpty()
        assertThat(settings.link.first()).isNull()
    }

    /** Fails if re-checking the saved link asks for a PIN, or leaves the problem. */
    @Test
    fun theSameLinkAsksNoPin() = runTest {
        val linking = linking(SAVED)
        api.failNext = HaProblem.TOKEN_REFUSED
        runCatching { ha.read("vacuum.hoover") }
        assertThat(ha.problem.value).isEqualTo(HaProblem.TOKEN_REFUSED)
        assertThat(linking.check("192.168.1.20", "")).isEqualTo(CheckOutcome.Linked)
        assertThat(access.asked).isEmpty()
        assertThat(ha.problem.value).isNull()
    }

    /** Fails if a different address is checked with the saved token. */
    @Test
    fun aChangedAddressNeedsAToken() = runTest {
        val linking = linking(SAVED)
        assertThat(linking.check("192.168.1.21", "")).isEqualTo(CheckOutcome.Failed("Paste a token for Culvery first."))
        assertThat(api.calls).isEmpty()
    }

    /** Fails if a new token for the saved address doesn't ask for the PIN and replace the old one. */
    @Test
    fun aNewTokenForTheSavedAddressAsksAndReplaces() = runTest {
        val linking = linking(SAVED)
        assertThat(linking.check("192.168.1.20", "new-token")).isEqualTo(CheckOutcome.Linked)
        assertThat(access.asked).hasSize(1)
        assertThat(settings.link.first()?.token).isEqualTo("new-token")
    }

    /** Fails if a kind set to None is chosen again by a later Check of the saved link. */
    @Test
    fun noneChosenLaterStaysNone() = runTest {
        oneOfEach()
        val linking = linking()
        linking.check("192.168.1.20", "new-token")
        hoover.choose(null)
        hoover.picks.clear()
        assertThat(linking.check("192.168.1.20", "")).isEqualTo(CheckOutcome.Linked)
        assertThat(hoover.picks).isEmpty()
    }

    /** Fails if the pickers' entities aren't the domain's, by name, with the id as the name when there is none. */
    @Test
    fun entitiesAreTheDomainsByName() = runTest {
        oneOfEach()
        api.states["climate.attic"] = entityState("climate.attic", "off")
        val linking = linking(SAVED)
        assertThat(linking.entities(setOf("climate"))["climate"].orEmpty().map { it.id to it.name })
            .containsExactly("climate.attic" to "climate.attic", "climate.hall" to "Hall").inOrder()
    }

    /** Fails if one load of the pickers reads Home Assistant's states more than once. */
    @Test
    fun oneLoadOfEveryKindIsOneRead() = runTest {
        oneOfEach()
        val linking = linking(SAVED)
        val before = api.calls.count { it == "states" }
        val all = linking.entities(setOf("vacuum", "climate"))
        assertThat(api.calls.count { it == "states" } - before).isEqualTo(1)
        assertThat(all.keys).containsExactly("vacuum", "climate")
    }

    /** Fails if a pick that can't be written undoes a saved link's Linked. */
    @Test
    fun aFailedAutoPickStillLinks() = runTest {
        oneOfEach()
        val linking = linking(null, hoover.also { it.failure = java.io.IOException("x") }, thermostat)
        assertThat(linking.check("192.168.1.20", "new-token")).isEqualTo(CheckOutcome.Linked)
        assertThat(settings.link.first()?.token).isEqualTo("new-token")
        assertThat(thermostat.picks).containsExactly("climate.hall")
    }

    /** Pin: a refused PIN leaves a refused token's problem in place. */
    @Test
    fun aRefusedPinKeepsTheTokenProblem() = runTest {
        access.role = Role.ADULT
        val linking = linking(SAVED)
        api.failNext = HaProblem.TOKEN_REFUSED
        runCatching { ha.read("vacuum.hoover") }
        assertThat(linking.check("192.168.1.20", "new-token")).isEqualTo(CheckOutcome.PinRefused)
        assertThat(ha.problem.value).isEqualTo(HaProblem.TOKEN_REFUSED)
    }

    /** Regression guard: with no pin stored, http links as before, with no certificate read and a link saved without a pin. */
    @Test
    fun httpStillLinksWithNoPin() = runTest {
        val linking = linking()
        assertThat(linking.check("http://192.168.1.20:8123", "new-token")).isEqualTo(CheckOutcome.Linked)
        assertThat(api.calls).doesNotContain("certificate")
        assertThat(access.labels).containsExactly(null)
        assertThat(access.lastDetail).isNull()
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(HTTP_H, "new-token", pin = null))
    }

    /** Fails if anything but the tokenless certificate read reaches Home Assistant before the Admin PIN is given. */
    @Test
    fun httpsAsksTheFreshPinBeforeTheTokenIsSent() = runTest {
        val linking = linking()
        val pad = CompletableDeferred<Unit>().also { access.padGate = it }
        val outcome = async { linking.check("https://192.168.1.20:8443", "new-token") }
        runCurrent()
        assertThat(api.calls).containsExactly("certificate")
        assertThat(access.asked).containsExactly(listOf("connections.manage"))
        pad.complete(Unit)
        assertThat(outcome.await()).isEqualTo(CheckOutcome.Linked)
        assertThat(api.calls.take(2)).containsExactly("certificate", "check").inOrder()
        assertThat(api.checked.first()).isEqualTo(HomeAssistantLink(HTTPS_H, "new-token", PIN))
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(HTTPS_H, "new-token", PIN))
        assertThat(access.asked).hasSize(1)
    }

    /** Fails if the PIN pad isn't labelled to trust Home Assistant, or doesn't show the fingerprint as grouped lowercase hex. */
    @Test
    fun thePinPadShowsTheFingerprint() = runTest {
        val linking = linking()
        linking.check("https://192.168.1.20:8443", "new-token")
        assertThat(access.labels).containsExactly("Trust this Home Assistant")
        assertThat(access.lastDetail).isEqualTo(
            "Certificate fingerprint\n0001 0203 0405 0607 0809 0a0b 0c0d 0e0f 1011 1213 1415 1617 1819 1a1b 1c1d 1e1f",
        )
    }

    /** Fails if a refused or cancelled trust PIN sends the token or saves anything. */
    @Test
    fun aRefusedPinSendsNoTokenAndSavesNothing() = runTest {
        val linking = linking(SAVED)
        for (role in listOf(Role.ADULT, null)) {
            access.role = role
            assertThat(linking.check("https://192.168.1.20:8443", "new-token")).isEqualTo(CheckOutcome.PinRefused)
        }
        assertThat(api.calls).containsExactly("certificate", "certificate")
        assertThat(settings.link.first()).isEqualTo(SAVED)
        assertThat(settings.storedPin.first()).isNull()
    }

    /** Fails if moving the saved http link to https on the same host needs the token again, or asks a second PIN to save. */
    @Test
    fun theUpgradeKeepsTheSavedToken() = runTest {
        val linking = linking(SAVED)
        assertThat(linking.check("https://192.168.1.21:8443", "")).isEqualTo(CheckOutcome.Failed(PASTE_A_TOKEN))
        assertThat(api.calls).isEmpty()
        assertThat(linking.check("https://192.168.1.20:8443", "")).isEqualTo(CheckOutcome.Linked)
        assertThat(access.labels).containsExactly("Trust this Home Assistant")
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(HTTPS_H, "test-token", PIN))
    }

    /** Fails if the pinned key at a new port needs the token again or asks to trust it; the different link still asks to save. */
    @Test
    fun theSameKeyOnANewPortKeepsTheToken() = runTest {
        val linking = linking(PINNED)
        assertThat(linking.check("https://192.168.1.20:9443", "")).isEqualTo(CheckOutcome.Linked)
        assertThat(access.labels).containsExactly(null)
        assertThat(access.lastDetail).isNull()
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(checkNotNull(homeAssistantUrl("https://192.168.1.20:9443")), "test-token", PIN))
    }

    /** Fails if an http address is contacted, or asks the PIN, once a pin is stored. */
    @Test
    fun httpIsRefusedOncePinned() = runTest {
        val linking = linking(PINNED)
        for (token in listOf("new-token", "")) {
            assertThat(linking.check("http://192.168.1.20:8123", token))
                .isEqualTo(CheckOutcome.Failed("This Home Assistant is linked over https. Type its address starting https://."))
        }
        assertThat(api.calls).isEmpty()
        assertThat(access.asked).isEmpty()
        assertThat(settings.link.first()).isEqualTo(PINNED)
    }

    /** Fails if a record with a pin but no address lets an http address through. */
    @Test
    fun httpIsRefusedWhenOnlyThePinIsStored() = runTest {
        val store = MemoryStore()
        store.updateData { it.toMutablePreferences().apply { set(stringPreferencesKey("certificate_pin"), PIN) } }
        val onlyPin = HomeAssistantSettings(store)
        ha = testHomeAssistant(api, onlyPin.link, lit, backgroundScope)
        val linking = HomeAssistantLinking(ha, onlyPin, access, setOf(hoover)) { homeAssistantUrl(it) }
        assertThat(onlyPin.link.first()).isNull()
        assertThat(linking.check("http://192.168.1.20:8123", "new-token")).isEqualTo(CheckOutcome.Failed(USE_HTTPS))
        assertThat(api.calls).isEmpty()
        assertThat(access.asked).isEmpty()
    }

    /** Fails if an http save that meets a pin stored meanwhile crashes, replaces the pin, or gets any line but the https one. */
    @Test
    fun anHttpSaveRacingAStoredPinGetsUseHttpsAndKeepsThePin() = runTest {
        val linking = linking(SAVED)
        val pad = CompletableDeferred<Unit>().also { access.padGate = it }
        val outcome = async { linking.check("http://192.168.1.20:8123", "new-token") }
        runCurrent()
        settings.save(PINNED)
        pad.complete(Unit)
        assertThat(outcome.await()).isEqualTo(CheckOutcome.Failed(USE_HTTPS))
        assertThat(settings.link.first()).isEqualTo(PINNED)
        assertThat(settings.storedPin.first()).isEqualTo(PINNED.pin)
    }

    /** Fails if the https-only rule reads the typed text rather than the address's scheme. */
    @Test
    fun aCapitalisedHttpSchemeIsRefusedOncePinned() = runTest {
        val linking = linking(PINNED)
        assertThat(linking.check("HTTP://192.168.1.20:8123", "new-token")).isEqualTo(CheckOutcome.Failed(USE_HTTPS))
        assertThat(api.calls).isEmpty()
        assertThat(access.asked).isEmpty()
    }

    /** Fails if a different key at the pinned address is trusted without the PIN and its fingerprint, or asks twice. */
    @Test
    fun aNewKeyAsksThePinAgain() = runTest {
        val linking = linking(PINNED)
        api.certificate = OTHER_PIN
        assertThat(linking.check("https://192.168.1.20:8443", "")).isEqualTo(CheckOutcome.Linked)
        assertThat(access.labels).containsExactly("Trust this Home Assistant")
        assertThat(access.lastDetail).isEqualTo("Certificate fingerprint\n${fingerprint(OTHER_PIN)}")
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(HTTPS_H, "test-token", OTHER_PIN))
    }

    /** Fails if re-checking the pinned link with the same key asks any PIN. */
    @Test
    fun theSameKeyDoesNotAskAgain() = runTest {
        val linking = linking(PINNED)
        assertThat(linking.check("https://192.168.1.20:8443", "")).isEqualTo(CheckOutcome.Linked)
        assertThat(access.asked).isEmpty()
        assertThat(api.checked).containsExactly(PINNED)
    }

    /** Fails if a key that changes between the certificate read and the check saves anything, or gets another line. */
    @Test
    fun aKeyChangedDuringCheckSavesNothing() = runTest {
        val linking = linking(SAVED)
        val pad = CompletableDeferred<Unit>().also { access.padGate = it }
        val outcome = async { linking.check("https://192.168.1.20:8443", "") }
        runCurrent()
        api.failNext = HaProblem.CERTIFICATE_CHANGED
        pad.complete(Unit)
        assertThat(outcome.await())
            .isEqualTo(CheckOutcome.Failed("Home Assistant’s certificate has changed. Press Check to trust the new one."))
        assertThat(settings.link.first()).isEqualTo(SAVED)
        assertThat(settings.storedPin.first()).isNull()
    }

    /** Fails if a certificate read that fails asks the PIN, or gets a line other than Check's. */
    @Test
    fun aFailedCertificateReadAsksNoPin() = runTest {
        val linking = linking()
        api.failNext = HaProblem.NOT_HOME_ASSISTANT
        assertThat(linking.check("https://192.168.1.20:8443", "new-token")).isEqualTo(CheckOutcome.Failed(NOT_HOME_ASSISTANT_LINE))
        assertThat(api.calls).containsExactly("certificate")
        assertThat(access.asked).isEmpty()
    }

    /** Fails if Forget clears without a fresh Admin PIN, leaves any of the three, or stops http linking again. */
    @Test
    fun forgetAsksThePinAndClearsAll() = runTest {
        val linking = linking(PINNED)
        access.role = Role.ADULT
        assertThat(linking.forget()).isFalse()
        assertThat(settings.link.first()).isEqualTo(PINNED)
        access.role = Role.ADMIN
        assertThat(linking.forget()).isTrue()
        assertThat(access.asked).containsExactly(listOf("connections.manage"), listOf("connections.manage"))
        assertThat(access.labels).containsExactly("Forget this Home Assistant", "Forget this Home Assistant")
        assertThat(settings.link.first()).isNull()
        assertThat(settings.storedPin.first()).isNull()
        assertThat(linking.check("http://192.168.1.20:8123", "new-token")).isEqualTo(CheckOutcome.Linked)
        assertThat(settings.link.first()).isEqualTo(HomeAssistantLink(HTTP_H, "new-token", pin = null))
    }

    /** Fails if a choice's text holds its id or name. */
    @Test
    fun aChoiceNamesNeitherIdNorName() {
        val text = EntityChoice("climate.hall", "Hall").toString()
        assertThat(text).doesNotContain("hall")
        assertThat(text).doesNotContain("Hall")
    }

    private companion object {
        val HTTP_H = checkNotNull(homeAssistantUrl("http://192.168.1.20:8123"))
        val HTTPS_H = checkNotNull(homeAssistantUrl("https://192.168.1.20:8443"))
        const val PIN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        const val OTHER_PIN = "HyAhIiMkJSYnKCkqKywtLi8wMTIzNDU2Nzg5Ojs8PT4="
        val PINNED = HomeAssistantLink(HTTPS_H, "test-token", PIN)
    }
}
