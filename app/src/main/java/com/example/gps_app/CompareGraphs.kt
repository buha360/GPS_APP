package com.example.gps_app

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class CompareGraph {

    class PotentialMatchesHolder {
        private val maxMatchesSize = 20
        private val potentialMatches = mutableListOf<Pair<Double, List<Point>>>()
        private val discoveredPaths = HashSet<List<Point>>()  // létrehozunk egy új HashSet-t

        @Synchronized
        fun addMatch(score: Double, match: List<Point>) {
            if (!discoveredPaths.contains(match)) {  // Ellenőrizzük, hogy az út már szerepel-e a HashSet-ben
                potentialMatches.add(Pair(score, match))
                discoveredPaths.add(match)  // adjuk hozzá az utat a HashSet-hoz
                potentialMatches.sortByDescending { it.first }
                if (potentialMatches.size > maxMatchesSize) {
                    potentialMatches.removeAt(potentialMatches.size - 1)
                }
            }
        }

        fun getTopMatches(): List<List<Point>> {
            return potentialMatches.map { it.second }
        }
    }

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

    private val potentialMatchesHolder = PotentialMatchesHolder()

    private fun depthFirstSearch(
        largeGraph: MutableMap<Point, MutableList<Point>>,
        smallGraph: CanvasView.Graph,
        currentPath: MutableList<Point>,
        visited: MutableSet<Point>,
        currentDepth: Int
    ) {
        if (currentDepth == smallGraph.vertices.size) {
            val score = matchScore(currentPath, smallGraph.vertices)
            potentialMatchesHolder.addMatch(score, currentPath.toList())
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
                    currentDepth + 1
                )

                visited.remove(neighbor)
                currentPath.removeAt(currentPath.size - 1)
            }
        }
    }

    fun findSubgraph(largeGraph: MutableMap<Point, MutableList<Point>>, smallGraph: CanvasView.Graph): List<List<Point>> {
        val rotations = (0 until 360 step 15).map { it.toDouble() }
        val scales = listOf(0.8, 0.9, 1.0, 1.1, 1.2)

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

                for (startNode in largeGraph.keys) {
                    val currentPath = mutableListOf(startNode)
                    val visited = mutableSetOf(startNode)

                    depthFirstSearch(
                        largeGraph,
                        CanvasView.Graph(transformedSmallGraph),
                        currentPath,
                        visited,
                        1
                    )
                }
            }
        }

        return potentialMatchesHolder.getTopMatches()
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
            totalScore += (1.0 - minOf(score, 0.8))
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