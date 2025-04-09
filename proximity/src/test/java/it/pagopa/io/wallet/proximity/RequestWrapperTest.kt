package it.pagopa.io.wallet.proximity

import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.proximity.request.RequestWrapper
import org.json.JSONObject
import org.junit.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class RequestWrapperTest {
    private val mockEuPidRequest by lazy {
        "omdkb2NUeXBld2V1LmV1cm9wYS5lYy5ldWRpLnBpZC4xam5hbWVTcGFjZXOhd2V1LmV1cm9wYS5lYy5ldWRpLnBpZC4xuCRrZmFtaWx5X25hbWX0amdpdmVuX25hbWX0amJpcnRoX2RhdGX0a2FnZV9vdmVyXzE49GthZ2Vfb3Zlcl8xM/RrYWdlX292ZXJfMTb0a2FnZV9vdmVyXzIx9GthZ2Vfb3Zlcl82MPRrYWdlX292ZXJfNjX0a2FnZV9vdmVyXzY49GxhZ2VfaW5feWVhcnP0bmFnZV9iaXJ0aF95ZWFy9HFmYW1pbHlfbmFtZV9iaXJ0aPRwZ2l2ZW5fbmFtZV9iaXJ0aPRrYmlydGhfcGxhY2X0bWJpcnRoX2NvdW50cnn0a2JpcnRoX3N0YXRl9GpiaXJ0aF9jaXR59HByZXNpZGVudF9hZGRyZXNz9HByZXNpZGVudF9jb3VudHJ59G5yZXNpZGVudF9zdGF0ZfRtcmVzaWRlbnRfY2l0efR0cmVzaWRlbnRfcG9zdGFsX2NvZGX0b3Jlc2lkZW50X3N0cmVldPR1cmVzaWRlbnRfaG91c2VfbnVtYmVy9GZnZW5kZXL0a25hdGlvbmFsaXR59G1pc3N1YW5jZV9kYXRl9GtleHBpcnlfZGF0ZfRxaXNzdWluZ19hdXRob3JpdHn0b2RvY3VtZW50X251bWJlcvR1YWRtaW5pc3RyYXRpdmVfbnVtYmVy9G9pc3N1aW5nX2NvdW50cnn0dGlzc3VpbmdfanVyaXNkaWN0aW9u9Ghwb3J0cmFpdPR1cG9ydHJhaXRfY2FwdHVyZV9kYXRl9A=="
    }
    private val mockMdlRequest by lazy {
        "omdkb2NUeXBldW9yZy5pc28uMTgwMTMuNS4xLm1ETGpuYW1lU3BhY2VzoXFvcmcuaXNvLjE4MDEzLjUuMbgga2ZhbWlseV9uYW1l9GpnaXZlbl9uYW1l9GpiaXJ0aF9kYXRl9Gppc3N1ZV9kYXRl9GtleHBpcnlfZGF0ZfRvaXNzdWluZ19jb3VudHJ59HFpc3N1aW5nX2F1dGhvcml0efRvZG9jdW1lbnRfbnVtYmVy9Ghwb3J0cmFpdPRyZHJpdmluZ19wcml2aWxlZ2Vz9HZ1bl9kaXN0aW5ndWlzaGluZ19zaWdu9HVhZG1pbmlzdHJhdGl2ZV9udW1iZXL0ZmhlaWdodPRmd2VpZ2h09GpleWVfY29sb3Vy9GtoYWlyX2NvbG91cvRrYmlydGhfcGxhY2X0cHJlc2lkZW50X2FkZHJlc3P0dXBvcnRyYWl0X2NhcHR1cmVfZGF0ZfRsYWdlX2luX3llYXJz9G5hZ2VfYmlydGhfeWVhcvRrYWdlX292ZXJfMTj0a2FnZV9vdmVyXzIx9HRpc3N1aW5nX2p1cmlzZGljdGlvbvRrbmF0aW9uYWxpdHn0bXJlc2lkZW50X2NpdHn0bnJlc2lkZW50X3N0YXRl9HRyZXNpZGVudF9wb3N0YWxfY29kZfRwcmVzaWRlbnRfY291bnRyefR4HmZhbWlseV9uYW1lX25hdGlvbmFsX2NoYXJhY3RlcvR4HWdpdmVuX25hbWVfbmF0aW9uYWxfY2hhcmFjdGVy9HRzaWduYXR1cmVfdXN1YWxfbWFya/Q="
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `test request from device JSON`() {
        val requestWrapperList = arrayListOf<JSONObject?>()
        val req = RequestWrapper(Base64.decode(mockEuPidRequest), false).prepare().toJson()
        val req1 = RequestWrapper(Base64.decode(mockMdlRequest), false).prepare().toJson()
        requestWrapperList.addAll(listOf(req, req1))
        val jsonToSend = requestWrapperList.toTypedArray().toRequest()
        println(jsonToSend)
        assert(jsonToSend.keys().asSequence().count() == 2)
        assert(!jsonToSend.getBoolean("isAuthenticated"))
        val request = jsonToSend.getJSONObject("request")
        val euPidJsonInside = request
            .getJSONObject(DocType.EU_PID.value)
            .getJSONObject(DocType.EU_PID.nameSpacesValue)
        val mdlJsonInside = request
            .getJSONObject(DocType.MDL.value)
            .getJSONObject(DocType.MDL.nameSpacesValue)
        println(euPidJsonInside)
        println(mdlJsonInside)
        assert(euPidJsonInside.keys().asSequence().map {
            !euPidJsonInside.getBoolean(it)
        }.first())
        assert(mdlJsonInside.keys().asSequence().map {
            !mdlJsonInside.getBoolean(it)
        }.first())
    }
}