package it.pagopa.io.wallet.cbor.model

/**An enum class to specify the correct [IssuerAuth.UnprotectedHeader] while parsing to
 * [org.json.JSONObject] the [com.upokecenter.cbor.CBORObject] representing the document*/
internal enum class COSEHeaderAlgorithm(
    val value: Int,
    var valueName: String
) {
    ALG(1, "alg"),//Cryptographic algorithm
    CRIT(2, "crit"),//Critical headers to be understood
    CONTENT_TYPE(3, "content_type"),//Payload content type
    KID(4, "kid"),//Key identifier
    IV(5, "iv"),//Initialization vector
    PARTIAL_IV(6, "partial_iv"),//Partial initialization vector
    COUNTER_SIGNATURE(7, "counter_signature"),//CBOR ENCODED SIGNATURE structure
    X5BAG(32, "x5bag"),//An unordered bag of X.509 certificates
    X5CHAIN(33, "x5chain"),//An ordered chain of X.509 certificates
    X5T(34, "x5t"),//Hash of an X.509 certificate
    X5U(35, "x5u");//URI pointing to an X.509 certificate

    companion object {
        fun fromInt(from: Int) = entries.firstOrNull {
            it.value == from
        }?.valueName ?: from.toString()
    }
}