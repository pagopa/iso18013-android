package it.pagopa.io.wallet.proximity.nfc

import android.content.Context
import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.mdoc.transport.DataTransport.Companion.fromConnectionMethod
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.android.util.NfcUtil
import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Tagged
import com.android.identity.crypto.EcPublicKey
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethod.Companion.disambiguate
import com.android.identity.mdoc.engagement.EngagementGenerator
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.nfc.apdu.Utils
import kotlinx.coroutines.Job
import java.util.concurrent.Executor

/**
 * Helper used for NFC engagement.
 *
 *
 * This implements NFC engagement as defined in ISO/IEC 18013-5:2021.
 *
 *
 * Applications can instantiate a [NfcEngagementHelperRefactor] using
 * [NfcEngagementHelperRefactor.Builder] to specify the NFC engagement
 * type (static or negotiated) and other details, such as which device
 * retrieval methods to offer with static handover.
 *
 *
 * If negotiated handover is used, [Listener.onTwoWayEngagementDetected]
 * is called when the NFC tag reader has selected the connection handover service.
 *
 *
 * When a remote mdoc reader connects to either one of the transports advertised
 * via static handover or one of the transports offered by the reader via
 * negotiated handover, [Listener.onDeviceConnected] is called
 * and the application can use the passed-in [DataTransport] to create a
 * [com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper]
 * to start the transaction.
 */
