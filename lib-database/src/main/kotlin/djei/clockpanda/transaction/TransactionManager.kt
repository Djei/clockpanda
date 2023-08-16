package djei.clockpanda.transaction

import arrow.core.Either
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.springframework.stereotype.Component

/**
 * A lightweight wrapper around [DSLContext] to provide transactional blocks
 * with the scoped [TransactionalContext] that allows our transactions to distinguish between
 * transactional and non-transactional scopes of [DSLContext].
 */
@Component("JooqTransactionManager")
class TransactionManager(val ctx: DSLContext) {
    fun <A, B> transaction(block: (TransactionalContext) -> Either<A, B>): Either<A, B> {
        val result = try {
            ctx.transactionResult { txCtx ->
                when (val blockResult = block(TransactionalContext(txCtx.dsl()))) {
                    // If block returns Either.Left, we throw an ArrowEitherTransactionRollbackException to rollback
                    // the transaction and get back the Either.Left in the catch block
                    // Note that current implementation always rollback whichever value is in Either.Left
                    // Further improvements could be to rollback only when the value have some specific type
                    is Either.Left -> {
                        throw ArrowEitherTransactionRollbackException(blockResult)
                    }

                    is Either.Right -> {
                        blockResult
                    }
                }
            }
            // Our ArrowEitherTransactionRollbackException will be wrapped into a `DataAccessException`
        } catch (ex: DataAccessException) {
            when (ex.cause) {
                is ArrowEitherTransactionRollbackException -> {
                    return (ex.cause as ArrowEitherTransactionRollbackException).getEither()
                }

                else -> {
                    throw ex.cause ?: ex
                }
            }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private class ArrowEitherTransactionRollbackException(
        private val either: Either<*, *>
    ) : Exception("Rolling back transaction due to an error in Either: $either") {
        fun <A, B> getEither(): Either<A, B> {
            return either as Either<A, B>
        }
    }
}
