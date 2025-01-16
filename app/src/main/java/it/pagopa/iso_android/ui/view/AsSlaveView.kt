package it.pagopa.iso_android.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import it.pagopa.iso_android.R
import it.pagopa.iso_android.camerax.CameraView
import it.pagopa.iso_android.ui.AppDialog
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.view_model.SlaveViewViewModel
import it.pagopa.proximity.ProximityLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun SlaveView(
    vm: SlaveViewViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val dialogVisible = remember { mutableStateOf(false) }
    LaunchedEffect(vm) {
        vm.attachListenerAndObserve()
        this.launch(Dispatchers.IO) {
            vm.qrCodeString.collectLatest {
                if (it != "No qr code yet") {
                    try {
                        vm.qrCodeEngagement.connect(it)
                    } catch (e: Exception) {
                        ProximityLogger.e("not valid base64:", e.message.orEmpty())
                    }
                }
            }
        }
    }
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
            dialogVisible.value = true
        }
    }
    if (dialogVisible.value) {
        GenericDialog(
            dialog = AppDialog(
                title = "QrCode read:",
                image = painterResource(R.drawable.ic_launcher_background),
                description = qrCodeString,
                button = AppDialog.DialogButton(
                    text = "Ok",
                    onClick = {
                        dialogVisible.value = false
                    }
                )
            )
        )
    }
}