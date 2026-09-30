package uk.co.siland.culvery.core.setup

/** A step as the wizard reads it at this moment. */
internal data class StepStatus(val shown: Boolean, val done: Boolean, val canGoOn: Boolean)

/** The forward button: the step's own label, enabled or not, or "Skip for now". */
internal sealed interface Forward {
    data class Next(val label: String, val enabled: Boolean) : Forward

    data object Skip : Forward
}

/** 4a design §3.3 as plain functions of the steps' statuses, in order. */
internal object WizardRules {
    /** The first shown step not done; with every shown step done, the last shown one. */
    fun resumeAt(statuses: List<StepStatus>): Int =
        statuses.indexOfFirst { it.shown && !it.done }.takeIf { it >= 0 } ?: statuses.indexOfLast { it.shown }.coerceAtLeast(0)

    fun nextShown(from: Int, statuses: List<StepStatus>): Int? = (from + 1 until statuses.size).firstOrNull { statuses[it].shown }

    fun previousShown(from: Int, statuses: List<StepStatus>): Int? = (from - 1 downTo 0).firstOrNull { statuses[it].shown }

    fun dotCount(statuses: List<StepStatus>): Int = statuses.count { it.shown }

    /** [current]'s place among the shown steps. */
    fun dotIndex(current: Int, statuses: List<StepStatus>): Int = statuses.take(current).count { it.shown }

    /** A skippable step that isn't done offers Skip for now; otherwise Next, enabled once the step can go on. */
    fun forward(skippable: Boolean, label: String, status: StepStatus): Forward =
        if (skippable && !status.done) Forward.Skip else Forward.Next(label, status.canGoOn || skippable)
}
