package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.pagopa.cbor_implementation.document_manager.DocManager
import it.pagopa.cbor_implementation.model.DocType
import it.pagopa.cbor_implementation.model.Document
import it.pagopa.iso_android.R
import it.pagopa.iso_android.qr_code.QrCode
import it.pagopa.iso_android.ui.AppDialog
import it.pagopa.proximity.ProximityLogger
import it.pagopa.proximity.qr_code.QrEngagement
import it.pagopa.proximity.qr_code.QrEngagementListener
import it.pagopa.proximity.request.DocRequested
import it.pagopa.proximity.response.ResponseGenerator
import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

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
    val docManager by lazy {
        DocManager.getInstance(
            context = qrCodeEngagement.context,
            storageDirectory = qrCodeEngagement.context.noBackupFilesDir,
            prefix = "SECURE_STORAGE",
            alias = "SECURE_STORAGE_KEY_${qrCodeEngagement.context.noBackupFilesDir}"
        )
    }
    private lateinit var request: String
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

    private fun manageRequestFromDeviceUi(
        sessionsTranscript: ByteArray
    ) {
        val sb = StringBuilder().apply {
            append("${resources.getString(R.string.share_info_title)}:\n")
        }
        val req = JSONObject(request).optJSONObject("request")
        ProximityLogger.i("CERT is valid:", "${JSONObject(request).optBoolean("isAuthenticated")}")
        req?.optJSONObject(DocType.MDL.value)?.let { mdlJson ->
            sb.append("\n${resources.getString(R.string.driving_license)}:\n\n")
            mdlJson.keys().forEach { key ->
                if (mdlJson.optBoolean(key) == true)
                    sb.append("$key;\n")
            }
        }
        req?.optJSONObject(DocType.EU_PID.value)?.let { euPidJson ->
            sb.append("\n${resources.getString(R.string.eu_pid)}:\n\n")
            euPidJson.keys().forEach { key ->
                if (euPidJson.optBoolean(key) == true)
                    sb.append("$key;\n")
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
            val disclosedDocuments = ArrayList<Document>()
            val req = JSONObject(request).optJSONObject("request")
            req?.keys()?.forEach {
                when {
                    DocType(it) == DocType.MDL -> disclosedDocuments.add(getMdl()!!)
                    DocType(it) == DocType.EU_PID -> disclosedDocuments.add(getEuPid()!!)
                }
            }
            val docRequested = disclosedDocuments.map {
                DocRequested(
                    content = Base64.encodeToString(
                        it.rawValue,
                        Base64.DEFAULT
                    ),
                    alias = "SECURE_STORAGE_KEY_${qrCodeEngagement.context.noBackupFilesDir}"
                )
            }
            ResponseGenerator(
                sessionsTranscript = sessionsTranscript
            ).createResponse(
                documents = docRequested.toTypedArray(),
                fieldRequestedAndAccepted = req?.toString() ?: "{}",
                response = object : ResponseGenerator.Response {
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

    private fun attachListenerAndObserve() {
        qrCodeEngagement.withListener(object : QrEngagementListener {
            override fun onConnecting() {
                this@MasterViewViewModel.loader.value = "Connecting"
            }

            override fun onCommunicationError(msg: String) {}
            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                ProximityLogger.i(this@MasterViewViewModel.javaClass.name, "onDeviceDisconnected")
                this@MasterViewViewModel.loader.value = null
            }

            override fun onDeviceRetrievalHelperReady(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
                this@MasterViewViewModel.deviceConnected = deviceRetrievalHelper
            }

            override fun onNewDeviceRequest(
                request: String?,
                sessionsTranscript: ByteArray
            ) {
                ProximityLogger.i("request", request.toString())
                this@MasterViewViewModel.request = request.orEmpty()
                manageRequestFromDeviceUi(sessionsTranscript)
            }
        })
    }

    fun getEuPid() = docManager.gelAllEuPidDocuments().firstOrNull()
    fun getMdl() = docManager.gelAllMdlDocuments().firstOrNull()
}