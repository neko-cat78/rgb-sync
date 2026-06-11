package com.rgbledsync

import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var colorNameText: TextView
    private lateinit var colorPreview: TextView
    private lateinit var actionButton: Button

    private var isCapturing = false
    private lateinit var irController: IrController

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startCaptureService(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        colorNameText = findViewById(R.id.colorNameText)
        colorPreview = findViewById(R.id.colorPreview)
        actionButton = findViewById(R.id.actionButton)

        irController = IrController.create(this)
        actionButton.setOnClickListener { toggleCapture() }

        setupForceSendButtons()
    }

    private fun toggleCapture() {
        if (isCapturing) {
            stopCaptureService()
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpm.createScreenCaptureIntent()
        captureLauncher.launch(intent)
    }

    private fun startCaptureService(resultCode: Int, data: Intent) {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }
        ContextCompat.startForegroundService(this, intent)
        isCapturing = true
        actionButton.text = "Stop Sync"
        statusText.text = "Status: Running"
    }

    private fun stopCaptureService() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        isCapturing = false
        actionButton.text = "Start Sync"
        statusText.text = "Status: Stopped"
        colorNameText.text = ""
        colorPreview.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun setupForceSendButtons() {
        val colorButtons = listOf(
            R.id.btnOrange to ColorMapping.colors[0],
            R.id.btnLightGreen to ColorMapping.colors[1],
            R.id.btnDarkBlue to ColorMapping.colors[2],
            R.id.btnDeepYellow to ColorMapping.colors[3],
            R.id.btnCyan to ColorMapping.colors[4],
            R.id.btnPurple to ColorMapping.colors[5],
            R.id.btnLightYellow to ColorMapping.colors[6],
            R.id.btnTeal to ColorMapping.colors[7],
            R.id.btnMagenta to ColorMapping.colors[8],
        )

        val modeButtons = listOf(
            R.id.btnFlash to ColorMapping.modes[0],
            R.id.btnStrobe to ColorMapping.modes[1],
            R.id.btnFade to ColorMapping.modes[2],
        )

        for ((id, entry) in colorButtons) {
            findViewById<Button>(id).apply {
                setOnClickListener {
                    irController.transmit(entry.necCode)
                    colorNameText.text = "Sent: ${entry.name}"
                    colorPreview.setBackgroundColor(entry.rgb)
                }
            }
        }

        for ((id, entry) in modeButtons) {
            findViewById<Button>(id).apply {
                setOnClickListener {
                    irController.transmit(entry.necCode)
                    colorNameText.text = "Sent: ${entry.name}"
                }
            }
        }
    }
}
