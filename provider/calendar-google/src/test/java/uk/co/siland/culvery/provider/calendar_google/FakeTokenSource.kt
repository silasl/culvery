package uk.co.siland.culvery.provider.calendar_google

/** Hands out "token-1", "token-2"… and records what it was asked and told. */
internal class FakeTokenSource : TokenSource {
    private var issued = 0
    val asked = mutableListOf<String>()
    val invalidated = mutableListOf<String>()
    /** When set, token() throws it: Play services wanting the user, or unreachable. */
    var failWith: Exception? = null

    override suspend fun token(account: String): String {
        synchronized(this) { asked += account }
        failWith?.let { throw it }
        return synchronized(this) { "token-${++issued}" }
    }

    override suspend fun invalidate(token: String) {
        synchronized(this) { invalidated += token }
    }
}
