package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import it.pagopa.io.wallet.cbor.document_manager.DocManager
import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.qr_code.QrEngagement
import it.pagopa.io.wallet.proximity.qr_code.QrEngagementListener
import it.pagopa.io.wallet.proximity.request.DocRequested
import it.pagopa.io.wallet.proximity.response.ResponseGenerator
import it.pagopa.io.wallet.proximity.session_data.SessionDataStatus
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper
import it.pagopa.iso_android.R
import it.pagopa.iso_android.qr_code.QrCode
import it.pagopa.iso_android.ui.AppDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

class MasterViewViewModel(
    val qrCodeEngagement: QrEngagement,
    private val resources: Resources
) : BaseVmKeyCtrl() {
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

    private infix fun String.acceptFieldsExcept(notAccepted: Array<String> = arrayOf()): String {
        val originalReq = JSONObject(this).optJSONObject("request")
        val jsonAccepted = JSONObject()
        originalReq?.keys()?.forEach {
            originalReq.optJSONObject(it)?.let { json ->
                val keyJson = JSONObject()
                json.keys().forEach { key ->
                    json.optJSONObject(key)?.let { internalJson ->
                        val internalNewJson = JSONObject()
                        internalJson.keys().forEach { dataKey ->
                            if (!notAccepted.contains(dataKey))
                                internalNewJson.put(dataKey, true)
                        }
                        keyJson.put(key, internalNewJson)
                    }
                }
                jsonAccepted.put(it, keyJson)
            }
        }
        return jsonAccepted.toString()
    }

    private fun manageRequestFromDeviceUi(
        sessionsTranscript: ByteArray
    ) {
        val sb = StringBuilder().apply {
            append("${resources.getString(R.string.share_info_title)}:\n")
        }
        val req = JSONObject(request).optJSONObject("request")
        if (req?.has(DocType.MDL.value) == true || req?.has(DocType.EU_PID.value) == true) {
            req.optJSONObject(DocType.MDL.value)?.let { mdlJson ->
                sb.append("\n${resources.getString(R.string.driving_license)}:\n\n")
                mdlJson.keys().forEach { key ->
                    if (key == "isAuthenticated")
                        ProximityLogger.i("CERT is valid:", "${mdlJson.optBoolean(key)}")
                    mdlJson.optJSONObject(key)?.let { internalJson ->
                        sb.append("$key:\n")
                        internalJson.keys().forEach { dataKey ->
                            sb.append("$dataKey;\n")
                        }
                    }
                }
            }
            req.optJSONObject(DocType.EU_PID.value)?.let { euPidJson ->
                sb.append("\n${resources.getString(R.string.eu_pid)}:\n\n")
                euPidJson.keys().forEach { key ->
                    if (key == "isAuthenticated")
                        ProximityLogger.i("CERT is valid:", "${euPidJson.optBoolean(key)}")
                    euPidJson.optJSONObject(key)?.let { internalJson ->
                        sb.append("$key:\n")
                        internalJson.keys().forEach { dataKey ->
                            sb.append("$dataKey;\n")
                        }
                    }
                }
            }
        } else {
            req?.keys()?.forEach {
                sb.append("\n${it}:\n\n")
                req.optJSONObject(it)?.let { json ->
                    json.keys().forEach { key ->
                        if (key == "isAuthenticated")
                            ProximityLogger.i("CERT is valid:", "${json.optBoolean(key)}")
                        json.optJSONObject(key)?.let { internalJson ->
                            sb.append("$key:\n")
                            internalJson.keys().forEach { dataKey ->
                                sb.append("$dataKey;\n")
                            }
                        }
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
        if (!keyExists())
            generateKey()
        this.loader.value = resources.getString(R.string.sending_doc)
        viewModelScope.launch(Dispatchers.IO) {
            val disclosedDocuments = ArrayList<Document>()
            val req =
                this@MasterViewViewModel.request acceptFieldsExcept arrayOf()
            JSONObject(req).keys().forEach {
                when {
                    DocType(it) == DocType.MDL -> disclosedDocuments.add(getMdl()!!)
                    DocType(it) == DocType.EU_PID -> disclosedDocuments.add(getEuPid()!!)
                }
            }
            if (disclosedDocuments.isEmpty()) {
                disclosedDocuments.add(getMdl()!!)
                disclosedDocuments.add(getEuPid()!!)
            }
            val docRequested = disclosedDocuments.mapNotNull {
                it.issuerSigned?.rawValue?.let { doc ->
                    DocRequested(
                        issuerSignedContent =
                            doc,
                        alias = alias,
                        docType = it.docType!!
                    )
                }
            }
            ResponseGenerator(
                sessionsTranscript = sessionsTranscript
            ).createResponse(
                documents = docRequested.toTypedArray(),
                fieldRequestedAndAccepted = req,
                response = object : ResponseGenerator.Response {
                    override fun onResponseGenerated(response: ByteArray) {
                        qrCodeEngagement.sendResponse(response)
                        ProximityLogger.i(
                            "RESPONSE TO SEND",
                            Base64.encodeToString(response, Base64.NO_WRAP)
                        )
                    }

                    override fun onError(message: String) {
                        this@MasterViewViewModel.loader.value = null
                        dialogFailure(message)
                        val isNoDocFound = message == "no doc found"
                        val toSend = if (isNoDocFound)
                            SessionDataStatus.ERROR_SESSION_ENCRYPTION
                        else
                            SessionDataStatus.ERROR_CBOR_DECODING
                        qrCodeEngagement.sendErrorResponse(toSend)
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
            override fun onDeviceConnecting() {
                this@MasterViewViewModel.loader.value = "Connecting"
            }

            override fun onError(error: Throwable) {
                ProximityLogger.e(
                    this@MasterViewViewModel.javaClass.name,
                    "onCommunicationError: ${error.message}"
                )
                this@MasterViewViewModel.loader.value = null
            }

            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                ProximityLogger.i(this@MasterViewViewModel.javaClass.name, "onDeviceDisconnected")
                this@MasterViewViewModel.loader.value = null
                if (transportSpecificTermination) {
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
                    this@MasterViewViewModel.loader.value = null
                }
            }

            override fun onDeviceConnected(deviceRetrievalHelper: DeviceRetrievalHelperWrapper) {
                this@MasterViewViewModel.deviceConnected = deviceRetrievalHelper
            }

            override fun onDocumentRequestReceived(
                request: String?,
                sessionsTranscript: ByteArray
            ) {
                ProximityLogger.i("request", request.toString())
                this@MasterViewViewModel.request = request.orEmpty()
                if (request == null) {
                    qrCodeEngagement.sendErrorResponse(SessionDataStatus.ERROR_CBOR_DECODING)
                    _shouldGoBack.value = true
                } else
                    manageRequestFromDeviceUi(sessionsTranscript)
            }
        })
    }

    fun getEuPid() = docManager.gelAllEuPidDocuments().firstOrNull()
    fun getMdl() = docManager.gelAllMdlDocuments().firstOrNull()
}