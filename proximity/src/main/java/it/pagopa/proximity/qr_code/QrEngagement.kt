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
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import it.pagopa.proximity.ProximityLogger
import it.pagopa.proximity.document.reader_auth.ReaderTrustStore
import it.pagopa.proximity.request.RequestFromDevice
import it.pagopa.proximity.request.RequestWrapper
import it.pagopa.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.proximity.retrieval.connectionMethods
import it.pagopa.proximity.retrieval.transportOptions
import it.pagopa.proximity.wrapper.DeviceRetrievalHelperWrapper
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
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

    @JvmName("setReaderTrustStorePrivate")
    private fun <T> List<T>.setReaderTrustStore() {
        readerTrustStore = this.toReaderTrustStore(context)
    }

    /**
     * Use this if you have certificates into your **Raw Resource** folder
     * @return [QrEngagement]
     */
    fun withReaderTrustStore(certificates: List<Int>) = apply {
        certificates.setReaderTrustStore()
    }

    /**
     * Use this if you have certificates **As byte[]**
     * @return [QrEngagement]
     */
    @JvmName("withReaderTrustStore1")
    fun withReaderTrustStore(certificates: List<ByteArray>) = apply {
        certificates.setReaderTrustStore()
    }

    /**
     * Use this if you have certificates **As String**
     * @return [QrEngagement]
     */
    @JvmName("withReaderTrustStore2")
    fun withReaderTrustStore(certificates: List<String>) = apply {
        certificates.setReaderTrustStore()
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
            val deviceRetrievalHelperBuilt = DeviceRetrievalHelper.Builder(
                context,
                deviceRetrievalHelperListener,
                context.mainExecutor(),
                eDevicePrivateKey,
            ).useForwardEngagement(
                transport,
                qrEngagement.deviceEngagement,
                qrEngagement.handover
            ).build()
            deviceRetrievalHelper = DeviceRetrievalHelperWrapper(deviceRetrievalHelperBuilt)
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
                (each toReaderAuthWith this@QrEngagement.readerTrustStore).let {
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

    /**
     * Use this method to send a generic error message
     */
    fun sendErrorResponse() {
        if (deviceRetrievalHelper == null) return
        deviceRetrievalHelper!!.sendResponse(
            null,
            Constants.SESSION_DATA_STATUS_ERROR_CBOR_DECODING
        )
    }

    /**
     * Use this method to send a generic error message
     */
    fun sendErrorResponseNoData() {
        if (deviceRetrievalHelper == null) return
        deviceRetrievalHelper!!.sendResponse(
            null,
            Constants.DEVICE_RESPONSE_STATUS_GENERAL_ERROR
        )
    }

    /**
     * Use this method to send a good retrieved response
     * it does nothing if [DeviceRetrievalHelperWrapper] was lost
     */
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