package io.goodmidnight.transfer.core.qr

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A highly optimized ImageAnalysis.Analyzer for scanning QR codes using the device camera.
 * Employs frame throttling and state tracking to prevent CPU overload and excessive battery drain.
 */
class QrAnalyzer(
    private val onQrScanned: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader()

    // Thread-safe flag to ensure the callback is only triggered once per successful scan.
    private val isScanned = AtomicBoolean(false)

    // Throttle timestamp to prevent analyzing 60 frames per second.
    private var lastAnalyzedTimestamp = 0L

    companion object {
        // Only process 2 frames per second to save battery and CPU.
        private const val ANALYSIS_INTERVAL_MS = 500L
    }

    override fun analyze(image: ImageProxy) {
        if (isScanned.get()) {
            image.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalyzedTimestamp < ANALYSIS_INTERVAL_MS) {
            image.close()
            return
        }

        lastAnalyzedTimestamp = currentTime

        if (image.format == ImageFormat.YUV_420_888 || image.format == ImageFormat.YV12) {
            val buffer = image.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val source = PlanarYUVLuminanceSource(
                data,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decode(binaryBitmap)
                if (isScanned.compareAndSet(false, true)) {
                    onQrScanned(result.text)
                }
            } catch (e: NotFoundException) {
                Log.v("QrAnalyzer", "No QR code found in current frame", e)
            } catch (e: Exception) {
                Log.e("QrAnalyzer", "Unexpected error during QR decoding", e)
            } finally {
                image.close()
            }
        } else {
            Log.w("QrAnalyzer", "Unsupported image format: ${image.format}. Expecting YUV_420_888.")
            image.close()
        }
    }

    /**
     * Resets the analyzer state, allowing it to scan a new QR code.
     * Call this method when re-entering the scanner screen.
     */
    fun reset() {
        isScanned.set(false)
    }
}