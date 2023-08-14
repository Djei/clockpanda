package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.score.stream.ConstraintStreamImplType
import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicType
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig
import ai.timefold.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig
import ai.timefold.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig
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
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
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

    fun optimizeSchedule(): Either<OptimizationServiceError, List<OptimizationServiceResult>> {
        val users = userRepository.list(dslContext)
            .getOrElse { return OptimizationServiceError.UserRepositoryError(it).left() }
        return users.map { user ->
            val problem = generateOptimizationProblem(user)
                .getOrElse { return it.left() }
            Either.catch {
                val solver = getOptimizationProblemSolverFactory().buildSolver()
                val solution = solver.solve(problem)
                logger.info("Scheduled optimized for user ${user.email}")
                OptimizationServiceResult.fromSolvedOptimizationProblem(solution)
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
            // We ignore existing focus time from existing schedule since optimization will add them back
            .filter { it.getType() != CalendarEventType.FOCUS_TIME }
            .map { calendarEvent ->
                Event.fromCalendarEvent(calendarEvent, userPreferences.preferredTimeZone)
                    .getOrElse { error ->
                        return OptimizationServiceError.EventError(error).left()
                    }
            }
        // We artificially add 1 focus time event planning entities to be optimized by the solver per day in the optimization range
        // This is a bit of a hack as we need to investigate if it is possible to add planning entities dynamically
        val focusTimesToOptimize = (1..OPTIMIZATION_RANGE_IN_DAYS).map { index ->
            Event(
                id = "focus-time-$index",
                type = CalendarEventType.FOCUS_TIME,
                startTimeGrain = TimeGrain(optimizationRangeStart),
                durationInTimeGrains = 1,
                owner = user.email
            )
        }
        val scheduleToOptimize = existingSchedule + focusTimesToOptimize

        return OptimizationProblem(
            optimizationRange = TimeSpan(optimizationRangeStart, optimizationRangeEnd),
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
                    .withInitializingScoreTrend("ANY")
            )
            .withPhases(
                ConstructionHeuristicPhaseConfig()
                    .withConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT),
                LocalSearchPhaseConfig()
                    .withAcceptorConfig(LocalSearchAcceptorConfig().withLateAcceptanceSize(400))
                    .withForagerConfig(LocalSearchForagerConfig().withAcceptedCountLimit(1))
            )
            .withTerminationConfig(TerminationConfig().withSecondsSpentLimit(solverSecondsSpentTerminationConfig))
        return SolverFactory.create(solverConfig)
    }

    data class OptimizationServiceResult(
        val user: User,
        val optimizationRange: TimeSpan,
        val focusTimes: List<Event>
    ) {
        companion object {
            fun fromSolvedOptimizationProblem(solvedOptimizationProblem: OptimizationProblem): OptimizationServiceResult {
                return OptimizationServiceResult(
                    user = solvedOptimizationProblem.users.first(),
                    optimizationRange = solvedOptimizationProblem.optimizationRange,
                    focusTimes = solvedOptimizationProblem.schedule
                        .filter { it.type == CalendarEventType.FOCUS_TIME }
                )
            }
        }
    }
}
