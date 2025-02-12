package it.pagopa.io.wallet.proximity.retrieval

import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.util.Constants
import it.pagopa.io.wallet.proximity.ProximityLogger

internal fun DeviceRetrievalHelper.stopPresentation(
    sendSessionTerminationMessage: Boolean,
    useTransportSpecificSessionTermination: Boolean,
) {
    try {
        if (sendSessionTerminationMessage) {
            if (useTransportSpecificSessionTermination) {
                sendTransportSpecificTermination()
            } else {
                sendDeviceResponse(
                    null,
                    Constants.SESSION_DATA_STATUS_SESSION_TERMINATION,
                )
            }
        }
        disconnect()
    } catch (e: Exception) {
        ProximityLogger.e(this.javaClass.name, "Error ignored. $e")
    }
}