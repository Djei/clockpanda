package djei.clockpanda.authnz.config

import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.User
import djei.clockpanda.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.jooq.DSLContext
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.stereotype.Component

@Component
class ClockPandaAuthorizedClientRepository(
    private val userRepository: UserRepository,
    private val dslContext: DSLContext,
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

        val email = user.attributes["email"] as String
        val firstName = user.attributes["given_name"] as String
        val lastName = user.attributes["family_name"] as String

        if (userRepository.fetchByEmail(dslContext, email) == null) {
            userRepository.create(
                ctx = dslContext,
                user = User(
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
                    calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
                    googleRefreshToken = refreshToken.tokenValue,
                    metadata = null
                )
            )
        } else {
            userRepository.updateGoogleRefreshToken(
                ctx = dslContext,
                email = email,
                refreshTokenValue = refreshToken.tokenValue
            )
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
