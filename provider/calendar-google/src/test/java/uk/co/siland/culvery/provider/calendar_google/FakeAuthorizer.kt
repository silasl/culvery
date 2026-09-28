package uk.co.siland.culvery.provider.calendar_google

import android.content.Intent

/** A grant of both calendar scopes. */
internal fun granted(token: String): Authorization = Authorization.Granted(token, CALENDAR_SCOPES)

/** Play services' answers, scripted: no Play services in tests. */
internal class FakeAuthorizer : Authorizer {
    /** What the next authorize answers, once; after it, authorize grants both scopes. Ignored while [failWith] is set. */
    var next: Authorization? = null
    var failWith: Exception? = null
    /** What the account chooser and consent screens answer. */
    var fromScreens: Authorization = granted("token-after-screens")
    val accounts = mutableListOf<String?>()
    val cleared = mutableListOf<String>()

    override suspend fun authorize(account: String?): Authorization {
        accounts += account
        failWith?.let { throw it }
        return next?.also { next = null } ?: granted("token-granted")
    }

    override fun authorizationFrom(data: Intent?): Authorization = fromScreens

    override suspend fun clearToken(token: String) {
        cleared += token
    }
}
