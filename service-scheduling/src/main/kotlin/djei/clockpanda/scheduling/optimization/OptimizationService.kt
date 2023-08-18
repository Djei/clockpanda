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
import djei.clockpanda.transaction.TransactionManager
import kotlinx.datetime.DayOfWeek
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class OptimizationService(
    @Value("\${scheduling.solver.secondsSpentTerminationConfig}")
    private val solverSecondsSpentTerminationConfig: Long,
    private val googleCalendarApiFacade: GoogleCalendarApiFacade,
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val logger: Logger
) {
    companion object {
        // Optimize for 2 weeks
        const val OPTIMIZATION_RANGE_IN_WEEKS = 2
        val WEEK_START_DAY_OF_WEEK = DayOfWeek.MONDAY
    }

    fun calculateOptimizedSchedule(): Either<OptimizationServiceError, List<OptimizedScheduleResult>> {
        val users = transactionManager.transaction { ctx ->
            userRepository.list(ctx)
        }.getOrElse { return OptimizationServiceError.UserRepositoryError(it).left() }
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

        val optimizationProblemParameters = OptimizationProblem.OptimizationProblemParameters(
            optimizationMaxRangeInWeeks = OPTIMIZATION_RANGE_IN_WEEKS,
            weekStartDayOfWeek = WEEK_START_DAY_OF_WEEK
        )

        val existingScheduleConsiderationRange = optimizationProblemParameters.existingScheduleConsiderationRange
        val userCalendarEvents = googleCalendarApiFacade.listCalendarEvents(
            user,
            TimeSpan(
                existingScheduleConsiderationRange.start,
                existingScheduleConsiderationRange.end
            )
        ).getOrElse { return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left() }
        val existingSchedule = userCalendarEvents
            // Only keep busy events from existing schedule as those are the only ones we need to plan around
            .filter { it.busy }
            // This works for now as we are only optimizing for a single user
            .filter { it.isUserAttending(user.email) }
            .map { calendarEvent ->
                Event.fromCalendarEvent(calendarEvent, userPreferences.preferredTimeZone)
            }
        val existingFocusTimes = existingSchedule.filter { it.type == CalendarEventType.FOCUS_TIME }

        val planningEntityOptimizationRange = optimizationProblemParameters.planningEntityOptimizationRange
        // We artificially ensure to have enough focus time event planning entities to be optimized by the solver
        // It will simply reduce some to 0 duration if it can't fit them all
        val focusTimeMaxPoolSize = OPTIMIZATION_RANGE_IN_WEEKS * 7 * 2 // 2 focus time block per day
        val extraFocusTimesToOptimize = (1..focusTimeMaxPoolSize - existingFocusTimes.size).map { index ->
            Event(
                id = "focus-time-$index",
                type = CalendarEventType.FOCUS_TIME,
                startTimeGrain = TimeGrain(planningEntityOptimizationRange.start),
                durationInTimeGrains = 0,
                originalCalendarEvent = null,
                owner = user.email
            )
        }
        val scheduleToOptimize = existingSchedule + extraFocusTimesToOptimize
        return OptimizationProblem(
            parameters = optimizationProblemParameters,
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
            googleCalendarApiFacade.createClockPandaEvent(
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
                    googleCalendarApiFacade.updateClockPandaEvent(
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
        val focusTimes: List<Event>
    ) {
        companion object {
            fun fromSolvedOptimizationProblem(solvedOptimizationProblem: OptimizationProblem): OptimizedScheduleResult {
                return OptimizedScheduleResult(
                    user = solvedOptimizationProblem.users.first(),
                    focusTimes = solvedOptimizationProblem.schedule
                        .filter { it.type == CalendarEventType.FOCUS_TIME }
                )
            }
        }
    }
}
