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
import it.pagopa.proximity.qr_code.QrEngagement
import it.pagopa.proximity.qr_code.QrEngagementListener
import it.pagopa.proximity.request.RequestFromDevice
import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MasterViewViewModel(
    val qrCodeEngagement: QrEngagement
) : ViewModel() {
    val qrCodeBitmap = mutableStateOf<Bitmap?>(null)
    private var mdlRequested = false
    private var euPidRequest = false
    var mdlResponseToSend: ByteArray? = null
    var euPidResponseToSend: ByteArray? = null
    private val _onRequest = MutableStateFlow(false)
    private val onRequest = _onRequest.asStateFlow()
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
        viewModelScope.launch(Dispatchers.IO) {
            this@MasterViewViewModel.onRequest.collectLatest { requested ->
                if (requested) {
                    ProximityLogger.i("MDL response","$mdlResponseToSend")
                    ProximityLogger.i("Eu pid response","$euPidResponseToSend")
                    when {
                        mdlRequested && euPidRequest -> qrCodeEngagement.sendResponse(
                            mdlResponseToSend!! + euPidResponseToSend!!
                        )

                        mdlRequested -> qrCodeEngagement.sendResponse(mdlResponseToSend!!)
                        euPidRequest -> qrCodeEngagement.sendResponse(euPidResponseToSend!!)
                    }
                }
            }
        }
        qrCodeEngagement.withListener(object : QrEngagementListener {
            override fun onConnecting() {}
            override fun onCommunicationError(msg: String) {}
            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {}
            override fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
                this@MasterViewViewModel.deviceConnected = deviceRetrievalHelper
            }

            override fun onNewDeviceRequest(request: RequestFromDevice) {
                ProximityLogger.i("request", request.toString())
                request.getList().forEach {
                    mdlRequested = it.requiredFields?.docType == DocType.MDL
                    euPidRequest = it.requiredFields?.docType == DocType.EU_PID
                }
                if (mdlRequested)
                    mdlResponseToSend =
                        (getMdl() as? IssuedDocument)?.getDocumentCborBytes() ?: byteArrayOf()
                if (euPidRequest)
                    euPidResponseToSend =
                        (getEuPid() as? IssuedDocument)?.getDocumentCborBytes() ?: byteArrayOf()
                if (mdlRequested || euPidRequest)
                    _onRequest.value = true
            }
        })
    }

    fun getEuPid() = documentManager.getAllEuPidDocuments(Document.State.ISSUED).firstOrNull()
    fun getMdl() = documentManager.getAllMdlDocuments(Document.State.ISSUED).firstOrNull()
}