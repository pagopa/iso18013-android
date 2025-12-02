package it.pagopa.io.wallet.proximity.retrieval

import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.connectionmethod.ConnectionMethodNfc
import com.android.identity.util.UUID
import it.pagopa.io.wallet.proximity.bluetooth.BleRetrievalMethod
import it.pagopa.io.wallet.proximity.nfc.NfcRetrievalMethod

// Genera un UUID BLE una sola volta per la sessione
private val bleSessionUuid: UUID by lazy { UUID.randomUUID() }

internal val DeviceRetrievalMethod.connectionMethod: List<ConnectionMethod>
    get() = when (this) {
        is BleRetrievalMethod -> {
            if (!peripheralServerMode && !centralClientMode) emptyList()
            else {
                mutableListOf<ConnectionMethod>().apply {
                    add(
                        ConnectionMethodBle(
                            peripheralServerMode,
                            centralClientMode,
                            if (peripheralServerMode) bleSessionUuid else null,
                            if (centralClientMode) bleSessionUuid else null
                        )
                    )
                }
            }
        }

        is NfcRetrievalMethod -> mutableListOf<ConnectionMethod>().apply {
            if (this@connectionMethod.useBluetooth) {
                // Solo peripheral server mode per NFC-based BLE handover
                // Usa lo stesso UUID della sessione
                add(
                    ConnectionMethodBle(
                        supportsPeripheralServerMode = true,
                        supportsCentralClientMode = false,
                        peripheralServerModeUuid = bleSessionUuid,
                        centralClientModeUuid = null  // Deve essere null se non supporta central client
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
    get() {
        val methods = flatMap { it.connectionMethod }
        // Restituisci solo metodi unici combinando quelli simili
        // Questo previene la creazione di più BLE advertiser con lo stesso UUID
        return ConnectionMethod.combine(methods)
    }
