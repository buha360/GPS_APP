package com.example.gps_app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

class CanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    data class Vertex(val x: Float, val y: Float) {
        override fun toString(): String {
            return "($x, $y)"
        }
    }

    data class Edge(val start: Vertex, val end: Vertex) {
        override fun toString(): String {
            return "[$start -> $end]"
        }
    }

    class Graph {
        val vertices = mutableListOf<Vertex>()
        val edges = mutableListOf<Edge>()

        override fun toString(): String {
            return "Vertices: $vertices, Edges: $edges"
        }
    }

    private val path = Path()
    private val paint = Paint()
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private val brushSize = 8f
    private val pathData = ArrayList<String>()

    object DataHolder {
        var graph: Graph? = null
    }

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
                pathData.add("U")
            }
            else -> return false
        }

        invalidate()
        return true
    }

    fun clearCanvas() {
        pathData.clear()
        path.reset()  // Törli a path-ot
        drawCanvas?.drawColor(Color.BLACK) // Fekete hátterűvé teszi a canvas-t
        invalidate()
    }

    fun createGraphFromPathData(){
        val graph = Graph()

        var lastVertex: Vertex? = null
        for (data in pathData) {
            if (data == "U") {
                lastVertex = null
                continue
            }

            val parts = data.split(" ")
            val x = parts[1].toFloat()
            val y = parts[2].toFloat()

            val currentVertex = Vertex(x, y)

            if (lastVertex != null) {
                graph.edges.add(Edge(lastVertex, currentVertex))
            }

            graph.vertices.add(currentVertex)
            lastVertex = currentVertex
        }
        DataHolder.graph = graph
        Log.d("gps_app-canvasview: - graph: ", DataHolder.graph.toString())
    }
}