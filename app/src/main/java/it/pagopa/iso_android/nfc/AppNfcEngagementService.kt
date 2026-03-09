package it.pagopa.iso_android.nfc

import it.pagopa.io.wallet.proximity.nfc.NfcEngagementService
import org.json.JSONObject

class AppNfcEngagementService : NfcEngagementService() {
    // HERE WE CAN DECIDE TO not send some fields respect the request from reader or to send all putting it to true
    override fun nfcOnlyFieldAcceptation(
        jsonString: String
    ): String {
        val originalReq = JSONObject(jsonString).optJSONObject("request")
        val jsonAccepted = JSONObject()
        originalReq?.keys()?.forEach {
            originalReq.optJSONObject(it)?.let { json ->
                val keyJson = JSONObject()
                json.keys().forEach { key ->
                    json.optJSONObject(key)?.let { internalJson ->
                        val internalNewJson = JSONObject()
                        internalJson.keys().forEach { dataKey ->
                            internalNewJson.put(dataKey, true)
                        }
                        keyJson.put(key, internalNewJson)
                    }
                }
                jsonAccepted.put(it, keyJson)
            }
        }
        return jsonAccepted.toString()
    }
}