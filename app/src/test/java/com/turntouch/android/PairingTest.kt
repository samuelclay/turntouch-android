package com.turntouch.android

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PairingTest {
    @Test fun waitsForButtonThenStopsAfterSuccessfulAuthorization() = runBlocking {
        var attempts=0
        var waiting=0
        val result=awaitPairing(retryMillis=10,onFailure={waiting++}) {
            attempts++
            if(attempts<3)error("link button not pressed")
            mapOf("token" to "test-token")
        }
        assertEquals("test-token",result["token"])
        delay(30)
        assertEquals(3,attempts)
        assertEquals(2,waiting)
    }
    @Test fun leavingTheScreenCancelsFurtherAttempts() = runBlocking {
        var attempts=0
        val firstAttempt=CompletableDeferred<Unit>()
        val job=launch {
            awaitPairing(retryMillis=20,onFailure={}) {
                attempts++;firstAttempt.complete(Unit);error("waiting")
            }
        }
        firstAttempt.await()
        job.cancelAndJoin()
        delay(50)
        assertEquals(1,attempts)
    }
    @Test fun cancelledRequestCannotSaveALateToken() = runBlocking {
        val requestStarted=CompletableDeferred<Unit>()
        var saved=false
        val job=launch {
            awaitPairing(retryMillis=10,onFailure={}) {
                requestStarted.complete(Unit)
                withContext(NonCancellable) { delay(40) }
                mapOf("token" to "late-token")
            }
            saved=true
        }
        requestStarted.await()
        job.cancelAndJoin()
        assertFalse(saved)
    }
}
