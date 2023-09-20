package com.example.gps_app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class Finish  : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.finish)

        val SVGtoGraphDraw = SVGtoGraph(CanvasView.DataHolder.pathData)
        val finishedDrawGraph = SVGtoGraphDraw.SVGToGraph() // Feltételezem, hogy ezt visszaadja a metódus
        Log.d("MyApp: - finishedDrawGraph: ", finishedDrawGraph.toString())

        val SVGtoGraphMap = SVGtoGraph(MainActivity.DataHolder.pathData)
        val finishedDrawMap = SVGtoGraphMap.SVGToGraph() // Feltételezem, hogy ezt visszaadja a metódus
        Log.d("MyApp: - finishedDrawMap: ", finishedDrawMap.toString())

        val compare = CompareGraphs(this, finishedDrawGraph, finishedDrawMap)
        val bestMatch = compare.alignGraphs()
        Log.d("MyApp: - bestMatch: ", bestMatch.toString())

        // Új Bitmap és Canvas létrehozása
        val bitmap = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        // Kirajzoljuk az igazított gráfot
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

        val scaleX = canvas.width / (maxX - minX)
        val scaleY = canvas.height / (maxY - minY)
        val offsetX = -minX
        val offsetY = -minY

        for (edge in bestMatch) {
            val from = edge.from
            val to = edge.to

            val adjustedFromX = (from.x + offsetX) * scaleX
            val adjustedFromY = (from.y + offsetY) * scaleY
            val adjustedToX = (to.x + offsetX) * scaleX
            val adjustedToY = (to.y + offsetY) * scaleY

            Log.d("MyApp: - Adjusted from: ", "($adjustedFromX, $adjustedFromY)")
            Log.d("MyApp: - Adjusted to: ", "($adjustedToX, $adjustedToY)")

            canvas.drawLine(adjustedFromX, adjustedFromY, adjustedToX, adjustedToY, paint)
        }

        // UI frissítése a fő szálon
        val imageView = findViewById<ImageView>(R.id.imageViewFinish)
        imageView.setImageBitmap(bitmap)
    }
}