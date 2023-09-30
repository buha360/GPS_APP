package com.example.gps_app

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

    class SVGtoGraph(private val pathData: List<String>, private val detectedCorners: List<Point>) {

        data class Point(val x: Float, val y: Float)
        data class Edge(
            val from: Point,
            val to: Point,
            val connectedPoints: MutableList<Point> = mutableListOf()
        )

        private val graph: MutableList<Edge> = mutableListOf()

        private fun Point.distanceTo(other: Point): Float {
            return sqrt((this.x - other.x).pow(2) + (this.y - other.y).pow(2))
        }

        fun processPathData(connectOnSecondRun: Boolean = false): MutableList<Edge> {
            val fullData = pathData.joinToString(" ")
            val commands = fullData.split(Regex("[ ,]")).filter { it.isNotEmpty() }

            var index = 0
            var lastPoint: Point? = null
            var startPoint: Point? = null

            while (index < commands.size) {
                when (commands[index]) {
                    "M" -> {
                        lastPoint?.let { previousLastPoint ->
                            graph.add(Edge(previousLastPoint, lastPoint!!))
                        }
                        val x = commands[++index].toFloat()
                        val y = commands[++index].toFloat()
                        startPoint = Point(x, y)
                        lastPoint = startPoint
                    }

                    "L" -> {
                        val x = commands[++index].toFloat()
                        val y = commands[++index].toFloat()
                        val newPoint = Point(x, y)
                        lastPoint?.let { previousPoint ->
                            graph.add(Edge(previousPoint, newPoint))
                        }
                        lastPoint = newPoint
                    }

                    "Z" -> {
                        if (startPoint != null && lastPoint != null) {
                            graph.add(Edge(lastPoint, startPoint))
                            startPoint = null
                            lastPoint = null
                        }
                    }
                }
                index++
            }

            if (connectOnSecondRun) {
                insertCorners()
                connectClosePoints()
            }

            return graph
        }

        private fun insertCorners() {
            for (edge in graph) {
                for (corner in detectedCorners) {
                    if (corner.isBetween(edge.from, edge.to)) {
                        edge.connectedPoints.add(corner)
                    }
                }
            }
        }

        private fun connectClosePoints() {
            val radius = 10.0f
            for (i in detectedCorners.indices) {
                for (j in i + 1 until detectedCorners.size) {
                    if (detectedCorners[i].distanceTo(detectedCorners[j]) <= radius) {
                        for (edge in graph) {
                            if (edge.connectedPoints.contains(detectedCorners[i])) {
                                edge.connectedPoints.add(detectedCorners[j])
                            }
                        }
                    }
                }
            }
        }

        private fun Point.isBetween(a: Point, b: Point): Boolean {
            return (this.x in min(a.x, b.x)..max(a.x, b.x)) && (this.y in min(a.y, b.y)..max(
                a.y,
                b.y
            ))
        }

        fun createSVGFromGraph(): String {
            val stringBuilder = StringBuilder()
            stringBuilder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" version=\"1.1\">")

            for (edge in graph) {
                stringBuilder.append("<path d=\"M${edge.from.x},${edge.from.y} L${edge.to.x},${edge.to.y}\" stroke=\"red\" fill=\"none\"/>")
                for (point in edge.connectedPoints) {
                    stringBuilder.append("<circle cx=\"${point.x}\" cy=\"${point.y}\" r=\"2\" fill=\"blue\" />")
                }
            }

            stringBuilder.append("</svg>")
            return stringBuilder.toString()
        }
    }