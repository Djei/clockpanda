package djei.clockpanda.model

import djei.clockpanda.model.fixtures.UserPersonalTaskFixtures
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
    fun `test serialization deserialization - weekly spread`() {
        val serialized = Json.Default.encodeToString(
            UserPersonalTaskFixtures.djei1WeeklySpreadFocusTimeUserPersonalTask
        )
        val deserialized = Json.Default.decodeFromString<UserPersonalTask>(serialized)

        assertThat(deserialized).isEqualTo(UserPersonalTaskFixtures.djei1WeeklySpreadFocusTimeUserPersonalTask)
    }
}
