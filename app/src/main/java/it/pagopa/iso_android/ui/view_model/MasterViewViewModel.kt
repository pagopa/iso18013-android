package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import it.pagopa.io.wallet.cbor.document_manager.DocManager
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.engagement.EngagementListener
import it.pagopa.io.wallet.proximity.qr_code.QrEngagement
import it.pagopa.io.wallet.proximity.session_data.SessionDataStatus
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper
import it.pagopa.iso_android.R
import it.pagopa.iso_android.qr_code.QrCode
import it.pagopa.iso_android.ui.AppDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MasterViewViewModel(
    val qrCodeEngagement: QrEngagement,
    private val resources: Resources
) : BaseEngagementViewModel(resources) {
    val docManager by lazy {
        DocManager.getInstance(
            context = qrCodeEngagement.context,
            storageDirectory = qrCodeEngagement.context.noBackupFilesDir,
            prefix = "SECURE_STORAGE",
            alias = "SECURE_STORAGE_KEY_${qrCodeEngagement.context.noBackupFilesDir}"
        )
    }

    override fun getEuPid() = docManager.gelAllEuPidDocuments().firstOrNull()
    override fun getMdl() = docManager.gelAllMdlDocuments().firstOrNull()
    override val engagement = this.qrCodeEngagement
    val qrCodeBitmap = mutableStateOf<Bitmap?>(null)

    fun getQrCodeBitmap(qrCodeSize: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runBlocking {
                qrCodeBitmap.value = QrCode(
                    qrCodeEngagement
                        .configure()
                        .getQrCodeString()
                ).asBitmap(qrCodeSize)
            }
            qrCodeEngagement.setupDeviceEngagementForNfc()
            attachListenerAndObserve()
        }
    }

    private fun attachListenerAndObserve() {
        qrCodeEngagement.withListener(object : EngagementListener {
            override fun onDeviceConnecting() {
                this@MasterViewViewModel.loader.value = "Connecting"
            }

            override fun onError(error: Throwable) {
                ProximityLogger.e(
                    this@MasterViewViewModel.javaClass.name,
                    "onCommunicationError: ${error.message}"
                )
                this@MasterViewViewModel.loader.value = null
                _shouldGoBack.value = true
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
                this@MasterViewViewModel.loader.value = "Connected"
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

}