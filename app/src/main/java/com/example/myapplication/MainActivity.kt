package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var mqttClient: MqttClient
    private lateinit var imageCapture: ImageCapture
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    private val mqttBroker = "tcp://broker.hivemq.com:1883"
    private val mqttTopic = "android/camera/${System.currentTimeMillis()}"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        setupMqtt()

        if (hasCameraPermission()) startCamera()
        else requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { takePhoto() }
        }
    }

    private fun setupMqtt() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                mqttClient = MqttClient(mqttBroker, "AndroidClient-${System.currentTimeMillis()}")
                val options = MqttConnectOptions().apply {
                    isCleanSession = true  // Fixed using proper accessor
                    connectionTimeout = 3
                    keepAliveInterval = 60
                }

                mqttClient.connect(options)

                withContext(Dispatchers.Main) {
                    val status = if (mqttClient.isConnected) "MQTT Connected!" else "Connection failed"
                    Toast.makeText(this@MainActivity, status, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("MQTT", "Connection error: ${e.stackTraceToString()}")
            }
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val previewView = findViewById<PreviewView>(R.id.previewView)

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("Camera", "Setup failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val file = File(filesDir, "photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "Saved: ${file.path}", Toast.LENGTH_LONG).show()
                    sendMqttMessage("Photo saved: ${file.name}")
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Camera", "Capture failed: ${exc.message}")
                }
            }
        )
    }

    private fun sendMqttMessage(message: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (::mqttClient.isInitialized && mqttClient.isConnected) {
                    mqttClient.publish(mqttTopic, message.toByteArray(), 1, false)
                    Log.d("MQTT", "Message sent: $message")
                }
            } catch (e: Exception) {
                Log.e("MQTT", "Send failed: ${e.stackTraceToString()}")
            }
        }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.disconnect()
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Disconnect error: ${e.message}")
        }
        super.onDestroy()
    }
}