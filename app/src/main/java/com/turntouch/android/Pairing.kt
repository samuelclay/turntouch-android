package com.turntouch.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Sequential attempts stop on success or when the waiting screen is left. */
suspend fun awaitPairing(
    retryMillis: Long = 5000,
    onFailure: (Exception) -> Unit,
    attempt: suspend () -> Map<String, String>
): Map<String, String> {
    while (true) {
        currentCoroutineContext().ensureActive()
        try {
            val result = attempt()
            currentCoroutineContext().ensureActive()
            return result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onFailure(failure)
        }
        delay(retryMillis)
    }
}
