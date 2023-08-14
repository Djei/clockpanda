package djei.clockpanda.scheduling.optimization

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacade
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEventType
import org.slf4j.Logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class OptimizationCronJob(
    private val optimizationService: OptimizationService,
    private val googleCalendarApiFacade: GoogleCalendarApiFacade,
    private val logger: Logger
) {

    @Scheduled(fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    fun runOptimization() {
        logger.info("Running optimization cron job")
        when (val optimizationResult = optimizationService.optimizeSchedule()) {
            is Either.Left -> {
                logger.error("Optimization run failed: ${optimizationResult.value.message}", optimizationResult.value)
            }

            is Either.Right -> {
                logger.info("Optimization run succeeded. Apply it on user calendar")
                optimizationResult.value.forEach {
                    when (val syncResult = syncUserCalendar(it)) {
                        is Either.Left -> {
                            logger.error("Failed to sync user calendar: ${syncResult.value.message}", syncResult.value)
                        }

                        is Either.Right -> {
                            logger.info("User ${it.user.email} calendar synced successfully")
                        }
                    }
                }
            }
        }
        logger.info("Finished optimization cron job")
    }

    private fun syncUserCalendar(
        optimizationServiceResult: OptimizationService.OptimizationServiceResult
    ): Either<OptimizationCronJobError, Unit> {
        val user = optimizationServiceResult.user
        val optimizationRange = optimizationServiceResult.optimizationRange
        val existingFocusTimes = googleCalendarApiFacade.listCalendarEvents(user, optimizationRange)
            .getOrElse { return OptimizationCronJobError("Failed to list calendar events for user $user", it).left() }
            .filter { it.getType() == CalendarEventType.FOCUS_TIME }
        val newFocusTimes = optimizationServiceResult.focusTimes
        // Delete existing focus times
        existingFocusTimes.forEach { it ->
            googleCalendarApiFacade.deleteCalendarEvent(user, it)
                .getOrElse {
                    return OptimizationCronJobError(
                        "Failed to delete calendar event $it for user $user",
                        it
                    ).left()
                }
        }
        // Create the new focus times
        newFocusTimes
            .filter {
                it.getDurationInMinutes() != 0
            }
            .forEach { it ->
                googleCalendarApiFacade.createCalendarEvent(
                    user = user,
                    title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
                    description = null,
                    startTime = it.getStartTime(),
                    endTime = it.getEndTime()
                ).getOrElse {
                    return OptimizationCronJobError(
                        "Failed to create calendar event $it for user $user",
                        it
                    ).left()
                }
            }
        return Unit.right()
    }
}

private data class OptimizationCronJobError(
    override val message: String,
    override val cause: Throwable? = null
) : Error(message, cause)
