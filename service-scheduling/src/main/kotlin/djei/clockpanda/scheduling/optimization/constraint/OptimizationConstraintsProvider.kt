package djei.clockpanda.scheduling.optimization.constraint

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import ai.timefold.solver.core.api.score.stream.Constraint
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors
import ai.timefold.solver.core.api.score.stream.ConstraintFactory
import ai.timefold.solver.core.api.score.stream.ConstraintProvider
import ai.timefold.solver.core.api.score.stream.Joiners
import djei.clockpanda.model.User
import djei.clockpanda.model.UserPersonalTaskMetadata
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.scheduling.optimization.model.OptimizationProblem
import djei.clockpanda.scheduling.optimization.model.OptimizerEvent
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atDate
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.DurationUnit

class OptimizationConstraintsProvider : ConstraintProvider {
    override fun defineConstraints(constraintFactory: ConstraintFactory): Array<Constraint> {
        return arrayOf(
            // 1. Hard constraint: Clock Panda events should NOT overlap with other events - penalty proportional to overlap
            clockPandaEventsShouldNotOverlapWithOtherEvents(constraintFactory),
            // 2. Hard constraint: Clock Panda events should NOT be outside user working hours - penalty proportional to amount outside
            clockPandaEventsShouldNotBeOutsideOfWorkingHours(constraintFactory),
            // 3. Hard constraint: Clock Panda events should start and end on the same day - fixed penalty per event
            clockPandaEventsShouldStartAndEndOnTheSameDay(constraintFactory),
            // 4. Hard constraint: Personal task should have exactly their specified duration - penalty proportional to difference
            personalTasksShouldHaveExactlyTheirTargetDuration(constraintFactory),
            // 5. Medium constraints:
            // - Personal task events should reach desired target - penalty proportional to amount missing.
            // - Focus time total amount should reach the desired target - penalty proportional to amount missing
            // - High priority personal task > standard personal task > focus time
            // 1 minute of focus time = 1 point
            // 1 minute of standard personal task should be worth 20 hours of focus time = 20 * 60 = 1200 points
            // 1 minute of high priority personal task should be worth 20 hours of standard personal task = 20 * 1200 = 24000 points
            // Focus time scoring is implemented as 2 constraints because empty weekly buckets are completely removed by first constraint
            // See https://stackoverflow.com/questions/67274703/optaplanner-constraint-streams-other-join-types-than-inner-join
            // This forces us to implement a separate `focusTimeTotalAmountIsZeroInAWeekForGivenUser` constraint for empty buckets
            personalTasksDurationScoring(constraintFactory),
            focusTimeTotalAmountPartiallyMeetingUserWeeklyTarget(constraintFactory),
            focusTimeTotalAmountIsZeroInAWeekForGivenUser(constraintFactory),
            // 7. Medium constraint: An existing focus time event should only be moved if it gives an extra 30 minutes of focus time - fixed penalty of 30 per event moved
            existingFocusTimeShouldOnlyBeMovedIfTheyGiveMoreFocusTime(constraintFactory),
            // 8. Soft constraint: Focus time events should be within preferred focus time range - penalty proportional to amount outside
            focusTimeShouldBeWithinPreferredFocusTimeRange(constraintFactory),
            // 9. Soft constraint: Clock Panda events should be scheduled on the hour or half hour - fixed penalty per event
            clockPandaEventsShouldBeScheduledOnTheHourOrHalfHour(constraintFactory),
            // 10. Soft constraint: Personal tasks should be within preferred time range - penalty proportional to amount outside
            personalTaskShouldBeWithinPreferredTimeRange(constraintFactory),
            // 11. Soft constraint: Personal tasks should be as soon as possible - penalty proportional to how far they are in the future and if they are high priority
            personalTaskShouldBeScheduledAsSoonAsPossible(constraintFactory)
        )
    }

