package com.example.gps_app

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.properties.Delegates

class Finish : AppCompatActivity() {

    private lateinit var solution: CanvasView.Graph
    private lateinit var bitmap: Bitmap
    private lateinit var imageView: ImageView

    enum class DisplayMode {
        Original, Transformed, Both
    }

    private var transformedGraphColor by Delegates.notNull<Int>()
    private var currentDisplayMode = DisplayMode.Both

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        imageView = findViewById(R.id.imageView)

        loadImage()

        val colorButton = findViewById<Button>(R.id.button_color)
        val restartButton = findViewById<Button>(R.id.button_restart)
        val changeButton = findViewById<Button>(R.id.button_change)
        val controller = ColorPickerController()

        transformedGraphColor = Color.GREEN

        restartButton.setOnClickListener {
            // Újraindítás a MainActivityvel
            val restartIntent = Intent(this, MainActivity::class.java)
            startActivity(restartIntent)
        }

        updateChangeButtonText()
        changeButton.setOnClickListener {
            // Váltás a megjelenítési módok között
            currentDisplayMode = when (currentDisplayMode) {
                DisplayMode.Original -> DisplayMode.Transformed
                DisplayMode.Transformed -> DisplayMode.Both
                DisplayMode.Both -> DisplayMode.Original
            }
            updateChangeButtonText()
            updateImageDisplay()
        }

        colorButton.setOnClickListener{
            showColorPicker()
        }

        imageView.post {
            CoroutineScope(Dispatchers.IO).launch {
                val cGraph = CompareGraph.getInstance()

                solution = MainActivity.DataHolder.largeGraph?.let {
                    cGraph.findBestRotationMatch(it, CanvasView.DataHolder.graph)
                } ?: CanvasView.Graph()

                withContext(Dispatchers.Main) {
                    updateImageDisplay() // Frissítjük a képmegjelenítést
                }
            }
        }
    }

    private fun showColorPicker() {
        val dialog = Dialog(this)
        dialog.setContentView(ComposeView(this).apply {
            setContent {
                val controller = remember { ColorPickerController() }
                ColorPickerDialog(controller) { selectedColor ->
                    transformedGraphColor = selectedColor
                    dialog.dismiss()
                }
            }
        })
        dialog.show()
    }

    @Composable
    fun ColorPickerDialog(controller: ColorPickerController, onColorSelected: (Int) -> Unit) {
        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
                .padding(10.dp),
            controller = controller,
            onColorChanged = { colorEnvelope: ColorEnvelope ->
                // Konvertálja a Compose Color-t Android Color-ra
                onColorSelected(android.graphics.Color.argb(
                    colorEnvelope.color.alpha,
                    colorEnvelope.color.red,
                    colorEnvelope.color.green,
                    colorEnvelope.color.blue
                ))
            }
        )
    }

    private fun drawTransformedImage() {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = transformedGraphColor // A kiválasztott szín használata
            strokeWidth = 5f
        }

        solution.edges.forEach { edge ->
            canvas.drawLine(
                edge.start.x.toFloat(), edge.start.y.toFloat(),
                edge.end.x.toFloat(), edge.end.y.toFloat(),
                paint
            )
        }

        imageView.setImageBitmap(bitmap)
    }

    private fun updateChangeButtonText() {
        val changeButton = findViewById<Button>(R.id.button_change)
        changeButton.text = when (currentDisplayMode) {
            DisplayMode.Original -> "Original"
            DisplayMode.Transformed -> "Transformed"
            DisplayMode.Both -> "Both"
        }
    }

    private fun drawOriginalImage() {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLUE
            strokeWidth = 5f
        }

        CanvasView.DataHolder.graph.edges.forEach { edge ->
            canvas.drawLine(
                edge.start.x.toFloat(), edge.start.y.toFloat(),
                edge.end.x.toFloat(), edge.end.y.toFloat(),
                paint
            )
        }

        imageView.setImageBitmap(bitmap)
    }

    private fun drawBothImages() {
        val canvas = Canvas(bitmap)
        val originalPaint = Paint().apply {
            color = Color.BLUE
            strokeWidth = 5f
        }
        val transformedPaint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 5f
        }

        // Eredeti gráf rajzolása
        CanvasView.DataHolder.graph.edges.forEach { edge ->
            canvas.drawLine(
                edge.start.x.toFloat(), edge.start.y.toFloat(),
                edge.end.x.toFloat(), edge.end.y.toFloat(),
                originalPaint
            )
        }

        // Transzformált gráf rajzolása
        solution.edges.forEach { edge ->
            canvas.drawLine(
                edge.start.x.toFloat(), edge.start.y.toFloat(),
                edge.end.x.toFloat(), edge.end.y.toFloat(),
                transformedPaint
            )
        }

        imageView.setImageBitmap(bitmap)
    }

    override fun onDestroy() {
        super.onDestroy()
        bitmap.recycle() // Bitmap felszabadítása
    }

    private fun loadImage() {
        val imageView: ImageView = findViewById(R.id.imageView)
        val directory = getExternalFilesDir(null)?.absolutePath + "/gps_app"
        val file = File(directory, "map_snapshot.png")

        if (file.exists()) {
            bitmap = BitmapFactory.decodeFile(file.absolutePath).copy(Bitmap.Config.ARGB_8888, true)
            imageView.setImageBitmap(bitmap)
        } else {
            Toast.makeText(this, "The picture is not found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateImageDisplay() {
        loadImage() // Minden váltás előtt újratöltjük az alapképet
        when (currentDisplayMode) {
            DisplayMode.Original -> drawOriginalImage()
            DisplayMode.Transformed -> drawTransformedImage()
            DisplayMode.Both -> drawBothImages()
        }
    }
}