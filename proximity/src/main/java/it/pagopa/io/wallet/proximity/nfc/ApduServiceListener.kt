package it.pagopa.io.wallet.proximity.nfc

import android.os.Bundle

interface ApduServiceListener {
    fun onCreateService()
    fun onDeactivated(reason: Int)
    fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray
}