package it.pagopa.iso_android.ui.view_model

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import it.pagopa.cbor_implementation.impl.MDoc
import it.pagopa.cbor_implementation.model.DocumentX
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class CborViewViewModel : ViewModel() {
    var cborText by mutableStateOf("")
    var listToShow by mutableStateOf<List<Map<String, List<DocumentX>>?>?>(null)
    fun decodeMDoc() {
        val mDoc = MDoc(this.cborText)
        mDoc.decodeMDoc(
            onComplete = { list ->
                this.listToShow = list.documents?.map {
                    it.issuerSigned?.nameSpaces
                }
            },
            onError = { ex ->
                this.listToShow = listOf(
                    mapOf(
                        "Error" to listOf(
                            DocumentX(
                                digestID = 0,
                                random = ByteArray(0),
                                elementIdentifier = "Exception:",
                                elementValue = ex
                            )
                        )
                    )
                )
            }
        )
    }

    fun mapToLazyColumnItem(
        map: Map<String, List<DocumentX?>>?,
        lazyColumnScope: LazyListScope
    ) {
        map?.keys?.forEach { key ->
            lazyColumnScope.lazyColumnItem(map, key)
        }
    }

    private fun LazyListScope.lazyColumnItem(
        map: Map<String, List<DocumentX?>>,
        key: String
    ) {
        item {
            Text(
                modifier = Modifier
                    .padding(top = 16.dp),
                text = key,
                color = Color.Black
            )
        }
        items(
            items = map[key].orEmpty(),
            itemContent = { item ->
                item?.ElementDecoded()
            }
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Composable
    private fun DocumentX.ElementDecoded() {
        val item = this
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
                val bArray: ByteArray? = when (item.elementValue) {
                    is ByteArray -> item.elementValue as ByteArray
                    is String -> Base64.decode(item.elementValue as String)
                    else -> null
                }
                bArray?.let {
                    Image(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                        bitmap = BitmapFactory.decodeByteArray(
                            it,
                            0,
                            it.size
                        ).asImageBitmap(),
                        contentDescription = null
                    )
                }
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
}