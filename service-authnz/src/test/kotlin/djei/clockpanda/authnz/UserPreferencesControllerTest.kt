package djei.clockpanda.authnz

import arrow.core.Either
import djei.clockpanda.authnz.controller.UserPreferencesController
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.testing.ClockPandaSpringBootTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import java.time.DayOfWeek

@ClockPandaSpringBootTest
class UserPreferencesControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var dslContext: DSLContext

    @Test
    fun `test putUserPreferences fails if no authenticated user`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/preferences")
                .with(csrf().asHeader())
        ).andReturn()

        assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `test putUserPreferences fails if authenticated user tries to update different user`() {
        val putUserPreferencesRequest = UserPreferencesController.PutUserPreferencesRequest(
            email = "not_my_user@email.com",
            preferences = UserFixtures.userPreferences
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/preferences")
                .with(csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", "user@email.com")
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(putUserPreferencesRequest))
        ).andReturn()

        assertThat(result.response.status).isEqualTo(403)
        val responseContent = Json.Default.decodeFromString<UserPreferencesController.PutUserPreferencesResponse>(
            result.response.contentAsString
        )
        assertThat(responseContent)
            .isInstanceOf(UserPreferencesController.PutUserPreferencesResponse.PutUserPreferencesFailResponse::class.java)
        val failureResponseContent =
            responseContent as UserPreferencesController.PutUserPreferencesResponse.PutUserPreferencesFailResponse
        assertThat(failureResponseContent.errors).containsExactly("Cannot update preferences for another user")
    }

    @Test
    fun `test putUserPreferences fails if user preferences working hours not defined for any day of the week`() {
        val preferencesWithWorkingHoursMissingSaturdayAndSunday = UserFixtures.userPreferences
            .copy(
                workingHours = UserFixtures.userPreferences.workingHours
                    .filterKeys { it != DayOfWeek.SATURDAY && it != DayOfWeek.SUNDAY }
            )
        val putUserPreferencesRequest = UserPreferencesController.PutUserPreferencesRequest(
            email = UserFixtures.userWithNoPreferences.email,
            preferences = preferencesWithWorkingHoursMissingSaturdayAndSunday
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/preferences")
                .with(csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.userWithNoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(putUserPreferencesRequest))
        ).andReturn()

        assertThat(result.response.status).isEqualTo(400)
        val responseContent = Json.Default.decodeFromString<UserPreferencesController.PutUserPreferencesResponse>(
            result.response.contentAsString
        )
        assertThat(responseContent)
            .isInstanceOf(UserPreferencesController.PutUserPreferencesResponse.PutUserPreferencesFailResponse::class.java)
        val failureResponseContent =
            responseContent as UserPreferencesController.PutUserPreferencesResponse.PutUserPreferencesFailResponse
        assertThat(failureResponseContent.errors).containsExactly(
            "Working hours not defined for SATURDAY",
            "Working hours not defined for SUNDAY"
        )
    }

    @Test
    fun `test putUserPreferences - happy path`() {
        val putUserPreferencesRequest = UserPreferencesController.PutUserPreferencesRequest(
            email = UserFixtures.userWithNoPreferences.email,
            preferences = UserFixtures.userPreferences
        )
        userRepository.create(dslContext, UserFixtures.userWithNoPreferences)

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/preferences")
                .with(csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.userWithNoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(putUserPreferencesRequest))
        ).andReturn()

        assertThat(result.response.status).isEqualTo(200)
        val responseContent = Json.Default.decodeFromString<UserPreferencesController.PutUserPreferencesResponse>(
            result.response.contentAsString
        )
        assertThat(responseContent)
            .isInstanceOf(UserPreferencesController.PutUserPreferencesResponse.PutUserPreferencesSuccessResponse::class.java)
        val successResponseContent =
            responseContent as UserPreferencesController.PutUserPreferencesResponse.PutUserPreferencesSuccessResponse
        assertThat(successResponseContent.email).isEqualTo(UserFixtures.userWithNoPreferences.email)
        val fetchAfterPutPreferencesUser = userRepository.fetchByEmail(
            dslContext,
            UserFixtures.userWithNoPreferences.email
        )
        when (fetchAfterPutPreferencesUser) {
            is Either.Left -> fail("Fetch should have succeeded")
            is Either.Right -> {
                assertThat(fetchAfterPutPreferencesUser.value?.preferences).isEqualTo(UserFixtures.userPreferences)
            }
        }
    }
}
