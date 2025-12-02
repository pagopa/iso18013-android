package it.pagopa.iso_android.ui.view_model

import android.content.res.Resources
import androidx.lifecycle.viewModelScope
import it.pagopa.io.wallet.cbor.document_manager.DocManager
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.engagement.Engagement
import it.pagopa.io.wallet.proximity.nfc.NfcEngagementEvent
import it.pagopa.io.wallet.proximity.nfc.NfcEngagementEventBus
import it.pagopa.iso_android.R
import it.pagopa.iso_android.ui.AppDialog
import kotlinx.coroutines.launch

class NfcEngagementViewModel(
    private val docManager: DocManager,
    private val resources: Resources
) : BaseEngagementViewModel(resources) {
    override val engagement: Engagement? = null
    override fun getEuPid() = docManager.gelAllEuPidDocuments().firstOrNull()
    override fun getMdl() = docManager.gelAllMdlDocuments().firstOrNull()

    fun observeEvents() {
        viewModelScope.launch {
            NfcEngagementEventBus.events.collect { event ->
                when (event) {
                    is NfcEngagementEvent.Connecting -> loader.value = "Connecting..."
                    is NfcEngagementEvent.Connected -> {
                        loader.value = "Connected"
                        deviceConnected = event.device
                    }

                    is NfcEngagementEvent.Error -> {
                        ProximityLogger.e(
                            this@NfcEngagementViewModel.javaClass.name,
                            "onCommunicationError: ${event.error.message}"
                        )
                        loader.value = null
                    }

                    is NfcEngagementEvent.DocumentRequestReceived -> {
                        val request = event.request
                        event.sessionTranscript
                        ProximityLogger.i("request", request.toString())
                        this@NfcEngagementViewModel.request = request.orEmpty()
                        manageRequestFromDeviceUi(event.sessionTranscript)
                    }

                    is NfcEngagementEvent.Disconnected -> {
                        ProximityLogger.i(
                            this@NfcEngagementViewModel.javaClass.name,
                            "onDeviceDisconnected"
                        )
                        this@NfcEngagementViewModel.loader.value = null
                        if (event.transportSpecificTermination) {
                            this@NfcEngagementViewModel.dialog.value = AppDialog(
                                title = resources.getString(R.string.data),
                                description = resources.getString(R.string.sent),
                                button = AppDialog.DialogButton(
                                    "${resources.getString(R.string.perfect)}!!",
                                    onClick = {
                                        dialog.value = null
                                        _shouldGoBack.value = true
                                    }
                                )
                            )
                            this@NfcEngagementViewModel.loader.value = null
                        }
                    }
                }
            }
        }
    }
}