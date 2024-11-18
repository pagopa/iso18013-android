package it.pagopa.iso_android.ui.view_model

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import it.pagopa.cbor_implementation.impl.MDoc
import it.pagopa.cbor_implementation.model.DocType
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.MediumText
import it.pagopa.iso_android.ui.model.DocsModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

class CborViewViewModel : ViewModel() {
    var cborText by mutableStateOf("")
    var model by mutableStateOf<DocsModel?>(null)
    var errorToShow by mutableStateOf<Pair<String, String>?>(null)
    fun decodeMDoc() {
        val mDoc = MDoc(this.cborText)
        mDoc.decodeMDoc(
            onComplete = { model ->
                val json = JSONObject(model.toJson())
                this@CborViewViewModel.model = DocsModel.fromJson(json)
                this@CborViewViewModel.errorToShow = null
            },
            onError = { ex ->
                this@CborViewViewModel.model = null
                this@CborViewViewModel.errorToShow = "Exception" to ex.message.orEmpty()
            }
        )
    }

    fun modelToList(
        lazyColumnScope: LazyListScope
    ) {
        this.model?.let { model ->
            model.docList?.forEach { doc ->
                doc.docType?.let { lazyColumnScope.title(it) }
                doc.issuerSigned?.nameSpaces?.let { nameSpaces ->
                    val array = JSONArray(nameSpaces)
                    if (array.length() > 0) {
                        array.optJSONObject(0)?.optString(DocType(doc.docType).nameSpacesValue)
                            ?.let { nameSpacesArrayString ->
                                val nameSpacesArray = JSONArray(nameSpacesArrayString)
                                for (i in 0 until nameSpacesArray.length()) {
                                    nameSpacesArray.optJSONObject(i)?.let { currentJson ->
                                        currentJson.keys().forEach { key ->
                                            if (key == "portrait" || key == "signature_usual_mark") {
                                                val bArray: ByteArray? =
                                                    when (currentJson.get(key)) {
                                                        is ByteArray -> currentJson.get(key) as ByteArray
                                                        is String -> Base64.getDecoder()
                                                            .decode(currentJson.get(key) as String)

                                                        else -> null
                                                    }
                                                bArray?.let {
                                                    lazyColumnScope.lazyColumnItem(
                                                        key,
                                                        bArray
                                                    )
                                                }
                                            } else {
                                                if (key != "random" && key != "digestID") {
                                                    lazyColumnScope.lazyColumnItem(
                                                        key,
                                                        currentJson.get(key)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }

    fun errorToShowToComposable(lazyColumnScope: LazyListScope) {
        errorToShow?.let {
            lazyColumnScope.lazyColumnItem(it.first, it.second)
        }
    }

    private fun LazyListScope.title(title: String) {
        item {
            HorizontalDivider(
                Modifier
                    .padding(top = 16.dp)
            )
            BigText(
                modifier = Modifier
                    .padding(top = 16.dp),
                text = "$title:",
                color = Color.Black
            )
        }
    }

    private fun LazyListScope.lazyColumnItem(
        key: String,
        value: Any
    ) {
        item {
            MediumText(
                modifier = Modifier
                    .padding(top = 16.dp),
                text = "$key:",
                color = Color.Black
            )
        }
        if (key == "driving_privileges") {
            val array = JSONArray(value.toString())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                obj.keys().forEach {
                    item {
                        MediumText(
                            modifier = Modifier
                                .padding(top = 8.dp),
                            text = "$it: ${obj.get(it)}",
                            color = Color.Black
                        )
                    }
                }
            }
        } else {
            item {
                if (value is ByteArray) {
                    Image(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth(),
                        bitmap = BitmapFactory.decodeByteArray(
                            value,
                            0,
                            value.size
                        ).asImageBitmap(),
                        contentDescription = null
                    )
                } else {
                    MediumText(
                        modifier = Modifier
                            .padding(top = 16.dp),
                        text = value.toString(),
                        color = Color.Black
                    )
                }
            }
        }
    }
}