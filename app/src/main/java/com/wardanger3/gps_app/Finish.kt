package com.wardanger3.gps_app

import android.app.Activity
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
import com.wardanger3.gps_app.interfaces.IFinish
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.properties.Delegates

class Finish : AppCompatActivity(), IFinish {

    private lateinit var solution: CanvasView.Graph
    private lateinit var bitmap: Bitmap
    private lateinit var imageView: ImageView
    private lateinit var mAdView : AdView
    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private var transformedGraphColor by Delegates.notNull<Int>()
    private var currentDisplayMode = IFinish.DisplayMode.Both
    private var transformedGraphStrokeWidth = 5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        firebaseAnalytics = Firebase.analytics

        loadBannerAd()

        imageView = findViewById(R.id.imageView)

        val colorButton = findViewById<Button>(R.id.button_color)
        val restartButton = findViewById<Button>(R.id.button_restart)
        val changeButton = findViewById<Button>(R.id.button_change)

        transformedGraphColor = Color.GREEN

        restartButton.setOnClickListener {

            resetAppState()

            // Újraindítás a MainActivityvel
            val restartIntent = Intent(this, MainActivity::class.java)
            startActivity(restartIntent)
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

        colorButton.setOnClickListener {
            val intent = Intent(this, ColorPickerActivity::class.java)
            startActivityForResult(intent, IFinish.COLOR_PICKER_REQUEST)
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
            val strokeWidth = data?.getFloatExtra("strokeWidth", 5f) ?: 5f
            transformedGraphColor = selectedColor
            transformedGraphStrokeWidth = strokeWidth
            updateImageDisplay()
        }
    }

    private fun edgeExistsInGraph(edge: CanvasView.Edge, graph: Map<CanvasView.Vertex, List<CanvasView.Vertex>>): Boolean {
        return graph[edge.start]?.contains(edge.end) == true
    }

    override fun drawTransformedImage() {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = transformedGraphColor
            strokeWidth = transformedGraphStrokeWidth
        }

        val largeGraph = MainActivity.DataHolder.largeGraph

        solution.edges.forEach { edge ->
            if (largeGraph != null && edgeExistsInGraph(edge, largeGraph)) {
                canvas.drawLine(
                    edge.start.x.toFloat(), edge.start.y.toFloat(),
                    edge.end.x.toFloat(), edge.end.y.toFloat(),
                    paint
                )
            }
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
            color = transformedGraphColor
            strokeWidth = transformedGraphStrokeWidth
        }

        val largeGraph = MainActivity.DataHolder.largeGraph

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
            if (largeGraph != null && edgeExistsInGraph(edge, largeGraph)) {
                canvas.drawLine(
                    edge.start.x.toFloat(), edge.start.y.toFloat(),
                    edge.end.x.toFloat(), edge.end.y.toFloat(),
                    transformedPaint
                )
            }
        }

        imageView.setImageBitmap(bitmap)
    }

    override fun updateChangeButtonText() {
        val changeButton = findViewById<Button>(R.id.button_change)
        changeButton.text = when (currentDisplayMode) {
            IFinish.DisplayMode.Original -> "Original"
            IFinish.DisplayMode.Edited -> "Edited"
            IFinish.DisplayMode.Both -> "Both"
        }
    }

    override fun drawOriginalImage() {
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
        CanvasView.DataHolder.clearData()
        CompareGraph.DataHolder.clearData()
    }
}