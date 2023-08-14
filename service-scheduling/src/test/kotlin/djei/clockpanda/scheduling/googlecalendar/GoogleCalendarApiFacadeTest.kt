package djei.clockpanda.scheduling.googlecalendar

import arrow.core.Either
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.api.services.calendar.model.Events
import djei.clockpanda.model.CalendarConnectionStatus
import djei.clockpanda.model.CalendarProvider
import djei.clockpanda.model.User
import djei.clockpanda.model.fixtures.UserFixtures
import djei.clockpanda.scheduling.model.CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE
import djei.clockpanda.scheduling.model.CalendarEventType
import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.scheduling.model.fixtures.CalendarEventFixtures
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockConstructionWithAnswer
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class)
class GoogleCalendarApiFacadeTest {

    private val googleCalendarApiFacade = GoogleCalendarApiFacade("googleClientId", "googleClientSecret")

    private val user = User(
        email = "djei2@github.com",
        firstName = "Djei2 First Name",
        lastName = "Djei2 Last Name",
        calendarProvider = CalendarProvider.GOOGLE_CALENDAR,
        calendarConnectionStatus = CalendarConnectionStatus.CONNECTED,
        googleRefreshToken = "refresh_token",
        preferences = UserFixtures.userPreferences,
        createdAt = Clock.System.now(),
        lastUpdatedAt = Clock.System.now()
    )

    private val timeSpan = TimeSpan(
        start = Clock.System.now(),
        end = Clock.System.now().plus(24 * 7, DateTimeUnit.HOUR)
    )

    @Test
    fun `test listCalendarEvents - failed to retrieve user access token`() {
        // Clear access token cache to prevent us from getting one and force the retrieval failure
        googleCalendarApiFacade.clearAccessTokenCache()
        mockConstructionWithAnswer(
            GoogleRefreshTokenRequest::class.java,
            { throw RuntimeException("failed to refresh token") }
        ).use {
            val result = googleCalendarApiFacade.listCalendarEvents(
                user,
                timeSpan
            )

            when (result) {
                is Either.Left -> {
                    assertThat(result.value).isInstanceOf(GoogleCalendarApiFacadeError.GoogleAuthApiGetAccessTokenError::class.java)
                    val error = result.value as GoogleCalendarApiFacadeError.GoogleAuthApiGetAccessTokenError
                    assertThat(error.message).isEqualTo("google auth api get access token error: failed to refresh token")
                }

                is Either.Right -> fail("This should have returned left value")
            }
        }
    }

    @Test
    fun `test listCalendarEvents - failed to list events`() {
        mockConstruction(
            GoogleRefreshTokenRequest::class.java
        ) { mock, _ ->
            `when`(mock.execute()).thenReturn(
                GoogleTokenResponse()
                    .setAccessToken("access_token")
                    .setExpiresInSeconds(3600L)
            )
        }.use {
            mockConstruction(
                Calendar.Builder::class.java
            ) { mockBuilder, _ ->
                val mockCalendar = mock(Calendar::class.java)
                `when`(mockBuilder.setApplicationName(any())).thenReturn(mockBuilder)
                `when`(mockBuilder.build()).thenReturn(mockCalendar)
                `when`(mockCalendar.events()).thenThrow(RuntimeException("failed to execute events list"))
            }.use {
                val result = googleCalendarApiFacade.listCalendarEvents(
                    user,
                    timeSpan
                )

                when (result) {
                    is Either.Left -> {
                        assertThat(result.value).isInstanceOf(GoogleCalendarApiFacadeError.GoogleCalendarApiListEventsError::class.java)
                        val error = result.value as GoogleCalendarApiFacadeError.GoogleCalendarApiListEventsError
                        assertThat(error.message).isEqualTo("google calendar api list events error: failed to execute events list")
                    }

                    is Either.Right -> fail("This should have returned left value")
                }
            }
        }
    }

