package djei.clockpanda.authnz

import djei.clockpanda.authnz.controller.UserController
import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.User
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.testing.ClockPandaSpringBootTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@ClockPandaSpringBootTest
class UserControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var dslContext: DSLContext

    @Test
    fun `test get user - no authenticated user`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/user")
        ).andReturn()

        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `test get user - with authenticated user - not registered in Clock Panda database`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/user")
                .with(
                    oidcLogin().idToken {
                        it.claim("email", "user@email.com")
                    }
                )
        ).andReturn()

        assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `test get user - with authenticated user - happy path`() {
        val allValuesUser = User(
            email = "user@email.com",
            firstName = "user first name",
            lastName = "user last name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = UserFixtures.userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )
        userRepository.create(dslContext, allValuesUser)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/user")
                .with(
                    oidcLogin().idToken {
                        it.claim("email", "user@email.com")
                    }
                )
        ).andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val responseContent = Json.Default.decodeFromString<UserController.GetUserResponse>(
            result.response.contentAsString
        )
        assertThat(responseContent).isInstanceOf(UserController.GetUserResponse.GetUserSuccessResponse::class.java)
        val successResponseContent = responseContent as UserController.GetUserResponse.GetUserSuccessResponse
        assertThat(successResponseContent.email).isEqualTo("user@email.com")
        assertThat(successResponseContent.firstName).isEqualTo("user first name")
        assertThat(successResponseContent.lastName).isEqualTo("user last name")
        assertThat(successResponseContent.preferences).isEqualTo(UserFixtures.userPreferences)
    }
}
