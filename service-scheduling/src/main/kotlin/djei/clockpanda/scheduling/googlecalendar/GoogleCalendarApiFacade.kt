package djei.clockpanda.scheduling.googlecalendar

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventAttendee
import com.google.api.services.calendar.model.EventDateTime
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.OAuth2Credentials
import djei.clockpanda.model.User
import djei.clockpanda.scheduling.model.CalendarEvent
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaInstant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class GoogleCalendarApiFacade(
    @Value("\${spring.security.oauth2.client.registration.google.client_id}")
    private val googleClientId: String,
    @Value("\${spring.security.oauth2.client.registration.google.client_secret}")
    private val googleClientSecret: String
) {
    companion object {
        val ACCESS_TOKEN_CACHE = mutableMapOf<String, AccessToken>()
        const val EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_TYPE_KEY = "ClockPandaEventType"
        const val EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_PERSONAL_TASK_ID_KEY = "ClockPandaEventPersonalTaskId"
    }

    fun listCalendarEvents(
        user: User,
        range: TimeSpan
    ): Either<GoogleCalendarApiFacadeError, List<CalendarEvent>> {
        val accessToken = getAccessToken(user).getOrElse { return it.left() }
        val calendarService = getCalendarService(accessToken)

        return Either.catch {
            val listEventRequest = calendarService.events().list("primary")
            listEventRequest.singleEvents = true
            listEventRequest.timeMin = DateTime(range.start.toEpochMilliseconds())
            listEventRequest.timeMax = DateTime(range.end.toEpochMilliseconds())
            listEventRequest.orderBy = "startTime"
            var listEventResponse = listEventRequest.execute()
            val googleCalendarEvents = listEventResponse.items.toMutableList()
            var nextPageToken = listEventResponse.nextPageToken
            while (nextPageToken != null) {
                listEventRequest.pageToken = nextPageToken
                listEventResponse = listEventRequest.execute()
                googleCalendarEvents.addAll(listEventResponse.items)
                nextPageToken = listEventResponse.nextPageToken
            }
            googleCalendarEvents
        }.mapLeft { GoogleCalendarApiFacadeError.GoogleCalendarApiListEventsError(it) }
            .map { it.map(CalendarEvent::fromGoogleCalendarEvent) }
    }

    fun createClockPandaEvent(
        user: User,
        title: String,
        description: String? = null,
        startTime: Instant,
        endTime: Instant,
        calendarEventType: CalendarEventType,
        personalTaskId: String? = null
    ): Either<GoogleCalendarApiFacadeError, CalendarEvent> {
        if (calendarEventType == CalendarEventType.EXTERNAL_EVENT) {
            return GoogleCalendarApiFacadeError.NotAllowedToCreateExternalEventError(title).left()
        }
        val accessToken = getAccessToken(user).getOrElse { return it.left() }
        val calendarService = getCalendarService(accessToken)

        return Either.catch {
            val eventToInsert = Event()
            eventToInsert.summary = "$title \uD83D\uDC3C"
            eventToInsert.description = description
            eventToInsert.start = EventDateTime().setDateTime(DateTime.parseRfc3339(startTime.toString()))
            eventToInsert.end = EventDateTime().setDateTime(DateTime.parseRfc3339(endTime.toString()))
            eventToInsert.attendees = listOf(
                EventAttendee()
                    .setEmail(user.email)
                    .setResponseStatus("accepted")
            )
            eventToInsert.extendedProperties = Event.ExtendedProperties().setShared(
                mutableMapOf(
                    EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_TYPE_KEY to calendarEventType.name,
                ).apply {
                    if (personalTaskId != null) {
                        this[EXTENDED_PROPERTY_CLOCK_PANDA_EVENT_PERSONAL_TASK_ID_KEY] = personalTaskId
                    }
                }
            )
            val result = calendarService.events().insert(
                "primary",
                eventToInsert
            ).execute()
            CalendarEvent.fromGoogleCalendarEvent(result)
        }.mapLeft { GoogleCalendarApiFacadeError.GoogleCalendarApiCreateEventError(it) }
    }

    fun updateClockPandaEvent(
        user: User,
        calendarEvent: CalendarEvent
    ): Either<GoogleCalendarApiFacadeError, CalendarEvent> {
        if (calendarEvent.getCalendarEventType() == CalendarEventType.EXTERNAL_EVENT) {
            return GoogleCalendarApiFacadeError.NotAllowedToUpdateExternalEventError(
                calendarEvent.id,
                calendarEvent.title
            ).left()
        }
        val accessToken = getAccessToken(user).getOrElse { return it.left() }
        val calendarService = getCalendarService(accessToken)

        return Either.catch {
            val eventToUpdate = calendarService.events().get("primary", calendarEvent.id).execute()
            eventToUpdate.summary = "${calendarEvent.title} \uD83D\uDC3C"
            eventToUpdate.description = calendarEvent.description
            eventToUpdate.start =
                EventDateTime().setDateTime(DateTime.parseRfc3339(calendarEvent.getTimeSpan(TimeZone.UTC).start.toString()))
            eventToUpdate.end =
                EventDateTime().setDateTime(DateTime.parseRfc3339(calendarEvent.getTimeSpan(TimeZone.UTC).end.toString()))
            eventToUpdate.attendees = listOf(
                EventAttendee()
                    .setEmail(user.email)
                    .setResponseStatus("accepted")
            )
            val result = calendarService
                .events()
                .update("primary", calendarEvent.id, eventToUpdate)
                .execute()
            CalendarEvent.fromGoogleCalendarEvent(result)
        }.mapLeft { GoogleCalendarApiFacadeError.GoogleCalendarApiUpdateEventError(it) }
    }

    fun deleteCalendarEvent(
        user: User,
        calendarEvent: CalendarEvent
    ): Either<GoogleCalendarApiFacadeError, Unit> {
        if (calendarEvent.getCalendarEventType() == CalendarEventType.EXTERNAL_EVENT) {
            return GoogleCalendarApiFacadeError.NotAllowedToDeleteExternalEventError(calendarEvent.id).left()
        }
        val accessToken = getAccessToken(user).getOrElse { return it.left() }
        val calendarService = getCalendarService(accessToken)

        return Either.catch {
            calendarService.events()
                .delete("primary", calendarEvent.id)
                .execute()
            Unit
        }.mapLeft { GoogleCalendarApiFacadeError.GoogleCalendarApiDeleteEventError(it) }
    }

    internal fun clearAccessTokenCache() {
        ACCESS_TOKEN_CACHE.clear()
    }

    private fun getAccessToken(user: User): Either<GoogleCalendarApiFacadeError, AccessToken> {
        val cachedAccessToken = ACCESS_TOKEN_CACHE[user.email]
        if (cachedAccessToken != null &&
            // Only returned cached access token if it's not expired in 5 minutes
            cachedAccessToken.expirationTime.time > Clock.System.now().toJavaInstant().toEpochMilli() + 1000 * 60 * 5
        ) {
            return Either.Right(cachedAccessToken)
        }
        return Either.catch {
            GoogleRefreshTokenRequest(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                user.googleRefreshToken,
                googleClientId,
                googleClientSecret
            ).execute()
        }.mapLeft { GoogleCalendarApiFacadeError.GoogleAuthApiGetAccessTokenError(it) }
            .map {
                val accessToken = AccessToken(
                    it.accessToken,
                    Date.from(Clock.System.now().plus(it.expiresInSeconds, DateTimeUnit.SECOND).toJavaInstant())
                )
                ACCESS_TOKEN_CACHE[user.email] = accessToken
                accessToken
            }
    }

    private fun getCalendarService(accessToken: AccessToken): Calendar {
        return Calendar.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(OAuth2Credentials.create(accessToken))
        ).setApplicationName("Clock Panda").build()
    }
}
