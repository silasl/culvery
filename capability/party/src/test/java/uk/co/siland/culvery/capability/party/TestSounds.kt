package uk.co.siland.culvery.capability.party

import java.io.File
import java.util.Optional
import kotlinx.coroutines.flow.MutableStateFlow
import uk.co.siland.culvery.core.house.DoorQuiet
import uk.co.siland.culvery.core.house.HouseMode
import uk.co.siland.culvery.core.sound.BuiltInSound
import uk.co.siland.culvery.core.sound.Sound
import uk.co.siland.culvery.core.sound.SoundSource
import uk.co.siland.culvery.core.sound.id

internal fun builtIn(sound: BuiltInSound) = Sound(sound.id, sound.label, SoundSource.BuiltIn(sound), sound.theme)

internal val TROMBONE = builtIn(BuiltInSound.SAD_TROMBONE)
internal val ORGAN = builtIn(BuiltInSound.ORGAN)
internal val BIRTHDAY_SONG = builtIn(BuiltInSound.HAPPY_BIRTHDAY)
internal val HONK = Sound("file:Test Honk.wav", "Test Honk", SoundSource.OnTablet(File("Test Honk.wav")), null)

/** What the library holds in these tests: every built-in sound and Test Honk. */
internal val LIBRARY = BuiltInSound.entries.map(::builtIn) + HONK

internal fun inLibrary(id: String?): Sound? = LIBRARY.firstOrNull { it.id == id }

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
internal class GateRig(val door: FakeDoor? = FakeDoor()) {
    val house = FakeHoliday()
    var night: Boolean? = false
    val gate = PadGate(Optional.ofNullable(door), house) { night }
}

/** Stands in for the sound player: records each play and stop, and plays while [playing] says. */
internal class FakePlayer {
    val playing = MutableStateFlow<Int?>(null)
    val plays = mutableListOf<Pair<Int, Float>>()
    var stops = 0
    var starts = true

    /** Runs as a play begins, as a ring arriving while the sound opens would. */
    var whilePlaying: () -> Unit = {}

    fun play(pad: Int, sound: Sound, volume: Float): Boolean {
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
