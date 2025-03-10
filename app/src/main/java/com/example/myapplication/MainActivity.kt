package com.example.cameramqttapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val tv = findViewById<TextView>(R.id.sample_text)
        tv.text = stringFromJNI()
    }
    
    external fun stringFromJNI(): String
    
    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }
}