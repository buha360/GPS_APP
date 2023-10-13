package com.example.gps_app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.opencv.android.OpenCVLoader
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : FragmentActivity(), MapListener {

    data class GeoPoint(val lat: Double, val lon: Double)

    companion object {
        // A Föld sugara méterben
        const val EARTH_RADIUS = 6371000
    }

    data class Way(
        val type: String,
        val id: Long,
        val nodes: List<Long>,
        val tags: Map<String, String>
    )

    data class Node(
        val type: String,
        val id: Long,
        val lat: Double,
        val lon: Double
    )

    private lateinit var mMap: MapView
    private lateinit var controller: IMapController
    private lateinit var mMyLocationOverlay: MyLocationNewOverlay

    object DataHolder {
        var mapZoomLevel: Double? = null
        var mapLatitude: Double? = null
        var mapLongitude: Double? = null
        var graph: MutableMap<GeoPoint, MutableList<GeoPoint>>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences(getString(R.string.app_name), MODE_PRIVATE)
        )

        mMap = findViewById(R.id.osmmap)
        mMap.setTileSource(TileSourceFactory.MAPNIK)
        mMap.setMultiTouchControls(true)
        mMap.getLocalVisibleRect(Rect())

        mMyLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mMap)
        controller = mMap.controller

        mMyLocationOverlay.enableMyLocation()
        mMyLocationOverlay.enableFollowLocation()
        mMyLocationOverlay.isDrawAccuracyEnabled = true
        mMyLocationOverlay.runOnFirstFix {
            runOnUiThread {
                controller.setCenter(mMyLocationOverlay.myLocation)
                controller.animateTo(mMyLocationOverlay.myLocation)
            }
        }

        controller.setZoom(6.0)
        mMap.overlays.add(mMyLocationOverlay)
        mMap.addMapListener(this)

        val buttonNext: Button = findViewById(R.id.button_next)
        buttonNext.setOnClickListener {
            hideMapUIElements()
            fetchRoadsFromOverpass()

            DataHolder.mapZoomLevel = mMap.zoomLevelDouble
            DataHolder.mapLatitude = mMap.mapCenter.latitude
            DataHolder.mapLongitude = mMap.mapCenter.longitude

            val intent = Intent(this, DrawingActivity::class.java)
            startActivity(intent)
        }
    }

    private fun fetchRoadsFromOverpass() {
        val boundingBox = mMap.boundingBox
        val overpassQuery = """
        [out:json];
        (
            way["highway"~"motorway|trunk|primary|secondary|tertiary|unclassified|residential|service|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link"](${boundingBox.latSouth},${boundingBox.lonWest},${boundingBox.latNorth},${boundingBox.lonEast});
        );
        out body;
    >;
    out geom;
    """.trimIndent()

        Thread {
            try {
                val url = URL(
                    "https://overpass-api.de/api/interpreter?data=${
                        URLEncoder.encode(
                            overpassQuery,
                            "UTF-8"
                        )
                    }"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val result = connection.inputStream.bufferedReader().readText()
                    val (ways, nodes) = parseOverpassResponse(result)
                    val graph = buildGraph(ways, nodes)
                } else {
                    Log.e("OverpassAPI", "Error: ${connection.responseCode} - ${connection.responseMessage}")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("OverpassAPI", "Error fetching data: ${e.localizedMessage}")
            }
        }.start()
    }

    private fun buildGraph(ways: List<Way>, nodes: Map<Long, Node>): Map<GeoPoint, MutableList<GeoPoint>> {
        val graph = mutableMapOf<GeoPoint, MutableList<GeoPoint>>()
        val thresholdDistance = 7.0  // Ez az érték azt határozza meg, milyen távolságra legyenek az új csomópontok.

        for (way in ways) {
            val geoPoints = way.nodes.mapNotNull { nodes[it] }.map { GeoPoint(it.lat, it.lon) }
            val simplifiedWay = simplifyWay(geoPoints, 0.000005)

            for (i in 0 until simplifiedWay.size - 1) {
                val startGeoPoint = simplifiedWay[i]
                val endGeoPoint = simplifiedWay[i + 1]

                graph.computeIfAbsent(startGeoPoint) { mutableListOf() }.add(endGeoPoint)
                graph.computeIfAbsent(endGeoPoint) { mutableListOf() }.add(startGeoPoint)
            }
        }

        connectIntersectingWays(graph, thresholdDistance)

        DataHolder.graph = graph
        Log.d("gps_app-mainactivity: - graph: ", DataHolder.graph.toString())

        return graph
    }

    // Csomópontok közötti távolság kiszámítása
    private fun distanceBetweenPoints(p1: GeoPoint, p2: GeoPoint): Double {
        val dLat = Math.toRadians(p2.lat - p1.lat)
        val dLon = Math.toRadians(p2.lon - p1.lon)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.lat)) * cos(Math.toRadians(p2.lat)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS * c
    }

    // Metszéspontok és kapcsolatok felismerése és létrehozása
    private fun connectIntersectingWays(graph: MutableMap<GeoPoint, MutableList<GeoPoint>>, threshold: Double) {
        val keys = graph.keys.toList()
        for (i in keys.indices) {
            for (j in i + 1 until keys.size) {
                if (distanceBetweenPoints(keys[i], keys[j]) < threshold) {
                    graph[keys[i]]?.add(keys[j])
                    graph[keys[j]]?.add(keys[i])
                }
            }
        }
    }

    private fun parseOverpassResponse(response: String): Pair<List<Way>, Map<Long, Node>> {
        val gson = Gson()
        val jsonResponse = gson.fromJson(response, JsonObject::class.java)

        val ways = jsonResponse.getAsJsonArray("elements").filter {
            it.asJsonObject["type"].asString == "way"
        }.map { gson.fromJson(it, Way::class.java) }

        val nodesMap = jsonResponse.getAsJsonArray("elements").filter {
            it.asJsonObject["type"].asString == "node"
        }.map { gson.fromJson(it, Node::class.java) }.associateBy { it.id }

        return Pair(ways, nodesMap)
    }

    private fun simplifyWay(way: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
        val vertices = way.map { CanvasView.Vertex(it.lat, it.lon) }
        val simplifiedVertices = douglasPeucker(vertices, epsilon.toFloat())
        return simplifiedVertices.map { GeoPoint(it.x, it.y) }
    }

    private fun douglasPeucker(vertices: List<CanvasView.Vertex>, epsilon: Float): List<CanvasView.Vertex> {
        if (vertices.size <= 2) return vertices

        val firstVertex = vertices.first()
        val lastVertex = vertices.last()

        var maxDistance = 0.0f
        var index = 0

        for (i in 1 until vertices.size - 1) {
            val distance = pointLineDistance(vertices[i], firstVertex, lastVertex)
            if (distance > maxDistance) {
                index = i
                maxDistance = distance.toFloat()
            }
        }

        if (maxDistance > epsilon) {
            val leftRecursive = douglasPeucker(vertices.subList(0, index + 1), epsilon)
            val rightRecursive = douglasPeucker(vertices.subList(index, vertices.size), epsilon)

            return leftRecursive.dropLast(1) + rightRecursive
        } else {
            return listOf(firstVertex, lastVertex)
        }
    }

    private fun pointLineDistance(point: CanvasView.Vertex, lineStart: CanvasView.Vertex, lineEnd: CanvasView.Vertex): Double {
        val numerator = abs((lineEnd.y - lineStart.y) * point.x - (lineEnd.x - lineStart.x) * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        val denominator = sqrt((lineEnd.y - lineStart.y).pow(2) + (lineEnd.x - lineStart.x).pow(2))
        return (numerator / denominator)
    }

    private fun hideMapUIElements() {
        mMap.setMultiTouchControls(false) // Elrejti a zoom gombokat
        mMap.overlays.remove(mMyLocationOverlay) // Elrejti a helyzetet jelölő ikont
        mMap.invalidate()
    }

    override fun onScroll(event: ScrollEvent?): Boolean {
        return true
    }

    override fun onZoom(event: ZoomEvent?): Boolean {
        return false
    }
}