package it.pagopa.iso_android.ui.view_model

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import it.pagopa.cbor_implementation.document_manager.DocumentManager
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.iso_android.qr_code.QrCode
import it.pagopa.proximity.qr_code.QrEngagement
import it.pagopa.proximity.qr_code.QrEngagementListener
import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper

class MasterViewViewModel(
    private val qrCodeEngagement: QrEngagement
) : ViewModel() {
    val documentManager by lazy {
        DocumentManager.build(
            DocumentManagerBuilder(
                qrCodeEngagement.context
            ).enableUserAuth(false)
                .checkPublicKeyBeforeAdding(false)
        )
    }
    var deviceConnected: DeviceRetrievalHelperWrapper? = null
    fun getQrCodeBitmap(qrCodeSize: Int): Bitmap {
        return QrCode(
            qrCodeEngagement
                .getQrCodeString()
        ).asBitmap(qrCodeSize)
    }

    fun sendEuPidDocumentWhenReady(document: ByteArray) {
        qrCodeEngagement.withListener(object : QrEngagementListener {
            override fun onConnecting() {}
            override fun onCommunicationError(msg: String) {}
            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {}
            override fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
                this@MasterViewViewModel.deviceConnected = deviceRetrievalHelper
            }

            override fun onNewDeviceRequest(deviceRequestBytes: ByteArray) {
                this@MasterViewViewModel.deviceConnected?.sendResponse(document)
            }
        })
    }

    fun getEuPid() = documentManager.getAllEuPidDocuments(Document.State.ISSUED).firstOrNull()
}