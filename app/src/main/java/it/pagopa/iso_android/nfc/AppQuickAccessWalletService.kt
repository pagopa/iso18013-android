package it.pagopa.iso_android.nfc

import android.os.Build
import androidx.annotation.RequiresApi
import it.pagopa.io.wallet.proximity.nfc.NfcQuickAccessWalletService

@RequiresApi(Build.VERSION_CODES.R)
class AppQuickAccessWalletService : NfcQuickAccessWalletService()