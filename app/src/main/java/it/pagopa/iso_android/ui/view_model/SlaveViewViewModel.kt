package it.pagopa.iso_android.ui.view_model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class SlaveViewViewModel : ViewModel() {
    val qrCodeString = MutableStateFlow("No qr code yet")
    fun setQrCode(value: String?) {
        qrCodeString.value = value ?: "No qr code yet"
    }
}