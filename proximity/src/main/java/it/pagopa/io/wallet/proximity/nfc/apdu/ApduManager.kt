package it.pagopa.io.wallet.proximity.nfc.apdu

import com.android.identity.android.util.NfcUtil
import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.RawCbor
import com.android.identity.cbor.Tagged
import com.android.identity.mdoc.engagement.EngagementParser
import com.android.identity.mdoc.request.DeviceRequestParser
import it.pagopa.io.wallet.cbor.model.DocType
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.cbor.model.EU_PID_DOCTYPE
import it.pagopa.io.wallet.cbor.model.MDL_DOCTYPE
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.nfc.NfcEngagement
import it.pagopa.io.wallet.proximity.qr_code.toReaderAuthWith
import it.pagopa.io.wallet.proximity.request.DocRequested
import it.pagopa.io.wallet.proximity.request.RequestWrapper
import it.pagopa.io.wallet.proximity.response.ResponseGenerator
import it.pagopa.io.wallet.proximity.toRequest
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal class ApduManager(
    private val engagement: NfcEngagement,
    private val docs: Array<Document>,
    private val alias: String
) {
    private val tag = "ApduManager"
    private var envelopeBuffer = ByteArrayOutputStream()
    private var envelopeCollecting: Boolean = false
    private var responseBuffer: ByteArray? = null   // DO'53' already TLV codified
    private var responseOffset: Int = 0
    private var useExtendedLength: Boolean = true

    private fun resetApduDataRetrievalState() {
        envelopeBuffer.reset()
        envelopeCollecting = false
        responseBuffer = null
        responseOffset = 0
        useExtendedLength = true
    }

    fun handleSelectByAid(apdu: ByteArray): ByteArray {
        if (apdu.size < 12) {
            ProximityLogger.i(tag, "handleSelectByAid: unexpected APDU length ${apdu.size}")
            return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
        }
        val selectedAid = apdu.copyOfRange(5, 12)
        ProximityLogger.d(tag, "handleSelectByAid: AID = ${Utils.bytesToHex(selectedAid)}")
        if (selectedAid.contentEquals(NfcUtil.AID_FOR_MDL_DATA_TRANSFER)) {
            ProximityLogger.d(tag, "handleSelectByAid: mDL data transfer AID selected")
            resetApduDataRetrievalState()
            return NfcUtil.STATUS_WORD_OK
        }
        ProximityLogger.d(
            tag,
            "handleSelectByAid: Unexpected AID selected in APDU:${Utils.bytesToHex(apdu)}"
        )
        return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
    }

    // ENVELOPE (INS=C3)
    fun handleEnvelope(apdu: ByteArray): ByteArray {
        ProximityLogger.i(tag, "ENVELOPE - APDU size: ${apdu.size}")
        // INS = C3, P1P2=0000, datadata=DO'53' o part of; Nc=0 for "end of data string"
        if (apdu.size < 4 || (apdu[1].toInt() and 0xff) != 0xC3) {
            ProximityLogger.e(tag, "ENVELOPE: Invalid instruction")
            return NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED
        }
        if (((apdu[2].toInt() and 0xff) != 0x00) || ((apdu[3].toInt() and 0xff) != 0x00)) {
            ProximityLogger.e(tag, "ENVELOPE: Wrong parameters P1P2")
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
        }
        val (nc, dataField) = parseLcAndDataField(apdu)
        ProximityLogger.d(tag, "ENVELOPE: Nc=$nc, dataField size=${dataField.size}, useExtendedLength=$useExtendedLength")

        if (nc == 0) {
            // End of data string
            if (!envelopeCollecting) {
                ProximityLogger.e(tag, "ENVELOPE: End of data but no collecting in progress")
                return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
            }
            val fullRequest = envelopeBuffer.toByteArray()
            ProximityLogger.i(tag, "ENVELOPE: Full request collected, size=${fullRequest.size}")
            envelopeBuffer.reset()
            envelopeCollecting = false

            val requestEncrypted: ByteArray = try {
                parseDo53(fullRequest)
            } catch (e: Throwable) {
                ProximityLogger.e(tag, "ENVELOPE: Error parsing DO'53': ${e.message}")
                return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
            }
            ProximityLogger.d(tag, "ENVELOPE: Encrypted request size=${requestEncrypted.size}")

            val responseEncrypted: ByteArray = try {
                processReaderRequest(requestEncrypted)
            } catch (e: Throwable) {
                ProximityLogger.e(tag, "ENVELOPE: Error processing request: ${e.message}")
                return NfcUtil.STATUS_WORD_FILE_NOT_FOUND
            }
            ProximityLogger.d(tag, "ENVELOPE: Response encrypted size=${responseEncrypted.size}")

            // Encapsulating in DO'53'
            responseBuffer = encodeDo53(responseEncrypted)
            responseOffset = 0
            ProximityLogger.i(tag, "ENVELOPE: Response buffer size=${responseBuffer!!.size}, useExtendedLength=$useExtendedLength")

            // If oversize, responding with 61xx and waiting for GET RESPONSE
            return if (shouldUseGetResponse(responseBuffer!!)) {
                val sw = if (useExtendedLength) byteArrayOf(0x61, 0x00) else byteArrayOf(0x61, 0xFF.toByte())
                ProximityLogger.d(tag, "ENVELOPE: Response too large, sending SW=${Utils.bytesToHex(sw)}")
                sw
            } else {
                val out = ByteArray(responseBuffer!!.size + 2)
                System.arraycopy(responseBuffer!!, 0, out, 0, responseBuffer!!.size)
                out[out.size - 2] = 0x90.toByte()
                out[out.size - 1] = 0x00.toByte()
                ProximityLogger.d(tag, "ENVELOPE: Response fits, sending ${out.size} bytes directly")
                responseBuffer = null
                out
            }
        } else {
            // Data ok: all part together and responding 9000
            if (!envelopeCollecting) {
                ProximityLogger.d(tag, "ENVELOPE: Starting collection")
                envelopeCollecting = true
            }
            envelopeBuffer.write(dataField)
            ProximityLogger.d(tag, "ENVELOPE: Buffer now has ${envelopeBuffer.size()} bytes")
            // Waiting for final Envelope with Nc=0
            return NfcUtil.STATUS_WORD_OK
        }
    }


    // GET RESPONSE (INS=C0)
    fun handleGetResponse(apdu: ByteArray): ByteArray {
        ProximityLogger.i(tag, "GET RESPONSE - APDU size: ${apdu.size}")
        if (apdu.size < 4 || (apdu[1].toInt() and 0xff) != 0xC0) {
            ProximityLogger.e(tag, "GET RESPONSE: Invalid instruction")
            return NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED
        }
        if (((apdu[2].toInt() and 0xff) != 0x00) || ((apdu[3].toInt() and 0xff) != 0x00)) {
            ProximityLogger.e(tag, "GET RESPONSE: Wrong parameters")
            return NfcUtil.STATUS_WORD_WRONG_PARAMETERS
        }

        val le = parseLe(apdu)
        ProximityLogger.d(tag, "GET RESPONSE: Le=$le, useExtendedLength=$useExtendedLength")

        if (responseBuffer == null) {
            ProximityLogger.e(tag, "GET RESPONSE: No response buffer available")
            return NfcUtil.STATUS_WORD_OK
        }

        val remaining = responseBuffer!!.size - responseOffset
        ProximityLogger.d(tag, "GET RESPONSE: remaining=$remaining, responseOffset=$responseOffset, total buffer=${responseBuffer!!.size}")

        if (remaining <= 0) {
            ProximityLogger.i(tag, "GET RESPONSE: All data sent, clearing buffer")
            responseBuffer = null
            responseOffset = 0
            return NfcUtil.STATUS_WORD_OK
        }

        val unlimited = (le == 0 && useExtendedLength)
        val chunkSize = if (unlimited) remaining else remaining.coerceAtMost(le.coerceAtLeast(1))
        ProximityLogger.d(tag, "GET RESPONSE: Sending chunk of $chunkSize bytes (unlimited=$unlimited)")

        val chunk = responseBuffer!!.copyOfRange(responseOffset, responseOffset + chunkSize)
        responseOffset += chunkSize
        val more = responseBuffer!!.size - responseOffset

        return if (more > 0) {
            // Return chunk + status word indicating more data available
            val out = ByteArray(chunk.size + 2)
            System.arraycopy(chunk, 0, out, 0, chunk.size)
            if (useExtendedLength && more > 255) {
                out[out.size - 2] = 0x61.toByte()
                out[out.size - 1] = 0x00.toByte() // più di 255 bytes
                ProximityLogger.d(tag, "GET RESPONSE: More data available (>255 bytes), SW=6100")
            } else {
                out[out.size - 2] = 0x61.toByte()
                out[out.size - 1] = more.toByte()
                ProximityLogger.d(tag, "GET RESPONSE: More data available ($more bytes), SW=61${String.format("%02X", more)}")
            }
            out
        } else {
            val out = ByteArray(chunk.size + 2)
            System.arraycopy(chunk, 0, out, 0, chunk.size)
            out[out.size - 2] = 0x90.toByte()
            out[out.size - 1] = 0x00.toByte()
            ProximityLogger.i(tag, "GET RESPONSE: Final chunk sent (${chunk.size} bytes), SW=9000")
            responseBuffer = null
            responseOffset = 0
            out
        }
    }

    // ——— Helpers ———
    private fun parseLcAndDataField(apdu: ByteArray): Pair<Int, ByteArray> {
        if (apdu.size < 5) return 0 to ByteArray(0)
        val lc1 = apdu[4].toInt() and 0xff
        return if (lc1 == 0) {
            if (apdu.size < 7) return 0 to ByteArray(0)
            val lc = ((apdu[5].toInt() and 0xff) shl 8) or (apdu[6].toInt() and 0xff)
            useExtendedLength = true
            val start = 7
            val end = start + lc
            if (end > apdu.size) 0 to ByteArray(0) else lc to apdu.copyOfRange(start, end)
        } else {
            useExtendedLength = false
            val start = 5
            val end = start + lc1
            if (end > apdu.size) 0 to ByteArray(0) else lc1 to apdu.copyOfRange(start, end)
        }
    }

    private fun parseLe(apdu: ByteArray): Int {
        // Case GET RESPONSE: Le present
        // If extended and Le=00 -> unlimited
        // Short Le 1 byte; Extended Le 2 bytes;
        return if (apdu.size >= 7 && apdu[4].toInt() == 0) {
            ((apdu[5].toInt() and 0xff) shl 8) or (apdu[6].toInt() and 0xff)
        } else if (apdu.size >= 5) {
            apdu[4].toInt() and 0xff
        } else {
            0
        }
    }

    private fun parseDo53(tlv: ByteArray): ByteArray {
        var idx = 0
        if ((tlv[idx].toInt() and 0xff) != 0x53) throw IllegalArgumentException("Missing tag 0x53")
        idx++
        val (len, lenLen) = parseBerLength(tlv, idx)
        idx += lenLen
        if (idx + len > tlv.size) throw IllegalArgumentException("Length overflow")
        return tlv.copyOfRange(idx, idx + len)
    }

    private fun encodeDo53(value: ByteArray): ByteArray {
        val length = value.size
        val lenEnc = encodeBerLength(length)
        val out = ByteArray(1 + lenEnc.size + length)
        out[0] = 0x53.toByte()
        System.arraycopy(lenEnc, 0, out, 1, lenEnc.size)
        System.arraycopy(value, 0, out, 1 + lenEnc.size, length)
        return out
    }

    private fun parseBerLength(buf: ByteArray, offset: Int): Pair<Int, Int> {
        val b = buf[offset].toInt() and 0xff
        if (b < 0x80) return b to 1
        val num = b and 0x7f
        var len = 0
        for (i in 0 until num) {
            len = (len shl 8) or (buf[offset + 1 + i].toInt() and 0xff)
        }
        return len to (1 + num)
    }

    private fun encodeBerLength(len: Int): ByteArray {
        return when {
            len < 0x80 -> byteArrayOf(len.toByte())
            len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
            else -> byteArrayOf(
                0x82.toByte(),
                ((len shr 8) and 0xFF).toByte(),
                (len and 0xFF).toByte()
            )
        }
    }

    private fun shouldUseGetResponse(resp: ByteArray) = resp.size > 255 || useExtendedLength

    @OptIn(ExperimentalEncodingApi::class)
    private fun processReaderRequest(
        requestCborEncrypted: ByteArray
    ): ByteArray {
        val deviceEngagementBytes = engagement.nfcEngagementHelper.deviceEngagement
        val handoverBytes = engagement.nfcEngagementHelper.handover
        val eReaderKey = EngagementParser(deviceEngagementBytes).parse().eSenderKey
        val encodedEReaderKey = Cbor.encode(eReaderKey.toDataItem())

        val sessionTranscript = Cbor.encode(
            CborArray.builder()
                .add(Tagged(24, Bstr(deviceEngagementBytes)))
                .add(Tagged(24, Bstr(encodedEReaderKey)))
                .add(RawCbor(handoverBytes))
                .end()
                .build()
        )
        val listRequested: List<DeviceRequestParser.DocRequest> = DeviceRequestParser(
            requestCborEncrypted,
            sessionTranscript
        ).parse().docRequests
        //DOING B64 calculation only if we are in debug
        if (ProximityLogger.enabled) {
            val b64 = Base64.encode(requestCborEncrypted)
            ProximityLogger.i("DEVICE REQUEST", b64)
            val b64Session = Base64.encode(sessionTranscript)
            ProximityLogger.i("SESSION TRANSCRIPT", b64Session)
        }

        val requestWrapperList = arrayListOf<JSONObject?>()
        listRequested.forEach { each ->
            (each toReaderAuthWith engagement.readerTrustStores).let {
                requestWrapperList.add(
                    RequestWrapper(
                        each.itemsRequest,
                        it?.isSuccess() == true
                    ).prepare().toJson()
                )
            }
        }
        val jsonToSend = requestWrapperList.toTypedArray().toRequest()
        val disclosedDocuments = ArrayList<Document>()
        jsonToSend.keys().forEach {
            when {
                DocType(it) == DocType.MDL -> disclosedDocuments.add(docs.first { doc -> doc.docType == MDL_DOCTYPE })
                DocType(it) == DocType.EU_PID -> disclosedDocuments.add(docs.first { doc -> doc.docType == EU_PID_DOCTYPE })
            }
        }

        val docRequested = disclosedDocuments.mapNotNull {
            it.issuerSigned?.rawValue?.let { doc ->
                DocRequested(
                    issuerSignedContent =
                        doc,
                    alias = alias,
                    docType = it.docType!!
                )
            }
        }
        val (resp, itsOk) = ResponseGenerator(
            sessionsTranscript = sessionTranscript
        ).createResponse(
            documents = docRequested.toTypedArray(),
            fieldRequestedAndAccepted = jsonToSend.toString()
        )
        return if (itsOk == "created") resp!! else NfcUtil.STATUS_WORD_FILE_NOT_FOUND
    }
}
