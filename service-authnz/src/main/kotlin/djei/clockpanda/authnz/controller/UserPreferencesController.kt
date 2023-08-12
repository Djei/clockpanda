package djei.clockpanda.authnz.controller

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import djei.clockpanda.authnz.model.getEmail
import djei.clockpanda.model.UserPreferences
import djei.clockpanda.repository.UserRepository
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.slf4j.Logger
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserPreferencesController(
    private val userRepository: UserRepository,
    private val dslContext: DSLContext,
    private val logger: Logger
) {

    @PutMapping("/user/preferences")
    fun putUserPreferences(
        @AuthenticationPrincipal principal: OAuth2User,
        @RequestBody request: PutUserPreferencesRequest
    ): ResponseEntity<out PutUserPreferencesResponse> {
        return when (val requestValidationResult = validateRequest(principal, request)) {
            is Either.Left -> {
                requestValidationResult.value
            }

            is Either.Right -> {
                val updateMetadataResult = userRepository.updatePreferences(
                    ctx = dslContext,
                    email = request.email,
                    preferences = request.preferences
                )
                when (updateMetadataResult) {
                    is Either.Left -> {
                        logger.error("Failed to update user metadata", updateMetadataResult.value)
                        ResponseEntity
                            .internalServerError()
                            .body(
                                PutUserPreferencesResponse.PutUserPreferencesFailResponse(
                                    errors = listOf("Failed to update user metadata. Please retry.")
                                )
                            )
                    }

                    is Either.Right -> {
                        ResponseEntity
                            .ok(
                                PutUserPreferencesResponse.PutUserPreferencesSuccessResponse(
                                    email = request.email,
                                    userPreferences = request.preferences
                                )
                            )
                    }
                }
            }
        }
    }

    private fun validateRequest(
        principal: OAuth2User,
        request: PutUserPreferencesRequest
    ): Either<ResponseEntity<PutUserPreferencesResponse.PutUserPreferencesFailResponse>, Unit> {
        if (principal.getEmail() != request.email) {
            return ResponseEntity
                .status(HttpStatusCode.valueOf(403))
                .body(
                    PutUserPreferencesResponse.PutUserPreferencesFailResponse(
                        errors = listOf("Cannot update preferences for another user")
                    )
                )
                .left()
        }

        val inputMetadata = request.preferences
        val inputValidationErrors = mutableListOf<String>()
        // Check working hours given for all days of the week
        DayOfWeek.values().forEach { dayOfWeek ->
            if (inputMetadata.workingHours[dayOfWeek] == null) {
                inputValidationErrors.add("Working hours not defined for $dayOfWeek")
            }
        }
        return if (inputValidationErrors.isNotEmpty()) {
            ResponseEntity
                .badRequest()
                .body(
                    PutUserPreferencesResponse.PutUserPreferencesFailResponse(
                        errors = inputValidationErrors
                    )
                )
                .left()
        } else {
            Unit.right()
        }
    }

    @Serializable
    data class PutUserPreferencesRequest(
        val email: String,
        val preferences: UserPreferences
    )

    @Serializable
    sealed interface PutUserPreferencesResponse {
        @Serializable
        data class PutUserPreferencesFailResponse(
            val errors: List<String>
        ) : PutUserPreferencesResponse

        @Serializable
        data class PutUserPreferencesSuccessResponse(
            val email: String,
            val userPreferences: UserPreferences
        ) : PutUserPreferencesResponse
    }
}
