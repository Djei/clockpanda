package djei.clockpanda.repository

import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.User
import djei.clockpanda.model.UserMetadata
import djei.clockpanda.testing.ClockPandaSpringBootTest
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@ClockPandaSpringBootTest
class UserRepositoryTest {
    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var dslContext: DSLContext

    @Test
    fun `createUser should create a user`() {
        val result = userRepository.createUser(
            dslContext,
            User(
                email = "djei@github.com",
                firstName = "Djei First Name",
                lastName = "Djei Last Name",
                calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
                googleRefreshToken = "refresh-token",
                metadata = UserMetadata.Version1(
                    preferredTimeZone = TimeZone.of("Europe/London")
                ),
                createdAt = Clock.System.now(),
                lastUpdatedAt = null
            )
        )

        assertThat(result).isNotNull
        val fetchAfterCreate = userRepository.fetchByEmail(dslContext, "djei@github.com")
        assertThat(fetchAfterCreate).isNotNull
        assertThat(fetchAfterCreate!!.calendarProvider).isEqualTo(CalendarProvider.GOOGLE_CALENDAR)
        assertThat(fetchAfterCreate.metadata).isInstanceOf(UserMetadata.Version1::class.java)
        val metadataV1 = fetchAfterCreate.metadata as UserMetadata.Version1
        assertThat(metadataV1.preferredTimeZone).isEqualTo(TimeZone.of("Europe/London"))
    }
}
