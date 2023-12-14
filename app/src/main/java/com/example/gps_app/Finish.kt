package com.example.gps_app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

class Finish : AppCompatActivity(), CompareGraph.ProgressListener{

    private lateinit var solution: CanvasView.Graph
    private lateinit var bitmap: Bitmap
    private lateinit var progressBar: ProgressBar
    private lateinit var imageView: ImageView
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        imageView = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        loadImage()

        imageView.post {
            val width = imageView.width
            val height = imageView.height

            CoroutineScope(Dispatchers.IO).launch {
                val cGraph = CompareGraph.getInstance()
                cGraph.progressListener = this@Finish

                solution = MainActivity.DataHolder.largeGraph?.let {
                    cGraph.findBestRotationMatch(it, CanvasView.DataHolder.graph, width, height)
                } ?: CanvasView.Graph()

                withContext(Dispatchers.Main) {
                    drawSolutionOnImage()
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onProgressUpdate(progress: Int) {
        runOnUiThread {
            progressBar.progress = progress
            progressText.text = "$progress%"
        }
    }

    private fun drawSolutionOnImage() {
        Log.d("MapCanva-Finish-drawSolutionOnImage()", "Kirajzolás folyamatban")
        val canvas = Canvas(bitmap)
        val greenPaint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 5f
        }

        val yellowPaint = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        // Kirajzoljuk a megoldást zöld vonallal
        solution.edges.forEach { edge ->
            canvas.drawLine(
                edge.start.x.toFloat(), edge.start.y.toFloat(),
                edge.end.x.toFloat(), edge.end.y.toFloat(),
                greenPaint
            )
        }

        // Kirajzoljuk a legközelebbi pontokat sárga körökkel
        CompareGraph.DataHolder.closestMatches.forEach { (_, matchedVertex) ->
            canvas.drawCircle(matchedVertex.x.toFloat(), matchedVertex.y.toFloat(), 10f, yellowPaint)
        }

        val imageView: ImageView = findViewById(R.id.imageView)
        imageView.setImageBitmap(bitmap)
        Log.d("MapCanva-Finish-drawSolutionOnImage()", "Kirajzolás befejezve")
    }

    private fun drawGraphOnImage(bitmap: Bitmap): Bitmap {
        val canvas = Canvas(bitmap)
        val redPaint = Paint().apply {
            color = Color.RED
            strokeWidth = 4f
        }

        val bluePaint = Paint().apply {
            color = Color.BLUE
            strokeWidth = 4f
        }

        // Rajzoljuk ki a térkép gráfot
        val mapGraph = MainActivity.DataHolder.largeGraph
        mapGraph?.forEach { (vertex, neighbors) ->
            neighbors.forEach { neighbor ->
                canvas.drawLine(
                    vertex.x.toFloat(), vertex.y.toFloat(),
                    neighbor.x.toFloat(), neighbor.y.toFloat(),
                    redPaint
                )
            }
        }

        // Rajzoljuk ki a CanvasView-ban lévő rajzolt objektumokat
        val drawnGraph = CanvasView.DataHolder.graph
        drawnGraph.edges.forEach { edge ->
            canvas.drawLine(
                edge.start.x.toFloat(), edge.start.y.toFloat(),
                edge.end.x.toFloat(), edge.end.y.toFloat(),
                bluePaint
            )
        }

        return bitmap
    }

    private fun loadImage() {
        val imageView: ImageView = findViewById(R.id.imageView)
        val directory = getExternalFilesDir(null)?.absolutePath + "/gps_app"
        val file = File(directory, "map_snapshot.png")

        if (file.exists()) {
            bitmap = BitmapFactory.decodeFile(file.absolutePath).copy(Bitmap.Config.ARGB_8888, true)
            bitmap = drawGraphOnImage(bitmap)
            imageView.setImageBitmap(bitmap)
        } else {
            Toast.makeText(this, "A kép nem található!", Toast.LENGTH_SHORT).show()
        }
    }
}