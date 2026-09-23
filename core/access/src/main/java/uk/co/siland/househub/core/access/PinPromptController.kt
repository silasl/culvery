package uk.co.siland.househub.core.access

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PinError {
    data object WrongPin : PinError
    data class NotAllowed(val name: String) : PinError
}

/** One showing of the PIN pad. Identity equality: every retry is a new request. */
class PinRequest internal constructor(
    val label: String,
    val error: PinError?,
    val lockedUntilMillis: Long?,
) {
    internal val answer = CompletableDeferred<String?>()
}

@Singleton
class PinPromptController @Inject constructor() {
    private val _request = MutableStateFlow<PinRequest?>(null)
    val request: StateFlow<PinRequest?> = _request.asStateFlow()

    fun submit(pin: String) {
        _request.value?.answer?.complete(pin)
    }

    fun cancel() {
        _request.value?.answer?.complete(null)
    }

    internal fun open(label: String, error: PinError?, lockedUntilMillis: Long?): PinRequest =
        PinRequest(label, error, lockedUntilMillis).also { _request.value = it }

    internal suspend fun ask(label: String, error: PinError?, lockedUntilMillis: Long?): String? =
        open(label, error, lockedUntilMillis).answer.await()

    internal fun dismiss() {
        _request.value = null
    }
}
