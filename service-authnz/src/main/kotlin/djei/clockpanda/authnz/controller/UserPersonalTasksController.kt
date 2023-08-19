package djei.clockpanda.authnz.controller

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import djei.clockpanda.UUIDSerializer
import djei.clockpanda.authnz.model.getEmail
import djei.clockpanda.model.UserPersonalTask
import djei.clockpanda.model.UserPersonalTaskMetadata
import djei.clockpanda.repository.UserPersonalTaskRepository
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.transaction.TransactionManager
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class UserPersonalTasksController(
    private val userRepository: UserRepository,
    private val userPersonalTaskRepository: UserPersonalTaskRepository,
    private val transactionManager: TransactionManager,
    private val logger: Logger
) {

    @GetMapping("/user/personalTasks")
    fun userPersonalTasks(@AuthenticationPrincipal principal: OAuth2User): ResponseEntity<ListUserPersonalTasksResponse> {
        val email = principal.getEmail()
        val listUserPersonalTasksResult = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.listByUserEmail(ctx, email)
        }
        return when (listUserPersonalTasksResult) {
            is Either.Left -> {
                logger.error("Failed to list personal tasks by user email: $email", listUserPersonalTasksResult.value)
                ResponseEntity.internalServerError()
                    .body(ListUserPersonalTasksResponse.ListUserPersonalTasksFailResponse(listOf("Failed to list personal tasks by user email. Please refresh.")))
            }

            is Either.Right -> {
                ResponseEntity.ok(
                    ListUserPersonalTasksResponse.ListUserPersonalTasksSuccessResponse.from(
                        userPersonalTasks = listUserPersonalTasksResult.value
                    )
                )
            }
        }
    }

    @PutMapping("/user/personalTasks")
    fun putUserPersonalTasks(
        @AuthenticationPrincipal principal: OAuth2User,
        @RequestBody request: PutUserPersonalTasksRequest
    ): ResponseEntity<out PutPersonalTasksResponse> {
        return when (val requestValidationResult = validatePutPersonalTasksRequest(principal, request)) {
            is Either.Left -> {
                requestValidationResult.value
            }

            is Either.Right -> {
                val upsertResult = transactionManager.transaction { ctx ->
                    either {
                        request.inputs.map { personalTaskInput ->
                            userPersonalTaskRepository.upsertPersonalTask(
                                ctx = ctx,
                                userPersonalTask = personalTaskInput.toUserPersonalTask()
                            ).bind()
                        }
                    }
                }
                when (upsertResult) {
                    is Either.Left -> {
                        logger.error("Failed to upsert personal tasks", upsertResult.value)
                        ResponseEntity
                            .internalServerError()
                            .body(
                                PutPersonalTasksResponse.PutPersonalTasksFailResponse(
                                    errors = listOf("Failed to save personal tasks. Please retry.")
                                )
                            )
                    }

                    is Either.Right -> {
                        ResponseEntity
                            .ok(
                                PutPersonalTasksResponse.PutPersonalTasksSuccessResponse.from(
                                    userPersonalTasks = upsertResult.value
                                )
                            )
                    }
                }
            }
        }
    }

    private fun validatePutPersonalTasksRequest(
        principal: OAuth2User,
        request: PutUserPersonalTasksRequest
    ): Either<ResponseEntity<PutPersonalTasksResponse.PutPersonalTasksFailResponse>, Unit> {
        if (request.inputs.any { it.userEmail != principal.getEmail() }) {
            return ResponseEntity
                .status(HttpStatusCode.valueOf(403))
                .body(
                    PutPersonalTasksResponse.PutPersonalTasksFailResponse(
                        errors = listOf("Cannot update personalTasks for another user")
                    )
                )
                .left()
        }
        // Focus Time is a special personal task that should be unique and always be there
        if (request.inputs.filter { it.title == "Focus Time" }.size != 1) {
            return ResponseEntity
                .badRequest()
                .body(
                    PutPersonalTasksResponse.PutPersonalTasksFailResponse(
                        errors = listOf("Focus Time is a special personal task that should always be defined for user")
                    )
                )
                .left()
        }
        // Check that task priorities are unique
        val priorities = request.inputs.map { it.priority }
        if (priorities.size != priorities.toSet().size) {
            return ResponseEntity
                .badRequest()
                .body(
                    PutPersonalTasksResponse.PutPersonalTasksFailResponse(
                        errors = listOf("Personal task priorities should be unique")
                    )
                )
                .left()
        }
        return Unit.right()
    }

    @Serializable
    sealed interface ListUserPersonalTasksResponse {
        @Serializable
        data class ListUserPersonalTasksFailResponse(
            val errors: List<String>
        ) : ListUserPersonalTasksResponse

        @Serializable
        data class ListUserPersonalTasksSuccessResponse(
            val userPersonalTasks: List<UserPersonalTask>
        ) : ListUserPersonalTasksResponse {
            companion object {
                fun from(userPersonalTasks: List<UserPersonalTask>) = ListUserPersonalTasksSuccessResponse(
                    userPersonalTasks = userPersonalTasks
                )
            }
        }
    }

    @Serializable
    data class PutUserPersonalTasksRequest(
        val inputs: List<UserPersonalTaskRequestInput>
    ) {
        @Serializable
        data class UserPersonalTaskRequestInput(
            @Serializable(with = UUIDSerializer::class)
            val id: UUID? = null,
            val userEmail: String,
            val title: String,
            val description: String,
            val priority: Int,
            val metadata: UserPersonalTaskMetadata,
            val createdAt: Instant,
            val lastUpdatedAt: Instant? = null
        ) {
            fun toUserPersonalTask() = UserPersonalTask(
                id = id ?: UUID.randomUUID(),
                userEmail = userEmail,
                title = title,
                description = description,
                priority = priority,
                metadata = metadata,
                createdAt = createdAt,
                lastUpdatedAt = lastUpdatedAt
            )
        }
    }

    @Serializable
    sealed interface PutPersonalTasksResponse {
        @Serializable
        data class PutPersonalTasksFailResponse(
            val errors: List<String>
        ) : PutPersonalTasksResponse

        @Serializable
        data class PutPersonalTasksSuccessResponse(
            val userPersonalTasks: List<UserPersonalTask>
        ) : PutPersonalTasksResponse {
            companion object {
                fun from(userPersonalTasks: List<UserPersonalTask>) = PutPersonalTasksSuccessResponse(
                    userPersonalTasks = userPersonalTasks
                )
            }
        }
    }
}
