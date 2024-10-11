package it.pagopa.iso_android.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import it.pagopa.cbor_implementation.document_manager.DocumentManager
import it.pagopa.cbor_implementation.document_manager.DocumentManagerBuilder
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.view_model.DocumentStorageViewViewModel
import it.pagopa.iso_android.ui.view_model.viewModelWithDocManager

@Composable
fun DocumentStorageView(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val vm = viewModelWithDocManager<DocumentStorageViewViewModel>(
        dm = DocumentManager.build(
            DocumentManagerBuilder(
                context = context
            ).enableUserAuth(false)
                .checkPublicKeyBeforeAdding(false)
        )
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            vm.actions.forEachIndexed { i, each ->
                Button(onClick = {
                    vm.manageActions(i)
                }) {
                    Text(each)
                }
            }
        }
    }
    vm.appDialog.value?.let { appDialog ->
        GenericDialog(appDialog)
    }
}

@Preview
@Composable
fun DocStoragePreview() {
    BasePreview {
        DocumentStorageView { }
    }
}