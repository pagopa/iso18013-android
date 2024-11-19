package it.pagopa.proximity.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context

object BluetoothUtils {
    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        return bluetoothAdapter?.isEnabled == true
    }
}