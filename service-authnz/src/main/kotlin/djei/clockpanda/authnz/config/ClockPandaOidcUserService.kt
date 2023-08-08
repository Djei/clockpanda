package djei.clockpanda.authnz.config

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.oidc.user.OidcUser

class ClockPandaOidcUserService : OAuth2UserService<OidcUserRequest, OidcUser> {
    private val defaultOidcUserService = OidcUserService()

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = defaultOidcUserService.loadUser(userRequest)
        if (!oidcUser.authorities.contains(SimpleGrantedAuthority("SCOPE_https://www.googleapis.com/auth/calendar.readonly"))) {
            OAuth2Error(
                "invalid_grant",
                "Grant is missing required scope to read your primary calendar",
                null
            )
        }
        // Register user in database if not already registered

        // Get a refresh token if we do not already have a non-expired one

        return oidcUser
    }
}
