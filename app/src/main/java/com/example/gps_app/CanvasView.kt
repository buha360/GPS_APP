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
        var vertices = mutableListOf<Vertex>()
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

    private val path = Path()
    private val paint = Paint()
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private val brushSize = 7f
    private val pathData = ArrayList<String>()

    object DataHolder {
        lateinit var graph: Graph
        var endPoints = mutableListOf<Vertex>()
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
                pathData.add("U $x $y")
                val currentVertex = Vertex(x.toDouble(), y.toDouble())
                DataHolder.endPoints.add(currentVertex)
            }
            else -> return false
        }

        invalidate()
        return true
    }

    fun clearCanvas() {
        pathData.clear()
        path.reset() // Törli a path-ot
        drawCanvas?.drawColor(Color.BLACK) // Fekete hátterűvé teszi a canvas-t
        DataHolder.graph = Graph() // Új, üres gráf inicializálása
        DataHolder.endPoints.clear() // Végpontok törlése
        invalidate()
    }

    fun createGraphFromPathData() {
        val graph = Graph()
        val segment = mutableListOf<Vertex>()

        for (data in pathData) {
            when {
                data.startsWith("M") || data.startsWith("L") -> {
                    val parts = data.split(" ")
                    val x = parts[1].toDouble()
                    val y = parts[2].toDouble()
                    segment.add(Vertex(x, y))
                }
                data.startsWith("U") -> {
                    if (segment.isNotEmpty()) {
                        // Alkalmazzuk a Douglas-Peucker algoritmust a szegmentumra
                        val simplifiedSegment = douglasPeucker(segment, 15f) // 15 pixel tolerancia
                        if (simplifiedSegment.size > 1) {
                            for (i in 1 until simplifiedSegment.size) {
                                graph.edges.add(Edge(simplifiedSegment[i - 1], simplifiedSegment[i]))
                            }
                            graph.vertices.addAll(simplifiedSegment)
                        }
                        segment.clear()
                    }
                    // Hozzáadjuk az "U" parancsban lévő pontot is
                    val parts = data.split(" ")
                    val x = parts[1].toDouble()
                    val y = parts[2].toDouble()
                    val endPoint = Vertex(x, y)
                    if (!graph.vertices.contains(endPoint)) {
                        graph.vertices.add(endPoint)
                    }
                }
            }
        }

        DataHolder.graph = graph
    }

    private fun douglasPeucker(vertices: List<Vertex>, epsilon: Float): List<Vertex> {
        if (vertices.size <= 2) {
            Log.d("DouglasPeucker", "Nincs szükség finomításra, pontok száma: ${vertices.size}")
            return vertices
        }

        val firstVertex = vertices.first()
        val lastVertex = vertices.last()

        var maxDistance = 0.0f
        var index = 0

        for (i in 1 until vertices.size - 1) {
            if (!DataHolder.endPoints.contains(vertices[i])) {
                val distance = pointLineDistance(vertices[i], firstVertex, lastVertex)
                Log.d("DouglasPeucker", "Pont: ${vertices[i]}, Távolság: $distance")

                if (distance > maxDistance) {
                    index = i
                    maxDistance = distance.toFloat()
                }
            } else {
                Log.d("DouglasPeucker", "EndPoint találat, pont kihagyva: ${vertices[i]}")
            }
        }

        return if (maxDistance > epsilon) {
            val leftRecursive = douglasPeucker(vertices.subList(0, index + 1), epsilon)
            val rightRecursive = douglasPeucker(vertices.subList(index, vertices.size), epsilon)

            // Ellenőrizze, hogy az eredeti endPointok közül valamelyiket módosítottuk-e
            updateEndPointsIfNeeded(vertices, leftRecursive + rightRecursive)

            leftRecursive.dropLast(1) + rightRecursive
        } else {
            // Ellenőrizze, hogy az eredeti endPointok közül valamelyiket módosítottuk-e
            updateEndPointsIfNeeded(vertices, listOf(firstVertex, lastVertex))
            listOf(firstVertex, lastVertex)
        }
    }

    private fun updateEndPointsIfNeeded(originalVertices: List<Vertex>, simplifiedVertices: List<Vertex>) {
        originalVertices.forEach { originalVertex ->
            if (DataHolder.endPoints.contains(originalVertex)) {
                val index = simplifiedVertices.indexOfFirst { it == originalVertex }
                if (index != -1) {
                    val newVertex = simplifiedVertices[index]
                    val endPointIndex = DataHolder.endPoints.indexOf(originalVertex)
                    DataHolder.endPoints[endPointIndex] = newVertex
                }
            }
        }
    }

    private fun pointLineDistance(point: Vertex, lineStart: Vertex, lineEnd: Vertex): Double {
        val numerator = abs((lineEnd.y - lineStart.y) * point.x - (lineEnd.x - lineStart.x) * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        val denominator = sqrt((lineEnd.y - lineStart.y).pow(2) + (lineEnd.x - lineStart.x).pow(2))
        return (numerator / denominator)
    }
}