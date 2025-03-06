package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import android.graphics.Bitmap
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.pagopa.io.wallet.cbor.document_manager.DocManager
import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.qr_code.QrEngagement
import it.pagopa.io.wallet.proximity.qr_code.QrEngagementListener
import it.pagopa.io.wallet.proximity.request.DocRequested
import it.pagopa.io.wallet.proximity.response.ResponseGenerator
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
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

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
    private val keyStoreType by lazy {
        "AndroidKeyStore"
    }
    private val alias = "pagoPa"
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

    private fun keyExists(alias: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
            keyStore.containsAlias(alias)
        } catch (_: Exception) {
            false
        }
    }

    private fun generateKey(alias: String) {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                keyStoreType
            )
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .build()
        keyPairGenerator.initialize(keyGenParameterSpec)
        keyPairGenerator.generateKeyPair()
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
        if (!keyExists(alias)) {
            generateKey(alias)
        }
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
                fieldRequestedAndAccepted = req?.toString() ?: "{}",
                response = object : ResponseGenerator.Response {
                    override fun onResponseGenerated(response: ByteArray) {
                        this@MasterViewViewModel.loader.value = null
                        qrCodeEngagement.sendResponse(response)
                        ProximityLogger.i(
                            "RESPONSE TO SEND",
                            Base64.encodeToString(response, Base64.NO_WRAP)
                        )
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
                if (request == null) {
                    qrCodeEngagement.sendErrorResponse()
                    _shouldGoBack.value = true
                } else
                    manageRequestFromDeviceUi(sessionsTranscript)
            }
        })
    }

    fun getEuPid() = docManager.gelAllEuPidDocuments().firstOrNull()
    fun getMdl() = docManager.gelAllMdlDocuments().firstOrNull()
}