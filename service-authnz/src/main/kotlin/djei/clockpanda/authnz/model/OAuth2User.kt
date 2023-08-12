package djei.clockpanda.authnz.model

import org.springframework.security.oauth2.core.user.OAuth2User

fun OAuth2User.getEmail(): String {
    return this.attributes["email"] as String
}
