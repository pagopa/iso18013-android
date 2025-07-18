package it.pagopa.io.wallet.proximity.qr_code

import android.content.Context
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.engagement.QrEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPublicKey
import com.android.identity.mdoc.engagement.EngagementParser
import com.android.identity.mdoc.request.DeviceRequestParser
import com.android.identity.util.Constants
import com.android.identity.util.Logger
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.document.reader_auth.ReaderTrustStore
import it.pagopa.io.wallet.proximity.request.RequestWrapper
import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.io.wallet.proximity.retrieval.connectionMethods
import it.pagopa.io.wallet.proximity.retrieval.transportOptions
import it.pagopa.io.wallet.proximity.session_data.SessionDataStatus
import it.pagopa.io.wallet.proximity.toRequest
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
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

    private var retrievalMethods: List<DeviceRetrievalMethod> = listOf()
    private var readerTrustStores: List<ReaderTrustStore?>? = null
    private lateinit var qrEngagement: QrEngagementHelper
    private lateinit var qrEngagementBuilder: QrEngagementHelper.Builder
    private var listener: QrEngagementListener? = null
    private var deviceRetrievalHelper: DeviceRetrievalHelperWrapper? = null
    private val eDevicePrivateKey by lazy {
        Crypto.createEcPrivateKey(EcCurve.P256)
    }

    @JvmName("setReaderTrustStorePrivate")
    private fun <T> List<List<T>>.setReaderTrustStore() {
        readerTrustStores = this.toReaderTrustStore(context)
    }

    /**
     * Use this if you have certificates into your **Raw Resource** folder.
     * *You have still other two methods with [List] of [ByteArray] for raw certificates and [List] of [String] for pem*
     * @param certificates a [List] of [Int] representing your raw resource
     * @return [QrEngagement]
     */
    fun withReaderTrustStore(certificates: List<List<Int>>) = apply {
        certificates.setReaderTrustStore()
    }

    /**
     * Use this if you have certificates **As [ByteArray]**.
     * *You have still other two methods with [List] of [Int] for raw resources and [List] of [String] for pem*
     * @param certificates a [List] of [ByteArray] representing your raw certificates
     * @return [QrEngagement]
     */
    @JvmName("withReaderTrustStore1")
    fun withReaderTrustStore(certificates: List<List<ByteArray>>) = apply {
        certificates.setReaderTrustStore()
    }

    /**
     * Use this if you have certificates **As [String]**.
     * *You have still other two methods with [List] of [Int] for raw resources and [List] of [ByteArray] for raw certificates*
     * @param certificates a [List] of [String] representing your pem certificates
     * @return [QrEngagement]
     */
    @JvmName("withReaderTrustStore2")
    fun withReaderTrustStore(certificates: List<List<String>>) = apply {
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
            listener?.onDeviceConnecting()
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
            listener?.onDeviceConnected(
                requireNotNull(
                    deviceRetrievalHelper
                )
            )
        }

        override fun onError(error: Throwable) {
            ProximityLogger.e(this.javaClass.name, "QR onError: ${error.message}")
            listener?.onError(error)
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
            //DOING B64 calculation only if we are in debug
            if (ProximityLogger.enabled) {
                val b64 = kotlin.io.encoding.Base64.encode(deviceRequestBytes)
                ProximityLogger.i("DEVICE REQUEST", b64)
                val b64Session = kotlin.io.encoding.Base64.encode(sessionTranscript)
                ProximityLogger.i("SESSION TRANSCRIPT", b64Session)
            }
            val requestWrapperList = arrayListOf<JSONObject?>()
            listRequested.forEachIndexed { j, each ->
                (each toReaderAuthWith this@QrEngagement.readerTrustStores).let {
                    requestWrapperList.add(
                        RequestWrapper(
                            each.itemsRequest,
                            it?.isSuccess() == true
                        ).prepare().toJson()
                    )
                }
            }
            val jsonToSend = requestWrapperList.toTypedArray().toRequest()
            ProximityLogger.i("REQ_JSON", jsonToSend.toString())
            listener?.onDocumentRequestReceived(
                if (jsonToSend.keys().asSequence().toMutableList().isEmpty())
                    null
                else
                    jsonToSend.toString(),
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
            listener?.onError(error)
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
     * builds [QrEngagementHelper] by com.android.identity package
     * @return [QrEngagement] instance created via [QrEngagement.build] static method
     * */
    fun configure() = apply {
        qrEngagement = qrEngagementBuilder.build()
    }

    fun connect(mDocString: String) {
        val uri = mDocString.toUri()
        if (!uri.scheme.equals("mdoc"))
            throw IllegalArgumentException("mdoc string must contain mdoc:")
        val ba = Base64.decode(
            uri.encodedSchemeSpecificPart,
            Base64.URL_SAFE or Base64.NO_PADDING
        )
        val engagement = EngagementParser(ba).parse()
        if (engagement.connectionMethods.isEmpty())
            throw IllegalArgumentException("No connection methods in engagement")
        ProximityLogger.i("connectionMethods", engagement.connectionMethods.toString())
        // For now, just pick the first transport
        val connectionMethod = engagement.connectionMethods[0]
        ProximityLogger.d(this.javaClass.name, "Using connection method $connectionMethod")
        val transport = DataTransport.fromConnectionMethod(
            context,
            connectionMethod,
            DataTransport.Role.MDOC,
            this.retrievalMethods.transportOptions
        )
        val deviceRetrievalHelper = DeviceRetrievalHelper.Builder(
            context,
            deviceRetrievalHelperListener,
            context.mainExecutor(),
            eDevicePrivateKey
        ).useReverseEngagement(
            transport,
            ba,
            engagement.originInfos
        ).build()
        listener?.onDeviceConnected(DeviceRetrievalHelperWrapper(deviceRetrievalHelper))
    }

    /**
     * Use this method to send a generic error message
     */
    fun sendErrorResponse(sessionDataStatus: SessionDataStatus) {
        if (deviceRetrievalHelper == null) return
        deviceRetrievalHelper!!.sendResponse(
            null,
            sessionDataStatus.value
        )
    }

    /**
     * Use this method to send a good retrieved response
     * it does nothing if [DeviceRetrievalHelperWrapper] was lost
     */
    fun sendResponse(response: ByteArray) {
        ProximityLogger.i("RESPONSE", "deviceRetrievalHelper:$deviceRetrievalHelper")
        if (deviceRetrievalHelper == null) return
        ProximityLogger.i("RESPONSE", "SENDING")
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
         * First of all you must call [configure] to build QrEngagementHelper.
         * To accept just some certificates use [withReaderTrustStore] method.
         * To create a QrCode use [getQrCodeString] method.
         * To observe all events call [withListener] method.
         * To close the connection call [close] method.
         */
        fun build(context: Context, retrievalMethods: List<DeviceRetrievalMethod>): QrEngagement {
            return QrEngagement(context).apply {
                this.retrievalMethods = retrievalMethods
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