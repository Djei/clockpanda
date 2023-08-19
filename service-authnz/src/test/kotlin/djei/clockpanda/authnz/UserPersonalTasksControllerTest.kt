package djei.clockpanda.authnz

import arrow.core.getOrElse
import arrow.core.raise.either
import djei.clockpanda.authnz.controller.UserPersonalTasksController
import djei.clockpanda.model.UserPersonalTask
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.model.fixtures.UserPersonalTaskFixtures
import djei.clockpanda.repository.UserPersonalTaskRepository
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.testing.ClockPandaSpringBootTest
import djei.clockpanda.transaction.TransactionManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders

@ClockPandaSpringBootTest
class UserPersonalTasksControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var userPersonalTaskRepository: UserPersonalTaskRepository

    @Autowired
    lateinit var transactionManager: TransactionManager

    @Test
    fun `test personalTasks fails if no authenticated user`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/user/personalTasks")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `test get personalTasks - happy path`() {
        val existingPersonalTasks = transactionManager.transaction { ctx ->
            userRepository.create(
                ctx,
                UserFixtures.djei1NoPreferences
            )
            either {
                listOf(
                    UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask,
                    UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask
                ).map {
                    userPersonalTaskRepository.upsertPersonalTask(
                        ctx,
                        it
                    ).bind()
                }
            }
        }.getOrElse { fail("this should not fail", it) }

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/user/personalTasks")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(200)
        val responseContent = Json.Default.decodeFromString<UserPersonalTasksController.ListUserPersonalTasksResponse>(
            result.response.contentAsString
        )
        Assertions.assertThat(responseContent).isInstanceOf(
            UserPersonalTasksController.ListUserPersonalTasksResponse.ListUserPersonalTasksSuccessResponse::class.java
        )
        val successResponseContent =
            responseContent as UserPersonalTasksController.ListUserPersonalTasksResponse.ListUserPersonalTasksSuccessResponse
        Assertions.assertThat(successResponseContent.userPersonalTasks).hasSize(2)
        Assertions.assertThat(successResponseContent.userPersonalTasks)
            .containsExactlyInAnyOrderElementsOf(existingPersonalTasks)
    }

    @Test
    fun `test putPersonalTasks fails if no authenticated user`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTasks")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `test putPersonalTasks fails if putting personal tasks for another user`() {
        val putUserPersonalTasksRequest = getPutUserPersonalTasksRequestFromUserPersonalTasks(
            listOf(
                UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask,
                UserPersonalTaskFixtures.djei2WeeklyFocusTimeUserPersonalTask
            )
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTasks")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(putUserPersonalTasksRequest))
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(403)
    }

    @Test
    fun `test putPersonalTasks fails if missing focus time`() {
        val putUserPersonalTasksRequest = getPutUserPersonalTasksRequestFromUserPersonalTasks(
            listOf(
                UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask
            )
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTasks")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(putUserPersonalTasksRequest))
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(400)
    }

    @Test
    fun `test putPersonalTasks fails if priorities are not unique`() {
        val putUserPersonalTasksRequest = getPutUserPersonalTasksRequestFromUserPersonalTasks(
            listOf(
                UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask,
                UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.copy(
                    priority = UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.priority
                )
            )
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTasks")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(putUserPersonalTasksRequest))
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(400)
    }

    @Test
    fun `test putPersonalTasks - happy path`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
            userPersonalTaskRepository.upsertPersonalTask(
                ctx,
                UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask
            )
        }
        val putUserPersonalTasksRequest = getPutUserPersonalTasksRequestFromUserPersonalTasks(
            listOf(
                UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask,
                UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask
            )
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTasks")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(putUserPersonalTasksRequest))
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(200)
        val responseContent = Json.Default.decodeFromString<UserPersonalTasksController.PutPersonalTasksResponse>(
            result.response.contentAsString
        )
        Assertions.assertThat(responseContent).isInstanceOf(
            UserPersonalTasksController.PutPersonalTasksResponse.PutPersonalTasksSuccessResponse::class.java
        )
        val successResponse =
            responseContent as UserPersonalTasksController.PutPersonalTasksResponse.PutPersonalTasksSuccessResponse
        Assertions.assertThat(successResponse.userPersonalTasks).hasSize(2)
        Assertions.assertThat(successResponse.userPersonalTasks[0].priority).isEqualTo(2)
        Assertions.assertThat(successResponse.userPersonalTasks[0].id)
            .isEqualTo(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.id)
        Assertions.assertThat(successResponse.userPersonalTasks[1].priority).isEqualTo(3)
        Assertions.assertThat(successResponse.userPersonalTasks[1].id)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.id)
        val retrieveAfterPutRequest = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.listByUserEmail(ctx, UserFixtures.djei1NoPreferences.email)
        }.getOrElse { fail("this should not failed", it) }
        Assertions.assertThat(retrieveAfterPutRequest).hasSize(2)
        Assertions.assertThat(retrieveAfterPutRequest[0].priority).isEqualTo(2)
        Assertions.assertThat(retrieveAfterPutRequest[0].id)
            .isEqualTo(UserPersonalTaskFixtures.djei1DailyWalkUserPersonalTask.id)
        Assertions.assertThat(retrieveAfterPutRequest[1].priority).isEqualTo(3)
        Assertions.assertThat(retrieveAfterPutRequest[1].id)
            .isEqualTo(UserPersonalTaskFixtures.djei1WeeklyFocusTimeUserPersonalTask.id)
    }

    private fun getPutUserPersonalTasksRequestFromUserPersonalTasks(userPersonalTasks: List<UserPersonalTask>): UserPersonalTasksController.PutUserPersonalTasksRequest {
        return UserPersonalTasksController.PutUserPersonalTasksRequest(
            inputs = userPersonalTasks.map {
                UserPersonalTasksController.PutUserPersonalTasksRequest.UserPersonalTaskRequestInput(
                    id = it.id,
                    userEmail = it.userEmail,
                    title = it.title,
                    description = it.description,
                    priority = it.priority,
                    metadata = it.metadata,
                    createdAt = it.createdAt,
                    lastUpdatedAt = it.lastUpdatedAt
                )
            }
        )
    }
}
