package it.pagopa.io.wallet.proximity.nfc

import android.app.Activity
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.android.identity.android.util.NfcUtil
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.nfc.apdu.ApduManager

abstract class NfcExchangeService : HostApduService() {
    abstract val javaClassToLaunch: Class<out Activity>
    open val docs: Array<Document> = arrayOf()
    private var manager: ApduManager? = null
    open val alias: String = ""
    private val nfcEngagement: NfcEngagement by lazy {
        NfcEngagement.build(this.baseContext, listOf(NfcRetrievalMethod())).configure()
    }
    open val readerTrustStore: List<List<Any>> = listOf()

    @Suppress("UNCHECKED_CAST")
    override fun onCreate() {
        ProximityLogger.i(this.javaClass.name, "onCreate")
        super.onCreate()
    }

    override fun onDeactivated(reason: Int) {
        nfcEngagement.nfcEngagementHelper.nfcOnDeactivated(reason)
    }

    private fun ByteArray.processApdu() = when (NfcUtil.nfcGetCommandType(this)) {
        NfcUtil.COMMAND_TYPE_SELECT_BY_AID -> {
            manager?.let {
                val selectedAid = this.copyOfRange(5, 12)
                if (selectedAid.contentEquals(NfcUtil.AID_FOR_MDL_DATA_TRANSFER)) {
                    ProximityLogger.i("SELECTED", "AID_FOR_MDL_DATA_TRANSFER")
                    //MDL AID detected, using ApduManager for NFC-only
                    it.resetApduDataRetrievalState()
                    NfcUtil.STATUS_WORD_OK
                } else {
                    //Unknown AID, rejecting
                    NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED
                }
            } ?: run {
                //Manager null in NFC-only mode - this shouldn't happen
                NfcUtil.STATUS_WORD_FILE_NOT_FOUND
            }
        }

        NfcUtil.COMMAND_TYPE_ENVELOPE -> manager?.handleEnvelope(this)
            ?: NfcUtil.STATUS_WORD_FILE_NOT_FOUND

        NfcUtil.COMMAND_TYPE_RESPONSE -> manager?.handleGetResponse(this)
            ?: NfcUtil.STATUS_WORD_FILE_NOT_FOUND

        else -> NfcUtil.STATUS_WORD_INSTRUCTION_NOT_SUPPORTED
    }

    /**
     * Processes incoming NFC APDU commands.
     */
    override fun processCommandApdu(
        commandApdu: ByteArray, extras: Bundle?
    ): ByteArray? {
        if (manager == null)
            manager = ApduManager(nfcEngagement, docs, alias)
        ProximityLogger.i(
            "COMMAND_TYPE_EXCHANGE",
            NfcUtil.nfcGetCommandType(commandApdu).toString()
        )
        return commandApdu.processApdu()
    }
}