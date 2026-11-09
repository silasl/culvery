package uk.co.siland.culvery.capability.party

import java.io.File
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.sound.BuiltInSound
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.SoundSource
import uk.co.siland.culvery.core.sound.asSound
import uk.co.siland.culvery.core.sound.soundFor

/** The clip the family keeps on the tablet; pad 1 defaults to it. */
internal val TROMBONE = Sound("file:Sad trombone.wav", "Sad trombone", SoundSource.OnTablet(File("Sad trombone.wav")), null)
internal val ORGAN = BuiltInSound.ORGAN.asSound()
internal val BIRTHDAY_SONG = BuiltInSound.HAPPY_BIRTHDAY.asSound()
internal val HONK = Sound("file:Test Honk.wav", "Test Honk", SoundSource.OnTablet(File("Test Honk.wav")), null)

/** What the library holds in these tests: every built-in sound, Sad trombone and Test Honk. */
internal val LIBRARY = BuiltInSound.entries.map { it.asSound() } + TROMBONE + HONK

internal fun inLibrary(id: String?): Sound? = LIBRARY.soundFor(id)

internal class FakeDoor : DoorQuiet {
    val ring = MutableStateFlow(false)
    var quiet = false
    override val ringShowing = ring
    override suspend fun quietNow() = quiet
    override val volume = 0.6f
}

internal class FakeHoliday : HouseMode {
    override val holiday = MutableStateFlow<Boolean?>(false)
}

/** A gate over [door] (null: no door), [house], and a night that [night] says. */
internal class GateRig(val door: FakeDoor? = FakeDoor(), val clock: WallClock = WallClock { 0L }) {
    val house = FakeHoliday()
    var night: Boolean? = false
    val gate = PadGate(Optional.ofNullable(door), house, { night }, clock)
}

/** Stands in for the sound player: records each play and stop, and plays while [playing] says. */
internal class FakePlayer {
    val playing = MutableStateFlow<Int?>(null)
    val plays: MutableList<Pair<Int, Float>> = CopyOnWriteArrayList()
    @Volatile var stops = 0
    var starts = true

    /** Runs as a play begins, as a ring arriving while the sound opens would. */
    var whilePlaying: suspend () -> Unit = {}

    suspend fun play(pad: Int, sound: Sound, volume: Float): Boolean {
        plays += pad to volume
        whilePlaying()
        if (starts) playing.value = pad
        return starts
    }

    fun stop() {
        stops++
        playing.value = null
    }
}
