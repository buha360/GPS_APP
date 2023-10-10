package com.example.gps_app

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class CompareGraph {
    data class Point(val x: Double, val y: Double)

    fun findSubgraph(largeGraph: MutableMap<Point, MutableList<Point>>, smallGraph: CanvasView.Graph, iterations: Int = 200): List<Point>? {
        var bestMatch: List<Point>? = null
        var bestScore = Double.MIN_VALUE

        for (attempt in 1..iterations) {
            val randomStartIndex = Random.nextInt(largeGraph.keys.size)
            val startNode = largeGraph.keys.elementAt(randomStartIndex)

            Log.d("gps_app-finish: - Attempt: $attempt: ", "Starting from: $startNode")

            val currentConstructedGraph = mutableListOf<Point>()
            var currentPoint = startNode
            currentConstructedGraph.add(currentPoint)

            val sizeLimit = minOf(largeGraph.size, smallGraph.vertices.size)
            for (i in 0 until sizeLimit - 1) {
                val nextSmallGraphPoint = Point(smallGraph.vertices[i + 1].x, smallGraph.vertices[i + 1].y)
                val bestNeighbor = chooseNextPoint(largeGraph[currentPoint]!!, nextSmallGraphPoint)

                currentConstructedGraph.add(bestNeighbor)
                currentPoint = bestNeighbor
            }

            val score = matchScore(currentConstructedGraph, smallGraph.vertices)
            if (score > bestScore) {
                bestScore = score
                bestMatch = currentConstructedGraph
            }

            Log.d("gps_app-finish: - Attempt: $attempt: ", "Score: $score")
        }

        Log.d("gps_app-finish: - BestMatch: $bestMatch: ", "Score: $bestScore")
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

            val score = 0.3 * lengthDiff + 0.3 * angleDiff
            totalScore += (1.0 - minOf(score, 0.6))
        }

        return totalScore / (smallGraphVertices.size - 1)
    }

    private fun chooseNextPoint(neighbors: List<Point>, target: Point): Point {
        val bestNeighbor = neighbors.minByOrNull { euklideanDistance(it, target) }
        return bestNeighbor!!
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