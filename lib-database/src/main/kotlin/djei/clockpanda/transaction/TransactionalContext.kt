package djei.clockpanda.transaction

import org.jooq.DSLContext

/**
 * A delegated [DSLContext] subtype that is only instantiated from within transactions.
 * Allows us to distinguish between transactional and non-transactional contexts.
 *
 * 1. Prevent accidental writes outside of a transaction
 * 2. Scope of transaction is clear and propagated within repositories and related classes
 */
class TransactionalContext internal constructor(ctx: DSLContext) : DSLContext by ctx
