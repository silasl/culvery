package uk.co.siland.culvery

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** 4e design §5: the household's data is never copied off the tablet by Android's backup or device transfer. */
class NoBackupTest {
    // Unit tests run in the module's directory, as AppSourceTest relies on.
    private fun root(path: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(path)).documentElement

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).map(childNodes::item).filterIsInstance<Element>()

    private val application: Element
        get() = root("src/main/AndroidManifest.xml").children().single { it.tagName == "application" }

    @Test
    fun theManifestTurnsBackupOffAndNamesTheRules() {
        // allowBackup is what Android 11 and earlier honour for both backup and transfer; the rules cover Android 12+.
        assertThat(application.getAttributeNS(ANDROID, "allowBackup")).isEqualTo("false")
        assertThat(application.getAttributeNS(ANDROID, "dataExtractionRules")).isEqualTo("@xml/data_extraction_rules")
    }

    @Test
    fun theRulesExcludeEveryDomainFromCloudBackupAndDeviceTransfer() {
        val rules = root("src/main/res/xml/data_extraction_rules.xml")
        assertThat(rules.tagName).isEqualTo("data-extraction-rules")
        assertThat(rules.children().map { it.tagName }).containsExactly("cloud-backup", "device-transfer")
        for (section in rules.children()) {
            val entries = section.children()
            assertThat(entries.map { it.tagName }.toSet()).containsExactly("exclude")
            assertThat(entries.map { it.getAttribute("path") }.toSet()).containsExactly(".")
            assertThat(entries.map { it.getAttribute("domain") }).containsExactlyElementsIn(DOMAINS)
        }
    }

    /** Pin (11 §4.1): the Home Assistant token sits in the datastore folder, which the files domain's rule leaves out of both. */
    @Test
    fun theHomeAssistantTokenIsKeptWhereTheRulesExclude() {
        val settings = File("../core/homeassistant/src/main/java/uk/co/siland/culvery/core/homeassistant/HomeAssistantSettings.kt").readText()
        assertThat(settings).contains("context.preferencesDataStoreFile(FILE)")
        val rules = root("src/main/res/xml/data_extraction_rules.xml")
        for (section in rules.children()) {
            assertThat(section.children().filter { it.getAttribute("domain") == "file" }.map { it.getAttribute("path") }).containsExactly(".")
        }
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val DOMAINS = listOf(
            "root", "file", "database", "sharedpref", "external",
            "device_root", "device_file", "device_database", "device_sharedpref",
        )
    }
}
