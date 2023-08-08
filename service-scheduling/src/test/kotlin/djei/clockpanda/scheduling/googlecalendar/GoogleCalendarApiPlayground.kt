package djei.clockpanda.scheduling.googlecalendar

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.Calendar
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.OAuth2Credentials
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@Disabled
class GoogleCalendarApiPlayground {

    @Test
    fun test() {
        val accessToken = AccessToken(
            "",
            Date.from(Instant.now().plus(3600L, ChronoUnit.SECONDS))
        )
        val service = Calendar.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            HttpCredentialsAdapter(OAuth2Credentials.create(accessToken))
        ).setApplicationName("Google Drive Service").build()

        val result = service.CalendarList().list().execute().items
        println(result)
    }
}
