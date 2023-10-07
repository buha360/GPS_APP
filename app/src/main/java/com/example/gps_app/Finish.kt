package com.example.gps_app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.pow

class Finish : AppCompatActivity() {

    data class FloatPoint(val x: Float, val y: Float)

    private fun geoPointToCanvasPoint(geoPoint: MainActivity.GeoPoint, latRange: Pair<Double, Double>, lonRange: Pair<Double, Double>, width: Int, height: Int): FloatPoint {
        val x = ((geoPoint.lon - lonRange.first) / (lonRange.second - lonRange.first) * width).toFloat()
        val y = ((latRange.second - geoPoint.lat) / (latRange.second - latRange.first) * height).toFloat()

        // Logolás hozzáadása
        Log.d("gps_app", "GeoPoint: ${geoPoint.lon}, ${geoPoint.lat} -> CanvasPoint: $x, $y")

        return FloatPoint(x, y)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        loadAndDisplayMapSnapshot()

        CoroutineScope(Dispatchers.IO).launch {
            calculateBestMatch()
        }
    }

    private fun loadAndDisplayMapSnapshot() {
        val imageViewSnapshot: ImageView = findViewById(R.id.mapSnapshotImageView)

        // Az alkalmazás specifikus könyvtár meghatározása
        val externalFilesDir = getExternalFilesDir(null)
        val storageDirectory = File(externalFilesDir, "gps_app")

        val fileToLoad = File(storageDirectory, "map_snapshot.png")

        if (fileToLoad.exists()) {
            val originalBitmap = BitmapFactory.decodeFile(fileToLoad.absolutePath)
            val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

            val canvas = Canvas(mutableBitmap)
            drawGraphOnCanvas(canvas, MainActivity.DataHolder.graph)

            imageViewSnapshot.setImageBitmap(mutableBitmap)
        } else {
            Toast.makeText(this, "No snapshot found!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawGraphOnCanvas(canvas: Canvas, graph: Map<MainActivity.GeoPoint, MutableList<MainActivity.GeoPoint>>?) {
        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }

        val width = canvas.width
        val height = canvas.height
        val latRange = Pair(MainActivity.DataHolder.mapLatitude!! - height / 2 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!), MainActivity.DataHolder.mapLatitude!! + height / 2 * 2.0.pow(
            -MainActivity.DataHolder.mapZoomLevel!!
        )
        )
        val lonRange = Pair(MainActivity.DataHolder.mapLongitude!! - width / 2 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!), MainActivity.DataHolder.mapLongitude!! + width / 2 * 2.0.pow(
            -MainActivity.DataHolder.mapZoomLevel!!
        )
        )

        graph?.forEach { (startGeo, adjList) ->
            val startPoint = geoPointToCanvasPoint(startGeo, latRange, lonRange, width, height)
            adjList.forEach { endGeo ->
                val endPoint = geoPointToCanvasPoint(endGeo, latRange, lonRange, width, height)
                canvas.drawLine(startPoint.x, startPoint.y, endPoint.x, endPoint.y, paint)
            }
        }
    }

    private suspend fun calculateBestMatch() {
        withContext(Dispatchers.Main) {
            val cGraphs = CompareGraph.getInstance()
            val mainGraph = MainActivity.DataHolder.graph
            val canvasGraph = CanvasView.DataHolder.graph

            if (mainGraph != null && canvasGraph != null) {
                //cGraphs.findSubgraph(mainGraph, canvasGraph)
            } else {
                Log.d("gps_app-mainactivity: - graph: ", "valami null")
            }
        }
    }
}