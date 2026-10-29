package uk.co.siland.culvery.sample

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import uk.co.siland.culvery.CulveryApp
import uk.co.siland.culvery.DebugSampleHouseReceiver
import uk.co.siland.culvery.capability.lights.LightsLink
import uk.co.siland.culvery.capability.lights.LightsProblem
import uk.co.siland.culvery.capability.lights.LightsUnreachableException
import uk.co.siland.culvery.capability.music.MusicLink
import uk.co.siland.culvery.capability.music.MusicProblem
import uk.co.siland.culvery.capability.music.MusicUnreachableException
import uk.co.siland.culvery.capability.music.PlayState
import uk.co.siland.culvery.capability.music.RoomGroup

/** The debug samples (13 §8): in-memory speakers and lights the walk-through groups, plays and changes by hand. */
@RunWith(AndroidJUnit4::class)
@Config(application = CulveryApp::class)
class SampleHouseTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val deps by lazy { EntryPointAccessors.fromApplication(context, DebugSampleHouseReceiver.Dependencies::class.java) }
    private val music get() = deps.sampleMusic().system
    private val lights get() = deps.sampleLights().system

    private fun receive(configure: Intent.() -> Unit) = DebugSampleHouseReceiver().onReceive(context, Intent().apply(configure))

    private suspend inline fun <reified T : Exception> thrown(block: suspend () -> Unit): T {
        try {
            block()
        } catch (e: Exception) {
            if (e is T) return e
            throw e
        }
        throw AssertionError("Expected ${T::class.simpleName}")
    }

    private suspend fun partyOfThree() = music.group(KITCHEN, setOf(KITCHEN, LOUNGE, DINING))

    @Test
    fun theSampleSpeakersGroupAndRegroup() = runBlocking<Unit> {
        assertThat(music.groups.value.map { it.rooms.size }).containsExactly(1, 1, 1)
        val party = partyOfThree()
        assertThat(party.rooms.map { it.id }).containsExactly(KITCHEN, LOUNGE, DINING)
        assertThat(party.coordinatorId).isEqualTo(KITCHEN)
        assertThat(music.groups.value).hasSize(1)

        music.regroup(listOf(RoomGroup(KITCHEN, listOf(KITCHEN)), RoomGroup(LOUNGE, listOf(LOUNGE)), RoomGroup(DINING, listOf(DINING))))
        assertThat(music.groups.value.map { it.coordinatorId }).containsExactly(KITCHEN, LOUNGE, DINING)
        assertThat(music.groups.value.map { it.rooms.size }).containsExactly(1, 1, 1)
    }

    @Test
    fun theSampleSpeakersPlayAPick() = runBlocking<Unit> {
        val pick = music.favourites().first()
        val party = partyOfThree()
        music.playFavourite(party, pick)
        val playing = music.groups.value.single()
        assertThat(playing.state).isEqualTo(PlayState.PLAYING)
        assertThat(music.playingNow().map { it.coordinatorId }).containsExactly(KITCHEN)
    }

    @Test
    fun theSampleLightsShowTheSceneRecalled() = runBlocking<Unit> {
        val kitchen = lights.rooms.value.first { it.id == KITCHEN_LIGHTS }
        lights.recallScene(kitchen, kitchen.scenes.first { it.id == BRIGHT })
        val after = lights.rooms.value.first { it.id == KITCHEN_LIGHTS }
        assertThat(after.activeSceneId).isEqualTo(BRIGHT)
        assertThat(after.on).isTrue()
    }

    @Test
    fun aHandChangeIsWhatTheReceiverSays() = runBlocking<Unit> {
        val party = partyOfThree()
        music.playFavourite(party, music.favourites().first())
        lights.recallScene(lights.rooms.value.first { it.id == KITCHEN_LIGHTS }, lights.rooms.value.first { it.id == KITCHEN_LIGHTS }.scenes.first { it.id == BRIGHT })

        receive { putExtra("hand", "regroup") }
        assertThat(music.groups.value.single { it.coordinatorId == KITCHEN }.rooms.map { it.id }).containsExactly(KITCHEN, LOUNGE)
        assertThat(music.groups.value.single { it.coordinatorId == DINING }.rooms).hasSize(1)

        receive { putExtra("hand", "pause") }
        assertThat(music.groups.value.single { it.coordinatorId == KITCHEN }.state).isEqualTo(PlayState.PAUSED)

        receive { putExtra("hand", "scene") }
        assertThat(lights.rooms.value.first { it.id == KITCHEN_LIGHTS }.activeSceneId).isNull()
    }

    @Test
    fun anUnknownHandChangeDoesNothing() = runBlocking<Unit> {
        partyOfThree()
        receive { putExtra("hand", "dance") }
        assertThat(music.groups.value).hasSize(1)
    }

    @Test
    fun downMakesEveryCallUnreachable() = runBlocking<Unit> {
        receive { putExtra("down", true) }
        assertThat(thrown<MusicUnreachableException> { music.checkLayout() }.problem).isEqualTo(MusicProblem.UNREACHABLE)
        thrown<MusicUnreachableException> { music.favourites() }
        assertThat(thrown<LightsUnreachableException> { lights.readAll() }.problem).isEqualTo(LightsProblem.UNREACHABLE)
        thrown<LightsUnreachableException> { lights.setOn(lights.rooms.value.first(), true) }

        receive { putExtra("down", false) }
        music.checkLayout()
        lights.readAll()
        assertThat(music.problem.value).isNull()
        assertThat(lights.problem.value).isNull()
    }

    @Test
    fun theSamplesFindNothing() = runBlocking<Unit> {
        assertThat(deps.sampleMusic().find()).isNull()
        assertThat(deps.sampleMusic().check("192.0.2.1")).isNull()
        assertThat(deps.sampleLights().find()).isNull()
        assertThat(deps.sampleLights().check("192.0.2.1")).isNull()
        assertThat(deps.sampleMusic().descriptor.userConnectable).isFalse()
        assertThat(deps.sampleLights().descriptor.userConnectable).isFalse()
    }

    @Test
    fun sampleTrueKeepsTheSavedConnectionsAndSampleFalseBringsThemBack() = runBlocking<Unit> {
        val music = MusicLink("Sonos_test", "192.0.2.10:1400")
        val bridge = LightsLink("bridge-test", "192.0.2.20", "test-key")
        deps.musicSettings().connect("music.sonos", music)
        deps.lightsSettings().connect("lights.hue", bridge)

        receive { putExtra("sample", true) }
        withTimeout(10_000) {
            deps.musicSettings().prefs.first { it.providerId == "sample.music" }
            deps.lightsSettings().prefs.first { it.providerId == "sample.lights" }
        }
        // A second sample true must not replace what was kept with the samples.
        deps.sampleHouse().use(true)
        receive { putExtra("sample", false) }
        withTimeout(10_000) {
            assertThat(deps.musicSettings().prefs.first { it.providerId == "music.sonos" }.link).isEqualTo(music)
            assertThat(deps.lightsSettings().prefs.first { it.providerId == "lights.hue" }.link).isEqualTo(bridge)
        }
    }

    private companion object {
        const val KITCHEN = "Kitchen-id"
        const val LOUNGE = "Lounge-id"
        const val DINING = "Dining-id"
        const val KITCHEN_LIGHTS = "r1"
        const val BRIGHT = "s1"
    }
}
