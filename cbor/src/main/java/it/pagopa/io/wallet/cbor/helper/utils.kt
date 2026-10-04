package it.pagopa.io.wallet.cbor.helper

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Security

/**
 * Full BouncyCastle instance used directly, without registering it in [java.security.Security],
 * so the platform providers (e.g. the one used by Android Key Attestation) are never replaced.
 */
internal val bcProvider: Provider by lazy { BouncyCastleProvider() }

/** Appends BC at the end of the providers only if no provider with that name exists. */
internal fun addBcIfNeeded() {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(bcProvider)
    }
}
