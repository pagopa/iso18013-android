package it.pagopa.io.wallet.proximity.nfc

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
}