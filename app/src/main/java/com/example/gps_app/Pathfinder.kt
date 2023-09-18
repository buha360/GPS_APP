package com.example.gps_app

import android.graphics.Point
import java.util.*

class Pathfinder(private val whitePixels: List<Point>, private val blackPixels: List<Point>, private val extensionFactor: Double = 1.2) {
    private val path: MutableList<Point> = mutableListOf()

    init {
        findPath()
    }

    private fun findPath() {
        val startPoint = whitePixels.firstOrNull()
        if (startPoint != null) {
            path.add(startPoint)

            while (true) {
                val lastPoint = path.lastOrNull() ?: break
                val neighbors = getNeighbors(lastPoint)

                var nextPoint: Point? = null
                var minDistance = Double.MAX_VALUE

                for (neighbor in neighbors) {
                    if (!path.contains(neighbor) && isPathClear(lastPoint, neighbor)) {
                        val distance = calculateDistance(neighbor, startPoint)
                        if (distance < minDistance) {
                            minDistance = distance
                            nextPoint = neighbor
                        }
                    }
                }

                if (nextPoint != null) {
                    path.add(nextPoint)
                } else {
                    break
                }
            }
        }
    }

    private fun getNeighbors(point: Point): List<Point> {
        val neighbors = mutableListOf<Point>()
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val neighbor = Point(point.x + dx, point.y + dy)
                if (isValidPoint(neighbor)) {
                    neighbors.add(neighbor)
                }
            }
        }
        return neighbors
    }

    private fun isValidPoint(point: Point): Boolean {
        return point.x >= 0 && point.y >= 0 && point.x < 1100 && point.y < 740
    }

    private fun isPathClear(start: Point, end: Point): Boolean {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())

        val stepX = dx / distance.toInt()
        val stepY = dy / distance.toInt()

        var x = start.x
        var y = start.y

        for (i in 0 until distance.toInt()) {
            x += stepX
            y += stepY

            val currentPoint = Point(x, y)
            if (blackPixels.contains(currentPoint)) {
                return false
            }
        }

        return true
    }

    private fun calculateDistance(point1: Point, point2: Point): Double {
        val dx = point2.x - point1.x
        val dy = point2.y - point1.y
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
    }

    fun getPath(): List<Point> {
        // Extend or shorten the path based on the extensionFactor
        val extendedPath = mutableListOf<Point>()
        for (i in 0 until path.size - 1) {
            val point1 = path[i]
            val point2 = path[i + 1]
            val dx = point2.x - point1.x
            val dy = point2.y - point1.y
            val extendedX = (point2.x + dx * extensionFactor).toInt()
            val extendedY = (point2.y + dy * extensionFactor).toInt()
            extendedPath.add(point1)
            extendedPath.add(Point(extendedX, extendedY))
        }
        extendedPath.add(path.last())
        return extendedPath
    }
}