package djei.clockpanda.model.fixtures

import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.LocalTimeSpan
import djei.clockpanda.model.User
import djei.clockpanda.model.UserPreferences
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

class UserFixtures {
    companion object {
        val workingHours = mapOf(
            DayOfWeek.MONDAY to listOf(
                LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.TUESDAY to listOf(
                LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.WEDNESDAY to listOf(
                LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.THURSDAY to listOf(
                LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.FRIDAY to listOf(
                LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.SATURDAY to listOf(
                LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0))
            ),
            DayOfWeek.SUNDAY to listOf(
                LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0))
            )
        )

        val userPreferences = UserPreferences.Version1(
            preferredTimeZone = TimeZone.of("Europe/London"),
            workingHours = workingHours,
            targetFocusTimeHoursPerWeek = 20,
            preferredFocusTimeRange = LocalTimeSpan(LocalTime(14, 0), LocalTime(17, 0))
        )

        val djei1NoPreferences = User(
            email = "djei1@email.com",
            firstName = "Djei 1 First Name",
            lastName = "Djei 1 Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = null,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )

        val djei2WithPreferences = User(
            email = "djei2@email.com",
            firstName = "Djei 2 First Name",
            lastName = "Djei 2 Last Name",
            calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
            calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
            googleRefreshToken = "refresh-token",
            preferences = userPreferences,
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now()
        )
    }
}
