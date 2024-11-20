package it.pagopa.proximity

import org.json.JSONObject

fun Array<JSONObject?>.toRequest(): JSONObject{
    val jsonToSend = JSONObject()
    this.forEach {
        if (it != null) {
            val start = it.getJSONObject("values")
            val docTypeNameSpace = start.keys().asSequence().first()
            val docType = it.getString("docType")
            val nameSpaces = start.getJSONObject(docTypeNameSpace)
            if (!jsonToSend.has("request")) {
                jsonToSend.put("request", JSONObject().apply {
                    put(docType, JSONObject().apply {
                        nameSpaces.keys().forEach {
                            put(it, true)
                        }
                    })
                })
            } else {
                jsonToSend.getJSONObject("request").put(docType, JSONObject().apply {
                    nameSpaces.keys().forEach {
                        put(it, true)
                    }
                })
            }
            jsonToSend.put("isAuthenticated", it.getBoolean("isAuthenticated"))
        }
    }
    return jsonToSend
}