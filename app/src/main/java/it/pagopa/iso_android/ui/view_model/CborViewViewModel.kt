package it.pagopa.iso_android.ui.view_model

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import it.pagopa.io.wallet.cbor.CborLogger
import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.cbor.model.DocsModel
import it.pagopa.io.wallet.cbor.parser.CBorParser
import it.pagopa.iso_android.ui.BigText
import it.pagopa.iso_android.ui.MediumText
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

class CborViewViewModel : ViewModel() {
    var cborText by mutableStateOf(
        ""
    )
    private val separateElementIdentifier = true
    var model by mutableStateOf<DocsModel?>(null)
    var errorToShow by mutableStateOf<Pair<String, String>?>(null)
    fun decodeMDoc() {
        CBorParser(this.cborText).documentsCborToJson(separateElementIdentifier, onComplete = {
            CborLogger.i(
                "x5chain",
                JSONObject(it).optJSONArray("documents")?.optJSONObject(0)
                    ?.optJSONObject("issuerSigned")
                    ?.optJSONObject("issuerAuth")
                    ?.optJSONArray("unprotectedHeader")
                    ?.toString().orEmpty()
            )
            this@CborViewViewModel.model = DocsModel.fromJson(JSONObject(it))
            this@CborViewViewModel.errorToShow = null
        }) { ex ->
            this@CborViewViewModel.model = null
            this@CborViewViewModel.errorToShow = "Exception" to ex.message.orEmpty()
        }
    }

    fun modelToList(
        lazyColumnScope: LazyListScope
    ) {
        this.model?.let { model ->
            model.docList?.forEach { doc ->
                doc.docType?.let { lazyColumnScope.title(it) }
                doc.issuerSigned?.nameSpaces?.let { nameSpaces ->
                    val obj = JSONObject(nameSpaces)
                    obj.optString(DocType(doc.docType).nameSpacesValue)
                        .let { nameSpacesArrayString ->
                            val nameSpacesArray = JSONArray(nameSpacesArrayString)
                            for (i in 0 until nameSpacesArray.length()) {
                                nameSpacesArray.optJSONObject(i)?.let { currentJson ->
                                    if (separateElementIdentifier) {
                                        val elementIdentifier = currentJson.get("elementIdentifier")
                                        if (elementIdentifier == "portrait" || elementIdentifier == "signature_usual_mark") {
                                            val bArray: ByteArray? =
                                                when (currentJson.get("elementValue")) {
                                                    is ByteArray -> currentJson.get("elementValue") as ByteArray
                                                    is String -> Base64.getUrlDecoder()
                                                        .decode(currentJson.get("elementValue") as String)

                                                    else -> null
                                                }
                                            bArray?.let {
                                                lazyColumnScope.lazyColumnItem(
                                                    (elementIdentifier as? String).orEmpty(),
                                                    bArray
                                                )
                                            }
                                        } else {
                                            if (elementIdentifier != "random" && elementIdentifier != "digestID") {
                                                lazyColumnScope.lazyColumnItem(
                                                    (elementIdentifier as? String).orEmpty(),
                                                    currentJson.get("elementValue")
                                                )
                                            }
                                        }
                                    } else {
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
                    value.ImageOrEmpty()
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

@Composable
private fun ByteArray.ImageOrEmpty() {
    val bitmap = try {
        BitmapFactory.decodeByteArray(
            this,
            0,
            this.size
        ).asImageBitmap()
    } catch (e: Exception) {
        CborLogger.i("EXCEPTION IN IMAGE", e.message.orEmpty())
        null
    }
    if (bitmap != null) {
        Image(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            bitmap = bitmap,
            contentDescription = null
        )
    }
}