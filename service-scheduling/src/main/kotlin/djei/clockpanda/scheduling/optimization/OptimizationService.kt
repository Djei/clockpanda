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
import djei.clockpanda.model.UserPersonalTask
import djei.clockpanda.model.UserPersonalTaskMetadata
import djei.clockpanda.model.UserPreferences
import djei.clockpanda.repository.UserPersonalTaskRepository
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacade
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEvent
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.scheduling.optimization.constraint.OptimizationConstraintsProvider
import djei.clockpanda.scheduling.optimization.model.OptimizationProblem
import djei.clockpanda.scheduling.optimization.model.OptimizerEvent
import djei.clockpanda.scheduling.optimization.model.TimeGrain
import djei.clockpanda.transaction.TransactionManager
import kotlinx.datetime.DayOfWeek
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
class OptimizationService(
    @Value("\${scheduling.solver.secondsSpentTerminationConfig}")
    private val solverSecondsSpentTerminationConfig: Long,
    private val googleCalendarApiFacade: GoogleCalendarApiFacade,
    private val userRepository: UserRepository,
    private val userPersonalTaskRepository: UserPersonalTaskRepository,
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
        val activeUserPersonalTasks = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.listByUserEmail(ctx, user.email)
        }.getOrElse { return OptimizationServiceError.UserPersonalTaskRepositoryError(it).left() }
            .filter { it.isActive() }

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

        val planningEntityOptimizationRange = optimizationProblemParameters.planningEntityOptimizationRange

        val scheduleToOptimize = extractExternalOptimizerEventsFromExistingSchedule(
            userPreferences,
            existingSchedule
        ) + extractFocusTimeOptimizerEventsFromExistingSchedule(
            user,
            userPreferences,
            planningEntityOptimizationRange,
            existingSchedule
        ) + extractPersonalTaskOptimizerEventsFromExistingSchedule(
            user,
            userPreferences,
            planningEntityOptimizationRange,
            activeUserPersonalTasks,
            existingSchedule
        ).getOrElse {
            return it.left()
        }
        return OptimizationProblem(
            parameters = optimizationProblemParameters,
            schedule = scheduleToOptimize,
            users = listOf(user)
        ).right()
    }

    private fun extractExternalOptimizerEventsFromExistingSchedule(
        userPreferences: UserPreferences,
        existingSchedule: List<CalendarEvent>
    ): List<OptimizerEvent> {
        return existingSchedule
            .filter { it.getCalendarEventType() == CalendarEventType.EXTERNAL_EVENT }
            .map {
                val calendarEventTimeSpan = it.getTimeSpan(userPreferences.preferredTimeZone)
                val calendarEventDurationInMinutes = it.getDurationInMinutes(userPreferences.preferredTimeZone)
                OptimizerEvent(
                    id = it.id,
                    startTimeGrain = TimeGrain(calendarEventTimeSpan.start),
                    durationInTimeGrains = calendarEventDurationInMinutes / TimeGrain.TIME_GRAIN_RESOLUTION,
                    type = it.getCalendarEventType(),
                    title = it.title,
                    originalCalendarEvent = it,
                    owner = it.owner,
                    personalTaskId = null,
                    personalTaskTargetDurationInMinutes = null,
                    isHighPriorityPersonalTask = null
                )
            }
    }

    private fun extractFocusTimeOptimizerEventsFromExistingSchedule(
        user: User,
        userPreferences: UserPreferences,
        planningEntityOptimizationRange: TimeSpan,
        existingSchedule: List<CalendarEvent>
    ): List<OptimizerEvent> {
        val existingFocusTimeOptimizerEvents = existingSchedule
            .filter { it.getCalendarEventType() == CalendarEventType.FOCUS_TIME }
            .map {
                val calendarEventTimeSpan = it.getTimeSpan(userPreferences.preferredTimeZone)
                val calendarEventDurationInMinutes = it.getDurationInMinutes(userPreferences.preferredTimeZone)
                OptimizerEvent(
                    id = it.id,
                    startTimeGrain = TimeGrain(calendarEventTimeSpan.start),
                    durationInTimeGrains = calendarEventDurationInMinutes / TimeGrain.TIME_GRAIN_RESOLUTION,
                    type = it.getCalendarEventType(),
                    title = it.title,
                    originalCalendarEvent = it,
                    owner = it.owner,
                    personalTaskId = null,
                    personalTaskTargetDurationInMinutes = null,
                    isHighPriorityPersonalTask = null
                )
            }
        // We artificially ensure to have enough focus time event planning entities to be optimized by the solver
        // It will simply reduce some to 0 duration if it can't fit them all
        val focusTimeMaxPoolSize = OPTIMIZATION_RANGE_IN_WEEKS * 7 * 2 // 2 focus time block per day
        val extraFocusTimesToOptimize = (1..focusTimeMaxPoolSize - existingFocusTimeOptimizerEvents.size).map { index ->
            OptimizerEvent(
                id = "focus-time-$index",
                type = CalendarEventType.FOCUS_TIME,
                title = CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE,
                startTimeGrain = TimeGrain(planningEntityOptimizationRange.start),
                durationInTimeGrains = 0,
                originalCalendarEvent = null,
                owner = user.email,
                personalTaskId = null,
                personalTaskTargetDurationInMinutes = null,
                isHighPriorityPersonalTask = null
            )
        }
        return existingFocusTimeOptimizerEvents + extraFocusTimesToOptimize
    }

    private fun extractPersonalTaskOptimizerEventsFromExistingSchedule(
        user: User,
        userPreferences: UserPreferences,
        planningEntityOptimizationRange: TimeSpan,
        activeUserPersonalTasks: List<UserPersonalTask>,
        existingSchedule: List<CalendarEvent>
    ): Either<OptimizationServiceError, List<OptimizerEvent>> {
        val existingPersonalTasks = existingSchedule
            .filter { it.getCalendarEventType() == CalendarEventType.PERSONAL_TASK }
        val activeUserPersonalTaskIds = activeUserPersonalTasks.map { it.id.toString() }
        // For each personal task in the existing schedule, check whether there is corresponding active personal task
        // If not, delete if from Google calendar
        existingPersonalTasks.forEach { event ->
            if (event.personalTaskId !in activeUserPersonalTaskIds) {
                googleCalendarApiFacade.deleteCalendarEvent(user, event)
                    .getOrElse {
                        return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left()
                    }
            }
        }

        val existingPersonalTasksById = existingPersonalTasks.associateBy {
            it.personalTaskId
        }
        // For each active personal task, check whether there is a corresponding personal task in the existing schedule
        // If there is, use it to build the optimizer event. If not create a new one
        return activeUserPersonalTasks.map { activeUserPersonalTask ->
            val existingPersonalTask = existingPersonalTasksById[activeUserPersonalTask.id.toString()]
            if (existingPersonalTask == null) {
                OptimizerEvent(
                    id = activeUserPersonalTask.id.toString(),
                    startTimeGrain = TimeGrain(planningEntityOptimizationRange.start),
                    durationInTimeGrains = 0,
                    type = CalendarEventType.PERSONAL_TASK,
                    title = activeUserPersonalTask.title,
                    originalCalendarEvent = null,
                    owner = user.email,
                    personalTaskId = activeUserPersonalTask.id.toString(),
                    personalTaskTargetDurationInMinutes = (activeUserPersonalTask.metadata as UserPersonalTaskMetadata.OneOffTask).oneOffTaskDurationInMinutes,
                    isHighPriorityPersonalTask = (activeUserPersonalTask.metadata as UserPersonalTaskMetadata.OneOffTask).isHighPriority
                )
            } else {
                val calendarEventTimeSpan = existingPersonalTask.getTimeSpan(userPreferences.preferredTimeZone)
                val calendarEventDurationInMinutes = existingPersonalTask.getDurationInMinutes(
                    userPreferences.preferredTimeZone
                )
                OptimizerEvent(
                    id = existingPersonalTask.id,
                    startTimeGrain = TimeGrain(calendarEventTimeSpan.start),
                    durationInTimeGrains = calendarEventDurationInMinutes / TimeGrain.TIME_GRAIN_RESOLUTION,
                    type = existingPersonalTask.getCalendarEventType(),
                    title = existingPersonalTask.title,
                    originalCalendarEvent = existingPersonalTask,
                    owner = existingPersonalTask.owner,
                    personalTaskId = existingPersonalTask.personalTaskId,
                    personalTaskTargetDurationInMinutes = (activeUserPersonalTask.metadata as UserPersonalTaskMetadata.OneOffTask).oneOffTaskDurationInMinutes,
                    isHighPriorityPersonalTask = (activeUserPersonalTask.metadata as UserPersonalTaskMetadata.OneOffTask).isHighPriority
                )
            }
        }.right()
    }

    private fun getOptimizationProblemSolverFactory(): SolverFactory<OptimizationProblem> {
        val solverConfig = SolverConfig()
            .withSolutionClass(OptimizationProblem::class.java)
            .withEntityClasses(OptimizerEvent::class.java)
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
        handleNewFocusTimes(user, newFocusTimes)
            .getOrElse { return it.left() }
        handleExistingFocusTimes(user, existingFocusTimes)
            .getOrElse { return it.left() }

        val optimizedPersonalTasks = optimizedScheduleResult.personalTasks
        val newPersonalTasks = optimizedPersonalTasks.filter { it.originalCalendarEvent == null }
        val existingPersonalTasks = optimizedPersonalTasks.filter { it.originalCalendarEvent != null }
        handleNewPersonalTasks(user, newPersonalTasks)
            .getOrElse { return it.left() }
        handleExistingPersonalTasks(user, existingPersonalTasks)
            .getOrElse { return it.left() }

        return Unit.right()
    }

    private fun handleNewFocusTimes(
        user: User,
        newFocusTimes: List<OptimizerEvent>
    ): Either<OptimizationServiceError, Unit> {
        newFocusTimes.filter {
            it.getDurationInMinutes() != 0
        }.forEach { it ->
            logger.info("Creating focus time event for user ${user.email}: ${it.getStartTime()} - ${it.getEndTime()}}")
            googleCalendarApiFacade.createClockPandaEvent(
                user = user,
                title = it.title,
                description = null,
                startTime = it.getStartTime(),
                endTime = it.getEndTime(),
                calendarEventType = CalendarEventType.FOCUS_TIME
            ).getOrElse {
                return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left()
            }
        }
        return Unit.right()
    }

    private fun handleExistingFocusTimes(
        user: User,
        existingFocusTimes: List<OptimizerEvent>
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
                        it.originalCalendarEvent
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

    private fun handleNewPersonalTasks(
        user: User,
        newPersonalTasks: List<OptimizerEvent>
    ): Either<OptimizationServiceError, Unit> {
        newPersonalTasks.filter {
            it.getDurationInMinutes() != 0
        }.forEach { it ->
            logger.info("Creating personal task event for user ${user.email}: ${it.getStartTime()} - ${it.getEndTime()}}")
            googleCalendarApiFacade.createClockPandaEvent(
                user = user,
                title = it.title,
                description = null,
                startTime = it.getStartTime(),
                endTime = it.getEndTime(),
                calendarEventType = CalendarEventType.PERSONAL_TASK,
                personalTaskId = it.personalTaskId
            ).getOrElse {
                return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left()
            }
            // TODO update currently scheduled for on personal task metadata
        }
        return Unit.right()
    }

    private fun handleExistingPersonalTasks(
        user: User,
        existingPersonalTasks: List<OptimizerEvent>
    ): Either<OptimizationServiceError, Unit> {
        existingPersonalTasks.forEach { it ->
            // If duration is now 0, delete the event
            if (it.getDurationInMinutes() == 0) {
                logger.info("Deleting personal task event for user ${user.email}: ${it.originalCalendarEvent!!.id}")
                googleCalendarApiFacade.deleteCalendarEvent(user, it.originalCalendarEvent)
                    .getOrElse {
                        return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left()
                    }
            } else {
                val preferredTimeZone = user.preferences?.preferredTimeZone
                    ?: return OptimizationServiceError.UserHasNoPreferencesError(user).left()
                val hasChangedFromOriginal = it.hasChangedFromOriginal(preferredTimeZone)
                if (hasChangedFromOriginal) {
                    logger.info("Updating personal task event for user ${user.email}: ${it.originalCalendarEvent!!.id}")
                    googleCalendarApiFacade.updateClockPandaEvent(
                        user,
                        it.originalCalendarEvent
                    ).getOrElse {
                        return OptimizationServiceError.GoogleCalendarApiFacadeError(it).left()
                    }
                    // TODO update currently scheduled for on personal task metadata
                } else {
                    logger.info("No change detected for personal task event for user ${user.email}: ${it.originalCalendarEvent!!.id}")
                }
            }
        }
        return Unit.right()
    }

    data class OptimizedScheduleResult(
        val user: User,
        val focusTimes: List<OptimizerEvent>,
        val personalTasks: List<OptimizerEvent>
    ) {
        companion object {
            fun fromSolvedOptimizationProblem(solvedOptimizationProblem: OptimizationProblem): OptimizedScheduleResult {
                return OptimizedScheduleResult(
                    user = solvedOptimizationProblem.users.first(),
                    focusTimes = solvedOptimizationProblem.schedule
                        .filter { it.type == CalendarEventType.FOCUS_TIME },
                    personalTasks = solvedOptimizationProblem.schedule
                        .filter { it.type == CalendarEventType.PERSONAL_TASK }
                )
            }
        }
    }
}
