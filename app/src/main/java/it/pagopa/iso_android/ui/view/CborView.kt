package it.pagopa.iso_android.ui.view

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import it.pagopa.cbor_implementation.impl.MDoc
import it.pagopa.iso_android.ui.BasePreview

@Composable
fun CborView(
    onBack: () -> Unit
) {
    var cborText by remember { mutableStateOf("") }

    val listToShow = remember(cborText) {
        try {
            MDoc().decodeMDoc(cborText).documents
                ?.map {
                    it.issuerSigned?.nameSpaces
                }
        } catch (e: Exception) {
            listOf()
        }
    }

    BackHandler {
        onBack.invoke()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            modifier = Modifier
                .height(100.dp),
            value = cborText,
            onValueChange = {
                cborText = it
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.Bottom
        ) {
            listToShow
                ?.forEach {
                    it?.keys?.forEach { key ->
                        item {
                            Text(
                                modifier = Modifier
                                    .padding(top = 16.dp),
                                text = key,
                                color = Color.Black
                            )
                        }
                        items(
                            items = it[key].orEmpty(),
                            itemContent = { item ->
                                Column(
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .fillMaxWidth()
                                        .background(Color.LightGray)
                                        .padding(8.dp),
                                ) {
                                    Text(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        text = "${item.elementIdentifier} (${item.elementValue?.javaClass?.name})"
                                    )

                                    if (item.elementIdentifier == "portrait") {
                                        Image(
                                            modifier = Modifier
                                                .padding(top = 16.dp)
                                                .fillMaxWidth(),
                                            bitmap = BitmapFactory.decodeByteArray(item.elementValue as ByteArray, 0, (item.elementValue as ByteArray).size)
                                                .asImageBitmap(),
                                            contentDescription = null
                                        )
                                    } else {
                                        Text(
                                            modifier = Modifier
                                                .padding(top = 16.dp)
                                                .fillMaxWidth(),
                                            text = item.elementValue.toString()
                                        )
                                    }
                                }
                            }
                        )
                    }
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