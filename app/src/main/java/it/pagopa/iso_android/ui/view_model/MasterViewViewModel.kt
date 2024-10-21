package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.pagopa.cbor_implementation.document_manager.DocumentManager
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder
import it.pagopa.cbor_implementation.document_manager.document.Document
import it.pagopa.cbor_implementation.document_manager.document.IssuedDocument
import it.pagopa.iso_android.R
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
    val qrCodeEngagement: QrEngagement,
    private val resources: Resources
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
            append("${resources.getString(R.string.share_info_title)}:\n")
        }
        this.request.getList().forEach {
            ProximityLogger.i("CERT is valid:", "${it.isAuthenticated}")
            val isMdl = it.requiredFields!!.docType == DocType.MDL
            if (isMdl)
                sb.append("\n${resources.getString(R.string.driving_license)}:\n\n")
            else
                sb.append("\n${resources.getString(R.string.eu_pid)}:\n\n")
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
            title = resources.getString(R.string.warning),
            description = this.dialogText,
            button = AppDialog.DialogButton(
                resources.getString(R.string.ok),
                onClick = {
                    this.dialog.value = null
                    shareInfo(sessionsTranscript)
                },
            ),
            secondButton = AppDialog.DialogButton(
                resources.getString(R.string.no),
                onClick = {
                    this.dialog.value = null
                    _shouldGoBack.value = true
                },
            )
        )
    }

    private fun shareInfo(sessionsTranscript: ByteArray) {
        this.loader.value = resources.getString(R.string.sending_doc)
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
            ResponseGenerator(
                context = qrCodeEngagement.context,
                sessionsTranscript = sessionsTranscript
            ).createResponse(
                disclosedDocuments.toTypedArray(),
                object : ResponseGenerator.Response {
                    override fun onResponseGenerated(response: ByteArray) {
                        this@MasterViewViewModel.loader.value = null
                        qrCodeEngagement.sendResponse(response)
                        this@MasterViewViewModel.dialog.value = AppDialog(
                            title = resources.getString(R.string.data),
                            description = resources.getString(R.string.sent),
                            button = AppDialog.DialogButton(
                                "${resources.getString(R.string.perfect)}!!",
                                onClick = {
                                    dialog.value = null
                                    _shouldGoBack.value = true
                                }
                            )
                        )
                    }

                    override fun onError(message: String) {
                        this@MasterViewViewModel.loader.value = null
                        dialogFailure(message)
                        val isNoDocFound = message == "no doc found"
                        if (isNoDocFound)
                            qrCodeEngagement.sendErrorResponse()
                        else
                            qrCodeEngagement.sendErrorResponseNoData()
                    }
                }
            )
        }
    }

    private fun dialogFailure(message: String) {
        val isNoDocFound = message == "no doc found"
        this@MasterViewViewModel.dialog.value = AppDialog(
            title = if (isNoDocFound)
                resources.getString(R.string.warning)
            else
                resources.getString(R.string.data_not_sent),
            description = if (isNoDocFound)
                resources.getString(R.string.no_doc_found)
            else
                message,
            button = AppDialog.DialogButton(
                resources.getString(R.string.ok),
                onClick = {
                    dialog.value = null
                    _shouldGoBack.value = true
                }
            )
        )
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