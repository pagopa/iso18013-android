package it.pagopa.iso_android.qr_code

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.CharacterSetECI
import com.google.zxing.qrcode.QRCodeWriter
import it.pagopa.proximity.ProximityLogger
import java.util.EnumMap

/**
 * Wrapper for a QR code.
 * @property content the content of the QR code
 * @constructor Creates a QR code with the given [content]
 */
data class QrCode(val content: String) {
    /**
     * Returns the QR code as a [Bitmap] with the given [size].
     */
    fun asBitmap(size: Int): Bitmap = getQRCodeAsBitmap(content, size)

    /**
     * Creates a QR code as [Bitmap]
     * @param content is the required content of the QR code as string
     * @param size is the required size of the QR code as pixels
     * @return returns a [Bitmap] of the QR code
     */
    private fun getQRCodeAsBitmap(content: String, size: Int): Bitmap {
        ProximityLogger.i("Qrcode content", content)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
            hints[EncodeHintType.CHARACTER_SET] = CharacterSetECI.UTF8
            val bitMapMatrix =
                QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            for (i in 0 until size) {
                for (j in 0 until size) {
                    bitmap.setPixel(
                        i,
                        j,
                        if (bitMapMatrix[i, j]) Color.BLACK else Color.WHITE,
                    )
                }
            }
        } catch (e: WriterException) {
            ProximityLogger.e(this.javaClass.name, "Error creating QR code: $e")
        }
        return bitmap
    }
}