package it.pagopa.iso_android.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import it.pagopa.iso_android.camerax.CameraView
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.view_model.SlaveViewViewModel
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun SlaveView(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val vm = viewModel<SlaveViewViewModel>()
    val qrCodeString = vm.qrCodeString.asStateFlow().collectAsState().value
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CameraView(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
        ) { qrCodeString ->
            vm.setQrCode(qrCodeString)
        }
        BigText(
            modifier = Modifier
                .wrapContentSize()
                .align(Alignment.BottomCenter),
            text = qrCodeString,
            color = MaterialTheme.colorScheme.primary
        )
    }
}