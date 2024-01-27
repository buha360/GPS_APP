package com.example.gps_app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
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
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class FinishSC : AppCompatActivity(), CompareGraphsWithSC.ProgressListener {

    private lateinit var solution: CanvasViewSC.Graph
    private lateinit var bitmap: Bitmap
    private lateinit var progressBar: ProgressBar
    private lateinit var imageView: ImageView
    private lateinit var progressText: TextView

    enum class DisplayMode {
        Original, Transformed, Both
    }

    private var currentDisplayMode = DisplayMode.Both

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish_sc)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        imageView = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        loadImage()

        val backButton = findViewById<Button>(R.id.button_back)
        val restartButton = findViewById<Button>(R.id.button_restart)
        val changeButton = findViewById<Button>(R.id.button_change)
        changeButton.visibility = View.INVISIBLE  // Kezdetben láthatatlan

        backButton.setOnClickListener {
            // Visszatérés a DrawingActivity-hez
            val backIntent = Intent(this, DrawingActivitySC::class.java)
            startActivity(backIntent)
        }

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

        imageView.post {
            val width = imageView.width
            val height = imageView.height

            CoroutineScope(Dispatchers.IO).launch {
                val cGraph = CompareGraphsWithSC.getInstance()
                cGraph.progressListener = this@FinishSC

                val largeGraph = MainActivity.DataHolder.largeGraph?.let { map ->
                    convertMapToCanvasViewGraph(map)
                }

                val largeGraphSC = largeGraph?.let { graph ->
                    convertCanvasViewGraphToCanvasViewSCGraph(graph)
                }

                val largeGraphSCMap = largeGraphSC?.let { convertCanvasViewSCGraphToMap(it) }
                solution = largeGraphSCMap?.let {
                    cGraph.findBestRotationMatch(it, CanvasViewSC.DataHolder.graph, width, height)
                } ?: CanvasViewSC.Graph()

                val evenlySpacedPoints = generatePoissonDiskPoints(width, height, 45.0, 165)
                Log.d("spiralPoints", "Generált pontok száma kirajzolasnal: ${evenlySpacedPoints.size}")

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                    drawSolutionOnImage()
                    //drawPointsOnImage(evenlySpacedPoints)  // Megjeleníti a pontokat
                    //drawLogPolarCoordinatesOnImage()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onProgressUpdate(progress: Int) {
        runOnUiThread {
            progressBar.progress = progress
            progressText.text = "$progress%"

            if (progress >= 100) {  // Ha a progress eléri a 100%-ot
                val changeButton = findViewById<Button>(R.id.button_change)
                changeButton.visibility = View.VISIBLE  // A gomb láthatóvá válik
            }
        }
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

    private fun drawTransformedImage() {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.GREEN
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

    private fun convertCanvasViewSCGraphToMap(graph: CanvasViewSC.Graph): MutableMap<CanvasViewSC.Vertex, MutableList<CanvasViewSC.Vertex>> {
        val map = mutableMapOf<CanvasViewSC.Vertex, MutableList<CanvasViewSC.Vertex>>()
        graph.edges.forEach { edge ->
            map.computeIfAbsent(edge.start) { mutableListOf() }.add(edge.end)
            // Ha a gráf irányítatlan, akkor a visszafelé irányuló éleket is hozzáadjuk
            map.computeIfAbsent(edge.end) { mutableListOf() }.add(edge.start)
        }
        return map
    }

    private fun convertMapToCanvasViewGraph(map: MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>>): CanvasView.Graph {
        val graph = CanvasView.Graph()
        map.forEach { (vertex, neighbors) ->
            graph.vertices.add(vertex)
            neighbors.forEach { neighbor ->
                graph.edges.add(CanvasView.Edge(vertex, neighbor))
            }
        }
        return graph
    }

    private fun convertCanvasViewGraphToCanvasViewSCGraph(graph: CanvasView.Graph): CanvasViewSC.Graph {
        val convertedGraph = CanvasViewSC.Graph()

        // Csúcsok átalakítása
        graph.vertices.forEach { vertex ->
            convertedGraph.vertices.add(CanvasViewSC.Vertex(vertex.x, vertex.y))
        }

        // Élek átalakítása
        graph.edges.forEach { edge ->
            val startVertex = convertedGraph.vertices.find { it.x == edge.start.x && it.y == edge.start.y }
            val endVertex = convertedGraph.vertices.find { it.x == edge.end.x && it.y == edge.end.y }

            if (startVertex != null && endVertex != null) {
                convertedGraph.edges.add(CanvasViewSC.Edge(startVertex, endVertex))
            }
        }

        return convertedGraph
    }

    private fun drawSolutionOnImage() {
        Log.d("MapCanva-Finish-drawSolutionOnImage()", "Kirajzolás folyamatban")
        val canvas = Canvas(bitmap)
        val greenPaint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 5f
        }

        // Kirajzoljuk a megoldást zöld vonallal
        solution.edges.forEach { edge ->
            canvas.drawLine(
                edge.start.x.toFloat(), edge.start.y.toFloat(),
                edge.end.x.toFloat(), edge.end.y.toFloat(),
                greenPaint
            )
        }

        val imageView: ImageView = findViewById(R.id.imageView)
        imageView.setImageBitmap(bitmap)
        Log.d("MapCanva-Finish-drawSolutionOnImage()", "Kirajzolás befejezve")
    }

    override fun onDestroy() {
        super.onDestroy()
        bitmap.recycle() // Bitmap felszabadítása
    }

    private fun generatePoissonDiskPoints(canvasWidth: Int, canvasHeight: Int, minDist: Double, numberOfPoints: Int): List<CanvasViewSC.Vertex> {
        val samplePoints = mutableListOf<CanvasViewSC.Vertex>()
        val activeList = mutableListOf<CanvasViewSC.Vertex>()

        // Kezdőpont hozzáadása
        val initialPoint = CanvasViewSC.Vertex(canvasWidth / 2.0, canvasHeight / 2.0)
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
                val newPoint = CanvasViewSC.Vertex(newX, newY)

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

    private fun drawPointsOnImage(points: List<CanvasViewSC.Vertex>) {
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

    private fun drawLogPolarCoordinatesOnImage() {
        val shapeContext = ShapeContext(CanvasViewSC.DataHolder.graph, solution)
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

        // Rajzoljuk ki a CanvasViewSC-ban lévő rajzolt objektumokat
        val drawnGraph = CanvasViewSC.DataHolder.graph
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