package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import ai.timefold.solver.core.api.score.stream.ConstraintStreamImplType
import ai.timefold.solver.core.api.solver.SolutionManager
import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig
import ai.timefold.solver.core.config.solver.SolverConfig
import ai.timefold.solver.core.config.solver.termination.TerminationConfig
import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import djei.clockpanda.model.User
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacade
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.scheduling.optimization.constraint.OptimizationConstraintsProvider
import djei.clockpanda.scheduling.optimization.model.Event
import djei.clockpanda.scheduling.optimization.model.OptimizationProblem
import djei.clockpanda.scheduling.optimization.model.TimeGrain
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jooq.DSLContext
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class OptimizationService(
    @Value("\${scheduling.solver.secondsSpentTerminationConfig}")
    private val solverSecondsSpentTerminationConfig: Long,
    private val googleCalendarApiFacade: GoogleCalendarApiFacade,
    private val userRepository: UserRepository,
    private val dslContext: DSLContext,
    private val logger: Logger
) {
    companion object {
        // Optimize for 2 weeks
        const val OPTIMIZATION_RANGE_IN_DAYS = 14
    }

    fun calculateOptimizedSchedule(): Either<OptimizationServiceError, List<OptimizedScheduleResult>> {
        val users = userRepository.list(dslContext)
            .getOrElse { return OptimizationServiceError.UserRepositoryError(it).left() }
        return users.map { user ->
            val problem = generateOptimizationProblem(user)
                .getOrElse { return it.left() }
            Either.catch {
                val solverFactory = getOptimizationProblemSolverFactory()
                val solver = solverFactory.buildSolver()
                val solutionManager = SolutionManager.create<OptimizationProblem, HardMediumSoftScore>(solverFactory)
                val solution = solver.solve(problem)
                logger.info("Scheduled optimized for user ${user.email}: ${solutionManager.explain(solution).summary}")
                OptimizedScheduleResult.fromSolvedOptimizationProblem(solution)
            }.getOrElse { return OptimizationServiceError.SolverError(it).left() }
        }.right()
    }

    private fun generateOptimizationProblem(user: User): Either<OptimizationServiceError, OptimizationProblem> {
        logger.info("Generating optimization problem for user ${user.email}")
        val userPreferences = user.preferences
            ?: return OptimizationServiceError.UserHasNoPreferencesError(user).left()

        // Start optimizing from tomorrow start of day in UTC
        val optimizationRangeStart = Clock.System.now()
            .toLocalDateTime(TimeZone.UTC)
            .date
            .plus(1, DateTimeUnit.DAY)
            .atStartOfDayIn(TimeZone.UTC)
        val optimizationRangeEnd = optimizationRangeStart.plus(24 * OPTIMIZATION_RANGE_IN_DAYS, DateTimeUnit.HOUR)

        val userCalendarEvents = googleCalendarApiFacade.listCalendarEvents(
            user,
            TimeSpan(optimizationRangeStart, optimizationRangeEnd)
        ).getOrElse { return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left() }

        val existingSchedule = userCalendarEvents
            .map { calendarEvent ->
                Event.fromCalendarEvent(calendarEvent, userPreferences.preferredTimeZone)
            }
        val existingFocusTimes = existingSchedule.filter { it.type == CalendarEventType.FOCUS_TIME }
        val existingMealBreaks = existingSchedule.filter { it.type == CalendarEventType.MEAL_BREAK }
        // We artificially ensure to have enough focus time event planning entities to be optimized by the solver
        // It will simply reduce some to 0 duration if it can't fit them all
        val focusTimesToOptimize = (1..OPTIMIZATION_RANGE_IN_DAYS - existingFocusTimes.size).map { index ->
            Event(
                id = "focus-time-$index",
                type = CalendarEventType.FOCUS_TIME,
                startTimeGrain = TimeGrain(optimizationRangeStart),
                durationInTimeGrains = 0,
                originalCalendarEvent = null,
                owner = user.email
            )
        }
        // We artificially ensure to have enough meal breaks event planning entities to be optimized by the solver
        // It will simply reduce some to 0 duration if it can't fit them all
        val mealBreaksToOptimize = (1..OPTIMIZATION_RANGE_IN_DAYS - existingMealBreaks.size).map { index ->
            Event(
                id = "meal-break-$index",
                type = CalendarEventType.MEAL_BREAK,
                startTimeGrain = TimeGrain(optimizationRangeStart),
                durationInTimeGrains = 0,
                originalCalendarEvent = null,
                owner = user.email
            )
        }
        val scheduleToOptimize = existingSchedule + focusTimesToOptimize + mealBreaksToOptimize

        return OptimizationProblem(
            parametrization = OptimizationProblem.OptimizationProblemParametrization(
                optimizationRange = TimeSpan(optimizationRangeStart, optimizationRangeEnd)
            ),
            schedule = scheduleToOptimize,
            users = listOf(user)
        ).right()
    }

    private fun getOptimizationProblemSolverFactory(): SolverFactory<OptimizationProblem> {
        val solverConfig = SolverConfig()
            .withSolutionClass(OptimizationProblem::class.java)
            .withEntityClasses(Event::class.java)
            .withScoreDirectorFactory(
                ScoreDirectorFactoryConfig()
                    .withConstraintProviderClass(OptimizationConstraintsProvider::class.java)
                    .withConstraintStreamImplType(ConstraintStreamImplType.BAVET)
                    .withInitializingScoreTrend("ONLY_DOWN")
            )
            .withTerminationConfig(TerminationConfig().withSecondsSpentLimit(solverSecondsSpentTerminationConfig))
        return SolverFactory.create(solverConfig)
    }

    fun syncOptimizedScheduleWithUserCalendar(
        optimizedScheduleResult: OptimizedScheduleResult
    ): Either<OptimizationServiceError, Unit> {
        val user = optimizedScheduleResult.user
        val optimizedFocusTimes = optimizedScheduleResult.focusTimes
        val newFocusTimes = optimizedFocusTimes.filter { it.originalCalendarEvent == null }
        val existingFocusTimes = optimizedFocusTimes.filter { it.originalCalendarEvent != null }

        handleExistingFocusTimes(user, existingFocusTimes)
            .getOrElse { return it.left() }
        handleNewFocusTimes(newFocusTimes, user)
            .getOrElse { return it.left() }

        return Unit.right()
    }

    private fun handleNewFocusTimes(
        newFocusTimes: List<Event>,
        user: User
    ): Either<OptimizationServiceError, Unit> {
        newFocusTimes.filter {
            it.getDurationInMinutes() != 0
        }.forEach { it ->
            logger.info("Creating focus time event for user ${user.email}: ${it.getStartTime()} - ${it.getEndTime()}}")
            googleCalendarApiFacade.createCalendarEvent(
                user = user,
                title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
                description = null,
                startTime = it.getStartTime(),
                endTime = it.getEndTime()
            ).getOrElse {
                return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left()
            }
        }
        return Unit.right()
    }

    private fun handleExistingFocusTimes(
        user: User,
        existingFocusTimes: List<Event>
    ): Either<OptimizationServiceError, Unit> {
        existingFocusTimes.forEach { it ->
            // If duration is now 0, delete the event
            if (it.getDurationInMinutes() == 0) {
                logger.info("Deleting focus time event for user ${user.email}: ${it.originalCalendarEvent!!.id}")
                googleCalendarApiFacade.deleteCalendarEvent(user, it.originalCalendarEvent)
                    .getOrElse {
                        return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left()
                    }
            } else {
                val preferredTimeZone = user.preferences?.preferredTimeZone
                    ?: return OptimizationServiceError.UserHasNoPreferencesError(user).left()
                val hasChangedFromOriginal = it.hasChangedFromOriginal(preferredTimeZone)
                if (hasChangedFromOriginal) {
                    logger.info("Updating focus time event for user ${user.email}: ${it.originalCalendarEvent!!.id}")
                    googleCalendarApiFacade.updateCalendarEvent(
                        user,
                        it.originalCalendarEvent.id,
                        it.originalCalendarEvent.title,
                        it.originalCalendarEvent.description,
                        it.getStartTime(),
                        it.getEndTime()
                    ).getOrElse {
                        return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left()
                    }
                } else {
                    logger.info("No change detected for focus time event for user ${user.email}: ${it.originalCalendarEvent!!.id}")
                }
            }
        }
        return Unit.right()
    }

    data class OptimizedScheduleResult(
        val user: User,
        val optimizationRange: TimeSpan,
        val focusTimes: List<Event>
    ) {
        companion object {
            fun fromSolvedOptimizationProblem(solvedOptimizationProblem: OptimizationProblem): OptimizedScheduleResult {
                return OptimizedScheduleResult(
                    user = solvedOptimizationProblem.users.first(),
                    optimizationRange = solvedOptimizationProblem.parametrization.optimizationRange,
                    focusTimes = solvedOptimizationProblem.schedule
                        .filter { it.type == CalendarEventType.FOCUS_TIME }
                )
            }
        }
    }
}
