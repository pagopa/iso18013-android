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
import it.pagopa.iso_android.ui.AppDialog
import it.pagopa.iso_android.ui.CborValuesImpl
import it.pagopa.proximity.DocType
import it.pagopa.proximity.ProximityLogger
import it.pagopa.proximity.document.DisclosedDocument
import it.pagopa.proximity.qr_code.QrEngagement
import it.pagopa.proximity.qr_code.QrEngagementListener
import it.pagopa.proximity.request.CborValues
import it.pagopa.proximity.request.RequestFromDevice
import it.pagopa.proximity.response.ResponseGenerator
import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MasterViewViewModel(
    val qrCodeEngagement: QrEngagement
) : ViewModel() {
    private val _shouldGoBack = MutableStateFlow(false)
    val shouldGoBack = _shouldGoBack.asStateFlow()
    val dialog = mutableStateOf<AppDialog?>(null)
    val loader = mutableStateOf<String?>(null)
    val qrCodeBitmap = mutableStateOf<Bitmap?>(null)
    var dialogText = ""
    val documentManager by lazy {
        DocumentManager.build(
            DocumentManagerBuilder(
                qrCodeEngagement.context
            ).enableUserAuth(false)
                .checkPublicKeyBeforeAdding(false)
        )
    }
    private lateinit var request: RequestFromDevice
    var deviceConnected: DeviceRetrievalHelperWrapper? = null
    fun getQrCodeBitmap(qrCodeSize: Int, cborValues: CborValuesImpl) {
        viewModelScope.launch(Dispatchers.IO) {
            runBlocking {
                qrCodeBitmap.value = QrCode(
                    qrCodeEngagement
                        .configure()
                        .getQrCodeString()
                ).asBitmap(qrCodeSize)
            }
            attachListenerAndObserve(cborValues)
        }
    }

    private fun manageRequestFromDeviceUi(
        cborValues: CborValues,
        sessionsTranscript: ByteArray
    ) {
        val sb = StringBuilder().apply {
            append("You're going to share these info:\n")
        }
        this.request.getList().forEach {
            ProximityLogger.i("CERT is valid:", "${it.isAuthenticated}")
            val isMdl = it.requiredFields!!.docType == DocType.MDL
            if (isMdl)
                sb.append("\nDriving License:\n\n")
            else
                sb.append("\nEu Pid:\n\n")
            it.requiredFields?.toArray()?.forEach { (value, fieldName) ->
                if (it.requiredFields!!.fieldIsRequired(value)) {
                    val array = if (isMdl)
                        cborValues.mdlCborValues
                    else
                        cborValues.euPidCborValues
                    array
                        .filter { (cborValue, _) -> cborValue == fieldName }
                        .forEach { (_, appValue) ->
                            sb.append("$appValue;\n")
                        }
                }
            }
        }
        this.dialogText = sb.toString()
        dialog.value = AppDialog(
            title = "Warning",
            description = this.dialogText,
            button = AppDialog.DialogButton(
                "OK",
                onClick = {
                    this.dialog.value = null
                    shareInfo(sessionsTranscript)
                },
            ),
            secondButton = AppDialog.DialogButton(
                "NO",
                onClick = {
                    this.dialog.value = null
                    _shouldGoBack.value = true
                },
            )
        )
    }

    private fun shareInfo(sessionsTranscript: ByteArray) {
        this.loader.value = "Sending doc"
        viewModelScope.launch(Dispatchers.IO) {
            val disclosedDocuments = ArrayList<DisclosedDocument>()
            this@MasterViewViewModel.request.getList().forEach {
                val issuedDoc = if (it.requiredFields?.docType == DocType.MDL) {
                    getMdl() as? IssuedDocument
                } else
                    getEuPid() as? IssuedDocument
                issuedDoc?.let { issuedDoc ->
                    disclosedDocuments.add(
                        DisclosedDocument(
                            documentId = issuedDoc.id,
                            docType = issuedDoc.docType,
                            requestedFields = it.requiredFields!!,
                            nameSpaces = issuedDoc.nameSpacedDataValues
                        )
                    )
                }
            }
            if (disclosedDocuments.isNotEmpty()) {
                val responseGenerator = ResponseGenerator(
                    context = qrCodeEngagement.context,
                    sessionsTranscript = sessionsTranscript
                )
                val (responseToSend, message) = responseGenerator.createResponse(
                    disclosedDocuments.toTypedArray()
                )
                responseToSend?.let {
                    qrCodeEngagement.sendResponse(it)
                    this@MasterViewViewModel.dialog.value = AppDialog(
                        title = "Data",
                        description = "Sent",
                        button = AppDialog.DialogButton(
                            "Perfect!!",
                            onClick = {
                                dialog.value = null
                                _shouldGoBack.value = true
                            }
                        )
                    )
                } ?: run {
                    this@MasterViewViewModel.dialog.value = AppDialog(
                        title = "Data",
                        description = "Not Sent",
                        button = AppDialog.DialogButton(
                            "Ok",
                            onClick = {
                                dialog.value = null
                                _shouldGoBack.value = true
                            }
                        )
                    )
                    ProximityLogger.e(
                        "Sending resp",
                        "found doc but fail to generate raw response: $message"
                    )
                    qrCodeEngagement.sendErrorResponse()
                }
                this@MasterViewViewModel.loader.value = null
            } else {
                ProximityLogger.e("Sending resp", "no doc found")
                this@MasterViewViewModel.loader.value = null
                _shouldGoBack.value = true
                qrCodeEngagement.sendErrorResponseNoData()
            }
        }
    }

    private fun attachListenerAndObserve(cborValues: CborValues) {
        qrCodeEngagement.withListener(object : QrEngagementListener {
            override fun onConnecting() {}
            override fun onCommunicationError(msg: String) {}
            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                ProximityLogger.i(this@MasterViewViewModel.javaClass.name, "onDeviceDisconnected")
                this@MasterViewViewModel.loader.value = null
            }

            override fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
                this@MasterViewViewModel.deviceConnected = deviceRetrievalHelper
            }

            override fun onNewDeviceRequest(
                request: RequestFromDevice,
                sessionsTranscript: ByteArray
            ) {
                ProximityLogger.i("request", request.toString())
                this@MasterViewViewModel.request = request
                manageRequestFromDeviceUi(cborValues, sessionsTranscript)
            }
        })
    }

    fun getEuPid() = documentManager.getAllEuPidDocuments(Document.State.ISSUED).firstOrNull()
    fun getMdl() = documentManager.getAllMdlDocuments(Document.State.ISSUED).firstOrNull()
}