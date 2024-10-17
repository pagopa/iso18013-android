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
import java.security.Security
import java.security.cert.X509Certificate
import java.util.concurrent.Executor
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * To build use [QrEngagement.build] static method
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

    fun withReaderTrustStore(certificates: List<X509Certificate>) = apply {
        this.readerTrustStore = ReaderTrustStore.getDefault(certificates)
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

    fun configure() = apply {
        qrEngagement = qrEngagementBuilder.build()
    }

    fun sendResponse(response: ByteArray) {
        if (deviceRetrievalHelper == null) return
        deviceRetrievalHelper!!.sendResponse(
            response,
            Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
        )
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