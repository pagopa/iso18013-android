package it.pagopa.iso_android.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.pagopa.io.wallet.proximity.nfc.NfcEngagementService
import it.pagopa.iso_android.navigation.getActivity
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.CenteredComposable
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.LoaderDialog
import it.pagopa.iso_android.ui.preview.ThemePreviews
import it.pagopa.iso_android.ui.view_model.NfcEngagementViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun NfcEngagementView(
    viewModel: NfcEngagementViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val back = {
        NfcEngagementService.disable(ctx.getActivity()!!)
        onBack.invoke()
    }
    BackHandler(onBack = back)
    LaunchedEffect(viewModel) {
        viewModel.observeEvents()
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
            text = "NFC Test",
            color = MaterialTheme.colorScheme.primary
        )
        CenteredComposable(
            modifier = Modifier
                .height(0.dp)
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text("put the device near to the other")
        }
    }
    viewModel.dialog.value?.let {
        GenericDialog(it)
    }
    LoaderDialog(viewModel.loader)
}

@ThemePreviews
@Composable
fun NfcEngagementViewPreview() {
    BasePreview {
        NfcEngagementView(
            viewModel = viewModel<NfcEngagementViewModel>(),
            onBack = {})
    }
}