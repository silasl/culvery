package uk.co.siland.culvery.provider.door_ring

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 5 §7: a ring, motion, a ring then its snapshot, an unknown Ring alert, and Ring's ignorable notifications. */
class RingParserTest {
    @Test
    fun aRingIsARingOnItsDevice() = assertThat(RingParser.read(RingFixtures.ring)).isEqualTo(RingReading.Ring("Front Door"))

    /** The snapshot re-post is the same notification; a second press is the same key at another time. */
    @Test
    fun theSnapshotRepostKeepsKeyAndTimeAndASecondPressKeepsOnlyTheKey() {
        assertThat(RingParser.read(RingFixtures.ringWithSnapshot)).isEqualTo(RingReading.Ring("Front Door"))
        assertThat(RingFixtures.ringWithSnapshot.key to RingFixtures.ringWithSnapshot.whenMillis)
            .isEqualTo(RingFixtures.ring.key to RingFixtures.ring.whenMillis)
        assertThat(RingFixtures.ringAgain.key).isEqualTo(RingFixtures.ring.key)
        assertThat(RingFixtures.ringAgain.whenMillis).isNotEqualTo(RingFixtures.ring.whenMillis)
    }

    @Test
    fun motionIsMotion() = assertThat(RingParser.read(RingFixtures.motion)).isEqualTo(RingReading.Motion("Front Door"))

    /** The fallback: from Ring, not placed, not ignorable; it still alerts as a ring. */
    @Test
    fun anythingElseFromRingIsAnUnknownAlert() =
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
        val notice = RingFixtures.motion.copy(title = "Ring", text = "There is motion at your Back Garden.")
        assertThat(RingParser.read(notice)).isEqualTo(RingReading.Motion("Back Garden"))
    }

    @Test
    fun aNoticeWithNoWordsIsAnUnknownAlertWithNoDevice() {
        val notice = RingFixtures.ring.copy(title = null, text = null)
        assertThat(RingParser.read(notice)).isEqualTo(RingReading.UnknownRingAlert(""))
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
