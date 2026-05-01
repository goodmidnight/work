package io.goodmidnight.transfer.core.qr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A utility class responsible for generating QR code Bitmaps from connection metadata.
 * It serializes network credentials (SSID, Password, IP) into a JSON string and encodes it.
 */
@Singleton
class QrGenerator @Inject constructor() {

    /**
     * Generates a square Bitmap containing the QR code representation of the hotspot credentials.
     *
     * @param ssid The Service Set Identifier (Network Name) of the hotspot.
     * @param pw The WPA2 passphrase of the hotspot.
     * @param ip The local gateway IP address where the host is listening for connections.
     * @param size The width and height of the generated Bitmap in pixels (default: 512).
     * @return A valid Bitmap if encoding succeeds, or null if an error occurs.
     */
    fun generate(ssid: String, pw: String, ip: String, size: Int = 512): Bitmap? {
        return runCatching {
            val jsonString = JSONObject().apply {
                put("ssid", ssid)
                put("pw", pw)
                put("ip", ip)
            }.toString()

            val bitMatrix = QRCodeWriter().encode(jsonString, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height

            // RGB_565 consumes half the memory of ARGB_8888 and is perfectly fine for black-and-white QR codes.
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    val isBlack = bitMatrix.get(x, y)
                    bitmap[x, y] = if (isBlack) Color.BLACK else Color.WHITE
                }
            }
            bitmap
        }.onFailure { e ->
            Log.e("QrGenerator", "Failed to generate QR code bitmap", e)
        }.getOrNull()
    }
}