package uk.co.siland.culvery.capability.lights

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** 7 §4.1, §6: Find Hue Bridge, a typed address and pairing, and what each says when it can't. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LightsConnectTest {
    private val hue = TestLightsProvider()
    private val saved = mutableListOf<Pair<String, LightsLink>>()
    private val bridge = BridgeFound("001788fffe00a1b2", "192.168.1.30")
    private val link = LightsLink("001788fffe00a1b2", "192.168.1.30", "key")

    private fun connect(provider: LightsProvider? = hue, failSave: Boolean = false) = LightsConnect(provider) { id, l ->
        if (failSave) throw IOException("disk full")
        saved += id to l
    }

    /** Fails if find() doesn't hand back the bridge the provider found. */
    @Test
    fun findGivesTheBridge() = runTest {
        hue.found = bridge
        assertThat(connect().find()).isEqualTo(FindOutcome.Found(bridge))
        assertThat(hue.asked).containsExactly("find")
    }

    /** Fails if nothing found doesn't say what to do next. */
    @Test
    fun nothingFoundSaysWhatToDo() = runTest {
        assertThat(connect().find())
            .isEqualTo(FindOutcome.Failed("No Hue Bridge answered. Check it’s plugged in, or enter the bridge’s address."))
    }

    /** Fails if a typed address isn't tidied before it is checked, or the bridge answering isn't returned. */
    @Test
    fun aTypedAddressIsCheckedByItsConfig() = runTest {
        hue.bridges["192.168.1.30"] = bridge
        assertThat(connect().enterAddress(" 192.168.001.030 ")).isEqualTo(FindOutcome.Found(bridge))
        assertThat(hue.asked).containsExactly("check 192.168.1.30")
    }

    /** Fails if a mistyped, named or off-network address is sent to the provider, or the how-to-type line differs. */
    @Test
    fun aMistypedAddressContactsNothing() = runTest {
        val hueConnect = connect()
        val howToType = FindOutcome.Failed("Type the address as four numbers, like 192.168.1.30.")
        for (typed in listOf("192.168.1.30:443", "hue.local", "8.8.8.8", "192.168.1")) {
            assertThat(hueConnect.enterAddress(typed)).isEqualTo(howToType)
        }
        assertThat(hue.asked).isEmpty()
    }

    /** Fails if an address nothing answers at doesn't say so. */
    @Test
    fun anAddressWithNoBridgeSaysSo() = runTest {
        assertThat(connect().enterAddress("192.168.1.99"))
            .isEqualTo(FindOutcome.Failed("No Hue Bridge answered at that address. Check it in the Hue app."))
    }

    /** Fails if pairing doesn't ask at 0, 2 000 ... 28 000 ms (15 times, none at 1 999), or doesn't give up at 30 000 ms. */
    @Test
    fun pairingAsksEveryTwoSecondsForThirtySeconds() = runTest {
        val asks = mutableListOf<Long>()
        val timed = object : LightsProvider by hue {
            override suspend fun pair(bridge: BridgeFound): LightsLink? {
                asks += currentTime
                return null
            }
        }
        val outcome = async(start = CoroutineStart.UNDISPATCHED) { connect(timed).pair(bridge) }
        runCurrent()
        assertThat(asks).containsExactly(0L)
        advanceTimeBy(1_999)
        runCurrent()
        assertThat(asks).hasSize(1)
        advanceTimeBy(1)
        runCurrent()
        assertThat(asks).containsExactly(0L, 2_000L)
        advanceTimeBy(PAIR_FOR_MS - 2_000 - 1)
        runCurrent()
        assertThat(outcome.isCompleted).isFalse()
        advanceTimeBy(1)
        runCurrent()
        assertThat(outcome.await()).isEqualTo(PairOutcome.Failed("The button wasn’t pressed in time. Try again."))
        assertThat(asks).isEqualTo((0L..28_000L step 2_000L).toList())
        assertThat(asks).hasSize(15)
        assertThat(saved).isEmpty()
    }

    /** Fails if a press after some unanswered asks isn't saved as the connection, or pairing carries on after it. */
    @Test
    fun aPressInTimeIsSavedAsTheConnection() = runTest {
        hue.pairAnswers += listOf(null, null, link)
        assertThat(connect().pair(bridge)).isEqualTo(PairOutcome.Paired)
        assertThat(currentTime).isEqualTo(4_000L)
        assertThat(hue.asked).containsExactly("pair", "pair", "pair")
        assertThat(saved).containsExactly("lights.hue" to link)
    }

    /** Fails if an offline tablet keeps asking, or doesn't say it is offline. */
    @Test
    fun offlineSaysSoAndStops() = runTest {
        hue.failure = LightsUnreachableException(LightsProblem.OFFLINE)
        assertThat(connect().pair(bridge)).isEqualTo(PairOutcome.Failed("The tablet is offline. Check the Wi-Fi."))
        assertThat(hue.asked).containsExactly("pair")
        assertThat(saved).isEmpty()
    }

    /** Fails if a save that throws isn't reported as the settings' could-not-save line. */
    @Test
    fun aSaveThatFailsSaysSo() = runTest {
        hue.pairAnswers += link
        assertThat(connect(failSave = true).pair(bridge)).isEqualTo(PairOutcome.Failed("Couldn’t save that change — try again."))
    }
}
