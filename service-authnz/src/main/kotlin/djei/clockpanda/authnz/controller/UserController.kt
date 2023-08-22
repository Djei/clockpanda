package djei.clockpanda.authnz.controller

import arrow.core.Either
import djei.clockpanda.authnz.model.getEmail
import djei.clockpanda.model.User
import djei.clockpanda.model.UserPreferences
import djei.clockpanda.repository.UserRepository
import djei.clockpanda.transaction.TransactionManager
import kotlinx.serialization.Serializable
import org.slf4j.Logger
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userRepository: UserRepository,
    private val transactionManager: TransactionManager,
    private val logger: Logger
) {
    @GetMapping("/user")
    fun user(@AuthenticationPrincipal principal: OAuth2User): ResponseEntity<GetUserResponse> {
        val email = principal.getEmail()
        val fetchByEmailResult = transactionManager.transaction { ctx ->
            userRepository.getByEmail(ctx, email)
        }
        return when (fetchByEmailResult) {
            is Either.Left -> {
                logger.error("Failed to fetch user by email: $email", fetchByEmailResult.value)
                ResponseEntity.internalServerError()
                    .body(GetUserResponse.GetUserFailResponse(listOf("Failed to fetch user by email. Please refresh.")))
            }

            is Either.Right -> {
                val fetchedUser = fetchByEmailResult.value
                if (fetchedUser == null) {
                    ResponseEntity.notFound().build()
                } else {
                    ResponseEntity.ok(GetUserResponse.GetUserSuccessResponse.fromUser(fetchedUser))
                }
            }
        }
    }

    @DeleteMapping("/user")
    fun deleteUser(@AuthenticationPrincipal principal: OAuth2User): ResponseEntity<DeleteUserResponse> {
        val email = principal.getEmail()
        val deleteByEmailResult = transactionManager.transaction { ctx ->
            userRepository.delete(ctx, email)
        }
        return when (deleteByEmailResult) {
            is Either.Left -> {
                logger.error("Failed to delete user by email: $email", deleteByEmailResult.value)
                ResponseEntity.internalServerError()
                    .body(DeleteUserResponse.DeleteUserFailResponse(listOf("Failed to delete user by email. Please refresh.")))
            }

            is Either.Right -> {
                ResponseEntity.ok(DeleteUserResponse.DeleteUserSuccessResponse(email))
            }
        }
    }

    @Serializable
    sealed interface GetUserResponse {
        @Serializable
        data class GetUserFailResponse(
            val errors: List<String>
        ) : GetUserResponse

        @Serializable
        data class GetUserSuccessResponse(
            val email: String,
            val firstName: String,
            val lastName: String,
            val preferences: UserPreferences?
        ) : GetUserResponse {
            companion object {
                fun fromUser(user: User) = GetUserSuccessResponse(
                    email = user.email,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    preferences = user.preferences
                )
            }
        }
    }

    @Serializable
    sealed interface DeleteUserResponse {
        @Serializable
        data class DeleteUserFailResponse(
            val errors: List<String>
        ) : DeleteUserResponse

        @Serializable
        data class DeleteUserSuccessResponse(
            val email: String,
        ) : DeleteUserResponse
    }
}
