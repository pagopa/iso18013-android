package it.pagopa.iso_android.ui.view

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.pagopa.iso_android.base64mockedMoreDocsNoIssuerAuth
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.preview.ThemePreviews
import it.pagopa.iso_android.ui.view_model.CborViewViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CborView(
    vm: CborViewViewModel,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    LaunchedEffect(
        key1 = vm.cborText,
        block = {
            vm.decodeMDoc()
        }
    )
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                BigText(
                    modifier = Modifier.padding(vertical = 16.dp),
                    text = "Insert CBOR String Here",
                    color = MaterialTheme.colorScheme.primary
                )
                Button(modifier = Modifier.padding(bottom = 16.dp), onClick = {
                    vm.cborText = ""
                }) {
                    Text("clear CBOR text")
                }
                TextField(
                    modifier = Modifier
                        .wrapContentHeight(),
                    value = vm.cborText,
                    onValueChange = {
                        vm.cborText = it.trim()
                        scope.launch {
                            delay(500L)
                            listState.scrollToItem(
                                if (vm.model != null || vm.errorToShow != null) 1 else 0
                            )
                        }
                    }
                )
            }
            vm.modelToList(this)
            vm.errorToShowToComposable(this)
        }
    }
}

@ThemePreviews
@Composable
fun CborViewView() {
    BasePreview {
        val viewModel = viewModel<CborViewViewModel>()
        CborView(vm = viewModel.apply {
            cborText = base64mockedMoreDocsNoIssuerAuth
        }, onBack = {

        })
    }
}