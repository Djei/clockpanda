package djei.clockpanda.authnz.controller

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import djei.clockpanda.UUIDSerializer
import djei.clockpanda.authnz.model.getEmail
import djei.clockpanda.model.UserPersonalTask
import djei.clockpanda.model.UserPersonalTaskMetadata
import djei.clockpanda.repository.UserPersonalTaskRepository
import djei.clockpanda.transaction.TransactionManager
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class UserPersonalTaskController(
    private val userPersonalTaskRepository: UserPersonalTaskRepository,
    private val transactionManager: TransactionManager,
    private val logger: Logger
) {

    @GetMapping("/user/personalTask")
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

    @PutMapping("/user/personalTask")
    fun putUserPersonalTask(
        @AuthenticationPrincipal principal: OAuth2User,
        @RequestBody request: PutUserPersonalTaskRequest
    ): ResponseEntity<out PutPersonalTaskResponse> {
        return when (val requestValidationResult = validatePutPersonalTaskRequest(principal, request)) {
            is Either.Left -> {
                requestValidationResult.value
            }

            is Either.Right -> {
                val upsertResult = transactionManager.transaction { ctx ->
                    userPersonalTaskRepository.upsertPersonalTask(
                        ctx = ctx,
                        userPersonalTask = request.toUserPersonalTask()
                    )
                }
                when (upsertResult) {
                    is Either.Left -> {
                        logger.error("Failed to upsert personal tasks", upsertResult.value)
                        ResponseEntity
                            .internalServerError()
                            .body(
                                PutPersonalTaskResponse.PutPersonalTaskFailResponse(
                                    errors = listOf("Failed to save personal tasks. Please retry.")
                                )
                            )
                    }

                    is Either.Right -> {
                        ResponseEntity
                            .ok(
                                PutPersonalTaskResponse.PutPersonalTaskSuccessResponse(
                                    userPersonalTask = upsertResult.value
                                )
                            )
                    }
                }
            }
        }
    }

    @DeleteMapping("/user/personalTask")
    fun deleteUserPersonalTask(
        @AuthenticationPrincipal principal: OAuth2User,
        @RequestBody request: DeleteUserPersonalTaskRequest
    ): ResponseEntity<out DeletePersonalTaskResponse> {
        return when (val requestValidationResult = validateDeletePersonalTaskRequest(principal, request)) {
            is Either.Left -> {
                requestValidationResult.value
            }

            is Either.Right -> {
                val deleteResult = transactionManager.transaction { ctx ->
                    userPersonalTaskRepository.deletePersonalTask(ctx, request.id)
                }
                when (deleteResult) {
                    is Either.Left -> {
                        logger.error("Failed to delete personal task", deleteResult.value)
                        ResponseEntity
                            .internalServerError()
                            .body(
                                DeletePersonalTaskResponse.DeletePersonalTaskFailResponse(
                                    errors = listOf("Failed to delete personal task. Please retry.")
                                )
                            )
                    }

                    is Either.Right -> {
                        ResponseEntity
                            .ok(
                                DeletePersonalTaskResponse.DeletePersonalTaskSuccessResponse(
                                    id = request.id
                                )
                            )
                    }
                }
            }
        }
    }

    private fun validatePutPersonalTaskRequest(
        principal: OAuth2User,
        request: PutUserPersonalTaskRequest
    ): Either<ResponseEntity<PutPersonalTaskResponse.PutPersonalTaskFailResponse>, Unit> {
        val taskEmail = if (request.id == null) {
            request.email
        } else {
            transactionManager.transaction { ctx ->
                userPersonalTaskRepository.getById(ctx, request.id)
            }.getOrElse {
                return ResponseEntity
                    .status(HttpStatusCode.valueOf(500))
                    .body(
                        PutPersonalTaskResponse.PutPersonalTaskFailResponse(
                            errors = listOf("Failed to update task with id ${request.id}")
                        )
                    )
                    .left()
            }?.userEmail ?: run {
                return ResponseEntity
                    .status(HttpStatusCode.valueOf(404))
                    .body(
                        PutPersonalTaskResponse.PutPersonalTaskFailResponse(
                            errors = listOf("Cannot find personal task with id ${request.id}")
                        )
                    )
                    .left()
            }
        }
        if (taskEmail != principal.getEmail()) {
            return ResponseEntity
                .status(HttpStatusCode.valueOf(403))
                .body(
                    PutPersonalTaskResponse.PutPersonalTaskFailResponse(
                        errors = listOf("Cannot update personal task for another user")
                    )
                )
                .left()
        }
        return Unit.right()
    }

    private fun validateDeletePersonalTaskRequest(
        principal: OAuth2User,
        request: DeleteUserPersonalTaskRequest
    ): Either<ResponseEntity<DeletePersonalTaskResponse.DeletePersonalTaskFailResponse>, Unit> {
        val taskEmail = transactionManager.transaction { ctx ->
            userPersonalTaskRepository.getById(ctx, request.id)
        }.getOrElse {
            return ResponseEntity
                .status(HttpStatusCode.valueOf(500))
                .body(
                    DeletePersonalTaskResponse.DeletePersonalTaskFailResponse(
                        errors = listOf("Failed to delete task with id ${request.id}")
                    )
                )
                .left()
        }?.userEmail ?: run {
            return ResponseEntity
                .status(HttpStatusCode.valueOf(404))
                .body(
                    DeletePersonalTaskResponse.DeletePersonalTaskFailResponse(
                        errors = listOf("Cannot find personal task with id ${request.id}")
                    )
                )
                .left()
        }
        if (taskEmail != principal.getEmail()) {
            return ResponseEntity
                .status(HttpStatusCode.valueOf(403))
                .body(
                    DeletePersonalTaskResponse.DeletePersonalTaskFailResponse(
                        errors = listOf("Cannot delete personal task ${request.id} that belongs to another user")
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
    data class PutUserPersonalTaskRequest(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID? = null,
        val email: String,
        val title: String,
        val description: String,
        val metadata: UserPersonalTaskMetadata,
        val createdAt: Instant,
        val lastUpdatedAt: Instant? = null
    ) {
        fun toUserPersonalTask() = UserPersonalTask(
            id = id ?: UUID.randomUUID(),
            userEmail = email,
            title = title,
            description = description,
            metadata = metadata,
            createdAt = createdAt,
            lastUpdatedAt = lastUpdatedAt
        )
    }

    @Serializable
    sealed interface PutPersonalTaskResponse {
        @Serializable
        data class PutPersonalTaskFailResponse(
            val errors: List<String>
        ) : PutPersonalTaskResponse

        @Serializable
        data class PutPersonalTaskSuccessResponse(
            val userPersonalTask: UserPersonalTask
        ) : PutPersonalTaskResponse
    }

    @Serializable
    data class DeleteUserPersonalTaskRequest(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID
    )

    @Serializable
    sealed interface DeletePersonalTaskResponse {
        @Serializable
        data class DeletePersonalTaskFailResponse(
            val errors: List<String>
        ) : DeletePersonalTaskResponse

        @Serializable
        data class DeletePersonalTaskSuccessResponse(
            @Serializable(with = UUIDSerializer::class)
            val id: UUID
        ) : DeletePersonalTaskResponse
    }
}
