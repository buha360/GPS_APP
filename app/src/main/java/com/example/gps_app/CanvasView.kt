package com.example.gps_app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

class CanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val path = Path()
    private val paint = Paint()
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private val brushSize = 8f
    val pathData = ArrayList<String>()

    init {
        setupDrawing()
    }

    private fun setupDrawing() {
        paint.color = Color.WHITE // Fehér ecsetszín
        paint.isAntiAlias = true
        paint.strokeWidth = brushSize
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCanvas = Canvas(canvasBitmap!!)
        drawCanvas?.drawColor(Color.BLACK) // Fekete háttér beállítása
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(canvasBitmap!!, 0f, 0f, paint)
        canvas.drawPath(path, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                lastX = x
                lastY = y
                pathData.add("M $x $y") // Kezdőpont hozzáadása a pathData-hoz
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)
                lastX = x
                lastY = y
                pathData.add("L $x $y") // Vonalszakasz hozzáadása a pathData-hoz
            }
            MotionEvent.ACTION_UP -> {
                pathData.add("Z") // Zárási parancs hozzáadása a pathData-hoz
            }
            else -> return false
        }

        invalidate()
        return true
    }

    fun clearCanvas() {
        drawCanvas?.drawColor(Color.BLACK) // Fekete hátterűvé teszi a canvas-t
        pathData.clear()
        invalidate()
    }

    fun saveDrawingAsSVG(context: Context, filename: String) {
        val folderName = "gps_app"
        val folder = File(context.getExternalFilesDir(null), folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }

        val svgContent = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<svg width=\"${width}px\" height=\"${height}px\" version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" style=\"background-color:black\">\n")
            append("<path d=\"")

            for (i in 0 until pathData.size) {
                append(pathData[i])
            }

            append("\" stroke=\"white\" stroke-width=\"2\" />\n")
            append("</svg>")
        }

        try {
            val file = File(folder, filename)
            if (file.exists()) {
                file.delete() // Ha már létezik a fájl, törölje
            }
            val outputStreamWriter = OutputStreamWriter(FileOutputStream(file))
            outputStreamWriter.write(svgContent)
            outputStreamWriter.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}


