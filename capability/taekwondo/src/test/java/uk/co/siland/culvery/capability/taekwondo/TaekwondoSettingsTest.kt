package uk.co.siland.culvery.capability.taekwondo

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.co.siland.culvery.core.plugin.settingsDataStore

class TaekwondoSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store by lazy { settingsDataStore(scope) { File(folder.root, "taekwondo_settings.preferences_pb") } }
    private val settings by lazy { TaekwondoSettings(store) }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun theLanguageStartsAsKorean() = runBlocking {
        assertThat(settings.language.first()).isEqualTo(Language.KOREAN)
    }

    @Test
    fun aChosenLanguageIsKept() = runBlocking {
        settings.setLanguage(Language.PHONETIC)

        assertThat(TaekwondoSettings(store).language.first()).isEqualTo(Language.PHONETIC)
    }

    @Test
    fun aLanguageThisVersionDoesntKnowReadsAsKorean() = runBlocking {
        store.edit { it[stringPreferencesKey("language")] = "KLINGON" }

        assertThat(settings.language.first()).isEqualTo(Language.KOREAN)
    }
}
