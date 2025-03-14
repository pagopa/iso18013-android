package it.pagopa.io.wallet.cbor.parser

import com.upokecenter.cbor.CBORObject
import it.pagopa.io.wallet.cbor.CborLogger
import it.pagopa.io.wallet.cbor.impl.MDoc
import it.pagopa.io.wallet.cbor.model.DocsModel
import it.pagopa.io.wallet.cbor.model.IssuerSigned
import org.json.JSONObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Class to parse cbor raw value to json string, it accepts both bytes[] value or Base64 string*/
class CBorParser private constructor(
    private val source: Any,
    private val isByteArray: Boolean = false
) {
    constructor(source: String) : this(source, false)
    constructor(source: ByteArray) : this(source, true)

    /**
     * It parses a Cbor raw value to a json string without any other intervention as [documentsCborToJson] or [issuerSignedCborToJson]*/
    @OptIn(ExperimentalEncodingApi::class)
    fun toJson(): String? {
        return try {
            CBORObject.DecodeFromBytes(
                if (isByteArray) source as ByteArray
                else Base64.decode(source as String)
            )?.ToJSONString()
        } catch (_: Exception) {
            null
        }
    }


    /**
     * It parses a document Cbor raw value to a json string
     * @param separateElementIdentifier false if you want to merge elementIdentifier and elementValue as i.e.:
     * {
     *   "random": "749knalJ0xgtGBK1lVhhXe8D-beFtIiXyln1UhmTPKmbNmTwvolOya3h-_AyFE2MqCom3sBYs8VylU238nkOTA==",
     *   "digestID": 14,
     *   "family_name": "ANDERSSON"
     * } else i.e.:
     * {
     *   "random": "749knalJ0xgtGBK1lVhhXe8D-beFtIiXyln1UhmTPKmbNmTwvolOya3h-_AyFE2MqCom3sBYs8VylU238nkOTA==",
     *   "digestID": 14,
     *   "elementIdentifier": "family_name",
     *   "elementValue": "ANDERSSON"
     * }
     * @param onComplete callback with the json string
     * @param onError callback with the error as [Exception]*/
    fun documentsCborToJson(
        separateElementIdentifier: Boolean = true,
        onComplete: (String) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ) {
        val mDoc = if (isByteArray) MDoc(
            source = this.source as ByteArray
        ) else MDoc(
            source = this.source as String
        )
        mDoc.decodeMDoc(onComplete = { model ->
            val json = JSONObject(model.toJson(separateElementIdentifier))
            val model = DocsModel.fromJson(json)
            onComplete.invoke(model.toJson().toString())
        }, onError = onError ?: {})
    }
    /**
     * As [documentsCborToJson] It parses a issuerSigned Cbor raw value to a json string
     * @param separateElementIdentifier false if you want to merge elementIdentifier and elementValue as i.e.:
     * {
     *   "random": "749knalJ0xgtGBK1lVhhXe8D-beFtIiXyln1UhmTPKmbNmTwvolOya3h-_AyFE2MqCom3sBYs8VylU238nkOTA==",
     *   "digestID": 14,
     *   "family_name": "ANDERSSON"
     * } else i.e.:
     * {
     *   "random": "749knalJ0xgtGBK1lVhhXe8D-beFtIiXyln1UhmTPKmbNmTwvolOya3h-_AyFE2MqCom3sBYs8VylU238nkOTA==",
     *   "digestID": 14,
     *   "elementIdentifier": "family_name",
     *   "elementValue": "ANDERSSON"
     * }
     * @return json string or null if error*/
    @OptIn(ExperimentalEncodingApi::class)
    fun issuerSignedCborToJson(
        separateElementIdentifier: Boolean = true
    ): String? {
        return try {
            val rawValue = if (isByteArray)
                this.source as ByteArray
            else
                Base64.decode(this.source as String)
            IssuerSigned
                .issuerSignedFromByteArray(rawValue)?.toJson(separateElementIdentifier)?.toString()
        } catch (e: Exception) {
            CborLogger.e("ISSUER_SIGNED DECODING EX.", "$e")
            null
        }
    }
}