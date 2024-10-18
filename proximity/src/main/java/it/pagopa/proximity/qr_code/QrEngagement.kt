package it.pagopa.proximity.qr_code

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.core.content.ContextCompat
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.engagement.QrEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.javaX509Certificates
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import it.pagopa.proximity.ProximityLogger
import it.pagopa.proximity.document.ReaderAuth
import it.pagopa.proximity.document.reader_auth.ReaderTrustStore
import it.pagopa.proximity.request.RequestFromDevice
import it.pagopa.proximity.request.RequestWrapper
import it.pagopa.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.proximity.retrieval.connectionMethods
import it.pagopa.proximity.retrieval.transportOptions
import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.Executor
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * To build use [QrEngagement.build] static method and ***then call [configure] method***
 * */
class QrEngagement private constructor(
    val context: Context
) {
    init {
        val isBcAlreadyIntoProviders = Security.getProviders().any {
            it.name == BouncyCastleProvider.PROVIDER_NAME
        }
        if (!isBcAlreadyIntoProviders) {
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } else {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
        Logger.isDebugEnabled = ProximityLogger.enabled
    }

    private var readerTrustStore: ReaderTrustStore? = null

    private lateinit var qrEngagement: QrEngagementHelper
    private lateinit var qrEngagementBuilder: QrEngagementHelper.Builder
    private var listener: QrEngagementListener? = null
    private var deviceRetrievalHelper: DeviceRetrievalHelperWrapper? = null
    private val eDevicePrivateKey by lazy {
        Crypto.createEcPrivateKey(EcCurve.P256)
    }

    /**
     * Use this if you have certificates into your **Raw Resource** folder
     */
    fun withReaderTrustStore(certificates: List<Int>) = apply {
        certificates.map {
            convertPemToX509Certificate(context.resources.openRawResource(it))
        }.mapNotNull { it }.let {
            this.readerTrustStore = ReaderTrustStore.getDefault(it)
        }
    }

    /**
     * Use this if you have certificates **As byte[]**
     */
    @JvmName("withReaderTrustStore1")
    fun withReaderTrustStore(certificates: List<ByteArray>) = apply {
        certificates.map {
            convertPemToX509Certificate(it)
        }.mapNotNull { it }.let {
            this.readerTrustStore = ReaderTrustStore.getDefault(it)
        }
    }

    /**
     * Use this if you have certificates **As String**
     */
    @JvmName("withReaderTrustStore2")
    fun withReaderTrustStore(certificates: List<String>) = apply {
        certificates.map {
            convertPemToX509Certificate(it)
        }.mapNotNull { it }.let {
            this.readerTrustStore = ReaderTrustStore.getDefault(it)
        }
    }

    private fun checkQrEngagementInit(): Boolean {
        val back = ::qrEngagement.isInitialized
        if (!back)
            ProximityLogger.e(
                this.javaClass.name,
                "OnDeviceConnected for QR engagement -> Have you called .configure method?"
            )
        return back
    }

    private val qrEngagementListener = object : QrEngagementHelper.Listener {

        override fun onDeviceConnecting() {
            ProximityLogger.d(this.javaClass.name, "QR Engagement: Device Connecting")
            listener?.onConnecting()
        }

        override fun onDeviceConnected(transport: DataTransport) {
            if (!checkQrEngagementInit())
                return
            if (deviceRetrievalHelper != null) {
                ProximityLogger.d(
                    this.javaClass.name,
                    "OnDeviceConnected for QR engagement -> ignoring due to active presentation"
                )
                return
            }

            ProximityLogger.d(
                this.javaClass.name,
                "OnDeviceConnected via QR: qrEngagement=$qrEngagement"
            )

            val builder = DeviceRetrievalHelper.Builder(
                context,
                deviceRetrievalHelperListener,
                context.mainExecutor(),
                eDevicePrivateKey,
            )
            builder.useForwardEngagement(
                transport,
                qrEngagement.deviceEngagement,
                qrEngagement.handover
            )
            deviceRetrievalHelper = DeviceRetrievalHelperWrapper(builder.build())
            qrEngagement.close()
            listener?.onDeviceRetrievalHelperReady(
                requireNotNull(
                    deviceRetrievalHelper
                )
            )
        }

        override fun onError(error: Throwable) {
            ProximityLogger.e(this.javaClass.name, "QR onError: ${error.message}")
            listener?.onCommunicationError("$error")
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private val deviceRetrievalHelperListener = object : DeviceRetrievalHelper.Listener {
        override fun onEReaderKeyReceived(eReaderKey: EcPublicKey) {
            ProximityLogger.d(
                this.javaClass.name,
                "DeviceRetrievalHelper Listener (QR): OnEReaderKeyReceived\n PEM: ${eReaderKey.toPem()}"
            )
        }

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            ProximityLogger.d(
                this.javaClass.name,
                "DeviceRetrievalHelper Listener (QR): OnDeviceRequest"
            )
            val sessionTranscript = deviceRetrievalHelper!!.sessionTranscript()
            val listRequested: List<DeviceRequestParser.DocRequest> = DeviceRequestParser(
                deviceRequestBytes,
                sessionTranscript
            ).parse().docRequests
            val requestWrapperList = arrayListOf<RequestWrapper>()
            listRequested.forEachIndexed { j, each ->
                getReaderAuthFromDocRequest(each).let {
                    requestWrapperList.add(
                        RequestWrapper(
                            each.itemsRequest,
                            it?.readerSignIsValid == true
                        ).prepare()
                    )
                }
            }
            listener?.onNewDeviceRequest(
                RequestFromDevice(requestWrapperList.toList()),
                sessionTranscript
            )
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            ProximityLogger.d(
                this.javaClass.name,
                "DeviceRetrievalHelper Listener (QR): onDeviceDisconnected"
            )
            listener?.onDeviceDisconnected(transportSpecificTermination)
        }

        override fun onError(error: Throwable) {
            ProximityLogger.d(
                this.javaClass.name,
                "DeviceRetrievalHelper Listener (QR): onError -> ${error.message}"
            )
            listener?.onCommunicationError("$error")
        }
    }

    fun withListener(callback: QrEngagementListener) = apply {
        this.listener = callback
    }

    private fun getReaderAuthFromDocRequest(documentRequest: DeviceRequestParser.DocRequest): ReaderAuth? {
        val readerAuth = documentRequest.readerAuth ?: return null
        val readerCertificateChain = documentRequest.readerCertificateChain ?: return null
        if (documentRequest.readerCertificateChain?.javaX509Certificates?.isEmpty() == true) return null
        val trustStore = readerTrustStore ?: return null

        val certChain =
            trustStore.createCertificationTrustPath(readerCertificateChain.javaX509Certificates)
                ?.takeIf { it.isNotEmpty() } ?: readerCertificateChain.javaX509Certificates

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
            documentRequest.readerAuthenticated,
            readerCertificateChain.javaX509Certificates,
            trustStore.validateCertificationTrustPath(readerCertificateChain.javaX509Certificates),
            readerCommonName
        )
    }

    /**
     * Gives back QR code string for engagement
     */
    fun getQrCodeString(): String {
        if (!checkQrEngagementInit())
            return ""
        return qrEngagement.deviceEngagementUriEncoded
    }

    /**
     * builds [QrEngagementHelper] by google
     * @return [QrEngagement] instance created via [QrEngagement.build] static method
     * */
    fun configure() = apply {
        qrEngagement = qrEngagementBuilder.build()
    }

    fun sendErrorResponse() {
        if (deviceRetrievalHelper == null) return
        deviceRetrievalHelper!!.sendResponse(
            null,
            Constants.SESSION_DATA_STATUS_ERROR_CBOR_DECODING
        )
    }
    fun sendErrorResponseNoData() {
        if (deviceRetrievalHelper == null) return
        deviceRetrievalHelper!!.sendResponse(
            null,
            Constants.DEVICE_RESPONSE_STATUS_GENERAL_ERROR
        )
    }

    fun sendResponse(response: ByteArray) {
        if (deviceRetrievalHelper == null) return
        deviceRetrievalHelper!!.sendResponse(
            response,
            Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
        )
    }

    private fun convertPemToX509Certificate(pemCertificateBytes: ByteArray): X509Certificate? {
        return try {
            convertPemToX509CertificateByteArray(pemCertificateBytes)
        } catch (e: Exception) {
            ProximityLogger.e(
                "PemToX509",
                "error ${e.message} while generating certificate from pemBytes"
            )
            null
        }
    }

    private fun convertPemToX509CertificateByteArray(pemCertificateBytes: ByteArray): X509Certificate? {
        val inputStream: InputStream = ByteArrayInputStream(pemCertificateBytes)
        val certificateFactory = CertificateFactory.getInstance("X.509")
        return certificateFactory.generateCertificate(inputStream) as? X509Certificate
    }

    private fun convertPemToX509Certificate(pemCertificate: String): X509Certificate? {
        return try {
            val cleanedPem = pemCertificate
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\n", "")
                .replace("\r", "")
            val pemCertificateBytes = Base64.getDecoder().decode(cleanedPem)
            convertPemToX509CertificateByteArray(pemCertificateBytes)
        } catch (e: Exception) {
            ProximityLogger.e(
                "PemToX509",
                "error ${e.message} while generating certificate from pemString: $pemCertificate"
            )
            null
        }
    }

    private fun convertPemToX509Certificate(inputStream: InputStream): X509Certificate? {
        return try {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            certificateFactory.generateCertificate(inputStream) as? X509Certificate
        } catch (e: Exception) {
            ProximityLogger.e(
                "PemToX509",
                "error ${e.message} while generating certificate from inputStream"
            )
            null
        }
    }

    /**
     * Closes the connection with the mdoc verifier
     */
    fun close() {
        if (!checkQrEngagementInit())
            return
        try {
            if (deviceRetrievalHelper != null)
                deviceRetrievalHelper!!.disconnect()
            qrEngagement.close()
        } catch (exception: RuntimeException) {
            ProximityLogger.e(this.javaClass.name, "Error closing QR engagement $exception")
        }
    }

    private fun Context.mainExecutor(): Executor {
        return if (VERSION.SDK_INT >= VERSION_CODES.P) {
            mainExecutor
        } else {
            ContextCompat.getMainExecutor(this)
        }
    }

    companion object {
        /**
         * Create an instance and configures the QR engagement.
         * First of all you must call [QrEngagement.configure] to build QrEngagementHelper.
         * To accept just some certificates use [QrEngagement.withReaderTrustStore] method.
         * To create a QrCode use [QrEngagement.getQrCodeString] method.
         * To observe all events call [QrEngagement.withListener] method.
         * To close the connection call [QrEngagement.close] method.
         */
        fun build(context: Context, retrievalMethods: List<DeviceRetrievalMethod>): QrEngagement {
            return QrEngagement(context).apply {
                qrEngagementBuilder = QrEngagementHelper.Builder(
                    context,
                    eDevicePrivateKey.publicKey,
                    retrievalMethods.transportOptions,
                    qrEngagementListener,
                    context.mainExecutor()
                ).setConnectionMethods(retrievalMethods.connectionMethods)
            }
        }
    }
}