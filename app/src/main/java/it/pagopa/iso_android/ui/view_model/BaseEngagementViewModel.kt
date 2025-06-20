package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.engagement.Engagement
import it.pagopa.io.wallet.proximity.nfc.sendErrorResponseByNfc
import it.pagopa.io.wallet.proximity.nfc.sendResponseByNfc
import it.pagopa.io.wallet.proximity.request.DocRequested
import it.pagopa.io.wallet.proximity.response.ResponseGenerator
import it.pagopa.io.wallet.proximity.session_data.SessionDataStatus
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper
import it.pagopa.iso_android.R
import it.pagopa.iso_android.ui.AppDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

abstract class BaseEngagementViewModel(private val resources: Resources) : BaseVmKeyCtrl() {
    abstract fun getEuPid(): Document?
    abstract fun getMdl(): Document?
    abstract val engagement: Engagement?
    var dialogText = ""
    val loader = mutableStateOf<String?>(null)
    protected val _shouldGoBack = MutableStateFlow(false)
    val shouldGoBack = _shouldGoBack.asStateFlow()
    val dialog = mutableStateOf<AppDialog?>(null)
    protected lateinit var request: String
    var deviceConnected: DeviceRetrievalHelperWrapper? = null

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

    protected fun shareInfo(sessionsTranscript: ByteArray) {
        if (!keyExists())
            generateKey()
        this.loader.value = resources.getString(R.string.sending_doc)
        viewModelScope.launch(Dispatchers.IO) {
            val disclosedDocuments = ArrayList<Document>()
            val req =
                this@BaseEngagementViewModel.request acceptFieldsExcept arrayOf()
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
            val docRequested = disclosedDocuments.map {
                DocRequested(
                    issuerSignedContent = Base64.encodeToString(
                        it.issuerSigned?.rawValue,
                        Base64.DEFAULT
                    ),
                    alias = alias,
                    docType = it.docType!!
                )
            }
            ResponseGenerator(
                sessionsTranscript = sessionsTranscript
            ).createResponse(
                documents = docRequested.toTypedArray(),
                fieldRequestedAndAccepted = req,
                response = object : ResponseGenerator.Response {
                    override fun onResponseGenerated(response: ByteArray) {
                        ProximityLogger.i(
                            "RESPONSE TO SEND",
                            Base64.encodeToString(response, Base64.NO_WRAP)
                        )
                        this@BaseEngagementViewModel.engagement?.sendResponse(response) ?: run {
                            sendResponseByNfc(deviceConnected, response)
                        }
                        ProximityLogger.i(
                            "RESPONSE TO SEND",
                            Base64.encodeToString(response, Base64.NO_WRAP)
                        )
                    }

                    override fun onError(message: String) {
                        this@BaseEngagementViewModel.loader.value = null
                        dialogFailure(message)
                        val isNoDocFound = message == "no doc found"
                        val toSend = if (isNoDocFound)
                            SessionDataStatus.ERROR_SESSION_ENCRYPTION
                        else
                            SessionDataStatus.ERROR_CBOR_DECODING
                        this@BaseEngagementViewModel.engagement?.sendErrorResponse(toSend) ?: run {
                            sendErrorResponseByNfc(deviceConnected, toSend)
                        }
                    }
                }
            )
        }
    }

    protected fun manageRequestFromDeviceUi(
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

    private fun dialogFailure(message: String) {
        val isNoDocFound = message == "no doc found"
        this@BaseEngagementViewModel.dialog.value = AppDialog(
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
}