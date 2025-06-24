package it.pagopa.io.wallet.proximity.retrieval

import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc
import com.android.identity.util.UUID
import it.pagopa.io.wallet.proximity.bluetooth.BleRetrievalMethod
import it.pagopa.io.wallet.proximity.nfc.NfcRetrievalMethod

internal val DeviceRetrievalMethod.connectionMethod: List<ConnectionMethod>
    get() = when (this) {
        is BleRetrievalMethod -> {
            if (!peripheralServerMode && !centralClientMode) emptyList()
            else {
                mutableListOf<ConnectionMethod>().apply {
                    val randomUUID = UUID.randomUUID()
                    add(
                        ConnectionMethodBle(
                            peripheralServerMode,
                            centralClientMode,
                            if (peripheralServerMode) randomUUID else null,
                            if (centralClientMode) randomUUID else null
                        )
                    )
                }
            }
        }

        is NfcRetrievalMethod -> mutableListOf<ConnectionMethod>().apply {
            if (this@connectionMethod.useBluetooth) {
                val randomUUID = UUID.randomUUID()
                add(
                    ConnectionMethodBle(
                        true,
                        false,
                        randomUUID,
                        null
                    )
                )
            } else {
                add(
                    ConnectionMethodNfc(
                        commandDataFieldMaxLength,
                        responseDataFieldMaxLength
                    )
                )
            }
        }

        else -> throw IllegalArgumentException("Unsupported connection method")
    }

internal val List<DeviceRetrievalMethod>.transportOptions: DataTransportOptions
    get() = DataTransportOptions.Builder().apply {
        for (m in this@transportOptions) {
            if (m is BleRetrievalMethod)
                setBleClearCache(m.clearBleCache)
            else if (m is NfcRetrievalMethod) {
                if (m.useBluetooth)
                    setBleClearCache(true)
            }
        }
    }.build()

internal val List<DeviceRetrievalMethod>.connectionMethods: List<ConnectionMethod>
    get() = flatMap { it.connectionMethod }
