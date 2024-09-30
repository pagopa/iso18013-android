package it.pagopa.cbor_implementation.model

import com.upokecenter.cbor.CBORObject
import it.pagopa.cbor_implementation.helper.parse

data class ModelMDoc(
    var documents: List<Document>?,
    var status: Int?,
    var version: String?
) {
    companion object {
        fun fromCBORObject(model: CBORObject): ModelMDoc =
            ModelMDoc(
                version = model.get("version").AsString(),
                status = model.get("status").AsInt32(),
                documents = model.get("documents").values
                    .map { doc ->
                        Document(
                            docType = doc.get("docType").AsString(),
                            issuerSigned = IssuerSigned(
                                nameSpaces = doc.get("issuerSigned").get("nameSpaces").keys
                                    .let { keys ->
                                        val mNameSpaces = mutableMapOf<String, List<DocumentX>>()

                                        keys
                                            .distinct()
                                            .forEach { key ->
                                                val mList = mutableListOf<DocumentX>()

                                                doc.get("issuerSigned").get("nameSpaces").get(key).values.forEach {
                                                    val value = CBORObject.DecodeFromBytes(it.GetByteString())
                                                    mList.add(
                                                        DocumentX(
                                                            digestID = value.get("digestID").AsInt32(),
                                                            random = value.get("random").GetByteString(),
                                                            elementIdentifier = value.get("elementIdentifier").AsString(),
                                                            elementValue = value.get("elementValue").parse()
                                                        )
                                                    )
                                                }

                                                mNameSpaces[key.AsString()] = mList
                                            }

                                        mNameSpaces
                                    }
                            )
                        )
                    }
            )
    }
}

data class Document(
    var docType: String?,
    var issuerSigned: IssuerSigned?
)

data class IssuerSigned(
    var nameSpaces: Map<String, List<DocumentX>>?
)

data class DocumentX(
    val digestID: Int,
    val random: ByteArray,
    val elementIdentifier: String,
    val elementValue: Any?
)


