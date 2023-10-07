package com.example.gps_app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.http.ContentDisposition.Companion.File
import org.opencv.android.OpenCVLoader
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : FragmentActivity(), MapListener {

    data class GeoPoint(val lat: Double, val lon: Double)

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
            saveCurrentMapView()
            DataHolder.mapZoomLevel = mMap.zoomLevelDouble
            DataHolder.mapLatitude = mMap.mapCenter.latitude
            DataHolder.mapLongitude = mMap.mapCenter.longitude

            val intent = Intent(this, DrawingActivity::class.java)
            startActivity(intent)
        }
    }

    private fun saveCurrentMapView() {
        OpenCVLoader.initDebug()
        val snapshot: Bitmap = Bitmap.createBitmap(mMap.width, mMap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(snapshot)
        mMap.draw(canvas)

        // Az alkalmazás specifikus könyvtár meghatározása
        val externalFilesDir = getExternalFilesDir(null)
        val storageDirectory = File(externalFilesDir, "gps_app")

        // Ha a könyvtár még nem létezik, hozd létre
        if (!storageDirectory.exists()) {
            storageDirectory.mkdirs()
        }

        // Fájl létrehozása a könyvtárban
        val fileToSave = File(storageDirectory, "map_snapshot.png")

        try {
            val out = FileOutputStream(fileToSave)
            snapshot.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Toast.makeText(this, "Map saved to ${fileToSave.absolutePath}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchRoadsFromOverpass() {
        val boundingBox = mMap.boundingBox
        val overpassQuery = """
            [out:json];
            (
                way["highway"](${boundingBox.latSouth},${boundingBox.lonWest},${boundingBox.latNorth},${boundingBox.lonEast});
                relation["highway"](${boundingBox.latSouth},${boundingBox.lonWest},${boundingBox.latNorth},${boundingBox.lonEast});
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

        for (way in ways) {
            for (i in 0 until way.nodes.size - 1) {
                val startPoint = nodes[way.nodes[i]] ?: continue
                val endPoint = nodes[way.nodes[i + 1]] ?: continue

                val startGeoPoint = GeoPoint(startPoint.lat, startPoint.lon)
                val endGeoPoint = GeoPoint(endPoint.lat, endPoint.lon)

                graph.computeIfAbsent(startGeoPoint) { mutableListOf() }.add(endGeoPoint)
                graph.computeIfAbsent(endGeoPoint) { mutableListOf() }.add(startGeoPoint)
            }
        }

        DataHolder.graph = graph
        Log.d("gps_app-mainactivity: - graph: ", DataHolder.graph.toString())

        return graph
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