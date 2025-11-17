package it.pagopa.io.wallet.proximity.engagement

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.engagement.NfcEngagementHelper
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
import it.pagopa.io.wallet.proximity.qr_code.toReaderAuthWith
import it.pagopa.io.wallet.proximity.qr_code.toReaderTrustStore
import it.pagopa.io.wallet.proximity.request.RequestWrapper
import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.io.wallet.proximity.retrieval.transportOptions
import it.pagopa.io.wallet.proximity.session_data.SessionDataStatus
import it.pagopa.io.wallet.proximity.toRequest
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.json.JSONObject
import java.security.Security
import java.util.concurrent.Executor
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**Abstract class to do an engagement either by qrcode or by nfc*/
abstract class Engagement(val context: Context) {
    protected val tag: String = "Engagement"

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

    open fun getQrCodeString(): String {
        return ""
    }

    protected var retrievalMethods: List<DeviceRetrievalMethod> = listOf()
    private var readerTrustStores: List<ReaderTrustStore?>? = null
    protected var listener: EngagementListener? = null
    protected var deviceRetrievalHelper: DeviceRetrievalHelperWrapper? = null
    protected val eDevicePrivateKey by lazy {
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
     * @return [Engagement]
     */
    fun withReaderTrustStore(certificates: List<List<Int>>) = apply {
        certificates.setReaderTrustStore()
    }

    /**
     * Use this if you have certificates **As [ByteArray]**.
     * *You have still other two methods with [List] of [Int] for raw resources and [List] of [String] for pem*
     * @param certificates a [List] of [ByteArray] representing your raw certificates
     * @return [Engagement]
     */
    @JvmName("withReaderTrustStore1")
    fun withReaderTrustStore(certificates: List<List<ByteArray>>) = apply {
        certificates.setReaderTrustStore()
    }

    /**
     * Use this if you have certificates **As [String]**.
     * *You have still other two methods with [List] of [Int] for raw resources and [List] of [ByteArray] for raw certificates*
     * @param certificates a [List] of [String] representing your pem certificates
     * @return [Engagement]
     */
    @JvmName("withReaderTrustStore2")
    fun withReaderTrustStore(certificates: List<List<String>>) = apply {
        certificates.setReaderTrustStore()
    }

    open val qrEngagementListener: QrEngagementHelper.Listener? = null
    open val nfcEngagementListener: NfcEngagementHelper.Listener? = null

    @OptIn(ExperimentalEncodingApi::class)
    protected val deviceRetrievalHelperListener = object : DeviceRetrievalHelper.Listener {
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
                val b64 = Base64.encode(deviceRequestBytes)
                ProximityLogger.i("DEVICE REQUEST", b64)
                val b64Session = Base64.encode(sessionTranscript)
                ProximityLogger.i("SESSION TRANSCRIPT", b64Session)
            }
            val requestWrapperList = arrayListOf<JSONObject?>()
            listRequested.forEach{ each->
                (each toReaderAuthWith this@Engagement.readerTrustStores).let {
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

    fun withListener(callback: EngagementListener) = apply {
        this.listener = callback
    }

    abstract fun configure(): Engagement

    fun connect(mDocString: String) {
        val uri = mDocString.toUri()
        if (!uri.scheme.equals("mdoc"))
            throw IllegalArgumentException("mdoc string must contain mdoc:")
        val ba = android.util.Base64.decode(
            uri.encodedSchemeSpecificPart,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING
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
    abstract fun close()

    protected fun Context.mainExecutor(): Executor {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mainExecutor
        } else {
            ContextCompat.getMainExecutor(this)
        }
    }
}