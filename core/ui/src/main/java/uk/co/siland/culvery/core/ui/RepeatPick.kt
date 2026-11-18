package uk.co.siland.culvery.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import uk.co.siland.culvery.core.repeat.DEFAULT_TIMES
import uk.co.siland.culvery.core.repeat.MAX_TIMES
import uk.co.siland.culvery.core.repeat.MIN_TIMES
import uk.co.siland.culvery.core.repeat.RepeatChoice
import uk.co.siland.culvery.core.repeat.RepeatEnd
import uk.co.siland.culvery.core.repeat.SHORT_DAY
import uk.co.siland.culvery.core.repeat.choiceOf
import uk.co.siland.culvery.core.repeat.code
import uk.co.siland.culvery.core.repeat.daysOf
import uk.co.siland.culvery.core.repeat.lastOccurrence
import uk.co.siland.culvery.core.repeat.nthOf
import uk.co.siland.culvery.core.repeat.nthWeekday
import uk.co.siland.culvery.core.repeat.recurrenceRule
import uk.co.siland.culvery.core.repeat.ruleParts

/** What the repeat picker's taps do: [RepeatPick] does them itself; a form that guards its repeat passes its own. */
interface RepeatPickActions {
    fun choose(choice: RepeatChoice)

    fun keep()

    fun toggleDay(day: DayOfWeek, own: DayOfWeek)

    fun stopsAfter()

    fun keepsGoing()

    fun fewerTimes()

    fun moreTimes()
}

/** The repeat picker's state. [kept] is a rule shown as its own row because no choice writes it; it stays chosen until a choice is tapped. */
class RepeatPick(
    choice: RepeatChoice = RepeatChoice.Never,
    end: RepeatEnd = RepeatEnd.Never,
    extraDays: Set<DayOfWeek> = emptySet(),
    val kept: String? = null,
) : RepeatPickActions {
    var choice: RepeatChoice by mutableStateOf(choice)
        private set

    var end: RepeatEnd by mutableStateOf(end)
        private set

    /** Days beyond the chosen day's own. */
    var extraDays: Set<DayOfWeek> by mutableStateOf(extraDays)
        private set

    /** True while [kept] is the chosen row. */
    var keeping: Boolean by mutableStateOf(kept != null)
        private set

    /** Whether anything has been tapped since the pick was made. */
    var touched: Boolean by mutableStateOf(false)
        private set

    // The rule [read] was given and the day it was read against: written back as it came until something is tapped.
    private var readFrom: Pair<String, LocalDate>? = null

    val repeats: Boolean get() = keeping || choice != RepeatChoice.Never

    private val weekly: Boolean get() = choice == RepeatChoice.Weekly || choice == RepeatChoice.Fortnightly

    private val showsEnds: Boolean get() = !keeping && choice != RepeatChoice.Never

    /** Leaves [kept]; Never clears the end and the days. */
    override fun choose(choice: RepeatChoice) {
        touched = true
        keeping = false
        this.choice = choice
        if (choice == RepeatChoice.Never) {
            end = RepeatEnd.Never
            extraDays = emptySet()
        }
    }

    override fun keep() {
        if (kept == null) return
        touched = true
        keeping = true
    }

    /** [own] can't be toggled. */
    override fun toggleDay(day: DayOfWeek, own: DayOfWeek) {
        if (day == own) return
        touched = true
        extraDays = if (day in extraDays) extraDays - day else extraDays + day
    }

    /** Choosing it while it is chosen keeps the count. */
    override fun stopsAfter() {
        if (end !is RepeatEnd.After) chooseEnd(RepeatEnd.After(DEFAULT_TIMES))
    }

    override fun keepsGoing() = chooseEnd(RepeatEnd.Never)

    override fun fewerTimes() = stepTimes(-1)

    override fun moreTimes() = stepTimes(1)

    private fun chooseEnd(end: RepeatEnd) {
        if (!showsEnds) return
        touched = true
        this.end = end
    }

    private fun stepTimes(by: Int) {
        val after = end as? RepeatEnd.After ?: return
        chooseEnd(RepeatEnd.After((after.times + by).coerceIn(MIN_TIMES, MAX_TIMES)))
    }

    /** Own and extra for Weekly and Fortnightly, else empty. */
    fun daysOn(day: LocalDate): Set<DayOfWeek> = if (weekly) extraDays + day.dayOfWeek else emptySet()

    /** [kept] while keeping, the rule [read] was given while untouched on its day, else the rule the choice writes. */
    fun rule(day: LocalDate, nth: Int = nthWeekday(day)): String? {
        if (keeping) return kept
        readFrom?.let { (rule, first) -> if (!touched && first == day) return rule }
        return recurrenceRule(choice, end, day, nth, daysOn(day))
    }

    /** The stepper's centre while it stops after a count: "12 times · last on Sat 12 Dec". */
    fun stepperLabel(day: LocalDate, nth: Int = nthWeekday(day)): String? {
        val after = end as? RepeatEnd.After ?: return null
        val last = lastOccurrence(choice, day, after.times, nth, daysOn(day)) ?: return null
        return "${plural(after.times.toLong(), "time")} · last on ${last.format(SHORT_DAY)}"
    }

    override fun toString(): String = "RepeatPick"

    companion object {
        /** [rule] read against [first]: a choice with its days and a count of 2 to 52 when the picker can show all of it, else kept. Null rule is Never. */
        fun read(rule: String?, first: LocalDate): RepeatPick {
            if (rule == null) return RepeatPick()
            val parts = ruleParts(rule) ?: return RepeatPick(kept = rule)
            val choice = choiceOf(rule, first)
            val end = when (val count = parts["COUNT"]) {
                null -> RepeatEnd.Never
                else -> count.toIntOrNull()?.takeIf { it in MIN_TIMES..MAX_TIMES }?.let(RepeatEnd::After)
            }
            val shown = choice != null && end != null && "UNTIL" !in parts &&
                parts["WKST"].let { it == null || it == DayOfWeek.MONDAY.code() } &&
                nthOf(rule, first) == nthWeekday(first)
            if (!shown) return RepeatPick(kept = rule)
            return RepeatPick(checkNotNull(choice), checkNotNull(end), daysOf(rule, first) - first.dayOfWeek).also {
                it.readFrom = rule to first
            }
        }
    }
}
