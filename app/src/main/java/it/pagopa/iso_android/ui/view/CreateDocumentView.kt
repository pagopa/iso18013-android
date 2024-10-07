package it.pagopa.iso_android.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.view_model.CreateDocumentViewViewModel
import it.pagopa.iso_android.ui.view_model.viewModelWithResources

@Composable
fun CreateDocumentView(onBack: () -> Unit) {
    val context = LocalContext.current
    val vm = viewModelWithResources<CreateDocumentViewViewModel>(context.resources)
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        MediumText(
            modifier = Modifier.wrapContentSize(),
            text = "Use retrieve before",
            color = MaterialTheme.colorScheme.primary
        )
        Switch(checked = vm.switchChecked.value,
            onCheckedChange = {
                vm.switchChecked.value = it
                vm.useRetrieveBefore = it
            }
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.dp)
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Button(modifier = Modifier.align(Alignment.Center), onClick = {
                vm.manageDocument(context)
            }) {
                Text(text = vm.buttonText.value)
            }
        }
    }
    if (vm.appDialog.value != null) {
        GenericDialog(
            dialog = vm.appDialog.value!!
        )
    }
}

@Preview
@Composable
fun CreateDocumentViewPreview() {
    BasePreview {
        CreateDocumentView {

        }
    }
}