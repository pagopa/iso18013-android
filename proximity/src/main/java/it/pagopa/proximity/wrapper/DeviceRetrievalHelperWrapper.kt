package it.pagopa.proximity.wrapper

import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import it.pagopa.proximity.retrieval.stopPresentation

class DeviceRetrievalHelperWrapper(
    private val deviceRetrievalHelper: DeviceRetrievalHelper
) {
    fun sendResponse(
        deviceResponseBytes: ByteArray?,
        status: Long
    ) {
        deviceRetrievalHelper.sendDeviceResponse(
            deviceResponseBytes,
            status
        )
    }

    fun disconnect() {
        deviceRetrievalHelper.stopPresentation(true, true)
    }

    fun sessionTranscript() = this.deviceRetrievalHelper.sessionTranscript
}