    fun clockPandaEventsShouldNotOverlapWithOtherEvents(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                OptimizerEvent::class.java,
                Joiners.overlapping(OptimizerEvent::getStartTime, OptimizerEvent::getEndTime),
                // ensures the event pair is unique, and we do not join an event with itself
                Joiners.lessThan(OptimizerEvent::id),
                // Ignore external event overlapping between themselves since those are existing schedule overlaps
                Joiners.filtering { event1, event2 ->
                    !(event1.type == CalendarEventType.EXTERNAL_EVENT && event2.type == CalendarEventType.EXTERNAL_EVENT)
                }
            )
            // Penalize proportionally to the amount of overlap
            .penalize(HardMediumSoftScore.ONE_HARD) { event1, event2 ->
                event1.computeOverlapInMinutes(event2)
            }
            .asConstraint("Clock Panda events should not overlap with other events")
    }

    fun clockPandaEventsShouldNotBeOutsideOfWorkingHours(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type != CalendarEventType.EXTERNAL_EVENT }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                User::class.java,
                Joiners.equal(OptimizerEvent::owner, User::email)
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
            .asConstraint("Clock Panda Events should not be outside working hours")
    }

    fun clockPandaEventsShouldStartAndEndOnTheSameDay(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type != CalendarEventType.EXTERNAL_EVENT }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                User::class.java,
                Joiners.equal(OptimizerEvent::owner, User::email)
            )
            .filter { event, user ->
                val userPreferredTimeZone = user.preferences!!.preferredTimeZone
                event.getStartTime().toLocalDateTime(userPreferredTimeZone).date !=
                    event.getEndTime().toLocalDateTime(userPreferredTimeZone).date
            }
            .penalize(HardMediumSoftScore.ONE_HARD) { _, _ -> 1 }
            .asConstraint("Clock Panda Events should start and end on the same day")
    }

    fun personalTasksShouldHaveExactlyTheirTargetDuration(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type == CalendarEventType.PERSONAL_TASK }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .penalize(HardMediumSoftScore.ONE_HARD) { e ->
                val metadata = (e.userPersonalTask!!.metadata as UserPersonalTaskMetadata.OneOffTask)
                val targetDuration = metadata.oneOffTaskDurationInMinutes
                val missingInMinutes = abs(targetDuration - e.getDurationInMinutes())
                missingInMinutes
            }
            .asConstraint("Personal tasks should have exactly their target duration")
    }

    fun personalTasksDurationScoring(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type == CalendarEventType.PERSONAL_TASK }
            .penalize(HardMediumSoftScore.ONE_MEDIUM) { e ->
                val metadata = (e.userPersonalTask!!.metadata as UserPersonalTaskMetadata.OneOffTask)
                val targetDuration = metadata.oneOffTaskDurationInMinutes
                val isHighPriority = metadata.isHighPriority
                val missingInMinutes = abs(targetDuration - e.getDurationInMinutes())
                if (isHighPriority) {
                    missingInMinutes * 24000
                } else {
                    missingInMinutes * 1200
                }
            }
            .asConstraint("Personal tasks duration scoring")
    }

    fun focusTimeTotalAmountPartiallyMeetingUserWeeklyTarget(factory: ConstraintFactory): Constraint {
        return factory.forEach(User::class.java)
            .join(OptimizationProblem.OptimizationProblemParameters::class.java)
            // Create weekly buckets for each user over the problem optimization range
            .flattenLast { p ->
                p.splitExistingScheduleConsiderationRangeInWeeklyBuckets()
            }
            // Join user buckets with all events that they contain
            .join(
                OptimizerEvent::class.java,
                Joiners.equal({ u, _ -> u.email }, OptimizerEvent::owner),
                Joiners.overlapping(
                    { _, b -> b.start },
                    { _, b -> b.end },
                    OptimizerEvent::getStartTime,
                    OptimizerEvent::getEndTime
                ),
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
            .join(OptimizationProblem.OptimizationProblemParameters::class.java)
            // Create weekly buckets for each user over the problem optimization range
            .flattenLast { p ->
                p.splitExistingScheduleConsiderationRangeInWeeklyBuckets()
            }
            .ifNotExists(
                OptimizerEvent::class.java,
                Joiners.equal({ u, _ -> u.email }, OptimizerEvent::owner),
                Joiners.overlapping(
                    { _, b -> b.start },
                    { _, b -> b.end },
                    OptimizerEvent::getStartTime,
                    OptimizerEvent::getEndTime
                ),
                Joiners.filtering { _, _, e -> e.type == CalendarEventType.FOCUS_TIME }
            )
            .penalize(HardMediumSoftScore.ONE_MEDIUM) { user, _ ->
                val missingInMinutes = user.preferences!!.targetFocusTimeHoursPerWeek * 60
                missingInMinutes
            }
            .asConstraint("Focus time total amount is zero in a week for a given user")
    }

    fun existingFocusTimeShouldOnlyBeMovedIfTheyGiveMoreFocusTime(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .filter { e -> e.originalCalendarEvent != null }
            .filter { e -> e.hasChangedFromOriginal(TimeZone.UTC) }
            .penalize(HardMediumSoftScore.ONE_MEDIUM) { 30 }
            .asConstraint("Existing focus time events should only be moved if they give more focus time")
    }

    fun focusTimeShouldBeWithinPreferredFocusTimeRange(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type == CalendarEventType.FOCUS_TIME }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                User::class.java,
                Joiners.equal(OptimizerEvent::owner, User::email)
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

    fun clockPandaEventsShouldBeScheduledOnTheHourOrHalfHour(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type != CalendarEventType.EXTERNAL_EVENT }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                User::class.java,
                Joiners.equal(OptimizerEvent::owner, User::email)
            )
            .filter { event, user ->
                val userPreferredTimeZone = user.preferences!!.preferredTimeZone
                event.getStartTime().toLocalDateTime(userPreferredTimeZone).minute % 30 != 0
            }
            .penalize(HardMediumSoftScore.ONE_SOFT)
            .asConstraint("Focus time should be scheduled on the hour or half hour")
    }

    fun personalTaskShouldBeWithinPreferredTimeRange(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type == CalendarEventType.PERSONAL_TASK }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(
                User::class.java,
                Joiners.equal(OptimizerEvent::owner, User::email)
            )
            .penalize(HardMediumSoftScore.ONE_SOFT) { event, user ->
                val userPreferences = user.preferences!!
                val userPreferredTimeZone = userPreferences.preferredTimeZone
                val eventLocalStartTime = event.getStartTime().toLocalDateTime(userPreferredTimeZone)
                val metadata = (event.userPersonalTask!!.metadata as UserPersonalTaskMetadata.OneOffTask)
                val preferredTimeRange = metadata.timeRange.let {
                    TimeSpan(
                        start = it.start.atDate(eventLocalStartTime.date).toInstant(userPreferredTimeZone),
                        end = it.end.atDate(eventLocalStartTime.date).toInstant(userPreferredTimeZone)
                    )
                }
                event.computeOutsideRangeInMinutes(preferredTimeRange)
            }
            .asConstraint("Personal tasks should be within preferred time range")
    }

    fun personalTaskShouldBeScheduledAsSoonAsPossible(factory: ConstraintFactory): Constraint {
        return factory.forEach(OptimizerEvent::class.java)
            .filter { e -> e.type == CalendarEventType.PERSONAL_TASK }
            .filter { e -> e.getDurationInMinutes() > 0 }
            .join(OptimizationProblem.OptimizationProblemParameters::class.java)
            .join(
                User::class.java,
                Joiners.equal({ e, _ -> e.owner }, User::email)
            )
            .penalize(HardMediumSoftScore.ONE_SOFT) { event, problemParameters, user ->
                val userPreferences = user.preferences!!
                val userPreferredTimeZone = userPreferences.preferredTimeZone
                problemParameters.planningEntityOptimizationRange.start.toLocalDateTime(userPreferredTimeZone)
                val metadata = (event.userPersonalTask!!.metadata as UserPersonalTaskMetadata.OneOffTask)
                val penalty = abs(
                    (event.getStartTime() - problemParameters.planningEntityOptimizationRange.start)
                        .toInt(DurationUnit.MINUTES)
                )
                if (metadata.isHighPriority) {
                    penalty * 2
                } else {
                    penalty
                }
            }
            .asConstraint("Personal tasks should be scheduled as soon as possible")
    }
}
