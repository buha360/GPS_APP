package com.example.gps_app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DrawingActivity : AppCompatActivity() {
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        // példányosítások
        val canvasView = findViewById<CanvasView>(R.id.canvasView)
        val clearButton = findViewById<Button>(R.id.clearButton)
        val backButton = findViewById<Button>(R.id.buttonBack)
        val saveButton = findViewById<Button>(R.id.saveButton)

        // clear button
        clearButton.setOnClickListener {
            canvasView.clearCanvas() // clearCanvas függvény meghívása a CanvasView példányon
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
                val intent = Intent(this@DrawingActivity, Finish::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}