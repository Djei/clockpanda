package djei.clockpanda.transaction

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.repository.UserRepositoryError
import djei.clockpanda.testing.ClockPandaSpringBootTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired

@ClockPandaSpringBootTest
class TransactionManagerTest {

    @Autowired
    lateinit var transactionManager: TransactionManager

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `test transaction rollback when exception is thrown`() {
        assertThrows<RuntimeException> {
            transactionManager.transaction { ctx ->
                functionThatThrows(ctx)
            }
        }
        val afterRetrieve = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, UserFixtures.djei2WithPreferences.email)
        }.getOrElse { fail("This should return right value", it) }
        assertThat(afterRetrieve).isNull()
    }

    @Test
    fun `test transaction rollback when block returns Either Left`() {
        // No transaction rollback because Either.right is returned
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei2WithPreferences)
            return@transaction Unit.right()
        }
        val rightEitherRetrieveAfterCreate = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, UserFixtures.djei2WithPreferences.email)
        }
        when (rightEitherRetrieveAfterCreate) {
            is Either.Left -> fail("This should return right value", rightEitherRetrieveAfterCreate.value)
            is Either.Right -> {
                assertThat(rightEitherRetrieveAfterCreate.value).isNotNull
                assertThat(rightEitherRetrieveAfterCreate.value!!.email).isEqualTo(UserFixtures.djei2WithPreferences.email)
            }
        }

        // Transaction is rolled back and we cannot find the user because Either.left is returned
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
            return@transaction "Error".left()
        }
        val leftEitherRetrieveAfterCreate = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, UserFixtures.djei1NoPreferences.email)
        }
        when (leftEitherRetrieveAfterCreate) {
            is Either.Left -> fail("This should return right value", leftEitherRetrieveAfterCreate.value)
            is Either.Right -> {
                assertThat(leftEitherRetrieveAfterCreate.value).isNull()
            }
        }
    }

    private fun functionThatThrows(ctx: TransactionalContext): Either<UserRepositoryError, Unit> {
        userRepository.create(ctx, UserFixtures.djei2WithPreferences)
        throw RuntimeException("runtime exception")
    }
}
