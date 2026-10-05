package it.pagopa.io.wallet.cbor.helper

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

internal fun addBcIfNeeded(){
    val isBcAlreadyIntoProviders = Security.getProviders().any {
        it.name == BouncyCastleProvider.PROVIDER_NAME
    }
    if (!isBcAlreadyIntoProviders) {
        Security.addProvider(BouncyCastleProvider())
    }
}