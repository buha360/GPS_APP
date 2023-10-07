package com.example.gps_app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class DrawingActivity : AppCompatActivity() {
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
            canvasView.createGraphFromPathData() // Először létrehozzuk a gráfot
            val intent = Intent(this, Finish::class.java) // Majd elnavigálunk a Finish aktivitáshoz
            startActivity(intent)
        }
    }
}