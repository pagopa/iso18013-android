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
    resources: Resources
) : BaseEngagementViewModel(resources) {
    override val engagement: Engagement? = null
    override fun getEuPid() = docManager.gelAllEuPidDocuments().firstOrNull()
    override fun getMdl() = docManager.gelAllMdlDocuments().firstOrNull()
}