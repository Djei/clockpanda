package djei.clockpanda.scheduling.optimization

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.scheduling.SchedulingSpringBootTest
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacadeError
import djei.clockpanda.scheduling.model.fixtures.CalendarEventFixtures
import djei.clockpanda.transaction.TransactionManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.springframework.beans.factory.annotation.Autowired

class OptimizationServiceTest : SchedulingSpringBootTest() {
    @Autowired
    lateinit var optimizationService: OptimizationService

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var transactionManager: TransactionManager

    @Test
    fun `test optimizeSchedule returns left if user has no preferences`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.userWithNoPreferences)
        }

        when (val result = optimizationService.calculateOptimizedSchedule()) {
            is Either.Left -> {
                val error = result.value
                assertThat(error).isInstanceOf(OptimizationServiceError.UserHasNoPreferencesError::class.java)
                val userHasNoPreferencesError = error as OptimizationServiceError.UserHasNoPreferencesError
                assertThat(userHasNoPreferencesError.primaryUser).isEqualTo(UserFixtures.userWithNoPreferences)
                assertThat(userHasNoPreferencesError.message).isEqualTo("user djei1@email.com has no preferences")
            }

            is Either.Right -> fail("This should return left")
        }
    }

    @Test
    fun `test optimizeSchedule returns left if google api failure`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.userWithPreferences)
        }
        given(
            googleCalendarApiFacade.listCalendarEvents(
                any(),
                any()
            )
        ).willReturn(
            GoogleCalendarApiFacadeError.GoogleCalendarApiListEventsError(RuntimeException("some error")).left()
        )

        when (val result = optimizationService.calculateOptimizedSchedule()) {
            is Either.Left -> {
                val error = result.value
                assertThat(error).isInstanceOf(OptimizationServiceError.GoogleCalendarApiFacadeError::class.java)
                val googleCalendarApiFacadeError = error as OptimizationServiceError.GoogleCalendarApiFacadeError
                assertThat(googleCalendarApiFacadeError.message).isEqualTo("google calendar api facade error: google calendar api list events error: some error")
            }

            is Either.Right -> fail("This should return left")
        }
    }

    @Test
    fun `test optimizeSchedule - happy path`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.userWithPreferences)
        }
        given(
            googleCalendarApiFacade.listCalendarEvents(
                any(),
                any()
            )
        ).willReturn(
            listOf(CalendarEventFixtures.externalTypeCalendarEvent).right()
        )

        when (val result = optimizationService.calculateOptimizedSchedule()) {
            is Either.Left -> fail("This should return right", result.value)

            is Either.Right -> {
                assertThat(result.value).hasSize(1)
                val solvedOptimizationProblem = result.value[0]
                assertThat(solvedOptimizationProblem.user).isEqualTo(UserFixtures.userWithPreferences)
                assertThat(solvedOptimizationProblem.focusTimes).hasSize(28)
            }
        }
    }
}
