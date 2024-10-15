package it.pagopa.proximity.wrapper

import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.util.Constants
import it.pagopa.proximity.retrieval.stopPresentation

class DeviceRetrievalHelperWrapper(
    private val deviceRetrievalHelper: DeviceRetrievalHelper
) {
    fun sendResponse(
        deviceResponseBytes: ByteArray?
    ) {
        deviceRetrievalHelper.sendDeviceResponse(
            deviceResponseBytes,
            Constants.DEVICE_RESPONSE_STATUS_OK
        )
    }

    fun disconnect() {
        deviceRetrievalHelper.stopPresentation(true, true)
    }

    fun sessionTranscript() = this.deviceRetrievalHelper.sessionTranscript
}