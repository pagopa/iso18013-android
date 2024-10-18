package it.pagopa.iso_android.ui.view

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.pagopa.iso_android.R
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.CborValuesImpl
import it.pagopa.iso_android.ui.CenteredComposable
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.LoaderDialog
import it.pagopa.iso_android.ui.view_model.MasterViewViewModel
import it.pagopa.iso_android.ui.view_model.viewModelWithQrEngagement
import it.pagopa.proximity.bluetooth.BleRetrievalMethod
import it.pagopa.proximity.qr_code.QrEngagement
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MasterView(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = viewModelWithQrEngagement<MasterViewViewModel>(
        QrEngagement.build(
            context = context,
            retrievalMethods = listOf(
                BleRetrievalMethod(
                    peripheralServerMode = true,
                    centralClientMode = false,
                    clearBleCache = true
                )
            )
        ).withReaderTrustStore(listOf(R.raw.eudi_pid_issuer_ut))
    )
    val back = {
        viewModel.qrCodeEngagement.close()
        onBack.invoke()
    }
    BackHandler(onBack = back)
    val qrCodeSize = with(LocalDensity.current) { 200.dp.toPx() }.roundToInt()
    LaunchedEffect(viewModel) {
        viewModel.getQrCodeBitmap(
            qrCodeSize,
            CborValuesImpl(context.resources)
        )
        this.launch {
            viewModel.shouldGoBack.collectLatest {
                if (it)
                    back.invoke()
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        BigText(
            modifier = Modifier.wrapContentSize(),
            text = "QR Code Test",
            color = MaterialTheme.colorScheme.primary
        )
        CenteredComposable(
            modifier = Modifier
                .height(0.dp)
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            viewModel.qrCodeBitmap.value?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    modifier = Modifier.size(200.dp),
                    contentDescription = "Qr code"
                )
            }
        }
    }
    viewModel.dialog.value?.let {
        GenericDialog(it)
    }
    LoaderDialog(viewModel.loader)
}

@Preview
@Composable
fun MasterViewPreview() {
    BasePreview {
        MasterView(onBack = {})
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
fun MasterViewPreviewNight() {
    BasePreview {
        MasterView(onBack = {})
    }
}