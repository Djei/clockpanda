package djei.clockpanda.scheduling.googlecalendar

import djei.clockpanda.model.User

sealed class GoogleCalendarApiFacadeError(message: String) : Error(message) {
    data class GoogleAuthApiGetAccessTokenError(val details: String?) :
        GoogleCalendarApiFacadeError("google auth api get access token error: ${details ?: "unknown error"}")

    data class GoogleCalendarApiListCalendarListError(val details: String?) :
        GoogleCalendarApiFacadeError("google calendar api list calendar list error: ${details ?: "unknown error"}")

    data class GoogleCalendarApiNoPrimaryCalendarFoundForUserError(val user: User) :
        GoogleCalendarApiFacadeError("no primary calendar found for user: ${user.email}")

    data class GoogleCalendarApiListEventsError(val details: String?) :
        GoogleCalendarApiFacadeError("google calendar api list events error: ${details ?: "unknown error"}")
}
