package com.example.gps_app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.gps_app.MainActivity.DataHolder.detectedCorners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class Finish  : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        CoroutineScope(Dispatchers.IO).launch {
            calculateBestMatch()
        }
    }

    private suspend fun calculateBestMatch() {
        val detectedCornersConverted = detectedCorners.map { SVGtoGraph.Point(it.x.toFloat(), it.y.toFloat()) }
        val SVGtoGraphDraw = SVGtoGraph(MainActivity.DataHolder.pathData, detectedCornersConverted)
        val finishedDrawGraph = SVGtoGraphDraw.processPathData(false)
        Log.d("gps_app: - finishedDrawGraph: ", finishedDrawGraph.toString())

        val SVGtoGraphMap = SVGtoGraph(MainActivity.DataHolder.pathData, detectedCornersConverted)
        val finishedDrawMap = SVGtoGraphMap.processPathData(true)
        Log.d("gps_app: - finishedDrawMap: ", finishedDrawMap.toString())

        val svgContent = SVGtoGraphMap.createSVGFromGraph()
        val svgFile = File(getExternalFilesDir(null), "output_graph.svg")
        svgFile.writeText(svgContent)

        val compare = CompareGraphs(finishedDrawGraph, finishedDrawMap)

        // Megkeressük az összehasonlító éleket
        //val bestMatch = compare.findPath()
        //Log.d("MyApp: - bestMatch: ", bestMatch.toString())

        val bitmap = createBitmapFromBestMatch(finishedDrawMap)

        // UI frissítése a fő szálon
        withContext(Dispatchers.Main) {
            val imageView = findViewById<ImageView>(R.id.imageViewFinish)
            imageView.setImageBitmap(bitmap)
        }
    }

    private fun createBitmapFromBestMatch(bestMatch: List<SVGtoGraph.Edge>): Bitmap {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val backgroundBitmap = loadImageFromExternalStorage(this, "gps_app/map_snapshot.png")

        // Ellenőrizd, hogy létezik-e a háttérkép és rajzold meg.
        if (backgroundBitmap != null) {
            // Skálázd a hátteret a Canvas méretéhez
            val scaledBackground = Bitmap.createScaledBitmap(backgroundBitmap, 400, 300, true)
            canvas.drawBitmap(scaledBackground, 0f, 0f, null)
        } else {
            canvas.drawColor(Color.BLACK)
        }

        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (edge in bestMatch) {
            val from = edge.from
            val to = edge.to

            minX = minOf(minX, from.x, to.x)
            minY = minOf(minY, from.y, to.y)
            maxX = maxOf(maxX, from.x, to.x)
            maxY = maxOf(maxY, from.y, to.y)
        }

        // Kiszámítjuk a skálázási értékeket, és azonos értéket használunk mindkét tengelyen
        val scale = minOf(canvas.width / (maxX - minX), canvas.height / (maxY - minY))

        val offsetX = -minX
        val offsetY = -minY

        for (edge in bestMatch) {
            val from = edge.from
            val to = edge.to

            val adjustedFromX = (from.x + offsetX) * scale
            val adjustedFromY = (from.y + offsetY) * scale
            val adjustedToX = (to.x + offsetX) * scale
            val adjustedToY = (to.y + offsetY) * scale

            canvas.drawLine(adjustedFromX, adjustedFromY, adjustedToX, adjustedToY, paint)
        }

        return bitmap
    }

    private fun loadImageFromExternalStorage(context: Context, fileName: String): Bitmap? {
        val filePath = File(context.getExternalFilesDir(null), fileName)
        return BitmapFactory.decodeFile(filePath.absolutePath)
    }

}