package com.example.gps_app

import java.io.File
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.PersistableBundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class Finish  : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        val imageView = findViewById<ImageView>(R.id.imageViewFinish)

        // Hozz létre egy üres Bitmap-et a kép méretével
        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)

        // Hozz létre egy Canvas objektumot a Bitmap-re
        val canvas = Canvas(bitmap)

        val mainActivityInstance = MainActivity.getInstance()
        val pngFilePath = File(this.getExternalFilesDir(null), "gps_app/map_converted.png").path
        val pngDrawing = File(this.getExternalFilesDir(null), "gps_app/drawing.png").path

        // Hozz létre egy új Pathfinder példányt
        val whitePixels = mainActivityInstance?.findWhitePixelsCoordinates(pngFilePath)
        val blackPixelsDrawing = mainActivityInstance?.findBlackPixelsCoordinates(pngDrawing)
        if (whitePixels != null && blackPixelsDrawing != null) {
            val pathfinder = Pathfinder(whitePixels, blackPixelsDrawing)
            drawFinishBitmap(pathfinder, canvas)

            // Itt tedd be a korábban elkészített Bitmap-et az ImageView-be
            imageView.setImageBitmap(bitmap)
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

    companion object {
        private var instance: Finish? = null

        fun getInstance(): Finish? {
            return instance
        }
    }
}