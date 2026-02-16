package it.pagopa.iso_android.nfc

import android.app.Activity
import it.pagopa.io.wallet.proximity.nfc.NfcExchangeService
import it.pagopa.iso_android.MainActivity
import it.pagopa.iso_android.R

class AppNfcExchangeService : NfcExchangeService() {
    override val javaClassToLaunch: Class<out Activity>
        get() = MainActivity::class.java
    override val readerTrustStore: List<List<Any>> by lazy {
        listOf(listOf(R.raw.eudi_pid_issuer_ut))
    }
}