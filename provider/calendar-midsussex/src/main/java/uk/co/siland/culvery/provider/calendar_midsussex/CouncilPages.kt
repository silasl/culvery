package uk.co.siland.culvery.provider.calendar_midsussex

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okhttp3.HttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal const val FIELD_HOUSE = "address_name_number"
internal const val FIELD_POSTCODE = "address_postcode"

private const val START_LINK_TEXT = "View my collections"

enum class BinsFailure { NO_START_LINK, NO_ADDRESS_LIST, ADDRESS_GONE, NO_COLLECTIONS, BAD_DATE, NO_SERVICES }

class BinsReadFailed(val kind: BinsFailure) : Exception(kind.name)

data class Address(val line: String, val url: HttpUrl) {
    override fun toString() = "Address"
}

/** [kinds] are keys (food, garden, recycling, waste) or the council's own words for a bin we don't know. */
@Serializable
data class BinDay(@Serializable(with = IsoDate::class) val date: LocalDate, val kinds: List<String>)

internal object IsoDate : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("IsoDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = try {
        LocalDate.parse(decoder.decodeString())
    } catch (_: DateTimeException) {
        throw SerializationException("Not an ISO date")
    }
}

private val KNOWN_KINDS = listOf("food" to "food", "garden" to "garden", "recycling" to "recycling", "refuse" to "waste")
/** How the council's collections page writes a date. */
const val COUNCIL_DATE_PATTERN = "dd/MM/uuuu"

private val DATE = DateTimeFormatter.ofPattern(COUNCIL_DATE_PATTERN).withResolverStyle(ResolverStyle.STRICT)

private fun HttpUrl.onSiteOf(base: HttpUrl) = scheme == base.scheme && host == base.host

fun searchUrl(startHtml: String, baseUrl: HttpUrl): HttpUrl {
    val href = Jsoup.parse(startHtml).select("a[href]")
        .firstOrNull { it.text().contains(START_LINK_TEXT, ignoreCase = true) }
        ?.attr("href")
    val link = href?.let(baseUrl::resolve)?.takeIf { it.onSiteOf(baseUrl) } ?: throw BinsReadFailed(BinsFailure.NO_START_LINK)
    return link.newBuilder().setQueryParameter("seq", "2").build()
}

fun addresses(listHtml: String, baseUrl: HttpUrl): List<Address> {
    val list = Jsoup.parse(listHtml).selectFirst("div#property_list")
        ?: throw BinsReadFailed(BinsFailure.NO_ADDRESS_LIST)
    val links = list.select("a[href]")
    val kept = links.mapNotNull { link ->
        baseUrl.resolve(link.attr("href"))?.takeIf { it.onSiteOf(baseUrl) }?.let { Address(link.text().trim(), it) }
    }
    if (kept.isEmpty() && links.isNotEmpty()) throw BinsReadFailed(BinsFailure.NO_ADDRESS_LIST)
    return kept
}

fun pick(addresses: List<Address>, line: String): Address =
    addresses.firstOrNull { it.line == line } ?: throw BinsReadFailed(BinsFailure.ADDRESS_GONE)

fun collections(houseHtml: String): List<BinDay> {
    val page = Jsoup.parse(houseHtml)
    if (page.selectFirst("span#waste-hint") != null) throw BinsReadFailed(BinsFailure.NO_SERVICES)
    val section = page.selectFirst("section#scheduled-collections")
        ?: page.selectFirst("h2#upcoming-scheduled-collections")?.parent()
        ?: throw BinsReadFailed(BinsFailure.NO_COLLECTIONS)
    val rows = section.select("ul.displayinlineblock").mapNotNull(::row)
    if (rows.isEmpty()) throw BinsReadFailed(BinsFailure.NO_COLLECTIONS)
    return rows.groupBy({ it.first }, { it.second })
        .toSortedMap()
        .map { (date, kinds) -> BinDay(date, kinds.distinct().sortedWith(KIND_ORDER)) }
}

private fun row(ul: Element): Pair<LocalDate, String>? {
    val cells = ul.children().filter { it.tagName() == "li" }
    if (cells.size < 3) return null
    val date = try {
        LocalDate.parse(cells[1].text().trim(), DATE)
    } catch (_: DateTimeException) {
        throw BinsReadFailed(BinsFailure.BAD_DATE)
    }
    val name = cells[2].text().trim()
    if (name.isEmpty()) return null
    return date to kindOf(name)
}

private fun kindOf(name: String): String =
    KNOWN_KINDS.firstOrNull { (word, _) -> name.contains(word, ignoreCase = true) }?.second ?: name

private val KIND_ORDER = compareBy<String>(
    { kind -> KNOWN_KINDS.indexOfFirst { it.second == kind }.takeIf { it >= 0 } ?: KNOWN_KINDS.size },
    { it },
)
