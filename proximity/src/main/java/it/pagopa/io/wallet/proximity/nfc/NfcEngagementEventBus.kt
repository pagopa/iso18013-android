package it.pagopa.io.wallet.proximity.nfc

import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.crypto.EcPrivateKey
import it.pagopa.io.wallet.cbor.model.Document
import it.pagopa.io.wallet.proximity.retrieval.DeviceRetrievalMethod
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**Singleton event bus using SharedFlow for NFC notifications.  */
object NfcEngagementEventBus {
    // No replay; only current collectors receive events.
    private val _events = MutableSharedFlow<NfcEngagementEvent>(
        replay = 0,
        extraBufferCapacity = 1 // Optional: prevents lost fast events
    )
    val events = _events.asSharedFlow()
    private val _internalEvent = MutableStateFlow<ServiceEvents?>(
        null
    )
    internal val internalEvent = _internalEvent.asSharedFlow()

    // Use outside coroutines (e.g., in listeners); non-suspending.
    internal fun tryEmit(event: NfcEngagementEvent) {
        _events.tryEmit(event)
    }

    /**It emits the event for the [NfcEngagementService] to setup engagement
     * @return true-> if event is sent correctly*/
    fun setupNfcService(
        retrievalMethods: List<DeviceRetrievalMethod>,
        documents: List<Document>?=null,
        alias: String?=null,
        readerTrustStore: List<List<Any>>?=null
    ): Boolean {
        return _internalEvent.tryEmit(
            ServiceEvents.SetupReady(
                retrievalMethods,
                documents,
                alias,
                readerTrustStore
            )
        )
    }

    /**
     * It emits the event for the [NfcEngagementService] to setup engagement
     * @return true-> if event is sent correctly*/
    internal fun setupDeviceEngagementFromQr(deviceEngagementSetup: Pair<ByteArray, EcPrivateKey>) {
        _internalEvent.tryEmit(ServiceEvents.QrCodeDeviceEngagement(deviceEngagementSetup))
    }
}