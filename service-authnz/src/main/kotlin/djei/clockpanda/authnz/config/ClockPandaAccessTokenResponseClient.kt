package djei.clockpanda.authnz.config

import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse
import org.springframework.stereotype.Component

@Component
class ClockPandaAccessTokenResponseClient : OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
    private val defaultAuthorizationCodeTokenResponseClient = DefaultAuthorizationCodeTokenResponseClient()

    override fun getTokenResponse(authorizationGrantRequest: OAuth2AuthorizationCodeGrantRequest?): OAuth2AccessTokenResponse? {
        val tokenResponse = defaultAuthorizationCodeTokenResponseClient.getTokenResponse(authorizationGrantRequest)
            ?: return null
        validateCalendarScopes(authorizationGrantRequest, tokenResponse)
        return tokenResponse
    }

    private fun validateCalendarScopes(
        authorizationGrantRequest: OAuth2AuthorizationCodeGrantRequest?,
        tokenResponse: OAuth2AccessTokenResponse
    ) {
        when (authorizationGrantRequest?.clientRegistration?.registrationId) {
            "google" -> validateGoogleCalendarScopes(tokenResponse)
            else -> throw OAuth2AuthenticationException(
                OAuth2Error(
                    "server_error",
                    "Unsupported OAuth2 provider",
                    "https://tools.ietf.org/html/rfc6749#section-5.2"
                )
            )
        }
    }

    private fun validateGoogleCalendarScopes(tokenResponse: OAuth2AccessTokenResponse) {
        if (!tokenResponse.accessToken.scopes.contains("https://www.googleapis.com/auth/calendar.readonly")) {
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    "server_error",
                    "Missing required scope to list your calendars to determine your primary calendar",
                    "https://tools.ietf.org/html/rfc6749#section-5.2"
                )
            )
        }
        if (!tokenResponse.accessToken.scopes.contains("https://www.googleapis.com/auth/calendar.events.owned")) {
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    "server_error",
                    "Missing required scope to create events on your primary calendar",
                    "https://tools.ietf.org/html/rfc6749#section-5.2"
                )
            )
        }
    }
}
