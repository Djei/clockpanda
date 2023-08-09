package djei.clockpanda.model

import djei.clockpanda.jooq.tables.records.UserRecord
import kotlinx.datetime.Clock
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
    val metadata: UserMetadata?,
    val createdAt: Instant = Clock.System.now(),
    val lastUpdatedAt: Instant? = null
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
                metadata = record.metadata?.let { UserMetadata.fromJson(it.toString(Charsets.UTF_8)) },
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
            metadata = metadata?.toJson()?.toByteArray(Charsets.UTF_8),
            createdAt = createdAt.toJavaInstant().atOffset(ZoneOffset.UTC),
            lastUpdatedAt = lastUpdatedAt?.toJavaInstant()?.atOffset(ZoneOffset.UTC)
        )
    }
}

@Serializable
sealed class UserMetadata {
    companion object {
        private val json = Json {
            classDiscriminator = "version_num"
        }

        fun fromJson(value: String) = json.decodeFromString<UserMetadata>(value)
    }

    fun toJson() = json.encodeToString(this)

    @Serializable
    @SerialName("1")
    data class Version1(
        val preferredTimeZone: TimeZone
    ) : UserMetadata()
}

enum class CalendarProvider {
    GOOGLE_CALENDAR
}

enum class CalendarConnectionStatus {
    CONNECTED,
    DISCONNECTED
}
