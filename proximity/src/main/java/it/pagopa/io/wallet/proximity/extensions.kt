package it.pagopa.io.wallet.proximity

import org.json.JSONObject

internal fun Array<JSONObject?>.toRequest(): JSONObject {
    val jsonToSend = JSONObject()
    this.forEach {
        if (it != null) {
            val start = it.getJSONObject("values")
            val docType = it.getString("docType")
            if (!jsonToSend.has("request")) {
                jsonToSend.put("request", JSONObject().apply {
                    put(docType, start)
                })
            } else {
                jsonToSend.getJSONObject("request").put(docType, start)
            }
            jsonToSend.put("isAuthenticated", it.getBoolean("isAuthenticated"))
        }
    }
    return jsonToSend
}
