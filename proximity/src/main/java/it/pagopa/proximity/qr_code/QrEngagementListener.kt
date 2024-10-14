package it.pagopa.proximity.qr_code

import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper

interface QrEngagementListener {
    fun onConnecting()
    fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper)
    fun onCommunicationError(msg: String)
    fun onNewDeviceRequest(deviceRequestBytes: ByteArray)
    fun onDeviceDisconnected(transportSpecificTermination: Boolean)
}