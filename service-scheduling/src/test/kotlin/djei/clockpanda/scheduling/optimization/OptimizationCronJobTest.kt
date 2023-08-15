package djei.clockpanda.scheduling.optimization

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore
import ai.timefold.solver.core.api.solver.SolutionManager
import ai.timefold.solver.core.api.solver.SolverFactory
import ai.timefold.solver.core.impl.score.DefaultScoreExplanation
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager
import ai.timefold.solver.core.impl.solver.DefaultSolver
import ai.timefold.solver.core.impl.solver.DefaultSolverFactory
import arrow.core.left
import arrow.core.right
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.scheduling.SchedulingSpringBootTest
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacadeError
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.scheduling.model.fixtures.CalendarEventFixtures
import djei.clockpanda.scheduling.optimization.OptimizationService.Companion.OPTIMIZATION_RANGE_IN_DAYS
import djei.clockpanda.scheduling.optimization.fixtures.EventFixtures
import kotlinx.datetime.Instant
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atMost
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.willReturn
import org.springframework.beans.factory.annotation.Autowired

class OptimizationCronJobTest : SchedulingSpringBootTest() {

    @Autowired
    lateinit var optimizationCronJob: OptimizationCronJob

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var dslContext: DSLContext

    @Test
    fun `test run optimization cron job failure does not throw exception`() {
        userRepository.create(dslContext, UserFixtures.userWithPreferences)
        given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
            GoogleCalendarApiFacadeError.GoogleCalendarApiListEventsError(RuntimeException("some error")).left()
        )

        optimizationCronJob.optimizeSchedule()

        verify(googleCalendarApiFacade, never()).deleteCalendarEvent(any(), any())
        verify(googleCalendarApiFacade, never()).createCalendarEvent(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun `test run optimization cron job - failure to update existing focus times`() {
        mockStatic(SolutionManager::class.java).use { solutionManagerMockStatic ->
            mockStatic(SolverFactory::class.java).use { solverFactoryMockStatic ->
                val solvedOptimizationProblem = OptimizationProblem(
                    parametrization = OptimizationProblem.OptimizationProblemParametrization(
                        optimizationRange = TimeSpan(
                            start = Instant.parse("2021-01-01T00:00:00Z"),
                            end = Instant.parse("2021-01-31T00:00:00Z")
                        )
                    ),
                    schedule = listOf(
                        EventFixtures.noChangeExistingFocusTime,
                        EventFixtures.updatedExistingFocusTime,
                        EventFixtures.externalEvent
                    ),
                    users = listOf(UserFixtures.userWithPreferences)
                )
                setupOptimizationResult(solvedOptimizationProblem, solutionManagerMockStatic, solverFactoryMockStatic)
                userRepository.create(dslContext, UserFixtures.userWithPreferences)
                given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
                    listOf(
                        CalendarEventFixtures.focusTimeCalendarEvent1,
                        CalendarEventFixtures.externalTypeCalendarEvent
                    ).right()
                )
                given(googleCalendarApiFacade.updateCalendarEvent(any(), any())).willReturn {
                    GoogleCalendarApiFacadeError.GoogleCalendarApiUpdateEventError(RuntimeException("some error"))
                        .left()
                }

                optimizationCronJob.optimizeSchedule()

                verify(googleCalendarApiFacade).updateCalendarEvent(
                    eq(UserFixtures.userWithPreferences),
                    argThat {
                        id == EventFixtures.updatedExistingFocusTime.id
                    }
                )
                verify(googleCalendarApiFacade, never()).updateCalendarEvent(
                    eq(UserFixtures.userWithPreferences),
                    argThat {
                        id == EventFixtures.noChangeExistingFocusTime.id
                    }
                )
                verify(googleCalendarApiFacade, never()).createCalendarEvent(any(), any(), anyOrNull(), any(), any())
            }
        }
    }

