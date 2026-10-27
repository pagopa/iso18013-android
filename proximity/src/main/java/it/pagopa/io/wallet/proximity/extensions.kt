package it.pagopa.io.wallet.proximity

import org.json.JSONObject

/** Keys of the document request metadata copied as they are next to the requested namespaces */
private val REQUEST_METADATA_KEYS =
    listOf("isSignatureValid", "readerAuth", "readerCertificateChain", "requestInfo")

internal fun Array<JSONObject?>.toRequest(): JSONObject {
    val request = JSONObject()
    this.filterNotNull().forEach {
        val document = it.getJSONObject("values").apply {
            put("isAuthenticated", it.getBoolean("isAuthenticated"))
            put("certificateData", it.optJSONObject("issuerRdnMap"))
            REQUEST_METADATA_KEYS.forEach { key -> it.opt(key)?.let { value -> put(key, value) } }
        }
        request.put(it.getString("docType"), document)
    }
    return if (request.length() == 0) JSONObject() else JSONObject().put("request", request)
}
