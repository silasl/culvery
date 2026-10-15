package uk.co.siland.culvery.provider.door_ring

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 5 §7: Ring's captured press and motion, the channel and word fallbacks, an unknown Ring alert, and Ring's ignorable notifications. */
class RingParserTest {
    @Test
    fun theCapturedPressIsARingOnItsDevice() = assertThat(RingParser.read(RingFixtures.ring)).isEqualTo(RingReading.Ring("Front Door"))

    @Test
    fun theCapturedPersonMotionIsMotionOnItsDevice() =
        assertThat(RingParser.read(RingFixtures.motion)).isEqualTo(RingReading.Motion("Front Door"))

    /** Ring posts each notice three times; the key and time stay the same and only the picture differs. */
    @Test
    fun thePictureRepostsReadTheSameAsTheFirstPost() {
        assertThat(RingParser.read(RingFixtures.ringWithPicture)).isEqualTo(RingParser.read(RingFixtures.ring))
        assertThat(RingParser.read(RingFixtures.ringWithPictureAgain)).isEqualTo(RingParser.read(RingFixtures.ring))
        assertThat(RingParser.read(RingFixtures.motionWithPicture)).isEqualTo(RingParser.read(RingFixtures.motion))
        assertThat(RingFixtures.ringWithPicture.key to RingFixtures.ringWithPicture.whenMillis)
            .isEqualTo(RingFixtures.ring.key to RingFixtures.ring.whenMillis)
    }

    @Test
    fun theGroupSummaryIsIgnored() = assertThat(RingParser.read(RingFixtures.groupSummary)).isEqualTo(RingReading.Ignore)

    @Test
    fun aMotionChannelNoticeWithUnfamiliarWordsIsMotion() =
        assertThat(RingParser.read(RingFixtures.unfamiliarMotionChannel)).isEqualTo(RingReading.Motion(""))

    @Test
    fun aDingChannelNoticeWithUnfamiliarWordsIsARing() =
        assertThat(RingParser.read(RingFixtures.unfamiliarDingChannel)).isEqualTo(RingReading.Ring(""))

    @Test
    fun theChannelOutranksTheWords() {
        val motionWordsOnDingChannel = RingFixtures.motion.copy(channelId = "ding_channel_notification1")
        assertThat(RingParser.read(motionWordsOnDingChannel)).isEqualTo(RingReading.Ring("Front Door"))
    }

    @Test
    fun anUnknownChannelWithPressWordsIsARing() =
        assertThat(RingParser.read(RingFixtures.pressWordsOnAnotherChannel)).isEqualTo(RingReading.Ring("Front Door"))

    @Test
    fun anUnknownChannelWithMotionWordsIsMotion() {
        listOf("person", "package", "vehicle", "animal").forEach { what ->
            val article = if (what == "animal") "an" else "a"
            val notice = RingFixtures.unknown.copy(text = "There is $article $what at your Front Door")
            assertThat(RingParser.read(notice)).isEqualTo(RingReading.Motion("Front Door"))
        }
        assertThat(RingParser.read(RingFixtures.unknown.copy(text = "There is motion at your Front Door")))
            .isEqualTo(RingReading.Motion("Front Door"))
    }

    /** The fallback: from Ring, not placed by channel or words, not ignorable; it still alerts as a ring. */
    @Test
    fun anUnknownChannelWithUnknownWordsIsAnUnknownAlert() =
        assertThat(RingParser.read(RingFixtures.unknown)).isEqualTo(RingReading.UnknownRingAlert("Front Door"))

    /** Ring may post a press as an ongoing, call-style alert; the words still decide. */
    @Test
    fun anOngoingNoticeWithARingsWordsIsARing() = assertThat(RingParser.read(RingFixtures.ongoingRing)).isEqualTo(RingReading.Ring("Front Door"))

    @Test
    fun anOngoingNoticeWithNoRingsWordsIsIgnored() = assertThat(RingParser.read(RingFixtures.ongoingStatus)).isEqualTo(RingReading.Ignore)

    @Test
    fun liveViewIsIgnoredByItsWords() = assertThat(RingParser.read(RingFixtures.liveView)).isEqualTo(RingReading.Ignore)

    @Test
    fun aBundleSummaryIsIgnoredEvenWithARingsWords() = assertThat(RingParser.read(RingFixtures.bundle)).isEqualTo(RingReading.Ignore)

    @Test
    fun aPromotionIsIgnoredEvenWithARingsWords() = assertThat(RingParser.read(RingFixtures.promo)).isEqualTo(RingReading.Ignore)

    @Test
    fun anAppNoticeIsIgnoredEvenWithARingsWords() = assertThat(RingParser.read(RingFixtures.appUpdate)).isEqualTo(RingReading.Ignore)

    /** When the title is the app's own name, the device is what follows "at your". */
    @Test
    fun theDeviceComesFromTheWordsWhenTheTitleIsRings() {
        val notice = RingFixtures.unknown.copy(title = "Ring", text = "There is motion at your Back Garden.")
        assertThat(RingParser.read(notice)).isEqualTo(RingReading.Motion("Back Garden"))
    }

    @Test
    fun theDeviceComesFromTheTextAfterTheEmojiWhenThereIsNoTitle() {
        val notice = RingFixtures.ring.copy(text = "🔔 Someone is at your Back Garden")
        assertThat(RingParser.read(notice)).isEqualTo(RingReading.Ring("Back Garden"))
    }

    @Test
    fun aNoticeWithNoWordsIsAnUnknownAlertWithNoDevice() {
        val notice = RingFixtures.unknown.copy(title = null, text = null)
        assertThat(RingParser.read(notice)).isEqualTo(RingReading.UnknownRingAlert(""))
    }

    /** The debug fake ring (adb shell, channel "shell_cmd", a title) is still read by its words (5 §7). */
    @Test
    fun aFakeRingFromTheShellIsReadByItsWords() {
        val fake = RingFixtures.unknown.copy(
            packageName = SHELL_PACKAGE, channelId = "shell_cmd", title = "Front Door", text = "There’s someone at your Front Door",
        )
        assertThat(RingParser.read(fake)).isEqualTo(RingReading.Ring("Front Door"))
        assertThat(RingParser.read(fake.copy(text = "There is motion at your Front Door"))).isEqualTo(RingReading.Motion("Front Door"))
    }

    /** 5 §6: what a log may say. */
    @Test
    fun eachOutcomeHasWordsWithNoDevice() {
        assertThat(
            listOf(RingReading.Ring("Front Door"), RingReading.Motion("Front Door"), RingReading.UnknownRingAlert("Front Door"), RingReading.Ignore)
                .map { it.outcome },
        ).containsExactly("a ring", "motion", "an unknown alert", "ignored").inOrder()
    }
}
