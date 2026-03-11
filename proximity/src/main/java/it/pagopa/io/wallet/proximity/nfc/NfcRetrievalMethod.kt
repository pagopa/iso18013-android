package it.pagopa.io.wallet.proximity.nfc

import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod

/**
 * NFC Retrieval Method
 * @property commandDataFieldMaxLength
 * @property responseDataFieldMaxLength
 */
data class NfcRetrievalMethod(
    val commandDataFieldMaxLength: Long = 65279,
    val responseDataFieldMaxLength: Long = 65279
) : DeviceRetrievalMethod
