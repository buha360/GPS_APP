package com.example.gps_app

import kotlin.math.pow
import kotlin.math.sqrt

class SVGtoGraph(private val pathData: List<String>, private val detectedCorners: List<Point>) {

    data class Point(val x: Float, val y: Float)
    data class Edge(val from: Point, val to: Point)

    private val graph: MutableList<Edge> = mutableListOf()

    private fun Point.distanceTo(other: Point): Float {
        return sqrt((this.x - other.x).pow(2) + (this.y - other.y).pow(2))
    }

    fun processPathData(connectOnSecondRun: Boolean = false): MutableList<Edge> {
        val fullData = pathData.joinToString(" ")
        val commands = fullData.split(Regex("[ ,]")).filter { it.isNotEmpty() }

        var index = 0
        var lastPoint: Point? = null
        var currentSection = mutableListOf<Point>()

        while (index < commands.size) {
            when (commands[index]) {
                "M" -> {
                    val x = commands[++index].toFloat()
                    val y = commands[++index].toFloat()
                    lastPoint = Point(x, y)
                    currentSection.add(lastPoint)
                }

                "L" -> {
                    val x = commands[++index].toFloat()
                    val y = commands[++index].toFloat()
                    val newPoint = Point(x, y)
                    currentSection.add(newPoint)
                    if (lastPoint != null) {
                        graph.add(Edge(lastPoint, newPoint))
                        lastPoint = newPoint
                    }
                }

                "Z" -> {
                    // Close the path if there are at least 2 points
                    if (currentSection.size >= 2) {
                        graph.add(Edge(currentSection.last(), currentSection.first()))
                    }
                    currentSection.clear()
                }
            }
            index++
        }

        if (connectOnSecondRun) {
            graph.addAll(connectClosePoints(detectedCorners))
        }

        return graph
    }

    private fun connectClosePoints(points: List<Point>): MutableList<Edge> {
        val radius = 30.0f
        val newEdges = mutableListOf<Edge>()
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                if (points[i].distanceTo(points[j]) <= radius) {
                    newEdges.add(Edge(points[i], points[j]))
                }
            }
        }
        return newEdges
    }

    fun createSVGFromGraph(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\">")

        for (edge in graph) {
            stringBuilder.append("<path d=\"M${edge.from.x},${edge.from.y} L${edge.to.x},${edge.to.y}\" stroke=\"red\" fill=\"none\"/>")
        }

        stringBuilder.append("</svg>")
        return stringBuilder.toString()
    }
}