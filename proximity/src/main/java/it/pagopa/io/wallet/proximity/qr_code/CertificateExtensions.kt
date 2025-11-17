package it.pagopa.io.wallet.proximity.qr_code

import android.content.Context
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.mdoc.request.DeviceRequestParser
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.document.ReaderAuth
import it.pagopa.io.wallet.proximity.document.reader_auth.ReaderTrustStore
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

private fun InputStream.toX509Certificate(): X509Certificate? {
    return CertificateFactory.getInstance("X.509").generateCertificate(this) as? X509Certificate
}

@VisibleForTesting
fun ByteArray.toX509Certificate() = ByteArrayInputStream(this).toX509Certificate()

@VisibleForTesting
fun tryGetCertificate(predicate: () -> X509Certificate?): X509Certificate? {
    return try {
        predicate.invoke()
    } catch (e: Exception) {
        ProximityLogger.e(
            "PemToX509",
            "error ${e.message} while generating certificate from pemBytes"
        )
        null
    }
}

private infix fun <T> List<T>.tryGetCertificatesUsing(context: Context): List<X509Certificate?> {
    return when (this.firstOrNull()) {
        is ByteArray -> this.map { certificateBytes ->
            tryGetCertificate {
                (certificateBytes as ByteArray).toX509Certificate()
            }
        }

        is Int -> this.map { rawResId ->
            tryGetCertificate {
                context.resources?.openRawResource(rawResId as Int)?.toX509Certificate()
            }
        }

        is String -> this.map { pemCertificate ->
            tryGetCertificate {
                val cleanedPem = (pemCertificate as String)
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replace("\n", "")
                    .replace("\r", "")
                val pemCertificateBytes = Base64.getDecoder().decode(cleanedPem)
                pemCertificateBytes.toX509Certificate()
            }
        }

        else -> listOf(null)
    }
}

@CheckResult
private infix fun <T> List<T>.toNotNullMapWith(context: Context): List<X509Certificate> {
    return (this tryGetCertificatesUsing context).mapNotNull { it }
}

internal fun <T> List<T>.toReaderTrustStore(context: Context): ReaderTrustStore {
    val certs = this toNotNullMapWith context
    return ReaderTrustStore.getDefault(certs)
}

internal fun <T> List<List<T>>.toReaderTrustStore(context: Context): List<ReaderTrustStore> {
    return this.map {
        it.toReaderTrustStore(context)
    }
}

/**It converts a [DeviceRequestParser.DocRequest] into a [ReaderAuth] class
 * @param readerTrustStores: a [List<ReaderTrustStore?>] specified with one of [QrEngagement.withReaderTrustStore] method*/
internal infix fun DeviceRequestParser.DocRequest.toReaderAuthWith(
    readerTrustStores: List<ReaderTrustStore?>?
): ReaderAuth? {
    if (readerTrustStores == null)
        return null
    val trustStore = readerTrustStores.firstOrNull { trustStore ->
        val readerCertificateChain = this.readerCertificateChain ?: return null
        if (this.readerCertificateChain?.javaX509Certificates?.isEmpty() == true) return null
        trustStore?.validateCertificationTrustPath(readerCertificateChain.javaX509Certificates) == true
    }
    val readerCertificateChain = this.readerCertificateChain ?: return null
    if (trustStore == null)
        return null
    val certChain =
        trustStore.createCertificationTrustPath(readerCertificateChain.javaX509Certificates)
            ?.takeIf { it.isNotEmpty() } ?: readerCertificateChain.javaX509Certificates
    val readerAuth = this.readerAuth ?: return null
    val readerCommonName = certChain.firstOrNull()
        ?.subjectX500Principal
        ?.name
        ?.split(",")
        ?.map { it.split("=", limit = 2) }
        ?.firstOrNull { it.size == 2 && it[0] == "CN" }
        ?.get(1)
        ?.trim()
        ?: ""
    return ReaderAuth(
        readerAuth,
        this.readerAuthenticated,
        readerCertificateChain.javaX509Certificates,
        trustStore.validateCertificationTrustPath(readerCertificateChain.javaX509Certificates),
        readerCommonName
    )
}