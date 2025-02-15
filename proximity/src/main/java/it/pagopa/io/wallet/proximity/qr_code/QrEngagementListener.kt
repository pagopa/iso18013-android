package it.pagopa.io.wallet.proximity.qr_code

import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper

interface QrEngagementListener {
    fun onConnecting()
    fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper)
    fun onCommunicationError(msg: String)
    fun onNewDeviceRequest(request: String?, sessionsTranscript: ByteArray)
    fun onDeviceDisconnected(transportSpecificTermination: Boolean)
}