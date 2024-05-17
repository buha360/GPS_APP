package com.wardanger3.gps_app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.wardanger3.gps_app.manuals.IntroductionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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
        var onGraphBuildCompleteListener: (() -> Unit)? = null
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
    private lateinit var mAdView : AdView

    object DataHolder {
        var mapZoomLevel: Double? = null
        var mapLatitude: Double? = null
        var mapLongitude: Double? = null
        var mapRotation by Delegates.notNull<Float>()
        var largeGraph: MutableMap<CanvasView.Vertex, MutableList<CanvasView.Vertex>>? = null
        var areaCentroid: CanvasView.Vertex? = null

        // Ez a függvény tisztítja meg a DataHolder adatokat
        fun clearData() {
            mapZoomLevel = null
            mapLatitude = null
            mapLongitude = null
            mapRotation = 0.0f
            largeGraph?.clear()
            areaCentroid = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPref = getSharedPreferences("MyAppPreferences", MODE_PRIVATE)
        val editor = sharedPref.edit()

        // Ellenőrizzük, hogy ez az első indítás-e
        if (sharedPref.getBoolean("firstTime", true)) {
            // A nyelvi beállítások alapján döntünk
            val currentLocale = Locale.getDefault()
            val languageCode = when (currentLocale.language) {
                "en" -> "en"
                "hu" -> "hu"
                else -> "en" // Alapértelmezett nyelv
            }

            // Első indítás esetén átnavigálunk az IntroductionActivity-re és átadjuk a nyelvi kódot
            val intent = Intent(this, IntroductionActivity::class.java).apply {
                putExtra("LanguageCode", languageCode)
            }

            startActivity(intent)

            editor.putBoolean("firstTime", false)
            editor.apply()
            return
        }

        setContentView(R.layout.layout)

        FirebaseApp.initializeApp(this)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences(getString(R.string.MapCanva), MODE_PRIVATE)
        )

        mMap = findViewById(R.id.osmmap)
        mMap.setTileSource(TileSourceFactory.MAPNIK)
        mMap.setMultiTouchControls(true)
        mMap.getLocalVisibleRect(Rect())

        // Alapértelmezett helyzet beállítása Európa középpontjára
        controller = mMap.controller
        val defaultLocation = org.osmdroid.util.GeoPoint(50.0, 15.0) // Európa középpontja
        mMap.setExpectedCenter(defaultLocation)
        mMap.controller.setZoom(4.5) // 4.5-ös zoom szint

        // Zoom vezérlők kikapcsolása
        mMap.setBuiltInZoomControls(false)

        // Létrehozás és beállítás
        mRotationGestureOverlay = RotationGestureOverlay(mMap)
        mRotationGestureOverlay.isEnabled = true

        // Hozzáadás a MapView-hoz
        mMap.overlays.add(mRotationGestureOverlay)

        mMap.addMapListener(this)

        // Teljes képernyős mód beállítása + navigációs sáv elrejtése
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        MobileAds.initialize(this) {}
        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)

        loadBannerAd()

        var selectedMode: String? = null
        val button: Button = findViewById(R.id.button)
        val buttonComplex: Button = findViewById(R.id.button_nextComplex)
        val buttonSimple: Button = findViewById(R.id.button_nextSimple)

        button.isEnabled = true

        // Zoom események kezelése
        mMap.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val zoomLevel = mMap.zoomLevelDouble
                updateSaveButtonState(button, zoomLevel, selectedMode)
                return true
            }
        })

        var counterComplex = 0
        buttonComplex.setOnClickListener {
            counterComplex++
            selectedMode = "complex"
            if(counterComplex % 2 == 1){
                buttonComplex.background = ContextCompat.getDrawable(this, R.drawable.custom_button_green)
                buttonComplex.setTextColor(Color.parseColor("#00ff7b"))
                buttonSimple.isEnabled = false
                if(isZoomLevelAppropriate(selectedMode!!, mMap.zoomLevelDouble)){
                    button.isEnabled = true
                    button.background = ContextCompat.getDrawable(this, R.drawable.custom_button_green)
                    button.setTextColor(Color.parseColor("#00ff7b"))
                }
            }else{
                selectedMode = null
                buttonComplex.background = ContextCompat.getDrawable(this, R.drawable.custom_button_red)
                buttonComplex.setTextColor(Color.parseColor("#ff0000"))
                buttonSimple.isEnabled = true
                button.isEnabled = false
                button.background = ContextCompat.getDrawable(this, R.drawable.custom_button_red)
                button.setTextColor(Color.parseColor("#ff0000"))
            }
        }

        var counterSimple = 0
        buttonSimple.setOnClickListener {
            counterSimple++
            selectedMode = "simple"
            if(counterSimple % 2 == 1){
                buttonSimple.background = ContextCompat.getDrawable(this, R.drawable.custom_button_green)
                buttonSimple.setTextColor(Color.parseColor("#00ff7b"))
                buttonComplex.isEnabled = false
                if(isZoomLevelAppropriate(selectedMode!!, mMap.zoomLevelDouble)){
                    button.isEnabled = true
                    button.background = ContextCompat.getDrawable(this, R.drawable.custom_button_green)
                    button.setTextColor(Color.parseColor("#00ff7b"))
                }
            }else{
                selectedMode = null
                buttonSimple.background = ContextCompat.getDrawable(this, R.drawable.custom_button_red)
                buttonSimple.setTextColor(Color.parseColor("#ff0000"))
                buttonComplex.isEnabled = true
                button.isEnabled = false
                button.background = ContextCompat.getDrawable(this, R.drawable.custom_button_red)
                button.setTextColor(Color.parseColor("#ff0000"))
            }
        }

        button.setOnClickListener {
            button.isEnabled = selectedMode != null

            if ((selectedMode != null) && isZoomLevelAppropriate(selectedMode!!, mMap.zoomLevelDouble)) {
                // A zoom szint megfelelő, folytatjuk a műveleteket
                button.isEnabled = false
                button.background = ContextCompat.getDrawable(this, R.drawable.custom_button_green)
                button.setTextColor(Color.parseColor("#00ff7b"))

                CoroutineScope(Dispatchers.IO).launch {
                    val areaCentroid = calculateVisibleAreaCentroid()
                    DataHolder.areaCentroid = areaCentroid

                    // az aktuális elforgatási szög
                    DataHolder.mapRotation = mMap.mapOrientation

                    saveMapAsImage()
                    fetchRoadsFromOverpass()

                    DataHolder.mapZoomLevel = mMap.zoomLevelDouble
                    DataHolder.mapLatitude = mMap.mapCenter.latitude
                    DataHolder.mapLongitude = mMap.mapCenter.longitude

                    if (selectedMode == "simple") {
                        val intent = Intent(this@MainActivity, DrawingActivity::class.java)
                        startActivity(intent)
                    } else if (selectedMode == "complex") {
                        val intent = Intent(this@MainActivity, DrawingActivitySC::class.java)
                        startActivity(intent)
                    }
                }
            } else {
                // Check for toast if no mode selected and button disabled
                if (selectedMode == null && !button.isEnabled) {
                    val toast = Toast.makeText(this, "Pick a mode and zoom further!", Toast.LENGTH_SHORT)
                    toast.show()
                }
            }
        }

        // Ellenőrizzük, hogy a rajzolós oldal első indítása-e ez
        if (sharedPref.getBoolean("firstTimeDrawingPage", true)) {
            AlertDialog.Builder(this)
                .setTitle("Choose a mode!")
                .setMessage("Choose between the complex or simple modes! Simple mode offers an easy, immediate placement and is the easiest to use. Complex mode involves a sophisticated algorithm that is time-consuming. After selecting, zoom into a section of the road that appeals to you until the go button turns green in the center.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                }
                .show()

            // Jelöljük, hogy már a rajzolós oldalt is megnyitották először
            editor.putBoolean("firstTimeDrawingPage", false)
            editor.apply()
        }
    }

    private fun loadBannerAd() {
        MobileAds.initialize(this) {}
        mAdView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        mAdView.loadAd(adRequest)
    }

    private fun updateSaveButtonState(button: Button, zoomLevel: Double, selectedMode: String?) {
        val isAppropriateZoomLevel = selectedMode != null && isZoomLevelAppropriate(selectedMode, zoomLevel)

        if (isAppropriateZoomLevel) {
            button.background = ContextCompat.getDrawable(this, R.drawable.custom_button_green)
            button.setTextColor(Color.parseColor("#00ff7b"))
        } else {
            button.background = ContextCompat.getDrawable(this, R.drawable.custom_button_red)
            button.setTextColor(Color.parseColor("#ff0000"))
        }
    }

    private fun isZoomLevelAppropriate(mode: String, zoomLevel: Double): Boolean {
        return when (mode) {
            "complex" -> zoomLevel >= 14.4
            "simple" -> zoomLevel >= 14.4
            else -> false
        }
    }

    private fun fetchRoadsFromOverpass() {
        val boundingBox = mMap.boundingBox
        val overpassQuery = """
    [out:json];
    (
        way["highway"](${boundingBox.latSouth},${boundingBox.lonWest},${boundingBox.latNorth},${boundingBox.lonEast});
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

        runOnUiThread {
            onGraphBuildCompleteListener?.invoke()
        }
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
        val segmentLength = 15.0 // 15 méteres szegmensekre osztás
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
                    Toast.makeText(this, "Screen saved", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Failed to save the screen", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}