package it.pagopa.iso_android.nfc

import it.pagopa.io.wallet.proximity.nfc.NfcEngagementService
import it.pagopa.io.wallet.proximity.nfc.NfcRetrievalMethod
import it.pagopa.iso_android.R

class AppNfcEngagementService : NfcEngagementService() {
    override val readerTrustStore: List<Any> = listOf(R.raw.eudi_pid_issuer_ut)
    override val retrievalMethods: List<NfcRetrievalMethod> = listOf(
        NfcRetrievalMethod(
            commandDataFieldMaxLength = 256L,
            responseDataFieldMaxLength = 256L,
            useBluetooth = true
        )
    )
}