package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import it.pagopa.io.wallet.cbor.document_manager.DocManager
import it.pagopa.io.wallet.proximity.qr_code.QrEngagement
import it.pagopa.iso_android.qr_code.QrCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MasterViewViewModel(
    val qrCodeEngagement: QrEngagement,
    resources: Resources
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
}