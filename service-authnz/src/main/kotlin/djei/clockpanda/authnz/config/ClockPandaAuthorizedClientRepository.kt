package djei.clockpanda.authnz.config

import arrow.core.Either
import djei.clockpanda.authnz.model.getEmail
import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.User
import djei.clockpanda.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.jooq.DSLContext
import org.slf4j.Logger
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.stereotype.Component

@Component
class ClockPandaAuthorizedClientRepository(
    private val userRepository: UserRepository,
    private val dslContext: DSLContext,
    private val logger: Logger,
    authorizedClientService: OAuth2AuthorizedClientService,
) : OAuth2AuthorizedClientRepository {
    private val defaultOAuth2AuthorizedClientRepository = AuthenticatedPrincipalOAuth2AuthorizedClientRepository(
        authorizedClientService
    )

    override fun <T : OAuth2AuthorizedClient?> loadAuthorizedClient(
        clientRegistrationId: String?,
        principal: Authentication?,
        request: HttpServletRequest?
    ): T {
        return defaultOAuth2AuthorizedClientRepository.loadAuthorizedClient<T>(
            clientRegistrationId,
            principal,
            request
        )
    }

    override fun saveAuthorizedClient(
        authorizedClient: OAuth2AuthorizedClient?,
        principal: Authentication?,
        request: HttpServletRequest?,
        response: HttpServletResponse?
    ) {
        defaultOAuth2AuthorizedClientRepository.saveAuthorizedClient(
            authorizedClient,
            principal,
            request,
            response
        )
        registerClockPandaUser(principal, authorizedClient)
    }

    private fun registerClockPandaUser(
        principal: Authentication?,
        authorizedClient: OAuth2AuthorizedClient?
    ) {
        val refreshToken = authorizedClient?.refreshToken ?: throw OAuth2AuthenticationException(
            OAuth2Error(
                "server_error",
                "Refresh token not received from authorization flow",
                "https://tools.ietf.org/html/rfc6749#section-5.2"
            )
        )
        val user = (principal as OAuth2AuthenticationToken).principal
        val isEmailVerified = user.attributes["email_verified"] as Boolean
        if (!isEmailVerified) {
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    "server_error",
                    "Email not verified",
                    "https://tools.ietf.org/html/rfc6749#section-5.2"
                )
            )
        }

        val email = user.getEmail()
        val firstName = user.attributes["given_name"] as String
        val lastName = user.attributes["family_name"] as String

        when (val fetchExistingUserResult = userRepository.fetchByEmail(dslContext, email)) {
            is Either.Left -> {
                logger.error("Error fetching user by email", fetchExistingUserResult.value)
                throw OAuth2AuthenticationException(
                    OAuth2Error(
                        "server_error",
                        "",
                        "https://tools.ietf.org/html/rfc6749#section-5.2"
                    )
                )
            }
            is Either.Right -> {
                registerUser(fetchExistingUserResult.value, email, firstName, lastName, refreshToken)
            }
        }
    }

    private fun registerUser(
        user: User?,
        email: String,
        firstName: String,
        lastName: String,
        refreshToken: OAuth2RefreshToken
    ) {
        val registrationResult = if (user == null) {
            userRepository.create(
                ctx = dslContext,
                user = User(
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                    calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
                    googleRefreshToken = refreshToken.tokenValue,
                    preferences = null
                )
            )
        } else {
            userRepository.updateGoogleRefreshToken(
                ctx = dslContext,
                email = email,
                refreshTokenValue = refreshToken.tokenValue
            )
        }
        when (registrationResult) {
            is Either.Left -> {
                logger.error("Error fetching user by email", registrationResult.leftOrNull())
                throw OAuth2AuthenticationException(
                    OAuth2Error(
                        "server_error",
                        "Failed to register user in user repository",
                        "https://tools.ietf.org/html/rfc6749#section-5.2"
                    )
                )
            }
            is Either.Right -> {
                logger.info("Successfully registered user with email $email")
            }
        }
    }

    override fun removeAuthorizedClient(
        clientRegistrationId: String?,
        principal: Authentication?,
        request: HttpServletRequest?,
        response: HttpServletResponse?
    ) {
        defaultOAuth2AuthorizedClientRepository.removeAuthorizedClient(
            clientRegistrationId,
            principal,
            request,
            response
        )
    }
}
