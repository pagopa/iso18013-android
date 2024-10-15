package it.pagopa.iso_android.ui.view_model

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.pagopa.cbor_implementation.document_manager.DocumentManager
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.iso_android.qr_code.QrCode
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

class MasterViewViewModel(
    val qrCodeEngagement: QrEngagement
) : ViewModel() {
    val qrCodeBitmap = mutableStateOf<Bitmap?>(null)
    private val _mdlRequested = MutableStateFlow(false)
    val mdlRequested = _mdlRequested.asStateFlow()
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
            qrCodeBitmap.value = QrCode(
                qrCodeEngagement
                    .configure()
                    .getQrCodeString()
            ).asBitmap(qrCodeSize)
        }
    }

    fun attachListenerAndObserve() {
        viewModelScope.launch {
            this@MasterViewViewModel.mdlRequested.collectLatest {requested->
                if(requested)
                    qrCodeEngagement.sendResponse()
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
            }
        })
    }

    fun getEuPid() = documentManager.getAllEuPidDocuments(Document.State.ISSUED).firstOrNull()
    fun getMdl() = documentManager.getAllMdlDocuments(Document.State.ISSUED).firstOrNull()
}