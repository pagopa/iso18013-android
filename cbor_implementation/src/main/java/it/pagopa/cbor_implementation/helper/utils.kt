package it.pagopa.cbor_implementation.helper

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

internal fun addBcIfNeeded(){
    val isBcAlreadyIntoProviders = Security.getProviders().any {
        it.name == BouncyCastleProvider.PROVIDER_NAME
    }
    if (!isBcAlreadyIntoProviders) {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    } else {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}