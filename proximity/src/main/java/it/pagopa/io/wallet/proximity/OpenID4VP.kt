package it.pagopa.io.wallet.proximity

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Simple
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto

/** class to generate a DeviceResponse for ISO 18013-7 OID4VP flow.
 * @param clientId Authorization Request 'client_id'
 * @param responseUri: Authorization Request 'response_uri'
 * @param authorizationRequestNonce: Authorization Request 'nonce'
 * @param mdocGeneratedNonce: cryptographically random number with sufficient entropy
 **/
class OpenID4VP(
    private val clientId: String,
    private val responseUri: String,
    private val authorizationRequestNonce: String,
    private val mdocGeneratedNonce: String
) {
    /**Generate session transcript with OID4VPHandover
     *@return A CBOR-encoded SessionTranscript object*/
    fun createSessionTranscript(): ByteArray {
        val clientIdToHash = Cbor.encode(
            CborArray.builder()
                .add(clientId)
                .add(mdocGeneratedNonce)
                .end()
                .build()
        )
        val clientIdHash = Crypto.digest(Algorithm.SHA256, clientIdToHash)

        val responseUriToHash = Cbor.encode(
            CborArray.builder()
                .add(responseUri)
                .add(mdocGeneratedNonce)
                .end()
                .build()
        )
        val responseUriHash = Crypto.digest(Algorithm.SHA256, responseUriToHash)

        val oid4vpHandover = CborArray.builder()
            .add(clientIdHash)
            .add(responseUriHash)
            .add(authorizationRequestNonce)
            .end()
            .build()

        return Cbor.encode(
            CborArray.builder()
                .add(Simple.NULL)
                .add(Simple.NULL)
                .add(oid4vpHandover)
                .end()
                .build()
        )
    }
}