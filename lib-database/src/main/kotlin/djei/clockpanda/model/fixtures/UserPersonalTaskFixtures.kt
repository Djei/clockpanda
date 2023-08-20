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
                timeRange = LocalTimeSpan(LocalTime(9, 0), LocalTime(12, 0)),
                isHighPriority = true,
                isTimeRangeStrict = true
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )

        val djei1OneOffDoExpensesUserPersonalTask = UserPersonalTask(
            id = UUID.randomUUID(),
            userEmail = "djei1@email.com",
            title = "do expense",
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

        val djei2OneOffReadPaperUserPersonalTask = UserPersonalTask(
            id = UUID.randomUUID(),
            userEmail = "djei2@email.com",
            title = "read paper",
            description = "description",
            metadata = UserPersonalTaskMetadata.OneOffTask(
                oneOffTaskDurationInMinutes = 60,
                timeRange = LocalTimeSpan(LocalTime(12, 0), LocalTime(17, 0)),
                isHighPriority = false,
                isTimeRangeStrict = false
            ),
            createdAt = Clock.System.now(),
            lastUpdatedAt = null
        )
    }
}
