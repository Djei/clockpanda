package djei.clockpanda.authnz

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
class UserController {
    @GetMapping("/user")
    fun user(principal: Principal): ResponseEntity<GetUserResponse> {
        return ResponseEntity.ok(GetUserResponse(principal.name))
    }

    data class GetUserResponse(
        val name: String
    )
}
