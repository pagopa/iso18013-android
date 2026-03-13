package it.pagopa.io.wallet.proximity.nfc

import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.mdoc.transport.DataTransport.Companion.fromConnectionMethod
import com.android.identity.android.util.NfcUtil
import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.RawCbor
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.Tstr
import com.android.identity.cose.CoseKey
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethod.Companion.disambiguate
import com.android.identity.mdoc.engagement.EngagementGenerator
import com.android.identity.mdoc.sessionencryption.SessionEncryption
import com.android.identity.util.Constants
import com.android.identity.util.toHex
import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.cbor.model.EU_PID_DOCTYPE
import it.pagopa.io.wallet.cbor.model.MDL_DOCTYPE
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.document.reader_auth.ReaderTrustStore
import it.pagopa.io.wallet.proximity.nfc.apdu.CommandApdu
import it.pagopa.io.wallet.proximity.nfc.apdu.encapsulateInDo53
import it.pagopa.io.wallet.proximity.nfc.apdu.extractFromDo53
import it.pagopa.io.wallet.proximity.nfc.response.Nfc
import it.pagopa.io.wallet.proximity.nfc.response.ResponseApdu
import it.pagopa.io.wallet.proximity.nfc.utils.NfcEngagementHelperUtils
import it.pagopa.io.wallet.proximity.nfc.utils.OnlyNfcEvents
import it.pagopa.io.wallet.proximity.parser.DeviceRequestParserRefactor
import it.pagopa.io.wallet.proximity.qr_code.toReaderAuthWith
import it.pagopa.io.wallet.proximity.request.DocRequested
import it.pagopa.io.wallet.proximity.request.RequestWrapper
import it.pagopa.io.wallet.proximity.response.ResponseGenerator
import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod
import it.pagopa.io.wallet.proximity.retrieval.transportOptions
import it.pagopa.io.wallet.proximity.toRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import org.json.JSONObject
import java.util.concurrent.Executor
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NfcTransportMdoc(
    private val context: Context,
    private val eDeviceKey: EcPrivateKey,
    private val retrievalMethods: List<DeviceRetrievalMethod>,
    private val listener: Listener,
    private val executor: Executor,
    private val whatToDoWithRequest: (String) -> String
) : MdocTransport() {
    private var alias = ""
    private var useExtendedLength: Boolean = true
    private lateinit var sendResponse: (response: ResponseApdu) -> Unit
    private var eDeviceKeyFromQr: EcPrivateKey? = null
    private var handoverSelectMessage: ByteArray? = null
    lateinit var handover: ByteArray
    private var docs: Array<Document> = arrayOf()
    private var transports = mutableListOf<DataTransport>()
    private var selectedNfcFile: ByteArray? = null
    private var staticHandoverConnectionMethods: List<ConnectionMethod>? = null
    private var reportedDeviceConnecting = false
    private var transportsSetupTimestamp: Long = 0
    private var negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
    private val options by lazy {
        this.retrievalMethods.transportOptions
    }

    var sessionEncryption: SessionEncryption? = null
    private var deviceEngagementFromQr: ByteArray? = null
    private var readerTrustStores: List<ReaderTrustStore?>? = listOf()
    private var updateBinaryData: ByteArray? = null
    private var handoverRequestMessage: ByteArray? = null
    private var envelopeResponseDeferred = false
    private val incomingMessages = Channel<ByteString>(Channel.UNLIMITED)

    val deviceEngagement: ByteArray = EngagementGenerator(
        if (eDeviceKeyFromQr != null) eDeviceKeyFromQr!!.publicKey else eDeviceKey.publicKey,
        EngagementGenerator.ENGAGEMENT_VERSION_1_0
    ).generate()

    fun setKeyFromQr(eDeviceKeyFromQr: EcPrivateKey) = apply {
        ProximityLogger.i(TAG, "setKeyFromQr called")
        this.eDeviceKeyFromQr = eDeviceKeyFromQr
    }

    fun deviceEngagementFromQr(deviceEngagementFromQr: ByteArray) = apply {
        ProximityLogger.i(TAG, "deviceEngagementFromQr called")
        this.deviceEngagementFromQr = deviceEngagementFromQr
    }

    fun withDocs(docs: Array<Document>) = apply {
        this.docs = docs
    }

    fun withStaticHandoverConnectionMethods(connMethods: List<ConnectionMethod>) = apply {
        this.staticHandoverConnectionMethods = connMethods
    }

    fun withAlias(alias: String) = apply {
        this.alias = alias
    }

    fun withReaderTrustStores(list: List<ReaderTrustStore?>?) {
        this.readerTrustStores = list
    }

    private fun createHandover(hsMessage: ByteArray): ByteArray {
        handoverSelectMessage = hsMessage
        handoverRequestMessage = null
        return Cbor.encode(
            CborArray.builder()
                .add(handoverSelectMessage!!)
                .add(Simple.NULL)
                .end()
                .build()
        )
    }


    companion object {
        private const val TAG = "NfcTransportMdoc"
        private const val NEGOTIATED_HANDOVER_STATE_NOT_STARTED = 0
        private const val NEGOTIATED_HANDOVER_STATE_EXPECT_SERVICE_SELECT = 1
        private const val NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST = 2
        private const val NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT = 3

        // Must be called by platform when receiving APDUs for Nfc.ISO_MDOC_NFC_DATA_TRANSFER_APPLICATION_ID
        //
        suspend fun processCommandApdu(
            commandApdu: ByteArray,
            sendResponse: (responseApdu: ByteArray) -> Unit,
        ) {
            if (instances.isEmpty()) {
                ProximityLogger.i(TAG, "No NfcTransportMdoc instances")
            } else {
                if (instances.size > 1) {
                    ProximityLogger.i(
                        TAG,
                        "${instances.size} NfcTransportMdoc instances, expected just one"
                    )
                }
                instances.forEach { instance ->
                    instance.processApdu(
                        command = commandApdu,
                        sendResponse = { response ->
                            sendResponse(response.encode())
                            if(response.isFinalResponse){
                                onDeactivated()
                            }
                        }
                    )
                }
            }
        }

        suspend fun doPresentment(
            timeout: Duration? = 10.seconds,
            timeoutSubsequentRequests: Duration? = 30.seconds
        ) {
            if (instances.isEmpty()) {
                ProximityLogger.i(TAG, "No NfcTransportMdoc instances")
            } else {
                if (instances.size > 1) {
                    ProximityLogger.i(
                        TAG,
                        "${instances.size} NfcTransportMdoc instances, expected just one"
                    )
                }
                val transport = instances[0]
                if (transport.state.value != State.CONNECTED)
                    NfcEngagementEventBus.tryEmit(NfcEngagementEvent.Error(Throwable("Expected state CONNECTED but found ${transport.state.value}")))
                var numRequestsServed = 0
                var sendSessionTermination = true
                try {
                    while (true) {
                        val timeoutToUse =
                            if (numRequestsServed == 0) timeout else timeoutSubsequentRequests
                        val sessionData = if (timeoutToUse == null) {
                            transport.waitForMessage()
                        } else {
                            try {
                                withTimeout(timeoutToUse) {
                                    transport.waitForMessage()
                                }
                            } catch (e: TimeoutCancellationException) {
                                NfcEngagementEventBus.tryEmit(NfcEngagementEvent.Error(Throwable("Timed out waiting for message from remote reader: $e")))
                                byteArrayOf()
                            }
                        }
                        if (sessionData.isEmpty()) {
                            ProximityLogger.i(
                                TAG,
                                "Received transport-specific session termination message from reader"
                            )
                            sendSessionTermination = false
                            break
                        }
                        val response: ByteArray = try {
                            transport.processReaderRequest(sessionData)
                        } catch (e: Throwable) {
                            ProximityLogger.e(
                                TAG,
                                "ENVELOPE: Error processing request: ${e.message}"
                            )
                            transport.sendMessage(
                                byteArrayOf(0x6a.toByte(), 0x82.toByte())//FILE NOT FOUND
                            )
                            break
                        }
                        ProximityLogger.d(
                            TAG,
                            "ENVELOPE: Response size=${response.size}"
                        )
                        val messageBack = transport.sessionEncryption!!.encryptMessage(
                            response,
                            null
                        )
                        transport.sendMessage(messageBack)
                        numRequestsServed += 1
                    }
                } finally {
                    if (sendSessionTermination) {
                        ProximityLogger.i(TAG, "Sending session-termination")
                        try {
                            transport.sendMessage(
                                SessionEncryption.encodeStatus(Constants.SESSION_DATA_STATUS_SESSION_TERMINATION)
                            )
                        } catch (e: Throwable) {
                            ProximityLogger.e(
                                TAG,
                                "Caught error while sending session-termination: $e"
                            )
                        }
                    } else {
                        NfcEngagementEventBus.tryEmit(NfcEngagementEvent.Error(Throwable("Session not finished")))
                    }
                    ProximityLogger.i(TAG, "Closing transport")
                    transport.close()
                }
            }
        }

        // Must be called by platform for deactivation event for Nfc.ISO_MDOC_NFC_DATA_TRANSFER_APPLICATION_ID
        //
        fun onDeactivated() {
            ProximityLogger.i(TAG, "onDeactivated")
            if (instances.isEmpty()) {
                ProximityLogger.i(TAG, "No NfcTransportMdoc instances")
            } else {
                if (instances.size > 1) {
                    ProximityLogger.i(
                        TAG,
                        "${instances.size} NfcTransportMdoc instances, expected just one"
                    )
                }
                CoroutineScope(Dispatchers.Default).launch {
                    // Get a read-only copy since the caller may modify `instances` variable.
                    instances.toList().forEach { instance ->
                        instance.onDeactivated()
                    }
                }
            }
        }

        private val instances = mutableListOf<NfcTransportMdoc>()
    }

    private val mutex = Mutex()

    private val _state = MutableStateFlow(State.IDLE)
    override val state: StateFlow<State> = _state.asStateFlow()

    override suspend fun advertise() {
    }

    private var leReceived = 0
    private val outgoingChunks = mutableListOf<ByteString>()
    private var outgoingChunksRemainingBytesAvailable = 0

    private fun getNextOutgoingChunkResponse(): ResponseApdu? {
        if (outgoingChunks.isEmpty()) {
            return null
        }
        val chunk = outgoingChunks.removeAt(0)
        ProximityLogger.i(
            TAG,
            "getNextOutgoingChunkResponse: chunk size=${chunk.size}, leReceived=$leReceived, outgoingChunksRemainingBytesAvailable=$outgoingChunksRemainingBytesAvailable"
        )
        outgoingChunksRemainingBytesAvailable -= chunk.size

        /* Following excerpts are from ISO/IEC 18013-5:2021 clause 8.3.3.1.2 Data retrieval using
         * near field communication (NFC)
         */
        val isLastChunk = outgoingChunks.isEmpty()
        ProximityLogger.i("is last chunk", "$isLastChunk")
        if (isLastChunk) {
            /* If Le ≥ the number of available bytes, the mdoc shall include all
             * available bytes in the response and set the status words to ’90 00’.
             */
            return ResponseApdu(
                status = Nfc.RESPONSE_STATUS_SUCCESS,
                payload = chunk,
                isFinalResponse = true
            )
        } else {
            if (outgoingChunksRemainingBytesAvailable <= leReceived + 255) {
                /* If Le < the number of available bytes ≤ Le + 255, the mdoc shall
                 * include as many bytes in the response as indicated by Le and shall
                 * set the status words to ’61 XX’, where XX is the number of available
                 * bytes remaining. The mdoc reader shall respond with a GET RESPONSE
                 * command where Le is set to XX.
                 */
                val numBytesRemaining = outgoingChunksRemainingBytesAvailable - leReceived
                return ResponseApdu(
                    status = Nfc.RESPONSE_STATUS_CHAINING_RESPONSE_BYTES_STILL_AVAILABLE + numBytesRemaining.and(
                        0xff
                    ),
                    payload = chunk
                )
            } else {
                /* If the number of available bytes > Le + 255, the mdoc shall include
                 * as many bytes in the response as indicated by Le and shall set the
                 * status words to ’61 00’. The mdoc reader shall respond with a GET
                 * RESPONSE command where Le is set to the maximum length of the
                 * response data field that is supported by both the mdoc and the mdoc
                 * reader.
                 */
                val status = if (useExtendedLength)
                    Nfc.RESPONSE_STATUS_CHAINING_RESPONSE_BYTES_STILL_AVAILABLE
                else
                    0x61FF
                return ResponseApdu(
                    status = status,
                    payload = chunk
                )
            }
        }
    }

    override suspend fun open() {
        mutex.withLock {
            check(_state.value == State.IDLE) { "Expected state IDLE, got ${_state.value}" }
            ProximityLogger.i(TAG, "open")
            instances.add(this)
        }
    }

    override suspend fun sendMessage(message: ByteArray) {
        mutex.withLock {
            check(_state.value == State.CONNECTED) { "Expected state CONNECTED, got ${_state.value}" }
            ProximityLogger.i(TAG, "sendMessage")

            if (leReceived == 0) {
                val e =
                    IllegalStateException("Trying to send a message before having received one (leReceived is 0)")
                failTransport(e)
                throw e
            }

            val encapsulatedMessage = ByteString(message).encapsulateInDo53()
            val maxChunkSize = leReceived
            val offsets = 0 until encapsulatedMessage.size step maxChunkSize
            for (offset in offsets) {
                val chunkSize = min(maxChunkSize, encapsulatedMessage.size - offset)
                val chunk = encapsulatedMessage.substring(offset, offset + chunkSize)
                outgoingChunks.add(chunk)
            }
            outgoingChunksRemainingBytesAvailable += encapsulatedMessage.size

            if (envelopeResponseDeferred) {
                ProximityLogger.i(TAG, "envelopeResponseDeferred = true, sending")
                val response = getNextOutgoingChunkResponse()!!
                val responseBytes = response.encode()
                ProximityLogger.dHex(
                    TAG,
                    "ENVELOPE_RESPONSE_STATUS",
                    byteArrayOf(
                        responseBytes[responseBytes.size - 2],
                        responseBytes[responseBytes.size - 1]
                    )
                )
                sendResponse(response)
                if (response.isFinalResponse && response.status == Nfc.RESPONSE_STATUS_SUCCESS) {
                    NfcEngagementEventBus.tryEmit(
                        NfcEngagementEvent.DocumentSent
                    )
                }
                envelopeResponseDeferred = false
            }
        }
    }

    private fun processEnvelope(command: CommandApdu): ResponseApdu? {
        ProximityLogger.i(TAG, "processEnvelope")
        check(applicationSelected) { "Application not selected" }
        currentIncomingEncapsulatedMessage.append(command.payload)
        val apdu = command.encode()
        useExtendedLength = apdu.size > 5 && (apdu[4].toInt() and 0xff) == 0x00
        ProximityLogger.i("useExtendedLength", useExtendedLength.toString())
        when (command.cla) {
            Nfc.CLA_CHAIN_LAST -> {
                // For the last ENVELOPE command in a chain, Le shall be set to the maximum length
                // of the response data field that is supported by both the mdoc and the mdoc reader.
                //
                // We'll need this for later.
                if (leReceived == 0) {
                    leReceived = command.le
                    ProximityLogger.i(TAG, "LE in last ENVELOPE is $leReceived")
                }
                // No more data coming.
                val message = currentIncomingEncapsulatedMessage.toByteString().extractFromDo53()
                currentIncomingEncapsulatedMessage = ByteStringBuilder()
                incomingMessages.trySend(message)
                val chunkResponse = getNextOutgoingChunkResponse()
                envelopeResponseDeferred = chunkResponse == null
                NfcEngagementEventBus.tryEmit(
                    NfcEngagementEvent.NfcOnlyEventListener(
                        OnlyNfcEvents.DATA_TRANSFER_STARTED
                    )
                )
                return chunkResponse
            }

            Nfc.CLA_CHAIN_NOT_LAST -> {
                // More data is coming
                check(command.le == 0) { "Expected LE 0 for non-last ENVELOPE, got ${command.le}" }
                ProximityLogger.i(TAG, "processEnvelope: returning SUCCESS")
                return ResponseApdu(status = Nfc.RESPONSE_STATUS_SUCCESS)
            }

            else -> {
                throw IllegalStateException("Expected CLA 0x00 or 0x10 for ENVELOPE, got ${command.cla}")
            }
        }
    }

    override suspend fun close() {
        mutex.withLock {
            if (_state.value == State.FAILED || _state.value == State.CLOSED) {
                return
            }
            ProximityLogger.i(TAG, "close")
            _state.value = State.CLOSED
        }
    }

    private var inError = false
    private var applicationSelected = false

    private fun failTransport(error: Throwable) {
        check(mutex.isLocked) { "failTransport called without holding lock" }
        inError = true
        if (_state.value == State.FAILED || _state.value == State.CLOSED) {
            return
        }
        ProximityLogger.i(TAG, "Failing transport with error: ${error.message}")
        _state.value = State.FAILED
    }

    private fun processSelectApplication(command: CommandApdu): ResponseApdu {
        ProximityLogger.i(TAG, "COMMAND: select_application")
        check(!applicationSelected) { "Application already selected" }
        val requestedApplicationId = command.payload
        if (requestedApplicationId != Nfc.ISO_MDOC_NFC_DATA_TRANSFER_APPLICATION_ID && requestedApplicationId != Nfc.NDEF_APPLICATION_ID) {
            NfcEngagementEventBus.tryEmit(NfcEngagementEvent.NotSupported)
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        } else if (requestedApplicationId == Nfc.NDEF_APPLICATION_ID) {
            NfcEngagementEventBus.tryEmit(
                NfcEngagementEvent.NfcOnlyEventListener(
                    OnlyNfcEvents.NFC_ENGAGEMENT_STARTED
                )
            )
        } else {
            applicationSelected = true
        }
        updateBinaryData = null
        // We're open for business.
        _state.value = State.CONNECTED
        return ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS)
    }

    private var currentIncomingEncapsulatedMessage = ByteStringBuilder()

    fun peerIsConnecting() {
        if (!reportedDeviceConnecting) {
            reportedDeviceConnecting = true
            reportDeviceConnecting()
        }
    }

    fun reportDeviceConnecting() {
        ProximityLogger.i(TAG, "reportDeviceConnecting")
        executor.execute {
            listener.onDeviceConnecting()
        }
    }

    fun reportDeviceConnected(transport: DataTransport) {
        ProximityLogger.i(TAG, "reportDeviceConnected: transport=$transport")
        ProximityLogger.i(TAG, "reportDeviceConnected: listener=$listener, executor=$executor")
        executor.execute {
            ProximityLogger.i(
                TAG,
                "reportDeviceConnected: Calling listener.onDeviceConnected()"
            )
            listener.onDeviceConnected(transport)
            ProximityLogger.i(
                TAG,
                "reportDeviceConnected: listener.onDeviceConnected() completed"
            )
        }
    }

    override suspend fun waitForMessage(): ByteArray {
        mutex.withLock {
            check(_state.value == State.CONNECTED) { "Expected state CONNECTED, got ${_state.value}" }
            ProximityLogger.i(TAG, "waitForMessage")
        }
        return incomingMessages.receive().toByteArray()
    }

    fun reportError(error: Throwable) {
        ProximityLogger.i(TAG, "reportError: error: :$error")
        executor.execute {
            listener.onError(error)
        }
    }

    fun peerHasConnected(transport: DataTransport) {
        ProximityLogger.i(
            TAG, "Peer has connected on transport " + transport
                    + " - shutting down other transports"
        )
        for (t in transports) {
            t.setListener(null, null)
            if (t !== transport) {
                t.close()
            }
        }
        transports.clear()
        reportDeviceConnected(transport)
    }

    private fun setupTransports(connectionMethods: List<ConnectionMethod>): List<ConnectionMethod> {
        val setupConnectionMethods = mutableListOf<ConnectionMethod>()

        if (transports.isNotEmpty()) {
            ProximityLogger.i(
                TAG,
                "Closing ${transports.size} existing transport(s) before setting up new ones"
            )
            for (transport in transports) {
                try {
                    transport.close()
                } catch (e: Exception) {
                    ProximityLogger.e(TAG, "Error closing existing transport: $e")
                }
            }
            transports.clear()
            ProximityLogger.i(TAG, "All existing transports closed and cleared")
        }

        ProximityLogger.i(
            TAG,
            "Setting up transports for ${connectionMethods.size} connection method(s): $connectionMethods"
        )
        val timeStartedSettingUpTransports = System.currentTimeMillis()
        val encodedEDeviceKeyBytes = Cbor.encode(
            Tagged(24, Bstr(Cbor.encode(eDeviceKey.publicKey.toCoseKey().toDataItem())))
        )

        val disambiguatedMethods = disambiguate(connectionMethods)
        ProximityLogger.i(
            TAG,
            "After disambiguation: ${disambiguatedMethods.size} method(s): $disambiguatedMethods"
        )
        for (cm in disambiguatedMethods) {
            val transport = fromConnectionMethod(
                context, cm, DataTransport.Role.MDOC, options
            )
            transport.setEDeviceKeyBytes(encodedEDeviceKeyBytes)
            transports.add(transport)
            ProximityLogger.i(TAG, "Added transport #${transports.size} for $cm")
        }

        for (transport in transports) {
            transport.setListener(object : DataTransport.Listener {
                override fun onConnecting() {
                    ProximityLogger.i(TAG, "onConnecting for $transport")
                    peerIsConnecting()
                }

                override fun onConnected() {
                    ProximityLogger.i(TAG, "onConnected for $transport")
                    peerHasConnected(transport)
                }

                override fun onDisconnected() {
                    ProximityLogger.i(TAG, "onDisconnected for $transport")
                    transport.close()
                }

                override fun onError(error: Throwable) {
                    transport.close()
                    reportError(error)
                }

                override fun onMessageReceived() {
                    ProximityLogger.i(TAG, "onMessageReceived for $transport")
                }

                override fun onTransportSpecificSessionTermination() {
                    ProximityLogger.i(TAG, "Received transport-specific session termination")
                    transport.close()
                }
            }, executor)
            ProximityLogger.i(
                TAG,
                "Calling connect() on transport $transport at ${System.currentTimeMillis()}"
            )
            transport.connect()
            ProximityLogger.i(
                TAG,
                "connect() returned for transport $transport at ${System.currentTimeMillis()}"
            )
            setupConnectionMethods.add(transport.connectionMethodForTransport)
        }
        val setupTimeMillis = System.currentTimeMillis() - timeStartedSettingUpTransports
        ProximityLogger.i(
            TAG,
            "All transports set up in $setupTimeMillis msec, now ready to advertise"
        )

        transportsSetupTimestamp = System.currentTimeMillis()
        return setupConnectionMethods
    }

    private fun processGetResponse(): ResponseApdu {
        ProximityLogger.i(TAG, "processGetResponse")
        check(applicationSelected) { "Application not selected" }
        val chunkResponse = getNextOutgoingChunkResponse()
        check(chunkResponse != null)
        if (chunkResponse.isFinalResponse && chunkResponse.status == Nfc.RESPONSE_STATUS_SUCCESS) {
            NfcEngagementEventBus.tryEmit(NfcEngagementEvent.DocumentSent)
            ProximityLogger.i(TAG, "All chunks sent, emitted DocumentSent event")
        }
        return chunkResponse
    }

    fun decryptMessage(
        requestCborEncrypted: ByteArray,
        sessionTranscript: ByteArray
    ): Pair<ByteArray?, Long?> {
        val cbor = Cbor.decode(requestCborEncrypted)
        ProximityLogger.i("cbor", cbor.toString())
        val eReaderKeyDataItem = cbor[Tstr("eReaderKey")]
        ProximityLogger.i("eReaderKeyDataItem", eReaderKeyDataItem.toString())
        val eReaderKeyBytes = eReaderKeyDataItem.asTagged.asBstr
        ProximityLogger.i("eReaderKeyBytes", eReaderKeyBytes.toHex())
        val otherKey = EcPublicKey
            .fromCoseKey(
                CoseKey.fromDataItem(Cbor.decode(eReaderKeyBytes))
            )
        ProximityLogger.i("OTHER_KEY", otherKey.toPem())
        ProximityLogger.i(
            "MY_PRIVATE_KEY",
            (if (eDeviceKeyFromQr != null) eDeviceKeyFromQr!! else eDeviceKey).toPem()
        )

        this.sessionEncryption = SessionEncryption(
            SessionEncryption.Role.MDOC,
            if (eDeviceKeyFromQr != null) eDeviceKeyFromQr!! else eDeviceKey,
            otherKey,
            sessionTranscript
        )
        val decrypted = sessionEncryption!!.decryptMessage(requestCborEncrypted)
        ProximityLogger.i("decrypted", decrypted.first?.toHex().orEmpty())
        ProximityLogger.i("long_value", decrypted.second.toString())
        return decrypted
    }

    fun processReaderRequest(
        requestCborEncrypted: ByteArray
    ): ByteArray {
        ProximityLogger.i("requestCborEncrypted", requestCborEncrypted.toHex())
        val eReaderKey = Cbor.decode(requestCborEncrypted)
        try {
            val status = eReaderKey[Tstr("status")]
            val statusValue = status.asNumber
            ProximityLogger.i("Received status message", statusValue.toString())
            if (statusValue == 20L)
                return NfcUtil.STATUS_WORD_OK
        } catch (_: Exception) {
        }
        ProximityLogger.i("eReaderKey", eReaderKey.toString())
        val encodedEReaderKey = try {
            eReaderKey["eReaderKey"].asTagged.asBstr
        } catch (e: Exception) {
            ProximityLogger.e(TAG, "Error extracting eReaderKey: $e")
            throw Exception("Error extracting eReaderKey")
        }
        var deviceEng = deviceEngagement
        val handoverBytes = if (::handover.isInitialized)
            handover
        else {
            ProximityLogger.i(
                TAG,
                "using deviceEngagementFromQr"
            )
            ProximityLogger.i(
                TAG,
                deviceEngagementFromQr?.toHex() ?: "null"
            )
            deviceEng = deviceEngagementFromQr!!
            Cbor.encode(Simple.NULL)
        }
        val sessionTranscript = Cbor.encode(
            CborArray.builder()
                .add(Tagged(24, Bstr(deviceEng)))
                .add(Tagged(24, Bstr(encodedEReaderKey)))
                .add(RawCbor(handoverBytes))
                .end()
                .build()
        )
        ProximityLogger.i("Session Transcript", Cbor.decode(sessionTranscript).toString())
        val (deviceRequestBytes, _) = this.decryptMessage(requestCborEncrypted, sessionTranscript)
        val listRequested: List<DeviceRequestParserRefactor.DocRequest> =
            DeviceRequestParserRefactor(
                deviceRequestBytes!!,
                sessionTranscript
            ).parse().docRequests

        ProximityLogger.i("DEVICE REQUEST", deviceRequestBytes.toHex())
        ProximityLogger.i("SESSION TRANSCRIPT", sessionTranscript.toHex())
        val requestWrapperList = arrayListOf<JSONObject?>()
        listRequested.forEach { each ->
            (each toReaderAuthWith readerTrustStores).let {
                requestWrapperList.add(
                    RequestWrapper(
                        each.itemsRequest,
                        it?.isSuccess() == true
                    ).prepare().toJson()
                )
            }
        }
        val jsonToSend = requestWrapperList.toTypedArray().toRequest()
        NfcEngagementEventBus.tryEmit(
            NfcEngagementEvent.DocumentRequestReceived(
                request = jsonToSend.toString(),
                sessionTranscript = sessionTranscript,
                onlyNfc = true
            )
        )
        val disclosedDocuments = ArrayList<Document>()
        val req = this.whatToDoWithRequest.invoke(jsonToSend.toString())
        JSONObject(req).keys().forEach {
            when {
                DocType(it) == DocType.MDL -> disclosedDocuments.add(docs.first { doc -> doc.docType == MDL_DOCTYPE })
                DocType(it) == DocType.EU_PID -> disclosedDocuments.add(docs.first { doc -> doc.docType == EU_PID_DOCTYPE })
            }
        }
        val docRequested = disclosedDocuments.mapNotNull {
            it.issuerSigned?.rawValue?.let { doc ->
                DocRequested(
                    issuerSignedContent = doc,
                    alias = alias,
                    docType = it.docType!!
                )
            }
        }
        docRequested.forEachIndexed { i, each ->
            ProximityLogger.i(
                "docRequested $i",
                "Doc type: ${each.docType};\nAlias: ${each.alias};"
            )
        }
        ProximityLogger.i("originalJson", jsonToSend.toString(3).orEmpty())
        val (resp, itsOk) = ResponseGenerator(
            sessionsTranscript = sessionTranscript
        ).createResponse(
            documents = docRequested.toTypedArray(),
            fieldRequestedAndAccepted = req
        )
        return if (itsOk == "created") resp!! else NfcUtil.STATUS_WORD_FILE_NOT_FOUND
    }

    internal suspend fun processApdu(
        command: ByteArray,
        sendResponse: (response: ResponseApdu) -> Unit,
    ) {
        this.sendResponse = sendResponse
        val response = processApdu(command)
        if (response != null) {
            mutex.withLock {
                sendResponse(response)
            }
        }
    }

    private suspend fun processApdu(
        command: ByteArray,
    ): ResponseApdu? {
        if (inError) {
            ProximityLogger.i(
                TAG,
                "processApdu: Already in error state, responding to APDU with status 6f00"
            )
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_NO_PRECISE_DIAGNOSIS)
        }
        try {
            return when (val commandType = NfcUtil.nfcGetCommandType(command)){
                NfcUtil.COMMAND_TYPE_SELECT_BY_AID -> processSelectApplication(CommandApdu.decode(command))
                NfcUtil.COMMAND_TYPE_SELECT_FILE -> processSelectFile(CommandApdu.decode(command))
                NfcUtil.COMMAND_TYPE_READ_BINARY ->  processReadBinary(CommandApdu.decode(command))
                NfcUtil.COMMAND_TYPE_ENVELOPE -> processEnvelope(CommandApdu.decode(command))
                NfcUtil.COMMAND_TYPE_UPDATE_BINARY -> processUpdateBinary(CommandApdu.decode(command))
                NfcUtil.COMMAND_TYPE_RESPONSE -> processGetResponse()
                else -> {
                    failTransport(Error("Command APDU $commandType not supported, returning 6d00 (RESPONSE_STATUS_ERROR_INSTRUCTION_NOT_SUPPORTED_OR_INVALID)"))
                    ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_INSTRUCTION_NOT_SUPPORTED_OR_INVALID)
                }
            }
        } catch (error: Throwable) {
            error.printStackTrace()
            failTransport(Error("Error processing APDU: ${error.message}", error))
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_NO_PRECISE_DIAGNOSIS)
        }
    }

    fun reportHandoverSelectMessageSent() {
        ProximityLogger.i(TAG, "onHandoverSelectMessageSent")
        executor.execute {
            listener.onHandoverSelectMessageSent()
        }
    }

    private suspend fun processReadBinary(command: CommandApdu): ResponseApdu {
        ProximityLogger.i(TAG, "COMMAND: read_binary")
        val apdu = command.encode()
        val startTime = System.currentTimeMillis()
        if (apdu.size < 5) {
            ProximityLogger.i(TAG, "handleReadBinary: unexpected APDU length ${apdu.size}")
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        }
        if (selectedNfcFile == null) {
            ProximityLogger.i(
                TAG,
                "handleReadBinary: no file selected -> STATUS_WORD_FILE_NOT_FOUND"
            )
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        }
        val contents: ByteArray = selectedNfcFile!!
        val offset =
            (apdu[2].toInt() and 0xff) * 255 + (apdu[3].toInt() and 0xff)
        var size = apdu[4].toInt() and 0xff
        if (size == 0) {
            if (apdu.size < 7) {
                return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
            }
            size = (apdu[5].toInt() and 0xff) * 255
            size += apdu[6].toInt() and 0xff
        }
        if (offset >= contents.size) {
            ProximityLogger.i(
                TAG, "handleReadBinary: starting offset $offset beyond file " +
                        "end ${contents.size} -> STATUS_WORD_WRONG_PARAMETERS"
            )
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_INSTRUCTION_NOT_SUPPORTED_OR_INVALID)
        }
        if (offset + size > contents.size) {
            ProximityLogger.i(
                TAG, "handleReadBinary: ending offset ${offset + size} beyond file" +
                        " end ${contents.size} -> STATUS_WORD_END_OF_FILE_REACHED"
            )
            return ResponseApdu(0x6282)
        }

        val isLastRead = offset + size == contents.size
        val isLargeNdefFile = contents.size > 100
        val shouldDelayForBle = handoverSelectMessage != null && isLastRead && isLargeNdefFile &&
                transportsSetupTimestamp > 0

        if (shouldDelayForBle) {
            val timeSinceSetup = System.currentTimeMillis() - transportsSetupTimestamp
            val minimumDelayNeeded = 500

            if (timeSinceSetup < minimumDelayNeeded) {
                val remainingDelay = minimumDelayNeeded - timeSinceSetup
                ProximityLogger.i(
                    TAG,
                    "handleReadBinary: Last read of NDEF file (size=${contents.size}). Time since transport setup: ${timeSinceSetup}ms"
                )
                ProximityLogger.i(
                    TAG,
                    "handleReadBinary: Delaying ${remainingDelay}ms to ensure BLE is ready and visible (timestamp: ${System.currentTimeMillis()})"
                )
                withContext(Dispatchers.IO) {
                    Thread.sleep(remainingDelay)
                }
                ProximityLogger.i(
                    TAG,
                    "handleReadBinary: BLE should be fully visible now (timestamp: ${System.currentTimeMillis()})"
                )
            } else {
                ProximityLogger.i(
                    TAG,
                    "handleReadBinary: Last read of NDEF file. BLE has been ready for ${timeSinceSetup}ms, no delay needed"
                )
            }
        }

        val response = ByteArray(size)
        System.arraycopy(contents, offset, response, 0, size)
        val processingTime = System.currentTimeMillis() - startTime
        ProximityLogger.i(
            TAG, "handleReadBinary: returning $size bytes from offset $offset " +
                    "(file size ${contents.size}, processing time: ${processingTime}ms)"
        )

        if ((negotiatedHandoverState == NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT ||
                    (handoverSelectMessage != null))
            && isLastRead
        ) {
            reportHandoverSelectMessageSent()
        }

        return ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS, ByteString(response))
    }

    private fun handleUpdateBinaryNdefMessage(ndefMessage: ByteArray): ResponseApdu {
        return when (negotiatedHandoverState) {
            NEGOTIATED_HANDOVER_STATE_EXPECT_SERVICE_SELECT -> handleServiceSelect(ndefMessage)
            NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST -> handleHandoverRequest(ndefMessage)
            else -> {
                ProximityLogger.i(TAG, "Unexpected state $negotiatedHandoverState")
                ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
            }
        }
    }

    fun reportTwoWayEngagementDetected() {
        ProximityLogger.i(TAG, "reportTwoWayEngagementDetected")
        executor.execute {
            listener.onTwoWayEngagementDetected()
        }
    }

    private fun handleHandoverRequest(ndefMessagePayload: ByteArray): ResponseApdu {
        ProximityLogger.dHex(TAG, "handleHandoverRequest: payload", ndefMessagePayload)
        val message = try {
            NdefMessage(ndefMessagePayload)
        } catch (e: FormatException) {
            ProximityLogger.e(TAG, "handleHandoverRequest: Error parsing NdefMessage: $e")
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return ResponseApdu(0x6b00)
        }
        val records = message.records
        if (records.size < 2) {
            ProximityLogger.e(
                TAG,
                "handleServiceSelect: Expected at least two NdefRecords, found ${records.size}"
            )
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return ResponseApdu(0x6b00)
        }
        val parsedCms = mutableListOf<ConnectionMethod>()
        for (r in records) {
            if (r.tnf == NdefRecord.TNF_MIME_MEDIA || r.tnf == NdefRecord.TNF_EXTERNAL_TYPE) {
                val cm = NfcUtil.fromNdefRecord(r, false)
                if (cm != null) {
                    ProximityLogger.i(TAG, "Found connectionMethod: $cm")
                    parsedCms.add(cm)
                }
            }
        }
        if (parsedCms.isEmpty()) {
            ProximityLogger.i(TAG, "No connection methods found. Bailing.")
            negotiatedHandoverState =
                NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return ResponseApdu(0x6b00)
        }
        val disambiguatedCms = disambiguate(parsedCms)
        for (cm in disambiguatedCms) {
            ProximityLogger.i(TAG, "Have connectionMethod: $cm")
        }
        val method = disambiguatedCms[0]
        val listWithSelectedConnectionMethod = mutableListOf<ConnectionMethod>()
        listWithSelectedConnectionMethod.add(method)
        val hsMessage = NfcUtil.createNdefMessageHandoverSelect(
            listWithSelectedConnectionMethod,
            deviceEngagement,
            options
        )
        val fileContents = ByteArray(hsMessage.size + 2)
        fileContents[0] = (hsMessage.size / 255).toByte()
        fileContents[1] = (hsMessage.size and 0xff).toByte()
        System.arraycopy(hsMessage, 0, fileContents, 2, hsMessage.size)
        selectedNfcFile = fileContents
        negotiatedHandoverState =
            NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT
        handoverSelectMessage = hsMessage
        handoverRequestMessage = ndefMessagePayload
        handover = Cbor.encode(
            CborArray.builder()
                .add(handoverSelectMessage!!)
                .add(handoverRequestMessage!!)
                .end()
                .build()
        )
        ProximityLogger.dCbor(TAG, "NFC negotiated DeviceEngagement", deviceEngagement)
        ProximityLogger.dCbor(TAG, "NFC negotiated Handover", handover)
        setupTransports(listWithSelectedConnectionMethod)
        return ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS)
    }

    private fun handleServiceSelect(ndefMessagePayload: ByteArray): ResponseApdu {
        ProximityLogger.dHex(TAG, "handleServiceSelect: payload", ndefMessagePayload)
        val message = try {
            NdefMessage(ndefMessagePayload)
        } catch (e: FormatException) {
            ProximityLogger.e(TAG, "handleServiceSelect: Error parsing NdefMessage: $e")
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return ResponseApdu(0x6b00)
        }
        val records = message.records
        if (records.size != 1) {
            ProximityLogger.e(
                TAG,
                "handleServiceSelect: Expected one NdefRecord, found ${records.size}"
            )
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return ResponseApdu(0x6b00)
        }
        val record = records[0]
        val expectedPayload = " urn:nfc:sn:handover".toByteArray()
        expectedPayload[0] = (expectedPayload.size - 1).toByte()
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN ||
            !record.type.contentEquals("Ts".toByteArray()) ||
            record.payload == null ||
            !record.payload.contentEquals(expectedPayload)
        ) {
            ProximityLogger.e(TAG, "handleServiceSelect: NdefRecord is malformed")
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return ResponseApdu(0x6b00)
        }
        ProximityLogger.i(TAG, "Service Select NDEF message has been validated")
        reportTwoWayEngagementDetected()

        val statusMessage = NfcEngagementHelperUtils.calculateStatusMessage()
        ProximityLogger.dHex(TAG, "handleServiceSelect: Status message", statusMessage)
        val fileContents = ByteArray(statusMessage.size + 2)
        fileContents[0] = (statusMessage.size / 255).toByte()
        fileContents[1] = (statusMessage.size and 0xff).toByte()
        System.arraycopy(statusMessage, 0, fileContents, 2, statusMessage.size)
        selectedNfcFile = fileContents
        negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST
        return ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS)
    }

    private fun processUpdateBinary(command: CommandApdu): ResponseApdu {
        ProximityLogger.i(TAG, "COMMAND: update_binary")
        val apdu = command.encode()
        if (apdu.size < 5) {
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        }
        val offset =
            (apdu[2].toInt() and 0xff) * 255 + (apdu[3].toInt() and 0xff)
        val size = apdu[4].toInt() and 0xff
        val dataSize = apdu.size - 5
        if (dataSize != size) {
            ProximityLogger.e(
                TAG,
                "Expected length embedded in APDU to be $dataSize but found $size"
            )
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        }

        val payload = ByteArray(dataSize)
        System.arraycopy(apdu, 5, payload, 0, dataSize)
        ProximityLogger.dHex(
            TAG,
            "handleUpdateBinary: payload",
            payload
        )
        return if (offset == 0) {
            if (payload.size == 2) {
                if (payload[0].toInt() == 0x00 && payload[1].toInt() == 0x00) {
                    ProximityLogger.i(
                        TAG,
                        "handleUpdateBinary: Reset length message"
                    )
                    if (updateBinaryData != null) {
                        ProximityLogger.i(
                            TAG,
                            "Got reset but we are already active"
                        )
                        return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
                    }
                    updateBinaryData = ByteArray(0)
                    ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS)
                } else {
                    val length =
                        (apdu[5].toInt() and 0xff) * 255 + (apdu[6].toInt() and 0xff)
                    ProximityLogger.i(
                        TAG,
                        "handleUpdateBinary: Update length message with length $length"
                    )
                    if (updateBinaryData == null) {
                        ProximityLogger.i(
                            TAG,
                            "Got length but we are not active"
                        )
                        return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
                    }
                    if (length != updateBinaryData!!.size) {
                        ProximityLogger.i(
                            TAG,
                            "Length $length doesn't match received data of " +
                                    "${updateBinaryData!!.size} bytes"
                        )
                        return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
                    }

                    val ndefMessage = updateBinaryData!!
                    updateBinaryData = null
                    handleUpdateBinaryNdefMessage(ndefMessage)
                }
            } else {
                if (updateBinaryData != null) {
                    ProximityLogger.i(
                        TAG,
                        "Got data in single UPDATE_BINARY but we are already active"
                    )
                    return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
                }
                ProximityLogger.dHex(
                    TAG,
                    "handleUpdateBinary: single UPDATE_BINARY message " +
                            "with payload: ",
                    payload
                )
                val ndefMessage = payload.copyOfRange(2, payload.size)
                handleUpdateBinaryNdefMessage(ndefMessage)
            }
        } else if (offset == 1) {
            ProximityLogger.i(
                TAG,
                "Unexpected offset $offset"
            )
            ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
        } else {
            if (updateBinaryData == null) {
                ProximityLogger.i(
                    TAG,
                    "Got data but we are not active"
                )
                ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_FILE_OR_APPLICATION_NOT_FOUND)
            }
            ProximityLogger.dHex(
                TAG,
                "handleUpdateBinary: Data message offset $offset with payload: ",
                payload
            )
            val newLength = offset - 2 + payload.size
            if (updateBinaryData!!.size < newLength) {
                updateBinaryData = updateBinaryData!!.copyOf(newLength)
            }
            System.arraycopy(payload, 0, updateBinaryData!!, offset - 2, payload.size)
            ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS)
        }
    }

    private fun processSelectFile(command: CommandApdu): ResponseApdu {
        ProximityLogger.i(TAG, "COMMAND: select_file")
        val apdu = command.encode()
        if (apdu.size < 7) {
            ProximityLogger.i(TAG, "handleSelectFile: unexpected APDU length " + apdu.size)
            return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_INSTRUCTION_NOT_SUPPORTED_OR_INVALID)
        }
        val fileId =
            (apdu[5].toInt() and 0xff) * 256 + (apdu[6].toInt() and 0xff)
        ProximityLogger.i(TAG, "handleSelectFile: fileId $fileId")

        when (fileId) {
            NfcUtil.CAPABILITY_CONTAINER_FILE_ID -> {
                selectedNfcFile = byteArrayOf(
                    0x00.toByte(), 0x0f.toByte(), 0x20.toByte(), 0x7f.toByte(),
                    0xff.toByte(), 0x7f.toByte(), 0xff.toByte(), 0x04.toByte(),
                    0x06.toByte(), 0xe1.toByte(), 0x04.toByte(), 0x7f.toByte(),
                    0xff.toByte(), 0x00.toByte(), 0xff.toByte()
                )
                ProximityLogger.i(TAG, "handleSelectFile: CAPABILITY file selected")
            }

            NfcUtil.NDEF_FILE_ID -> {
                ProximityLogger.i(
                    TAG, "handleSelectFile: NDEF file selected and using static " +
                            "handover - setting up transports"
                )
                val cmsFromTransports = setupTransports(staticHandoverConnectionMethods!!)

                ProximityLogger.i(
                    TAG,
                    "handleSelectFile: Creating handover message with ${cmsFromTransports.size} connection method(s)"
                )
                for ((index, cm) in cmsFromTransports.withIndex()) {
                    ProximityLogger.i(TAG, "handleSelectFile: Connection method #${index + 1}: $cm")
                }
                val hsMessage = NfcUtil.createNdefMessageHandoverSelect(
                    cmsFromTransports,
                    deviceEngagement,
                    options
                )
                ProximityLogger.dHex(TAG, "handleSelectFile: Handover Select", hsMessage)

                try {
                    val ndefMsg = NdefMessage(hsMessage)
                    ProximityLogger.i(
                        TAG,
                        "handleSelectFile: Handover Select contains ${ndefMsg.records.size} NDEF record(s)"
                    )
                    for ((index, record) in ndefMsg.records.withIndex()) {
                        val typeStr = String(record.type)
                        val idStr = if (record.id != null) String(record.id) else "null"
                        ProximityLogger.i(
                            TAG,
                            "  Record #${index + 1}: TNF=${record.tnf}, Type='$typeStr', ID='$idStr', PayloadSize=${record.payload?.size ?: 0}"
                        )
                    }
                } catch (e: Exception) {
                    ProximityLogger.e(
                        TAG,
                        "handleSelectFile: Error parsing NDEF message for logging: $e"
                    )
                }
                val fileContents = ByteArray(hsMessage.size + 2)
                fileContents[0] = (hsMessage.size / 255).toByte()
                fileContents[1] = (hsMessage.size and 0xff).toByte()
                System.arraycopy(hsMessage, 0, fileContents, 2, hsMessage.size)
                ProximityLogger.dHex(TAG, "FILE CONTENTS:", fileContents)
                selectedNfcFile = fileContents
                handover = createHandover(hsMessage)
                ProximityLogger.dCbor(TAG, "NFC static DeviceEngagement", deviceEngagement)
                ProximityLogger.dCbor(TAG, "NFC static Handover", handover)
            }

            else -> {
                ProximityLogger.i(TAG, "handleSelectFile: Unknown file selected with id $fileId")
                return ResponseApdu(Nfc.RESPONSE_STATUS_ERROR_INSTRUCTION_NOT_SUPPORTED_OR_INVALID)
            }
        }
        return ResponseApdu(Nfc.RESPONSE_STATUS_SUCCESS)
    }

    internal suspend fun onDeactivated() {
        mutex.withLock {
            instances.remove(this)
            failTransport(Error("onDeactivated"))
        }
    }

    interface Listener {
        fun onTwoWayEngagementDetected()
        fun onHandoverSelectMessageSent()
        fun onDeviceConnecting()
        fun onDeviceConnected(transport: DataTransport)
        fun onError(error: Throwable)
    }
}