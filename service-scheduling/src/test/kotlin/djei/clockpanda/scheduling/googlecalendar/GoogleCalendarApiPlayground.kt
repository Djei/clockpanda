package djei.clockpanda.scheduling.googlecalendar

import djei.clockpanda.repository.UserRepository
import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.transaction.TransactionManager
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.sql.DriverManager

@Disabled
class GoogleCalendarApiPlayground {

    private val userRepository = UserRepository()
    private val dsl = DSL.using(DriverManager.getConnection("jdbc:sqlite:file:../db.sqlite3?foreign_keys=on"), SQLDialect.SQLITE)
    private val transactionManager = TransactionManager(dsl)
    private val user = transactionManager.transaction { ctx ->
        userRepository.getByEmail(
            ctx = ctx,
            email = ""
        )
    }
    private val googleCalendarApiFacade = GoogleCalendarApiFacade(
        googleClientId = "",
        googleClientSecret = ""
    )

    @Test
    fun `test list calendar events`() {
        val result = user.map {
            val rangeStart = Clock.System.now()
            googleCalendarApiFacade.listCalendarEvents(
                user = it!!,
                range = TimeSpan(rangeStart, rangeStart.plus(24 * 14, DateTimeUnit.HOUR))
            )
        }

        println(result)
    }
}
