package it.pagopa.io.wallet.proximity.nfc

import com.android.identity.util.Constants
import it.pagopa.io.wallet.proximity.ProximityLogger
import it.pagopa.io.wallet.proximity.session_data.SessionDataStatus
import it.pagopa.io.wallet.proximity.wrapper.DeviceRetrievalHelperWrapper

/**
 * Use this method to send a generic error message
 */
fun sendErrorResponseByNfc(
    deviceRetrievalHelper: DeviceRetrievalHelperWrapper?,
    sessionDataStatus: SessionDataStatus
) {
    if (deviceRetrievalHelper == null) return
    deviceRetrievalHelper.sendResponse(
        null,
        sessionDataStatus.value
    )
}
/**
 * Use this method to send a good retrieved response
 * it does nothing if [DeviceRetrievalHelperWrapper] was lost
 */
fun sendResponseByNfc(deviceRetrievalHelper: DeviceRetrievalHelperWrapper?, response: ByteArray) {
    ProximityLogger.i("RESPONSE", "deviceRetrievalHelper:$deviceRetrievalHelper")
    if (deviceRetrievalHelper == null) return
    ProximityLogger.i("RESPONSE", "SENDING")
    deviceRetrievalHelper.sendResponse(
        response,
        Constants.SESSION_DATA_STATUS_SESSION_TERMINATION
    )
}