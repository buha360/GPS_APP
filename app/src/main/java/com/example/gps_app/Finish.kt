package com.example.gps_app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.pow

class Finish : AppCompatActivity() {

    data class FloatPoint(val x: Float, val y: Float)

    object DataHolder {
        var bestMatches: List<List<CompareGraph.Point>>? = null
    }

    private lateinit var mMap: MapView

    private var currentBestMatchIndex: Int = 0

    private fun geoPointToCanvasPoint(
        geoPoint: MainActivity.GeoPoint,
        latRange: Pair<Double, Double>,
        lonRange: Pair<Double, Double>,
        width: Int,
        height: Int
    ): FloatPoint {
        val xNormalized = (geoPoint.lon - lonRange.first) / (lonRange.second - lonRange.first)
        val yNormalized =
            1.0 - (geoPoint.lat - latRange.first) / (latRange.second - latRange.first)  // invert y

        val x = (xNormalized * width).toFloat()
        val y = (yNormalized * height).toFloat()

        return FloatPoint(x, y)
    }

    private fun canvasPointToGeoPoint(
        canvasPoint: FloatPoint,
        latRange: Pair<Double, Double>,
        lonRange: Pair<Double, Double>,
        width: Int,
        height: Int
    ): MainActivity.GeoPoint {
        val xNormalized = canvasPoint.x / width
        val yNormalized = 1.0 - (canvasPoint.y / height)  // invert y

        val lon = lonRange.first + xNormalized * (lonRange.second - lonRange.first)
        val lat = latRange.first + yNormalized * (latRange.second - latRange.first)

        return MainActivity.GeoPoint(lat, lon)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        initOpenStreetMap()
        drawGraphOnMap(MainActivity.DataHolder.graph)
        drawAllIntersectionPointsWithIcons()

        CoroutineScope(Dispatchers.IO).launch {
            calculateBestMatch()
        }

        val prevButton: Button = findViewById(R.id.prevMatchButton)
        val nextButton: Button = findViewById(R.id.nextMatchButton)

        Log.d("gps_app-","canva: ${CanvasView.DataHolder.graph}")

        prevButton.setOnClickListener {
            if (currentBestMatchIndex > 0) {
                currentBestMatchIndex--
                drawBestMatchOnMap(DataHolder.bestMatches?.getOrNull(currentBestMatchIndex))
                Log.d("gps_app-", "Current best match graph: ${DataHolder.bestMatches?.getOrNull(currentBestMatchIndex)}")
            }
        }

        nextButton.setOnClickListener {
            if (currentBestMatchIndex < (DataHolder.bestMatches?.size ?: 0) - 1) {
                currentBestMatchIndex++
                drawBestMatchOnMap(DataHolder.bestMatches?.getOrNull(currentBestMatchIndex))
                Log.d("gps_app-", "Current best match graph: ${DataHolder.bestMatches?.getOrNull(currentBestMatchIndex)}")
            }
        }
    }

    private fun drawIntersectionPointWithIcon(geoPoint: MainActivity.GeoPoint) {
        val drawable = resources.getDrawable(androidx.appcompat.R.drawable.abc_btn_check_material, null) // Cserélje le a "your_icon_name"-et az ön ikonjának nevére
        val marker = org.osmdroid.views.overlay.Marker(mMap)
        marker.icon = drawable
        marker.position = org.osmdroid.util.GeoPoint(geoPoint.lat, geoPoint.lon)
        mMap.overlays.add(marker)
    }

    private fun drawAllIntersectionPointsWithIcons() {
        MainActivity.DataHolder.intersectionPoints.forEach { geoPoint ->
            drawIntersectionPointWithIcon(geoPoint)
        }
        mMap.invalidate()  // Frissítse a térképet
    }

    private fun initOpenStreetMap() {
        mMap = findViewById(R.id.osmmap)
        mMap.setTileSource(TileSourceFactory.MAPNIK)
        mMap.setMultiTouchControls(true)

        // Az átadott adatok beállítása:
        val zoomLevel = MainActivity.DataHolder.mapZoomLevel!!
        val latitude = MainActivity.DataHolder.mapLatitude!!
        val longitude = MainActivity.DataHolder.mapLongitude!!

        mMap.controller.setZoom(zoomLevel)
        mMap.setExpectedCenter(org.osmdroid.util.GeoPoint(latitude, longitude))
    }

    private fun drawGraphOnMap(mainGraph: Map<MainActivity.GeoPoint, MutableList<MainActivity.GeoPoint>>?) {
        mainGraph?.forEach { (key, value) ->
            val startPoint = org.osmdroid.util.GeoPoint(key.lat, key.lon)
            value.forEach { adjPoint ->
                val endPoint = org.osmdroid.util.GeoPoint(adjPoint.lat, adjPoint.lon)

                // Create and set up the Polyline overlay for each key-adjacent point pair
                val polyline = Polyline(mMap)
                polyline.setPoints(listOf(startPoint, endPoint))
                polyline.setColor(Color.RED)
                mMap.overlays.add(polyline)
            }
        }
        mMap.invalidate() // Refresh the map
    }

