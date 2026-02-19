package it.pagopa.io.wallet.proximity.nfc

import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.crypto.EcPrivateKey
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper

/** Represents the possible NFC event notifications.*/
sealed class NfcEngagementEvent {
    data object Connecting : NfcEngagementEvent()
    data object NotSupported : NfcEngagementEvent()
    data class Connected(val device: DeviceRetrievalHelperWrapper) : NfcEngagementEvent()
    data class Error(val error: Throwable) : NfcEngagementEvent()
    data class Disconnected(val transportSpecificTermination: Boolean) : NfcEngagementEvent()
    data class DocumentRequestReceived(val request: String?, val sessionTranscript: ByteArray) :
        NfcEngagementEvent()

    data object DocumentSent : NfcEngagementEvent()
}

internal sealed class ServiceEvents {
    data class SetupReady(
        val retrievalMethods: List<DeviceRetrievalMethod>,
        val documents: List<Document>?,
        val alias: String?,
        val readerTrustStore: List<List<Any>>?
    ) : ServiceEvents()

    data class QrCodeDeviceEngagement(
        val deviceEngagementSetup: Pair<ByteArray, EcPrivateKey>
    ) : ServiceEvents()
}
