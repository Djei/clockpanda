package djei.clockpanda.app.controller

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

@Controller
class MvcController {
    @RequestMapping("/")
    fun index(model: Model): String {
        // available timezones in America, Europe, and Asia
        val availableTimeZones = TimeZone.availableZoneIds
            .filter { it.startsWith("America") || it.startsWith("Europe") || it.startsWith("Asia") }
            .sorted()
        model.addAttribute("availableTimeZones", availableTimeZones)

        // available times with 30 minute intervals
        val availableTimes = mutableListOf<String>()
        for (hour in 0..23) {
            for (minute in 0..30 step 30) {
                availableTimes.add("%02d:%02d".format(hour, minute))
            }
        }
        model.addAttribute("availableTimes", availableTimes)

        // available days of the week
        val availableDays = DayOfWeek.values().map { it.name }
        model.addAttribute("availableDays", availableDays)

        return "index"
    }
}
