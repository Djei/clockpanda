package djei.clockpanda.app.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class MvcController {
    @RequestMapping("/")
    fun index(): String {
        return "index"
    }
}