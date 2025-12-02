package it.pagopa.io.wallet.proximity.nfc

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**Singleton event bus using SharedFlow for NFC notifications.  */
object NfcEngagementEventBus {
    // No replay; only current collectors receive events.
    private val _events = MutableSharedFlow<NfcEngagementEvent>(
        replay = 0,
        extraBufferCapacity = 1 // Optional: prevents lost fast events
    )
    val events = _events.asSharedFlow()

    // Use outside coroutines (e.g., in listeners); non-suspending.
    fun tryEmit(event: NfcEngagementEvent) {
        _events.tryEmit(event)
    }

    var bluetoothOn = true
}