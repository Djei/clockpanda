package djei.clockpanda.scheduling.optimization.constraint

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners
import djei.clockpanda.model.User
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.scheduling.optimization.model.Event
import djei.clockpanda.scheduling.optimization.model.OptimizationProblem
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

class OptimizationConstraintsProvider : ConstraintProvider {
    override fun defineConstraints(constraintFactory: ConstraintFactory): Array<Constraint> {
        return arrayOf(
            // 1. Hard constraint: Focus time events should NOT overlap with other events - penalty proportional to overlap
            focusTimeEventsShouldNotOverlapWithOtherEvents(constraintFactory),
            // 2. Hard constraint: Focus time should NOT be outside user working hours - penalty proportional to amount outside
            focusTimeEventsShouldNotBeOutsideOfWorkingHours(constraintFactory),
            // 3. Hard constraint: Focus time should start and end on the same day - fixed penalty per event
            focusTimeShouldStartAndEndOnTheSameDay(constraintFactory),
            // 4. Medium constraint: Focus time total amount should reach the desired target - penalty proportional to amount missing
            // Implemented as 2 constraints because empty weekly buckets are completely removed by first constraint
            // See https://stackoverflow.com/questions/67274703/optaplanner-constraint-streams-other-join-types-than-inner-join
            // This forces us to implement a separate `focusTimeTotalAmountIsZeroInAWeekForGivenUser` constraint for empty buckets
            focusTimeTotalAmountPartiallyMeetingUserWeeklyTarget(constraintFactory),
            focusTimeTotalAmountIsZeroInAWeekForGivenUser(constraintFactory),
            // 5. Medium constraint: An existing focus time total amount should only be moved if it gives an extra 30 minutes of focus time - fixed penalty of 30 per event moved
            existingFocusTimeShouldOnlyBeMovedIfTheyGiveMoreFocusTime(constraintFactory),
            // 6. Soft constraint: Focus time events should be within preferred focus time range - penalty proportional to amount outside
            focusTimeShouldBeWithinPreferredFocusTimeRange(constraintFactory),
            // 7. Soft constraint: Focus time should be scheduled on the hour or half hour - fixed penalty per event
            focusTimesShouldBeScheduledOnTheHourOrHalfHour(constraintFactory)
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
            .penalize(HardMediumSoftScore.ONE_HARD) { event1, event2 ->
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
                val workingHoursForEvent = userPreferences
                    .workingHours[eventLocalStartTime.dayOfWeek]
                    ?.get(0) // We only support specifying one working hour block per day for now
                    ?.let {
                        TimeSpan(
                            start = it.start.atDate(eventLocalStartTime.date).toInstant(userPreferredTimeZone),
                            end = it.end.atDate(eventLocalStartTime.date).toInstant(userPreferredTimeZone)
                        )
                    }
                if (workingHoursForEvent == null) {
                    0
                } else {
                    event.computeOutsideRangeInMinutes(workingHoursForEvent)
                }
            }
            .penalize(HardMediumSoftScore.ONE_HARD) { amountOutsideWorkingHours -> amountOutsideWorkingHours }
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
            .penalize(HardMediumSoftScore.ONE_HARD) { _, _ -> 1 }
            .asConstraint("Focus time should start and end on the same day")
    }

    fun focusTimeTotalAmountPartiallyMeetingUserWeeklyTarget(factory: ConstraintFactory): Constraint {
        return factory.forEach(User::class.java)
            .join(OptimizationProblem.OptimizationProblemParametrization::class.java)
            // Create weekly buckets for each user over the problem optimization range
            .flattenLast { p ->
                val split = p.optimizationRange.start.plus(7 * 24, DateTimeUnit.HOUR)
                listOf(
                    TimeSpan(p.optimizationRange.start, split),
                    TimeSpan(split, p.optimizationRange.end)
                )
            }
            // Join user buckets with all events that they contain
            .join(
                Event::class.java,
                Joiners.equal({ u, _ -> u.email }, Event::owner),
                Joiners.overlapping({ _, b -> b.start }, { _, b -> b.end }, Event::getStartTime, Event::getEndTime),
                Joiners.filtering { _, _, e -> e.type == CalendarEventType.FOCUS_TIME }
            )
            // Group events in the buckets
            .groupBy(
                { u, _, _ -> u.email to u.preferences!!.targetFocusTimeHoursPerWeek },
                { _, b, _ -> b.start.toEpochMilliseconds() },
                ConstraintCollectors.sum { _, _, e -> e.getDurationInMinutes() }
            )
            .penalize(HardMediumSoftScore.ONE_MEDIUM) { userParameter, _, amountOfFocusTimeReserved ->
                val missingInMinutes = abs(userParameter.second * 60 - amountOfFocusTimeReserved)
                missingInMinutes
            }
            .asConstraint("Focus time total amount not meeting user weekly target")
    }

    fun focusTimeTotalAmountIsZeroInAWeekForGivenUser(factory: ConstraintFactory): Constraint {
        return factory.forEach(User::class.java)
            .join(OptimizationProblem.OptimizationProblemParametrization::class.java)
            // Create weekly buckets for each user over the problem optimization range
            .flattenLast { p ->
                val split = p.optimizationRange.start.plus(7 * 24, DateTimeUnit.HOUR)
                listOf(
                    TimeSpan(p.optimizationRange.start, split),
                    TimeSpan(split, p.optimizationRange.end)
                )
            }
            .ifNotExists(
                Event::class.java,
                Joiners.equal({ u, _ -> u.email }, Event::owner),
                Joiners.overlapping({ _, b -> b.start }, { _, b -> b.end }, Event::getStartTime, Event::getEndTime),
                Joiners.filtering { _, _, e -> e.type == CalendarEventType.FOCUS_TIME }
            )
            .penalize(HardMediumSoftScore.ONE_MEDIUM) { user, _ ->
                val missingInMinutes = user.preferences!!.targetFocusTimeHoursPerWeek * 60
                missingInMinutes
            }
            .asConstraint("Focus time total amount is zero in a week for a given user")
    }

    fun existingFocusTimeShouldOnlyBeMovedIfTheyGiveMoreFocusTime(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .filter { e -> e.originalCalendarEvent != null }
            .filter { e -> e.hasChangedFromOriginal(TimeZone.UTC) }
            .penalize(HardMediumSoftScore.ONE_MEDIUM) { 30 }
            .asConstraint("Existing focus time events should only be moved if they give more focus time")
    }

    fun focusTimeShouldBeWithinPreferredFocusTimeRange(factory: ConstraintFactory): Constraint {
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
                val preferredFocusTimeRange = userPreferences.preferredFocusTimeRange.let {
                    TimeSpan(
                        start = it.start.atDate(eventLocalStartTime.date).toInstant(userPreferredTimeZone),
                        end = it.end.atDate(eventLocalStartTime.date).toInstant(userPreferredTimeZone)
                    )
                }
                event.computeOutsideRangeInMinutes(preferredFocusTimeRange)
            }
            .penalize(HardMediumSoftScore.ONE_SOFT) { amountOutsidePreferredRange -> amountOutsidePreferredRange }
            .asConstraint("Focus time events should be within preferred focus time range")
    }

    fun focusTimesShouldBeScheduledOnTheHourOrHalfHour(factory: ConstraintFactory): Constraint {
        return factory.forEach(Event::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                User::class.java,
                Joiners.equal(Event::owner, User::email)
            )
            .filter { event, user ->
                val userPreferredTimeZone = user.preferences!!.preferredTimeZone
                event.getStartTime().toLocalDateTime(userPreferredTimeZone).minute % 30 != 0
            }
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Focus time should be scheduled on the hour or half hour")
    }
}
