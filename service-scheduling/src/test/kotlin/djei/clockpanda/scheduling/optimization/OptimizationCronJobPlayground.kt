package djei.clockpanda.scheduling.optimization

import djei.clockpanda.repository.UserRepository
import djei.clockpanda.scheduling.googlecalendar.GoogleCalendarApiFacade
import djei.clockpanda.transaction.TransactionManager
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.sql.DriverManager

@Disabled
class OptimizationCronJobPlayground {

    private val userRepository = UserRepository()
    private val dsl = DSL.using(DriverManager.getConnection("jdbc:sqlite:file:../db.sqlite3?foreign_keys=on"), SQLDialect.SQLITE)
    private val googleCalendarApiFacade = GoogleCalendarApiFacade(
        googleClientId = "",
        googleClientSecret = ""
    )
    private val transactionManager = TransactionManager(dsl)

    @Test
    fun `test optimizeSchedule`() {
        val optimizationService = OptimizationService(
            solverSecondsSpentTerminationConfig = 30L,
            googleCalendarApiFacade = googleCalendarApiFacade,
            userRepository = userRepository,
            transactionManager = transactionManager,
            logger = LoggerFactory.getLogger(OptimizationService::class.java)
        )
        OptimizationCronJob(
            optimizationService = optimizationService,
            logger = LoggerFactory.getLogger(OptimizationCronJob::class.java)
        ).optimizeSchedule()
    }
}
