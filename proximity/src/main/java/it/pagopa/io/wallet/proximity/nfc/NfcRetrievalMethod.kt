package it.pagopa.io.wallet.proximity.nfc

import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod

/**
 * NFC Retrieval Method
 * @property commandDataFieldMaxLength
 * @property responseDataFieldMaxLength
 */
data class NfcRetrievalMethod(
    val commandDataFieldMaxLength: Long = 256L,
    val responseDataFieldMaxLength: Long = 256L,
    val useBluetooth: Boolean = true
): DeviceRetrievalMethod
