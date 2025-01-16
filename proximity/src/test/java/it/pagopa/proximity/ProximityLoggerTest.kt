package it.pagopa.proximity

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Test

class ProximityLoggerTest {
    @Test
    fun loggerTest() {
        mockkStatic(Log::class)
        val tag = "fake"
        every { Log.v(tag, "ciao") } returns 0
        every { Log.e(tag, "ciao") } returns 0
        every { Log.d(tag, "ciao") } returns 0
        every { Log.i(tag, "ciao") } returns 0
        val logger = ProximityLogger
        logger.enabled = false
        assert(!logger.enabled)
        logger.v(tag, "ciao")
        verify(exactly = 0) { Log.v(tag, "ciao") }
        logger.e(tag, "ciao")
        verify(exactly = 0) { Log.e(tag, "ciao") }
        logger.d(tag, "ciao")
        verify(exactly = 0) { Log.d(tag, "ciao") }
        logger.i(tag, "ciao")
        verify(exactly = 0) { Log.i(tag, "ciao") }
        logger.enabled = true
        assert(logger.enabled)
        logger.v(tag, "ciao")
        verify(exactly = 1) { Log.v(tag, "ciao") }
        logger.e(tag, "ciao")
        verify(exactly = 1) { Log.e(tag, "ciao") }
        logger.d(tag, "ciao")
        verify(exactly = 1) { Log.d(tag, "ciao") }
        logger.i(tag, "ciao")
        verify(exactly = 1) { Log.i(tag, "ciao") }
    }
}