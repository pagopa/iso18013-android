package it.pagopa.iso_android.nfc

import it.pagopa.io.wallet.cbor.document_manager.DocManager
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.nfc.NfcEngagementService
import it.pagopa.io.wallet.proximity.nfc.NfcRetrievalMethod
import it.pagopa.iso_android.R

/**
 * NFC Exchange Service for NFC-only data transfer (no Bluetooth handover).
 *
 * This service handles direct NFC data transfer using mDL APDU commands:
 * - SELECT AID (A0000002480400)
 * - ENVELOPE (device request)
 * - GET RESPONSE (device response chunks)
 *
 * For Bluetooth handover, use AppNfcEngagementService instead.
 */
class AppNfcExchangeService : NfcEngagementService() {
    override val docs: Array<Document> by lazy {
        DocManager.getInstance(
            context = baseContext,
            storageDirectory = baseContext.noBackupFilesDir,
            prefix = "SECURE_STORAGE",
            alias = "SECURE_STORAGE_KEY_${baseContext.noBackupFilesDir}"
        ).gelAllDocuments().toTypedArray()
    }
    override val alias: String by lazy { "SECURE_STORAGE_KEY_${baseContext.noBackupFilesDir}"}

    // NFC-only mode: data transfer directly via NFC without Bluetooth handover
    override val useBluetooth: Boolean = false

    override val readerTrustStore: List<List<Any>> by lazy {
        listOf(listOf(R.raw.eudi_pid_issuer_ut))
    }
    override val retrievalMethods: List<NfcRetrievalMethod> = listOf(
        NfcRetrievalMethod(
            commandDataFieldMaxLength = 256L,
            responseDataFieldMaxLength = 256L,
            useBluetooth = useBluetooth
        )
    )
}