package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners
import djei.clockpanda.model.User
import djei.clockpanda.scheduling.model.CalendarEventType
import kotlinx.datetime.toLocalDateTime

class OptimizationConstraintsProvider : ConstraintProvider {
    // Constraint priority design
    // 1. Focus time events should not overlap with other events: -1000 point for one violation
    // 2. Focus time should respect user working hours: -1000 point for one violation
    // 3. Focus time total amount not meeting the target: -1 point per missing 15 minutes - assuming 60 hours target -> -240 points maximum score
    override fun defineConstraints(constraintFactory: ConstraintFactory): Array<Constraint> {
        return arrayOf(
            focusTimeEventsShouldNotOverlapWithOtherEvents(constraintFactory),
            focusTimeEventsShouldNotBeOutsideOfWorkingHours(constraintFactory),
            focusTimeTotalAmountNotMeetingTheTarget(constraintFactory)
        )
    }

    fun focusTimeEventsShouldNotOverlapWithOtherEvents(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .filter { e -> e.getDurationInMinutes() > 0 }
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

    fun focusTimeEventsShouldNotBeOutsideOfWorkingHours(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                User::class.java,
                Joiners.equal(Event::owner, User::email)
            )
            .filter { event, user ->
                val userPreferences = user.preferences
                if (userPreferences == null) {
                    false
                } else {
                    val userPreferredTimeZone = userPreferences.preferredTimeZone
                    val eventLocalStartTime = event.getStartTime().toLocalDateTime(userPreferredTimeZone)
                    val eventLocalEndTime = event.getEndTime().toLocalDateTime(userPreferredTimeZone)
                    // We only support specifying one working hour block per day for now
                    val workingHoursForEvent = userPreferences.workingHours[eventLocalStartTime.dayOfWeek]?.get(0)
                    if (eventLocalStartTime.date != eventLocalEndTime.date) {
                        true
                    } else if (workingHoursForEvent == null) {
                        false
                    } else {
                        eventLocalStartTime.time < workingHoursForEvent.start || eventLocalEndTime.time > workingHoursForEvent.end
                    }
                }
            }.penalize(HardSoftScore.ONE_HARD) { _, _ -> 1000 }
            .asConstraint("Focus time events should not be outside working hours")
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
