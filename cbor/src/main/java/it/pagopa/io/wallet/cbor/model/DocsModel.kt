package it.pagopa.io.wallet.cbor.model

import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject

@Serializable
data class DocsModel(
    val docList: List<DocModel>?,
    var status: Int?,
    var version: String?
) {
    companion object {
        fun fromJson(json: JSONObject): DocsModel {
            return DocsModel(
                DocModel.fromJsonArray(JSONArray(json.getString("documents"))),
                json.optInt("status"),
                json.optString("version")
            )
        }
    }

    fun toJson(separateElementIdentifier: Boolean = true) = JSONObject().apply {
        put("documents", JSONArray().apply {
            this@DocsModel.docList?.forEach { doc ->
                this.put(doc.toJson(separateElementIdentifier))
            }
        })
    }
}

@Serializable
data class DocModel(val docType: String?, val issuerSigned: DocIssuerSigned?) {
    companion object {
        fun fromJsonArray(jsonArray: JSONArray?): List<DocModel> {
            val backList = ArrayList<DocModel>()
            if (jsonArray != null)
                for (i in 0 until jsonArray.length()) {
                    backList.add(
                        DocModel(
                            jsonArray.optJSONObject(i)?.optString("docType"),
                            DocIssuerSigned.fromJson(
                                jsonArray.optJSONObject(i)?.optJSONObject("issuerSigned")
                            )
                        )
                    )
                }
            return backList.toList()
        }
    }

    fun toJson(separateElementIdentifier: Boolean = true) = JSONObject().apply {
        put("docType", this@DocModel.docType)
        put(
            "issuerSigned", this@DocModel.issuerSigned?.toJson(
                this@DocModel.docType,
                separateElementIdentifier
            )
        )
    }
}

@Serializable
data class DocIssuerSigned(val issuerAuth: String?, val nameSpaces: String?) {
    companion object {
        fun fromJson(json: JSONObject?): DocIssuerSigned? {
            if (json == null) return null
            return DocIssuerSigned(
                json.optString("issuerAuth"),
                json.optJSONObject("nameSpaces")?.toString()
            )
        }
    }

    fun toJson(docType: String?, separateElementIdentifier: Boolean = true) = JSONObject().apply {
        put("issuerAuth", this@DocIssuerSigned.issuerAuth)
        if (this@DocIssuerSigned.nameSpaces == null)
            put("nameSpaces", null)
        else {
            val jsonBack = JSONObject()
            val obj = JSONObject(this@DocIssuerSigned.nameSpaces)
            val docTypeNamespaces = DocType(docType).nameSpacesValue
            obj.optString(docTypeNamespaces)
                .let { nameSpacesArrayString ->
                    val nameSpacesArray = JSONArray(nameSpacesArrayString)
                    val docTypeJsonArray = JSONArray()
                    for (i in 0 until nameSpacesArray.length()) {
                        nameSpacesArray.optJSONObject(i)?.let { currentJson ->
                            currentJson.keys().forEach { key ->
                                val jsonToAdd = JSONObject()
                                if (separateElementIdentifier) {
                                    jsonToAdd.put("elementIdentifier", key)
                                    jsonToAdd.put("elementValue", currentJson.get(key))
                                } else
                                    jsonToAdd.put(key, currentJson.get(key))
                                docTypeJsonArray.put(jsonToAdd)
                            }
                        }
                    }
                    jsonBack.put(docTypeNamespaces, docTypeJsonArray)
                }
            put("nameSpaces", jsonBack)
        }
    }
}