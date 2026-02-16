package it.pagopa.io.wallet.proximity.nfc

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.nfc.cardemulation.PollingFrame
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.android.identity.util.toHex
import it.pagopa.io.wallet.proximity.ProximityLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds


abstract class NfcObserveModeHelperService : HostApduService() {

    private val helper by lazy{
        NfcObserveModeHelper(applicationContext)
    }

    companion object {
        private const val TAG = "NfcObserveModeHelperService"
    }

    override fun onDestroy() {
        super.onDestroy()
        ProximityLogger.i(TAG, "onDestroy")
    }

    override fun onCreate() {
        ProximityLogger.i(TAG, "onCreate")
        super.onCreate()
        helper.updateObserveMode()
    }


    override fun onDeactivated(p0: Int) {
    }
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun processPollingFrames(frames: List<PollingFrame>) {
        ProximityLogger.i(TAG, "processPollingFrames")
        var foundIdentityReader = false
        for (frame in frames) {
            if (frame.data.toHex().startsWith("6a028103")) {
                foundIdentityReader = true
            }
        }
        if (foundIdentityReader) {
            ProximityLogger.i(TAG, "Detected identity reader and in observe mode: " +
                    "inhibiting observe to allow transaction to go through"
            )
            helper.inhibitObserveModeForTransaction()
        }
    }

    override fun processCommandApdu(p0: ByteArray?, p1: Bundle?): ByteArray? {
        ProximityLogger.i(TAG, "processCommandApdu")
        return null
    }
}


// Need to implement this so it works even when the application is not initialized.. this
// is because the OS might create NfcObserveModeHelperService without the app running.
//
class NfcObserveModeHelper(private val applicationContext: Context) {
    private val TAG = "NfcObserveModeHelper"

    private var observeModeExplicitlyInhibited = false
    private var inhbitForTransactionUntil: Instant? = null

    // Inhibits observe mode for 5 seconds to allow a transaction to go through
    fun inhibitObserveModeForTransaction() {
        inhbitForTransactionUntil = Clock.System.now() + 5.seconds
        updateObserveMode()
    }

    private var pollJob: Job? = null

    internal fun updateObserveMode() {
        if (Build.VERSION.SDK_INT < 36) {
            return
        }

        ensurePollingFiltersRegistered()

        if (pollJob == null) {
            pollJob = CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    delay(1.seconds)
                    updateObserveMode()
                }
            }
        }

        val adapter = NfcAdapter.getDefaultAdapter(applicationContext)
        if (adapter != null) {
            val isObserveModeEnabledOnAdapter = adapter.isObserveModeEnabled
            val observeModeShouldBeEnabled = if (observeModeExplicitlyInhibited) {
                false
            } else {
                inhbitForTransactionUntil?.let {
                    val now = Clock.System.now()
                    if (now < it) {
                        false
                    } else {
                        inhbitForTransactionUntil = null
                        true
                    }
                } ?: true
            }
            if (isObserveModeEnabledOnAdapter != observeModeShouldBeEnabled) {
                ProximityLogger.i(
                    TAG,
                    "isObserveModeEnabled=$isObserveModeEnabledOnAdapter changing to $observeModeShouldBeEnabled"
                )
                adapter.isObserveModeEnabled = observeModeShouldBeEnabled
            }
            //Logger.i(TAG, "observe mode enabled: ${adapter.isObserveModeEnabled}")
        }
    }

    fun inhibitObserveMode() {
        ProximityLogger.i(TAG, "inhibitObserveMode() called")
        if (observeModeExplicitlyInhibited) {
            ProximityLogger.e(
                TAG,
                "inhibitObserveMode() called but observeModeExplicitlyInhibited is already true"
            )
        }
        observeModeExplicitlyInhibited = true
        updateObserveMode()
    }

    fun uninhibitObserveMode() {
        ProximityLogger.i(TAG, "uninhibitObserveMode() called")
        if (!observeModeExplicitlyInhibited) {
            ProximityLogger.e(
                TAG,
                "uninhibitObserveMode() called but observeModeExplicitlyInhibited is not true"
            )
        }
        observeModeExplicitlyInhibited = false
        updateObserveMode()
    }

    private var pollingFiltersRegistered = false

    private fun ensurePollingFiltersRegistered() {
        if (pollingFiltersRegistered) {
            return
        }

        val adapter = NfcAdapter.getDefaultAdapter(applicationContext)
        if (adapter == null) {
            ProximityLogger.e(TAG, "No NFC adapter available")
            return
        }

        if (Build.VERSION.SDK_INT < 36) {
            ProximityLogger.i(
                TAG, "Observe mode not supported by Android version ${Build.VERSION.SDK_INT}, " +
                        "requires  ${36} or later"
            )
            return
        }

        if (!adapter.isObserveModeSupported) {
            ProximityLogger.i(TAG, "Observe mode not supported by adapter")
            return
        }

        val componentName =
            ComponentName(applicationContext, NfcObserveModeHelperService::class.java)
        val cardEmulation = CardEmulation.getInstance(adapter)

        cardEmulation.registerPollingLoopPatternFilterForService(
            componentName,
            "6a028103.*",
            false
        )

        ProximityLogger.i(TAG, "Polling filters registered")
        pollingFiltersRegistered = true
    }
}