class NfcEngagementHelperRefactor private constructor(
    private val context: Context,
    private val eDeviceKey: EcPublicKey,
    private val options: DataTransportOptions,
    private val listener: Listener,
    private val executor: Executor,
    private val usingBle: Boolean = true
) {
    private var staticHandoverConnectionMethods: List<ConnectionMethod>? = null
    private var job = Job()

    // Dynamically created when a NFC tag reader is in the field
    private var transports = mutableListOf<DataTransport>()

    /**
     * Gets the bytes of the `DeviceEngagement` CBOR.
     *
     * This returns the bytes of the `DeviceEngagement` CBOR according to
     * ISO/IEC 18013-5:2021 section 8.2.2.1.
     */
    val deviceEngagement: ByteArray = EngagementGenerator(
        eDeviceKey,
        EngagementGenerator.ENGAGEMENT_VERSION_1_0
    ).generate()

    /**
     * Gets the bytes of the `Handover` CBOR.
     *
     * This returns the bytes of the `Handover` CBOR according to
     * ISO/IEC 18013-5:2021 section 9.1.5.1.
     */
    lateinit var handover: ByteArray

    private var reportedDeviceConnecting = false
    private var handoverSelectMessage: ByteArray? = null
    private var handoverRequestMessage: ByteArray? = null

    private var negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
    private var selectedNfcFile: ByteArray? = null
    private var peerConnected = false

    // Timestamp di quando setupTransports è stato chiamato, per calcolare il delay necessario
    private var transportsSetupTimestamp: Long = 0

    /**
     * Close all transports currently being listened on.
     *
     * No callbacks will be done on a listener after calling this.
     *
     * This method is idempotent so it is safe to call multiple times.
     */
    fun close() {
        if (!transports.isEmpty()) {
            var numTransportsClosed = 0
            for (transport in transports) {
                transport.close()
                numTransportsClosed += 1
            }
            ProximityLogger.i(TAG, "Closed $numTransportsClosed transports")
            transports.clear()
        }
        selectedNfcFile = null
        transportsSetupTimestamp = 0
    }

    // Used by both static and negotiated handover... safe to be called multiple times.
    private fun setupTransports(connectionMethods: List<ConnectionMethod>): List<ConnectionMethod> {
        val setupConnectionMethods = mutableListOf<ConnectionMethod>()

        // IMPORTANTE: Chiudi tutti i trasporti esistenti prima di crearne di nuovi
        // Questo previene duplicati quando NFC viene tappato multiple volte
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
            Tagged(24, Bstr(Cbor.encode(eDeviceKey.toCoseKey().toDataItem())))
        )

        // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
        // if both BLE modes are available at the same time.
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

        // Careful, we're using the user-provided Executor below so these callbacks might happen
        // in another thread than we're in right now. For example this happens if using
        // ThreadPoolExecutor.
        //
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
                    job.cancel()
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
                    job.cancel()
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

        // Salva il timestamp per calcolare quanto tempo è passato quando il verifier leggerà il file
        transportsSetupTimestamp = System.currentTimeMillis()

        return setupConnectionMethods
    }

    /**
     * Method to call when link has been lost or if a different NFC AID has been selected.
     *
     * This should be called from the application's implementation of
     * [android.nfc.cardemulation.HostApduService.onDeactivated].
     *
     * @param reason Either [android.nfc.cardemulation.HostApduService.DEACTIVATION_LINK_LOSS]
     * or [android.nfc.cardemulation.HostApduService.DEACTIVATION_DESELECTED].
     */
    fun nfcOnDeactivated(reason: Int) {
        ProximityLogger.i(TAG, "nfcOnDeactivated reason $reason")
        job.cancel()
    }

    /**
     * Method to call when a command APDU has been received.
     *
     *
     * This should be called from the application's implementation of
     * [android.nfc.cardemulation.HostApduService.processCommandApdu].
     *
     * @param apdu The APDU that was received from the remote device.
     * @return a byte-array containing the response APDU.
     */
    fun nfcProcessCommandApdu(apdu: ByteArray): ByteArray {
        if (ProximityLogger.enabled) {
            ProximityLogger.d(TAG, "nfcProcessCommandApdu: apdu: ${Utils.bytesToHex(apdu)}")
        }
        return when (val commandType = NfcUtil.nfcGetCommandType(apdu)) {
            NfcUtil.COMMAND_TYPE_SELECT_BY_AID -> handleSelectByAid(apdu)
            NfcUtil.COMMAND_TYPE_SELECT_FILE -> handleSelectFile(apdu)
            NfcUtil.COMMAND_TYPE_READ_BINARY -> {
                val resp = handleReadBinary(apdu)
                return resp
            }

            NfcUtil.COMMAND_TYPE_UPDATE_BINARY -> handleUpdateBinary(apdu)
            else -> {
                ProximityLogger.i(
                    TAG,
                    "nfcProcessCommandApdu: command type $commandType not handled"
                )
                NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED
            }
        }
    }

    private fun handleSelectByAid(apdu: ByteArray): ByteArray {
        if (apdu.size < 12) {
            ProximityLogger.i(TAG, "handleSelectByAid: unexpected APDU length ${apdu.size}")
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }
        if (apdu.copyOfRange(5, 12)
                .contentEquals(NfcUtil.AID_FOR_MDL_DATA_TRANSFER)
        ) {
            ProximityLogger.i(TAG, "handleInitialSelectByAid: connecting")
            updateBinaryData = null
            return NfcUtil.STATUS_WORD_OK
        }
        if (apdu.copyOfRange(5, 12)
                .contentEquals(NfcUtil.AID_FOR_TYPE_4_TAG_NDEF_APPLICATION)
        ) {
            ProximityLogger.i(TAG, "handleSelectByAid: NDEF application selected")
            updateBinaryData = null
            return NfcUtil.STATUS_WORD_OK
        }
        NfcEngagementEventBus.tryEmit(NfcEngagementEvent.NotSupported)
        ProximityLogger.dHex(TAG, "handleSelectByAid: Unexpected AID selected in APDU", apdu)
        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
    }

    private fun handleSelectFile(apdu: ByteArray): ByteArray {
        if (apdu.size < 7) {
            ProximityLogger.i(TAG, "handleSelectFile: unexpected APDU length " + apdu.size)
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }
        val fileId = (apdu[5].toInt() and 0xff) * 256 + (apdu[6].toInt() and 0xff)
        ProximityLogger.i(TAG, "handleSelectFile: fileId $fileId")
        // We only support two files
        if (fileId == NfcUtil.CAPABILITY_CONTAINER_FILE_ID) {
            // This is defined in NFC Forum Type 4 Tag Technical Specification v1.2 table 6
            // and section 4.7.3 NDEF-File_Ctrl_TLV
            selectedNfcFile = byteArrayOf(
                0x00.toByte(),
                0x0f.toByte(),
                0x20.toByte(),
                0x7f.toByte(),
                0xff.toByte(),
                0x7f.toByte(),
                0xff.toByte(),
                0x04.toByte(),
                0x06.toByte(),
                0xe1.toByte(),
                0x04.toByte(),
                0x7f.toByte(),
                0xff.toByte(),
                0x00.toByte(),  // file read access condition (allow read)
                0xff.toByte() // file write access condition (allow/disallow write)
            )
            ProximityLogger.i(TAG, "handleSelectFile: CAPABILITY file selected")
        } else if (fileId == NfcUtil.NDEF_FILE_ID) {
            ProximityLogger.i(
                TAG, "handleSelectFile: NDEF file selected and using static "
                        + "handover - setting up transports"
            )
            val cmsFromTransports = setupTransports(
                staticHandoverConnectionMethods!!
            )

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

            // Log dettagliato dei record NDEF
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
            fileContents[0] = (hsMessage.size / 256).toByte()
            fileContents[1] = (hsMessage.size and 0xff).toByte()
            System.arraycopy(hsMessage, 0, fileContents, 2, hsMessage.size)
            selectedNfcFile = fileContents
            handoverSelectMessage = hsMessage
            handoverRequestMessage = null
            handover = Cbor.encode(
                CborArray.builder()
                    .add(handoverSelectMessage!!) // Handover Select message
                    .add(Simple.NULL)             // Handover Request message
                    .end()
                    .build()
            )
            ProximityLogger.dCbor(TAG, "NFC static DeviceEngagement", deviceEngagement)
            ProximityLogger.dCbor(TAG, "NFC static Handover", handover)
        } else {
            ProximityLogger.i(TAG, "handleSelectFile: Unknown file selected with id $fileId")
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }
        return NfcUtil.STATUS_WORD_OK
    }

    private fun handleReadBinary(apdu: ByteArray): ByteArray {
        val startTime = System.currentTimeMillis()
        if (apdu.size < 5) {
            ProximityLogger.i(TAG, "handleReadBinary: unexpected APDU length ${apdu.size}")
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }
        if (selectedNfcFile == null) {
            ProximityLogger.i(
                TAG,
                "handleReadBinary: no file selected -> STATUS_WORD_FILE_NOT_FOUND"
            )
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }
        val contents: ByteArray = selectedNfcFile!!
        val offset = (apdu[2].toInt() and 0xff) * 256 + (apdu[3].toInt() and 0xff)
        var size = apdu[4].toInt() and 0xff
        if (size == 0) {
            // Handle Extended Length encoding
            if (apdu.size < 7) {
                return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
            }
            size = (apdu[5].toInt() and 0xff) * 256
            size += apdu[6].toInt() and 0xff
        }
        if (offset >= contents.size) {
            ProximityLogger.i(
                TAG, "handleReadBinary: starting offset $offset beyond file " +
                        "end ${contents.size} -> STATUS_WORD_WRONG_PARAMETERS"
            )
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
        }
        if (offset + size > contents.size) {
            ProximityLogger.i(
                TAG, "handleReadBinary: ending offset ${offset + size} beyond file" +
                        " end ${contents.size} -> STATUS_WORD_END_OF_FILE_REACHED"
            )
            return NfcUtil.STATUS_WORD_END_OF_FILE_REACHED
        }
        // Se stiamo per inviare gli ultimi byte del file NDEF (quello con Handover Select)
        // in static handover con BLE, aggiungi un delay se necessario per dare tempo al BLE di essere pronto.
        // Il CAPABILITY file è piccolo (15 bytes), il file NDEF con Handover Select è molto più grande (200+ bytes)
        val isLastRead = offset + size == contents.size
        val isLargeNdefFile = contents.size > 100 // Il file NDEF è > 100 bytes, CAPABILITY è 15
        val shouldDelayForBle =
            usingBle && handoverSelectMessage != null && isLastRead && isLargeNdefFile &&
                    transportsSetupTimestamp > 0

        if (shouldDelayForBle) {
            val timeSinceSetup = System.currentTimeMillis() - transportsSetupTimestamp
            val minimumDelayNeeded =
                500 // Aumentato a 500ms per dare più tempo al BLE di essere visibile

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
                Thread.sleep(remainingDelay)
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

        val response = ByteArray(size + NfcUtil.STATUS_WORD_OK.size)
        System.arraycopy(contents, offset, response, 0, size)
        System.arraycopy(NfcUtil.STATUS_WORD_OK, 0, response, size, NfcUtil.STATUS_WORD_OK.size)
        val processingTime = System.currentTimeMillis() - startTime
        ProximityLogger.i(
            TAG, "handleReadBinary: returning $size bytes from offset $offset " +
                    "(file size ${contents.size}, processing time: ${processingTime}ms)"
        )

        // Chiamata reportHandoverSelectMessageSent() solo quando tutti i byte sono stati letti
        if ((negotiatedHandoverState == NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT ||
                    (handoverSelectMessage != null))
            && isLastRead
        ) {
            reportHandoverSelectMessageSent()
        }

        return response
    }

    private var updateBinaryData: ByteArray? = null

    private fun handleUpdateBinary(apdu: ByteArray): ByteArray {
        if (apdu.size < 5) {
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }
        val offset = (apdu[2].toInt() and 0xff) * 256 + (apdu[3].toInt() and 0xff)
        val size = apdu[4].toInt() and 0xff
        val dataSize = apdu.size - 5
        if (dataSize != size) {
            ProximityLogger.e(
                TAG,
                "Expected length embedded in APDU to be $dataSize but found $size"
            )
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }

        // This code implements the procedure specified by
        //
        //  Type 4 Tag Technical Specification Version 1.2 section 7.5.5 NDEF Write Procedure
        val payload = ByteArray(dataSize)
        System.arraycopy(apdu, 5, payload, 0, dataSize)
        ProximityLogger.dHex(TAG, "handleUpdateBinary: payload", payload)
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
                        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
                    }
                    updateBinaryData = ByteArray(0)
                    NfcUtil.STATUS_WORD_OK
                } else {
                    val length = (apdu[5].toInt() and 0xff) * 256 + (apdu[6].toInt() and 0xff)
                    ProximityLogger.i(
                        TAG,
                        "handleUpdateBinary: Update length message with length $length"
                    )
                    if (updateBinaryData == null) {
                        ProximityLogger.i(TAG, "Got length but we are not active")
                        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
                    }
                    if (length != updateBinaryData!!.size) {
                        ProximityLogger.i(
                            TAG, "Length $length doesn't match received data of " +
                                    "${updateBinaryData!!.size} bytes"
                        )
                        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
                    }

                    // At this point we got the whole NDEF message that the reader wanted to send.
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
                    return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
                }
                ProximityLogger.dHex(
                    TAG, "handleUpdateBinary: single UPDATE_BINARY message " +
                            "with payload: ", payload
                )
                val ndefMessage = payload.copyOfRange(2, payload.size)
                handleUpdateBinaryNdefMessage(ndefMessage)
            }
        } else if (offset == 1) {
            ProximityLogger.i(TAG, "Unexpected offset $offset")
            NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        } else {
            // offset >= 2
            if (updateBinaryData == null) {
                ProximityLogger.i(TAG, "Got data but we are not active")
                return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
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
            NfcUtil.STATUS_WORD_OK
        }
    }

    private fun handleUpdateBinaryNdefMessage(ndefMessage: ByteArray): ByteArray {
        // Falls through here only if we have a full NDEF message.
        return if (negotiatedHandoverState == NEGOTIATED_HANDOVER_STATE_EXPECT_SERVICE_SELECT) {
            handleServiceSelect(ndefMessage)
        } else if (negotiatedHandoverState == NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST) {
            handleHandoverRequest(ndefMessage)
        } else {
            ProximityLogger.i(TAG, "Unexpected state $negotiatedHandoverState")
            NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }
    }

    private fun handleServiceSelect(ndefMessagePayload: ByteArray): ByteArray {
        ProximityLogger.dHex(TAG, "handleServiceSelect: payload", ndefMessagePayload)
        // NDEF message specified in NDEF Exchange Protocol 1.0: 4.2.2 Service Select Record
        val message = try {
            NdefMessage(ndefMessagePayload)
        } catch (e: FormatException) {
            ProximityLogger.e(TAG, "handleServiceSelect: Error parsing NdefMessage: $e")
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
        }
        val records = message.records
        if (records.size != 1) {
            ProximityLogger.e(
                TAG,
                "handleServiceSelect: Expected one NdefRecord, found ${records.size}"
            )
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
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
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
        }
        ProximityLogger.i(TAG, "Service Select NDEF message has been validated")
        reportTwoWayEngagementDetected()

        // From NDEF Exchange Protocol 1.0: 4.3 TNEP Status Message
        // If the NFC Tag Device has received a Service Select Message with a known
        // Service, it will return a TNEP Status Message to confirm a successful
        // Service selection.
        val statusMessage = calculateStatusMessage(0x00)
        ProximityLogger.dHex(TAG, "handleServiceSelect: Status message", statusMessage)
        val fileContents = ByteArray(statusMessage.size + 2)
        fileContents[0] = (statusMessage.size / 256).toByte()
        fileContents[1] = (statusMessage.size and 0xff).toByte()
        System.arraycopy(statusMessage, 0, fileContents, 2, statusMessage.size)
        selectedNfcFile = fileContents
        negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST
        return NfcUtil.STATUS_WORD_OK
    }

    private fun handleHandoverRequest(ndefMessagePayload: ByteArray): ByteArray {
        ProximityLogger.dHex(TAG, "handleHandoverRequest: payload", ndefMessagePayload)
        val message = try {
            NdefMessage(ndefMessagePayload)
        } catch (e: FormatException) {
            ProximityLogger.e(TAG, "handleHandoverRequest: Error parsing NdefMessage: $e")
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
        }
        val records = message.records
        if (records.size < 2) {
            ProximityLogger.e(
                TAG,
                "handleServiceSelect: Expected at least two NdefRecords, found ${records.size}"
            )
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
        }
        val parsedCms = mutableListOf<ConnectionMethod>()
        for (r in records) {
            // This parses the various carrier specific NDEF records, see
            // DataTransport.parseNdefRecord() for details.
            //
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
            negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_NOT_STARTED
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
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
        fileContents[0] = (hsMessage.size / 256).toByte()
        fileContents[1] = (hsMessage.size and 0xff).toByte()
        System.arraycopy(hsMessage, 0, fileContents, 2, hsMessage.size)
        selectedNfcFile = fileContents
        negotiatedHandoverState = NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT
        handoverSelectMessage = hsMessage
        handoverRequestMessage = ndefMessagePayload
        handover = Cbor.encode(
            CborArray.builder()
                .add(handoverSelectMessage!!)    // Handover Select message
                .add(handoverRequestMessage!!)   // Handover Request message
                .end()
                .build()
        )
        ProximityLogger.dCbor(TAG, "NFC negotiated DeviceEngagement", deviceEngagement)
        ProximityLogger.dCbor(TAG, "NFC negotiated Handover", handover)


        // Technically we should ensure the transports are up until sending the response...
        setupTransports(listWithSelectedConnectionMethod)
        return NfcUtil.STATUS_WORD_OK
    }

    private fun calculateStatusMessage(statusCode: Int): ByteArray {
        val payload = byteArrayOf(statusCode.toByte())
        val record = NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            "Te".toByteArray(),
            null,
            payload
        )
        val arrayOfRecords = arrayOfNulls<NdefRecord>(1)
        arrayOfRecords[0] = record
        val message = NdefMessage(arrayOfRecords)
        return message.toByteArray()
    }

    fun peerIsConnecting() {
        if (!reportedDeviceConnecting) {
            reportedDeviceConnecting = true
            reportDeviceConnecting()
        }
    }

    fun peerHasConnected(transport: DataTransport) {
        // stop listening on other transports
        //
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
        peerConnected = true
    }

    // Note: The report*() methods are safe to call from any thread.
    fun reportTwoWayEngagementDetected() {
        ProximityLogger.i(TAG, "reportTwoWayEngagementDetected")
        val listener = listener
        val executor = executor
        executor.execute {
            listener.onTwoWayEngagementDetected()
        }
    }

    fun reportHandoverSelectMessageSent() {
        ProximityLogger.i(TAG, "onHandoverSelectMessageSent")
        val listener = listener
        val executor = executor
        executor.execute {
            listener.onHandoverSelectMessageSent()
        }
    }

    fun reportDeviceConnecting() {
        ProximityLogger.i(TAG, "reportDeviceConnecting")
        val listener = listener
        val executor = executor
        executor.execute {
            listener.onDeviceConnecting()
        }
    }

    fun reportDeviceConnected(transport: DataTransport) {
        ProximityLogger.i(TAG, "reportDeviceConnected: transport=$transport")
        val listener = listener
        val executor = executor
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

    fun reportError(error: Throwable) {
        ProximityLogger.i(TAG, "reportError: error: :$error")
        val listener = listener
        val executor = executor
        job.cancel()
        executor.execute {
            listener.onError(error)
        }
    }

    /**
     * Listener for [NfcEngagementHelperRefactor].
     */
    interface Listener {
        /**
         * Called when two-way engagement has been detected.
         *
         *
         * If negotiated handover is used, this is called when the NFC tag reader has
         * selected the connection handover service.
         */
        fun onTwoWayEngagementDetected()

        /**
         * Called when the Handover Select message has been sent to the NFC tag reader.
         *
         *
         * This is a good point for an app to notify the user that an mdoc transaction
         * is about to to take place and they can start removing the device from the field.
         */
        fun onHandoverSelectMessageSent()

        /**
         * Called when a remote mdoc reader is starting to connect.
         */
        fun onDeviceConnecting()

        /**
         * Called when a remote mdoc reader has connected.
         *
         *
         * The application should use the passed-in [DataTransport] with
         * [com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper]
         * to start the transaction.
         *
         *
         * After this is called, no more callbacks will be done on listener and all other
         * listening transports will be closed. Calling [.close] will not close the
         * passed-in transport.
         *
         * @param transport a [DataTransport] for the connection to the remote mdoc reader.
         */
        fun onDeviceConnected(transport: DataTransport)

        /**
         * Called when an irrecoverable error has occurred.
         *
         * @param error details of what error has occurred.
         */
        fun onError(error: Throwable)
    }

    /**
     * A builder for [NfcEngagementHelperRefactor].
     *
     * @param context application context.
     * @param eDeviceKey the public part of `EDeviceKey` for *mdoc session
     * encryption* according to ISO/IEC 18013-5:2021 section 9.1.1.4.
     * @param options set of options for creating [DataTransport] instances.
     * @param listener the listener.
     * @param executor a [Executor] to use with the listener.
     */
    class Builder(
        context: Context,
        eDeviceKey: EcPublicKey,
        options: DataTransportOptions,
        listener: Listener,
        executor: Executor,
        usingBle: Boolean
    ) {
        var helper = NfcEngagementHelperRefactor(
            context,
            eDeviceKey,
            options,
            listener,
            executor,
            usingBle
        )

        /**
         * Configures the builder so NFC Static Handover is used.
         *
         * @param connectionMethods a list of connection methods to use.
         * @return the builder.
         */
        infix fun staticHandoverWith(connectionMethods: List<ConnectionMethod>) = apply {
            helper.staticHandoverConnectionMethods = connectionMethods
        }

        /**
         * Builds the [NfcEngagementHelperRefactor] and starts listening for connections.
         *
         *]
         * and deactivation events using [.nfcOnDeactivated].
         *
         * @return the helper, ready to be used.
         */
        fun build(): NfcEngagementHelperRefactor {
            return helper
        }
    }

    companion object {
        private const val TAG = "NfcEngagementHelper"
        private const val NEGOTIATED_HANDOVER_STATE_NOT_STARTED = 0
        private const val NEGOTIATED_HANDOVER_STATE_EXPECT_SERVICE_SELECT = 1
        private const val NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_REQUEST = 2
        private const val NEGOTIATED_HANDOVER_STATE_EXPECT_HANDOVER_SELECT = 3
    }
}