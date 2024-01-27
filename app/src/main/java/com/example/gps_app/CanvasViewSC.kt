package com.example.gps_app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import com.example.gps_app.abstract_classes.AbstractCanvasView
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class CanvasViewSC(context: Context, attrs: AttributeSet) : AbstractCanvasView(context, attrs) {

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

    interface OnDrawListener {
        fun onDrawStarted()
    }

    var onDrawListener: OnDrawListener? = null

    object DataHolder {
        lateinit var graph: Graph
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
                pathData.add("U")
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
            if (data == "U") {
                if (segment.isNotEmpty()) {
                    val simplifiedSegment = douglasPeucker(segment, 30f) // 30 pixel tolerancia
                    for (i in 1 until simplifiedSegment.size) {
                        graph.edges.add(Edge(simplifiedSegment[i - 1], simplifiedSegment[i]))
                    }
                    graph.vertices.addAll(simplifiedSegment)
                }
                segment.clear()
                continue
            }

            val parts = data.split(" ")
            val x = parts[1].toDouble()
            val y = parts[2].toDouble()
            val currentVertex = Vertex(x, y)

            segment.add(currentVertex)
        }

        DataHolder.graph = graph
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

        return if (maxDistance > epsilon) {
            val leftRecursive = douglasPeucker(vertices.subList(0, index + 1), epsilon)
            val rightRecursive = douglasPeucker(vertices.subList(index, vertices.size), epsilon)

            leftRecursive.dropLast(1) + rightRecursive
        } else {
            listOf(firstVertex, lastVertex)
        }
    }

    private fun pointLineDistance(point: Vertex, lineStart: Vertex, lineEnd: Vertex): Double {
        val numerator = abs((lineEnd.y - lineStart.y) * point.x - (lineEnd.x - lineStart.x) * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        val denominator = sqrt((lineEnd.y - lineStart.y).pow(2) + (lineEnd.x - lineStart.x).pow(2))
        return (numerator / denominator)
    }
}