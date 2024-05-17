package com.wardanger3.gps_app

import android.annotation.SuppressLint
import android.app.Activity
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
import com.wardanger3.gps_app.interfaces.IFinish
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.properties.Delegates

class FinishSC : AppCompatActivity(), CompareGraphsSC.ProgressListener, IFinish {

    private lateinit var solution: CanvasViewSC.Graph
    private lateinit var bitmap: Bitmap
    private lateinit var progressBar: ProgressBar
    private lateinit var imageView: ImageView
    private lateinit var progressText: TextView
    private lateinit var mAdView : AdView

    private var transformedGraphColor by Delegates.notNull<Int>()
    private var currentDisplayMode = IFinish.DisplayMode.Both

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish_sc)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        loadBannerAd()
        loadImage()

        imageView = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)

        val colorButton = findViewById<Button>(R.id.button_color)
        val restartButton = findViewById<Button>(R.id.button_restart)
        val changeButton = findViewById<Button>(R.id.button_change)
        changeButton.visibility = View.INVISIBLE  // Kezdetben láthatatlan

        transformedGraphColor = Color.GREEN

        restartButton.setOnClickListener {
            resetAppState()
            val restartIntent = Intent(this, MainActivity::class.java)
            startActivity(restartIntent)
        }

        colorButton.setOnClickListener {
            val intent = Intent(this, ColorPickerActivity::class.java)
            startActivityForResult(intent, IFinish.COLOR_PICKER_REQUEST)
        }

        updateChangeButtonText()
        changeButton.setOnClickListener {
            // Váltás a megjelenítési módok között
            currentDisplayMode = when (currentDisplayMode) {
                IFinish.DisplayMode.Original -> IFinish.DisplayMode.Edited
                IFinish.DisplayMode.Edited -> IFinish.DisplayMode.Both
                IFinish.DisplayMode.Both -> IFinish.DisplayMode.Original
            }
            updateChangeButtonText()
            updateImageDisplay()
        }

        imageView.post {
            val width = imageView.width
            val height = imageView.height

            CoroutineScope(Dispatchers.IO).launch {
                val cGraph = CompareGraphsSC.getInstance()
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

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                    drawSolutionOnImage()
                    changeButton.visibility = View.VISIBLE  // A gomb láthatóvá válik
                }
            }
        }
    }

    private fun loadBannerAd(){
        MobileAds.initialize(this) {}
        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IFinish.COLOR_PICKER_REQUEST && resultCode == Activity.RESULT_OK) {
            val selectedColor = data?.getIntExtra("selectedColor", Color.GREEN) ?: Color.GREEN
            transformedGraphColor = selectedColor
            updateImageDisplay()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onProgressUpdate(progress: Int) {
        runOnUiThread {
            progressBar.progress = progress
            progressText.text = "$progress%"
        }
    }

    override fun updateChangeButtonText() {
        val changeButton = findViewById<Button>(R.id.button_change)
        changeButton.text = when (currentDisplayMode) {
            IFinish.DisplayMode.Original -> "Original"
            IFinish.DisplayMode.Edited -> "Edited"
            IFinish.DisplayMode.Both -> "Both"
        }
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
        val vertexMap = hashMapOf<Pair<Double, Double>, CanvasViewSC.Vertex>()

        // Pre-fill the hashmap with converted vertices for fast lookup
        graph.vertices.forEach { vertex ->
            val scVertex = CanvasViewSC.Vertex(vertex.x, vertex.y)
            convertedGraph.vertices.add(scVertex)
            vertexMap[vertex.x to vertex.y] = scVertex
        }

        // Use the hashmap for quick edge conversion
        graph.edges.forEach { edge ->
            val startVertex = vertexMap[edge.start.x to edge.start.y]
            val endVertex = vertexMap[edge.end.x to edge.end.y]
            if (startVertex != null && endVertex != null) {
                convertedGraph.edges.add(CanvasViewSC.Edge(startVertex, endVertex))
            }
        }

        return convertedGraph
    }

    override fun drawOriginalImage() {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLUE
            strokeWidth = 5f
        }

        CanvasViewSC.DataHolder.graph.edges.forEach { edge ->
            canvas.drawLine(
                edge.start.x.toFloat(), edge.start.y.toFloat(),
                edge.end.x.toFloat(), edge.end.y.toFloat(),
                paint
            )
        }

        imageView.setImageBitmap(bitmap)
    }

    override fun drawTransformedImage() {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = transformedGraphColor
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

    override fun drawBothImages() {
        val canvas = Canvas(bitmap)
        val originalPaint = Paint().apply {
            color = Color.BLUE
            strokeWidth = 5f
        }
        val transformedPaint = Paint().apply {
            color = transformedGraphColor // Itt használjuk a kiválasztott színt
            strokeWidth = 5f
        }

        // Eredeti gráf rajzolása
        CanvasViewSC.DataHolder.graph.edges.forEach { edge ->
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

    override fun loadImage() {
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

    override fun updateImageDisplay() {
        loadImage() // Minden váltás előtt újratöltjük az alapképet
        when (currentDisplayMode) {
            IFinish.DisplayMode.Original -> drawOriginalImage()
            IFinish.DisplayMode.Edited -> drawTransformedImage()
            IFinish.DisplayMode.Both -> drawBothImages()
        }
    }

    override fun resetAppState() {
        MainActivity.DataHolder.clearData()
        CanvasViewSC.DataHolder.clearData()
    }
}