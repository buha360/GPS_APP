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
import kotlin.math.sqrt

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

                val evenlySpacedPoints = generatePoissonDiskPoints(width, height, 45.0, 165)
                Log.d("spiralPoints", "Generált pontok száma kirajzolasnal: ${evenlySpacedPoints.size}")

                withContext(Dispatchers.Main) {
                    drawSolutionOnImage()
                    //drawPointsOnImage(evenlySpacedPoints)  // Megjeleníti a pontokat
                    //drawLogPolarCoordinatesOnImage()
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bitmap.recycle() // Bitmap felszabadítása
    }

    private fun generatePoissonDiskPoints(canvasWidth: Int, canvasHeight: Int, minDist: Double, numberOfPoints: Int): List<CanvasView.Vertex> {
        val samplePoints = mutableListOf<CanvasView.Vertex>()
        val activeList = mutableListOf<CanvasView.Vertex>()

        // Kezdőpont hozzáadása
        val initialPoint = CanvasView.Vertex(canvasWidth / 2.0, canvasHeight / 2.0)
        samplePoints.add(initialPoint)
        activeList.add(initialPoint)

        val centerX = canvasWidth / 2.0
        val centerY = canvasHeight / 2.0
        val maxRadius = min(canvasWidth, canvasHeight) / 2.8

        while (activeList.isNotEmpty() && samplePoints.size < numberOfPoints) {
            val pointIndex = (Math.random() * activeList.size).toInt()
            val point = activeList[pointIndex]
            var found = false

            for (i in 0 until numberOfPoints) {
                val angle = Math.random() * 2 * Math.PI
                val radius = minDist * (Math.random() + 1)
                val newX = point.x + radius * cos(angle)
                val newY = point.y + radius * sin(angle)
                val newPoint = CanvasView.Vertex(newX, newY)

                // Ellenőrizzük, hogy a pont a körön belül van-e és elegendő távolságra van-e a többi ponttól
                val isInsideCircle = (newX - centerX) * (newX - centerX) + (newY - centerY) * (newY - centerY) <= maxRadius * maxRadius
                val isFarEnough = samplePoints.none { existingPoint ->
                    val dx = existingPoint.x - newX
                    val dy = existingPoint.y - newY
                    sqrt(dx * dx + dy * dy) < minDist
                }

                if (isInsideCircle && isFarEnough) {
                    activeList.add(newPoint)
                    samplePoints.add(newPoint)
                    found = true
                    break
                }
            }

            if (!found) {
                activeList.removeAt(pointIndex)
            }
        }

        return samplePoints
    }

    private fun drawPointsOnImage(points: List<CanvasView.Vertex>) {
        val canvas = Canvas(bitmap)
        val pointPaint = Paint().apply {
            color = Color.RED
            strokeWidth = 10f
            style = Paint.Style.FILL
        }

        points.forEach { point ->
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 5f, pointPaint)
        }

        imageView.setImageBitmap(bitmap)
        Log.d("MapCanva-Finish-drawPointsOnImage", "Pontok kirajzolva")
    }

    @SuppressLint("SetTextI18n")
    override fun onProgressUpdate(progress: Int) {
        runOnUiThread {
            progressBar.progress = progress
            progressText.text = "$progress%"
        }
    }

    private fun drawSolutionOnImage() {
        Log.d("MapCanva-Finish", "Kirajzolás folyamatban")
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 5f
        }

        // Tegyük fel, hogy a nagy gráfot itt tároljuk:
        val largeGraph = MainActivity.DataHolder.largeGraph

        solution.edges.forEach { edge ->
            // Ellenőrizzük, hogy az él létezik-e a nagy gráfban
            if (largeGraph?.get(edge.start)?.contains(edge.end) == true) {
                canvas.drawLine(
                    edge.start.x.toFloat(), edge.start.y.toFloat(),
                    edge.end.x.toFloat(), edge.end.y.toFloat(),
                    paint
                )
                Log.d("Teljes-graph-f", "Él rajzolva: ${edge.start} -> ${edge.end}")
            }
        }

        imageView.setImageBitmap(bitmap)
        imageView.invalidate()
        Log.d("MapCanva-Finish-drawSolutionOnImage()", "Kirajzolás befejezve")
    }

    private fun drawLogPolarGrid(canvas: Canvas, paint: Paint, numRadiusBins: Int, numAngleBins: Int, centerX: Float, centerY: Float, maxRadius: Float) {
        // Sugár (radius) bin-ek rajzolása
        for (i in 0 until numRadiusBins) {
            val radius = (i.toFloat() / numRadiusBins) * maxRadius
            canvas.drawCircle(centerX, centerY, radius, paint)
        }

        // Szögek (angle) bin-ek rajzolása
        for (j in 0 until numAngleBins) {
            val angle = (j.toFloat() / numAngleBins) * (2 * Math.PI).toFloat()
            val xEnd = centerX + maxRadius * cos(angle)
            val yEnd = centerY + maxRadius * sin(angle)
            canvas.drawLine(centerX, centerY, xEnd, yEnd, paint)
        }
    }

    // A függvény hívása a megfelelő helyen a Finish osztályban:
    private fun drawLogPolarCoordinatesOnImage() {
        val shapeContext = ShapeContext(CanvasView.DataHolder.graph, solution)
        val (numBinsRadius, numBinsAngle) = shapeContext.getNumBins()

        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.MAGENTA
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        // A logpolár koordináta rendszer középpontjának és sugárának beállítása
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f
        val maxRadius = centerX.coerceAtMost(centerY) // Például a kisebbik érték

        //drawLogPolarGrid(canvas, paint, numBinsRadius, numBinsAngle, centerX, centerY, maxRadius)

        imageView.setImageBitmap(bitmap)
        Log.d("MapCanva-Finish", "Logpolár koordináta rendszer kirajzolva")
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