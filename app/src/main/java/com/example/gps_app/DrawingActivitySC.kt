package com.example.gps_app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrawingActivitySC : AppCompatActivity() {
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing_sc)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        // példányosítások
        val canvasView: CanvasViewSC = findViewById(R.id.canvasViewSC)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val backButton = findViewById<Button>(R.id.buttonBack)
        val saveButton = findViewById<Button>(R.id.saveButton)

        clearButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_red)
        backButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_green)
        saveButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_red)

        clearButton.isEnabled = false
        saveButton.isEnabled = false

        canvasView.onDrawListener = object : CanvasViewSC.OnDrawListener {
            override fun onDrawStarted() {
                clearButton.isEnabled = true
                saveButton.isEnabled = true
                clearButton.setTextColor(Color.parseColor("#00ff7b"))
                saveButton.setTextColor(Color.parseColor("#00ff7b"))
                clearButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_green)
                saveButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_green)
            }
        }

        // clear button
        clearButton.setOnClickListener {
            canvasView.clearCanvas()

            saveButton.isEnabled = false
            clearButton.isEnabled = false
            saveButton.setTextColor(Color.parseColor("#ff0000")) // Piros szövegszín
            saveButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_red)
            clearButton.setTextColor(Color.parseColor("#ff0000")) // Piros szövegszín
            clearButton.background = ContextCompat.getDrawable(this@DrawingActivitySC, R.drawable.custom_button_red)
        }

        //back button
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java) // MainActivity újraindítása
            startActivity(intent)
        }

        //save button
        saveButton.setOnClickListener {
            activityScope.launch {
                // Először létrehozzuk a gráfot
                withContext(Dispatchers.Default) {
                    canvasView.createGraphFromPathData()
                }
                // Ha befejeződött, akkor elnavigálunk a Finish aktivitáshoz
                val intent = Intent(this@DrawingActivitySC, FinishSC::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}