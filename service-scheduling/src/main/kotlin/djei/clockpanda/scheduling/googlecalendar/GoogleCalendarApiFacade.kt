package djei.clockpanda.scheduling.googlecalendar

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.OAuth2Credentials
import djei.clockpanda.model.User
import djei.clockpanda.scheduling.model.CalendarEvent
import djei.clockpanda.scheduling.model.TimeSpan
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
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
    fun listCalendarEvents(
        user: User,
        range: TimeSpan
    ): Either<GoogleCalendarApiFacadeError, List<CalendarEvent>> {
        val accessToken = getAccessToken(user).getOrElse { return it.left() }
        val primaryCalendarId = getPrimaryCalendar(user, accessToken).getOrElse { return it.left() }
        val calendarService = getCalendarService(accessToken)

        return Either.catch {
            val listEventRequest = calendarService.events().list(primaryCalendarId)
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

    private fun getPrimaryCalendar(user: User, accessToken: AccessToken): Either<GoogleCalendarApiFacadeError, String> {
        val calendars = Either.catch {
            val calendarService = getCalendarService(accessToken)
            calendarService.calendarList()
                .list()
                .execute()
                .items
        }.getOrElse {
            return GoogleCalendarApiFacadeError.GoogleCalendarApiListCalendarListError(it).left()
        }

        val primaryCalendar = calendars?.find { it.isPrimary && it.accessRole == "owner" }

        return primaryCalendar?.id?.right()
            ?: GoogleCalendarApiFacadeError.GoogleCalendarApiNoPrimaryCalendarFoundForUserError(user).left()
    }

    private fun getAccessToken(user: User): Either<GoogleCalendarApiFacadeError, AccessToken> {
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
                AccessToken(
                    it.accessToken,
                    Date.from(Clock.System.now().plus(it.expiresInSeconds, DateTimeUnit.SECOND).toJavaInstant())
                )
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
