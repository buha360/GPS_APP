package com.wardanger3.gps_app.abstract_classes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

abstract class AbstractCanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    data class Vertex(val x: Double, val y: Double) {
        override fun toString(): String = "($x, $y)"
    }

    data class Edge(val start: Vertex, val end: Vertex) {
        override fun toString(): String = "[$start -> $end]"
    }

    class Graph {
        private var vertices = mutableListOf<Vertex>()
        var edges = mutableListOf<Edge>()

        fun getNeighbors(vertex: Vertex): List<Vertex> {
            val neighbors = mutableListOf<Vertex>()
            for (edge in edges) {
                when (vertex) {
                    edge.start -> neighbors.add(edge.end)
                    edge.end -> neighbors.add(edge.start)
                }
            }
            return neighbors.distinct()
        }

        override fun toString(): String {
            return "Graph(vertices=$vertices, edges=$edges)"
        }
    }

    protected val path = Path()
    protected val paint = Paint()
    protected var lastX: Float = 0f
    protected var lastY: Float = 0f
    protected var canvasBitmap: Bitmap? = null
    protected var drawCanvas: Canvas? = null
    protected val brushSize = 7f
    protected val pathData = ArrayList<String>()

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
        drawCanvas?.drawColor(Color.parseColor("#31343b"))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(canvasBitmap!!, 0f, 0f, paint)
        canvas.drawPath(path, paint)
    }

    abstract override fun onTouchEvent(event: MotionEvent): Boolean
    abstract fun clearCanvas()
    abstract fun createGraphFromPathData()
}