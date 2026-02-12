package it.pagopa.io.wallet.proximity.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import it.pagopa.io.wallet.proximity.ProximityLogger


abstract class NfcObserveModeHelperService : HostApduService() {
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
    }


    override fun onDeactivated(p0: Int) {
    }

    override fun processCommandApdu(p0: ByteArray?, p1: Bundle?): ByteArray? {
        return null
    }
}