    @Test
    fun `test run optimization cron job - failure to delete existing focus times`() {
        mockStatic(SolutionManager::class.java).use { solutionManagerMockStatic ->
            mockStatic(SolverFactory::class.java).use { solverFactoryMockStatic ->
                val solvedOptimizationProblem = OptimizationProblem(
                    parametrization = OptimizationProblem.OptimizationProblemParametrization(
                        optimizationRange = TimeSpan(
                            start = Instant.parse("2021-01-01T00:00:00Z"),
                            end = Instant.parse("2021-01-31T00:00:00Z")
                        )
                    ),
                    schedule = listOf(
                        EventFixtures.existingFocusTimeToBeDeleted,
                        EventFixtures.externalEvent
                    ),
                    users = listOf(UserFixtures.userWithPreferences)
                )
                setupOptimizationResult(solvedOptimizationProblem, solutionManagerMockStatic, solverFactoryMockStatic)
                userRepository.create(dslContext, UserFixtures.userWithPreferences)
                given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
                    listOf(
                        CalendarEventFixtures.focusTimeCalendarEvent1,
                        CalendarEventFixtures.externalTypeCalendarEvent
                    ).right()
                )
                given(googleCalendarApiFacade.deleteCalendarEvent(any(), any())).willReturn {
                    GoogleCalendarApiFacadeError.GoogleCalendarApiDeleteEventError(RuntimeException("some error"))
                        .left()
                }

                optimizationCronJob.optimizeSchedule()

                verify(googleCalendarApiFacade).deleteCalendarEvent(
                    eq(UserFixtures.userWithPreferences),
                    argThat {
                        id == EventFixtures.existingFocusTimeToBeDeleted.id
                    }
                )
                verify(googleCalendarApiFacade, never()).createCalendarEvent(any(), any(), anyOrNull(), any(), any())
            }
        }
    }

    @Test
    fun `test run optimization cron job - failure to create new focus times`() {
        mockStatic(SolutionManager::class.java).use { solutionManagerMockStatic ->
            mockStatic(SolverFactory::class.java).use { solverFactoryMockStatic ->
                val solvedOptimizationProblem = OptimizationProblem(
                    parametrization = OptimizationProblem.OptimizationProblemParametrization(
                        optimizationRange = TimeSpan(
                            start = Instant.parse("2021-01-01T00:00:00Z"),
                            end = Instant.parse("2021-01-31T00:00:00Z")
                        )
                    ),
                    schedule = listOf(
                        EventFixtures.newFocusTimeToBeCreated,
                        EventFixtures.externalEvent
                    ),
                    users = listOf(UserFixtures.userWithPreferences)
                )
                setupOptimizationResult(solvedOptimizationProblem, solutionManagerMockStatic, solverFactoryMockStatic)
                userRepository.create(dslContext, UserFixtures.userWithPreferences)
                given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
                    listOf(
                        CalendarEventFixtures.focusTimeCalendarEvent1,
                        CalendarEventFixtures.focusTimeCalendarEvent2,
                        CalendarEventFixtures.externalTypeCalendarEvent
                    ).right()
                )
                given(googleCalendarApiFacade.createCalendarEvent(any(), any(), anyOrNull(), any(), any())).willReturn {
                    GoogleCalendarApiFacadeError.GoogleCalendarApiCreateEventError(RuntimeException("some error"))
                        .left()
                }

                optimizationCronJob.optimizeSchedule()

                verify(googleCalendarApiFacade).createCalendarEvent(
                    any(),
                    eq(CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE),
                    anyOrNull(),
                    any(),
                    any()
                )
            }
        }
    }

    @Test
    fun `test run optimization cron job - happy path`() {
        mockStatic(SolutionManager::class.java).use { solutionManagerMockStatic ->
            mockStatic(SolverFactory::class.java).use { solverFactoryMockStatic ->
                val solvedOptimizationProblem = OptimizationProblem(
                    parametrization = OptimizationProblem.OptimizationProblemParametrization(
                        optimizationRange = TimeSpan(
                            start = Instant.parse("2021-01-01T00:00:00Z"),
                            end = Instant.parse("2021-01-31T00:00:00Z")
                        )
                    ),
                    schedule = listOf(
                        EventFixtures.newFocusTimeToBeCreated,
                        EventFixtures.existingFocusTimeToBeDeleted,
                        EventFixtures.updatedExistingFocusTime,
                        EventFixtures.externalEvent
                    ),
                    users = listOf(UserFixtures.userWithPreferences)
                )
                setupOptimizationResult(solvedOptimizationProblem, solutionManagerMockStatic, solverFactoryMockStatic)
                userRepository.create(dslContext, UserFixtures.userWithPreferences)
                given(googleCalendarApiFacade.listCalendarEvents(any(), any())).willReturn(
                    listOf(
                        CalendarEventFixtures.focusTimeCalendarEvent1,
                        CalendarEventFixtures.focusTimeCalendarEvent2,
                        CalendarEventFixtures.externalTypeCalendarEvent
                    ).right()
                )
                given(googleCalendarApiFacade.deleteCalendarEvent(any(), any())).willReturn {
                    Unit.right()
                }
                given(googleCalendarApiFacade.updateCalendarEvent(any(), any())).willReturn {
                    CalendarEventFixtures.focusTimeCalendarEvent1.right()
                }
                given(googleCalendarApiFacade.createCalendarEvent(any(), any(), anyOrNull(), any(), any()))
                    .willReturn {
                        CalendarEventFixtures.focusTimeCalendarEvent1.right()
                    }

                optimizationCronJob.optimizeSchedule()

                verify(googleCalendarApiFacade).updateCalendarEvent(
                    eq(UserFixtures.userWithPreferences),
                    argThat {
                        id == EventFixtures.updatedExistingFocusTime.id
                    }
                )
                verify(googleCalendarApiFacade).deleteCalendarEvent(
                    eq(UserFixtures.userWithPreferences),
                    argThat {
                        id == EventFixtures.existingFocusTimeToBeDeleted.id
                    }
                )
                verify(googleCalendarApiFacade, atMost(OPTIMIZATION_RANGE_IN_DAYS)).createCalendarEvent(
                    any(),
                    eq(CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE),
                    anyOrNull(),
                    any(),
                    any()
                )
                verify(googleCalendarApiFacade, never()).deleteCalendarEvent(
                    any(),
                    eq(CalendarEventFixtures.externalTypeCalendarEvent)
                )
            }
        }
    }

    private fun setupOptimizationResult(
        solvedOptimizationProblem: OptimizationProblem,
        solutionManagerMockStatic: MockedStatic<SolutionManager<*, *>>,
        solverFactoryMockStatic: MockedStatic<SolverFactory<*>>
    ) {
        val mockSolverFactory = mock<DefaultSolverFactory<OptimizationProblem>>()
        val mockSolver = mock<DefaultSolver<OptimizationProblem>>()
        val mockSolutionManager = mock<DefaultSolutionManager<OptimizationProblem, HardMediumSoftScore>>()
        val mockScoreExplanation = mock<DefaultScoreExplanation<OptimizationProblem, HardMediumSoftScore>>()
        given(mockSolverFactory.buildSolver()).willReturn(mockSolver)
        given(mockSolver.solve(any())).willReturn(solvedOptimizationProblem)
        given(mockSolutionManager.explain((any()))).willReturn(mockScoreExplanation)
        solverFactoryMockStatic.`when`<SolverFactory<OptimizationProblem>> {
            SolverFactory.create<OptimizationProblem>(any())
        }.thenReturn(mockSolverFactory)
        solutionManagerMockStatic.`when`<SolutionManager<OptimizationProblem, HardMediumSoftScore>> {
            SolutionManager.create<OptimizationProblem, HardMediumSoftScore>(any())
        }.thenReturn(mockSolutionManager)
    }
}
