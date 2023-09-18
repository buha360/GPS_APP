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
import com.google.android.gms.maps.model.PolylineOptions
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
        val cropHeight = image.rows() / 9 // Alsó 9%-át levágni a Google logó miatt
        val croppedImage = image.submat(0, image.rows() - cropHeight, 0, image.cols())

        // Csak a fekete vonalak tartása meg
        val result = Mat()
        Core.inRange(croppedImage, Scalar(0.0), Scalar(10.0), result) // A 0.0 és 100.0 határok az értékfüggőek lehetnek, módosítsd szükség szerint

        val contours = ArrayList<MatOfPoint>()
        Imgproc.adaptiveThreshold(croppedImage, result, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 25, 12.0)
        Imgproc.findContours(result, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        // PNG létrehozása közvetlenül SVG-ből
        val pngFilePath = "gps_app/map_snapshot_converted.png"
        val bitmap = Bitmap.createBitmap(
            croppedImage.cols(),
            croppedImage.rows(),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE) // Beállítjuk a háttérszínt fehérre

        for (contour in contours) {
            val points = ArrayList<Point>(contour.toList())
            val path = Path()
            path.moveTo(points[0].x.toFloat(), points[0].y.toFloat())
            for (i in 1 until points.size) {
                path.lineTo(points[i].x.toFloat(), points[i].y.toFloat())
            }
            path.close()
            val paint = Paint()
            paint.color = Color.BLACK
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
        }

        val pngFile = File(context.getExternalFilesDir(null), pngFilePath)
        pngFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val svgDraw = "gps_app/drawing.svg"
        val pngDraw =  "gps_app/drawing_converted.png"

        convertSvgToPngExternal(svgDraw, pngDraw)
    }

    private fun convertSvgToPngExternal(svgFilePath: String, pngFilePath: String) {
        val svgFile = File(this.getExternalFilesDir(null), svgFilePath)
        val svg = SVG.getFromInputStream(svgFile.inputStream())

        val bitmap = Bitmap.createBitmap(
            svg.documentWidth.toInt(),
            svg.documentHeight.toInt(),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK) // Beállítjuk a háttérszínt feketére

        svg.renderToCanvas(canvas)

        val pngFile = File(this.getExternalFilesDir(null), pngFilePath)
        pngFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}