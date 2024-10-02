package it.pagopa.cbor_implementation.model

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.helper.toModelMDoc

data class ModelMDoc(
    var documents: List<Document>?,
    var status: Int?,
    var version: String?
) {
    fun hasVersion() = this.version != null
    fun hasStatus() = this.status != null

    companion object {
        fun fromCBORObject(
            model: CBORObject,
            onComplete: (ModelMDoc) -> Unit,
            onError: (Exception) -> Unit
        ) {
            try {
                onComplete.invoke(model.toModelMDoc())
            } catch (ex: Exception) {
                onError.invoke(ex)
            }
        }
    }
}

data class Document(
    var docType: String?,
    var issuerSigned: IssuerSigned?
)

data class IssuerSigned(
    var nameSpaces: Map<String, List<DocumentX>>?
)

/**
 * elementIdentifier->es.:Name
 * elementValue->es:John*/
data class DocumentX(
    val digestID: Int,
    val random: ByteArray,
    val elementIdentifier: String,
    val elementValue: Any?
)


