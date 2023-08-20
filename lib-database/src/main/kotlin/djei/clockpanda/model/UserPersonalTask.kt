package djei.clockpanda.model

import djei.clockpanda.UUIDSerializer
import djei.clockpanda.jooq.tables.records.UserPersonalTaskRecord
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneOffset
import java.util.*

@Serializable
data class UserPersonalTask(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val userEmail: String,
    val title: String,
    val description: String,
    val metadata: UserPersonalTaskMetadata,
    val createdAt: Instant,
    val lastUpdatedAt: Instant?
) {
    companion object {
        fun fromJooqRecord(record: UserPersonalTaskRecord): UserPersonalTask {
            return UserPersonalTask(
                id = record.id!!,
                userEmail = record.userEmail!!,
                title = record.title!!,
                description = record.description ?: "",
                metadata = UserPersonalTaskMetadata.fromJooqData(record.metadata!!),
                createdAt = record.createdAt!!.toInstant().toKotlinInstant(),
                lastUpdatedAt = record.lastUpdatedAt?.toInstant()?.toKotlinInstant()
            )
        }
    }

    fun toJooqRecord(): UserPersonalTaskRecord {
        return UserPersonalTaskRecord(
            id = id,
            userEmail = userEmail,
            title = title,
            description = description,
            metadata = metadata.toJooqData(),
            createdAt = createdAt.toJavaInstant().atOffset(ZoneOffset.UTC),
            lastUpdatedAt = lastUpdatedAt?.toJavaInstant()?.atOffset(ZoneOffset.UTC)
        )
    }
}

@Serializable
sealed interface UserPersonalTaskMetadata {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }

        private fun fromJson(value: String) = json.decodeFromString<UserPersonalTaskMetadata>(value)

        fun fromJooqData(value: ByteArray) = fromJson(value.toString(Charsets.UTF_8))
    }

    private fun toJson() = json.encodeToString(this)

    fun toJooqData() = toJson().toByteArray(Charsets.UTF_8)

    /**
     * Here be dragons!!
     * Once a version is actively used, we need to be careful of existing installation and make backward compatible updates
     * If a non-backward compatible change is needed, you will need to define a new object and business logic should be able to handle all active object
     */
    @Serializable
    @SerialName("OneOff")
    data class OneOffTask(
        val oneOffTaskDurationInMinutes: Int,
        val timeRange: LocalTimeSpan,
        val isTimeRangeStrict: Boolean,
        val isHighPriority: Boolean,
        val currentScheduledAt: Instant? = null
    ) : UserPersonalTaskMetadata
}
