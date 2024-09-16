package it.pagopa.proximity.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import it.pagopa.proximity.ProximityLogger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothConnection(
    private val context: Context,
    private val deviceAddress: String
) {
    private val tag = this.javaClass.name
    private val bluetoothManager = context
        .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    fun connect() {
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            ProximityLogger.e(tag, "Device doesn't support Bluetooth")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            // Bluetooth is not enabled
            ProximityLogger.e(tag, "Bluetooth is not enabled")
            return
        }
        // return if device is not found
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress) ?: run {
            ProximityLogger.e(tag, "Device not found")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ProximityLogger.e(tag, "Bluetooth Permission not granted")
            return
        }
        try {
            // Create a BluetoothSocket
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
            // Connect to the device
            bluetoothSocket?.connect()
            // Connection successful
            if (bluetoothSocket?.isConnected == true) {
                // Get input and output streams
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
            }
        } catch (e: IOException) {
            // Connection failed
            try {
                bluetoothSocket?.close()
            } catch (e2: IOException) {
                // Error closing socket
            }
        }
    }

    fun sendData(data: ByteArray) {
        try {
            outputStream?.write(data)
        } catch (e: IOException) {
            ProximityLogger.e(tag, "Exception while sending data: $e")
        }
    }

    fun receiveData(): ByteArray? {
        try {
            val buffer = ByteArray(1024) // Adjust buffer size as needed
            val bytesRead = inputStream?.read(buffer) ?: 0
            if (bytesRead > 0) {
                return buffer.copyOfRange(0, bytesRead)
            }
        } catch (e: IOException) {
            ProximityLogger.e(tag, "Exception while receiving data: $e")
        }
        return null
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            // Error closing socket
        }
    }

    companion object {
        private val MY_UUID = UUID.randomUUID()
    }
}