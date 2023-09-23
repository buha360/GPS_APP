package com.example.gps_app

import kotlin.math.sqrt

class SVGtoGraph(private val pathData: List<String>) {

    data class Point(val x: Float, val y: Float)
    data class Edge(val from: Point, val to: Point)

    private val graph: MutableList<Edge> = mutableListOf()

    fun SVGToGraph(): MutableList<Edge> {
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

        return graph
    }
}