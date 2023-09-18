package com.example.gps_app

import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.imgcodecs.Imgcodecs
import java.io.FileOutputStream
import java.io.IOException

class Finish  : AppCompatActivity() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        val imageView = findViewById<ImageView>(R.id.imageViewFinish)

        // Hozz létre egy üres Bitmap-et a kép méretével
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)

        // Hozz létre egy Canvas objektumot a Bitmap-re
        val canvas = Canvas(bitmap)

        // Fájl elérési útvonalak inicializálása
        val pngFilePath = File(getExternalFilesDir("gps_app"), "map_snapshot_converted.png").path
        val pngDrawing = File(getExternalFilesDir("gps_app"), "drawing_converted.png").path

        // A feldolgozást Coroutine segítségével végezzük
        GlobalScope.launch(Dispatchers.IO) {
            val whitePixelsMap = findWhitePixelsCoordinates(pngFilePath)
            val WhitePixelsDrawing = findWhitePixelsCoordinates(pngDrawing)

            if (whitePixelsMap != null && WhitePixelsDrawing != null) { // Ellenőrizzük, hogy ne legyenek null értékek
                val pathPoints = calculatePath(whitePixelsMap, WhitePixelsDrawing)
                Log.d("MyApp: calculatePath() - pathPoints: ", pathPoints.toString())
                if (pathPoints != null) { // Ellenőrizzük, hogy ne legyen null érték
                    val pathfinder = Pathfinder(whitePixelsMap, WhitePixelsDrawing)
                    drawFinishBitmap(pathfinder, canvas)

                    // Itt tedd be a korábban elkészített Bitmap-et az ImageView-be
                    withContext(Dispatchers.Main) {
                        imageView.setImageBitmap(bitmap)
                        // Mentés a PNG-fájlba
                        saveBitmapToFile(bitmap, "finish_image.png")
                    }
                }
            }
        }
    }

    private fun drawFinishBitmap(pathfinder: Pathfinder, canvas: Canvas) {
        // Hozz létre egy Paint objektumot a vonal stílusának beállításához
        val paint = Paint()
        paint.color = Color.RED // Választhatod a vonal színét
        paint.strokeWidth = 2f // Választhatod a vonal vastagságát

        // Hozz létre egy új Path objektumot az útvonalhoz
        val path = Path()

        // Iterálj végig az útvonalon és add hozzá a Path-hoz a pontokat
        val pathPoints = pathfinder.getPath()
        for (i in pathPoints.indices) {
            val point = pathPoints[i]
            if (i == 0) {
                path.moveTo(point.x.toFloat(), point.y.toFloat())
            } else {
                path.lineTo(point.x.toFloat(), point.y.toFloat())
            }
        }

        // Rajzold meg az útvonalat a Canvas-re a Path és a Paint segítségével
        canvas.drawPath(path, paint)
    }

    private fun findWhitePixelsCoordinates(pngFilePath: String): List<android.graphics.Point> {
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
        Log.d("MyApp: findWhitePixelsCoordinates() - whitePixels: ", whitePixels.toString())
        return whitePixels
    }

    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String) {
        val storageDir = File(getExternalFilesDir("gps_app").toString())
        storageDir.mkdirs()

        val imageFile = File(storageDir, fileName)

        // Fájl mentése a külső tárhelyen
        try {
            val stream = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun calculatePath(whitePixels: List<Point>?, blackPixelsDrawing: List<Point>?): List<Point> {
        if (whitePixels != null && blackPixelsDrawing != null) {
            val pathfinder = Pathfinder(whitePixels, blackPixelsDrawing)
            return pathfinder.getPath()
        } else {
            // Kezelés, ha a listák null-ok
            return emptyList()
        }
    }
}