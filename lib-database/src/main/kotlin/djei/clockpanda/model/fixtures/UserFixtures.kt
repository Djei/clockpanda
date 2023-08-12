package djei.clockpanda.model.fixtures

import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.User
import djei.clockpanda.model.UserPreferences
import djei.clockpanda.model.WorkingHourBlock
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class UserFixtures {
    companion object {
        val workingHours = mapOf(
            DayOfWeek.MONDAY to listOf(
                WorkingHourBlock(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.TUESDAY to listOf(
                WorkingHourBlock(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.WEDNESDAY to listOf(
                WorkingHourBlock(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.THURSDAY to listOf(
                WorkingHourBlock(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.FRIDAY to listOf(
                WorkingHourBlock(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.SATURDAY to listOf(
                WorkingHourBlock(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.SUNDAY to listOf(
                WorkingHourBlock(LocalTime(9, 0), LocalTime(17, 0))
            )
        )

        val userPreferences = UserPreferences.Version1(
            preferredTimeZone = TimeZone.of("Europe/London"),
            workingHours = workingHours,
            targetFocusTimeHoursPerWeek = 20
        )

        val userWithNoPreferences = User(
            email = "djei@github.com",
            firstName = "Djei First Name",
            lastName = "Djei Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = null,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )
    }
}
