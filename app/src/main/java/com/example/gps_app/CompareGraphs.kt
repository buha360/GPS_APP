package com.example.gps_app

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

class CompareGraph {
    data class Point(val x: Double, val y: Double)

    fun findSubgraph(largeGraph: MutableMap<Point, MutableList<Point>>, smallGraph: CanvasView.Graph): List<Point>? {
        var bestMatch: List<Point>? = null
        var bestScore = Double.MIN_VALUE

        for (startNode in largeGraph.keys) {
            if (isValidStartNode(largeGraph, startNode, smallGraph.vertices.size)) {
                val score = matchScore(largeGraph, startNode, smallGraph.vertices)
                if (score > bestScore) {
                    bestScore = score
                    bestMatch = largeGraph[startNode]
                }
            }
        }

        return bestMatch
    }

    private fun isValidStartNode(largeGraph: MutableMap<Point, MutableList<Point>>, startNode: Point, requiredNeighbors: Int): Boolean {
        return (largeGraph[startNode]?.size ?: 0) >= requiredNeighbors
    }

    private fun euklideanDistance(p1: Point, p2: Point): Double {
        return sqrt(((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y)))
    }

    private fun matchScore(largeGraph: MutableMap<Point, MutableList<Point>>, startNode: Point, smallGraphVertices: List<CanvasView.Vertex>): Double {
        if (smallGraphVertices.isEmpty() || (largeGraph[startNode]?.size ?: 0) < smallGraphVertices.size) return 0.0

        var totalScore = 0.0

        for (i in 0 until smallGraphVertices.size - 1) {
            val e1From = largeGraph[startNode]?.get(i) ?: return 0.0
            val e1To = largeGraph[startNode]?.get(i + 1) ?: return 0.0

            val e2From = Point(smallGraphVertices[i].x, smallGraphVertices[i].y)
            val e2To = Point(smallGraphVertices[i + 1].x, smallGraphVertices[i + 1].y)

            val length1 = euklideanDistance(e1From, e1To)
            val length2 = euklideanDistance(e2From, e2To)
            val lengthDiff = abs(length1 - length2) / maxOf(length1, length2)

            val angle1 = atan2((e1To.y - e1From.y), (e1To.x - e1From.x))
            val angle2 = atan2((e2To.y - e2From.y), (e2To.x - e2From.x))
            val angleDiff = abs(angle1 - angle2)

            val startDistance = euklideanDistance(e1From, e2From)
            val endDistance = euklideanDistance(e1To, e2To)

            val score = 0.3 * lengthDiff + 0.3 * angleDiff + 0.3 * startDistance + 0.3 * endDistance
            totalScore += (1.0 - minOf(score, 0.55))
        }

        return totalScore / (smallGraphVertices.size - 1)  // Average the score
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