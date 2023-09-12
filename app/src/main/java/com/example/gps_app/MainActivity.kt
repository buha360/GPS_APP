package com.example.gps_app

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.File
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.PolylineOptions
import org.opencv.android.OpenCVLoader
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import java.io.FileOutputStream
import java.io.IOException
import java.util.regex.Pattern
import kotlin.math.pow

class MainActivity : FragmentActivity(), OnMapReadyCallback {

    private var mGoogleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LocationPermissionRequest = 1001
    private val folderName = "gps_app"
    private val MIN_DISTANCE_THRESHOLD = 5.0 // Az ütközés minimális küszöbértéke

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)
        instance = this

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val nextBtn = findViewById<Button>(R.id.button_next)
        nextBtn.setOnClickListener {
            captureAndSaveMapSnapshot(this) { success ->
                if (success) {
                    val intent = Intent(this, DrawingActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        mGoogleMap?.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))

        // Engedélyek ellenőrzése és helyzetlekérdezés
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            mGoogleMap!!.isMyLocationEnabled = true

            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        mGoogleMap!!.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 10f))
                    }
                }
        } else {
            // Engedélyek hiányában engedélykérés indítása
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LocationPermissionRequest)
        }

        // Custom útvonal rajzolása
        val polylineOptions = PolylineOptions()
            .add(LatLng(47.12345, 19.56789))
            .add(LatLng(47.23456, 19.67890))
            .add(LatLng(47.34456, 19.98890))
            .add(LatLng(47.65456, 19.31290))
            .color(Color.BLUE)
            .width(5f)
        mGoogleMap!!.addPolyline(polylineOptions)
    }

    private fun captureAndSaveMapSnapshot(context: Context, callback: (success: Boolean) -> Unit) {
        mGoogleMap?.snapshot { bitmap ->
            if (bitmap != null) {
                val folder = File(context.getExternalFilesDir(null), folderName)
                if (!folder.exists()) {
                    folder.mkdirs()
                }

                val imagePath = File(folder, "map_snapshot.png")
                val outputStream = FileOutputStream(imagePath)

                try {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                    outputStream.close()
                    callback(true) // Sikeres mentés
                    convertSnapshotImageOpenCV(this)
                } catch (e: IOException) {
                    e.printStackTrace()
                    callback(false) // Mentés sikertelen
                }
            } else {
                callback(false) // Snapshot hiányzik
            }
        }
    }

    /*
        - elkezdtem a kimentést, de egy elvágott képet ad vissza
        ? meg kéne adni kezdőpontokat, ahonnét elkezdheti a path létrehozását vagy
            valamit ki kéne találni erre
    */

    private fun convertSnapshotImageOpenCV(context: Context) {
        OpenCVLoader.initDebug()

        val imagePath = File(context.getExternalFilesDir(null), "gps_app/map_snapshot.png")

        // Betöltés Mat objektumba
        val image = Imgcodecs.imread(imagePath.absolutePath, Imgcodecs.IMREAD_GRAYSCALE) // Átalakítjuk szürkeárnyalatos képpé

        // Az alsó rész levágása
        val cropHeight = image.rows() / 9 // Alsó 9%-át levágni a google logo miatt
        val croppedImage = image.submat(0, image.rows() - cropHeight, 0, image.cols())

        // Csak a fekete vonalak tartása meg
        val result = Mat()
        Core.inRange(croppedImage, Scalar(0.0), Scalar(10.0), result) // A 0.0 és 100.0 határok az értékfüggőek lehetnek, módosítsd szükség szerint

        val contours = ArrayList<MatOfPoint>()
        Imgproc.adaptiveThreshold(croppedImage, result, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 25, 12.0)
        Imgproc.findContours(result, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val svgFile = File(context.getExternalFilesDir(null), "gps_app/map_snapshot.svg")

        // Törlés, ha létezik
        if (svgFile.exists()) {
            svgFile.delete()
        }

        svgFile.bufferedWriter().use { writer ->
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$image.cols()\" height=\"$image.rows()\">\n")
            for (contour in contours) {
                val points = ArrayList<Point>(contour.toList())
                writer.write("<path d=\"M${points[0].x},${points[0].y} ")
                for (i in 1 until points.size) {
                    writer.write("L${points[i].x},${points[i].y} ")
                }
                writer.write("Z\" fill=\"black\" stroke=\"black\" />\n")
            }
            writer.write("</svg>")
        }

        val svgFilePath = "map_snapshot_converted.svg"
        svgFile.copyTo(File(svgFilePath), overwrite = true)
    }

    private fun rotatePath(pathElement: Element, maxRotation: Double) {
        // A "transform" attribútumot módosítjuk úgy, hogy a maximális elfordulásig forgassuk el
        val currentTransform = pathElement.attr("transform")
        val modifiedTransform = rotateTransform(currentTransform, maxRotation)
        pathElement.attr("transform", modifiedTransform)
    }

    private fun scalePath(pathElement: Element, scaleFactor: Double) {
        // Az "d" attribútum értékeket módosítjuk a scaleFactor-rel
        val dAttribute = pathElement.attr("d")
        val modifiedDAttribute = scaleDAttribute(dAttribute, scaleFactor)
        pathElement.attr("d", modifiedDAttribute)
    }

    private fun scaleDAttribute(dAttribute: String, scaleFactor: Double): String {
        val pathDataSegments = dAttribute.split("\\s+")
        val scaledSegments = mutableListOf<String>()

        for (segment in pathDataSegments) {
            val matcher = Pattern.compile("([MmLlCcSsZzAaHhVvQqTt])([0-9.-]+)").matcher(segment)

            while (matcher.find()) {
                val command = matcher.group(1)
                val values = matcher.group(2)!!.split(",")

                val scaledValues = values.map { (it.toDouble() * scaleFactor).toString() }

                val scaledSegment = StringBuilder(command!!)
                for (scaledValue in scaledValues) {
                    scaledSegment.append(",").append(scaledValue)
                }

                scaledSegments.add(scaledSegment.toString())
            }
        }

        return scaledSegments.joinToString(" ")
    }

    private fun rotateTransform(currentTransform: String, maxRotation: Double): String {
        val matcher = Pattern.compile("rotate\\(([^)]+)\\)").matcher(currentTransform)
        if (matcher.find()) {
            val currentRotation = matcher.group(1)!!.toDouble()
            val newRotation = if (currentRotation > maxRotation) maxRotation else currentRotation
            return "rotate($newRotation)"
        }

        return currentTransform
    }

    private fun modifyPathsInSVG(svgDocument1: Element?): List<Element> {
        val svgElements1: Elements = svgDocument1!!.select("svg")
        if (!svgElements1.isEmpty()) {
            val svgElement1: Element? = svgElements1.first()
            val pathElements: Elements = svgElement1?.select("path") ?: Elements() // Üres lista inicializálása, ha a select null

            val modifiedPaths = mutableListOf<Element>()
            val originalPaths = mutableListOf<Element>() // Ebben a listában tároljuk az eredeti útvonalakat

            for (pathElement in pathElements) {
                val random = java.util.Random()

                // Példa: Méretkorlátozás
                val scale = 0.9 + random.nextDouble() * 0.2 // 0.9-től 1.1-ig terjedő méretezés
                scalePath(pathElement, scale)

                // Példa: Forgatási szög korlátozás
                val maxRotation = 12.5 // Maximális elfordulás 12.5 fok
                rotatePath(pathElement, random.nextDouble() * maxRotation) // Véletlenszerű elfordulás 0-12.5 fok között

                modifiedPaths.add(pathElement)
                originalPaths.add(pathElement.clone()) // Klónozás az eredeti útvonalak tárolásához
            }

            // Az eredeti útvonalakat is visszaadjuk
            modifiedPaths.addAll(originalPaths)

            return modifiedPaths
        }

        return emptyList()
    }

    fun readSVGFiles(context: Context) {
        // SVG fájlok beolvasása és elemezése
        val svgFile1 = File(context.getExternalFilesDir(null),"gps_app/drawing.svg")
        val svgFile2 = File(context.getExternalFilesDir(null),"gps_app/map_snapshot.svg")

        val svgDocument1: Document = Jsoup.parse(svgFile1, "UTF-8")
        val svgDocument2: Document = Jsoup.parse(svgFile2, "UTF-8")

        // Az első SVG fájl vonalainak módosítása
        val modifiedPaths = modifyPathsInSVG(svgDocument1).toMutableList()
        val originalPaths = mutableListOf<Element>() // Az eredeti útvonalak tárolása

        // Ellenőrzés, hogy a módosított vonalak ütköznek-e a második SVG fájlban található adatokkal
        val collisionDetected = checkForCollisions(modifiedPaths, svgDocument2)

        // Ha van ütközés
        if (collisionDetected) {
            // Visszalépés az előző vonalhoz és újramódosítás
            val maxAttempts = 20 // Maximális újramódosítások száma

            for (pathElement in modifiedPaths) {
                var attempts = 0
                var modified = false

                while (attempts < maxAttempts && !modified) {
                    // Módosítás az aktuális vonalon
                    val originalPath = originalPaths.firstOrNull { it == pathElement }
                    if (originalPath != null) {
                        val modifiedPathList = modifyPathsInSVG(originalPath)
                        modifiedPaths.addAll(modifiedPathList)
                    }

                    // Ellenőrzés az ütközésekkel
                    val collisionDetectedInRetry = checkForCollisions(modifiedPaths, svgDocument2)

                    if (!collisionDetectedInRetry) {
                        // Sikeres módosítás esetén kilépés a ciklusból
                        modified = true
                    } else {
                        // Sikertelen módosítás esetén visszalépés az előző vonalhoz
                        if (originalPath != null) {
                            modifiedPaths[modifiedPaths.indexOf(originalPath)] = originalPath
                        }
                        attempts++
                    }
                }
            }
        }

        // Módosított vonalak hozzáadása a második SVG fájlhoz
        addModifiedPathsToSVG(svgDocument2, modifiedPaths)

        // A módosított tartalom mentése egy új SVG fájlba
        try {
            val modifiedSVGFile = File(context.getExternalFilesDir(null), "gps_app/modified_map.svg")
            modifiedSVGFile.writeText(svgDocument2.outerHtml(), Charsets.UTF_8)
        }catch (e: IOException){
            e.printStackTrace()
            showToast(context, "nem jó he") // Toast üzenet hozzáadása
        }

    }

    private fun addModifiedPathsToSVG(svgDocument: Document, modifiedPaths: List<Element>) {
        // Ebben a függvényben hozzáadhatod a módosított útvonalakat a második SVG dokumentumhoz
        val svgElement: Element? = svgDocument.select("svg").firstOrNull()
        if (svgElement != null) {
            for (pathElement in modifiedPaths) {
                svgElement.appendChild(pathElement)
            }
        }
    }

    private fun checkForCollisions(modifiedPaths: List<Element>, svgDocument2: Document): Boolean {
        val svgElements2: Elements = svgDocument2.select("svg")
        if (!svgElements2.isEmpty()) {
            val svgElement2: Element? = svgElements2.first()
            val pathElements2: Elements = svgElement2?.select("path") ?: return false // Ha nincsenek path elemek, nincs ütközés sem

            // Iterálj az összes módosított útvonalon
            for (modifiedPath in modifiedPaths) {
                val modifiedDAttribute = modifiedPath.attr("d")

                // Ellenőrizd az ütközést minden második SVG útvonallal
                for (pathElement2 in pathElements2) {
                    val dAttribute2 = pathElement2.attr("d")

                    // Itt írd meg az ütközés ellenőrzését a modifiedDAttribute és dAttribute2 alapján
                    if (checkCollision(modifiedDAttribute, dAttribute2)) {
                        return true // Ütközés esetén térj vissza true értékkel
                    }
                }
            }
        }

        // Ha nem találtál ütközést, térj vissza false értékkel
        return false
    }

    private fun checkCollision(dAttribute1: String, dAttribute2: String): Boolean {
        // Az útvonalakból pontokat kinyerni
        val points1 = extractPointsFromDAttribute(dAttribute1)
        val points2 = extractPointsFromDAttribute(dAttribute2)

        // Ellenőrizni, hogy van-e olyan pont a két útvonalon,
        // ami túl közel van egymáshoz, és ha igen, akkor ütközés van
        for (point1 in points1) {
            for (point2 in points2) {
                val distance = calculateDistance(point1, point2)
                if (distance < MIN_DISTANCE_THRESHOLD) {
                    return true // Ütközés esetén térjünk vissza true értékkel
                }
            }
        }

        return false // Ha nincs ütközés, térjünk vissza false-szal
    }

    private fun extractPointsFromDAttribute(dAttribute: String): List<Point> {
        // Az "M" és "L" parancsokból származó pontok kinyerése
        val points = mutableListOf<Point>()
        val matcher = Pattern.compile("[ML]([0-9.-]+),([0-9.-]+)").matcher(dAttribute)
        while (matcher.find()) {
            val x = matcher.group(1)!!.toDouble()
            val y = matcher.group(2)!!.toDouble()
            points.add(Point(x, y))
        }
        return points
    }

    private fun calculateDistance(point1: Point, point2: Point): Double {
        // Két pont közötti távolság számítása
        return kotlin.math.sqrt((point1.x - point2.x).pow(2) + (point1.y - point2.y).pow(2))
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private var instance: MainActivity? = null

        fun getInstance(): MainActivity? {
            return instance
        }
    }
}