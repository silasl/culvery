package uk.co.siland.culvery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import uk.co.siland.culvery.capability.lights.LightsSettings
import uk.co.siland.culvery.capability.music.MusicSettings
import uk.co.siland.culvery.sample.SampleHouse
import uk.co.siland.culvery.sample.SampleLightsProvider
import uk.co.siland.culvery.sample.SampleMusicProvider

/**
 * Debug builds only: the sample speakers and lights for walking Party mode through (13 §8). Each extra is optional:
 * `adb shell am broadcast -n uk.co.siland.culvery/.DebugSampleHouseReceiver --ez sample true` connects the samples and keeps
 * the saved connections, `--ez sample false` brings those back, `--es hand regroup|pause|scene` changes the samples by hand
 * as the family would, and `--ez down true` makes every sample call fail as unreachable until `--ez down false`.
 */
class DebugSampleHouseReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun sampleHouse(): SampleHouse

        fun sampleMusic(): SampleMusicProvider

        fun sampleLights(): SampleLightsProvider

        fun musicSettings(): MusicSettings

        fun lightsSettings(): LightsSettings
    }

    override fun onReceive(context: Context, intent: Intent) {
        val house = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).sampleHouse()
        intent.getStringExtra(EXTRA_HAND)?.let(house::hand)
        if (intent.hasExtra(EXTRA_DOWN)) house.down(intent.getBooleanExtra(EXTRA_DOWN, false))
        if (intent.hasExtra(EXTRA_SAMPLE)) {
            val pending = goAsync()
            house.useSoon(intent.getBooleanExtra(EXTRA_SAMPLE, false)) { pending?.finish() }
        }
    }

    companion object {
        const val EXTRA_SAMPLE = "sample"
        const val EXTRA_HAND = "hand"
        const val EXTRA_DOWN = "down"
    }
}
