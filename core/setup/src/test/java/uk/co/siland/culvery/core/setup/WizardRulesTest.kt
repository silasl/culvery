package uk.co.siland.culvery.core.setup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WizardRulesTest {
    private fun status(shown: Boolean = true, done: Boolean = false, canGoOn: Boolean = done, skippable: Boolean = false) =
        StepStatus(shown, done, canGoOn, skippable)

    @Test
    fun aFreshWizardOpensAtTheFirstStep() {
        assertThat(WizardRules.resumeAt(listOf(status(), status(), status()))).isEqualTo(0)
    }

    @Test
    fun itResumesAtTheFirstShownStepThatIsNotDone() {
        assertThat(WizardRules.resumeAt(listOf(status(done = true), status(done = true), status(), status()))).isEqualTo(2)
    }

    @Test
    fun aSkippedStepIsWhereItResumes() {
        // Welcome passed, Home location skipped (not done), You done.
        assertThat(WizardRules.resumeAt(listOf(status(done = true), status(), status(done = true), status()))).isEqualTo(1)
    }

    @Test
    fun aStepSkippedBeforeIsNotWhereItResumes() {
        // Welcome passed, Home location skipped, You done, Household skipped: the furthest passed is Household.
        val statuses = listOf(status(done = true), status(skippable = true), status(done = true), status(skippable = true), status(canGoOn = true))
        assertThat(WizardRules.resumeAt(statuses, passedThrough = 3)).isEqualTo(4)
    }

    @Test
    fun aSkippableStepAfterTheFurthestPassedIsWhereItResumes() {
        assertThat(WizardRules.resumeAt(listOf(status(skippable = true), status(skippable = true), status()), passedThrough = 0)).isEqualTo(1)
    }

    @Test
    fun aPassedStepWhoseRequiredInputIsMissingIsWhereItResumes() {
        val statuses = listOf(status(done = true), status(), status(skippable = true), status())
        assertThat(WizardRules.resumeAt(statuses, passedThrough = 2)).isEqualTo(1)
    }

    @Test
    fun itResumesAtReviewOnceConnectIsDone() {
        // Welcome, …, Connect done; Review shown and not yet done; Done.
        val statuses = listOf(status(done = true), status(done = true), status(done = true), status(), status(canGoOn = true))
        assertThat(WizardRules.resumeAt(statuses)).isEqualTo(3)
    }

    @Test
    fun aHiddenStepIsNeverWhereItResumes() {
        assertThat(WizardRules.resumeAt(listOf(status(done = true), status(shown = false), status()))).isEqualTo(2)
    }

    @Test
    fun withEveryShownStepDoneItOpensAtTheLastShownOne() {
        assertThat(WizardRules.resumeAt(listOf(status(done = true), status(done = true), status(shown = false)))).isEqualTo(1)
    }

    @Test
    fun nextAndBackSkipHiddenSteps() {
        val statuses = listOf(status(), status(shown = false), status(), status(shown = false))
        assertThat(WizardRules.nextShown(0, statuses)).isEqualTo(2)
        assertThat(WizardRules.nextShown(2, statuses)).isNull()
        assertThat(WizardRules.previousShown(2, statuses)).isEqualTo(0)
        assertThat(WizardRules.previousShown(0, statuses)).isNull()
    }

    @Test
    fun theDotsCountOnlyShownSteps() {
        val statuses = listOf(status(), status(shown = false), status(), status())
        assertThat(WizardRules.dotCount(statuses)).isEqualTo(3)
        assertThat(WizardRules.dotIndex(2, statuses)).isEqualTo(1)
    }

    @Test
    fun aSkippableStepNotDoneOffersSkipForNow() {
        assertThat(WizardRules.forward(skippable = true, "Next", status())).isEqualTo(Forward.Skip)
        assertThat(WizardRules.forward(skippable = true, "Next", status(done = true))).isEqualTo(Forward.Next("Next", enabled = true))
    }

    @Test
    fun nextIsEnabledWhenTheStepCanGoOn() {
        assertThat(WizardRules.forward(skippable = false, "Next", status())).isEqualTo(Forward.Next("Next", enabled = false))
        assertThat(WizardRules.forward(skippable = false, "Start", status(canGoOn = true))).isEqualTo(Forward.Next("Start", enabled = true))
    }
}
