package djei.clockpanda.model

import djei.clockpanda.jooq.tables.records.UserRecord
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneOffset

data class User(
    val email: String,
    val firstName: String,
    val lastName: String,
    val calendarProvider: CalendarProvider,
    val calendarConnectionStatus: CalendarConnectionStatus,
    val googleRefreshToken: String?,
    val preferences: UserPreferences?,
    val createdAt: Instant,
    val lastUpdatedAt: Instant?
) {
    companion object {
        fun fromJooqRecord(record: UserRecord): User {
            return User(
                email = record.email!!,
                firstName = record.firstName!!,
                lastName = record.lastName!!,
                calendarProvider = CalendarProvider.valueOf(record.calendarProvider!!),
                calendarConnectionStatus = CalendarConnectionStatus.valueOf(record.calendarConnectionStatus!!),
                googleRefreshToken = record.googleRefreshToken,
                preferences = record.preferences?.let { UserPreferences.fromJooqData(it) },
                createdAt = record.createdAt!!.toInstant().toKotlinInstant(),
                lastUpdatedAt = record.lastUpdatedAt?.toInstant()?.toKotlinInstant()
            )
        }
    }

    fun toJooqRecord(): UserRecord {
        return UserRecord(
            email = email,
            firstName = firstName,
            lastName = lastName,
            calendarProvider = calendarProvider.name,
            calendarConnectionStatus = calendarConnectionStatus.name,
            googleRefreshToken = googleRefreshToken,
            preferences = preferences?.toJooqData(),
            createdAt = createdAt.toJavaInstant().atOffset(ZoneOffset.UTC),
            lastUpdatedAt = lastUpdatedAt?.toJavaInstant()?.atOffset(ZoneOffset.UTC)
        )
    }

    // Override toString to explicitly hide refresh token and avoid accidentally logging it
    override fun toString(): String {
        return "User(email='$email', calendarProvider=$calendarProvider, calendarConnectionStatus=$calendarConnectionStatus, preferences=$preferences, createdAt=$createdAt, lastUpdatedAt=$lastUpdatedAt)"
    }
}

@Serializable
sealed interface UserPreferences {
    companion object {
        private val json = Json {
            classDiscriminator = "version_num"
        }

        private fun fromJson(value: String) = json.decodeFromString<UserPreferences>(value)

        fun fromJooqData(value: ByteArray) = fromJson(value.toString(Charsets.UTF_8))
    }

    private fun toJson() = json.encodeToString(this)

    fun toJooqData() = toJson().toByteArray(Charsets.UTF_8)

    val preferredTimeZone: TimeZone
    val workingHours: WorkingHours
    val targetFocusTimeHoursPerWeek: Int
    val preferredFocusTimeRange: LocalTimeSpan

    /**
     * Here be dragons!!
     * Once a version is actively used, we need to be careful of existing installation and make backward compatible updates
     * If a non-backward compatible change is needed, you will need to define a new version and business logic should be able to handle all active versions
     */
    @Serializable
    @SerialName("1")
    data class Version1(
        override val preferredTimeZone: TimeZone,
        override val workingHours: WorkingHours,
        override val targetFocusTimeHoursPerWeek: Int,
        override val preferredFocusTimeRange: LocalTimeSpan
    ) : UserPreferences
}

enum class CalendarProvider {
    GOOGLE_CALENDAR
}

enum class CalendarConnectionStatus {
    CONNECTED,
    DISCONNECTED
}
