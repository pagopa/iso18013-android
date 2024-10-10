package it.pagopa.proximity.qr_code

import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper

interface QrEngagementListener {
    fun onConnecting()
    fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelper)
    fun onCommunicationError(msg: String)
    fun onNewDeviceRequest(deviceRequestBytes: ByteArray)
    fun onDeviceDisconnected(transportSpecificTermination: Boolean)
}