package uk.co.siland.culvery.provider.calendar_google

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class PathTemplateTest {
    private val api = GoogleApi("https://www.googleapis.com/calendar/v3/".toHttpUrl(), FakeTokenSource(), okhttp3.OkHttpClient())

    @Test
    fun idsAreReplacedSoNoEmailReachesTheLog() {
        val template = pathTemplate(api.url("calendars", "family@example.com", "events", "abc123def"))
        assertThat(template).isEqualTo("calendars/{id}/events/{id}")
        assertThat(template).doesNotContain("family")
        assertThat(template).doesNotContain("@")
    }

    @Test
    fun anIdSpelledLikeAPathWordIsStillReplaced() {
        assertThat(pathTemplate(api.url("calendars", "events", "events", "calendars"))).isEqualTo("calendars/{id}/events/{id}")
    }

    @Test
    fun theFixedPathsStayReadable() {
        assertThat(pathTemplate(api.url("users", "me", "calendarList", query = mapOf("showHidden" to "true"))))
            .isEqualTo("users/me/calendarList")
        assertThat(pathTemplate(api.url("calendars", "primary"))).isEqualTo("calendars/{id}")
    }
}
