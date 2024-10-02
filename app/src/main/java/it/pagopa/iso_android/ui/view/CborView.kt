package it.pagopa.iso_android.ui.view

import android.content.res.Configuration
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.pagopa.iso_android.ui.BasePreview
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.view_model.CborViewViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CborView(
    onBack: () -> Unit
) {
    val vm = viewModel<CborViewViewModel>()
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
                        .wrapContentHeight()
                        .padding(bottom = 16.dp),
                    value = vm.cborText,
                    onValueChange = {
                        vm.cborText = it.trim()
                        scope.launch {
                            delay(500L)
                            listState.scrollToItem(
                                if (vm.listToShow?.isNotEmpty() == true) 1 else 0
                            )
                        }
                    }
                )
            }
            vm.listToShow
                ?.forEach {
                    vm.mapToLazyColumnItem(map = it, lazyColumnScope = this)
                }
        }
    }
}


@Preview
@Composable
fun CborViewView() {
    BasePreview {
        CborView(onBack = {

        })
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
fun CborViewViewNight() {
    BasePreview {
        CborView(onBack = {

        })
    }
}