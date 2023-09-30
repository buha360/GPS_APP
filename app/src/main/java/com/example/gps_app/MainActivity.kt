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
import android.util.Log
import android.widget.Button
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

    object DataHolder {
        var pathData = ArrayList<String>()
        var detectedCorners = mutableListOf<Point>()
    }

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
                    detectCornersUsingOpenCV(this)
                } catch (e: IOException) {
                    e.printStackTrace()
                    callback(false) // Mentés sikertelen
                }
            } else {
                callback(false) // Snapshot hiányzik
            }
        }
    }

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
        Core.inRange(croppedImage, Scalar(0.0), Scalar(25.0), result) // A 0.0 és 100.0 határok az értékfüggőek lehetnek, módosítsd szükség szerint

        val contours = ArrayList<MatOfPoint>()
        Imgproc.adaptiveThreshold(croppedImage, result, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 25, 12.0)
        Imgproc.findContours(result, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        Log.d("MyApp", "Kontúrok száma: ${contours.size}")

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
            val svgPathData = StringBuilder("M") // "M" az "move to" SVG parancs

            if (points.isNotEmpty()) {
                svgPathData.append("${points[0].x},${points[0].y}")  // Kezdőpont hozzáadása
            }

            for (i in 1 until points.size) {
                path.lineTo(points[i].x.toFloat(), points[i].y.toFloat())
                svgPathData.append(" L ${points[i].x},${points[i].y}") // "L" az "line to" SVG parancs
            }

            path.close()

            svgPathData.append(" Z") // "Z" az "close path" SVG parancs
            DataHolder.pathData.add(svgPathData.toString()) // SVG path adat hozzáadása az osztályszintű változóhoz
            Log.d("gps_app: - pathData main: ", DataHolder.pathData.toString())

            val cornerPaint = Paint()
            cornerPaint.color = Color.BLUE
            cornerPaint.style = Paint.Style.FILL

            val paint = Paint()
            paint.color = Color.BLACK
            paint.style = Paint.Style.FILL
            canvas.drawPath(path, paint)
        }

        val directory = File(context.getExternalFilesDir(null), "gps_app")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val svgFile = File(directory, "convertedSVG_Map.svg")
        if (svgFile.exists()) {
            svgFile.delete()
        }

        createSVGFromPathData(DataHolder.pathData)
        svgFile.writeText(DataHolder.pathData.toString())
    }

    private fun detectCornersUsingOpenCV(context: Context){
        OpenCVLoader.initDebug()

        val imagePath = File(context.getExternalFilesDir(null), "gps_app/map_snapshot.png")
        val image = Imgcodecs.imread(imagePath.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)

        // Harris sarokdetekció
        val harrisCorners = Mat()
        Imgproc.cornerHarris(image, harrisCorners, 2, 3, 0.04)
        val normalizedCorners = Mat()
        Core.normalize(harrisCorners, normalizedCorners, 0.0, 255.0, Core.NORM_MINMAX)
        Core.convertScaleAbs(normalizedCorners, normalizedCorners)

        for (y in 0 until normalizedCorners.rows()) {
            for (x in 0 until normalizedCorners.cols()) {
                if (normalizedCorners.get(y, x)[0] > 150.0) {
                    DataHolder.detectedCorners.add(Point(x.toDouble(), y.toDouble()))
                }
            }
        }
        Log.d("MyApp: - detectedCorners: ", DataHolder.detectedCorners.toString())
    }

    private fun createSVGFromPathData(pathData: List<String>): String {
        val builder = StringBuilder()

        builder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\">\n")

        for (data in pathData) {
            builder.append("<path d=\"$data\" fill=\"none\" stroke=\"black\"/>\n")
        }

        builder.append("</svg>")

        return builder.toString()
    }
}