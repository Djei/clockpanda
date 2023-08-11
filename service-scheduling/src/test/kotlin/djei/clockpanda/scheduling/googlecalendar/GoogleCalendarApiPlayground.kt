package djei.clockpanda.scheduling.googlecalendar

import djei.clockpanda.repository.UserRepository
import djei.clockpanda.scheduling.model.TimeSpan
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
    private val dsl = DSL.using(DriverManager.getConnection("jdbc:sqlite:file:../db.sqlite3"), SQLDialect.SQLITE)
    private val user = userRepository.fetchByEmail(
        ctx = dsl,
        email = ""
    )
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