    @Test
    fun `test listCalendarEvents - happy path`() {
        val mockCalendarEvents = mock(Calendar.Events::class.java)
        val mockCalendarEventsList = mock(Calendar.Events.List::class.java)
        mockConstruction(
            GoogleRefreshTokenRequest::class.java
        ) { mock, _ ->
            `when`(mock.execute()).thenReturn(
                GoogleTokenResponse()
                    .setAccessToken("access_token")
                    .setExpiresInSeconds(0L)
            )
        }.use {
            mockConstruction(
                Calendar.Builder::class.java
            ) { mockBuilder, _ ->
                val mockCalendar = mock(Calendar::class.java)
                `when`(mockBuilder.setApplicationName(any())).thenReturn(mockBuilder)
                `when`(mockBuilder.build()).thenReturn(mockCalendar)
                val mockEvents = mock(Events::class.java)
                val mockEvent1 = mock(Event::class.java)
                val mockEvent2 = mock(Event::class.java)
                `when`(mockCalendar.events()).thenReturn(mockCalendarEvents)
                `when`(mockCalendarEvents.list(any())).thenReturn(mockCalendarEventsList)
                `when`(mockCalendarEventsList.execute()).thenReturn(mockEvents)
                `when`(mockEvents.items)
                    .thenReturn(listOf(mockEvent1))
                    .thenReturn(listOf(mockEvent2))
                `when`(mockEvents.nextPageToken)
                    .thenReturn("next_page_token")
                    .thenReturn(null)
                `when`(mockEvent1.id).thenReturn("event_id_1")
                `when`(mockEvent1.summary).thenReturn(CLOCK_PANDA_FOCUS_TIME_EVENT_TITLE)
                `when`(mockEvent1.description).thenReturn("description")
                `when`(mockEvent1.start).thenReturn(
                    EventDateTime().setDateTime(DateTime.parseRfc3339("2021-01-10T10:00:00.000Z"))
                )
                `when`(mockEvent1.end).thenReturn(
                    EventDateTime().setDateTime(DateTime.parseRfc3339("2021-01-10T11:00:00.000Z"))
                )
                `when`(mockEvent1.iCalUID).thenReturn("ical_uid_1")
                `when`(mockEvent1.recurringEventId).thenReturn("recurring_event_id_1")
                `when`(mockEvent1.organizer).thenReturn(
                    Event.Organizer().setEmail("organizer_email_1@email.com")
                )
                `when`(mockEvent2.id).thenReturn("event_id_2")
                `when`(mockEvent2.summary).thenReturn(null)
                `when`(mockEvent2.description).thenReturn(null)
                `when`(mockEvent2.start).thenReturn(
                    EventDateTime().setDate(DateTime.parseRfc3339("2021-01-10"))
                )
                `when`(mockEvent2.end).thenReturn(
                    EventDateTime().setDate(DateTime.parseRfc3339("2021-01-11"))
                )
                `when`(mockEvent2.iCalUID).thenReturn("ical_uid_2")
                `when`(mockEvent2.recurringEventId).thenReturn(null)
                `when`(mockEvent2.organizer).thenReturn(
                    Event.Organizer().setEmail(null)
                )
            }.use {
                val result = googleCalendarApiFacade.listCalendarEvents(
                    user,
                    timeSpan
                )

                when (result) {
                    is Either.Left -> fail("This should have succeeded")
                    is Either.Right -> {
                        val calendarEvents = result.value
                        assertThat(calendarEvents).hasSize(2)
                        val calendarEvent1 = calendarEvents[0]
                        assertThat(calendarEvent1.id).isEqualTo("event_id_1")
                        assertThat(calendarEvent1.title).isEqualTo("[ClockPanda] Focus Time")
                        assertThat(calendarEvent1.description).isEqualTo("description")
                        val timeSpan1 = calendarEvent1.getTimeSpan(TimeZone.of("America/New_York"))
                        assertThat(timeSpan1.start).isEqualTo(Instant.parse("2021-01-10T10:00:00.000Z"))
                        assertThat(timeSpan1.end).isEqualTo(Instant.parse("2021-01-10T11:00:00.000Z"))
                        assertThat(calendarEvent1.iCalUid).isEqualTo("ical_uid_1")
                        assertThat(calendarEvent1.isRecurring).isTrue
                        assertThat(calendarEvent1.getType()).isEqualTo(CalendarEventType.FOCUS_TIME)
                        val calendarEvent2 = calendarEvents[1]
                        assertThat(calendarEvent2.id).isEqualTo("event_id_2")
                        assertThat(calendarEvent2.title).isEqualTo("")
                        assertThat(calendarEvent2.description).isEqualTo("")
                        val timeSpan2 = calendarEvent2.getTimeSpan(TimeZone.of("America/New_York"))
                        assertThat(timeSpan2.start).isEqualTo(Instant.parse("2021-01-10T05:00:00.000Z"))
                        assertThat(timeSpan2.end).isEqualTo(Instant.parse("2021-01-11T05:00:00.000Z"))
                        assertThat(calendarEvent2.iCalUid).isEqualTo("ical_uid_2")
                        assertThat(calendarEvent2.isRecurring).isFalse
                        assertThat(calendarEvent2.getType()).isEqualTo(CalendarEventType.EXTERNAL_EVENT)
                        verify(mockCalendarEventsList).singleEvents = true
                        verify(mockCalendarEventsList).timeMin = DateTime(timeSpan.start.toEpochMilliseconds())
                        verify(mockCalendarEventsList).timeMax = DateTime(timeSpan.end.toEpochMilliseconds())
                        verify(mockCalendarEventsList).orderBy = "startTime"
                        verify(mockCalendarEventsList).pageToken = "next_page_token"
                        verify(mockCalendarEventsList, times(2)).execute()
                    }
                }
            }
        }
    }

