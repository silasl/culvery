package uk.co.siland.culvery.core.google.test

import android.content.Intent
import uk.co.siland.culvery.core.google.Authorization
import uk.co.siland.culvery.core.google.Authorizer

/** A grant of [scopes]. */
fun granted(token: String, scopes: List<String>): Authorization = Authorization.Granted(token, scopes)

/** Play services' answers, scripted: no Play services in tests. */
class FakeAuthorizer : Authorizer {
    /** What the next authorize answers, once; after it, authorize grants the scopes just asked. Ignored while [failWith] is set. */
    var next: Authorization? = null
    var failWith: Exception? = null

    /** What the account chooser and consent screens answer; unset, they grant the scopes last asked. */
    var fromScreens: Authorization? = null
    val accounts = mutableListOf<String?>()
    val scopesAsked = mutableListOf<List<String>>()
    val cleared = mutableListOf<String>()
    var readFromScreens = 0

    override suspend fun authorize(account: String?, scopes: List<String>): Authorization {
        accounts += account
        scopesAsked += scopes
        failWith?.let { throw it }
        return next?.also { next = null } ?: granted("token-granted", scopes)
    }

    override fun authorizationFrom(data: Intent?): Authorization {
        readFromScreens++
        return fromScreens ?: granted("token-after-screens", scopesAsked.lastOrNull() ?: error("authorize before the screens"))
    }

    override suspend fun clearToken(token: String) {
        cleared += token
    }
}
