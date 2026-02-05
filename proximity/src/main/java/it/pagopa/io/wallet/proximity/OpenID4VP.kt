package it.pagopa.io.wallet.proximity

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Bstr
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import java.util.Base64

/** class to generate a DeviceResponse for ISO 18013-7 OID4VP flow.
 * @param clientId Authorization Request 'client_id'
 * @param responseUri: Authorization Request 'response_uri'
 * @param authorizationRequestNonce: Authorization Request 'nonce'
 * @param jwkThumbprint: the JWK SHA-256 Thumbprint if response_mode is direct_post.jwt, 
 *                       null if response_mode is direct_post
 **/
class OpenID4VP(
    private val clientId: String,
    private val responseUri: String,
    private val authorizationRequestNonce: String,
    private val jwkThumbprint: String?
) {

    /** Build the OpenID4VPHandover CBOR structure */
    fun createSessionTranscript(): ByteArray {

        // Convert JWK thumbprint: String? → bstr or null
        val jwkThumbprintCbor =
            if (jwkThumbprint != null) {
                Bstr(Base64.getUrlDecoder().decode(jwkThumbprint))
            } else {
                Simple.NULL
            }

        // Build OpenID4VPHandoverInfo
        val handoverInfo = CborArray.builder()
            .add(clientId)
            .add(authorizationRequestNonce)
            .add(jwkThumbprintCbor)
            .add(responseUri)
            .end()
            .build()

        // Encode to bytes (OpenID4VPHandoverInfoBytes)
        val handoverInfoBytes = Cbor.encode(handoverInfo)

        // SHA-256 hash
        val infoHash = Crypto.digest(Algorithm.SHA256, handoverInfoBytes)

        // Final structure: ["OpenID4VPHandover", <hash>]
        val finalCbor = CborArray.builder()
            .add("OpenID4VPHandover")
            .add(Bstr(infoHash))
            .end()
            .build()

        return Cbor.encode(finalCbor)
    }
}
