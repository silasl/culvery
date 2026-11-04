package uk.co.siland.culvery

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uk.co.siland.culvery.core.plugin.WallClock
import uk.co.siland.culvery.core.plugin.today
import uk.co.siland.culvery.provider.calendar_midsussex.COUNCIL_BASE_URL
import uk.co.siland.culvery.provider.calendar_midsussex.COUNCIL_DATE_PATTERN
import uk.co.siland.culvery.provider.calendar_midsussex.di.CouncilInterceptors

/** What the sample council's collections page shows. */
enum class CouncilPage {
    GOOD,
    MISSING,
    UNKNOWN,
    ;

    companion object {
        fun of(raw: String?): CouncilPage? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

private const val SAMPLE_POSTCODE = "ZZ999ZZ"
private const val POSTCODE_FIELD = "address_postcode"
private const val SAMPLE_TRACK = "SAMPLE"
private const val UNKNOWN_BIN = "Domestic Glass Collection Service"
private const val UNKNOWN_DAY = 5L

private const val FOOD = "Domestic Food Waste Service"
private const val GARDEN = "Domestic Garden Waste Service"
private const val RECYCLING = "Domestic Recycling Waste Collection Service"
private const val REFUSE = "Domestic Refuse Waste Collection Service"

/** Days from today and the council's names for the bins out that day; day 3 has all four. */
private val SCHEDULE = listOf(
    1L to listOf(FOOD),
    3L to listOf(FOOD, GARDEN, RECYCLING, REFUSE),
    8L to listOf(FOOD, RECYCLING),
    10L to listOf(GARDEN, REFUSE),
    15L to listOf(FOOD),
    17L to listOf(RECYCLING, REFUSE),
)

private val COUNCIL_DATE = DateTimeFormatter.ofPattern(COUNCIL_DATE_PATTERN)

/**
 * Debug builds only: answers the council's pages for the postcode ZZ99 9ZZ without touching the network, so the
 * walkthrough never reads the real site. The pages after the search carry no postcode, so the address links hold a
 * sample-only Track value to tell them from the real council's. The start page is always served here; a search for any
 * other postcode goes on to the real site. The collections page switches with
 * `adb shell am broadcast -n uk.co.siland.culvery/.DebugCouncilReceiver -a uk.co.siland.culvery.debug.COUNCIL --es page good|missing|unknown`.
 * Dates count from the wall clock's today.
 */
@Singleton
class DebugCouncil internal constructor(
    private val asset: (String) -> String,
    private val clock: WallClock,
    private val zone: ZoneId,
) : Interceptor {
    @Inject
    constructor(@ApplicationContext context: Context, clock: WallClock) : this(
        { name -> context.assets.open("council/$name").use { it.readBytes().decodeToString() } },
        clock,
        ZoneId.systemDefault(),
    )

    @Volatile
    var page: CouncilPage = CouncilPage.GOOD

    private val host = COUNCIL_BASE_URL.toHttpUrl().host

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = bodyFor(request) ?: return chain.proceed(request)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("text/html; charset=utf-8".toMediaType()))
            .build()
    }

    private fun bodyFor(request: Request): String? {
        val url = request.url
        if (url.host != host) return null
        val track = url.queryParameter("Track")
        return when {
            request.method == "POST" -> if (postcodeOf(request) == SAMPLE_POSTCODE) asset("addresses.html") else null
            track == null -> asset("start.html")
            track == SAMPLE_TRACK && url.queryParameter("seq") == "3" -> collections()
            else -> null
        }
    }

    private fun postcodeOf(request: Request): String? {
        val form = request.body as? FormBody ?: return null
        val index = (0 until form.size).firstOrNull { form.name(it) == POSTCODE_FIELD } ?: return null
        return form.value(index).filterNot { it.isWhitespace() }.uppercase()
    }

    private fun collections(): String {
        if (page == CouncilPage.MISSING) return asset("collections_missing.html")
        val today = clock.today(zone)
        val days = SCHEDULE.flatMap { (offset, names) -> names.map { today.plusDays(offset) to it } }
        val rows = if (page == CouncilPage.UNKNOWN) days + (today.plusDays(UNKNOWN_DAY) to UNKNOWN_BIN) else days
        val row = asset("collections_row.html")
        return asset("collections_page.html").replace("{{rows}}", rows.joinToString("") { (date, name) -> rowOf(row, date, name) })
    }

    private fun rowOf(template: String, date: LocalDate, name: String) =
        template.replace("{{date}}", COUNCIL_DATE.format(date)).replace("{{name}}", name)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugCouncilModule {
    @Binds
    @IntoSet
    @CouncilInterceptors
    abstract fun interceptor(impl: DebugCouncil): Interceptor
}
