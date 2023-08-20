package djei.clockpanda.model

import djei.clockpanda.model.fixtures.UserPersonalTaskFixtures
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserPersonalTaskTest {
    @Test
    fun `test serialization deserialization - one off`() {
        val serialized = Json.Default.encodeToString(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice)
        val deserialized = Json.Default.decodeFromString<UserPersonalTask>(serialized)

        assertThat(deserialized).isEqualTo(UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice)
    }

    @Test
    fun `test isActive`() {
        assertThat(
            UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.copy(
                metadata = UserPersonalTaskMetadata.OneOffTask(
                    oneOffTaskDurationInMinutes = 60,
                    timeRange = LocalTimeSpan(LocalTime(9, 0), LocalTime(12, 0)),
                    isHighPriority = true,
                    isTimeRangeStrict = true,
                    currentScheduledAt = null
                )
            ).isActive()
        ).isTrue()
        assertThat(
            UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.copy(
                metadata = UserPersonalTaskMetadata.OneOffTask(
                    oneOffTaskDurationInMinutes = 60,
                    timeRange = LocalTimeSpan(LocalTime(9, 0), LocalTime(12, 0)),
                    isHighPriority = true,
                    isTimeRangeStrict = true,
                    currentScheduledAt = Clock.System.now().plus(30, DateTimeUnit.MINUTE)
                )
            ).isActive()
        ).isTrue()
        assertThat(
            UserPersonalTaskFixtures.djei1OneOffDropPackageAtPostOffice.copy(
                metadata = UserPersonalTaskMetadata.OneOffTask(
                    oneOffTaskDurationInMinutes = 60,
                    timeRange = LocalTimeSpan(LocalTime(9, 0), LocalTime(12, 0)),
                    isHighPriority = true,
                    isTimeRangeStrict = true,
                    currentScheduledAt = Clock.System.now().minus(30, DateTimeUnit.MINUTE)
                )
            ).isActive()
        ).isFalse()
    }
}
