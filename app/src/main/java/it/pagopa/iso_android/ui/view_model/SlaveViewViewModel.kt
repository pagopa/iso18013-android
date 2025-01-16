package it.pagopa.iso_android.ui.view_model

import androidx.lifecycle.ViewModel
import it.pagopa.proximity.ProximityLogger
import it.pagopa.proximity.qr_code.QrEngagement
import it.pagopa.proximity.qr_code.QrEngagementListener
import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper
import kotlinx.coroutines.flow.MutableStateFlow

class SlaveViewViewModel(
    val qrCodeEngagement: QrEngagement
) : ViewModel() {
    val qrCodeString = MutableStateFlow("No qr code yet")
    var deviceConnected: DeviceRetrievalHelperWrapper? = null
    fun setQrCode(value: String?) {
        value?.let { qrValue ->
            try {
                qrCodeString.value = qrValue
            } catch (e: Exception) {
                qrCodeString.value = qrValue
                ProximityLogger.e("not a base 64:", "$e")
            }
        } ?: run {
            qrCodeString.value = "No qr code yet"
        }
    }

    fun attachListenerAndObserve() {
        qrCodeEngagement.withListener(object : QrEngagementListener {
            override fun onConnecting() {
                ProximityLogger.i("ProximityLogger", "onConnecting")
            }

            override fun onCommunicationError(msg: String) {
                ProximityLogger.i("ProximityLogger", "onCommunicationError: $msg")
            }

            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                ProximityLogger.i("ProximityLogger", "onDeviceDisconnected")
                //this@SlaveViewViewModel.loader.value = null
            }

            override fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
                ProximityLogger.i("ProximityLogger", "onDeviceRetrievalHelperReady")
                this@SlaveViewViewModel.deviceConnected = deviceRetrievalHelper
            }

            override fun onNewDeviceRequest(request: String?, sessionsTranscript: ByteArray) {
                ProximityLogger.i("request", request.orEmpty())
            }
        })
    }
}