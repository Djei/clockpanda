package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners
import djei.clockpanda.model.User
import djei.clockpanda.scheduling.model.CalendarEventType

class OptimizationConstraintsProvider : ConstraintProvider {
    // Constraint priority design
    // 1. Focus time events should not overlap with other events: -1000 point for one violation that should completely overshadow other constraints
    // 2. TODO Focus time should respect user working hours
    // 3. Focus time total amount not meeting the target: -1 point per missing 15 minutes - assuming 60 hours target -> -240 points maximum score
    override fun defineConstraints(constraintFactory: ConstraintFactory): Array<Constraint> {
        return arrayOf(
            focusTimeEventsShouldNotOverlapWithOtherEvents(constraintFactory),
            focusTimeTotalAmountNotMeetingTheTarget(constraintFactory)
        )
    }

    fun focusTimeEventsShouldNotOverlapWithOtherEvents(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .join(
                Event::class.java,
                Joiners.overlapping(Event::getStartTime, Event::getEndTime),
                // ensures the event pair is unique, and we do not join an event with itself
                Joiners.lessThan(Event::id)
            )
            // Penalize by 1000 points for each overlapping pair
            .penalize(HardSoftScore.ONE_HARD) { _, _ -> 1000 }
            .asConstraint("Focus time events should not overlap with other events")
    }

    fun focusTimeTotalAmountNotMeetingTheTarget(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .groupBy(Event::owner, ConstraintCollectors.sum(Event::getDurationInMinutes))
            .join(
                User::class.java,
                Joiners.equal({ owner, _ -> owner }, User::email)
            )
            .penalize(HardSoftScore.ONE_HARD) { _, amountOfFocusTimeReserved, user ->
                val userPreferences = user.preferences
                val targetFocusTimeHoursPerWeek = userPreferences?.targetFocusTimeHoursPerWeek ?: 0
                val missing = maxOf(targetFocusTimeHoursPerWeek * 60 - amountOfFocusTimeReserved, 0)
                missing / 15
            }
            .asConstraint("Focus time total amount not meeting the target")
    }
}
