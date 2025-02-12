package it.pagopa.io.wallet.proximity.response

import android.util.Base64
import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.RawCbor
import com.android.identity.cbor.Tagged
import com.android.identity.document.DocumentRequest
import com.android.identity.document.NameSpacedData
import com.android.identity.mdoc.mso.StaticAuthDataParser
import com.android.identity.mdoc.response.DeviceResponseGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.util.Constants
import it.pagopa.io.wallet.cbor.cose.COSEManager
import it.pagopa.io.wallet.cbor.cose.SignWithCOSEResult
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.request.DocRequested
import org.json.JSONObject
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.iterator
import it.pagopa.io.wallet.cbor.model.Document as DocumentModel

class ResponseGenerator(
    private val sessionsTranscript: ByteArray
) {
    interface Response {
        /**@param [response] [ByteArray] generated for response*/
        fun onResponseGenerated(response: ByteArray)

        /**@param [message] [String] for error reached*/
        fun onError(message: String)
    }

    /**
     * It creates a mdoc response in ByteArray format respect documents requested and disclosed
     * @return[Response.onResponseGenerated] if ByteArray is created without Exceptions, else
     * [Response.onError] if disclosedDocumentsArray is Empty with "no doc found" message or if an
     * [Exception] was reached with [Throwable.message].
     */
    @JvmName("createResponseWithCallback")
    fun createResponse(
        documents: Array<DocRequested>,
        fieldRequestedAndAccepted: String,
        response: Response
    ) {
        val (responseToSend, message) = this.createResponse(
            documents, fieldRequestedAndAccepted
        )
        responseToSend?.let {
            response.onResponseGenerated(it)
        } ?: run {
            response.onError(message)
            ProximityLogger.e(
                "Sending resp",
                "found doc but fail to generate raw response: $message"
            )
        }
    }

    /**
     * It creates a mdoc response in ByteArray format respect documents requested and disclosed
     * @return[Pair]-> out <[ByteArray],[String]> with first element nullable,
     * if ByteArray is created without Exceptions message back will be "created" else
     * [Throwable.message] reached or empty string if this is null
     */
    @JvmName("createResponseWithBase64")
    private fun createResponse(
        documents: Array<DocRequested>,
        fieldRequestedAndAccepted: String
    ): Pair<ByteArray?, String> {
        try {
            val deviceResponse = DeviceResponseGenerator(Constants.DEVICE_RESPONSE_STATUS_OK)
            val fieldsRequested = JSONObject(fieldRequestedAndAccepted)
            fieldsRequested.keys().forEach { key ->
                documents.filter {
                    val bytes = Base64.decode(it.content, Base64.DEFAULT)
                    val doc = DocumentModel.fromByteArray(bytes)
                    doc.docType == key
                }.map {
                    val bytes = Base64.decode(it.content, Base64.DEFAULT)
                    DocumentModel.fromByteArray(bytes) to it.alias
                }.forEach { (doc, alias) ->
                    addDocToResponse(
                        responseGenerator = deviceResponse,
                        document = doc,
                        fieldsRequested = fieldsRequested,
                        key = key,
                        transcript = sessionsTranscript,
                        alias = alias,
                        docType = doc.docType!!
                    )
                }
            }
            return deviceResponse.generate() to "created"
        } catch (e: Exception) {
            return null to e.message.orEmpty()
        }
    }

    private fun setDeviceNamespaces(
        sessionTranscript: ByteArray,
        alias: String, docType: String
    ): DataItem {
        val dataElements = NameSpacedData.Builder().build()
        val mapBuilder = CborMap.builder()
        for (nameSpaceName in dataElements.nameSpaceNames) {
            val nsBuilder = mapBuilder.putMap(nameSpaceName)
            for (dataElementName in dataElements.getDataElementNames(
                nameSpaceName
            )) {
                nsBuilder.put(
                    dataElementName,
                    RawCbor(
                        dataElements.getDataElement(
                            nameSpaceName,
                            dataElementName
                        )
                    )
                )
            }
        }
        mapBuilder.end()
        val encodedDeviceNameSpaces = Cbor.encode(mapBuilder.end().build())
        val deviceAuthentication = Cbor.encode(
            CborArray.builder()
                .add("DeviceAuthentication")
                .add(RawCbor(sessionTranscript))
                .add(docType)
                .addTaggedEncodedCbor(encodedDeviceNameSpaces)
                .end()
                .build()
        )
        val deviceAuthenticationBytes = Cbor.encode(
            Tagged(24, Bstr(deviceAuthentication))
        )
        var encodedDeviceSignature: ByteArray
        when (val result = COSEManager().signWithCOSE(
            data = deviceAuthenticationBytes,
            alias = alias,
            isDetached = true
        )) {
            is SignWithCOSEResult.Success -> encodedDeviceSignature = result.signature
            else -> throw IllegalArgumentException("Fail to sign Sign1 message")
        }
        val deviceAuthType = "deviceSignature"
        val deviceAuthDataItem = Cbor.decode(encodedDeviceSignature)
        return CborMap.builder()
            .putTaggedEncodedCbor("nameSpaces", encodedDeviceNameSpaces)
            .putMap("deviceAuth")
            .put(deviceAuthType, deviceAuthDataItem)
            .end()
            .end()
            .build()
    }

    private fun addDocToResponse(
        responseGenerator: DeviceResponseGenerator,
        document: DocumentModel,
        fieldsRequested: JSONObject,
        key: String,
        transcript: ByteArray,
        alias: String,
        docType: String
    ) {
        val dataElements = ArrayList<DocumentRequest.DataElement>()
        val json = fieldsRequested.optJSONObject(key)
        json?.keys()?.forEach { nameSpaceValue ->
            document.issuerSigned?.nameSpaces?.keys?.forEach { nameSpaceKey ->
                val isRequested = json.optBoolean(nameSpaceValue) == true
                if (isRequested) {
                    document.issuerSigned?.nameSpaces?.get(nameSpaceKey)?.filter {
                        it.elementIdentifier == nameSpaceValue
                    }?.forEach {
                        dataElements.add(
                            DocumentRequest.DataElement(
                                nameSpaceKey,
                                it.elementIdentifier!!,
                                false
                            )
                        )
                    }
                }
            }
        }
        val deviceSigned = setDeviceNamespaces(
            transcript,
            alias,
            docType
        )
        val staticAuthData = StaticAuthDataParser(document.issuerSigned!!.rawValue!!).parse()
        val request = DocumentRequest(dataElements)
        val issuerNamespaces = MdocUtil.mergeIssuerNamesSpaces(
            request,
            NameSpacedData.fromDataItem(nameSpacedData(document.issuerSigned!!.nameSpacedData)),
            staticAuthData
        )
        val issuerSignedMapBuilder = CborMap.builder()
        val insOuter = CborMap.builder()
        for (ns in issuerNamespaces.keys) {
            val insInner = insOuter.putArray(ns)
            for (encodedIssuerSignedItemBytes in issuerNamespaces[ns]!!) {
                insInner.add(RawCbor(encodedIssuerSignedItemBytes))
            }
            insInner.end()
        }
        insOuter.end()
        issuerSignedMapBuilder.put("nameSpaces", insOuter.end().build())
        issuerSignedMapBuilder.put("issuerAuth", RawCbor(document.issuerSigned!!.issuerAuth!!))
        val issuerSigned = issuerSignedMapBuilder.end().build()
        val mapBuilder = CborMap.builder().apply {
            put("docType", docType)
            put("issuerSigned", issuerSigned)
            put("deviceSigned", deviceSigned)
        }
        responseGenerator.addDocument(Cbor.encode(mapBuilder.end().build()))
    }

    private fun nameSpacedData(map: Map<String, Map<String, ByteArray>>): DataItem {
        CborMap.builder().run {
            for (namespaceName in map.keys) {
                val innerMapBuilder = putMap(namespaceName)
                val namespace = map[namespaceName]!!
                for ((dataElementName, dataElementValue) in namespace) {
                    innerMapBuilder.putTagged(
                        dataElementName,
                        Tagged.ENCODED_CBOR,
                        Bstr(dataElementValue)
                    )
                }
            }
            return end().build()
        }
    }
}