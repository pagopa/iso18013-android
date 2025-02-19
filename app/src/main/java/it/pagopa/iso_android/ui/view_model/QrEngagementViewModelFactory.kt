package it.pagopa.iso_android.ui.view_model

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import it.pagopa.io.wallet.proximity.qr_code.QrEngagement

@Composable
inline fun <reified VM : ViewModel> viewModelWithQrEngagement(qrEngagement: QrEngagement): VM {
    return viewModel<VM>(factory = QrEngagementViewModelFactory<VM>(qrEngagement))
}

class QrEngagementViewModelFactory<out VM : ViewModel>(
    private val qrEngagement: QrEngagement
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val constructor = modelClass.getConstructor(QrEngagement::class.java)
        val instance = constructor.newInstance(qrEngagement) as? VM
        return (instance as? T) ?: run {
            throw IllegalArgumentException("Cannot create ViewModel class for ${modelClass.name}")
        }
    }
}
