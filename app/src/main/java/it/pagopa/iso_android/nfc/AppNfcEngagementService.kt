package it.pagopa.iso_android.nfc

import it.pagopa.io.wallet.proximity.bluetooth.BleRetrievalMethod
import it.pagopa.io.wallet.proximity.nfc.NfcEngagementService
import it.pagopa.io.wallet.proximity.nfc.NfcRetrievalMethod
import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.iso_android.R

class AppNfcEngagementService : NfcEngagementService() {
    override val readerTrustStore: List<Any> = listOf(R.raw.eudi_pid_issuer_ut)
    override val retrievalMethods: List<DeviceRetrievalMethod> = listOf(
        BleRetrievalMethod(
            peripheralServerMode = true,
            centralClientMode = false,
            clearBleCache = true
        ),
        NfcRetrievalMethod(
            commandDataFieldMaxLength = 256L,
            responseDataFieldMaxLength = 256L
        )
    )
}