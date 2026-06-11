package com.rgbledsync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "rgb_led_sync_capture"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val CAPTURE_WIDTH = 320
        private const val CAPTURE_HEIGHT = 180
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null

    private lateinit var transmitter: IrController
    private var lastSentCode: Long = -1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RGB LED Sync")
            .setContentText("Starting...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, startNotification)

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        val data = intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)

        if (resultCode == -1 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val mpm = if (Build.VERSION.SDK_INT >= 33) {
                getSystemService(MediaProjectionManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            }

            mediaProjection = mpm.getMediaProjection(resultCode, data)

            val density = resources.displayMetrics.densityDpi

            imageReader = ImageReader.newInstance(CAPTURE_WIDTH, CAPTURE_HEIGHT, PixelFormat.RGBA_8888, 2)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                CAPTURE_WIDTH, CAPTURE_HEIGHT, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            transmitter = IrController.create(this)
            startCaptureLoop()

            updateNotification("Running")
        } catch (e: Exception) {
            updateNotification("Error: ${e.message}")
            stopSelf()
        }

        return START_STICKY
    }

    private fun startCaptureLoop() {
        captureJob = scope.launch {
            delay(500)
            while (isActive) {
                try {
                    captureAndProcess()
                } catch (_: Exception) {
                }
                delay(1000)
            }
        }
    }

    private fun captureAndProcess() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return

        try {
            val bitmap = bitmapFromImage(image) ?: return
            val avgColor = ColorAnalyzer.analyzeBitmap(bitmap)
            val matched = ColorMapping.findClosestColor(avgColor)

            if (matched.necCode != lastSentCode) {
                transmitter.transmit(matched.necCode)
                lastSentCode = matched.necCode
                updateNotification("Matched: ${matched.name}")
            }
        } finally {
            image.close()
        }
    }

    private fun bitmapFromImage(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null

        val plane = planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        buffer.rewind()
        if (width <= 0 || height <= 0) return null

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        if (pixelStride == 4 && rowStride == width * 4) {
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }

        val tempRow = ByteArray(rowStride.coerceAtMost(4096))
        for (row in 0 until height) {
            buffer.position(row * rowStride)
            val readLen = minOf(rowStride, tempRow.size)
            buffer.get(tempRow, 0, readLen)

            for (col in 0 until width) {
                val idx = col * pixelStride
                if (idx + 3 >= readLen) break
                val r = tempRow[idx].toInt() and 0xFF
                val g = tempRow[idx + 1].toInt() and 0xFF
                val b = tempRow[idx + 2].toInt() and 0xFF
                val a = if (pixelStride >= 4) tempRow[idx + 3].toInt() and 0xFF else 0xFF
                bitmap.setPixel(col, row, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }

        return bitmap
    }

    private fun updateNotification(text: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RGB LED Sync")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build()

            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        captureJob?.cancel()
        scope.cancel()
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { if (::transmitter.isInitialized) transmitter.release() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
