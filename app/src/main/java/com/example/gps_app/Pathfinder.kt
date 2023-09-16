package com.example.gps_app

import android.graphics.Point
import java.util.*

class Pathfinder(private val whitePixels: List<Point>, private val blackPixels: List<Point>) {
    private val path: MutableList<Point> = mutableListOf()
    private val visited: MutableSet<Point> = HashSet()

    init {
        if (whitePixels == null || blackPixels == null) {
            throw IllegalArgumentException("whitePixels and blackPixels must not be null")
        }

        findPath()
    }

    private fun findPath() {
        val randomStartPoint = whitePixels.random()
        val startNode = Node(randomStartPoint, null)
        val queue: Queue<Node> = LinkedList()
        queue.add(startNode)
        visited.add(randomStartPoint)

        val maxDepth = 1000 // A maximális mélység számát itt állíthatod be
        var currentDepth = 0

        while (queue.isNotEmpty() && currentDepth < maxDepth) {
            val currentNode = queue.poll()
            val neighbors = getNeighbors(currentNode!!.point)

            for (neighbor in neighbors) {
                if (!visited.contains(neighbor) && isPathClear(currentNode.point, neighbor)) {
                    visited.add(neighbor)
                    val newNode = Node(neighbor, currentNode)
                    queue.add(newNode)
                }
            }

            currentDepth++
        }

        // Reconstruct the path
        if (visited.isNotEmpty()) {
            var current = visited.random()
            while (getParent(current) != null) {
                path.add(current)
                current = getParent(current)!!
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

    private fun getParent(point: Point): Point? {
        for (i in path.indices) {
            if (path[i] == point) {
                return if (i > 0) path[i - 1] else null
            }
        }
        return null
    }

    fun getPath(): List<Point> {
        if (path.isEmpty()) {
            return emptyList() // Üres lista visszaadása, ha nincs útvonal
        }
        return path.reversed()
    }

    data class Node(val point: Point, val parent: Node?)
}