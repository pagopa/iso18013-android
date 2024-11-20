package it.pagopa.iso_android.ui.model

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
}

@Serializable
data class DocIssuerSigned(val issuerAuth: String?, val nameSpaces: String?) {
    companion object {
        fun fromJson(json: JSONObject?): DocIssuerSigned? {
            if(json==null) return null
            return DocIssuerSigned(
                json.optString("issuerAuth"),
                json.optJSONObject("nameSpaces")?.toString()
            )
        }
    }
}
