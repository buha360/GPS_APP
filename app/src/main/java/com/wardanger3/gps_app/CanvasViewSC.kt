package com.wardanger3.gps_app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import com.wardanger3.gps_app.abstract_classes.AbstractCanvasView
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class CanvasViewSC(context: Context, attrs: AttributeSet) : AbstractCanvasView(context, attrs) {

    data class Vertex(val x: Double, val y: Double) {
        override fun toString(): String = "($x, $y)"
    }

    data class Edge(val start: Vertex, val end: Vertex) {
        override fun toString(): String = "[$start -> $end]"
    }

    class Graph {
        var vertices = mutableListOf<Vertex>()
        var edges = mutableListOf<Edge>()

        override fun toString(): String {
            return "Graph(vertices=$vertices, edges=$edges)"
        }
    }

    interface OnDrawListener {
        fun onDrawStarted()
    }

    var onDrawListener: OnDrawListener? = null

    object DataHolder {
        lateinit var graph: Graph
        var endPoints = mutableListOf<Vertex>()

        fun clearData() {
            graph = Graph()
            endPoints.clear()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onDrawListener?.onDrawStarted()
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

    override fun clearCanvas() {
        pathData.clear()
        path.reset()  // Törli a path-ot
        DataHolder.graph = Graph() // Új, üres gráf inicializálása
        drawCanvas?.drawColor(Color.parseColor("#31343b"))
        invalidate()
    }

    override fun createGraphFromPathData() {
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
                        val simplifiedSegment = douglasPeucker(segment, 50f)
                        if (simplifiedSegment.size > 1) {
                            // Itt használjuk a subdividedPath-t a segmentLength paraméterrel
                            val subdividedSegment = subdividePath(simplifiedSegment, 180.0)
                            for (i in 1 until subdividedSegment.size) {
                                graph.edges.add(Edge(subdividedSegment[i - 1], subdividedSegment[i]))
                            }
                            graph.vertices.addAll(subdividedSegment)
                        }
                        segment.clear()
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

    private fun pointLineDistance(point: Vertex, lineStart: Vertex, lineEnd: Vertex): Double {
        val numerator = abs((lineEnd.y - lineStart.y) * point.x - (lineEnd.x - lineStart.x) * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        val denominator = sqrt((lineEnd.y - lineStart.y).pow(2) + (lineEnd.x - lineStart.x).pow(2))
        return (numerator / denominator)
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

    private fun subdividePath(vertices: List<Vertex>, segmentLength: Double = 180.0): List<Vertex> {
        val subdividedPath = mutableListOf<Vertex>()
        for (i in 0 until vertices.size - 1) {
            val start = vertices[i]
            val end = vertices[i + 1]
            subdividedPath.add(start)

            val distance = calculateDistance(start, end)
            val numberOfSegments = (distance / segmentLength).toInt()

            for (j in 1..numberOfSegments) {
                val ratio = j / numberOfSegments.toDouble()
                val intermediatePoint = Vertex(
                    start.x + ratio * (end.x - start.x),
                    start.y + ratio * (end.y - start.y)
                )
                subdividedPath.add(intermediatePoint)
            }
        }
        subdividedPath.add(vertices.last())
        return subdividedPath
    }

    private fun calculateDistance(start: Vertex, end: Vertex): Double {
        return sqrt((end.x - start.x).pow(2) + (end.y - start.y).pow(2))
    }
}