package com.example.myapplication

import android.os.Bundle
import android.widget.Button // ADD THIS IMPORT
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera) 

        val captureButton = findViewById<Button>(R.id.captureButton)
        captureButton.setOnClickListener {
            Toast.makeText(this, "Camera works!", Toast.LENGTH_SHORT).show()
        }
    }
}