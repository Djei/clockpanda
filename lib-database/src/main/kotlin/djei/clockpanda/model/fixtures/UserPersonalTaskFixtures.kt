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
            metadata = UserPersonalTaskMetadata.OneOffTask(
                oneOffTaskDurationInMinutes = 60,
                timeRange = LocalTimeSpan(LocalTime(9, 0), LocalTime(17, 0)),
                isHighPriority = true,
                isTimeRangeStrict = false
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )

        val djei1WeeklySpreadFocusTimeUserPersonalTask = UserPersonalTask(
            id = UUID.randomUUID(),
            userEmail = "djei1@email.com",
            title = "Focus Time",
            description = "description",
            metadata = UserPersonalTaskMetadata.WeeklySpreadTask(
                weeklyTargetAmountInMinutes = 15 * 60,
                minInstanceDurationInMinutes = 60 * 2,
                maxInstanceDurationInMinutes = 60 * 8,
                timeRange = LocalTimeSpan(LocalTime(15, 0), LocalTime(19, 0)),
                isTimeRangeStrict = false
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )

        val djei2WeeklySpreadFocusTimeUserPersonalTask = UserPersonalTask(
            id = UUID.randomUUID(),
            userEmail = "djei2@email.com",
            title = "Focus Time",
            description = "description",
            metadata = UserPersonalTaskMetadata.WeeklySpreadTask(
                weeklyTargetAmountInMinutes = 5 * 60,
                minInstanceDurationInMinutes = 60 * 2,
                maxInstanceDurationInMinutes = 60 * 8,
                timeRange = LocalTimeSpan(LocalTime(15, 0), LocalTime(19, 0)),
                isTimeRangeStrict = false
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )
    }
}
