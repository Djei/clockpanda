package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners
import djei.clockpanda.model.User
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import kotlinx.datetime.atDate
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.DurationUnit

class OptimizationConstraintsProvider : ConstraintProvider {
    // Constraint priority design
    // 1. Hard constraint: Focus time events should NOT overlap with other events
    // 2. Hard constraint: Focus time should NOT be outside user working hours
    // 3. Hard constraint: Focus time should start and end on the same day
    // 4. Soft constraint: Focus time total amount should reach the desired target
    override fun defineConstraints(constraintFactory: ConstraintFactory): Array<Constraint> {
        return arrayOf(
            focusTimeEventsShouldNotOverlapWithOtherEvents(constraintFactory),
            focusTimeEventsShouldNotBeOutsideOfWorkingHours(constraintFactory),
            focusTimeShouldStartAndEndOnTheSameDay(constraintFactory),
            focusTimeTotalAmountNotMeetingTheTarget(constraintFactory)
        )
    }

    fun focusTimeEventsShouldNotOverlapWithOtherEvents(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                Event::class.java,
                Joiners.overlapping(Event::getStartTime, Event::getEndTime),
                // ensures the event pair is unique, and we do not join an event with itself
                Joiners.lessThan(Event::id),
                // Ignore external event overlapping between themselves since those are existing schedule overlaps
                Joiners.filtering { event1, event2 ->
                    !(event1.type == CalendarEventType.EXTERNAL_EVENT && event2.type == CalendarEventType.EXTERNAL_EVENT)
                }
            )
            // Penalize proportionally to the amount of overlap
            .penalize(HardSoftScore.ONE_HARD) { event1, event2 ->
                event1.computeOverlapInMinutes(event2)
            }
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
            .map { event, user ->
                val userPreferences = user.preferences!!
                val userPreferredTimeZone = userPreferences.preferredTimeZone
                val eventLocalStartTime = event.getStartTime().toLocalDateTime(userPreferredTimeZone)
                // We only support specifying one working hour block per day for now
                val workingHoursForEvent = userPreferences
                    .workingHours[eventLocalStartTime.dayOfWeek]
                    ?.get(0)
                    ?.let {
                        TimeSpan(
                            start = it.start.atDate(eventLocalStartTime.date).toInstant(userPreferredTimeZone),
                            end = it.end.atDate(eventLocalStartTime.date).toInstant(userPreferredTimeZone)
                        )
                    }
                if (workingHoursForEvent == null) {
                    0
                } else {
                    var amountOutsideWorkingHours = 0
                    amountOutsideWorkingHours += if (event.getStartTime() < workingHoursForEvent.start) {
                        (workingHoursForEvent.start - event.getStartTime()).toInt(DurationUnit.MINUTES)
                    } else {
                        0
                    }
                    amountOutsideWorkingHours += if (event.getEndTime() > workingHoursForEvent.end) {
                        (event.getEndTime() - workingHoursForEvent.end).toInt(DurationUnit.MINUTES)
                    } else {
                        0
                    }
                    amountOutsideWorkingHours
                }
            }.penalize(HardSoftScore.ONE_HARD) { amountOutsideWorkingHours -> amountOutsideWorkingHours }
            .asConstraint("Focus time events should not be outside working hours")
    }

    fun focusTimeShouldStartAndEndOnTheSameDay(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                User::class.java,
                Joiners.equal(Event::owner, User::email)
            )
            .filter { event, user ->
                val userPreferredTimeZone = user.preferences!!.preferredTimeZone
                event.getStartTime().toLocalDateTime(userPreferredTimeZone).date !=
                    event.getEndTime().toLocalDateTime(userPreferredTimeZone).date
            }
            .penalize(HardSoftScore.ONE_HARD) { _, _ -> 1 }
            .asConstraint("Focus time should start and end on the same day")
    }

    fun focusTimeTotalAmountNotMeetingTheTarget(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .groupBy(Event::owner, ConstraintCollectors.sum(Event::getDurationInMinutes))
            .join(
                User::class.java,
                Joiners.equal({ owner, _ -> owner }, User::email)
            )
            .penalize(HardSoftScore.ONE_SOFT) { _, amountOfFocusTimeReserved, user ->
                val userPreferences = user.preferences
                val targetFocusTimeHoursPerWeek = userPreferences?.targetFocusTimeHoursPerWeek ?: 0
                val missing = maxOf(targetFocusTimeHoursPerWeek * 60 * 2 - amountOfFocusTimeReserved, 0)
                missing / 15
            }
            .asConstraint("Focus time total amount not meeting the target")
    }
}
