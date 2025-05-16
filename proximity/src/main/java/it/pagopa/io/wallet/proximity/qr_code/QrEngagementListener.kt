package it.pagopa.io.wallet.proximity.qr_code

import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper

interface QrEngagementListener {
    fun onDeviceConnecting()
    fun onDeviceConnected(deviceRetrievalHelper: DeviceRetrievalHelperWrapper)
    fun onError(error: Throwable)
    fun onDocumentRequestReceived(request: String?, sessionsTranscript: ByteArray)
    fun onDeviceDisconnected(transportSpecificTermination: Boolean)
}