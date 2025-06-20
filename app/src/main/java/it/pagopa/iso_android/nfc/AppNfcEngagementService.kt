package it.pagopa.iso_android.nfc

import it.pagopa.io.wallet.proximity.nfc.NfcEngagementService
import it.pagopa.iso_android.R

class AppNfcEngagementService : NfcEngagementService(){
    override val readerTrustStore: List<Any> = listOf(R.raw.eudi_pid_issuer_ut)
}