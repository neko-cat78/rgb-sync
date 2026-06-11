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

        const val ACTION_STOP = "com.rgbledsync.STOP_CAPTURE"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null

    private lateinit var transmitter: IrController
    private var lastSentCode: Long = -1
    private var lastColorName: String = ""
    private var lastColorRgb: Int = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val startNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RGB LED Sync")
            .setContentText("Capturing screen...")
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

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        transmitter = IrController.create(this)
        startCaptureLoop()

        return START_STICKY
    }

    private fun startCaptureLoop() {
        captureJob = scope.launch {
            while (isActive) {
                captureAndProcess()
                delay(1000)
            }
        }
    }

    private fun captureAndProcess() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return

        try {
            val bitmap = bitmapFromImage(image)
            if (bitmap == null) return

            val avgColor = ColorAnalyzer.analyzeBitmap(bitmap)
            val matched = ColorMapping.findClosestColor(avgColor)

            if (matched.necCode != lastSentCode) {
                transmitter.transmit(matched.necCode)
                lastSentCode = matched.necCode
                lastColorName = matched.name
                lastColorRgb = matched.rgb

                updateNotification(matched.name)
            }
        } catch (_: Exception) {
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

        val pixels = IntArray(width * height)
        val rowBytes = ByteArray(rowStride)

        for (row in 0 until height) {
            buffer.position(row * rowStride)
            buffer.get(rowBytes, 0, rowStride)

            for (col in 0 until width) {
                val idx = col * pixelStride
                val r = rowBytes[idx].toInt() and 0xFF
                val g = rowBytes[idx + 1].toInt() and 0xFF
                val b = rowBytes[idx + 2].toInt() and 0xFF
                val a = if (pixelStride >= 4) rowBytes[idx + 3].toInt() and 0xFF else 0xFF
                pixels[row * width + col] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun updateNotification(colorName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RGB LED Sync")
            .setContentText("Matched: $colorName")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        captureJob?.cancel()
        scope.cancel()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        transmitter.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
