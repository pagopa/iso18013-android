package it.pagopa.iso_android.ui.view

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.CborValuesImpl
import it.pagopa.iso_android.ui.CenteredComposable
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.LoaderDialog
import it.pagopa.iso_android.ui.preview.ThemePreviews
import it.pagopa.iso_android.ui.view_model.MasterViewViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MasterView(
    viewModel: MasterViewViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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

@ThemePreviews
@Composable
fun MasterViewPreview() {
    BasePreview {
        MasterView(viewModel = viewModel<MasterViewViewModel>(), onBack = {})
    }
}