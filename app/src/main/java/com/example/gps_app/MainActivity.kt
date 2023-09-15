package com.example.gps_app

import java.io.File
import org.opencv.core.Core
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.caverock.androidsvg.SVG
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : FragmentActivity(), OnMapReadyCallback {

    private var mGoogleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LocationPermissionRequest = 1001
    private val folderName = "gps_app"

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
        - sikerült kiszedni mindkét átalakított képről a fekete - fehér koordinátákat
        - a drawing svg átalakítást meg kéne nézni, mert mindig a régebbi svg-t alakítja át, sose az új van!
        - a fehér pixelek alapján pathdfindingnak kiiundulási pontok létrehozása!
        - már csak akkor egy pathfinding algoritmust kell implementálni, ami flexibilisen tudja adjustolni a koordinátákat
            a térkép részletre (forgatás, kicsinyítés, nagyítás és egyes koordináták megváltoztatásával!)
        - HA ezek megvannak, akkor még 1 oldal beszúrása, amin betöltöm a kész képet és CSŐ
        - AZTÁN A VÉGÉN ÖSSZEVETHETEM AZ ELKÉSZÜLT RAJZOT AZ EREDITEL ÉS %-OSAN KIÍRNI AZ EGYEZÉST!
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

        // Törlés, ha létezik
        if (imagePath.exists()) {
            imagePath.delete()
        }

        imagePath.bufferedWriter().use { writer ->
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1100\" height=\"740\">\n")
            for (contour in contours) {
                val points = ArrayList<Point>(contour.toList())
                writer.write("<path d=\"M${points[0].x},${points[0].y} ")
                for (i in 1 until points.size) {
                    writer.write("L${points[i].x},${points[i].y} ")
                }
                writer.write("Z\" fill=\"black\" stroke=\"black\" />\n")
            }
            writer.write("</svg>")
            writer.close()
        }
        val svgFilePath = File(context.getExternalFilesDir(null), "gps_app/map_snapshot.svg").path
        val pngFilePath = File(context.getExternalFilesDir(null), "gps_app/map_converted.png").path
        val svgDrawing = File(context.getExternalFilesDir(null), "gps_app/drawing.svg").path
        val pngDrawing = File(context.getExternalFilesDir(null), "gps_app/drawing.png").path

        convertSvgToPngExternal(svgFilePath, pngFilePath)
        convertSvgToPngExternal(svgDrawing, pngDrawing)
    }

    fun findWhitePixelsCoordinates(pngFilePath: String): List<android.graphics.Point> {
        // Betöltés Mat objektumba
        val image = Imgcodecs.imread(pngFilePath) // Színes kép beolvasása

        // Fehér pixelek kinyerése
        val whitePixels = mutableListOf<android.graphics.Point>()

        for (x in 0 until image.cols()) {
            for (y in 0 until image.rows()) {
                val pixel = image.get(y, x)
                val b = pixel[0].toDouble()
                val g = pixel[1].toDouble()
                val r = pixel[2].toDouble()

                // A pixel csak akkor számít fehérnek, ha az összes színkomponens értéke közel van a maximálishoz (255)
                if (b > 200.0 && g > 200.0 && r > 200.0) {
                    whitePixels.add(android.graphics.Point(x, y))
                }
            }
        }

        return whitePixels
    }

    fun findBlackPixelsCoordinates(pngFilePath: String): List<android.graphics.Point> {
        // Betöltés Mat objektumba
        val image = Imgcodecs.imread(pngFilePath) // Színes kép beolvasása

        // Fekete pixelek kinyerése
        val blackPixels = mutableListOf<android.graphics.Point>()

        for (x in 0 until image.cols()) {
            for (y in 0 until image.rows()) {
                val pixel = image.get(y, x)
                val b = pixel[0].toDouble()
                val g = pixel[1].toDouble()
                val r = pixel[2].toDouble()

                // A pixel csak akkor számít fekete, ha az összes színkomponens értéke közel van a minimális (0)
                if (b < 200.0 && g < 200.0 && r < 200.0) {
                    blackPixels.add(android.graphics.Point(x, y))
                }
            }
        }

        return blackPixels
    }

    private fun convertSvgToPngExternal(svgFileName: String, pngFileName: String) {
        val svgFile = File(svgFileName)
        val svg = SVG.getFromInputStream(svgFile.inputStream())

        val bitmap = Bitmap.createBitmap(
            svg.documentWidth.toInt(),
            svg.documentHeight.toInt(),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE) // Beállítjuk a háttérszínt fehérre (tehát az utak lesznek fehérek)

        svg.renderToCanvas(canvas)

        val pngFile = File(pngFileName)
        pngFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private var instance: MainActivity? = null

        fun getInstance(): MainActivity? {
            return instance
        }
    }
}