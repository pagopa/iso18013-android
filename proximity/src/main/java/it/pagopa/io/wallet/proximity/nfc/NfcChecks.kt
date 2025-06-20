package it.pagopa.io.wallet.proximity.nfc

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.provider.Settings

class NfcChecks(private val context: Context) {
    /**It checks if device has NFC feature*/
    fun hasNfcFeature() = NfcAdapter.getDefaultAdapter(context) != null

    /**It checks if device has NFC enabled or not*/
    fun isNfcAvailable() = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true
    /**It opens NFC Settings*/
    fun openNfcSettings() {
        val intent = Intent(Settings.ACTION_NFC_SETTINGS)
        context.startActivity(intent)
    }
}