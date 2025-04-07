package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
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
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken

import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private var isMqttConnected = false

    private lateinit var mqttClient: MqttClient
    private lateinit var imageCapture: ImageCapture
    private lateinit var statusTextView: TextView
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

    private val mqttBroker = "tcp://broker.hivemq.com:1883"
    private val mqttTopic = "android/camera/${System.currentTimeMillis()}"
    private val mqttPhotoTopic = "$mqttTopic/photo"
    private val MAX_MESSAGE_SIZE = 10 * 1024 * 1024

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            updateStatus("ERROR: Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        statusTextView = findViewById(R.id.statusTextView)
        updateStatus("Initializing...")

        setupMqtt()

        if (hasCameraPermission()) startCamera()
        else requestPermissionLauncher.launch(Manifest.permission.CAMERA)

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            if (!isNetworkAvailable()) {
                Toast.makeText(this, "No network connection", Toast.LENGTH_LONG).show()
                updateStatus("ERROR: No network connection")
                return@setOnClickListener
            }
            if (!isMqttConnected) {
                Toast.makeText(this, "MQTT not connected", Toast.LENGTH_LONG).show()
                updateStatus("ERROR: MQTT not connected, please wait...")
                // Optionally, you can call setupMqtt() again or disable the button until connected.
                return@setOnClickListener
            }
            if (!::mqttClient.isInitialized || !mqttClient.isConnected) {
                Toast.makeText(this, "MQTT not connected", Toast.LENGTH_LONG).show()
                updateStatus("ERROR: MQTT not connected, reconnecting...")
                setupMqtt()
                return@setOnClickListener
            }

            updateStatus("Taking photo...")
            lifecycleScope.launch(Dispatchers.IO) { takePhoto() }
        }
    }

    private fun updateStatus(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            statusTextView.text = message
            Log.d("Status", message)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun setupMqtt() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (::mqttClient.isInitialized && mqttClient.isConnected) {
                    try {
                        mqttClient.disconnect()
                    } catch (e: MqttException) {
                        Log.e("MQTT", "Disconnect error: ${e.message}")
                    }
                }

                val clientId = "AndroidClient-${System.currentTimeMillis()}"
                mqttClient = MqttClient(mqttBroker, clientId)

                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        Log.e("MQTT", "Connection lost: ${cause?.message}")
                        updateStatus("MQTT connection lost. Reconnecting...")
                        setupMqtt()
                    }

                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        Log.d("MQTT", "Message received on topic: $topic")
                    }

                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        Log.d("MQTT", "Message delivery complete")
                    }
                })

                val options = MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 30
                    keepAliveInterval = 60
                    isAutomaticReconnect = true
                }

                mqttClient.connect(options)

                var connectStatus = "MQTT Connection failed"
                if (mqttClient.isConnected) {
                    mqttClient.publish("$mqttTopic/status", "Connected".toByteArray(), 1, false)
                    isMqttConnected = true  // Add this line
                    connectStatus = "MQTT Connected to $mqttBroker"
                } else {
                    connectStatus = "MQTT Connection failed"
                }
                withContext(Dispatchers.Main) {
                    updateStatus(connectStatus)
                    Toast.makeText(this@MainActivity, connectStatus, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                val errorMsg = "MQTT Error: ${e.message}"
                Log.e("MQTT", "Connection error: ${e.stackTraceToString()}")
                withContext(Dispatchers.Main) {
                    updateStatus(errorMsg)
                    Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val previewView = findViewById<PreviewView>(R.id.previewView)

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                    updateStatus("Camera ready")
                } catch (e: Exception) {
                    val errorMsg = "Camera setup failed: ${e.message}"
                    Log.e("Camera", errorMsg)
                    updateStatus(errorMsg)
                }
            } catch (e: Exception) {
                val errorMsg = "Camera provider error: ${e.message}"
                Log.e("Camera", errorMsg)
                updateStatus(errorMsg)
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
                    val msg = "Photo saved: ${file.path}"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    updateStatus("Uploading photo...")
                    sendMqttMessage("$mqttTopic/info", "Photo captured: ${file.name}")
                    uploadPhotoViaMqtt(file)
                }

                override fun onError(exc: ImageCaptureException) {
                    val errorMsg = "Photo capture failed: ${exc.message}"
                    Log.e("Camera", errorMsg)
                    updateStatus(errorMsg)
                }
            }
        )
    }

    private fun uploadPhotoViaMqtt(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!::mqttClient.isInitialized || !mqttClient.isConnected) {
                    updateStatus("ERROR: MQTT disconnected during upload")
                    setupMqtt()
                    return@launch
                }

                val photoBytes = try {
                    file.readBytes()
                } catch (e: IOException) {
                    Log.e("MQTT", "Failed to read photo file: ${e.message}")
                    updateStatus("ERROR: Failed to read photo file")
                    return@launch
                }

                if (photoBytes.size > MAX_MESSAGE_SIZE) {
                    val errorMsg = "Photo too large for MQTT: ${photoBytes.size} bytes"
                    Log.e("MQTT", errorMsg)
                    updateStatus(errorMsg)
                    sendMqttMessage("$mqttTopic/warning",
                        "Photo too large (${photoBytes.size} bytes). Consider compression or alternative upload method")
                    return@launch
                }
                try {
                    mqttClient.publish(mqttPhotoTopic, photoBytes, 2, false)
                    Log.d("MQTT", "Photo delivery confirmed")
                    updateStatus("SUCCESS: Photo uploaded successfully")
                } catch (e: MqttException) {
                    Log.e("MQTT", "Photo delivery failed", e)
                    updateStatus("ERROR: Photo upload failed: ${e.message}")
                }




            } catch (e: Exception) {
                val errorMsg = "Photo upload failed: ${e.message}"
                Log.e("MQTT", "Upload error: ${e.stackTraceToString()}")
                updateStatus("ERROR: $errorMsg")
            }
        }
    }

    private fun sendMqttMessage(topic: String, message: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (::mqttClient.isInitialized && mqttClient.isConnected) {
                    mqttClient.publish(topic, message.toByteArray(), 1, false)
                    Log.d("MQTT", "Message sent to $topic: $message")
                } else {
                    Log.e("MQTT", "Cannot send message: client not connected")
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
                sendMqttMessage("$mqttTopic/info", "Device disconnecting")
                mqttClient.disconnect()
            }
        } catch (e: MqttException) {
            Log.e("MQTT", "Disconnect error: ${e.message}")
        }
        super.onDestroy()
    }
}