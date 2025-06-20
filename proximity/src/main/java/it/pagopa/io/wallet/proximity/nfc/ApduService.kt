package it.pagopa.io.wallet.proximity.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

class ApduService(private val listener: ApduServiceListener) : HostApduService() {
    override fun onCreate() {
        super.onCreate()
        listener.onCreateService()
    }

    override fun onDeactivated(reason: Int) {
        listener.onDeactivated(reason)
    }

    override fun processCommandApdu(
        commandApdu: ByteArray, extras: Bundle?
    ): ByteArray? = listener.processCommandApdu(commandApdu, extras)
}