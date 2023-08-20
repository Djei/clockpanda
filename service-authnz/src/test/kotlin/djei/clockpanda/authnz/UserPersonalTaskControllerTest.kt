package djei.clockpanda.authnz

import arrow.core.getOrElse
import arrow.core.raise.either
import djei.clockpanda.authnz.controller.UserPersonalTaskController
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
class UserPersonalTaskControllerTest {
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
            MockMvcRequestBuilders.get("/user/personalTask")
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
                    UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice,
                    UserPersonalTaskFixtures.djei1WeeklySpreadFocusTimeUserPersonalTask
                ).map {
                    userPersonalTaskRepository.upsertPersonalTask(
                        ctx,
                        it
                    ).bind()
                }
            }
        }.getOrElse { fail("this should not fail", it) }

        val result = mockMvc.perform(
            MockMvcRequestBuilders.get("/user/personalTask")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(200)
        val responseContent = Json.Default.decodeFromString<UserPersonalTaskController.ListUserPersonalTasksResponse>(
            result.response.contentAsString
        )
        Assertions.assertThat(responseContent).isInstanceOf(
            UserPersonalTaskController.ListUserPersonalTasksResponse.ListUserPersonalTasksSuccessResponse::class.java
        )
        val successResponseContent =
            responseContent as UserPersonalTaskController.ListUserPersonalTasksResponse.ListUserPersonalTasksSuccessResponse
        Assertions.assertThat(successResponseContent.userPersonalTasks).hasSize(2)
        Assertions.assertThat(successResponseContent.userPersonalTasks)
            .containsExactlyInAnyOrderElementsOf(existingPersonalTasks)
    }

    @Test
    fun `test putPersonalTask fails if no authenticated user`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTask")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `test putPersonalTask fails if putting personal tasks for another user`() {
        val putUserPersonalTasksRequest = UserPersonalTaskController.PutUserPersonalTaskRequest(
            email = UserPersonalTaskFixtures.djei2WeeklySpreadFocusTimeUserPersonalTask.userEmail,
            title = UserPersonalTaskFixtures.djei2WeeklySpreadFocusTimeUserPersonalTask.title,
            description = UserPersonalTaskFixtures.djei2WeeklySpreadFocusTimeUserPersonalTask.description,
            metadata = UserPersonalTaskFixtures.djei2WeeklySpreadFocusTimeUserPersonalTask.metadata,
            createdAt = UserPersonalTaskFixtures.djei2WeeklySpreadFocusTimeUserPersonalTask.createdAt,
            lastUpdatedAt = UserPersonalTaskFixtures.djei2WeeklySpreadFocusTimeUserPersonalTask.lastUpdatedAt
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTask")
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
    fun `test putPersonalTask fails if cannot find personal task to update`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
        }
        val putUserPersonalTasksRequest = UserPersonalTaskController.PutUserPersonalTaskRequest(
            id = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.id,
            email = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.userEmail,
            title = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.title,
            description = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.description,
            metadata = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.metadata,
            createdAt = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.createdAt,
            lastUpdatedAt = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.lastUpdatedAt
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTask")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(putUserPersonalTasksRequest))
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `test putPersonalTask - happy path`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
            userPersonalTaskRepository.upsertPersonalTask(
                ctx,
                UserPersonalTaskFixtures.djei1WeeklySpreadFocusTimeUserPersonalTask
            )
        }
        val putUserPersonalTasksRequest = UserPersonalTaskController.PutUserPersonalTaskRequest(
            id = null,
            email = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.userEmail,
            title = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.title,
            description = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.description,
            metadata = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.metadata,
            createdAt = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.createdAt,
            lastUpdatedAt = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.lastUpdatedAt
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.put("/user/personalTask")
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
        val responseContent = Json.Default.decodeFromString<UserPersonalTaskController.PutPersonalTaskResponse>(
            result.response.contentAsString
        )
        Assertions.assertThat(responseContent).isInstanceOf(
            UserPersonalTaskController.PutPersonalTaskResponse.PutPersonalTaskSuccessResponse::class.java
        )
        val successResponse =
            responseContent as UserPersonalTaskController.PutPersonalTaskResponse.PutPersonalTaskSuccessResponse
        val getAfterPutRequest = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, successResponse.userPersonalTask.id)
        }.getOrElse { fail("this should not failed", it) }
        Assertions.assertThat(getAfterPutRequest).isNotNull
        val listAfterPutRequest = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.listByUserEmail(ctx, UserFixtures.djei1NoPreferences.email)
        }.getOrElse { fail("this should not failed", it) }
        Assertions.assertThat(listAfterPutRequest).hasSize(2)
        Assertions.assertThat(listAfterPutRequest.map { it.id }).contains(
            successResponse.userPersonalTask.id,
            UserPersonalTaskFixtures.djei1WeeklySpreadFocusTimeUserPersonalTask.id
        )
    }

    @Test
    fun `test deletePersonalTask fails if no authenticated user`() {
        val result = mockMvc.perform(
            MockMvcRequestBuilders.delete("/user/personalTask")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(401)
    }

    @Test
    fun `test deletePersonalTask fails if cannot find personal task to delete`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
        }
        val deleteUserPersonalTasksRequest = UserPersonalTaskController.DeleteUserPersonalTaskRequest(
            id = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.id,
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.delete("/user/personalTask")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(deleteUserPersonalTasksRequest))
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(404)
    }

    @Test
    fun `test deletePersonalTask fails if deleting personal task of another user`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
            userRepository.create(ctx, UserFixtures.djei2WithPreferences)
            userPersonalTaskRepository.upsertPersonalTask(
                ctx,
                UserPersonalTaskFixtures.djei2WeeklySpreadFocusTimeUserPersonalTask
            )
        }
        val deleteUserPersonalTasksRequest = UserPersonalTaskController.DeleteUserPersonalTaskRequest(
            id = UserPersonalTaskFixtures.djei2WeeklySpreadFocusTimeUserPersonalTask.id,
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.delete("/user/personalTask")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(deleteUserPersonalTasksRequest))
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(403)
    }

    @Test
    fun `test deletePersonalTask - happy path`() {
        transactionManager.transaction { ctx ->
            userRepository.create(ctx, UserFixtures.djei1NoPreferences)
            userPersonalTaskRepository.upsertPersonalTask(
                ctx,
                UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice
            )
        }
        val deleteUserPersonalTasksRequest = UserPersonalTaskController.DeleteUserPersonalTaskRequest(
            id = UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.id,
        )

        val result = mockMvc.perform(
            MockMvcRequestBuilders.delete("/user/personalTask")
                .with(SecurityMockMvcRequestPostProcessors.csrf().asHeader())
                .with(
                    SecurityMockMvcRequestPostProcessors.oidcLogin().idToken {
                        it.claim("email", UserFixtures.djei1NoPreferences.email)
                    }
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(Json.Default.encodeToString(deleteUserPersonalTasksRequest))
        ).andReturn()

        Assertions.assertThat(result.response.status).isEqualTo(200)
        val responseContent = Json.Default.decodeFromString<UserPersonalTaskController.DeletePersonalTaskResponse>(
            result.response.contentAsString
        )
        Assertions.assertThat(responseContent).isInstanceOf(
            UserPersonalTaskController.DeletePersonalTaskResponse.DeletePersonalTaskSuccessResponse::class.java
        )
        val successResponse =
            responseContent as UserPersonalTaskController.DeletePersonalTaskResponse.DeletePersonalTaskSuccessResponse
        val getAfterDeleteRequest = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, successResponse.id)
        }.getOrElse { fail("this should not failed", it) }
        Assertions.assertThat(getAfterDeleteRequest).isNull()
    }
}
