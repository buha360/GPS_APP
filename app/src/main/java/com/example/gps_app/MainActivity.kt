package com.example.gps_app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import kotlin.properties.Delegates

class MainActivity : FragmentActivity(), MapListener {

    data class Vertex(val lat: Double, val lon: Double)

    class Graph {
        val adjacencyList = mutableMapOf<Vertex, MutableList<Edge>>()

        fun addEdge(start: Vertex, end: Vertex, weight: Double) {
            adjacencyList.computeIfAbsent(start) { mutableListOf() }.add(Edge(end, weight))
        }
    }

    data class Edge(val vertex: Vertex, val weight: Double)

    data class GeoPoint(val lat: Double, val lon: Double)

    companion object {
        const val EARTH_RADIUS = 6371000 // A Föld sugara méterben
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

    private lateinit var mRotationGestureOverlay: RotationGestureOverlay
    private lateinit var mMap: MapView
    private lateinit var controller: IMapController
    private lateinit var mMyLocationOverlay: MyLocationNewOverlay

    object DataHolder {
        var mapZoomLevel: Double? = null
        var mapLatitude: Double? = null
        var mapLongitude: Double? = null
        var mapRotation by Delegates.notNull<Float>()
        var largeGraph: MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>>? = null
        var areaCentroid: CanvasView.Vertex? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences(getString(R.string.MapCanva), MODE_PRIVATE)
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

        // Létrehozás és beállítás
        mRotationGestureOverlay = RotationGestureOverlay(mMap)
        mRotationGestureOverlay.isEnabled = true

        // Hozzáadás a MapView-hoz
        mMap.overlays.add(mRotationGestureOverlay)

        controller.setZoom(6.0)
        mMap.overlays.add(mMyLocationOverlay)
        mMap.addMapListener(this)

        val buttonNext: Button = findViewById(R.id.button_next)
        buttonNext.setOnClickListener {
            val areaCentroid = calculateVisibleAreaCentroid()
            DataHolder.areaCentroid = areaCentroid

            // az aktuális elforgatási szög
            DataHolder.mapRotation = mMap.mapOrientation

            saveMapAsImage()
            fetchRoadsFromOverpass()

            DataHolder.mapZoomLevel = mMap.zoomLevelDouble
            DataHolder.mapLatitude = mMap.mapCenter.latitude
            DataHolder.mapLongitude = mMap.mapCenter.longitude

            Log.d("MapCanva-MainActivity", "Graph: ${DataHolder.largeGraph}")

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

                    Log.d("OverpassAPI", "Response: $result")

                    val (ways, nodes) = parseOverpassResponse(result)
                    buildGraph(ways, nodes)
                } else {
                    Log.e("OverpassAPI", "Error: ${connection.responseCode} - ${connection.responseMessage}")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("OverpassAPI", "Error fetching data: ${e.localizedMessage}")
            }
        }.start()
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

    private fun buildGraph(ways: List<Way>, nodes: Map<Long, Node>) {
        val graph = Graph()
        ways.forEach { way ->
            val wayVertices = way.nodes.mapNotNull { nodes[it]?.let { node -> Vertex(node.lat, node.lon) } }
            val subdividedWay = subdivideWay(wayVertices)
            for (i in 0 until subdividedWay.size - 1) {
                val start = subdividedWay[i]
                val end = subdividedWay[i + 1]
                val weight = calculateDistance(start, end)
                graph.addEdge(start, end, weight)
                graph.addEdge(end, start, weight) // Ha a gráf irányítatlan
            }
        }

        var screenGraph = convertToScreenCoordinates(graph)

        Log.d("rotationAngle","${DataHolder.mapRotation}")
        screenGraph = rotateGraphCoordinates(screenGraph, DataHolder.mapRotation, mMap)

        DataHolder.largeGraph = screenGraph
    }

    private fun rotateGraphCoordinates(graph: MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>>, rotationAngle: Float, mapView: MapView): MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>> {
        val rotatedGraph = mutableMapOf<CanvasView.Vertex, MutableList<CanvasView.Vertex>>()
        val mapCenter = mapView.mapCenter
        val centerPoint = mapView.projection.toPixels(org.osmdroid.util.GeoPoint(mapCenter.latitude, mapCenter.longitude), null)

        graph.forEach { (vertex, neighbors) ->
            val rotatedVertex = rotatePoint(vertex, centerPoint, rotationAngle)
            val rotatedNeighbors = neighbors.map { rotatePoint(it, centerPoint, rotationAngle) }.toMutableList()
            rotatedGraph[rotatedVertex] = rotatedNeighbors
        }

        return rotatedGraph
    }

    private fun rotatePoint(point: CanvasView.Vertex, pivot: Point, angle: Float): CanvasView.Vertex {
        val rad = Math.toRadians(angle.toDouble())
        val sin = sin(rad)
        val cos = cos(rad)

        // Eltoljuk a pontot a forgatási középpontig
        var x = point.x - pivot.x
        var y = point.y - pivot.y

        // Elforgatjuk a pontot
        val newX = x * cos - y * sin
        val newY = x * sin + y * cos

        // Visszatoljuk a forgatott pontot az eredeti helyére
        x = newX + pivot.x
        y = newY + pivot.y

        return CanvasView.Vertex(x, y)
    }

    private fun subdivideWay(nodes: List<Vertex>): List<Vertex> {
        val segmentLength = 10.0 // 10 méteres szegmensekre osztás
        val subdividedWay = mutableListOf<Vertex>()
        for (i in 0 until nodes.size - 1) {
            val start = nodes[i]
            val end = nodes[i + 1]
            subdividedWay.add(start)

            val distance = calculateDistance(start, end)
            val numberOfSegments = (distance / segmentLength).toInt()

            for (j in 1 until numberOfSegments) {
                val ratio = j / numberOfSegments.toDouble()
                val intermediatePoint = interpolate(start, end, ratio)
                subdividedWay.add(intermediatePoint)
            }
        }
        subdividedWay.add(nodes.last())
        return subdividedWay
    }

    private fun calculateDistance(start: Vertex, end: Vertex): Double {
        val latDistance = Math.toRadians(end.lat - start.lat)
        val lonDistance = Math.toRadians(end.lon - start.lon)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(start.lat)) * cos(Math.toRadians(end.lat)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS * c
    }

    private fun interpolate(start: Vertex, end: Vertex, ratio: Double): Vertex {
        val lat = start.lat + ratio * (end.lat - start.lat)
        val lon = start.lon + ratio * (end.lon - start.lon)
        return Vertex(lat, lon)
    }

    private fun convertToScreenCoordinates(graph: Graph): MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>> {
        val screenGraph = mutableMapOf<CanvasView.Vertex, MutableList<CanvasView.Vertex>>()
        graph.adjacencyList.forEach { (vertex, edges) ->
            val screenVertex = convertGeoPointToScreenCoordinates(GeoPoint(vertex.lat, vertex.lon))
            edges.forEach { edge ->
                val screenEdgeVertex = convertGeoPointToScreenCoordinates(GeoPoint(edge.vertex.lat, edge.vertex.lon))
                screenGraph.computeIfAbsent(screenVertex) { mutableListOf() }.add(screenEdgeVertex)
            }
        }
        return screenGraph
    }

    private fun convertGeoPointToScreenCoordinates(geoPoint: GeoPoint): CanvasView.Vertex {
        val point = mMap.projection.toPixels(org.osmdroid.util.GeoPoint(geoPoint.lat, geoPoint.lon), null)
        return CanvasView.Vertex(point.x.toDouble(), point.y.toDouble())
    }

    override fun onScroll(event: ScrollEvent?): Boolean {
        return true
    }

    override fun onZoom(event: ZoomEvent?): Boolean {
        return false
    }

    private fun calculateVisibleAreaCentroid(): CanvasView.Vertex {
        val visibleArea = getVisibleAreaRect()
        val visibleVertices = DataHolder.largeGraph?.keys?.filter {
            it.x >= visibleArea.left && it.x <= visibleArea.right &&
                    it.y >= visibleArea.top && it.y <= visibleArea.bottom
        } ?: emptyList()

        val sumX = visibleVertices.sumOf { it.x }
        val sumY = visibleVertices.sumOf { it.y }
        return CanvasView.Vertex(sumX / visibleVertices.size, sumY / visibleVertices.size)
    }

    private fun getVisibleAreaRect(): Rect {
        val visibleBoundingBox = mMap.boundingBox
        val topLeftPoint = mMap.projection.toPixels(org.osmdroid.util.GeoPoint(visibleBoundingBox.latNorth, visibleBoundingBox.lonWest), null)
        val bottomRightPoint = mMap.projection.toPixels(org.osmdroid.util.GeoPoint(visibleBoundingBox.latSouth, visibleBoundingBox.lonEast), null)
        return Rect(topLeftPoint.x, topLeftPoint.y, bottomRightPoint.x, bottomRightPoint.y)
    }

    private fun saveMapAsImage() {
        mMap.post {
            val snapshot = Bitmap.createBitmap(mMap.width, mMap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(snapshot)
            mMap.draw(canvas)

            val directory = getExternalFilesDir(null)?.absolutePath + "/gps_app"
            val fileDir = File(directory)
            if (!fileDir.exists()) {
                fileDir.mkdirs()
            }

            val fileName = "map_snapshot.png"
            val file = File(fileDir, fileName)
            try {
                val outputStream = FileOutputStream(file)
                snapshot.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                outputStream.close()
                runOnUiThread {
                    Toast.makeText(this, "Sikerült", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Nem sikerült", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}