package com.example.gps_app

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CompareGraph {
    data class Point(val x: Double, val y: Double) {
        fun rotate(angle: Double, about: Point): Point {
            val sinA = sin(Math.toRadians(angle))
            val cosA = cos(Math.toRadians(angle))
            val dx = x - about.x
            val dy = y - about.y
            val newX = cosA * dx - sinA * dy + about.x
            val newY = sinA * dx + cosA * dy + about.y
            return Point(newX, newY)
        }

        fun scale(factor: Double, about: Point): Point {
            val dx = x - about.x
            val dy = y - about.y
            val newX = dx * factor + about.x
            val newY = dy * factor + about.y
            return Point(newX, newY)
        }
    }

    private val maxMatchesSize = 20  // Fixed size list

    private fun depthFirstSearch(
        largeGraph: MutableMap<Point, MutableList<Point>>,
        smallGraph: CanvasView.Graph,
        currentPath: MutableList<Point>,
        visited: MutableSet<Point>,
        currentDepth: Int,
        potentialMatches: MutableList<List<Point>>
    ) {
        if (currentDepth == smallGraph.vertices.size) {
            synchronized(potentialMatches) {
                if (potentialMatches.size >= maxMatchesSize) {
                    // Ha a lista mérete meghaladja a maxMatchesSize értékét, eltávolítjuk a legrosszabb pontszámú útvonalat
                    val worstMatch = potentialMatches.minByOrNull { matchScore(it, smallGraph.vertices) }
                    potentialMatches.remove(worstMatch)
                }
                potentialMatches.add(currentPath.toList())  // Copy and add the current path to potential matches
            }
            return
        }

        val currentVertex = currentPath.last()
        val neighbors = largeGraph[currentVertex] ?: return

        for (neighbor in neighbors) {
            if (!visited.contains(neighbor)) {
                currentPath.add(neighbor)
                visited.add(neighbor)

                depthFirstSearch(
                    largeGraph,
                    smallGraph,
                    currentPath,
                    visited,
                    currentDepth + 1,
                    potentialMatches
                )

                visited.remove(neighbor)
                currentPath.removeAt(currentPath.size - 1)
            }
        }
    }

    fun findSubgraph(largeGraph: MutableMap<Point, MutableList<Point>>, smallGraph: CanvasView.Graph): List<Point> {
        val rotations = listOf(0.0, 90.0, 180.0, 270.0)
        val scales = listOf(0.8, 1.0, 1.2)

        var bestScore = Double.MIN_VALUE
        var bestMatch: List<Point> = mutableListOf()

        // Calculate the centroid of the small graph to use as a reference point for transformations
        val centroid = Point(
            smallGraph.vertices.map { it.x }.average(),
            smallGraph.vertices.map { it.y }.average()
        )

        for (rotation in rotations) {
            for (scale in scales) {
                val transformedSmallGraph = smallGraph.vertices.map {
                    Point(it.x, it.y).rotate(rotation, centroid).scale(scale, centroid).let {
                        CanvasView.Vertex(it.x, it.y)
                    }
                }

                val potentialMatches = mutableListOf<List<Point>>()

                for (startNode in largeGraph.keys) {
                    val currentPath = mutableListOf(startNode)
                    val visited = mutableSetOf(startNode)

                    depthFirstSearch(
                        largeGraph,
                        CanvasView.Graph(transformedSmallGraph),
                        currentPath,
                        visited,
                        1,
                        potentialMatches
                    )
                }

                for (match in potentialMatches) {
                    val score = matchScore(match, transformedSmallGraph)

                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = match
                    }
                }
            }
        }

        return bestMatch
    }

    private fun euklideanDistance(p1: Point, p2: Point): Double {
        return sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
    }

    private fun matchScore(largeGraph: List<Point>, smallGraphVertices: List<CanvasView.Vertex>): Double {
        if (smallGraphVertices.isEmpty() || largeGraph.size < smallGraphVertices.size) return 0.0

        var totalScore = 0.0
        for (i in 0 until smallGraphVertices.size - 1) {
            val e1From = largeGraph[i]
            val e1To = largeGraph[i + 1]

            val e2From = Point(smallGraphVertices[i].x, smallGraphVertices[i].y)
            val e2To = Point(smallGraphVertices[i + 1].x, smallGraphVertices[i + 1].y)

            val length1 = euklideanDistance(e1From, e1To)
            val length2 = euklideanDistance(e2From, e2To)
            val lengthDiff = abs(length1 - length2) / maxOf(length1, length2)

            val angle1 = atan2((e1To.y - e1From.y), (e1To.x - e1From.x))
            val angle2 = atan2((e2To.y - e2From.y), (e2To.x - e2From.x))
            val angleDiff = abs(angle1 - angle2)

            val score = 0.5 * lengthDiff + 0.5 * angleDiff
            totalScore += (1.0 - minOf(score, 0.85))
        }

        return totalScore / (smallGraphVertices.size - 1)
    }

    companion object {
        private var instance: CompareGraph? = null
        fun getInstance(): CompareGraph {
            if (instance == null) {
                instance = CompareGraph()
            }
            return instance!!
        }
    }
}