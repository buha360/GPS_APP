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
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class CanvasView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    data class Vertex(val x: Double, val y: Double) {
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
                pathData.add("M $x $y")
            }
            MotionEvent.ACTION_MOVE -> {
                val distance = sqrt((x - lastX).pow(2) + (y - lastY).pow(2))
                if (distance > 10) {
                    path.lineTo(x, y)
                    lastX = x
                    lastY = y
                    pathData.add("L $x $y")
                }
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

    fun createGraphFromPathData() {
        val graph = Graph()

        var lastVertex: Vertex? = null
        var segment = mutableListOf<Vertex>()

        for (data in pathData) {
            if (data == "U") {
                if (segment.isNotEmpty()) {
                    val simplifiedSegment = douglasPeucker(segment, 6.9f) // 6.9 pixel tolerancia
                    for (i in 1 until simplifiedSegment.size) {
                        graph.edges.add(Edge(simplifiedSegment[i - 1], simplifiedSegment[i]))
                    }
                    graph.vertices.addAll(simplifiedSegment)
                }
                segment.clear()
                lastVertex = null
                continue
            }

            val parts = data.split(" ")
            val x = parts[1].toDouble()
            val y = parts[2].toDouble()
            val currentVertex = Vertex(x, y)

            segment.add(currentVertex)
            lastVertex = currentVertex
        }

        DataHolder.graph = graph
        Log.d("gps_app-canvasview: - graph: ", DataHolder.graph.toString())
    }

    private fun douglasPeucker(vertices: List<Vertex>, epsilon: Float): List<Vertex> {
        if (vertices.size <= 2) return vertices

        val firstVertex = vertices.first()
        val lastVertex = vertices.last()

        var maxDistance = 0.0f
        var index = 0

        for (i in 1 until vertices.size - 1) {
            val distance = pointLineDistance(vertices[i], firstVertex, lastVertex)
            if (distance > maxDistance) {
                index = i
                maxDistance = distance.toFloat()
            }
        }

        if (maxDistance > epsilon) {
            val leftRecursive = douglasPeucker(vertices.subList(0, index + 1), epsilon)
            val rightRecursive = douglasPeucker(vertices.subList(index, vertices.size), epsilon)

            return leftRecursive.dropLast(1) + rightRecursive
        } else {
            return listOf(firstVertex, lastVertex)
        }
    }

    private fun pointLineDistance(point: Vertex, lineStart: Vertex, lineEnd: Vertex): Double {
        val numerator = abs((lineEnd.y - lineStart.y) * point.x - (lineEnd.x - lineStart.x) * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        val denominator = sqrt((lineEnd.y - lineStart.y).pow(2) + (lineEnd.x - lineStart.x).pow(2))
        return (numerator / denominator)
    }
}