    @Test
    fun `test deleteCalendarEvent - failed to retrieve user access token`() {
        // Clear access token cache to prevent us from getting one and force the retrieval failure
        googleCalendarApiFacade.clearAccessTokenCache()
        mockConstructionWithAnswer(
            GoogleRefreshTokenRequest::class.java,
            { throw RuntimeException("failed to refresh token") }
        ).use {
            val result = googleCalendarApiFacade.deleteCalendarEvent(
                user,
                CalendarEventFixtures.externalTypeCalendarEvent
            )

            when (result) {
                is Either.Left -> {
                    assertThat(result.value).isInstanceOf(GoogleCalendarApiFacadeError.GoogleAuthApiGetAccessTokenError::class.java)
                    val error = result.value as GoogleCalendarApiFacadeError.GoogleAuthApiGetAccessTokenError
                    assertThat(error.message).isEqualTo("google auth api get access token error: failed to refresh token")
                }

                is Either.Right -> fail("This should have failed")
            }
        }
    }

    @Test
    fun `test deleteCalendarEvent - call to google calendar api failed`() {
        mockConstruction(
            GoogleRefreshTokenRequest::class.java
        ) { mock, _ ->
            `when`(mock.execute()).thenReturn(
                GoogleTokenResponse()
                    .setAccessToken("access_token")
                    .setExpiresInSeconds(3600L)
            )
        }.use {
            mockConstruction(
                Calendar.Builder::class.java
            ) { mockBuilder, _ ->
                val mockCalendar = mock(Calendar::class.java)
                `when`(mockBuilder.setApplicationName(any())).thenReturn(mockBuilder)
                `when`(mockBuilder.build()).thenReturn(mockCalendar)
                `when`(mockCalendar.events()).thenThrow(RuntimeException("failed to delete event"))
            }.use {
                val result = googleCalendarApiFacade.deleteCalendarEvent(
                    user,
                    CalendarEventFixtures.externalTypeCalendarEvent
                )

                when (result) {
                    is Either.Left -> {
                        assertThat(result.value).isInstanceOf(GoogleCalendarApiFacadeError.GoogleCalendarApiDeleteEventError::class.java)
                        val error = result.value as GoogleCalendarApiFacadeError.GoogleCalendarApiDeleteEventError
                        assertThat(error.message).isEqualTo("google calendar api delete event error: failed to delete event")
                    }

                    is Either.Right -> fail("This should have returned left value")
                }
            }
        }
    }

    @Test
    fun `test deleteCalendarEvent - happy path`() {
        val mockCalendarEvents = mock(Calendar.Events::class.java)
        val mockCalendarEventsDelete = mock(Calendar.Events.Delete::class.java)
        mockConstruction(
            GoogleRefreshTokenRequest::class.java
        ) { mock, _ ->
            `when`(mock.execute()).thenReturn(
                GoogleTokenResponse()
                    .setAccessToken("access_token")
                    .setExpiresInSeconds(3600L)
            )
        }.use {
            mockConstruction(
                Calendar.Builder::class.java
            ) { mockBuilder, _ ->
                val mockCalendar = mock(Calendar::class.java)
                `when`(mockBuilder.setApplicationName(any())).thenReturn(mockBuilder)
                `when`(mockBuilder.build()).thenReturn(mockCalendar)
                `when`(mockCalendar.events()).thenReturn(mockCalendarEvents)
                `when`(mockCalendarEvents.delete(any(), any())).thenReturn(mockCalendarEventsDelete)
            }.use {
                val result = googleCalendarApiFacade.deleteCalendarEvent(
                    user,
                    CalendarEventFixtures.externalTypeCalendarEvent
                )

                when (result) {
                    is Either.Left -> fail("This should have returned right value")

                    is Either.Right -> {
                        verify(mockCalendarEvents).delete(
                            eq("primary"),
                            eq(CalendarEventFixtures.externalTypeCalendarEvent.id)
                        )
                    }
                }
            }
        }
    }
}
