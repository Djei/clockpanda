package djei.clockpanda.model.fixtures

import djei.clockpanda.model.LocalTimeSpan
import djei.clockpanda.model.UserPersonalTask
import djei.clockpanda.model.UserPersonalTaskMetadata
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import java.util.*

class UserPersonalTaskFixtures {
    companion object {
        val djei1OneOffDropPackageAtPostOffice = UserPersonalTask(
            id = UUID.randomUUID(),
            userEmail = "djei1@email.com",
            title = "drop package at post office",
            description = "description",
            priority = 1,
            metadata = UserPersonalTaskMetadata.OneOffTask(
                oneOffTaskDurationInMinutes = 60,
                timeRange = LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0)),
                isTimeRangeStrict = true,
                isWithinWorkingHours = false
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )

        val djei1DailyWalkUserPersonalTask = UserPersonalTask(
            id = UUID.randomUUID(),
            userEmail = "djei1@email.com",
            title = "morning walk",
            description = "description",
            priority = 2,
            metadata = UserPersonalTaskMetadata.DailyTask(
                dailyTargetAmountInMinutes = 15 * 60,
                timeRange = LocalTimeSpan(LocalTime(9, 0), LocalTime(12, 0)),
                isTimeRangeStrict = false,
                isWithinWorkingHours = false
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )

        val djei1WeeklyFocusTimeUserPersonalTask = UserPersonalTask(
            id = UUID.randomUUID(),
            userEmail = "djei1@email.com",
            title = "Focus Time",
            description = "description",
            priority = 3,
            metadata = UserPersonalTaskMetadata.WeeklyTask(
                weeklyTargetAmountInMinutes = 15 * 60,
                maxInstancePerWeek = 5,
                minInstanceDurationInMinutes = 60 * 2,
                maxInstanceDurationInMinutes = 60 * 8,
                timeRange = LocalTimeSpan(LocalTime(15, 0), LocalTime(19, 0)),
                isTimeRangeStrict = false,
                isWithinWorkingHours = true
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )

        val djei2WeeklyFocusTimeUserPersonalTask = UserPersonalTask(
            id = UUID.randomUUID(),
            userEmail = "djei2@email.com",
            title = "Focus Time",
            description = "description",
            priority = 1,
            metadata = UserPersonalTaskMetadata.WeeklyTask(
                weeklyTargetAmountInMinutes = 5 * 60,
                maxInstancePerWeek = 5,
                minInstanceDurationInMinutes = 60 * 2,
                maxInstanceDurationInMinutes = 60 * 8,
                timeRange = LocalTimeSpan(LocalTime(15, 0), LocalTime(19, 0)),
                isTimeRangeStrict = false,
                isWithinWorkingHours = true
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )
    }
}
