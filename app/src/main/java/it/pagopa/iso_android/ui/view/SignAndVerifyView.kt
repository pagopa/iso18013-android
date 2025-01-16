package it.pagopa.iso_android.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.GenericDialog
import it.pagopa.iso_android.ui.LoaderDialog
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.preview.ThemePreviews
import it.pagopa.iso_android.ui.view_model.SignAndVerifyViewViewModel

@Composable
fun SignAndVerifyView(
    vm: SignAndVerifyViewViewModel?,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        MediumText(
            modifier = Modifier.wrapContentSize(),
            text = "String to sign",
            color = MaterialTheme.colorScheme.onBackground
        )
        TextField(
            modifier = Modifier
                .wrapContentHeight(),
            value = vm?.stringToSign?.value.orEmpty(),
            onValueChange = {
                vm?.stringToSign?.value = it
            }
        )
        Button(onClick = {
            vm?.sign(vm.stringToSign.value)
        }) {
            Text(text = "sign it")
        }
        MediumText(
            modifier = Modifier.wrapContentSize(),
            text = "To check",
            color = MaterialTheme.colorScheme.onBackground
        )
        TextField(
            modifier = Modifier
                .wrapContentHeight(),
            value = vm?.stringToCheck?.value.orEmpty(),
            onValueChange = {
                vm?.stringToCheck?.value = it
            }
        )
        Spacer(Modifier.height(16.dp))
        MediumText(
            modifier = Modifier.wrapContentSize(),
            text = "PublicKey",
            color = MaterialTheme.colorScheme.onBackground
        )
        TextField(
            modifier = Modifier
                .wrapContentHeight(),
            value = vm?.publicKey?.value.orEmpty(),
            onValueChange = {
                vm?.publicKey?.value = it
            }
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            vm?.verify()
        }) {
            Text(text = "check it all")
        }
        Spacer(Modifier.height(8.dp))
        MediumText(
            modifier = Modifier.wrapContentSize(),
            text = "is valid: ${vm?.isValidString?.value}",// text is valid or not
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(16.dp))
    }
    vm?.appDialog?.value?.let {
        GenericDialog(it)
    }
    vm?.let {
        LoaderDialog(it.loader)
    }
}

@ThemePreviews
@Composable
fun SignAndVerifyViewPreview() {
    BasePreview {
        SignAndVerifyView(vm = null) {

        }
    }
}