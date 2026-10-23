package uk.co.siland.culvery.provider.calendar_google

/** The calendar scopes Culvery asks for (3a design D2): every calendar read, events written. */
internal val CALENDAR_SCOPES = listOf(
    "https://www.googleapis.com/auth/calendar.readonly",
    "https://www.googleapis.com/auth/calendar.events",
)
