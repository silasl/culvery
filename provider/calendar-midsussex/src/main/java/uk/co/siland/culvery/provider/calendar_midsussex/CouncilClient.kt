package uk.co.siland.culvery.provider.calendar_midsussex

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import uk.co.siland.culvery.capability.calendar.UnreachableException
import uk.co.siland.culvery.core.net.await
import uk.co.siland.culvery.core.net.internetHttpClient
import uk.co.siland.culvery.core.net.withUserAgent
import uk.co.siland.culvery.core.plugin.asUnreachable

const val COUNCIL_BASE_URL = "https://waste.services.midsussex.gov.uk/"

private const val COUNCIL_SITE = "the council’s site"

private const val MAX_PAGE_BYTES = 2L * 1024 * 1024

/** A plain browser User-Agent, as the council's site expects; [interceptors] are the debug build's sample council. */
internal fun councilHttpClient(version: String, interceptors: Set<Interceptor>): OkHttpClient = internetHttpClient()
    .withUserAgent("Mozilla/5.0 (Linux; Android) Culvery/$version")
    .apply { interceptors.forEach(::addInterceptor) }
    .build()

/**
 * The council's three pages (spec 4.1). Each read or search is its own session, its start page's cookie kept only for
 * that walk; [readAddress] goes on in the session of the search that [Found] the address. Throws [UnreachableException]
 * for the network or a busy site, [BinsReadFailed] for a page that isn't what it should be.
 */
class CouncilClient(private val baseUrl: HttpUrl, private val client: Lazy<OkHttpClient>) {
    /** A search's [addresses], with the session whose cookie their links need. */
    class Found internal constructor(val addresses: List<Address>, internal val session: CouncilClient.Session)

    suspend fun search(house: String, postcode: String): Found = Session().let { Found(it.search(house, postcode), it) }

    /** The collections at [address], one of [found]'s, without walking from the start again. */
    suspend fun readAddress(found: Found, address: Address): List<BinDay> = found.session.collectionsAt(address)

    suspend fun read(house: String, postcode: String, line: String): List<BinDay> {
        val session = Session()
        return session.collectionsAt(pick(session.search(house, postcode), line))
    }

    internal inner class Session {
        private val http = client.value.newBuilder().cookieJar(SessionCookies()).build()

        suspend fun search(house: String, postcode: String): List<Address> {
            val start = page(Request.Builder().url(baseUrl).build(), BinsFailure.NO_START_LINK)
            val form = FormBody.Builder().add(FIELD_HOUSE, house).add(FIELD_POSTCODE, postcode).build()
            val list = page(Request.Builder().url(searchUrl(start, baseUrl)).post(form).build(), BinsFailure.NO_ADDRESS_LIST)
            return addresses(list, baseUrl)
        }

        suspend fun collectionsAt(address: Address): List<BinDay> =
            collections(page(Request.Builder().url(address.url).build(), BinsFailure.NO_COLLECTIONS))

        private suspend fun page(request: Request, refused: BinsFailure): String {
            val answer = try {
                http.newCall(request).await(MAX_PAGE_BYTES)
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                throw e.asUnreachable(COUNCIL_SITE, offlineOnUnknownHost = true)
            }
            if (answer.code == 429 || answer.code >= 500) {
                throw UnreachableException("The council’s site asked to try later (${answer.code})")
            }
            if (answer.code !in 200..299) throw BinsReadFailed(refused)
            return answer.text
        }
    }
}

private class SessionCookies : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
        this.cookies += cookies
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }
}
