package djei.clockpanda.authnz.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component

@Component
class ClockPandaAuthorizationRequestResolver(
    clientRegistrationRepository: ClientRegistrationRepository
) : OAuth2AuthorizationRequestResolver {
    val defaultAuthorizationRequestResolver = DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository,
        "/oauth2/authorization"
    )

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        val defaultAuthorizationRequest = this.defaultAuthorizationRequestResolver.resolve(request) ?: return null

        return customAuthorizationRequest(defaultAuthorizationRequest)
    }

    override fun resolve(request: HttpServletRequest, clientRegistrationId: String): OAuth2AuthorizationRequest? {
        val defaultAuthorizationRequest = this.defaultAuthorizationRequestResolver.resolve(
            request,
            clientRegistrationId
        ) ?: return null

        return customAuthorizationRequest(defaultAuthorizationRequest)
    }

    private fun customAuthorizationRequest(
        defaultAuthorizationRequest: OAuth2AuthorizationRequest
    ): OAuth2AuthorizationRequest {
        val additionalParameters = defaultAuthorizationRequest.additionalParameters.toMutableMap()
        additionalParameters["access_type"] = "offline"

        return OAuth2AuthorizationRequest.from(defaultAuthorizationRequest)
            .additionalParameters(additionalParameters)
            .build()
    }
}
