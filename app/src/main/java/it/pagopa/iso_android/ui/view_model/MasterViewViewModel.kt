package it.pagopa.iso_android.ui.view_model

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.pagopa.cbor_implementation.document_manager.DocumentManager
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.cbor_implementation.document_manager.document.IssuedDocument
import it.pagopa.iso_android.qr_code.QrCode
import it.pagopa.proximity.DocType
import it.pagopa.proximity.ProximityLogger
import it.pagopa.proximity.document.DisclosedDocument
import it.pagopa.proximity.qr_code.QrEngagement
import it.pagopa.proximity.qr_code.QrEngagementListener
import it.pagopa.proximity.request.RequestFromDevice
import it.pagopa.proximity.response.ResponseGenerator
import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MasterViewViewModel(
    val qrCodeEngagement: QrEngagement
) : ViewModel() {
    val qrCodeBitmap = mutableStateOf<Bitmap?>(null)
    val documentManager by lazy {
        DocumentManager.build(
            DocumentManagerBuilder(
                qrCodeEngagement.context
            ).enableUserAuth(false)
                .checkPublicKeyBeforeAdding(false)
        )
    }
    var deviceConnected: DeviceRetrievalHelperWrapper? = null
    fun getQrCodeBitmap(qrCodeSize: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runBlocking {
                qrCodeBitmap.value = QrCode(
                    qrCodeEngagement
                        .configure()
                        .getQrCodeString()
                ).asBitmap(qrCodeSize)
            }
            attachListenerAndObserve()
        }
    }

    private fun attachListenerAndObserve() {
        qrCodeEngagement.withListener(object : QrEngagementListener {
            override fun onConnecting() {}
            override fun onCommunicationError(msg: String) {}
            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {}
            override fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
                this@MasterViewViewModel.deviceConnected = deviceRetrievalHelper
            }

            override fun onNewDeviceRequest(
                request: RequestFromDevice,
                sessionsTranscript: ByteArray
            ) {
                ProximityLogger.i("request", request.toString())
                val disclosedDocuments = ArrayList<DisclosedDocument>()
                request.getList().forEach {
                    val issuedDoc = if (it.requiredFields?.docType == DocType.MDL) {
                        getMdl() as IssuedDocument
                    } else
                        getEuPid() as IssuedDocument
                    disclosedDocuments.add(
                        DisclosedDocument(
                            documentId = issuedDoc.id,
                            docType = issuedDoc.docType,
                            requestedFields = it.requiredFields!!,
                            nameSpaces = issuedDoc.nameSpacedDataValues
                        )
                    )
                }
                val responseToSend = ResponseGenerator(
                    context = qrCodeEngagement.context,
                    sessionsTranscript = sessionsTranscript
                )
                if (disclosedDocuments.isNotEmpty()) {
                    responseToSend.createResponse(
                        disclosedDocuments.toTypedArray()
                    )?.let {
                        qrCodeEngagement.sendResponse(it)
                    } ?: run {
                        ProximityLogger.e(
                            "Sending resp",
                            "found doc but fail to generate raw response"
                        )
                    }
                } else
                    ProximityLogger.e("Sending resp", "no doc found")
            }
        })
    }

    fun getEuPid() = documentManager.getAllEuPidDocuments(Document.State.ISSUED).firstOrNull()
    fun getMdl() = documentManager.getAllMdlDocuments(Document.State.ISSUED).firstOrNull()
}