    private suspend fun calculateBestMatch() {
        withContext(Dispatchers.Main) {
            val cGraphs = CompareGraph.getInstance()
            val mainGraph = MainActivity.DataHolder.graph
            val canvasGraph = CanvasView.DataHolder.graph

            // 1. Projektáljuk a geokoordinátás gráfot a vászon pontokra
            val projectedMainGraph = projectMainGraphToCanvas(mainGraph)

            // 2. Keresünk subgraphot a projektált gráfon
            val projectedBestMatches = canvasGraph?.let {
                cGraphs.findSubgraph(
                    projectedMainGraph,
                    it
                )
            } ?: emptyList()

            val inverseProjectedMatches = projectedBestMatches.map { inverseProjectGraph(it) }

            // Transzformáljuk a mainGraph-ot, hogy megfeleljen a CompareGraph.Point típusnak
            val transformedMainGraph = transformMainGraphToCompareGraphPoint(mainGraph)

            val finalBestMatches = inverseProjectedMatches.flatMap { projectedMatch ->
                val inverseProjectedGraph = CanvasView.Graph()
                projectedMatch.forEach { point ->
                    inverseProjectedGraph.vertices.add(CanvasView.Vertex(point.x, point.y))
                }

                // 4. Újra keresünk subgraphot az eredeti geokoordinátás gráfon
                cGraphs.findSubgraph(transformedMainGraph, inverseProjectedGraph) ?: emptyList()
            }

            // Az eredményt tároljuk a DataHolder-ben
            DataHolder.bestMatches = finalBestMatches
            Log.d("gps_app-finish: - bestMatches: ", DataHolder.bestMatches.toString())
            drawBestMatchOnMap(DataHolder.bestMatches?.getOrNull(currentBestMatchIndex))
        }
    }

    private fun transformMainGraphToCompareGraphPoint(graph: MutableMap<MainActivity.GeoPoint, MutableList<MainActivity.GeoPoint>>?): MutableMap<CompareGraph.Point, MutableList<CompareGraph.Point>> {
        val transformedGraph = mutableMapOf<CompareGraph.Point, MutableList<CompareGraph.Point>>()
        graph?.forEach { (key, value) ->
            val transformedKey = CompareGraph.Point(key.lat, key.lon)
            val transformedValue = value.map { CompareGraph.Point(it.lat, it.lon) }.toMutableList()
            transformedGraph[transformedKey] = transformedValue
        }
        return transformedGraph
    }

    private fun projectMainGraphToCanvas(mainGraph: Map<MainActivity.GeoPoint, MutableList<MainActivity.GeoPoint>>?): MutableMap<CompareGraph.Point, MutableList<CompareGraph.Point>> {
        val projectedGraph = mutableMapOf<CompareGraph.Point, MutableList<CompareGraph.Point>>()
        val latRange = Pair(
            MainActivity.DataHolder.mapLatitude!! - 200 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!),
            MainActivity.DataHolder.mapLatitude!! + 200 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!)
        )
        val lonRange = Pair(
            MainActivity.DataHolder.mapLongitude!! - 200 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!),
            MainActivity.DataHolder.mapLongitude!! + 200 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!)
        )
        mainGraph?.forEach { (key, value) ->
            val start = geoPointToCanvasPoint(key, latRange, lonRange, 400, 400)
            val neighbors = value.map { geoPointToCanvasPoint(it, latRange, lonRange, 400, 400) }
            projectedGraph[CompareGraph.Point(start.x.toDouble(), start.y.toDouble())] =
                neighbors.map { CompareGraph.Point(it.x.toDouble(), it.y.toDouble()) }.toMutableList()
        }
        return projectedGraph
    }

    private fun inverseProjectGraph(projectedGraph: List<CompareGraph.Point>): List<CompareGraph.Point> {
        val inverseList = mutableListOf<CompareGraph.Point>()
        val latRange = Pair(
            MainActivity.DataHolder.mapLatitude!! - 200 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!),
            MainActivity.DataHolder.mapLatitude!! + 200 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!)
        )
        val lonRange = Pair(
            MainActivity.DataHolder.mapLongitude!! - 200 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!),
            MainActivity.DataHolder.mapLongitude!! + 200 * 2.0.pow(-MainActivity.DataHolder.mapZoomLevel!!)
        )
        projectedGraph.forEach { point ->
            val geoPoint = canvasPointToGeoPoint(FloatPoint(point.x.toFloat(), point.y.toFloat()), latRange, lonRange, 400, 400)
            inverseList.add(CompareGraph.Point(geoPoint.lat, geoPoint.lon))
        }
        return inverseList
    }

    private fun drawBestMatchOnMap(bestMatch: List<CompareGraph.Point>?) {
        bestMatch?.takeIf { it.size > 1 }?.let {
            for (i in 0 until it.size - 1) {
                val startPoint = org.osmdroid.util.GeoPoint(it[i].x, it[i].y)
                val endPoint = org.osmdroid.util.GeoPoint(it[i+1].x, it[i+1].y)

                val polyline = Polyline(mMap)
                polyline.setPoints(listOf(startPoint, endPoint))
                polyline.setColor(Color.BLUE)
                mMap.overlays.add(polyline)
            }
            mMap.invalidate()
        }
    }
}